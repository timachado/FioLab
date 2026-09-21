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
import com.timachado.fiolab.core.embroidery.SatinUnderlayMode
import com.timachado.fiolab.core.embroidery.StitchCommand
import com.timachado.fiolab.core.embroidery.TextMatrixOptions
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Digitalizador de texto TTF/OTF baseado no mesmo pipeline funcional
 * observado no app de referência:
 * contorno do glifo -> polygonize -> scan spans em dois eixos ->
 * escolha da menor largura média -> colunas Satin -> divisão de
 * colunas largas -> underlay/locks -> pontos.
 *
 * A implementação é própria e independente do código do app de referência.
 */
internal object ReferenceImportedFontEngine {

    private const val DEFAULT_SATIN_DENSITY_MM =
        0.4f

    private const val DEFAULT_MAX_SATIN_WIDTH_MM =
        7f

    private const val DEFAULT_PULL_MM =
        0.2f

    private const val LETTER_SPACING_FACTOR =
        0.04f

    private const val MAX_STITCH_UNITS =
        70f

    private const val TRIM_TRAVEL_UNITS =
        50f

    private const val LOCK_UNITS =
        6f

    private data class FPoint(
        val x: Float,
        val y: Float
    )

    private data class Polygon(
        val points: List<FPoint>
    )

    private data class SpanSegment(
        val position: Float,
        val start: Float,
        val end: Float,
        val transposed: Boolean
    ) {
        fun withPull(
            pullUnits: Float
        ): SatinRow =
            if (
                transposed
            ) {
                SatinRow(
                    a =
                        FPoint(
                            position,
                            start -
                                pullUnits
                        ),
                    b =
                        FPoint(
                            position,
                            end +
                                pullUnits
                        )
                )
            } else {
                SatinRow(
                    a =
                        FPoint(
                            start -
                                pullUnits,
                            position
                        ),
                    b =
                        FPoint(
                            end +
                                pullUnits,
                            position
                        )
                )
            }
    }

    private data class SatinRow(
        val a: FPoint,
        val b: FPoint
    )

    private data class SatinColumn(
        val rows:
            MutableList<SatinRow> =
            mutableListOf()
    )

    private data class ActiveColumn(
        val lastSpan: SpanSegment,
        val column: SatinColumn
    )

    private data class GlyphPath(
        val path: Path
    )

