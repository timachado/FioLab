package com.timachado.fiolab.font

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import com.timachado.fiolab.core.embroidery.EmbroideryBounds
import com.timachado.fiolab.core.embroidery.EmbroideryDesign
import com.timachado.fiolab.core.embroidery.EmbroideryPoint
import com.timachado.fiolab.core.embroidery.HoopValidator
import com.timachado.fiolab.core.embroidery.MatrixConverter
import com.timachado.fiolab.core.embroidery.SatinGenerator
import com.timachado.fiolab.core.embroidery.StitchCommand
import com.timachado.fiolab.core.embroidery.TextMatrixOptions
import com.timachado.fiolab.core.embroidery.TextStitchStyle
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

object ImportedFontMatrixGenerator {

    fun generateGlyph(
        font: ImportedFont,
        char: Char,
        options: TextMatrixOptions
    ): Result<EmbroideryDesign> =
        generateTextInternal(
            font = font,
            sourceText = char.toString(),
            options = options,
            filePrefix = "fonte"
        )

    fun generateText(
        font: ImportedFont,
        text: String,
        options: TextMatrixOptions
    ): Result<EmbroideryDesign> =
        generateTextInternal(
            font = font,
            sourceText = text,
            options = options,
            filePrefix = "nome"
        )

    private fun generateTextInternal(
        font: ImportedFont,
        sourceText: String,
        options: TextMatrixOptions,
        filePrefix: String
    ): Result<EmbroideryDesign> =
        runCatching {
            require(
                options.heightMm in 4f..40f
            ) {
                "A altura deve ficar entre 4 e 40 mm."
            }

            require(
                options.stitchLengthMm in 1f..5f
            ) {
                "O comprimento de ponto deve ficar entre 1 e 5 mm."
            }

            require(
                options.satinWidthMm in 1f..6f
            ) {
                "A largura Satin deve ficar entre 1 e 6 mm."
            }

            require(
                options.satinDensityMm in 0.3f..1.2f
            ) {
                "A densidade Satin deve ficar entre 0,3 e 1,2 mm."
            }

            require(
                options.satinPullCompensationMm in 0f..1f
            ) {
                "A compensação de repuxo deve ficar entre 0 e 1 mm."
            }

            val text =
                sourceText
                    .trim()
                    .take(24)

            require(
                text.isNotBlank()
            ) {
                "Digite um nome."
            }

            val outputFormat =
                options.outputFormat
                    .uppercase(Locale.ROOT)

            require(
                outputFormat in
                    MatrixConverter.supportedFormats
            ) {
                "Formato de saída inválido."
            }

            val typeface =
                ImportedFontStore
                    .loadTypeface(font)
                    .getOrThrow()

            val paint =
                Paint(
                    Paint.ANTI_ALIAS_FLAG
                ).apply {
                    this.typeface =
                        typeface
                    textSize =
                        1000f
                    style =
                        Paint.Style.FILL
                }

            val renderableText =
                text.map {
                        char ->
                    resolveCharacter(
                        paint = paint,
                        font = font,
                        char = char
                    )
                }.joinToString("")

            val path =
                Path()

            paint.getTextPath(
                renderableText,
                0,
                renderableText.length,
                0f,
                0f,
                path
            )

            require(!path.isEmpty) {
                "A fonte não gerou um contorno utilizável para este texto."
            }

            val metrics =
                paint.fontMetrics

            val emHeightPx =
                (
                    metrics.descent -
                        metrics.ascent
                    ).coerceAtLeast(1f)

            val scale =
                options.heightMm *
                    10f /
                    emHeightPx

            val sampled =
                samplePath(path)

            require(
                sampled.isNotEmpty()
            ) {
                "Não foi possível interpretar o contorno da fonte."
            }

            val points =
                mutableListOf<
                    EmbroideryPoint
                >()

            var currentX = 0
            var currentY = 0

            sampled.forEachIndexed {
                    index,
                    contour ->
                val units =
                    contour.points
                        .map {
                            point ->
                            Pair(
                                (
                                    point.first *
                                        scale
                                    ).roundToInt(),
                                (
                                    (
                                        point.second -
                                            metrics.ascent
                                        ) *
                                        scale
                                    ).roundToInt()
                            )
                        }
                        .fold(
                            mutableListOf<
                                Pair<Int, Int>
                            >()
                        ) {
                            acc,
                            point ->
                            if (
                                acc.lastOrNull() !=
                                    point
                            ) {
                                acc += point
                            }

                            acc
                        }

                if (
                    units.size <
                        2
                ) {
                    return@forEachIndexed
                }

                val stroke =
                    units.toMutableList()

                if (
                    contour.closed &&
                    stroke.first() !=
                        stroke.last()
                ) {
                    stroke +=
                        stroke.first()
                }

                if (
                    index > 0 &&
                    points.isNotEmpty()
                ) {
                    points +=
                        EmbroideryPoint(
                            currentX,
                            currentY,
                            StitchCommand.TRIM,
                            0
                        )
                }

                val first =
                    stroke.first()

                points +=
                    EmbroideryPoint(
                        first.first,
                        first.second,
                        StitchCommand.JUMP,
                        0
                    )

                currentX =
                    first.first

                currentY =
                    first.second

                when (
                    options.style
                ) {
                    TextStitchStyle.RUNNING -> {
                        stroke
                            .drop(1)
                            .forEach {
                                    target ->
                                val result =
                                    appendRunning(
                                        output =
                                            points,
                                        fromX =
                                            currentX,
                                        fromY =
                                            currentY,
                                        toX =
                                            target.first,
                                        toY =
                                            target.second,
                                        maxLengthUnits =
                                            options
                                                .stitchLengthMm *
                                                10f
                                    )

                                currentX =
                                    result.first

                                currentY =
                                    result.second
                            }
                    }

                    TextStitchStyle.SATIN -> {
                        val built =
                            SatinGenerator
                                .append(
                                    points =
                                        points,
                                    stroke =
                                        stroke,
                                    currentX =
                                        currentX,
                                    currentY =
                                        currentY,
                                    widthUnits =
                                        options
                                            .satinWidthMm *
                                            10f,
                                    stepUnits =
                                        options
                                            .satinDensityMm *
                                            10f,
                                    pullCompensationUnits =
                                        options
                                            .satinPullCompensationMm *
                                            10f,
                                    shortStitches =
                                        options
                                            .satinShortStitches,
                                    underlayMode =
                                        options
                                            .satinUnderlayMode
                                )

                        currentX =
                            built.currentX

                        currentY =
                            built.currentY
                    }
                }
            }

            require(
                points.any {
                    it.command ==
                        StitchCommand.STITCH
                }
            ) {
                "A fonte não gerou pontadas."
            }

            val coordinates =
                points.filter {
                    it.command !=
                        StitchCommand.END
                }

            val minX =
                coordinates.minOf {
                    it.xUnits
                }

            val maxX =
                coordinates.maxOf {
                    it.xUnits
                }

            val minY =
                coordinates.minOf {
                    it.yUnits
                }

            val maxY =
                coordinates.maxOf {
                    it.yUnits
                }

            val centerX =
                (
                    minX +
                        maxX
                    ) /
                    2f

            val centerY =
                (
                    minY +
                        maxY
                    ) /
                    2f

            val centered =
                points.map {
                        point ->
                    point.copy(
                        xUnits =
                            (
                                point.xUnits -
                                    centerX
                                ).roundToInt(),
                        yUnits =
                            (
                                point.yUnits -
                                    centerY
                                ).roundToInt()
                    )
                }.toMutableList()

            val endPoint =
                centered
                    .lastOrNull()
                    ?: error(
                        "A fonte não gerou pontadas."
                    )

            centered +=
                EmbroideryPoint(
                    endPoint.xUnits,
                    endPoint.yUnits,
                    StitchCommand.END,
                    0
                )

            val centeredCoordinates =
                centered.filter {
                    it.command !=
                        StitchCommand.END
                }

            val bounds =
                EmbroideryBounds(
                    minXUnits =
                        centeredCoordinates
                            .minOf {
                                it.xUnits
                            },
                    maxXUnits =
                        centeredCoordinates
                            .maxOf {
                                it.xUnits
                            },
                    minYUnits =
                        centeredCoordinates
                            .minOf {
                                it.yUnits
                            },
                    maxYUnits =
                        centeredCoordinates
                            .maxOf {
                                it.yUnits
                            }
                )

            val design =
                EmbroideryDesign(
                    fileName =
                        filePrefix +
                            "-" +
                            safeName(
                                text
                            ) +
                            "." +
                            outputFormat
                                .lowercase(
                                    Locale.ROOT
                                ),
                    format =
                        outputFormat,
                    label =
                        text,
                    points =
                        centered,
                    bounds =
                        bounds,
                    stitchCount =
                        centered.count {
                            it.command ==
                                StitchCommand.STITCH
                        },
                    jumpCount =
                        centered.count {
                            it.command ==
                                StitchCommand.JUMP
                        },
                    colorChanges =
                        0,
                    endFound =
                        true,
                    sourceBytes =
                        ByteArray(0),
                    threadColors =
                        listOf(
                            options.color
                        ),
                    isModified =
                        true,
                    hoopProfile =
                        options.hoopProfile,
                    fabricProfile =
                        options.fabricProfile
                )

            if (
                options.enforceHoop &&
                options.hoopProfile !=
                    null
            ) {
                val fit =
                    HoopValidator
                        .validate(
                            design,
                            options
                                .hoopProfile
                        )

                require(fit.fits) {
                    "A matriz ultrapassa a área segura do bastidor " +
                        options
                            .hoopProfile
                            .displayName +
                        "."
                }
            }

            design
        }

