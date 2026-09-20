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

            val glyph =
                char.toString()

            val path =
                Path()

            paint.getTextPath(
                glyph,
                0,
                glyph.length,
                0f,
                0f,
                path
            )

            require(!path.isEmpty) {
                "A fonte não possui desenho para este caractere."
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

            points +=
                EmbroideryPoint(
                    currentX,
                    currentY,
                    StitchCommand.END,
                    0
                )

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

            val design =
                EmbroideryDesign(
                    fileName =
                        "fonte-" +
                            safeName(
                                glyph
                            ) +
                            "." +
                            outputFormat
                                .lowercase(
                                    Locale.ROOT
                                ),
                    format =
                        outputFormat,
                    label =
                        glyph,
                    points =
                        points,
                    bounds =
                        EmbroideryBounds(
                            minXUnits =
                                minX,
                            maxXUnits =
                                maxX,
                            minYUnits =
                                minY,
                            maxYUnits =
                                maxY
                        ),
                    stitchCount =
                        points.count {
                            it.command ==
                                StitchCommand.STITCH
                        },
                    jumpCount =
                        points.count {
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
                        96f
                    ).coerceIn(
                    2f,
                    18f
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
                Regex("\\p{M}+"),
                ""
            )
            .replace(
                Regex("[^A-Za-z0-9_-]+"),
                "_"
            )
            .trim('_')
            .take(24)
            .ifBlank {
                "glifo"
            }
}