    fun generate(
        font: ImportedFont,
        sourceText: String,
        options: TextMatrixOptions,
        filePrefix: String
    ): Result<EmbroideryDesign> =
        runCatching {
            require(
                options.heightMm in
                    4f..60f
            ) {
                "A altura deve ficar entre 4 e 60 mm."
            }

            require(
                options.satinDensityMm >
                    0.05f
            ) {
                "Densidade Satin inválida."
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
                    .uppercase(
                        Locale.ROOT
                    )

            require(
                outputFormat in
                    MatrixConverter
                        .supportedFormats
            ) {
                "Formato de saída inválido."
            }

            val typeface =
                ImportedFontStore
                    .loadTypeface(
                        font
                    )
                    .getOrThrow()

            val targetHeightUnits =
                options.heightMm *
                    10f

            val paint =
                Paint(
                    Paint.ANTI_ALIAS_FLAG
                ).apply {
                    this.typeface =
                        typeface
                    style =
                        Paint.Style.FILL
                    textSize =
                        resolveFontSizeForCapHeight(
                            this,
                            targetHeightUnits
                        )
                }

            val renderableText =
                resolveText(
                    paint =
                        paint,
                    font =
                        font,
                    text =
                        text
                )

            val spacingUnits =
                targetHeightUnits *
                    LETTER_SPACING_FACTOR +
                    options.spacingMm *
                        10f

            val glyphPaths =
                extractGlyphPaths(
                    paint =
                        paint,
                    text =
                        renderableText,
                    spacingUnits =
                        spacingUnits
                )

            require(
                glyphPaths.isNotEmpty()
            ) {
                "A fonte não gerou glifos bordáveis."
            }

            val unionBounds =
                unionBounds(
                    glyphPaths
                )

            require(
                unionBounds.width() >
                    0.5f &&
                unionBounds.height() >
                    0.5f
            ) {
                "A fonte não gerou uma área bordável."
            }

            val centerX =
                unionBounds.centerX()

            val centerY =
                unionBounds.centerY()

            val polygonsByGlyph =
                glyphPaths.map {
                        glyph ->
                    polygonize(
                        path =
                            glyph.path,
                        centerX =
                            centerX,
                        centerY =
                            centerY
                    )
                }

            val guidePoints =
                buildGuidePoints(
                    polygonsByGlyph
                        .flatten()
                )

            val densityMm =
                if (
                    options.satinDensityMm in
                        0.06f..2f
                ) {
                    options.satinDensityMm
                } else {
                    DEFAULT_SATIN_DENSITY_MM
                }

            val pullMm =
                if (
                    options.satinPullCompensationMm in
                        0f..1f
                ) {
                    options.satinPullCompensationMm
                } else {
                    DEFAULT_PULL_MM
                }

            /*
             * O app de referência usa 7 mm como largura máxima Satin.
             * O FioLab antigo usava 2,4 mm como "largura" fixa, o que
             * fragmentava demais letras largas. Aqui a forma da fonte
             * define a largura e este valor atua somente como limite para
             * dividir colunas fisicamente largas.
             */
            val maxSatinWidthMm =
                DEFAULT_MAX_SATIN_WIDTH_MM

            val points =
                mutableListOf<
                    EmbroideryPoint
                >()

            val emitter =
                SatinEmitter(
                    output =
                        points
                )

            polygonsByGlyph
                .forEach {
                        polygons ->
                    val columns =
                        sampleColumns(
                            polygons =
                                polygons,
                            densityMm =
                                densityMm,
                            maxSatinWidthMm =
                                maxSatinWidthMm,
                            pullCompensationMm =
                                pullMm
                        )

                    columns
                        .forEach {
                                column ->
                            emitter.emitColumn(
                                column =
                                    column,
                                includeUnderlay =
                                    options
                                        .satinUnderlayMode !=
                                        SatinUnderlayMode
                                            .NONE,
                                densityMm =
                                    densityMm
                            )
                        }
                }

            require(
                points.any {
                    it.command ==
                        StitchCommand.STITCH
                }
            ) {
                "O texto não produziu pontos — conteúdo sem área bordável."
            }

            val end =
                points.last()

            points +=
                EmbroideryPoint(
                    end.xUnits,
                    end.yUnits,
                    StitchCommand.END,
                    0
                )

            val coordinates =
                points.filter {
                    it.command !=
                        StitchCommand.END
                }

            val bounds =
                EmbroideryBounds(
                    minXUnits =
                        coordinates.minOf {
                            it.xUnits
                        },
                    maxXUnits =
                        coordinates.maxOf {
                            it.xUnits
                        },
                    minYUnits =
                        coordinates.minOf {
                            it.yUnits
                        },
                    maxYUnits =
                        coordinates.maxOf {
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
                        points,
                    bounds =
                        bounds,
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
                    guidePoints =
                        guidePoints,
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

                require(
                    fit.fits
                ) {
                    "A matriz ultrapassa a área segura do bastidor " +
                        options
                            .hoopProfile
                            .displayName +
                        "."
                }
            }

            design
        }

    private fun resolveFontSizeForCapHeight(
        paint: Paint,
        targetCapHeightUnits: Float
    ): Float {
        paint.textSize =
            100f

        val capPath =
            Path()

        val capChar =
            when {
                paint.hasGlyph(
                    "H"
                ) ->
                    "H"

                paint.hasGlyph(
                    "X"
                ) ->
                    "X"

                else ->
                    null
            }

        val capHeight =
            if (
                capChar !=
                    null
            ) {
                paint.getTextPath(
                    capChar,
                    0,
                    capChar.length,
                    0f,
                    0f,
                    capPath
                )

                val bounds =
                    RectF()

                capPath.computeBounds(
                    bounds,
                    true
                )

                bounds.height()
            } else {
                0f
            }

        val fallback =
            abs(
                paint
                    .fontMetrics
                    .ascent
            ) *
                0.72f

        val resolvedCapHeight =
            if (
                capHeight >
                    0.001f
            ) {
                capHeight
            } else {
                fallback
            }
                .coerceAtLeast(
                    1f
                )

        return 100f *
            targetCapHeightUnits /
            resolvedCapHeight
    }

    private fun resolveText(
        paint: Paint,
        font: ImportedFont,
        text: String
    ): String =
        text.map {
                char ->
            if (
                char.isWhitespace()
            ) {
                char.toString()
            } else {
                val original =
                    char.toString()

                if (
                    paint.hasGlyph(
                        original
                    )
                ) {
                    original
                } else {
                    val normalized =
                        Normalizer
                            .normalize(
                                original,
                                Normalizer.Form.NFD
                            )
                            .replace(
                                Regex(
                                    "\\p{M}+"
                                ),
                                ""
                            )

                    if (
                        normalized.isNotBlank() &&
                        paint.hasGlyph(
                            normalized
                        )
                    ) {
                        normalized
                    } else {
                        error(
                            "A fonte " +
                                font.displayName +
                                " não possui o caractere \"" +
                                char +
                                "\"."
                        )
                    }
                }
            }
        }.joinToString(
            ""
        )

    private fun extractGlyphPaths(
        paint: Paint,
        text: String,
        spacingUnits: Float
    ): List<GlyphPath> {
        val paths =
            mutableListOf<
                GlyphPath
            >()

        var penX =
            0f

        text.forEachIndexed {
                index,
                char ->
            val value =
                char.toString()

            if (
                !char.isWhitespace()
            ) {
                val path =
                    Path()

                paint.getTextPath(
                    value,
                    0,
                    value.length,
                    penX,
                    0f,
                    path
                )

                if (
                    !path.isEmpty
                ) {
                    paths +=
                        GlyphPath(
                            path
                        )
                }
            }

            penX +=
                paint.measureText(
                    value
                )

            if (
                index <
                    text.lastIndex
            ) {
                penX +=
                    spacingUnits
            }
        }

        return paths
    }

    private fun unionBounds(
        glyphs: List<GlyphPath>
    ): RectF {
        val union =
            RectF()

        var initialized =
            false

        glyphs.forEach {
                glyph ->
            val bounds =
                RectF()

            glyph.path
                .computeBounds(
                    bounds,
                    true
                )

            if (
                !initialized
            ) {
                union.set(
                    bounds
                )

                initialized =
                    true
            } else {
                union.union(
                    bounds
                )
            }
        }

        return union
    }

    private fun polygonize(
        path: Path,
        centerX: Float,
        centerY: Float
    ): List<Polygon> {
        val measure =
            PathMeasure(
                path,
                true
            )

        val polygons =
            mutableListOf<
                Polygon
            >()

        val position =
            FloatArray(
                2
            )

        do {
            val length =
                measure.length

            if (
                length <=
                    0f
            ) {
                continue
            }

            val sampleCount =
                max(
                    8,
                    ceil(
                        length /
                            2f
                    ).toInt()
                )

            val points =
                mutableListOf<
                    FPoint
                >()

            for (
                index in
                    0 until
                        sampleCount
            ) {
                val distance =
                    length *
                        index /
                        sampleCount

                if (
                    measure.getPosTan(
                        distance,
                        position,
                        null
                    )
                ) {
                    points +=
                        FPoint(
                            x =
                                position[0] -
                                    centerX,
                            y =
                                centerY -
                                    position[1]
                        )
                }
            }

            if (
                points.size >=
                    3
            ) {
                polygons +=
                    Polygon(
                        points
                    )
            }
        } while (
            measure.nextContour()
        )

        return polygons
    }

    private fun sampleColumns(
        polygons: List<Polygon>,
        densityMm: Float,
        maxSatinWidthMm: Float,
        pullCompensationMm: Float
    ): List<SatinColumn> {
        if (
            polygons.isEmpty()
        ) {
            return emptyList()
        }

        val pitchUnits =
            densityMm *
                10f

        val normal =
            scanSpans(
                polygons =
                    polygons,
                pitchUnits =
                    pitchUnits,
                transposed =
                    false
            )

        val transposed =
            scanSpans(
                polygons =
                    polygons,
                pitchUnits =
                    pitchUnits,
                transposed =
                    true
            )

        val normalWidth =
            meanSpanWidth(
                normal
            )

        val transposedWidth =
            meanSpanWidth(
                transposed
            )

        val selected =
            if (
                transposedWidth <
                    normalWidth
            ) {
                transposed
            } else {
                normal
            }

        return buildColumns(
            scanLines =
                selected,
            maxWidthUnits =
                maxSatinWidthMm *
                    10f,
            pullUnits =
                pullCompensationMm *
                    10f
        )
    }

    private fun scanSpans(
        polygons: List<Polygon>,
        pitchUnits: Float,
        transposed: Boolean
    ): List<List<SpanSegment>> {
        val all =
            polygons.flatMap {
                it.points
            }

        if (
            all.isEmpty()
        ) {
            return emptyList()
        }

        fun scanCoordinate(
            point: FPoint
        ): Float =
            if (
                transposed
            ) {
                point.x
            } else {
                point.y
            }

        fun crossCoordinate(
            point: FPoint
        ): Float =
            if (
                transposed
            ) {
                point.y
            } else {
                point.x
            }

        val minimum =
            all.minOf {
                scanCoordinate(
                    it
                )
            }

        val maximum =
            all.maxOf {
                scanCoordinate(
                    it
                )
            }

        val safePitch =
            pitchUnits
                .coerceAtLeast(
                    0.5f
                )

        val result =
            mutableListOf<
                List<SpanSegment>
            >()

        var scan =
            minimum +
                safePitch /
                    2f

        while (
            scan <=
                maximum
        ) {
            val intersections =
                mutableListOf<
                    Float
                >()

            polygons.forEach {
                    polygon ->
                val points =
                    polygon.points

                if (
                    points.size <
                        3
                ) {
                    return@forEach
                }

                for (
                    index in
                        points.indices
                ) {
                    val first =
                        points[
                            index
                        ]

                    val second =
                        points[
                            (
                                index +
                                    1
                                ) %
                                points.size
                        ]

                    val firstScan =
                        scanCoordinate(
                            first
                        )

                    val secondScan =
                        scanCoordinate(
                            second
                        )

                    val crosses =
                        (
                            firstScan <=
                                scan &&
                            secondScan >
                                scan
                            ) ||
                            (
                                secondScan <=
                                    scan &&
                                firstScan >
                                    scan
                                )

                    if (
                        !crosses
                    ) {
                        continue
                    }

                    val ratio =
                        (
                            scan -
                                firstScan
                            ) /
                            (
                                secondScan -
                                    firstScan
                                )

                    intersections +=
                        crossCoordinate(
                            first
                        ) +
                            (
                                crossCoordinate(
                                    second
                                ) -
                                    crossCoordinate(
                                        first
                                    )
                                ) *
                                ratio
                }
            }

            intersections.sort()

            val spans =
                mutableListOf<
                    SpanSegment
                >()

            var index =
                0

            while (
                index +
                    1 <
                    intersections.size
            ) {
                val start =
                    intersections[
                        index
                    ]

                val end =
                    intersections[
                        index +
                            1
                    ]

                if (
                    end -
                        start >=
                        1f
                ) {
                    spans +=
                        SpanSegment(
                            position =
                                scan,
                            start =
                                start,
                            end =
                                end,
                            transposed =
                                transposed
                        )
                }

                index +=
                    2
            }

            result +=
                spans

            scan +=
                safePitch
        }

        return result
    }

    private fun meanSpanWidth(
        lines:
            List<List<SpanSegment>>
    ): Double {
        var count =
            0

        var total =
            0.0

        lines.forEach {
                spans ->
            spans.forEach {
                    span ->
                total +=
                    (
                        span.end -
                            span.start
                        ).toDouble()

                count++
            }
        }

        return if (
            count ==
                0
        ) {
            Double.MAX_VALUE
        } else {
            total /
                count
        }
    }

    private fun buildColumns(
        scanLines:
            List<List<SpanSegment>>,
        maxWidthUnits: Float,
        pullUnits: Float
    ): List<SatinColumn> {
        val finished =
            mutableListOf<
                SatinColumn
            >()

        var active =
            mutableListOf<
                ActiveColumn
            >()

        scanLines.forEach {
                spans ->
            val used =
                BooleanArray(
                    spans.size
                )

            val nextActive =
                mutableListOf<
                    ActiveColumn
                >()

            active.forEach {
                    item ->
                var matchIndex =
                    -1

                for (
                    index in
                        spans.indices
                ) {
                    if (
                        used[
                            index
                        ]
                    ) {
                        continue
                    }

                    val current =
                        spans[
                            index
                        ]

                    if (
                        current.start <=
                            item.lastSpan
                                .end &&
                        current.end >=
                            item.lastSpan
                                .start
                    ) {
                        matchIndex =
                            index

                        break
                    }
                }

                if (
                    matchIndex >=
                        0
                ) {
                    val current =
                        spans[
                            matchIndex
                        ]

                    item.column
                        .rows +=
                        current.withPull(
                            pullUnits
                        )

                    used[
                        matchIndex
                    ] =
                        true

                    nextActive +=
                        ActiveColumn(
                            lastSpan =
                                current,
                            column =
                                item.column
                        )
                } else {
                    finished +=
                        item.column
                }
            }

            spans.forEachIndexed {
                    index,
                    span ->
                if (
                    !used[
                        index
                    ]
                ) {
                    val column =
                        SatinColumn()

                    column.rows +=
                        span.withPull(
                            pullUnits
                        )

                    nextActive +=
                        ActiveColumn(
                            lastSpan =
                                span,
                            column =
                                column
                        )
                }
            }

            active =
                nextActive
        }

        active.forEach {
            finished +=
                it.column
        }

        return finished
            .filter {
                it.rows
                    .isNotEmpty()
            }
            .flatMap {
                    column ->
                splitWideColumn(
                    column =
                        column,
                    maxWidthUnits =
                        maxWidthUnits
                )
            }
            .sortedWith(
                compareBy<SatinColumn> {
                    it.rows
                        .first()
                        .a
                        .x
                }.thenBy {
                    it.rows
                        .first()
                        .a
                        .y
                }
            )
    }

    private fun splitWideColumn(
        column: SatinColumn,
        maxWidthUnits: Float
    ): List<SatinColumn> {
        val maxFound =
            column.rows
                .maxOfOrNull {
                    distance(
                        it.a,
                        it.b
                    )
                }
                ?: 0f

        if (
            maxFound <=
                maxWidthUnits ||
            maxWidthUnits <=
                0f
        ) {
            return listOf(
                column
            )
        }

        val pieces =
            ceil(
                maxFound /
                    maxWidthUnits
            )
                .toInt()
                .coerceAtLeast(
                    1
                )

        return (
            0 until
                pieces
            ).map {
                    piece ->
                val start =
                    piece.toFloat() /
                        pieces

                val end =
                    (
                        piece +
                            1
                        ).toFloat() /
                        pieces

                SatinColumn(
                    rows =
                        column.rows
                            .map {
                                    row ->
                                SatinRow(
                                    a =
                                        lerp(
                                            row.a,
                                            row.b,
                                            start
                                        ),
                                    b =
                                        lerp(
                                            row.a,
                                            row.b,
                                            end
                                        )
                                )
                            }
                            .toMutableList()
                )
            }
    }

    private class SatinEmitter(
        private val output:
            MutableList<EmbroideryPoint>
    ) {
        private var current:
            FPoint? =
            null

        fun emitColumn(
            column: SatinColumn,
            includeUnderlay: Boolean,
            densityMm: Float
        ) {
            if (
                column.rows
                    .isEmpty()
            ) {
                return
            }

            val first =
                column.rows
                    .first()

            travelTo(
                first.a
            )

            if (
                includeUnderlay &&
                column.rows
                    .size >=
                    4
            ) {
                emitCenterRunUnderlay(
                    column =
                        column,
                    densityMm =
                        densityMm
                )
            }

            emitLock(
                first
            )

            column.rows
                .forEach {
                        row ->
                    stitchTo(
                        row.a
                    )

                    stitchTo(
                        row.b
                    )
                }

            emitLock(
                column.rows
                    .last()
            )
        }

        private fun emitCenterRunUnderlay(
            column: SatinColumn,
            densityMm: Float
        ) {
            val pitchUnits =
                densityMm *
                    10f

            val step =
                max(
                    1,
                    (
                        20f /
                            pitchUnits
                        ).roundToInt()
                )

            val centers =
                mutableListOf<
                    FPoint
                >()

            var index =
                0

            while (
                index <
                    column.rows
                        .size
            ) {
                centers +=
                    center(
                        column.rows[
                            index
                        ]
                    )

                index +=
                    step
            }

            val lastCenter =
                center(
                    column.rows
                        .last()
                )

            if (
                centers.isEmpty() ||
                distance(
                    centers.last(),
                    lastCenter
                ) >
                    0.01f
            ) {
                centers +=
                    lastCenter
            }

            centers.forEach {
                stitchTo(
                    it
                )
            }

            for (
                reverseIndex in
                    centers.size -
                        2 downTo
                        0
            ) {
                stitchTo(
                    centers[
                        reverseIndex
                    ]
                )
            }
        }

        private fun emitLock(
            row: SatinRow
        ) {
            val direction =
                normalize(
                    FPoint(
                        row.b.x -
                            row.a.x,
                        row.b.y -
                            row.a.y
                    )
                )

            val offset =
                FPoint(
                    direction.x *
                        LOCK_UNITS,
                    direction.y *
                        LOCK_UNITS
                )

            stitchTo(
                row.a
            )

            stitchTo(
                FPoint(
                    row.a.x +
                        offset.x,
                    row.a.y +
                        offset.y
                )
            )

            stitchTo(
                row.a
            )
        }

        private fun travelTo(
            target: FPoint
        ) {
            val before =
                current

            if (
                before ==
                    null
            ) {
                emit(
                    target,
                    StitchCommand.JUMP
                )

                return
            }

            val distance =
                distance(
                    before,
                    target
                )

            if (
                distance <
                    0.5f
            ) {
                current =
                    target

                return
            }

            if (
                distance >
                    TRIM_TRAVEL_UNITS
            ) {
                emit(
                    before,
                    StitchCommand.TRIM
                )
            }

            emitSegmented(
                from =
                    before,
                to =
                    target,
                command =
                    StitchCommand.JUMP
            )
        }

        private fun stitchTo(
            target: FPoint
        ) {
            val before =
                current

            if (
                before ==
                    null
            ) {
                emit(
                    target,
                    StitchCommand.JUMP
                )

                return
            }

            if (
                distance(
                    before,
                    target
                ) <
                0.0001f
            ) {
                current =
                    target

                return
            }

            emitSegmented(
                from =
                    before,
                to =
                    target,
                command =
                    StitchCommand.STITCH
            )
        }

        private fun emitSegmented(
            from: FPoint,
            to: FPoint,
            command: StitchCommand
        ) {
            val total =
                distance(
                    from,
                    to
                )

            val segments =
                max(
                    1,
                    ceil(
                        total /
                            MAX_STITCH_UNITS
                    ).toInt()
                )

            for (
                part in
                    1..segments
            ) {
                val ratio =
                    part.toFloat() /
                        segments

                emit(
                    lerp(
                        from,
                        to,
                        ratio
                    ),
                    command
                )
            }
        }

        private fun emit(
            point: FPoint,
            command: StitchCommand
        ) {
            output +=
                EmbroideryPoint(
                    xUnits =
                        point.x
                            .roundToInt(),
                    yUnits =
                        point.y
                            .roundToInt(),
                    command =
                        command,
                    colorIndex =
                        0
                )

            current =
                point
        }
    }

    private fun buildGuidePoints(
        polygons: List<Polygon>
    ): List<EmbroideryPoint> {
        val result =
            mutableListOf<
                EmbroideryPoint
            >()

        polygons.forEach {
                polygon ->
            val first =
                polygon.points
                    .firstOrNull()
                    ?: return@forEach

            if (
                result.isNotEmpty()
            ) {
                val last =
                    result.last()

                result +=
                    EmbroideryPoint(
                        last.xUnits,
                        last.yUnits,
                        StitchCommand.TRIM,
                        0
                    )
            }

            result +=
                EmbroideryPoint(
                    first.x
                        .roundToInt(),
                    first.y
                        .roundToInt(),
                    StitchCommand.JUMP,
                    0
                )

            polygon.points
                .drop(1)
                .forEach {
                        point ->
                    result +=
                        EmbroideryPoint(
                            point.x
                                .roundToInt(),
                            point.y
                                .roundToInt(),
                            StitchCommand.STITCH,
                            0
                        )
                }

            result +=
                EmbroideryPoint(
                    first.x
                        .roundToInt(),
                    first.y
                        .roundToInt(),
                    StitchCommand.STITCH,
                    0
                )
        }

        return result
    }

    private fun center(
        row: SatinRow
    ): FPoint =
        FPoint(
            x =
                (
                    row.a.x +
                        row.b.x
                    ) /
                    2f,
            y =
                (
                    row.a.y +
                        row.b.y
                    ) /
                    2f
        )

    private fun normalize(
        point: FPoint
    ): FPoint {
        val length =
            hypot(
                point.x.toDouble(),
                point.y.toDouble()
            )
                .toFloat()

        return if (
            length <
                0.0001f
        ) {
            FPoint(
                0f,
                0f
            )
        } else {
            FPoint(
                point.x /
                    length,
                point.y /
                    length
            )
        }
    }

    private fun distance(
        first: FPoint,
        second: FPoint
    ): Float =
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
            .toFloat()

    private fun lerp(
        first: FPoint,
        second: FPoint,
        ratio: Float
    ): FPoint =
        FPoint(
            x =
                first.x +
                    (
                        second.x -
                            first.x
                        ) *
                        ratio,
            y =
                first.y +
                    (
                        second.y -
                            first.y
                        ) *
                        ratio
        )

    private fun safeName(
        value: String
    ): String =
        Normalizer
            .normalize(
                value,
                Normalizer.Form.NFD
            )
            .replace(
                Regex(
                    "\\p{M}+"
                ),
                ""
            )
            .replace(
                Regex(
                    "[^A-Za-z0-9_-]+"
                ),
                "_"
            )
            .trim(
                '_'
            )
            .take(
                24
            )
            .ifBlank {
                "texto"
            }
}