    private fun resolveCharacter(
        paint: Paint,
        font: ImportedFont,
        char: Char
    ): String {
        if (
            char.isWhitespace()
        ) {
            return char.toString()
        }

        val original =
            char.toString()

        if (
            paint.hasGlyph(
                original
            )
        ) {
            return original
        }

        val normalized =
            Normalizer
                .normalize(
                    original,
                    Normalizer.Form.NFD
                )
                .replace(
                    Regex("\p{M}+"),
                    ""
                )

        if (
            normalized.isNotBlank() &&
            paint.hasGlyph(
                normalized
            )
        ) {
            return normalized
        }

        error(
            "A fonte " +
                font.displayName +
                " não possui o caractere \"" +
                char +
                "\"."
        )
    }

    private data class SampledContour(
        val points:
            List<Pair<Float, Float>>,
        val closed: Boolean
    )

    private fun samplePath(
        path: Path
    ): List<SampledContour> {
        val measure =
            PathMeasure(
                path,
                false
            )

        val result =
            mutableListOf<
                SampledContour
            >()

        val position =
            FloatArray(2)

        do {
            val length =
                measure.length

            if (length <= 0f) {
                continue
            }

            val step =
                (
                    length /
                        128f
                    ).coerceIn(
                    1.5f,
                    14f
                )

            val contour =
                mutableListOf<
                    Pair<Float, Float>
                >()

            var distance = 0f

            while (
                distance <
                    length
            ) {
                if (
                    measure.getPosTan(
                        distance,
                        position,
                        null
                    )
                ) {
                    contour +=
                        Pair(
                            position[0],
                            position[1]
                        )
                }

                distance +=
                    step
            }

            if (
                measure.getPosTan(
                    length,
                    position,
                    null
                )
            ) {
                contour +=
                    Pair(
                        position[0],
                        position[1]
                    )
            }

            if (
                contour.size >=
                    2
            ) {
                result +=
                    SampledContour(
                        points =
                            contour,
                        closed =
                            measure.isClosed
                    )
            }
        } while (
            measure.nextContour()
        )

        return result
    }

