package com.timachado.fiolab.font

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import com.timachado.fiolab.core.embroidery.EmbroideryBounds
import com.timachado.fiolab.core.embroidery.EmbroideryDesign
import com.timachado.fiolab.core.embroidery.EmbroideryPoint
import com.timachado.fiolab.core.embroidery.HoopValidator
import com.timachado.fiolab.core.embroidery.MatrixConverter
import com.timachado.fiolab.core.embroidery.StitchCommand
import com.timachado.fiolab.core.embroidery.TextMatrixOptions
import com.timachado.fiolab.core.embroidery.TextStitchStyle
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
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

            val pathBounds =
                RectF().also {
                    path.computeBounds(
                        it,
                        true
                    )
                }

            require(
                pathBounds.width() >
                    0.5f &&
                    pathBounds.height() >
                    0.5f
            ) {
                "A fonte não gerou uma área utilizável para este texto."
            }

            val targetHeightUnits =
                options.heightMm *
                    10f

            val scale =
                targetHeightUnits /
                    pathBounds.height()

            val sampled =
                samplePath(path)

            require(
                sampled.isNotEmpty()
            ) {
                "Não foi possível interpretar o contorno da fonte."
            }

            val transformed =
                sampled.map {
                        contour ->
                    SampledContour(
                        points =
                            contour.points
                                .map {
                                    point ->
                                    Pair(
                                        (
                                            (
                                                point.first -
                                                    pathBounds
                                                        .centerX()
                                                ) *
                                                scale
                                            ).roundToInt(),
                                        (
                                            (
                                                pathBounds
                                                    .centerY() -
                                                    point.second
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
                                        acc +=
                                            point
                                    }

                                    acc
                                },
                        closed =
                            contour.closed
                    )
                }
                .filter {
                    it.points.size >=
                        2
                }

            require(
                transformed.isNotEmpty()
            ) {
                "A fonte não gerou contornos válidos."
            }

            val points =
                when (
                    options.style
                ) {
                    TextStitchStyle.RUNNING ->
                        buildRunningOutline(
                            contours =
                                transformed,
                            stitchLengthUnits =
                                options
                                    .stitchLengthMm *
                                    10f
                        )

                    TextStitchStyle.SATIN ->
                        buildFilledText(
                            contours =
                                transformed,
                            rowStepUnits =
                                options
                                    .satinDensityMm *
                                    10f,
                            pullCompensationUnits =
                                options
                                    .satinPullCompensationMm *
                                    10f,
                            maxStitchUnits =
                                max(
                                    45f,
                                    options
                                        .satinWidthMm *
                                        10f
                                )
                        )
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

    private fun buildRunningOutline(
        contours: List<SampledContour>,
        stitchLengthUnits: Float
    ): MutableList<EmbroideryPoint> {
        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        var currentX = 0
        var currentY = 0

        contours.forEachIndexed {
                index,
                contour ->
            val stroke =
                contour.points
                    .toMutableList()

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
                output.isNotEmpty()
            ) {
                output +=
                    EmbroideryPoint(
                        currentX,
                        currentY,
                        StitchCommand.TRIM,
                        0
                    )
            }

            val first =
                stroke.first()

            output +=
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

            stroke
                .drop(1)
                .forEach {
                        target ->
                    val result =
                        appendRunning(
                            output =
                                output,
                            fromX =
                                currentX,
                            fromY =
                                currentY,
                            toX =
                                target.first,
                            toY =
                                target.second,
                            maxLengthUnits =
                                stitchLengthUnits
                        )

                    currentX =
                        result.first

                    currentY =
                        result.second
                }
        }

        return output
    }

    private fun buildFilledText(
        contours: List<SampledContour>,
        rowStepUnits: Float,
        pullCompensationUnits: Float,
        maxStitchUnits: Float
    ): MutableList<EmbroideryPoint> {
        val allPoints =
            contours.flatMap {
                it.points
            }

        val minY =
            allPoints.minOf {
                it.second
            }

        val maxY =
            allPoints.maxOf {
                it.second
            }

        val step =
            rowStepUnits
                .coerceIn(
                    3f,
                    12f
                )

        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        var currentX = 0
        var currentY = 0
        var hasCurrent = false
        var rowIndex = 0
        var previousSingleSegment =
            false

        var scanY =
            minY.toFloat() +
                step /
                    2f

        while (
            scanY <=
                maxY.toFloat() +
                    0.01f
        ) {
            val intersections =
                mutableListOf<Float>()

            contours.forEach {
                    contour ->
                val polygon =
                    if (
                        contour.closed &&
                        contour.points.first() !=
                            contour.points.last()
                    ) {
                        contour.points +
                            contour.points.first()
                    } else {
                        contour.points
                    }

                for (
                    index in
                        1 until polygon.size
                ) {
                    val first =
                        polygon[
                            index -
                                1
                        ]

                    val second =
                        polygon[index]

                    val y1 =
                        first.second
                            .toFloat()

                    val y2 =
                        second.second
                            .toFloat()

                    val crosses =
                        (
                            y1 <= scanY &&
                                y2 > scanY
                            ) ||
                            (
                                y2 <= scanY &&
                                    y1 > scanY
                                )

                    if (!crosses) {
                        continue
                    }

                    val ratio =
                        (
                            scanY -
                                y1
                            ) /
                            (
                                y2 -
                                    y1
                                )

                    intersections +=
                        first.first +
                            (
                                second.first -
                                    first.first
                                ) *
                                ratio
                }
            }

            intersections.sort()

            val segments =
                mutableListOf<
                    Pair<Int, Int>
                >()

            var index = 0

            while (
                index +
                    1 <
                    intersections.size
            ) {
                val left =
                    (
                        intersections[index] -
                            pullCompensationUnits
                        ).roundToInt()

                val right =
                    (
                        intersections[
                            index +
                                1
                        ] +
                            pullCompensationUnits
                        ).roundToInt()

                if (
                    right -
                        left >=
                        2
                ) {
                    segments +=
                        Pair(
                            left,
                            right
                        )
                }

                index +=
                    2
            }

            if (
                segments.isNotEmpty()
            ) {
                val reversed =
                    rowIndex %
                        2 ==
                        1

                val ordered =
                    if (reversed) {
                        segments.asReversed()
                    } else {
                        segments
                    }

                ordered.forEachIndexed {
                        segmentIndex,
                        segment ->
                    val startX =
                        if (reversed) {
                            segment.second
                        } else {
                            segment.first
                        }

                    val endX =
                        if (reversed) {
                            segment.first
                        } else {
                            segment.second
                        }

                    val y =
                        scanY
                            .roundToInt()

                    val canConnect =
                        hasCurrent &&
                            previousSingleSegment &&
                            segments.size ==
                                1 &&
                            segmentIndex ==
                                0 &&
                            hypot(
                                (
                                    startX -
                                        currentX
                                    ).toDouble(),
                                (
                                    y -
                                        currentY
                                    ).toDouble()
                            ) <=
                                max(
                                    24f,
                                    step *
                                        4f
                                ).toDouble()

                    if (canConnect) {
                        val connector =
                            appendRunning(
                                output =
                                    output,
                                fromX =
                                    currentX,
                                fromY =
                                    currentY,
                                toX =
                                    startX,
                                toY =
                                    y,
                                maxLengthUnits =
                                    24f
                            )

                        currentX =
                            connector.first

                        currentY =
                            connector.second
                    } else {
                        if (hasCurrent) {
                            val distance =
                                hypot(
                                    (
                                        startX -
                                            currentX
                                        ).toDouble(),
                                    (
                                        y -
                                            currentY
                                        ).toDouble()
                                )

                            if (
                                distance >
                                    40.0
                            ) {
                                output +=
                                    EmbroideryPoint(
                                        currentX,
                                        currentY,
                                        StitchCommand.TRIM,
                                        0
                                    )
                            }
                        }

                        output +=
                            EmbroideryPoint(
                                startX,
                                y,
                                StitchCommand.JUMP,
                                0
                            )

                        currentX =
                            startX

                        currentY =
                            y

                        hasCurrent =
                            true
                    }

                    val filled =
                        appendRunning(
                            output =
                                output,
                            fromX =
                                currentX,
                            fromY =
                                currentY,
                            toX =
                                endX,
                            toY =
                                y,
                            maxLengthUnits =
                                maxStitchUnits
                        )

                    currentX =
                        filled.first

                    currentY =
                        filled.second

                    hasCurrent =
                        true
                }

                previousSingleSegment =
                    segments.size ==
                        1
            } else {
                previousSingleSegment =
                    false
            }

            rowIndex++
            scanY +=
                step
        }

        return output
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
                    Regex("\\p{M}+"),
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
            List<Pair<Int, Int>>,
        val closed: Boolean
    )

    private data class RawSampledContour(
        val points:
            List<Pair<Float, Float>>,
        val closed: Boolean
    )

    private fun samplePath(
        path: Path
    ): List<RawSampledContour> {
        val measure =
            PathMeasure(
                path,
                false
            )

        val result =
            mutableListOf<
                RawSampledContour
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
                        180f
                    ).coerceIn(
                    1.2f,
                    10f
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
                    RawSampledContour(
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

        val safeMaxLength =
            maxLengthUnits
                .coerceAtLeast(
                    1f
                )

        val segments =
            max(
                1,
                ceil(
                    distance /
                        safeMaxLength
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
                "texto"
            }
}
