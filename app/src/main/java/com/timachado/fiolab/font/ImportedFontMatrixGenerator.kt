package com.timachado.fiolab.font

import android.graphics.Bitmap
import android.graphics.Canvas
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
import kotlin.math.sqrt

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

            val glyphGroups =
                sampleGlyphGroups(
                    paint = paint,
                    text = renderableText,
                    pathBounds = pathBounds,
                    scale = scale,
                    spacingMm =
                        options.spacingMm
                )

            require(
                glyphGroups.isNotEmpty()
            ) {
                "A fonte não gerou contornos válidos."
            }

            val points =
                buildTextInReadingOrder(
                    glyphGroups =
                        glyphGroups,
                    options =
                        options
                )

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

    private fun sampleGlyphGroups(
        paint: Paint,
        text: String,
        pathBounds: RectF,
        scale: Float,
        spacingMm: Float
    ): List<List<SampledContour>> {
        val groups =
            mutableListOf<
                List<SampledContour>
            >()

        val extraSpacingRaw =
            if (
                scale >
                    0.0001f
            ) {
                spacingMm *
                    10f /
                    scale
            } else {
                0f
            }

        text.forEachIndexed {
                index,
                char ->
            if (
                char.isWhitespace()
            ) {
                return@forEachIndexed
            }

            val glyphPath =
                Path()

            val prefixAdvance =
                if (
                    index ==
                        0
                ) {
                    0f
                } else {
                    paint.measureText(
                        text,
                        0,
                        index
                    )
                }

            val glyphX =
                prefixAdvance +
                    extraSpacingRaw *
                        index

            paint.getTextPath(
                text,
                index,
                index +
                    1,
                glyphX,
                0f,
                glyphPath
            )

            if (
                glyphPath.isEmpty
            ) {
                return@forEachIndexed
            }

            val transformed =
                samplePath(
                    glyphPath
                )
                    .map {
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

            if (
                transformed.isNotEmpty()
            ) {
                groups +=
                    transformed
            }
        }

        return groups
    }

    private fun buildTextInReadingOrder(
        glyphGroups:
            List<List<SampledContour>>,
        options: TextMatrixOptions
    ): MutableList<EmbroideryPoint> {
        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        glyphGroups.forEach {
                contours ->
            val glyphPoints =
                when (
                    options.style
                ) {
                    TextStitchStyle.RUNNING ->
                        buildRunningOutline(
                            contours =
                                contours,
                            stitchLengthUnits =
                                options
                                    .stitchLengthMm *
                                    10f
                        )

                    TextStitchStyle.SATIN ->
                        buildSatinByAxis(
                            contours =
                                contours,
                            options =
                                options
                        )
                }

            if (
                glyphPoints.isEmpty()
            ) {
                return@forEach
            }

            if (
                output.isNotEmpty()
            ) {
                val last =
                    output.last()

                if (
                    last.command !=
                        StitchCommand.TRIM
                ) {
                    output +=
                        EmbroideryPoint(
                            last.xUnits,
                            last.yUnits,
                            StitchCommand.TRIM,
                            0
                        )
                }
            }

            output +=
                glyphPoints
        }

        return output
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

    private data class SkeletonPoint(
        val x: Int,
        val y: Int
    )

    private data class SatinSampleRow(
        val yUnits: Int,
        val leftUnits: Int,
        val rightUnits: Int
    ) {
        val centerX: Float
            get() =
                (
                    leftUnits +
                        rightUnits
                    ) /
                    2f

        val widthUnits: Int
            get() =
                rightUnits -
                    leftUnits
    }

    private data class SatinSampleColumn(
        val rows:
            MutableList<SatinSampleRow>
    )

    private fun buildSatinByAxis(
        contours: List<SampledContour>,
        options: TextMatrixOptions
    ): MutableList<EmbroideryPoint> {
        val pitchUnits =
            (
                options
                    .satinDensityMm *
                    10f
                ).coerceIn(
                3f,
                9f
            )

        val rowLayers =
            sampleSatinRows(
                contours =
                    contours,
                pitchUnits =
                    pitchUnits
            )

        if (
            rowLayers.isEmpty()
        ) {
            return buildRunningOutline(
                contours =
                    contours,
                stitchLengthUnits =
                    options
                        .stitchLengthMm *
                        10f
            )
        }

        val columns =
            buildSatinColumns(
                rowLayers =
                    rowLayers,
                pitchUnits =
                    pitchUnits
            )
                .map {
                    smoothSatinColumn(
                        it
                    )
                }
                .filter {
                    it.rows.size >=
                        2
                }

        if (
            columns.isEmpty()
        ) {
            return buildRunningOutline(
                contours =
                    contours,
                stitchLengthUnits =
                    options
                        .stitchLengthMm *
                        10f
            )
        }

        val routed =
            routeSatinColumns(
                columns
            )

        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        routed.forEach {
                column ->
            emitSatinColumn(
                output =
                    output,
                column =
                    column,
                options =
                    options
            )
        }

        val quality =
            AdaptiveFontPolicy
                .qualityFromCoordinates(
                    output.map {
                            point ->
                        Triple(
                            point.xUnits,
                            point.yUnits,
                            point.command ==
                                StitchCommand.STITCH
                        )
                    }
                )

        val repaired =
            if (
                AdaptiveFontPolicy
                    .isAcceptable(
                        quality
                    )
            ) {
                output
            } else {
                repairSatinSequence(
                    output =
                        output,
                    maxLengthUnits =
                        80f
                )
            }

        return if (
            repaired.any {
                it.command ==
                    StitchCommand.STITCH
            }
        ) {
            repaired
        } else {
            buildRunningOutline(
                contours =
                    contours,
                stitchLengthUnits =
                    options
                        .stitchLengthMm *
                        10f
            )
        }
    }

    private fun sampleSatinRows(
        contours:
            List<SampledContour>,
        pitchUnits: Float
    ): List<
        Pair<
            Int,
            List<SatinSampleRow>
        >
    > {
        val allPoints =
            contours.flatMap {
                it.points
            }

        if (
            allPoints.isEmpty()
        ) {
            return emptyList()
        }

        val minY =
            allPoints.minOf {
                it.second
            }

        val maxY =
            allPoints.maxOf {
                it.second
            }

        val layers =
            mutableListOf<
                Pair<
                    Int,
                    List<SatinSampleRow>
                >
            >()

        var y =
            minY.toFloat() +
                pitchUnits /
                    2f

        while (
            y <=
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
                        contour.points
                            .first() !=
                            contour.points
                                .last()
                    ) {
                        contour.points +
                            contour.points
                                .first()
                    } else {
                        contour.points
                    }

                for (
                    index in
                        1 until
                            polygon.size
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
                            y1 <= y &&
                                y2 > y
                            ) ||
                            (
                                y2 <= y &&
                                    y1 > y
                                )

                    if (
                        !crosses
                    ) {
                        continue
                    }

                    val ratio =
                        (
                            y -
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

            val rows =
                mutableListOf<
                    SatinSampleRow
                >()

            var index =
                0

            while (
                index +
                    1 <
                    intersections.size
            ) {
                val left =
                    intersections[index]
                        .roundToInt()

                val right =
                    intersections[
                        index +
                            1
                    ]
                        .roundToInt()

                if (
                    right -
                        left >=
                        2
                ) {
                    rows +=
                        SatinSampleRow(
                            yUnits =
                                y.roundToInt(),
                            leftUnits =
                                left,
                            rightUnits =
                                right
                        )
                }

                index +=
                    2
            }

            if (
                rows.isNotEmpty()
            ) {
                layers +=
                    Pair(
                        y.roundToInt(),
                        rows
                    )
            }

            y +=
                pitchUnits
        }

        return layers
    }

    private fun buildSatinColumns(
        rowLayers:
            List<
                Pair<
                    Int,
                    List<SatinSampleRow>
                >
            >,
        pitchUnits: Float
    ): List<SatinSampleColumn> {
        val finished =
            mutableListOf<
                SatinSampleColumn
            >()

        var active =
            mutableListOf<
                SatinSampleColumn
            >()

        rowLayers.forEach {
                layer ->
            val rows =
                layer.second

            val candidates =
                mutableListOf<
                    Triple<
                        Int,
                        Int,
                        Float
                    >
                >()

            active.forEachIndexed {
                    columnIndex,
                    column ->
                val previous =
                    column.rows
                        .last()

                rows.forEachIndexed {
                        rowIndex,
                        row ->
                    val overlap =
                        minOf(
                            previous.rightUnits,
                            row.rightUnits
                        ) -
                            maxOf(
                                previous.leftUnits,
                                row.leftUnits
                            )

                    val centerDistance =
                        kotlin.math.abs(
                            previous.centerX -
                                row.centerX
                        )

                    val widthReference =
                        maxOf(
                            previous.widthUnits,
                            row.widthUnits
                        )
                            .toFloat()

                    val allowedGap =
                        maxOf(
                            pitchUnits *
                                2.2f,
                            widthReference *
                                .42f
                        )

                    if (
                        overlap >=
                            -pitchUnits ||
                        centerDistance <=
                            allowedGap
                    ) {
                        val score =
                            centerDistance -
                                overlap
                                    .coerceAtLeast(
                                        0
                                    ) *
                                    .35f

                        candidates +=
                            Triple(
                                columnIndex,
                                rowIndex,
                                score
                            )
                    }
                }
            }

            val assignedColumns =
                mutableSetOf<Int>()

            val assignedRows =
                mutableSetOf<Int>()

            val nextActive =
                mutableListOf<
                    SatinSampleColumn
                >()

            candidates
                .sortedBy {
                    it.third
                }
                .forEach {
                        candidate ->
                    val columnIndex =
                        candidate.first

                    val rowIndex =
                        candidate.second

                    if (
                        columnIndex in
                            assignedColumns ||
                        rowIndex in
                            assignedRows
                    ) {
                        return@forEach
                    }

                    val column =
                        active[
                            columnIndex
                        ]

                    column.rows +=
                        rows[
                            rowIndex
                        ]

                    assignedColumns +=
                        columnIndex

                    assignedRows +=
                        rowIndex

                    nextActive +=
                        column
                }

            active.forEachIndexed {
                    index,
                    column ->
                if (
                    index !in
                        assignedColumns
                ) {
                    finished +=
                        column
                }
            }

            rows.forEachIndexed {
                    index,
                    row ->
                if (
                    index !in
                        assignedRows
                ) {
                    nextActive +=
                        SatinSampleColumn(
                            rows =
                                mutableListOf(
                                    row
                                )
                        )
                }
            }

            active =
                nextActive
                    .distinct()
                    .toMutableList()
        }

        finished +=
            active

        return finished
    }

    private fun smoothSatinColumn(
        source:
            SatinSampleColumn
    ): SatinSampleColumn {
        val rows =
            source.rows

        if (
            rows.size <
                3
        ) {
            return source
        }

        val smoothed =
            rows.mapIndexed {
                    index,
                    row ->
                val start =
                    (
                        index -
                            1
                        ).coerceAtLeast(
                        0
                    )

                val end =
                    (
                        index +
                            1
                        ).coerceAtMost(
                        rows.lastIndex
                    )

                val window =
                    rows.subList(
                        start,
                        end +
                            1
                    )

                val left =
                    window
                        .map {
                            it.leftUnits
                        }
                        .average()
                        .roundToInt()

                val right =
                    window
                        .map {
                            it.rightUnits
                        }
                        .average()
                        .roundToInt()

                SatinSampleRow(
                    yUnits =
                        row.yUnits,
                    leftUnits =
                        minOf(
                            left,
                            right -
                                1
                        ),
                    rightUnits =
                        maxOf(
                            right,
                            left +
                                1
                        )
                )
            }

        return SatinSampleColumn(
            rows =
                smoothed
                    .toMutableList()
        )
    }

    private fun routeSatinColumns(
        source:
            List<SatinSampleColumn>
    ): List<SatinSampleColumn> {
        if (
            source.size <=
                1
        ) {
            return source
        }

        val remaining =
            source
                .map {
                    SatinSampleColumn(
                        rows =
                            it.rows
                                .toMutableList()
                    )
                }
                .toMutableList()

        val ordered =
            mutableListOf<
                SatinSampleColumn
            >()

        var currentX =
            remaining
                .minOfOrNull {
                    column ->
                    column.rows
                        .minOf {
                            it.leftUnits
                        }
                }
                ?.toFloat()
                ?: 0f

        var currentY =
            remaining
                .minOfOrNull {
                    column ->
                    column.rows
                        .first()
                        .yUnits
                }
                ?.toFloat()
                ?: 0f

        while (
            remaining.isNotEmpty()
        ) {
            var bestIndex =
                0

            var bestReverse =
                false

            var bestDistance =
                Double.MAX_VALUE

            remaining.forEachIndexed {
                    index,
                    column ->
                val first =
                    column.rows
                        .first()

                val last =
                    column.rows
                        .last()

                val firstDistance =
                    hypot(
                        (
                            first.centerX -
                                currentX
                            ).toDouble(),
                        (
                            first.yUnits -
                                currentY
                            ).toDouble()
                    )

                val lastDistance =
                    hypot(
                        (
                            last.centerX -
                                currentX
                            ).toDouble(),
                        (
                            last.yUnits -
                                currentY
                            ).toDouble()
                    )

                val reverse =
                    lastDistance <
                        firstDistance

                val distance =
                    minOf(
                        firstDistance,
                        lastDistance
                    )

                if (
                    distance <
                        bestDistance
                ) {
                    bestDistance =
                        distance

                    bestIndex =
                        index

                    bestReverse =
                        reverse
                }
            }

            val selected =
                remaining.removeAt(
                    bestIndex
                )

            val routed =
                if (
                    bestReverse
                ) {
                    SatinSampleColumn(
                        rows =
                            selected.rows
                                .asReversed()
                                .toMutableList()
                    )
                } else {
                    selected
                }

            ordered +=
                routed

            val end =
                routed.rows
                    .last()

            currentX =
                end.centerX

            currentY =
                end.yUnits
                    .toFloat()
        }

        return ordered
    }

    private fun emitSatinColumn(
        output:
            MutableList<EmbroideryPoint>,
        column:
            SatinSampleColumn,
        options:
            TextMatrixOptions
    ) {
        val rows =
            column.rows

        if (
            rows.size <
                2
        ) {
            return
        }

        if (
            output.isNotEmpty()
        ) {
            val last =
                output.last()

            if (
                last.command !=
                    StitchCommand.TRIM
            ) {
                output +=
                    EmbroideryPoint(
                        last.xUnits,
                        last.yUnits,
                        StitchCommand.TRIM,
                        0
                    )
            }
        }

        if (
            options
                .satinUnderlayMode !=
                com.timachado
                    .fiolab
                    .core
                    .embroidery
                    .SatinUnderlayMode
                    .NONE
        ) {
            val first =
                rows.first()

            output +=
                EmbroideryPoint(
                    first.centerX
                        .roundToInt(),
                    first.yUnits,
                    StitchCommand.JUMP,
                    0
                )

            var lastX =
                first.centerX

            var lastY =
                first.yUnits
                    .toFloat()

            rows.drop(1)
                .forEach {
                        row ->
                    val distance =
                        hypot(
                            (
                                row.centerX -
                                    lastX
                                ).toDouble(),
                            (
                                row.yUnits -
                                    lastY
                                ).toDouble()
                        )

                    if (
                        distance >=
                            16f
                    ) {
                        output +=
                            EmbroideryPoint(
                                row.centerX
                                    .roundToInt(),
                                row.yUnits,
                                StitchCommand.STITCH,
                                0
                            )

                        lastX =
                            row.centerX

                        lastY =
                            row.yUnits
                                .toFloat()
                    }
                }

            val last =
                output.last()

            output +=
                EmbroideryPoint(
                    last.xUnits,
                    last.yUnits,
                    StitchCommand.TRIM,
                    0
                )
        }

        val pull =
            options
                .satinPullCompensationMm *
                10f

        val first =
            rows.first()

        val firstLeft =
            (
                first.leftUnits -
                    pull
                ).roundToInt()

        val firstRight =
            (
                first.rightUnits +
                    pull
                ).roundToInt()

        output +=
            EmbroideryPoint(
                firstLeft,
                first.yUnits,
                StitchCommand.JUMP,
                0
            )

        appendSplitStitch(
            output =
                output,
            fromX =
                firstLeft,
            fromY =
                first.yUnits,
            toX =
                firstRight,
            toY =
                first.yUnits,
            maxLengthUnits =
                80f
        )

        var currentX =
            firstRight

        var currentY =
            first.yUnits

        rows.drop(1)
            .forEachIndexed {
                    index,
                    row ->
                val left =
                    (
                        row.leftUnits -
                            pull
                        ).roundToInt()

                val right =
                    (
                        row.rightUnits +
                            pull
                        ).roundToInt()

                val targetX =
                    if (
                        index %
                            2 ==
                            0
                    ) {
                        left
                    } else {
                        right
                    }

                appendSplitStitch(
                    output =
                        output,
                    fromX =
                        currentX,
                    fromY =
                        currentY,
                    toX =
                        targetX,
                    toY =
                        row.yUnits,
                    maxLengthUnits =
                        80f
                )

                currentX =
                    targetX

                currentY =
                    row.yUnits
            }
    }

    private fun thinMask(
        mask: BooleanArray,
        width: Int,
        height: Int
    ): BooleanArray {
        val result =
            mask.copyOf()

        var changed =
            true

        var pass =
            0

        while (
            changed &&
            pass <
                120
        ) {
            changed =
                false

            repeat(2) {
                    phase ->
                val remove =
                    mutableListOf<Int>()

                for (
                    y in
                        1 until
                            height -
                                1
                ) {
                    for (
                        x in
                            1 until
                                width -
                                    1
                    ) {
                        val index =
                            y *
                                width +
                                x

                        if (
                            !result[index]
                        ) {
                            continue
                        }

                        val neighbors =
                            booleanArrayOf(
                                result[
                                    index -
                                        width
                                ],
                                result[
                                    index -
                                        width +
                                        1
                                ],
                                result[
                                    index +
                                        1
                                ],
                                result[
                                    index +
                                        width +
                                        1
                                ],
                                result[
                                    index +
                                        width
                                ],
                                result[
                                    index +
                                        width -
                                        1
                                ],
                                result[
                                    index -
                                        1
                                ],
                                result[
                                    index -
                                        width -
                                        1
                                ]
                            )

                        val count =
                            neighbors.count {
                                it
                            }

                        if (
                            count !in
                                2..6
                        ) {
                            continue
                        }

                        var transitions =
                            0

                        for (
                            neighborIndex in
                                neighbors.indices
                        ) {
                            val current =
                                neighbors[
                                    neighborIndex
                                ]

                            val next =
                                neighbors[
                                    (
                                        neighborIndex +
                                            1
                                        ) %
                                        neighbors.size
                                ]

                            if (
                                !current &&
                                next
                            ) {
                                transitions++
                            }
                        }

                        if (
                            transitions !=
                                1
                        ) {
                            continue
                        }

                        val p2 =
                            neighbors[0]
                        val p4 =
                            neighbors[2]
                        val p6 =
                            neighbors[4]
                        val p8 =
                            neighbors[6]

                        val blocked =
                            if (
                                phase ==
                                    0
                            ) {
                                (
                                    p2 &&
                                        p4 &&
                                        p6
                                    ) ||
                                    (
                                        p4 &&
                                            p6 &&
                                            p8
                                        )
                            } else {
                                (
                                    p2 &&
                                        p4 &&
                                        p8
                                    ) ||
                                    (
                                        p2 &&
                                            p6 &&
                                            p8
                                        )
                            }

                        if (
                            !blocked
                        ) {
                            remove +=
                                index
                        }
                    }
                }

                if (
                    remove.isNotEmpty()
                ) {
                    changed =
                        true

                    remove.forEach {
                        result[it] =
                            false
                    }
                }
            }

            pass++
        }

        return result
    }

    private fun traceSkeleton(
        skeleton: BooleanArray,
        width: Int,
        height: Int
    ): List<List<SkeletonPoint>> {
        fun neighbors(
            index: Int
        ): List<Int> {
            val x =
                index %
                    width
            val y =
                index /
                    width

            val result =
                mutableListOf<Int>()

            for (
                dy in
                    -1..1
            ) {
                for (
                    dx in
                        -1..1
                ) {
                    if (
                        dx ==
                            0 &&
                        dy ==
                            0
                    ) {
                        continue
                    }

                    val nx =
                        x +
                            dx
                    val ny =
                        y +
                            dy

                    if (
                        nx in
                            0 until
                                width &&
                        ny in
                            0 until
                                height
                    ) {
                        val candidate =
                            ny *
                                width +
                                nx

                        if (
                            skeleton[
                                candidate
                            ]
                        ) {
                            result +=
                                candidate
                        }
                    }
                }
            }

            return result
        }

        fun edgeKey(
            first: Int,
            second: Int
        ): Long {
            val low =
                minOf(
                    first,
                    second
                )
            val high =
                maxOf(
                    first,
                    second
                )

            return (
                low.toLong()
                    shl
                    32
                ) or
                (
                    high.toLong() and
                        0xffffffffL
                    )
        }

        val active =
            skeleton.indices
                .filter {
                    skeleton[it]
                }

        val degree =
            HashMap<
                Int,
                Int
            >()

        active.forEach {
                degree[it] =
                    neighbors(it)
                        .size
            }

        val usedEdges =
            hashSetOf<Long>()

        val lines =
            mutableListOf<
                List<SkeletonPoint>
            >()

        fun trace(
            start: Int,
            firstNext: Int
        ): List<SkeletonPoint> {
            val line =
                mutableListOf<Int>()

            line +=
                start

            var previous =
                start

            var current =
                firstNext

            usedEdges +=
                edgeKey(
                    previous,
                    current
                )

            line +=
                current

            var guard =
                0

            while (
                guard <
                    active.size
            ) {
                guard++

                val nextCandidates =
                    neighbors(
                        current
                    )
                        .filter {
                            it !=
                                previous &&
                                edgeKey(
                                    current,
                                    it
                                ) !in
                                usedEdges
                        }

                if (
                    nextCandidates
                        .isEmpty()
                ) {
                    break
                }

                if (
                    degree[current] !=
                        2 &&
                    current !=
                        firstNext
                ) {
                    break
                }

                val next =
                    nextCandidates
                        .minByOrNull {
                            candidate ->
                            val cx =
                                current %
                                    width
                            val cy =
                                current /
                                    width
                            val px =
                                previous %
                                    width
                            val py =
                                previous /
                                    width
                            val nx =
                                candidate %
                                    width
                            val ny =
                                candidate /
                                    width

                            val inX =
                                cx -
                                    px
                            val inY =
                                cy -
                                    py
                            val outX =
                                nx -
                                    cx
                            val outY =
                                ny -
                                    cy

                            -(
                                inX *
                                    outX +
                                    inY *
                                        outY
                                )
                        } ?: break

                usedEdges +=
                    edgeKey(
                        current,
                        next
                    )

                previous =
                    current
                current =
                    next
                line +=
                    current
            }

            return line.map {
                    index ->
                SkeletonPoint(
                    x =
                        index %
                            width,
                    y =
                        index /
                            width
                )
            }
        }

        active
            .filter {
                (
                    degree[it]
                        ?: 0
                    ) !=
                    2 &&
                    (
                        degree[it]
                            ?: 0
                        ) >
                        0
            }
            .sortedBy {
                it %
                    width
            }
            .forEach {
                    start ->
                neighbors(start)
                    .forEach {
                            next ->
                        if (
                            edgeKey(
                                start,
                                next
                            ) !in
                            usedEdges
                        ) {
                            val line =
                                trace(
                                    start,
                                    next
                                )

                            if (
                                line.size >=
                                    2
                            ) {
                                lines +=
                                    line
                            }
                        }
                    }
            }

        active.forEach {
                start ->
            neighbors(start)
                .forEach {
                        next ->
                    if (
                        edgeKey(
                            start,
                            next
                        ) !in
                        usedEdges
                    ) {
                        val line =
                            trace(
                                start,
                                next
                            )

                        if (
                            line.size >=
                                2
                        ) {
                            lines +=
                                line
                        }
                    }
                }
        }

        return lines
    }

    private fun orientSkeletonLine(
        line: List<SkeletonPoint>
    ): List<SkeletonPoint> {
        if (
            line.size <
                2
        ) {
            return line
        }

        val first =
            line.first()
        val last =
            line.last()

        return if (
            first.x >
                last.x ||
            (
                first.x ==
                    last.x &&
                first.y >
                    last.y
                )
        ) {
            line.asReversed()
        } else {
            line
        }
    }

    private fun orderSkeletonLinesByProximity(
        source:
            List<List<SkeletonPoint>>
    ): List<List<SkeletonPoint>> {
        if (
            source.size <=
                1
        ) {
            return source
        }

        val remaining =
            source
                .map {
                    orientSkeletonLine(
                        it
                    )
                }
                .toMutableList()

        val ordered =
            mutableListOf<
                List<SkeletonPoint>
            >()

        var current =
            remaining
                .minByOrNull {
                    line ->
                    line.firstOrNull()
                        ?.x
                        ?: Int.MAX_VALUE
                }
                ?: return emptyList()

        remaining.remove(
            current
        )

        ordered +=
            current

        while (
            remaining.isNotEmpty()
        ) {
            val end =
                current.last()

            val next =
                remaining
                    .minByOrNull {
                        line ->
                        minOf(
                            pointDistance(
                                end,
                                line.first()
                            ),
                            pointDistance(
                                end,
                                line.last()
                            )
                        )
                    }
                    ?: break

            remaining.remove(
                next
            )

            current =
                if (
                    pointDistance(
                        end,
                        next.last()
                    ) <
                    pointDistance(
                        end,
                        next.first()
                    )
                ) {
                    next.asReversed()
                } else {
                    next
                }

            ordered +=
                current
        }

        return ordered
    }

    private fun pointDistance(
        first: SkeletonPoint,
        second: SkeletonPoint
    ): Double =
        hypot(
            (
                second.x -
                    first.x
                ).toDouble(),
            (
                second.y -
                    first.y
                ).toDouble()
        )

    private fun appendSplitStitch(
        output:
            MutableList<EmbroideryPoint>,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
        maxLengthUnits: Float
    ) {
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

            val x =
                (
                    fromX +
                        dx *
                            ratio
                    ).roundToInt()

            val y =
                (
                    fromY +
                        dy *
                            ratio
                    ).roundToInt()

            val last =
                output.lastOrNull()

            if (
                last?.xUnits ==
                    x &&
                last.yUnits ==
                    y
            ) {
                continue
            }

            output +=
                EmbroideryPoint(
                    xUnits =
                        x,
                    yUnits =
                        y,
                    command =
                        StitchCommand.STITCH,
                    colorIndex =
                        0
                )
        }
    }

    private fun repairSatinSequence(
        output:
            List<EmbroideryPoint>,
        maxLengthUnits:
            Float
    ): MutableList<EmbroideryPoint> {
        val repaired =
            mutableListOf<
                EmbroideryPoint
            >()

        var previous:
            EmbroideryPoint? =
            null

        output.forEach {
                point ->
            when (
                point.command
            ) {
                StitchCommand.STITCH -> {
                    val before =
                        previous

                    if (
                        before ==
                            null ||
                        (
                            before.xUnits ==
                                point.xUnits &&
                            before.yUnits ==
                                point.yUnits
                            )
                    ) {
                        previous =
                            point

                        return@forEach
                    }

                    appendSplitStitch(
                        output =
                            repaired,
                        fromX =
                            before.xUnits,
                        fromY =
                            before.yUnits,
                        toX =
                            point.xUnits,
                        toY =
                            point.yUnits,
                        maxLengthUnits =
                            maxLengthUnits
                    )

                    previous =
                        repaired.lastOrNull()
                            ?: point
                }

                StitchCommand.JUMP -> {
                    val last =
                        repaired.lastOrNull()

                    if (
                        last?.xUnits !=
                            point.xUnits ||
                        last.yUnits !=
                            point.yUnits ||
                        last.command !=
                            StitchCommand.JUMP
                    ) {
                        repaired +=
                            point
                    }

                    previous =
                        point
                }

                StitchCommand.TRIM,
                StitchCommand.STOP,
                StitchCommand.COLOR_CHANGE,
                StitchCommand.SEQUIN,
                StitchCommand.END -> {
                    if (
                        repaired
                            .lastOrNull()
                            ?.command !=
                            point.command
                    ) {
                        repaired +=
                            point
                    }

                    previous =
                        point
                }
            }
        }

        return repaired
    }

    private fun skeletonLineLength(
        line: List<SkeletonPoint>
    ): Float {
        var length =
            0.0

        for (
            index in
                1 until
                    line.size
        ) {
            val first =
                line[
                    index -
                        1
                ]

            val second =
                line[index]

            length +=
                hypot(
                    (
                        second.x -
                            first.x
                        ).toDouble(),
                    (
                        second.y -
                            first.y
                        ).toDouble()
                )
        }

        return length.toFloat()
    }

    private fun smoothSkeletonLine(
        source: List<SkeletonPoint>,
        radius: Int,
        passes: Int
    ): List<SkeletonPoint> {
        if (
            source.size <
                5 ||
            radius <=
                0 ||
            passes <=
                0
        ) {
            return source
        }

        var current =
            source

        repeat(
            passes
        ) {
            current =
                current.mapIndexed {
                        index,
                        point ->
                    if (
                        index ==
                            0 ||
                        index ==
                            current.lastIndex
                    ) {
                        point
                    } else {
                        val start =
                            (
                                index -
                                    radius
                                ).coerceAtLeast(
                                0
                            )

                        val end =
                            (
                                index +
                                    radius
                                ).coerceAtMost(
                                current.lastIndex
                            )

                        var sumX =
                            0

                        var sumY =
                            0

                        var count =
                            0

                        for (
                            neighborIndex in
                                start..end
                        ) {
                            sumX +=
                                current[
                                    neighborIndex
                                ].x

                            sumY +=
                                current[
                                    neighborIndex
                                ].y

                            count++
                        }

                        SkeletonPoint(
                            x =
                                (
                                    sumX.toFloat() /
                                        count
                                    ).roundToInt(),
                            y =
                                (
                                    sumY.toFloat() /
                                        count
                                    ).roundToInt()
                        )
                    }
                }
        }

        return current
            .fold(
                mutableListOf<
                    SkeletonPoint
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
            }
    }

    private fun sampleSkeletonLine(
        line: List<SkeletonPoint>,
        stepPixels: Float
    ): List<SkeletonPoint> {
        if (
            line.size <=
                2
        ) {
            return line
        }

        val result =
            mutableListOf<
                SkeletonPoint
            >()

        result +=
            line.first()

        var accumulated =
            0.0

        var previous =
            line.first()

        for (
            index in
                1 until
                    line.size
        ) {
            val current =
                line[index]

            accumulated +=
                hypot(
                    (
                        current.x -
                            previous.x
                        ).toDouble(),
                    (
                        current.y -
                            previous.y
                        ).toDouble()
                )

            if (
                accumulated >=
                    stepPixels
            ) {
                result +=
                    current
                accumulated =
                    0.0
            }

            previous =
                current
        }

        if (
            result.last() !=
                line.last()
        ) {
            result +=
                line.last()
        }

        return result
    }

    private fun boundaryDistance(
        mask: BooleanArray,
        width: Int,
        height: Int,
        centerX: Double,
        centerY: Double,
        normalX: Double,
        normalY: Double,
        direction: Double
    ): Double {
        var distance =
            0.5

        val limit =
            90.0

        while (
            distance <=
                limit
        ) {
            val x =
                (
                    centerX +
                        normalX *
                            distance *
                            direction
                    ).roundToInt()

            val y =
                (
                    centerY +
                        normalY *
                            distance *
                            direction
                    ).roundToInt()

            if (
                x !in
                    0 until
                        width ||
                y !in
                    0 until
                        height ||
                !mask[
                    y *
                        width +
                        x
                ]
            ) {
                return max(
                    0.0,
                    distance -
                        0.5
                )
            }

            distance +=
                0.5
        }

        return limit
    }

    private fun skeletonXToUnits(
        x: Float,
        minX: Int,
        padding: Int,
        unitsPerPixel: Float
    ): Int =
        (
            minX +
                (
                    x -
                        padding
                    ) *
                    unitsPerPixel
            ).roundToInt()

    private fun skeletonYToUnits(
        y: Float,
        maxY: Int,
        padding: Int,
        unitsPerPixel: Float
    ): Int =
        (
            maxY -
                (
                    y -
                        padding
                    ) *
                    unitsPerPixel
            ).roundToInt()

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