    private fun appendRunning(
        output:
            MutableList<EmbroideryPoint>,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
        maxLengthUnits: Float
    ): Pair<Int, Int> {
        val dx =
            toX -
                fromX

        val dy =
            toY -
                fromY

        val distance =
            hypot(
                dx.toDouble(),
                dy.toDouble()
            )

        val segments =
            maxOf(
                1,
                ceil(
                    distance /
                        maxLengthUnits
                ).toInt()
            )

        for (
            part in
                1..segments
        ) {
            val ratio =
                part.toFloat() /
                    segments

            output +=
                EmbroideryPoint(
                    xUnits =
                        (
                            fromX +
                                dx *
                                    ratio
                            ).roundToInt(),
                    yUnits =
                        (
                            fromY +
                                dy *
                                    ratio
                            ).roundToInt(),
                    command =
                        StitchCommand.STITCH,
                    colorIndex =
                        0
                )
        }

        return Pair(
            toX,
            toY
        )
    }

    private fun safeName(
        value: String
    ): String =
        Normalizer
            .normalize(
                value,
                Normalizer.Form.NFD
            )
            .replace(
                Regex("\p{M}+"),
                ""
            )
            .replace(
                Regex("[^A-Za-z0-9_-]+"),
                "_"
            )
            .trim('_')
            .take(24)
            .ifBlank {
                "texto"
            }
}
