package com.timachado.fiolab.font

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import com.timachado.fiolab.core.embroidery.EmbroideryBounds
import com.timachado.fiolab.core.embroidery.EmbroideryDesign
import com.timachado.fiolab.core.embroidery.EmbroideryPoint
import com.timachado.fiolab.core.embroidery.EmbroideryStressPolicy
import com.timachado.fiolab.core.embroidery.HoopProfile
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
import kotlin.math.floor
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

    private const val CONTINUOUS_CONNECTOR_STITCH_UNITS =
        15f

    private const val GLYPH_JOIN_UNITS =
        12f

    private const val CONNECTOR_SAMPLE_UNITS =
        8f

    /*
     * A escolha do próximo objeto Satin precisa ser mais conservadora
     * que o travel já escolhido. Passos menores impedem que uma diagonal
     * "salte" por cima de um vão estreito entre regiões desconectadas.
     */
    private const val ROUTING_SAMPLE_UNITS =
        2f

    private const val MAX_CONNECTED_ROUTING_CANDIDATES =
        24

    private const val CONNECTOR_EDGE_MARGIN_UNITS =
        2f

    private const val MAX_CONTOUR_SAMPLES =
        50_000

    private const val MAX_POLYGON_SAMPLES_PER_GLYPH =
        200_000

    private const val MAX_SCAN_LINES =
        20_000

    private const val MAX_FONT_GEOMETRY_UNITS =
        100_000f

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

    fun fitHeightToHoopFast(
        font: ImportedFont,
        sourceText: String,
        spacingMm: Float,
        hoop: HoopProfile,
        minHeightMm: Float = 4f,
        maxHeightMm: Float = 60f
    ): Result<Float> =
        runCatching {
            require(
                minHeightMm > 0f &&
                    maxHeightMm >=
                        minHeightMm
            ) {
                "Intervalo de altura inválido."
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

            val typeface =
                ImportedFontStore
                    .loadTypeface(
                        font
                    )
                    .getOrThrow()

            val paint =
                Paint(
                    Paint.ANTI_ALIAS_FLAG
                ).apply {
                    this.typeface =
                        typeface
                    style =
                        Paint.Style.FILL
                }

            fun measure(
                heightMm: Float
            ): RectF {
                val targetHeightUnits =
                    heightMm *
                        10f

                paint.textSize =
                    resolveFontSizeForCapHeight(
                        paint,
                        targetHeightUnits
                    )

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
                        spacingMm *
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

                return unionBounds(
                    glyphPaths
                )
            }

            /*
             * Medimos somente os contornos TTF/OTF. Isso evita executar
             * rasterização, thinning e geração Satin várias vezes durante
             * o auto-fit. A margem de 2% absorve compensação de repuxo e
             * pequenos pontos que ultrapassem o contorno visual.
             */
            val targetWidthUnits =
                hoop.usableWidthMm *
                    10f *
                    0.98f

            val targetHeightUnits =
                hoop.usableHeightMm *
                    10f *
                    0.98f

            fun fits(
                heightMm: Float
            ): Boolean {
                val bounds =
                    measure(
                        heightMm
                    )

                return bounds.width() <=
                    targetWidthUnits &&
                    bounds.height() <=
                        targetHeightUnits
            }

            require(
                fits(
                    minHeightMm
                )
            ) {
                "O texto não cabe na área segura do bastidor " +
                    hoop.displayName +
                    " nem no tamanho mínimo."
            }

            if (
                fits(
                    maxHeightMm
                )
            ) {
                return@runCatching maxHeightMm
            }

            var low =
                minHeightMm

            var high =
                maxHeightMm

            repeat(
                12
            ) {
                val candidate =
                    (
                        low +
                            high
                        ) /
                        2f

                if (
                    fits(
                        candidate
                    )
                ) {
                    low =
                        candidate
                } else {
                    high =
                        candidate
                }
            }

            (
                floor(
                    low *
                        10f
                ) /
                    10f
                ).coerceIn(
                minHeightMm,
                maxHeightMm
            )
        }

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

            require(
                unionBounds.left.isFinite() &&
                    unionBounds.top.isFinite() &&
                    unionBounds.right.isFinite() &&
                    unionBounds.bottom.isFinite() &&
                    unionBounds.width() <=
                        MAX_FONT_GEOMETRY_UNITS &&
                    unionBounds.height() <=
                        MAX_FONT_GEOMETRY_UNITS
            ) {
                "A geometria da fonte é extrema demais para processar com segurança."
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

                    emitter.emitGlyph(
                        columns =
                            columns,
                        polygons =
                            polygons,
                        startHint =
                            glyphVisualStartPoint(
                                polygons
                            ),
                        includeUnderlay =
                            options
                                .satinUnderlayMode !=
                                SatinUnderlayMode
                                    .NONE,
                        densityMm =
                            densityMm
                    )
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

            EmbroideryStressPolicy
                .requireGeneratedSafe(
                    design
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

        var totalSamples =
            0

        do {
            val length =
                measure.length

            if (
                length <=
                    0f
            ) {
                continue
            }

            require(
                length.isFinite()
            ) {
                "A fonte contém um contorno inválido."
            }

            val sampleCount =
                max(
                    8,
                    ceil(
                        length /
                            2f
                    ).toInt()
                )

            require(
                sampleCount <=
                    MAX_CONTOUR_SAMPLES
            ) {
                "Um glifo da fonte possui contorno complexo demais."
            }

            totalSamples +=
                sampleCount

            require(
                totalSamples <=
                    MAX_POLYGON_SAMPLES_PER_GLYPH
            ) {
                "Um glifo da fonte possui detalhes demais para digitalizar com segurança."
            }

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

    private fun glyphVisualStartPoint(
        polygons: List<Polygon>
    ): FPoint? {
        val points =
            polygons.flatMap {
                it.points
            }

        if (
            points.isEmpty()
        ) {
            return null
        }

        /*
         * Path/contour order from TTF/OTF is an implementation detail and
         * does not describe the visual beginning of a letter. For machine
         * embroidery we anchor the first Satin region at the left edge of
         * the glyph; for equal X, prefer the lower point in the Cartesian
         * coordinate system so script/cursive entry strokes start at the
         * natural lower-left side.
         */
        return points.minWithOrNull(
            compareBy<FPoint> {
                it.x
            }.thenBy {
                it.y
            }
        )
    }

    private fun columnLeftEdgeX(
        column: SatinColumn
    ): Float =
        column.rows
            .flatMap {
                    row ->
                listOf(
                    row.a.x,
                    row.b.x
                )
            }
            .minOrNull()
            ?: Float.MAX_VALUE

    private fun columnTopY(
        column: SatinColumn
    ): Float =
        column.rows
            .flatMap {
                    row ->
                listOf(
                    row.a.y,
                    row.b.y
                )
            }
            .maxOrNull()
            ?: -Float.MAX_VALUE

    private fun standardSewingOrder(
        columns: List<SatinColumn>
    ): List<SatinColumn> =
        columns
            .filter {
                it.rows
                    .isNotEmpty()
            }
            .sortedWith(
                compareBy<SatinColumn> {
                    columnLeftEdgeX(
                        it
                    )
                }.thenByDescending {
                    columnTopY(
                        it
                    )
                }
            )

    private data class RasterGlyph(
        val originX: Float,
        val originY: Float,
        val step: Float,
        val width: Int,
        val height: Int,
        val mask: BooleanArray
    ) {
        fun index(
            x: Int,
            y: Int
        ): Int =
            y *
                width +
                x

        fun inside(
            x: Int,
            y: Int
        ): Boolean =
            x in
                0 until width &&
                y in
                    0 until height &&
                mask[
                    index(
                        x,
                        y
                    )
                ]

        fun contains(
            point: FPoint
        ): Boolean {
            val x =
                kotlin.math.floor(
                    (
                        point.x -
                            originX
                        ) /
                        step
                )
                    .toInt()

            val y =
                kotlin.math.floor(
                    (
                        point.y -
                            originY
                        ) /
                        step
                )
                    .toInt()

            return inside(
                x,
                y
            )
        }

        fun center(
            index: Int
        ): FPoint {
            val x =
                index %
                    width

            val y =
                index /
                    width

            return FPoint(
                originX +
                    (
                        x +
                            0.5f
                        ) *
                        step,
                originY +
                    (
                        y +
                            0.5f
                        ) *
                        step
            )
        }
    }

    private const val MAX_ADAPTIVE_RASTER_CELLS =
        360_000

    private const val MAX_ADAPTIVE_THINNING_PASSES =
        256

    private const val ADAPTIVE_RAY_STEP_UNITS =
        0.75f

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

        val adaptive =
            buildAdaptiveSatinBlocks(
                polygons =
                    polygons,
                densityMm =
                    densityMm,
                maxSatinWidthMm =
                    maxSatinWidthMm,
                pullCompensationMm =
                    pullCompensationMm
            )

        if (
            adaptive.isNotEmpty()
        ) {
            return adaptive
        }

        /*
         * Fallback conservador para sinais muito pequenos/compactos em que
         * o eixo medial não produz um percurso estável. O motor antigo fica
         * disponível somente como proteção, não como estratégia principal.
         */
        return sampleColumnsByAxis(
            polygons =
                polygons,
            densityMm =
                densityMm,
            maxSatinWidthMm =
                maxSatinWidthMm,
            pullCompensationMm =
                pullCompensationMm
        )
    }

    private fun buildAdaptiveSatinBlocks(
        polygons: List<Polygon>,
        densityMm: Float,
        maxSatinWidthMm: Float,
        pullCompensationMm: Float
    ): List<SatinColumn> {
        val all =
            polygons.flatMap {
                it.points
            }

        if (
            all.isEmpty()
        ) {
            return emptyList()
        }

        val minX =
            all.minOf {
                it.x
            }

        val maxX =
            all.maxOf {
                it.x
            }

        val minY =
            all.minOf {
                it.y
            }

        val maxY =
            all.maxOf {
                it.y
            }

        val widthUnits =
            maxX -
                minX

        val heightUnits =
            maxY -
                minY

        if (
            widthUnits <
                2f ||
            heightUnits <
                2f
        ) {
            return emptyList()
        }

        val pitchUnits =
            (
                densityMm *
                    10f
                ).coerceAtLeast(
                1f
            )

        var rasterStep =
            (
                pitchUnits /
                    2f
                ).coerceIn(
                1f,
                2.5f
            )

        fun estimatedCells(
            step: Float
        ): Long {
            val width =
                ceil(
                    (
                        widthUnits /
                            step
                        ).toDouble()
                ).toLong() +
                    3L

            val height =
                ceil(
                    (
                        heightUnits /
                            step
                        ).toDouble()
                ).toLong() +
                    3L

            return width *
                height
        }

        val initialCells =
            estimatedCells(
                rasterStep
            )

        if (
            initialCells >
                MAX_ADAPTIVE_RASTER_CELLS
        ) {
            val factor =
                kotlin.math.sqrt(
                    initialCells.toDouble() /
                        MAX_ADAPTIVE_RASTER_CELLS
                )
                    .toFloat()

            rasterStep *=
                factor
        }

        val raster =
            rasterizeGlyph(
                polygons =
                    polygons,
                minX =
                    minX,
                minY =
                    minY,
                maxX =
                    maxX,
                maxY =
                    maxY,
                step =
                    rasterStep
            )

        if (
            raster.mask.none {
                it
            }
        ) {
            return emptyList()
        }

        val skeleton =
            thinMask(
                raster
            )

        val paths =
            traceSkeletonPaths(
                raster =
                    raster,
                skeleton =
                    skeleton
            )
                .filter {
                    it.size >=
                        3
                }

        if (
            paths.isEmpty()
        ) {
            return emptyList()
        }

        val maxWidthUnits =
            maxSatinWidthMm *
                10f

        val pullUnits =
            pullCompensationMm *
                10f

        val rawColumns =
            paths.mapNotNull {
                    path ->
                satinColumnFromSkeletonPath(
                    path =
                        path,
                    raster =
                        raster,
                    polygons =
                        polygons,
                    pitchUnits =
                        pitchUnits,
                    maxRayUnits =
                        max(
                            maxWidthUnits *
                                4f,
                            max(
                                widthUnits,
                                heightUnits
                            ) *
                                1.1f
                        ),
                    pullUnits =
                        pullUnits
                )
            }
                .filter {
                    it.rows.size >=
                        2
                }

        if (
            rawColumns.isEmpty()
        ) {
            return emptyList()
        }

        val rowsTotal =
            rawColumns.sumOf {
                it.rows.size
            }

        /*
         * Um eixo medial degenerado (por exemplo, um ponto isolado em um
         * glifo minúsculo) não deve substituir a varredura estável antiga.
         */
        if (
            rowsTotal <
                3
        ) {
            return emptyList()
        }

        val limited =
            rawColumns.flatMap {
                    column ->
                splitWideColumn(
                    column =
                        column,
                    maxWidthUnits =
                        maxWidthUnits
                )
            }

        return standardSewingOrder(
            limited
        )
    }

    private fun rasterizeGlyph(
        polygons: List<Polygon>,
        minX: Float,
        minY: Float,
        maxX: Float,
        maxY: Float,
        step: Float
    ): RasterGlyph {
        val originX =
            minX -
                step

        val originY =
            minY -
                step

        val width =
            (
                ceil(
                    (
                        (
                            maxX -
                                minX
                            ) /
                            step
                        ).toDouble()
                ).toInt() +
                    3
                ).coerceAtLeast(
                3
            )

        val height =
            (
                ceil(
                    (
                        (
                            maxY -
                                minY
                            ) /
                            step
                        ).toDouble()
                ).toInt() +
                    3
                ).coerceAtLeast(
                3
            )

        require(
            width.toLong() *
                height.toLong() <=
                MAX_ADAPTIVE_RASTER_CELLS
                    .toLong() *
                    2L
        ) {
            "A geometria da fonte ficou complexa demais para o Satin adaptativo."
        }

        val mask =
            BooleanArray(
                width *
                    height
            )

        /*
         * Preenche a máscara por scanline (regra par/ímpar). A versão
         * anterior chamava point-in-polygon para cada célula e, numa fonte
         * detalhada, repetia todos os segmentos do contorno milhares de
         * vezes. Aqui cada aresta é avaliada apenas uma vez por linha.
         */
        for (
            y in
                0 until height
        ) {
            val scanY =
                originY +
                    (
                        y +
                            0.5f
                        ) *
                        step

            val intersections =
                mutableListOf<Float>()

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

                var previous =
                    points.last()

                points.forEach {
                        current ->
                    val crosses =
                        (
                            previous.y <=
                                scanY &&
                            current.y >
                                scanY
                            ) ||
                            (
                                current.y <=
                                    scanY &&
                                previous.y >
                                    scanY
                                )

                    if (
                        crosses
                    ) {
                        val ratio =
                            (
                                scanY -
                                    previous.y
                                ) /
                                (
                                    current.y -
                                        previous.y
                                    )

                        intersections +=
                            previous.x +
                                (
                                    current.x -
                                        previous.x
                                    ) *
                                    ratio
                    }

                    previous =
                        current
                }
            }

            intersections.sort()

            var index =
                0

            while (
                index +
                    1 <
                    intersections.size
            ) {
                val left =
                    intersections[
                        index
                    ]

                val right =
                    intersections[
                        index +
                            1
                    ]

                val startX =
                    ceil(
                        (
                            (
                                left -
                                    originX
                                ) /
                                step -
                                0.5f
                            ).toDouble()
                    )
                        .toInt()
                        .coerceAtLeast(
                            0
                        )

                val endX =
                    kotlin.math.floor(
                        (
                            (
                                right -
                                    originX
                                ) /
                                step -
                                0.5f
                            ).toDouble()
                    )
                        .toInt()
                        .coerceAtMost(
                            width -
                                1
                        )

                if (
                    endX >=
                        startX
                ) {
                    for (
                        x in
                            startX..endX
                    ) {
                        mask[
                            y *
                                width +
                                x
                        ] =
                            true
                    }
                }

                index +=
                    2
            }
        }

        return RasterGlyph(
            originX =
                originX,
            originY =
                originY,
            step =
                step,
            width =
                width,
            height =
                height,
            mask =
                mask
        )
    }

    private fun pointInsideGlyphAdaptive(
        point: FPoint,
        polygons: List<Polygon>
    ): Boolean {
        var inside =
            false

        polygons.forEach {
                polygon ->
            if (
                pointInsidePolygonAdaptive(
                    point =
                        point,
                    polygon =
                        polygon
                )
            ) {
                inside =
                    !inside
            }
        }

        return inside
    }

    private fun pointInsidePolygonAdaptive(
        point: FPoint,
        polygon: Polygon
    ): Boolean {
        val points =
            polygon.points

        if (
            points.size <
                3
        ) {
            return false
        }

        var inside =
            false

        var previous =
            points.last()

        points.forEach {
                current ->
            val crosses =
                (
                    current.y >
                        point.y
                    ) !=
                    (
                        previous.y >
                            point.y
                    )

            if (
                crosses
            ) {
                val denominator =
                    previous.y -
                        current.y

                if (
                    kotlin.math.abs(
                        denominator
                    ) >
                        0.00001f
                ) {
                    val crossX =
                        (
                            previous.x -
                                current.x
                            ) *
                            (
                                point.y -
                                    current.y
                                ) /
                            denominator +
                            current.x

                    if (
                        point.x <
                            crossX
                    ) {
                        inside =
                            !inside
                    }
                }
            }

            previous =
                current
        }

        return inside
    }

    private fun thinMask(
        raster: RasterGlyph
    ): BooleanArray {
        val skeleton =
            raster.mask.copyOf()

        val width =
            raster.width

        val height =
            raster.height

        fun value(
            x: Int,
            y: Int
        ): Boolean =
            x in
                0 until width &&
                y in
                    0 until height &&
                skeleton[
                    y *
                        width +
                        x
                ]

        fun isBoundary(
            index: Int
        ): Boolean {
            if (
                !skeleton[
                    index
                ]
            ) {
                return false
            }

            val x =
                index %
                    width

            val y =
                index /
                    width

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

                    if (
                        !value(
                            x +
                                dx,
                            y +
                                dy
                        )
                    ) {
                        return true
                    }
                }
            }

            return false
        }

        fun shouldRemove(
            index: Int,
            secondPass: Boolean
        ): Boolean {
            if (
                !skeleton[
                    index
                ]
            ) {
                return false
            }

            val x =
                index %
                    width

            val y =
                index /
                    width

            val p2 =
                value(
                    x,
                    y -
                        1
                )

            val p3 =
                value(
                    x +
                        1,
                    y -
                        1
                )

            val p4 =
                value(
                    x +
                        1,
                    y
                )

            val p5 =
                value(
                    x +
                        1,
                    y +
                        1
                )

            val p6 =
                value(
                    x,
                    y +
                        1
                )

            val p7 =
                value(
                    x -
                        1,
                    y +
                        1
                )

            val p8 =
                value(
                    x -
                        1,
                    y
                )

            val p9 =
                value(
                    x -
                        1,
                    y -
                        1
                )

            val neighbors =
                booleanArrayOf(
                    p2,
                    p3,
                    p4,
                    p5,
                    p6,
                    p7,
                    p8,
                    p9
                )

            val count =
                neighbors.count {
                    it
                }

            if (
                count !in
                    2..6
            ) {
                return false
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
                return false
            }

            return if (
                !secondPass
            ) {
                !(
                    p2 &&
                        p4 &&
                        p6
                    ) &&
                    !(
                        p4 &&
                            p6 &&
                            p8
                        )
            } else {
                !(
                    p2 &&
                        p4 &&
                        p8
                    ) &&
                    !(
                        p2 &&
                            p6 &&
                            p8
                        )
            }
        }

        val candidates =
            linkedSetOf<Int>()

        skeleton.indices.forEach {
                index ->
            if (
                isBoundary(
                    index
                )
            ) {
                candidates +=
                    index
            }
        }

        var pass =
            0

        while (
            pass <
                MAX_ADAPTIVE_THINNING_PASSES &&
            candidates.isNotEmpty()
        ) {
            var removedAny =
                false

            for (
                secondPass in
                    listOf(
                        false,
                        true
                    )
            ) {
                val remove =
                    candidates.filter {
                            index ->
                        shouldRemove(
                            index =
                                index,
                            secondPass =
                                secondPass
                        )
                    }

                if (
                    remove.isEmpty()
                ) {
                    continue
                }

                removedAny =
                    true

                val affected =
                    linkedSetOf<Int>()

                remove.forEach {
                        index ->
                    skeleton[
                        index
                    ] =
                        false

                    val x =
                        index %
                            width

                    val y =
                        index /
                            width

                    for (
                        dy in
                            -1..1
                    ) {
                        for (
                            dx in
                                -1..1
                        ) {
                            val nx =
                                x +
                                    dx

                            val ny =
                                y +
                                    dy

                            if (
                                nx in
                                    0 until width &&
                                ny in
                                    0 until height
                            ) {
                                val neighbor =
                                    ny *
                                        width +
                                        nx

                                if (
                                    skeleton[
                                        neighbor
                                    ]
                                ) {
                                    affected +=
                                        neighbor
                                }
                            }
                        }
                    }
                }

                val iterator =
                    candidates.iterator()

                while (
                    iterator.hasNext()
                ) {
                    val index =
                        iterator.next()

                    if (
                        !isBoundary(
                            index
                        )
                    ) {
                        iterator.remove()
                    }
                }

                affected.forEach {
                        index ->
                    if (
                        isBoundary(
                            index
                        )
                    ) {
                        candidates +=
                            index
                    }
                }
            }

            if (
                !removedAny
            ) {
                break
            }

            pass++
        }

        return skeleton
    }

    private fun skeletonNeighbors(
        index: Int,
        raster: RasterGlyph,
        skeleton: BooleanArray
    ): List<Int> {
        val x =
            index %
                raster.width

        val y =
            index /
                raster.width

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
                    nx !in
                        0 until raster.width ||
                    ny !in
                        0 until raster.height
                ) {
                    continue
                }

                val neighbor =
                    ny *
                        raster.width +
                        nx

                if (
                    skeleton[
                        neighbor
                    ]
                ) {
                    result +=
                        neighbor
                }
            }
        }

        return result
    }

    private fun skeletonEdgeKey(
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
            low.toLong() shl
                32
            ) xor
            (
                high.toLong() and
                    0xffffffffL
                )
    }

    private fun traceSkeletonPaths(
        raster: RasterGlyph,
        skeleton: BooleanArray
    ): List<List<Int>> {
        val skeletonIndices =
            skeleton.indices.filter {
                skeleton[
                    it
                ]
            }

        if (
            skeletonIndices.isEmpty()
        ) {
            return emptyList()
        }

        val degree =
            IntArray(
                skeleton.size
            )

        skeletonIndices.forEach {
                index ->
            degree[
                index
            ] =
                skeletonNeighbors(
                    index =
                        index,
                    raster =
                        raster,
                    skeleton =
                        skeleton
                ).size
        }

        val visitedEdges =
            mutableSetOf<Long>()

        fun trace(
            start: Int,
            firstNext: Int
        ): List<Int> {
            val path =
                mutableListOf(
                    start
                )

            var previous =
                start

            var current =
                firstNext

            visitedEdges +=
                skeletonEdgeKey(
                    previous,
                    current
                )

            path +=
                current

            var guard =
                0

            while (
                guard <
                    skeleton.size
            ) {
                if (
                    current !=
                        start &&
                    degree[
                        current
                    ] !=
                        2
                ) {
                    break
                }

                val next =
                    skeletonNeighbors(
                        index =
                            current,
                        raster =
                            raster,
                        skeleton =
                            skeleton
                    )
                        .firstOrNull {
                                neighbor ->
                            neighbor !=
                                previous &&
                                skeletonEdgeKey(
                                    current,
                                    neighbor
                                ) !in
                                visitedEdges
                        }
                    ?: break

                visitedEdges +=
                    skeletonEdgeKey(
                        current,
                        next
                    )

                previous =
                    current

                current =
                    next

                path +=
                    current

                if (
                    current ==
                        start
                ) {
                    break
                }

                guard++
            }

            return path
        }

        val paths =
            mutableListOf<
                List<Int>
            >()

        val nodes =
            skeletonIndices.filter {
                degree[
                    it
                ] !=
                    2
            }

        nodes.forEach {
                node ->
            skeletonNeighbors(
                index =
                    node,
                raster =
                    raster,
                skeleton =
                    skeleton
            ).forEach {
                    neighbor ->
                val edge =
                    skeletonEdgeKey(
                        node,
                        neighbor
                    )

                if (
                    edge in
                        visitedEdges
                ) {
                    return@forEach
                }

                val path =
                    trace(
                        start =
                            node,
                        firstNext =
                            neighbor
                    )

                if (
                    path.size >=
                        2
                ) {
                    paths +=
                        path
                }
            }
        }

        /*
         * Componentes fechados, como anéis e voltas cursivas, podem não
         * possuir endpoint/junção. Percorremos as arestas restantes para
         * preservar essas voltas como blocos Satin curvos.
         */
        skeletonIndices.forEach {
                node ->
            skeletonNeighbors(
                index =
                    node,
                raster =
                    raster,
                skeleton =
                    skeleton
            ).forEach {
                    neighbor ->
                val edge =
                    skeletonEdgeKey(
                        node,
                        neighbor
                    )

                if (
                    edge in
                        visitedEdges
                ) {
                    return@forEach
                }

                val path =
                    trace(
                        start =
                            node,
                        firstNext =
                            neighbor
                    )

                if (
                    path.size >=
                        2
                ) {
                    paths +=
                        path
                }
            }
        }

        return paths
    }

    private fun satinColumnFromSkeletonPath(
        path: List<Int>,
        raster: RasterGlyph,
        polygons: List<Polygon>,
        pitchUnits: Float,
        maxRayUnits: Float,
        pullUnits: Float
    ): SatinColumn? {
        if (
            path.size <
                2
        ) {
            return null
        }

        val centers =
            path.map {
                raster.center(
                    it
                )
            }

        val sampledIndices =
            mutableListOf<Int>()

        var lastAccepted:
            FPoint? =
            null

        centers.forEachIndexed {
                index,
                point ->
            val shouldKeep =
                lastAccepted ==
                    null ||
                    distance(
                        lastAccepted!!,
                        point
                    ) >=
                    pitchUnits *
                        0.82f ||
                    index ==
                        centers.lastIndex

            if (
                shouldKeep
            ) {
                sampledIndices +=
                    index

                lastAccepted =
                    point
            }
        }

        if (
            sampledIndices.size <
                2
        ) {
            return null
        }

        val rows =
            mutableListOf<
                SatinRow
            >()

        var previousRow:
            SatinRow? =
            null

        sampledIndices.forEach {
                centerIndex ->
            val beforeIndex =
                (
                    centerIndex -
                        2
                    ).coerceAtLeast(
                    0
                )

            val afterIndex =
                (
                    centerIndex +
                        2
                    ).coerceAtMost(
                    centers.lastIndex
                )

            val before =
                centers[
                    beforeIndex
                ]

            val after =
                centers[
                    afterIndex
                ]

            val tangent =
                normalize(
                    FPoint(
                        after.x -
                            before.x,
                        after.y -
                            before.y
                    )
                )

            if (
                kotlin.math.abs(
                    tangent.x
                ) <
                    0.0001f &&
                kotlin.math.abs(
                    tangent.y
                ) <
                    0.0001f
            ) {
                return@forEach
            }

            val normal =
                FPoint(
                    -tangent.y,
                    tangent.x
                )

            val center =
                centers[
                    centerIndex
                ]

            if (
                !raster.contains(
                    center
                )
            ) {
                return@forEach
            }

            val positive =
                rayToRasterBoundary(
                    center =
                        center,
                    direction =
                        normal,
                    raster =
                        raster,
                    maxDistance =
                        maxRayUnits
                )

            val negative =
                rayToRasterBoundary(
                    center =
                        center,
                    direction =
                        FPoint(
                            -normal.x,
                            -normal.y
                        ),
                    raster =
                        raster,
                    maxDistance =
                        maxRayUnits
                )

            if (
                positive <=
                    0.25f ||
                negative <=
                    0.25f
            ) {
                return@forEach
            }

            var row =
                SatinRow(
                    a =
                        FPoint(
                            center.x -
                                normal.x *
                                    (
                                        negative +
                                            pullUnits
                                        ),
                            center.y -
                                normal.y *
                                    (
                                        negative +
                                            pullUnits
                                        )
                        ),
                    b =
                        FPoint(
                            center.x +
                                normal.x *
                                    (
                                        positive +
                                            pullUnits
                                        ),
                            center.y +
                                normal.y *
                                    (
                                        positive +
                                            pullUnits
                                        )
                        )
                )

            val previous =
                previousRow

            if (
                previous !=
                    null
            ) {
                val direct =
                    distance(
                        previous.a,
                        row.a
                    ) +
                        distance(
                            previous.b,
                            row.b
                        )

                val swapped =
                    distance(
                        previous.a,
                        row.b
                    ) +
                        distance(
                            previous.b,
                            row.a
                        )

                if (
                    swapped <
                        direct
                ) {
                    row =
                        SatinRow(
                            a =
                                row.b,
                            b =
                                row.a
                        )
                }
            }

            rows +=
                row

            previousRow =
                row
        }

        if (
            rows.size <
                2
        ) {
            return null
        }

        return SatinColumn(
            rows =
                rows
        )
    }

    private fun rayToRasterBoundary(
        center: FPoint,
        direction: FPoint,
        raster: RasterGlyph,
        maxDistance: Float
    ): Float {
        var lastInside =
            0f

        val step =
            max(
                ADAPTIVE_RAY_STEP_UNITS,
                raster.step *
                    0.75f
            )

        var distanceValue =
            step

        while (
            distanceValue <=
                maxDistance
        ) {
            val point =
                FPoint(
                    center.x +
                        direction.x *
                            distanceValue,
                    center.y +
                        direction.y *
                            distanceValue
                )

            if (
                !raster.contains(
                    point
                )
            ) {
                /*
                 * A máscara já tem resolução submilimétrica. Usar o meio
                 * entre o último centro interno e o primeiro externo limita
                 * o erro à metade de uma célula e elimina milhares de testes
                 * point-in-polygon durante a geração de uma palavra.
                 */
                return (
                    lastInside +
                        distanceValue
                    ) /
                    2f
            }

            lastInside =
                distanceValue

            distanceValue +=
                step
        }

        return lastInside
    }

    private fun sampleColumnsByAxis(
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

        val estimatedLines =
            ceil(
                (
                    maximum -
                        minimum
                    ).toDouble() /
                    safePitch
            ).toLong()

        require(
            estimatedLines in
                0..MAX_SCAN_LINES.toLong()
        ) {
            "A geometria da fonte exige varredura complexa demais."
        }

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
                    column ->
                    column.rows
                        .flatMap {
                                row ->
                            listOf(
                                row.a.x,
                                row.b.x
                            )
                        }
                        .minOrNull()
                        ?: Float.MAX_VALUE
                }.thenBy {
                        column ->
                    column.rows
                        .flatMap {
                                row ->
                            listOf(
                                row.a.y,
                                row.b.y
                            )
                        }
                        .minOrNull()
                        ?: Float.MAX_VALUE
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

        private data class OrientedChoice(
            val original: SatinColumn,
            val oriented: SatinColumn,
            val entryDistance: Float
        )

        private data class RoutedChoice(
            val index: Int,
            val oriented: SatinColumn,
            val entryDistance: Float
        )

        fun emitGlyph(
            columns: List<SatinColumn>,
            polygons: List<Polygon>,
            startHint: FPoint?,
            includeUnderlay: Boolean,
            densityMm: Float
        ) {
            val remaining =
                standardSewingOrder(
                    columns
                ).toMutableList()

            if (
                remaining.isEmpty()
            ) {
                return
            }

            var regionIndex =
                0

            while (
                remaining.isNotEmpty()
            ) {
                val firstColumn =
                    regionIndex ==
                        0

                val firstStandard =
                    remaining.first()

                val anchor =
                    if (
                        firstColumn
                    ) {
                        startHint
                            ?: current
                            ?: firstStandard
                                .rows
                                .first()
                                .a
                    } else {
                        current
                            ?: firstStandard
                                .rows
                                .first()
                                .a
                    }

                /*
                 * Comportamento PE-DESIGN-like por continuidade:
                 * - a primeira região continua sendo o início visual
                 *   determinístico da letra;
                 * - depois dela, uma região só pode furar a ordem padrão
                 *   quando existe um caminho direto totalmente escondido
                 *   dentro/na borda do próprio glifo;
                 * - entre as continuações válidas, usamos a entrada mais
                 *   curta; se nenhuma for válida, voltamos à ordem padrão
                 *   e o travelTo emite JUMP sobre o vazio.
                 *
                 * Isso reduz saltos sem costurar diagonais atravessando
                 * buracos ou áreas vazias da letra.
                 */
                val connected =
                    if (
                        !firstColumn &&
                        current !=
                            null
                    ) {
                        closestConnectedChoice(
                            columns =
                                remaining,
                            anchor =
                                current!!,
                            polygons =
                                polygons
                        )
                    } else {
                        null
                    }

                val column =
                    if (
                        connected !=
                            null
                    ) {
                        remaining.removeAt(
                            connected.index
                        )

                        connected.oriented
                    } else {
                        val original =
                            remaining.removeAt(
                                0
                            )

                        closestOrientation(
                            column =
                                original,
                            anchor =
                                anchor
                        ).oriented
                    }

                val entry =
                    column.rows
                        .first()
                        .a

                travelTo(
                    target =
                        entry,
                    polygons =
                        polygons,
                    firstColumn =
                        firstColumn
                )

                if (
                    includeUnderlay
                ) {
                    emitEdgeRunUnderlay(
                        column =
                            column,
                        densityMm =
                            densityMm
                    )
                }

                emitSatinColumn(
                    column
                )

                regionIndex +=
                    1
            }
        }

        private fun closestConnectedChoice(
            columns: List<SatinColumn>,
            anchor: FPoint,
            polygons: List<Polygon>
        ): RoutedChoice? {
            var best:
                RoutedChoice? =
                null

            /*
             * Testar todas as quatro orientações de todas as regiões contra
             * o contorno vetorial vira custo quadrático em fontes detalhadas.
             * Primeiro ranqueamos por distância barata aos quatro possíveis
             * pontos de entrada e só fazemos o teste geométrico caro nos
             * candidatos realmente próximos.
             */
            val nearby =
                columns
                    .mapIndexed {
                            index,
                            column ->
                        Pair(
                            index,
                            minimumEntryDistance(
                                column =
                                    column,
                                anchor =
                                    anchor
                            )
                        )
                    }
                    .sortedBy {
                        it.second
                    }
                    .take(
                        MAX_CONNECTED_ROUTING_CANDIDATES
                    )

            nearby.forEach {
                    indexed ->
                val index =
                    indexed.first

                val column =
                    columns[
                        index
                    ]

                val currentBest =
                    best

                if (
                    currentBest !=
                        null &&
                    indexed.second >
                        currentBest.entryDistance +
                            0.001f
                ) {
                    return@forEach
                }

                orientationCandidates(
                    column
                ).forEach {
                        candidate ->
                    val entry =
                        candidate.rows
                            .firstOrNull()
                            ?.a
                            ?: return@forEach

                    val entryDistance =
                        distance(
                            anchor,
                            entry
                        )

                    val previous =
                        best

                    if (
                        previous !=
                            null &&
                        entryDistance >
                            previous.entryDistance +
                                0.001f
                    ) {
                        return@forEach
                    }

                    if (
                        !segmentInsideGlyph(
                            from =
                                anchor,
                            to =
                                entry,
                            polygons =
                                polygons,
                            sampleUnits =
                                ROUTING_SAMPLE_UNITS
                        )
                    ) {
                        return@forEach
                    }

                    val choice =
                        RoutedChoice(
                            index =
                                index,
                            oriented =
                                candidate,
                            entryDistance =
                                entryDistance
                        )

                    if (
                        previous ==
                            null ||
                        choice.entryDistance <
                            previous.entryDistance -
                                0.001f ||
                        (
                            kotlin.math.abs(
                                choice.entryDistance -
                                    previous.entryDistance
                            ) <=
                                0.001f &&
                            choice.index <
                                previous.index
                            )
                    ) {
                        best =
                            choice
                    }
                }
            }

            return best
        }

        private fun minimumEntryDistance(
            column: SatinColumn,
            anchor: FPoint
        ): Float {
            val first =
                column.rows
                    .firstOrNull()
                    ?: return Float.MAX_VALUE

            val last =
                column.rows
                    .lastOrNull()
                    ?: return Float.MAX_VALUE

            return minOf(
                distance(
                    anchor,
                    first.a
                ),
                distance(
                    anchor,
                    first.b
                ),
                distance(
                    anchor,
                    last.a
                ),
                distance(
                    anchor,
                    last.b
                )
            )
        }

        private fun orientationCandidates(
            column: SatinColumn
        ): List<SatinColumn> {
            val normal =
                column.rows
                    .toList()

            val reversed =
                column.rows
                    .asReversed()

            return listOf(
                orientRows(
                    normal,
                    swap =
                        false
                ),
                orientRows(
                    normal,
                    swap =
                        true
                ),
                orientRows(
                    reversed,
                    swap =
                        false
                ),
                orientRows(
                    reversed,
                    swap =
                        true
                )
            )
        }

        private fun closestOrientation(
            column: SatinColumn,
            anchor: FPoint
        ): OrientedChoice {
            val candidates =
                orientationCandidates(
                    column
                )

            val best =
                candidates.minBy {
                        candidate ->
                    distance(
                        anchor,
                        candidate.rows
                            .first()
                            .a
                    )
                }

            return OrientedChoice(
                original =
                    column,
                oriented =
                    best,
                entryDistance =
                    distance(
                        anchor,
                        best.rows
                            .first()
                            .a
                    )
            )
        }

        private fun orientRows(
            rows: List<SatinRow>,
            swap: Boolean
        ): SatinColumn =
            SatinColumn(
                rows =
                    rows
                        .map {
                                row ->
                            if (
                                swap
                            ) {
                                SatinRow(
                                    a =
                                        row.b,
                                    b =
                                        row.a
                                )
                            } else {
                                row
                            }
                        }
                        .toMutableList()
            )

        private fun emitEdgeRunUnderlay(
            column: SatinColumn,
            densityMm: Float
        ) {
            val rows =
                column.rows

            if (
                rows.size <
                    2
            ) {
                return
            }

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

            val indices =
                mutableListOf<Int>()

            var index =
                0

            while (
                index <
                    rows.size
            ) {
                indices +=
                    index

                index +=
                    step
            }

            if (
                indices.lastOrNull() !=
                    rows.lastIndex
            ) {
                indices +=
                    rows.lastIndex
            }

            // Ida: uma borda da coluna.
            indices.forEach {
                    rowIndex ->
                stitchTo(
                    rows[
                        rowIndex
                    ].a
                )
            }

            // Cruza somente no final.
            stitchTo(
                rows
                    .last()
                    .b
            )

            // Volta: a outra borda, uma única vez.
            for (
                reverseIndex in
                    indices.size -
                        2 downTo
                        0
            ) {
                stitchTo(
                    rows[
                        indices[
                            reverseIndex
                        ]
                    ].b
                )
            }
        }

        private fun emitSatinColumn(
            column: SatinColumn
        ) {
            val rows =
                column.rows

            if (
                rows.isEmpty()
            ) {
                return
            }

            val before =
                current

            var nextIsA =
                if (
                    before ==
                        null
                ) {
                    false
                } else {
                    distance(
                        before,
                        rows.first().a
                    ) >=
                        distance(
                            before,
                            rows.first().b
                        )
                }

            rows.forEach {
                    row ->
                stitchTo(
                    if (
                        nextIsA
                    ) {
                        row.a
                    } else {
                        row.b
                    }
                )

                nextIsA =
                    !nextIsA
            }
        }

        private fun travelTo(
            target: FPoint,
            polygons: List<Polygon>,
            firstColumn: Boolean
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

            val travelDistance =
                distance(
                    before,
                    target
                )

            if (
                travelDistance <
                    0.5f
            ) {
                current =
                    target

                return
            }

            val canHideConnector =
                if (
                    firstColumn
                ) {
                    travelDistance <=
                        GLYPH_JOIN_UNITS
                } else {
                    segmentInsideGlyph(
                        from =
                            before,
                        to =
                            target,
                        polygons =
                            polygons
                    )
                }

            emitSegmented(
                from =
                    before,
                to =
                    target,
                command =
                    if (
                        canHideConnector
                    ) {
                        StitchCommand.STITCH
                    } else {
                        StitchCommand.JUMP
                    },
                maxSegmentUnits =
                    if (
                        canHideConnector
                    ) {
                        CONTINUOUS_CONNECTOR_STITCH_UNITS
                    } else {
                        MAX_STITCH_UNITS
                    }
            )
        }

        private fun segmentInsideGlyph(
            from: FPoint,
            to: FPoint,
            polygons: List<Polygon>,
            sampleUnits: Float =
                CONNECTOR_SAMPLE_UNITS
        ): Boolean {
            if (
                polygons.isEmpty()
            ) {
                return false
            }

            val total =
                distance(
                    from,
                    to
                )

            val samples =
                max(
                    2,
                    ceil(
                        total /
                            sampleUnits
                    ).toInt()
                )

            for (
                part in
                    1 until
                        samples
            ) {
                val ratio =
                    part.toFloat() /
                        samples

                val point =
                    lerp(
                        from,
                        to,
                        ratio
                    )

                if (
                    !pointInsideOrNearGlyph(
                        point =
                            point,
                        polygons =
                            polygons
                    )
                ) {
                    return false
                }
            }

            return true
        }

        private fun pointInsideOrNearGlyph(
            point: FPoint,
            polygons: List<Polygon>
        ): Boolean {
            var inside =
                false

            polygons.forEach {
                    polygon ->
                if (
                    pointInsidePolygon(
                        point =
                            point,
                        polygon =
                            polygon
                    )
                ) {
                    inside =
                        !inside
                }
            }

            if (
                inside
            ) {
                return true
            }

            return polygons.any {
                    polygon ->
                pointNearPolygonEdge(
                    point =
                        point,
                    polygon =
                        polygon,
                    margin =
                        CONNECTOR_EDGE_MARGIN_UNITS
                )
            }
        }

        private fun pointInsidePolygon(
            point: FPoint,
            polygon: Polygon
        ): Boolean {
            val points =
                polygon.points

            if (
                points.size <
                    3
            ) {
                return false
            }

            var inside =
                false

            var previous =
                points.last()

            points.forEach {
                    currentPoint ->
                val crosses =
                    (
                        currentPoint.y >
                            point.y
                        ) !=
                        (
                            previous.y >
                                point.y
                            )

                if (
                    crosses
                ) {
                    val denominator =
                        previous.y -
                            currentPoint.y

                    if (
                        abs(
                            denominator
                        ) >
                            0.00001f
                    ) {
                        val crossingX =
                            (
                                previous.x -
                                    currentPoint.x
                                ) *
                                (
                                    point.y -
                                        currentPoint.y
                                    ) /
                                denominator +
                                currentPoint.x

                        if (
                            point.x <
                                crossingX
                        ) {
                            inside =
                                !inside
                        }
                    }
                }

                previous =
                    currentPoint
            }

            return inside
        }

        private fun pointNearPolygonEdge(
            point: FPoint,
            polygon: Polygon,
            margin: Float
        ): Boolean {
            val points =
                polygon.points

            if (
                points.size <
                    2
            ) {
                return false
            }

            for (
                index in
                    points.indices
            ) {
                val a =
                    points[
                        index
                    ]

                val b =
                    points[
                        (
                            index +
                                1
                            ) %
                            points.size
                    ]

                if (
                    pointToSegmentDistance(
                        point =
                            point,
                        a =
                            a,
                        b =
                            b
                    ) <=
                    margin
                ) {
                    return true
                }
            }

            return false
        }

        private fun pointToSegmentDistance(
            point: FPoint,
            a: FPoint,
            b: FPoint
        ): Float {
            val dx =
                b.x -
                    a.x

            val dy =
                b.y -
                    a.y

            val lengthSquared =
                dx *
                    dx +
                    dy *
                        dy

            if (
                lengthSquared <=
                    0.00001f
            ) {
                return distance(
                    point,
                    a
                )
            }

            val projection =
                (
                    (
                        point.x -
                            a.x
                        ) *
                        dx +
                        (
                            point.y -
                                a.y
                            ) *
                            dy
                    ) /
                    lengthSquared

            val ratio =
                projection
                    .coerceIn(
                        0f,
                        1f
                    )

            val closest =
                FPoint(
                    x =
                        a.x +
                            dx *
                                ratio,
                    y =
                        a.y +
                            dy *
                                ratio
                )

            return distance(
                point,
                closest
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
            command: StitchCommand,
            maxSegmentUnits: Float =
                MAX_STITCH_UNITS
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
                            maxSegmentUnits
                                .coerceAtLeast(
                                    1f
                                )
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
            require(
                point.x.isFinite() &&
                    point.y.isFinite()
            ) {
                "A fonte gerou coordenadas inválidas."
            }

            require(
                output.size <
                    EmbroideryStressPolicy
                        .MAX_GENERATED_COMMANDS
            ) {
                "A fonte gerou pontos demais para processar com segurança no celular."
            }

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

    internal fun debugStandardSewingOrderPath():
        List<EmbroideryPoint> {
        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        val emitter =
            SatinEmitter(
                output
            )

        fun column(
            left: Float,
            bottom: Float
        ) =
            SatinColumn(
                mutableListOf(
                    SatinRow(
                        FPoint(
                            left,
                            bottom
                        ),
                        FPoint(
                            left +
                                12f,
                            bottom
                        )
                    ),
                    SatinRow(
                        FPoint(
                            left,
                            bottom +
                                10f
                        ),
                        FPoint(
                            left +
                                12f,
                            bottom +
                                10f
                        )
                    )
                )
            )

        val left =
            column(
                left =
                    5f,
                bottom =
                    0f
            )

        /*
         * A região do meio está bem longe no eixo Y; a antiga otimização
         * por proximidade preferia a região direita antes dela.
         */
        val middle =
            column(
                left =
                    40f,
                bottom =
                    180f
            )

        val right =
            column(
                left =
                    80f,
                bottom =
                    0f
            )

        emitter.emitGlyph(
            columns =
                listOf(
                    right,
                    middle,
                    left
                ),
            polygons =
                listOf(
                    Polygon(
                        listOf(
                            FPoint(
                                0f,
                                -5f
                            ),
                            FPoint(
                                22f,
                                -5f
                            ),
                            FPoint(
                                22f,
                                15f
                            ),
                            FPoint(
                                0f,
                                15f
                            )
                        )
                    ),
                    Polygon(
                        listOf(
                            FPoint(
                                35f,
                                175f
                            ),
                            FPoint(
                                57f,
                                175f
                            ),
                            FPoint(
                                57f,
                                195f
                            ),
                            FPoint(
                                35f,
                                195f
                            )
                        )
                    ),
                    Polygon(
                        listOf(
                            FPoint(
                                75f,
                                -5f
                            ),
                            FPoint(
                                97f,
                                -5f
                            ),
                            FPoint(
                                97f,
                                15f
                            ),
                            FPoint(
                                75f,
                                15f
                            )
                        )
                    )
                ),
            startHint =
                FPoint(
                    5f,
                    0f
                ),
            includeUnderlay =
                false,
            densityMm =
                0.4f
        )

        return output
    }

    internal fun debugConnectivityAwareSewingPath():
        List<EmbroideryPoint> {
        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        val emitter =
            SatinEmitter(
                output
            )

        fun column(
            left: Float,
            bottom: Float
        ) =
            SatinColumn(
                mutableListOf(
                    SatinRow(
                        FPoint(
                            left,
                            bottom
                        ),
                        FPoint(
                            left +
                                15f,
                            bottom
                        )
                    ),
                    SatinRow(
                        FPoint(
                            left,
                            bottom +
                                10f
                        ),
                        FPoint(
                            left +
                                15f,
                            bottom +
                                10f
                        )
                    )
                )
            )

        val left =
            column(
                left =
                    5f,
                bottom =
                    5f
            )

        /*
         * Pela ordem X, esta região viria em segundo. Porém ela está
         * fisicamente desconectada da faixa inferior.
         */
        val disconnectedMiddle =
            column(
                left =
                    30f,
                bottom =
                    100f
            )

        /*
         * Esta região vem depois no eixo X, mas está conectada ao primeiro
         * objeto pela mesma área bordável e deve ser concluída antes do
         * único salto necessário para a região superior.
         */
        val connectedRight =
            column(
                left =
                    50f,
                bottom =
                    5f
            )

        emitter.emitGlyph(
            columns =
                listOf(
                    disconnectedMiddle,
                    connectedRight,
                    left
                ),
            polygons =
                listOf(
                    Polygon(
                        listOf(
                            FPoint(
                                0f,
                                0f
                            ),
                            FPoint(
                                70f,
                                0f
                            ),
                            FPoint(
                                70f,
                                20f
                            ),
                            FPoint(
                                0f,
                                20f
                            )
                        )
                    ),
                    Polygon(
                        listOf(
                            FPoint(
                                25f,
                                95f
                            ),
                            FPoint(
                                50f,
                                95f
                            ),
                            FPoint(
                                50f,
                                115f
                            ),
                            FPoint(
                                25f,
                                115f
                            )
                        )
                    )
                ),
            startHint =
                FPoint(
                    5f,
                    5f
                ),
            includeUnderlay =
                false,
            densityMm =
                0.4f
        )

        return output
    }

    internal fun debugVisualStartPath():
        List<EmbroideryPoint> {
        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        val emitter =
            SatinEmitter(
                output
            )

        val left =
            SatinColumn(
                mutableListOf(
                    SatinRow(
                        FPoint(
                            5f,
                            0f
                        ),
                        FPoint(
                            20f,
                            0f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            5f,
                            10f
                        ),
                        FPoint(
                            20f,
                            10f
                        )
                    )
                )
            )

        val right =
            SatinColumn(
                mutableListOf(
                    SatinRow(
                        FPoint(
                            70f,
                            0f
                        ),
                        FPoint(
                            85f,
                            0f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            70f,
                            10f
                        ),
                        FPoint(
                            85f,
                            10f
                        )
                    )
                )
            )

        emitter.emitGlyph(
            columns =
                listOf(
                    right,
                    left
                ),
            polygons =
                listOf(
                    Polygon(
                        listOf(
                            FPoint(
                                0f,
                                -5f
                            ),
                            FPoint(
                                90f,
                                -5f
                            ),
                            FPoint(
                                90f,
                                15f
                            ),
                            FPoint(
                                0f,
                                15f
                            )
                        )
                    )
                ),
            startHint =
                FPoint(
                    90f,
                    10f
                ),
            includeUnderlay =
                false,
            densityMm =
                0.4f
        )

        return output
    }

    internal fun debugReferencePath(
        connected: Boolean,
        includeUnderlay: Boolean =
            true
    ): List<EmbroideryPoint> {
        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        val emitter =
            SatinEmitter(
                output
            )

        val first =
            SatinColumn(
                mutableListOf(
                    SatinRow(
                        FPoint(
                            5f,
                            5f
                        ),
                        FPoint(
                            20f,
                            5f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            5f,
                            13f
                        ),
                        FPoint(
                            20f,
                            13f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            5f,
                            21f
                        ),
                        FPoint(
                            20f,
                            21f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            5f,
                            29f
                        ),
                        FPoint(
                            20f,
                            29f
                        )
                    )
                )
            )

        val second =
            SatinColumn(
                mutableListOf(
                    SatinRow(
                        FPoint(
                            34f,
                            5f
                        ),
                        FPoint(
                            49f,
                            5f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            34f,
                            13f
                        ),
                        FPoint(
                            49f,
                            13f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            34f,
                            21f
                        ),
                        FPoint(
                            49f,
                            21f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            34f,
                            29f
                        ),
                        FPoint(
                            49f,
                            29f
                        )
                    )
                )
            )

        val polygons =
            if (
                connected
            ) {
                listOf(
                    Polygon(
                        listOf(
                            FPoint(
                                0f,
                                0f
                            ),
                            FPoint(
                                54f,
                                0f
                            ),
                            FPoint(
                                54f,
                                34f
                            ),
                            FPoint(
                                0f,
                                34f
                            )
                        )
                    )
                )
            } else {
                listOf(
                    Polygon(
                        listOf(
                            FPoint(
                                0f,
                                0f
                            ),
                            FPoint(
                                24f,
                                0f
                            ),
                            FPoint(
                                24f,
                                34f
                            ),
                            FPoint(
                                0f,
                                34f
                            )
                        )
                    ),
                    Polygon(
                        listOf(
                            FPoint(
                                30f,
                                0f
                            ),
                            FPoint(
                                54f,
                                0f
                            ),
                            FPoint(
                                54f,
                                34f
                            ),
                            FPoint(
                                30f,
                                34f
                            )
                        )
                    )
                )
            }

        emitter.emitGlyph(
            columns =
                listOf(
                    first,
                    second
                ),
            polygons =
                polygons,
            startHint =
                FPoint(
                    5f,
                    5f
                ),
            includeUnderlay =
                includeUnderlay,
            densityMm =
                0.4f
        )

        return output
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
            require(
                result.size <=
                    EmbroideryStressPolicy
                        .MAX_GUIDE_COMMANDS
            ) {
                "O contorno da fonte ficou complexo demais para processar com segurança no celular."
            }
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

    internal fun debugAdaptiveRowVectors(
        polygon:
            List<Pair<Float, Float>>,
        densityMm: Float =
            0.4f,
        maxWidthMm: Float =
            7f
    ): List<Pair<Float, Float>> =
        buildAdaptiveSatinBlocks(
            polygons =
                listOf(
                    Polygon(
                        polygon.map {
                            FPoint(
                                it.first,
                                it.second
                            )
                        }
                    )
                ),
            densityMm =
                densityMm,
            maxSatinWidthMm =
                maxWidthMm,
            pullCompensationMm =
                0f
        )
            .flatMap {
                it.rows
            }
            .map {
                    row ->
                Pair(
                    row.b.x -
                        row.a.x,
                    row.b.y -
                        row.a.y
                )
            }

    internal fun debugColumnWidths(
        polygon:
            List<Pair<Float, Float>>,
        densityMm: Float =
            0.4f,
        maxWidthMm: Float =
            7f
    ): List<Float> =
        sampleColumns(
            polygons =
                listOf(
                    Polygon(
                        polygon.map {
                            FPoint(
                                it.first,
                                it.second
                            )
                        }
                    )
                ),
            densityMm =
                densityMm,
            maxSatinWidthMm =
                maxWidthMm,
            pullCompensationMm =
                0f
        )
            .flatMap {
                it.rows
            }
            .map {
                distance(
                    it.a,
                    it.b
                )
            }

    internal fun debugColumnCount(
        polygons:
            List<List<Pair<Float, Float>>>,
        densityMm: Float =
            0.4f,
        maxWidthMm: Float =
            7f
    ): Int =
        sampleColumns(
            polygons =
                polygons.map {
                    points ->
                    Polygon(
                        points.map {
                            FPoint(
                                it.first,
                                it.second
                            )
                        }
                    )
                },
            densityMm =
                densityMm,
            maxSatinWidthMm =
                maxWidthMm,
            pullCompensationMm =
                0f
        )
            .size

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
