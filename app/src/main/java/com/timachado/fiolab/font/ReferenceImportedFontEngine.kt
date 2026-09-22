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
import com.timachado.fiolab.core.embroidery.ImportedFontDigitizingMode
import com.timachado.fiolab.core.embroidery.ImportedFontSewingOrder
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
 * Digitalizador de texto TTF/OTF com implementação própria.
 *
 * Caminho principal das fontes salvas:
 * contorno vetorial do glifo -> máscara geométrica -> eixo medial do traço ->
 * separação de ramos/junções -> linhas locais perpendiculares -> objetos
 * Satin/Tatami -> underlay/locks -> pontos.
 *
 * O pareamento direto de contornos permanece apenas como fallback. Isso
 * evita que letras cursivas com bifurcações unam bordas de traços diferentes
 * e formem leques diagonais no interior do glifo.
 */
internal object ReferenceImportedFontEngine {

    private const val DEFAULT_SATIN_DENSITY_MM =
        0.4f

    private const val DEFAULT_MAX_SATIN_WIDTH_MM =
        7f

    private const val OBJECT_SAMPLING_MAX_WIDTH_MM =
        24f

    private const val DEFAULT_TATAMI_STITCH_LENGTH_MM =
        2.5f

    private const val TATAMI_ROW_PITCH_MM =
        0.30f

    private const val TATAMI_EDGE_OVERLAP_MM =
        0.12f

    private const val TATAMI_STAGGER_FACTOR =
        0.50f

    private const val TATAMI_MIN_WIDE_ROW_RATIO =
        0.25f

    private const val COVERAGE_REPAIR_MAX_GAP_MM =
        0.55f

    private const val COVERAGE_REPAIR_DENSITY_MM =
        0.30f

    private const val COVERAGE_REPAIR_MAX_WIDTH_MM =
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

    private data class ContourRowCandidate(
        val row: SatinRow,
        val center: FPoint,
        val axis: FPoint,
        val width: Float
    )

    private data class RayBoundaryHit(
        val point: FPoint,
        val tangent: FPoint,
        val distance: Float
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
                if (
                    options.importedFontDigitizingMode ==
                        ImportedFontDigitizingMode.PROFESSIONAL_BLOCKS
                ) {
                    OBJECT_SAMPLING_MAX_WIDTH_MM
                } else {
                    DEFAULT_MAX_SATIN_WIDTH_MM
                }

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
                .forEachIndexed {
                        glyphIndex,
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
                                pullMm,
                            digitizingMode =
                                options.importedFontDigitizingMode
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
                            densityMm,
                        strictVisualOrder =
                            options.importedFontSewingOrder ==
                                ImportedFontSewingOrder.VISUAL,
                        endHint =
                            if (
                                glyphIndex ==
                                    polygonsByGlyph.lastIndex
                            ) {
                                glyphVisualEndPoint(
                                    polygons
                                )
                            } else {
                                null
                            }
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
         * Em letras cursivas, o ponto geometricamente mais à esquerda pode
         * ser um floreio inferior. Para o início visual, consideramos a
         * metade superior do glifo e, dentro dela, pegamos o ponto mais à
         * esquerda; em empate, o mais alto.
         *
         * Isso mantém a leitura esquerda -> direita / cima -> baixo sem
         * deixar um swash inferior "roubar" o primeiro bloco da letra.
         */
        val minY =
            points.minOf {
                it.y
            }

        val maxY =
            points.maxOf {
                it.y
            }

        val upperThreshold =
            minY +
                (
                    maxY -
                        minY
                    ) *
                    0.52f

        val upperPoints =
            points.filter {
                it.y >=
                    upperThreshold
            }

        val candidates =
            if (
                upperPoints.isNotEmpty()
            ) {
                upperPoints
            } else {
                points
            }

        return candidates.minWithOrNull(
            compareBy<FPoint> {
                it.x
            }.thenByDescending {
                it.y
            }
        )
    }

    private fun glyphVisualEndPoint(
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

        val minX =
            points.minOf {
                it.x
            }

        val maxX =
            points.maxOf {
                it.x
            }

        val minY =
            points.minOf {
                it.y
            }

        val maxY =
            points.maxOf {
                it.y
            }

        val width =
            (
                maxX -
                    minX
                ).coerceAtLeast(
                1f
            )

        val height =
            (
                maxY -
                    minY
                ).coerceAtLeast(
                1f
            )

        val bodyRightLimit =
            maxX -
                width *
                    0.08f

        val bodyLeftLimit =
            minX +
                width *
                    0.55f

        val lowerLimit =
            minY +
                height *
                    0.58f

        val candidates =
            points.filter {
                it.x >=
                    bodyLeftLimit &&
                    it.x <=
                        bodyRightLimit &&
                    it.y <=
                        lowerLimit
            }

        val pool =
            if (
                candidates.isNotEmpty()
            ) {
                candidates
            } else {
                points.filter {
                    it.y <=
                        lowerLimit
                }
                    .ifEmpty {
                        points
                    }
            }

        val target =
            FPoint(
                maxX -
                    width *
                        0.16f,
                minY +
                    height *
                        0.18f
            )

        return pool.minByOrNull {
            distance(
                it,
                target
            )
        }
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

    private data class CompactProfessionalRegion(
        val column: SatinColumn,
        val minX: Float,
        val maxX: Float,
        val minY: Float,
        val maxY: Float
    ) {
        fun contains(
            point: FPoint,
            margin: Float =
                0f
        ): Boolean =
            point.x >=
                minX -
                    margin &&
                point.x <=
                    maxX +
                        margin &&
                point.y >=
                    minY -
                        margin &&
                point.y <=
                    maxY +
                        margin
    }

    private data class ProfessionalGeometry(
        val raster: RasterGlyph,
        val distanceField: FloatArray,
        val compactRegions:
            List<CompactProfessionalRegion>
    )

    private const val MAX_ADAPTIVE_RASTER_CELLS =
        360_000

    private const val MAX_ADAPTIVE_THINNING_PASSES =
        256

    private const val ADAPTIVE_RAY_STEP_UNITS =
        0.75f

    private const val COMPACT_COMPONENT_MAX_WIDTH_FACTOR =
        0.58f

    private const val JUNCTION_TRIM_MIN_CELLS =
        2

    private const val ADAPTIVE_MAX_RAW_WIDTH_FACTOR =
        1.20f

    private const val MAX_ADAPTIVE_DIRECTION_TURN_DEGREES =
        52f

    private const val MAX_ADAPTIVE_WIDTH_GROWTH_FACTOR =
        1.65f

    private const val BLOCK_RADIUS_CAP_FACTOR =
        1.28f

    private const val BLOCK_RADIUS_SMOOTH_WINDOW =
        2

    private const val JUNCTION_CLEARANCE_FACTOR =
        1.35f

    private const val CONTOUR_PAIR_MIN_TANGENT_DOT =
        0.50f

    private const val CONTOUR_PAIR_MIN_LOCAL_TURN_DOT =
        0.82f

    private const val CONTOUR_CHAIN_MIN_ROW_DOT =
        0.78f

    private const val CONTOUR_CHAIN_MIN_AXIS_DOT =
        0.42f

    private const val CONTOUR_PAIR_MAX_WIDTH_FACTOR =
        1.12f

    private const val CONTOUR_PAIR_PROBE_UNITS =
        0.85f

    private fun buildProfessionalGeometry(
        polygons: List<Polygon>,
        densityMm: Float,
        maxSatinWidthMm: Float,
        pullCompensationMm: Float
    ): ProfessionalGeometry? {
        val all =
            polygons.flatMap {
                it.points
            }

        if (
            all.isEmpty()
        ) {
            return null
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
                1f ||
            heightUnits <
                1f
        ) {
            return null
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
            return null
        }

        val skeleton =
            thinMask(
                raster
            )

        val maxWidthUnits =
            maxSatinWidthMm *
                10f

        val pullUnits =
            pullCompensationMm *
                10f

        val compactRegions =
            splitSkeletonComponents(
                raster =
                    raster,
                skeleton =
                    skeleton
            )
                .mapNotNull {
                        component ->
                    val column =
                        compactComponentColumn(
                            raster =
                                raster,
                            skeletonComponent =
                                component,
                            pitchUnits =
                                pitchUnits,
                            maxWidthUnits =
                                maxWidthUnits,
                            pullUnits =
                                pullUnits
                        )
                            ?: return@mapNotNull null

                    val seed =
                        component.firstOrNull()
                            ?: return@mapNotNull null

                    val filled =
                        filledComponentFromSeed(
                            raster =
                                raster,
                            seed =
                                seed
                        )

                    if (
                        filled.isEmpty()
                    ) {
                        return@mapNotNull null
                    }

                    val xs =
                        filled.map {
                            it %
                                raster.width
                        }

                    val ys =
                        filled.map {
                            it /
                                raster.width
                        }

                    val regionMinX =
                        raster.originX +
                            (
                                xs.minOrNull()!!
                            ) *
                                raster.step

                    val regionMaxX =
                        raster.originX +
                            (
                                xs.maxOrNull()!! +
                                    1
                                ) *
                                raster.step

                    val regionMinY =
                        raster.originY +
                            (
                                ys.minOrNull()!!
                            ) *
                                raster.step

                    val regionMaxY =
                        raster.originY +
                            (
                                ys.maxOrNull()!! +
                                    1
                                ) *
                                raster.step

                    CompactProfessionalRegion(
                        column =
                            column,
                        minX =
                            regionMinX,
                        maxX =
                            regionMaxX,
                        minY =
                            regionMinY,
                        maxY =
                            regionMaxY
                    )
                }

        return ProfessionalGeometry(
            raster =
                raster,
            distanceField =
                buildDistanceField(
                    raster
                ),
            compactRegions =
                compactRegions
        )
    }

    private fun clampProfessionalColumns(
        columns: List<SatinColumn>,
        geometry: ProfessionalGeometry,
        maxSatinWidthMm: Float
    ): List<SatinColumn> {
        val maxWidthUnits =
            maxSatinWidthMm *
                10f

        val filtered =
            columns.filterNot {
                    column ->
                column.rows.any {
                        row ->
                    val center =
                        FPoint(
                            (
                                row.a.x +
                                    row.b.x
                                ) /
                                2f,
                            (
                                row.a.y +
                                    row.b.y
                                ) /
                                2f
                        )

                    geometry.compactRegions.any {
                        it.contains(
                            point =
                                center,
                            margin =
                                geometry.raster.step *
                                    2.5f
                        )
                    }
                }
            }

        val clamped =
            filtered.mapNotNull {
                    column ->
                val rows =
                    column.rows
                        .mapNotNull {
                                row ->
                            val center =
                                FPoint(
                                    (
                                        row.a.x +
                                            row.b.x
                                        ) /
                                        2f,
                                    (
                                        row.a.y +
                                            row.b.y
                                        ) /
                                        2f
                                )

                            val x =
                                kotlin.math.floor(
                                    (
                                        center.x -
                                            geometry.raster.originX
                                        ) /
                                        geometry.raster.step
                                )
                                    .toInt()

                            val y =
                                kotlin.math.floor(
                                    (
                                        center.y -
                                            geometry.raster.originY
                                        ) /
                                        geometry.raster.step
                                )
                                    .toInt()

                            if (
                                x !in
                                    0 until geometry.raster.width ||
                                y !in
                                    0 until geometry.raster.height
                            ) {
                                return@mapNotNull null
                            }

                            val index =
                                y *
                                    geometry.raster.width +
                                    x

                            val radius =
                                geometry.distanceField
                                    .getOrElse(
                                        index
                                    ) {
                                        0f
                                    }

                            if (
                                radius <=
                                    0.1f
                            ) {
                                return@mapNotNull null
                            }

                            val direction =
                                normalize(
                                    FPoint(
                                        row.b.x -
                                            row.a.x,
                                        row.b.y -
                                            row.a.y
                                    )
                                )

                            val width =
                                distance(
                                    row.a,
                                    row.b
                                )

                            val allowedWidth =
                                minOf(
                                    maxWidthUnits,
                                    radius *
                                        2f *
                                        1.35f +
                                        geometry.raster.step
                                )
                                    .coerceAtLeast(
                                        geometry.raster.step
                                    )

                            val half =
                                minOf(
                                    width,
                                    allowedWidth
                                ) /
                                    2f

                            SatinRow(
                                a =
                                    FPoint(
                                        center.x -
                                            direction.x *
                                                half,
                                        center.y -
                                            direction.y *
                                                half
                                    ),
                                b =
                                    FPoint(
                                        center.x +
                                            direction.x *
                                                half,
                                        center.y +
                                            direction.y *
                                                half
                                    )
                            )
                        }
                        .toMutableList()

                if (
                    rows.size >=
                        2
                ) {
                    SatinColumn(
                        rows =
                            rows
                    )
                } else {
                    null
                }
            }
                .toMutableList()

        geometry.compactRegions
            .forEach {
                clamped +=
                    it.column
            }

        return standardSewingOrder(
            clamped
        )
    }

    private fun rowCenter(
        row: SatinRow
    ): FPoint =
        FPoint(
            (
                row.a.x +
                    row.b.x
                ) /
                2f,
            (
                row.a.y +
                    row.b.y
                ) /
                2f
        )

    private fun pointToRowDistance(
        point: FPoint,
        row: SatinRow
    ): Float {
        val dx =
            row.b.x -
                row.a.x

        val dy =
            row.b.y -
                row.a.y

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
                row.a
            )
        }

        val ratio =
            (
                (
                    point.x -
                        row.a.x
                    ) *
                    dx +
                    (
                        point.y -
                            row.a.y
                        ) *
                        dy
                ) /
                lengthSquared

        val clamped =
            ratio.coerceIn(
                0f,
                1f
            )

        return distance(
            point,
            FPoint(
                row.a.x +
                    dx *
                        clamped,
                row.a.y +
                    dy *
                        clamped
            )
        )
    }

    private fun pointCoveredBySatinSweep(
        point: FPoint,
        column: SatinColumn,
        edgeToleranceUnits: Float
    ): Boolean {
        if (
            column.rows.any {
                pointToRowDistance(
                    point =
                        point,
                    row =
                        it
                ) <=
                    edgeToleranceUnits
            }
        ) {
            return true
        }

        return column.rows
            .zipWithNext()
            .any {
                    pair ->
                val sweep =
                    Polygon(
                        listOf(
                            pair.first.a,
                            pair.first.b,
                            pair.second.b,
                            pair.second.a
                        )
                    )

                pointInsidePolygonAdaptive(
                    point =
                        point,
                    polygon =
                        sweep
                )
            }
    }

    private fun buildCoverageRepairColumns(
        polygons: List<Polygon>,
        primary: List<SatinColumn>,
        densityMm: Float,
        pullCompensationMm: Float
    ): List<SatinColumn> {
        if (
            primary.none {
                it.rows.isNotEmpty()
            }
        ) {
            return emptyList()
        }

        val candidates =
            sampleColumnsByAxis(
                polygons =
                    polygons,
                densityMm =
                    minOf(
                        densityMm,
                        COVERAGE_REPAIR_DENSITY_MM
                    ),
                maxSatinWidthMm =
                    COVERAGE_REPAIR_MAX_WIDTH_MM,
                pullCompensationMm =
                    pullCompensationMm
                        .coerceIn(
                            0f,
                            0.25f
                        )
            )

        val maxGapUnits =
            COVERAGE_REPAIR_MAX_GAP_MM *
                10f

        val maxRepairWidthUnits =
            COVERAGE_REPAIR_MAX_WIDTH_MM *
                10f

        val repaired =
            mutableListOf<SatinColumn>()

        candidates.forEach {
                candidate ->
            var run =
                mutableListOf<SatinRow>()

            fun flush() {
                if (
                    run.size >=
                        2
                ) {
                    repaired +=
                        SatinColumn(
                            rows =
                                run
                                    .toMutableList()
                        )
                }

                run =
                    mutableListOf()
            }

            candidate.rows.forEach {
                    row ->
                val center =
                    rowCenter(
                        row
                    )

                val covered =
                    primary.any {
                            column ->
                        pointCoveredBySatinSweep(
                            point =
                                center,
                            column =
                                column,
                            edgeToleranceUnits =
                                maxGapUnits
                        )
                    }

                val localWidth =
                    distance(
                        row.a,
                        row.b
                    )

                val needsRepair =
                    !covered &&
                        localWidth <=
                            maxRepairWidthUnits *
                                1.05f

                if (
                    needsRepair
                ) {
                    run +=
                        row
                } else {
                    flush()
                }
            }

            flush()
        }

        return repaired
    }

    private fun sampleColumns(
        polygons: List<Polygon>,
        densityMm: Float,
        maxSatinWidthMm: Float,
        pullCompensationMm: Float,
        digitizingMode: ImportedFontDigitizingMode =
            ImportedFontDigitizingMode.PROFESSIONAL_BLOCKS
    ): List<SatinColumn> {
        if (
            polygons.isEmpty()
        ) {
            return emptyList()
        }

        val contourPaired =
            buildContourPairedSatinBlocks(
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
            digitizingMode ==
                ImportedFontDigitizingMode.PROFESSIONAL_BLOCKS
        ) {
            /*
             * Motor principal por eixo medial:
             * 1) rasteriza somente a área do glifo;
             * 2) extrai o centro físico de cada traço;
             * 3) separa bifurcações em blocos independentes;
             * 4) abre as travessas perpendicularmente ao caminho local.
             *
             * O pareamento direto de contorno era visualmente atraente em
             * hastes simples, mas podia ligar bordas pertencentes a ramos
             * diferentes em M, N, r e curvas cursivas. Ele fica agora só
             * como fallback quando o eixo medial não produzir geometria
             * utilizável.
             */
            val centerline =
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
                centerline.isNotEmpty()
            ) {
                val repairs =
                    buildCoverageRepairColumns(
                        polygons =
                            polygons,
                        primary =
                            centerline,
                        densityMm =
                            densityMm,
                        pullCompensationMm =
                            pullCompensationMm
                    )

                return if (
                    repairs.isEmpty()
                ) {
                    centerline
                } else {
                    standardSewingOrder(
                        centerline +
                            repairs
                    )
                }
            }

            if (
                contourPaired.isNotEmpty()
            ) {
                return contourPaired
            }

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

        if (
            contourPaired.isNotEmpty()
        ) {
            return contourPaired
        }

        /*
         * Compatibilidade mantém o motor adaptativo legado para fontes que
         * dependiam do eixo medial nas versões anteriores.
         */
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

    private fun cross(
        first: FPoint,
        second: FPoint
    ): Float =
        first.x *
            second.y -
            first.y *
                second.x

    private fun rayBoundaryHit(
        origin: FPoint,
        direction: FPoint,
        polygons: List<Polygon>,
        maxDistance: Float
    ): RayBoundaryHit? {
        var best:
            RayBoundaryHit? =
            null

        polygons.forEach {
                polygon ->
            val points =
                polygon.points

            if (
                points.size <
                    2
            ) {
                return@forEach
            }

            var previous =
                points.last()

            points.forEach {
                    current ->
                val segment =
                    FPoint(
                        current.x -
                            previous.x,
                        current.y -
                            previous.y
                    )

                val denominator =
                    cross(
                        direction,
                        segment
                    )

                if (
                    kotlin.math.abs(
                        denominator
                    ) >
                        0.00001f
                ) {
                    val delta =
                        FPoint(
                            previous.x -
                                origin.x,
                            previous.y -
                                origin.y
                        )

                    val distanceAlongRay =
                        cross(
                            delta,
                            segment
                        ) /
                            denominator

                    val segmentPosition =
                        cross(
                            delta,
                            direction
                        ) /
                            denominator

                    if (
                        distanceAlongRay >
                            CONTOUR_PAIR_PROBE_UNITS *
                                0.35f &&
                        distanceAlongRay <=
                            maxDistance &&
                        segmentPosition >=
                            -0.001f &&
                        segmentPosition <=
                            1.001f
                    ) {
                        val previousBest =
                            best

                        if (
                            previousBest ==
                                null ||
                            distanceAlongRay <
                                previousBest.distance
                        ) {
                            best =
                                RayBoundaryHit(
                                    point =
                                        FPoint(
                                            origin.x +
                                                direction.x *
                                                    distanceAlongRay,
                                            origin.y +
                                                direction.y *
                                                    distanceAlongRay
                                        ),
                                    tangent =
                                        normalize(
                                            segment
                                        ),
                                    distance =
                                        distanceAlongRay
                                )
                        }
                    }
                }

                previous =
                    current
            }
        }

        return best
    }

    private fun chooseInwardNormal(
        point: FPoint,
        tangent: FPoint,
        polygons: List<Polygon>
    ): FPoint? {
        val left =
            FPoint(
                -tangent.y,
                tangent.x
            )

        val right =
            FPoint(
                tangent.y,
                -tangent.x
            )

        val leftInside =
            pointInsideGlyphAdaptive(
                point =
                    FPoint(
                        point.x +
                            left.x *
                                CONTOUR_PAIR_PROBE_UNITS,
                        point.y +
                            left.y *
                                CONTOUR_PAIR_PROBE_UNITS
                    ),
                polygons =
                    polygons
            )

        val rightInside =
            pointInsideGlyphAdaptive(
                point =
                    FPoint(
                        point.x +
                            right.x *
                                CONTOUR_PAIR_PROBE_UNITS,
                        point.y +
                            right.y *
                                CONTOUR_PAIR_PROBE_UNITS
                    ),
                polygons =
                    polygons
            )

        return when {
            leftInside &&
                !rightInside ->
                left

            rightInside &&
                !leftInside ->
                right

            else ->
                null
        }
    }

    private fun segmentInsideGlyphGeometry(
        from: FPoint,
        to: FPoint,
        polygons: List<Polygon>,
        sampleUnits: Float
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
                        sampleUnits.coerceAtLeast(
                            0.5f
                        )
                )
                    .toInt()
            )

        for (
            part in
                0..samples
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
                !pointInsideGlyphAdaptive(
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

    private fun resampleContourForPairing(
        points: List<FPoint>,
        spacingUnits: Float
    ): List<FPoint> {
        if (
            points.size <
                2
        ) {
            return points
        }

        val spacing =
            spacingUnits
                .coerceIn(
                    0.8f,
                    3f
                )

        val result =
            mutableListOf<FPoint>()

        var previous =
            points.last()

        points.forEach {
                current ->
            val length =
                distance(
                    previous,
                    current
                )

            val segments =
                max(
                    1,
                    ceil(
                        (
                            length /
                                spacing
                            ).toDouble()
                    ).toInt()
                )

            for (
                part in
                    0 until segments
            ) {
                val ratio =
                    part.toFloat() /
                        segments

                val point =
                    lerp(
                        previous,
                        current,
                        ratio
                    )

                if (
                    result.lastOrNull()
                        ?.let {
                            distance(
                                it,
                                point
                            ) <
                                0.15f
                        } !=
                        true
                ) {
                    result +=
                        point
                }
            }

            previous =
                current
        }

        return if (
            result.size >
                MAX_POLYGON_SAMPLES_PER_GLYPH
        ) {
            val step =
                ceil(
                    result.size.toDouble() /
                        MAX_POLYGON_SAMPLES_PER_GLYPH
                )
                    .toInt()
                    .coerceAtLeast(
                        1
                    )

            result.filterIndexed {
                    index,
                    _ ->
                index %
                    step ==
                    0
            }
        } else {
            result
        }
    }

    private fun contourPairCandidates(
        polygons: List<Polygon>,
        pitchUnits: Float,
        maxWidthUnits: Float,
        pullUnits: Float
    ): List<ContourRowCandidate> {
        val result =
            mutableListOf<
                ContourRowCandidate
            >()

        polygons.forEach {
                polygon ->
            val points =
                resampleContourForPairing(
                    points =
                        polygon.points,
                    spacingUnits =
                        max(
                            1f,
                            pitchUnits *
                                0.45f
                        )
                )

            if (
                points.size <
                    6
            ) {
                return@forEach
            }

            val sampleStride =
                1

            var index =
                0

            while (
                index <
                    points.size
            ) {
                val before =
                    points[
                        (
                            index -
                                2 +
                                points.size
                            ) %
                            points.size
                    ]

                val point =
                    points[
                        index
                    ]

                val after =
                    points[
                        (
                            index +
                                2
                            ) %
                            points.size
                    ]

                val immediateBefore =
                    points[
                        (
                            index -
                                1 +
                                points.size
                            ) %
                            points.size
                    ]

                val immediateAfter =
                    points[
                        (
                            index +
                                1
                            ) %
                            points.size
                    ]

                val incoming =
                    normalize(
                        FPoint(
                            point.x -
                                immediateBefore.x,
                            point.y -
                                immediateBefore.y
                        )
                    )

                val outgoing =
                    normalize(
                        FPoint(
                            immediateAfter.x -
                                point.x,
                            immediateAfter.y -
                                point.y
                        )
                    )

                val localTurnDot =
                    incoming.x *
                        outgoing.x +
                        incoming.y *
                            outgoing.y

                if (
                    localTurnDot <
                        CONTOUR_PAIR_MIN_LOCAL_TURN_DOT
                ) {
                    index +=
                        sampleStride

                    continue
                }

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
                    index +=
                        sampleStride

                    continue
                }

                val inward =
                    chooseInwardNormal(
                        point =
                            point,
                        tangent =
                            tangent,
                        polygons =
                            polygons
                    )

                if (
                    inward ==
                        null
                ) {
                    index +=
                        sampleStride

                    continue
                }

                val origin =
                    FPoint(
                        point.x +
                            inward.x *
                                CONTOUR_PAIR_PROBE_UNITS *
                                0.55f,
                        point.y +
                            inward.y *
                                CONTOUR_PAIR_PROBE_UNITS *
                                0.55f
                    )

                val hit =
                    rayBoundaryHit(
                        origin =
                            origin,
                        direction =
                            inward,
                        polygons =
                            polygons,
                        maxDistance =
                            maxWidthUnits *
                                CONTOUR_PAIR_MAX_WIDTH_FACTOR
                    )

                if (
                    hit ==
                        null
                ) {
                    index +=
                        sampleStride

                    continue
                }

                val tangentAgreement =
                    kotlin.math.abs(
                        tangent.x *
                            hit.tangent.x +
                            tangent.y *
                                hit.tangent.y
                    )

                if (
                    tangentAgreement <
                        CONTOUR_PAIR_MIN_TANGENT_DOT
                ) {
                    index +=
                        sampleStride

                    continue
                }

                val start =
                    point

                val end =
                    hit.point

                val width =
                    distance(
                        start,
                        end
                    )

                if (
                    width <
                        0.8f ||
                    width >
                        maxWidthUnits *
                            CONTOUR_PAIR_MAX_WIDTH_FACTOR
                ) {
                    index +=
                        sampleStride

                    continue
                }

                val interiorStart =
                    FPoint(
                        start.x +
                            inward.x *
                                0.6f,
                        start.y +
                            inward.y *
                                0.6f
                    )

                val interiorEnd =
                    FPoint(
                        end.x -
                            inward.x *
                                0.6f,
                        end.y -
                            inward.y *
                                0.6f
                    )

                if (
                    !segmentInsideGlyphGeometry(
                        from =
                            interiorStart,
                        to =
                            interiorEnd,
                        polygons =
                            polygons,
                        sampleUnits =
                            1.5f
                    )
                ) {
                    index +=
                        sampleStride

                    continue
                }

                val row =
                    SatinRow(
                        a =
                            FPoint(
                                start.x -
                                    inward.x *
                                        pullUnits,
                                start.y -
                                    inward.y *
                                        pullUnits
                            ),
                        b =
                            FPoint(
                                end.x +
                                    inward.x *
                                        pullUnits,
                                end.y +
                                    inward.y *
                                        pullUnits
                            )
                    )

                val center =
                    FPoint(
                        (
                            row.a.x +
                                row.b.x
                            ) /
                            2f,
                        (
                            row.a.y +
                                row.b.y
                            ) /
                            2f
                    )

                result +=
                    ContourRowCandidate(
                        row =
                            row,
                        center =
                            center,
                        axis =
                            tangent,
                        width =
                            width
                    )

                index +=
                    sampleStride
            }
        }

        return result
    }

    private fun dedupeContourCandidates(
        candidates: List<ContourRowCandidate>,
        pitchUnits: Float
    ): List<ContourRowCandidate> {
        val accepted =
            mutableListOf<
                ContourRowCandidate
            >()

        candidates
            .sortedWith(
                compareBy<ContourRowCandidate> {
                    it.center.x
                }.thenBy {
                    it.center.y
                }
            )
            .forEach {
                    candidate ->
                val candidateDirection =
                    normalize(
                        FPoint(
                            candidate.row.b.x -
                                candidate.row.a.x,
                            candidate.row.b.y -
                                candidate.row.a.y
                        )
                    )

                val duplicate =
                    accepted.any {
                            existing ->
                        if (
                            distance(
                                candidate.center,
                                existing.center
                            ) >
                                pitchUnits *
                                    0.62f
                        ) {
                            false
                        } else {
                            val existingDirection =
                                normalize(
                                    FPoint(
                                        existing.row.b.x -
                                            existing.row.a.x,
                                        existing.row.b.y -
                                            existing.row.a.y
                                    )
                                )

                            kotlin.math.abs(
                                candidateDirection.x *
                                    existingDirection.x +
                                    candidateDirection.y *
                                        existingDirection.y
                            ) >
                                0.90f
                        }
                    }

                if (
                    !duplicate
                ) {
                    accepted +=
                        candidate
                }
            }

        return accepted
    }

    private fun contourCandidatesToColumns(
        candidates: List<ContourRowCandidate>,
        polygons: List<Polygon>,
        pitchUnits: Float
    ): List<SatinColumn> {
        if (
            candidates.isEmpty()
        ) {
            return emptyList()
        }

        val remaining =
            candidates
                .toMutableList()

        val columns =
            mutableListOf<
                SatinColumn
            >()

        while (
            remaining.isNotEmpty()
        ) {
            val seedIndex =
                remaining.indices.minWithOrNull(
                    compareBy<Int> {
                        remaining[
                            it
                        ].center.x
                    }.thenBy {
                        remaining[
                            it
                        ].center.y
                    }
                )
                    ?: 0

            val seed =
                remaining.removeAt(
                    seedIndex
                )

            val rows =
                mutableListOf(
                    seed
                )

            var current =
                seed

            while (
                remaining.isNotEmpty()
            ) {
                val currentDirection =
                    normalize(
                        FPoint(
                            current.row.b.x -
                                current.row.a.x,
                            current.row.b.y -
                                current.row.a.y
                        )
                    )

                var bestIndex =
                    -1

                var bestDistance =
                    Float.MAX_VALUE

                remaining.forEachIndexed {
                        index,
                        candidate ->
                    val centerDistance =
                        distance(
                            current.center,
                            candidate.center
                        )

                    val maximumDistance =
                        max(
                            pitchUnits *
                                3.4f,
                            minOf(
                                current.width,
                                candidate.width
                            ) *
                                0.95f
                        )

                    if (
                        centerDistance >
                            maximumDistance ||
                        centerDistance <=
                            0.001f
                    ) {
                        return@forEachIndexed
                    }

                    val candidateDirection =
                        normalize(
                            FPoint(
                                candidate.row.b.x -
                                    candidate.row.a.x,
                                candidate.row.b.y -
                                    candidate.row.a.y
                            )
                        )

                    val rowDot =
                        kotlin.math.abs(
                            currentDirection.x *
                                candidateDirection.x +
                                currentDirection.y *
                                    candidateDirection.y
                        )

                    if (
                        rowDot <
                            CONTOUR_CHAIN_MIN_ROW_DOT
                    ) {
                        return@forEachIndexed
                    }

                    val displacement =
                        normalize(
                            FPoint(
                                candidate.center.x -
                                    current.center.x,
                                candidate.center.y -
                                    current.center.y
                            )
                        )

                    val currentAxisDot =
                        kotlin.math.abs(
                            displacement.x *
                                current.axis.x +
                                displacement.y *
                                    current.axis.y
                        )

                    val candidateAxisDot =
                        kotlin.math.abs(
                            displacement.x *
                                candidate.axis.x +
                                displacement.y *
                                    candidate.axis.y
                        )

                    if (
                        max(
                            currentAxisDot,
                            candidateAxisDot
                        ) <
                            CONTOUR_CHAIN_MIN_AXIS_DOT
                    ) {
                        return@forEachIndexed
                    }

                    if (
                        !segmentInsideGlyphGeometry(
                            from =
                                current.center,
                            to =
                                candidate.center,
                            polygons =
                                polygons,
                            sampleUnits =
                                1.5f
                        )
                    ) {
                        return@forEachIndexed
                    }

                    if (
                        centerDistance <
                            bestDistance
                    ) {
                        bestDistance =
                            centerDistance

                        bestIndex =
                            index
                    }
                }

                if (
                    bestIndex <
                        0
                ) {
                    break
                }

                current =
                    remaining.removeAt(
                        bestIndex
                    )

                rows +=
                    current
            }

            if (
                rows.size <
                    2
            ) {
                continue
            }

            val stitchedRows =
                mutableListOf<
                    SatinRow
                >()

            rows.forEach {
                    candidate ->
                var row =
                    candidate.row

                val previous =
                    stitchedRows.lastOrNull()

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

                stitchedRows +=
                    row
            }

            val densifiedRows =
                densifyContourRows(
                    rows =
                        stitchedRows,
                    polygons =
                        polygons,
                    pitchUnits =
                        pitchUnits
                )

            val cappedRows =
                addContourEndCaps(
                    rows =
                        densifiedRows,
                    polygons =
                        polygons,
                    pitchUnits =
                        pitchUnits
                )

            if (
                cappedRows.size >=
                    2
            ) {
                columns +=
                    SatinColumn(
                        rows =
                            cappedRows
                    )
            }
        }

        return columns
    }

    private fun addContourEndCaps(
        rows: List<SatinRow>,
        polygons: List<Polygon>,
        pitchUnits: Float
    ): MutableList<SatinRow> {
        if (
            rows.size <
                2
        ) {
            return rows
                .toMutableList()
        }

        fun center(
            row: SatinRow
        ): FPoint =
            FPoint(
                (
                    row.a.x +
                        row.b.x
                    ) /
                    2f,
                (
                    row.a.y +
                        row.b.y
                    ) /
                    2f
            )

        fun shiftedRow(
            edge: SatinRow,
            neighbor: SatinRow,
            outward: Boolean
        ): SatinRow? {
            val edgeCenter =
                center(
                    edge
                )

            val neighborCenter =
                center(
                    neighbor
                )

            val rawDirection =
                if (
                    outward
                ) {
                    FPoint(
                        edgeCenter.x -
                            neighborCenter.x,
                        edgeCenter.y -
                            neighborCenter.y
                    )
                } else {
                    FPoint(
                        neighborCenter.x -
                            edgeCenter.x,
                        neighborCenter.y -
                            edgeCenter.y
                    )
                }

            val direction =
                normalize(
                    rawDirection
                )

            if (
                kotlin.math.abs(
                    direction.x
                ) <
                    0.0001f &&
                kotlin.math.abs(
                    direction.y
                ) <
                    0.0001f
            ) {
                return null
            }

            val neighborGap =
                distance(
                    edgeCenter,
                    neighborCenter
                )

            val shift =
                minOf(
                    pitchUnits *
                        0.55f,
                    neighborGap *
                        0.55f
                )

            if (
                shift <=
                    0.25f
            ) {
                return null
            }

            val candidate =
                SatinRow(
                    a =
                        FPoint(
                            edge.a.x +
                                direction.x *
                                    shift,
                            edge.a.y +
                                direction.y *
                                    shift
                        ),
                    b =
                        FPoint(
                            edge.b.x +
                                direction.x *
                                    shift,
                            edge.b.y +
                                direction.y *
                                    shift
                        )
                )

            val candidateCenter =
                center(
                    candidate
                )

            val innerA =
                lerp(
                    candidate.a,
                    candidateCenter,
                    0.10f
                )

            val innerB =
                lerp(
                    candidate.b,
                    candidateCenter,
                    0.10f
                )

            val centerTravelSafe =
                segmentInsideGlyphGeometry(
                    from =
                        candidateCenter,
                    to =
                        edgeCenter,
                    polygons =
                        polygons,
                    sampleUnits =
                        1.0f
                )

            val rowSafe =
                pointInsideGlyphAdaptive(
                    point =
                        candidateCenter,
                    polygons =
                        polygons
                ) &&
                    segmentInsideGlyphGeometry(
                        from =
                            innerA,
                        to =
                            innerB,
                        polygons =
                            polygons,
                        sampleUnits =
                            1.0f
                    )

            return if (
                centerTravelSafe &&
                rowSafe
            ) {
                candidate
            } else {
                null
            }
        }

        val result =
            rows
                .toMutableList()

        val startCap =
            shiftedRow(
                edge =
                    result.first(),
                neighbor =
                    result[
                        1
                    ],
                outward =
                    true
            )

        if (
            startCap !=
                null
        ) {
            result.add(
                0,
                startCap
            )
        }

        val endCap =
            shiftedRow(
                edge =
                    result.last(),
                neighbor =
                    result[
                        result.lastIndex -
                            1
                    ],
                outward =
                    true
            )

        if (
            endCap !=
                null
        ) {
            result +=
                endCap
        }

        return result
    }

    private fun densifyContourRows(
        rows: List<SatinRow>,
        polygons: List<Polygon>,
        pitchUnits: Float
    ): MutableList<SatinRow> {
        if (
            rows.size <
                2
        ) {
            return rows
                .toMutableList()
        }

        val result =
            mutableListOf<SatinRow>()

        rows.forEachIndexed {
                index,
                row ->
            if (
                index ==
                    0
            ) {
                result +=
                    row

                return@forEachIndexed
            }

            val previous =
                result.last()

            val previousCenter =
                FPoint(
                    (
                        previous.a.x +
                            previous.b.x
                        ) /
                        2f,
                    (
                        previous.a.y +
                            previous.b.y
                        ) /
                        2f
                )

            val currentCenter =
                FPoint(
                    (
                        row.a.x +
                            row.b.x
                        ) /
                        2f,
                    (
                        row.a.y +
                            row.b.y
                        ) /
                        2f
                )

            val gap =
                distance(
                    previousCenter,
                    currentCenter
                )

            if (
                gap >
                    pitchUnits *
                        1.45f
            ) {
                val inserts =
                    (
                        ceil(
                            (
                                gap /
                                    pitchUnits
                                ).toDouble()
                        ).toInt() -
                            1
                        )
                        .coerceIn(
                            1,
                            6
                        )

                for (
                    part in
                        1..inserts
                ) {
                    val ratio =
                        part.toFloat() /
                            (
                                inserts +
                                    1
                                )

                    val interpolated =
                        SatinRow(
                            a =
                                lerp(
                                    previous.a,
                                    row.a,
                                    ratio
                                ),
                            b =
                                lerp(
                                    previous.b,
                                    row.b,
                                    ratio
                                )
                        )

                    val center =
                        FPoint(
                            (
                                interpolated.a.x +
                                    interpolated.b.x
                                ) /
                                2f,
                            (
                                interpolated.a.y +
                                    interpolated.b.y
                                ) /
                                2f
                        )

                    val innerA =
                        lerp(
                            interpolated.a,
                            center,
                            0.08f
                        )

                    val innerB =
                        lerp(
                            interpolated.b,
                            center,
                            0.08f
                        )

                    if (
                        pointInsideGlyphAdaptive(
                            point =
                                center,
                            polygons =
                                polygons
                        ) &&
                        segmentInsideGlyphGeometry(
                            from =
                                innerA,
                            to =
                                innerB,
                            polygons =
                                polygons,
                            sampleUnits =
                                1.25f
                        )
                    ) {
                        result +=
                            interpolated
                    }
                }
            }

            result +=
                row
        }

        return result
    }

    private fun buildContourPairedSatinBlocks(
        polygons: List<Polygon>,
        densityMm: Float,
        maxSatinWidthMm: Float,
        pullCompensationMm: Float
    ): List<SatinColumn> {
        val pitchUnits =
            (
                densityMm *
                    10f
                ).coerceAtLeast(
                1f
            )

        val maxWidthUnits =
            maxSatinWidthMm *
                10f

        val pullUnits =
            pullCompensationMm *
                10f

        val candidates =
            dedupeContourCandidates(
                candidates =
                    contourPairCandidates(
                        polygons =
                            polygons,
                        pitchUnits =
                            pitchUnits,
                        maxWidthUnits =
                            maxWidthUnits,
                        pullUnits =
                            pullUnits
                    ),
                pitchUnits =
                    pitchUnits
            )

        val columns =
            contourCandidatesToColumns(
                candidates =
                    candidates,
                polygons =
                    polygons,
                pitchUnits =
                    pitchUnits
            )

        val rowCount =
            columns.sumOf {
                it.rows.size
            }

        if (
            rowCount <
                3
        ) {
            return emptyList()
        }

        return standardSewingOrder(
            columns
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

        val distanceField =
            buildDistanceField(
                raster
            )

        val maxWidthUnits =
            maxSatinWidthMm *
                10f

        val pullUnits =
            pullCompensationMm *
                10f

        /*
         * Processamos cada componente desconectado separadamente.
         * Isso é importante para pontos/acentos: um ponto do "i" não deve
         * virar uma estrela de pequenos ramos do esqueleto.
         */
        val rawColumns =
            splitSkeletonComponents(
                raster =
                    raster,
                skeleton =
                    skeleton
            )
                .flatMap {
                        component ->
                    val compact =
                        compactComponentColumn(
                            raster =
                                raster,
                            skeletonComponent =
                                component,
                            pitchUnits =
                                pitchUnits,
                            maxWidthUnits =
                                maxWidthUnits,
                            pullUnits =
                                pullUnits
                        )

                    if (
                        compact !=
                            null
                    ) {
                        listOf(
                            compact
                        )
                    } else {
                        val componentSkeleton =
                            BooleanArray(
                                skeleton.size
                            )

                        component.forEach {
                                index ->
                            componentSkeleton[
                                index
                            ] =
                                true
                        }

                        val junctionCenters =
                            findJunctionCenters(
                                raster =
                                    raster,
                                skeleton =
                                    componentSkeleton,
                                distanceField =
                                    distanceField
                            )

                        val blockSkeleton =
                            clearJunctionNeighborhoods(
                                raster =
                                    raster,
                                skeleton =
                                    componentSkeleton,
                                distanceField =
                                    distanceField,
                                pitchUnits =
                                    pitchUnits,
                                junctionCenters =
                                    junctionCenters
                            )

                        val branchColumns =
                            traceSkeletonPaths(
                                raster =
                                    raster,
                                skeleton =
                                    blockSkeleton
                            )
                                .mapNotNull {
                                        path ->
                                    prepareAdaptiveSkeletonPath(
                                        path =
                                            path,
                                        raster =
                                            raster,
                                        skeleton =
                                            blockSkeleton,
                                        pitchUnits =
                                            pitchUnits
                                    )
                                }
                                .flatMap {
                                        path ->
                                    satinColumnsFromSkeletonPath(
                                        path =
                                            path,
                                        raster =
                                            raster,
                                        distanceField =
                                            distanceField,
                                        pitchUnits =
                                            pitchUnits,
                                        maxRayUnits =
                                            max(
                                                maxWidthUnits *
                                                    2f,
                                                max(
                                                    widthUnits,
                                                    heightUnits
                                                ) *
                                                    0.8f
                                            ),
                                        maxSatinWidthUnits =
                                            maxWidthUnits,
                                        pullUnits =
                                            pullUnits
                                    )
                                }
                                .filter {
                                    it.rows.size >=
                                        2
                                }

                        val junctionColumns =
                            junctionCenters
                                .mapNotNull {
                                        junction ->
                                    buildJunctionSatinBlock(
                                        junction =
                                            junction,
                                        raster =
                                            raster,
                                        skeleton =
                                            componentSkeleton,
                                        distanceField =
                                            distanceField,
                                        pitchUnits =
                                            pitchUnits,
                                        maxSatinWidthUnits =
                                            maxWidthUnits,
                                        pullUnits =
                                            pullUnits
                                    )
                                }

                        branchColumns +
                            junctionColumns
                    }
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
                    !skeleton[
                        neighbor
                    ]
                ) {
                    continue
                }

                /*
                 * Em uma grade 8-conectada, um canto pode criar um pequeno
                 * triângulo: o pixel diagonal fica ligado aos dois vizinhos
                 * ortogonais e aparece como uma bifurcação que não existe no
                 * traço real. Isso era uma das origens dos leques nas fontes
                 * salvas. Quando já existe ponte ortogonal, ignoramos a aresta
                 * diagonal redundante.
                 */
                if (
                    dx !=
                        0 &&
                    dy !=
                        0
                ) {
                    val bridgeHorizontal =
                        x +
                            dx in
                            0 until raster.width &&
                        skeleton[
                            y *
                                raster.width +
                                (
                                    x +
                                        dx
                                    )
                        ]

                    val bridgeVertical =
                        y +
                            dy in
                            0 until raster.height &&
                        skeleton[
                            (
                                y +
                                    dy
                                ) *
                                raster.width +
                                x
                        ]

                    if (
                        bridgeHorizontal ||
                        bridgeVertical
                    ) {
                        continue
                    }
                }

                result +=
                    neighbor
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

    private fun buildDistanceField(
        raster: RasterGlyph
    ): FloatArray {
        val width =
            raster.width

        val height =
            raster.height

        val large =
            (
                width +
                    height
                ).toFloat() *
                2f

        val distance =
            FloatArray(
                raster.mask.size
            ) {
                index ->
                if (
                    raster.mask[
                        index
                    ]
                ) {
                    large
                } else {
                    0f
                }
            }

        val diagonal =
            1.41421356f

        for (
            y in
                0 until height
        ) {
            for (
                x in
                    0 until width
            ) {
                val index =
                    y *
                        width +
                        x

                if (
                    !raster.mask[
                        index
                    ]
                ) {
                    continue
                }

                var best =
                    distance[
                        index
                    ]

                if (
                    x >
                        0
                ) {
                    best =
                        minOf(
                            best,
                            distance[
                                index -
                                    1
                            ] +
                                1f
                        )
                }

                if (
                    y >
                        0
                ) {
                    best =
                        minOf(
                            best,
                            distance[
                                index -
                                    width
                            ] +
                                1f
                        )

                    if (
                        x >
                            0
                    ) {
                        best =
                            minOf(
                                best,
                                distance[
                                    index -
                                        width -
                                        1
                                ] +
                                    diagonal
                            )
                    }

                    if (
                        x <
                            width -
                                1
                    ) {
                        best =
                            minOf(
                                best,
                                distance[
                                    index -
                                        width +
                                        1
                                ] +
                                    diagonal
                            )
                    }
                }

                distance[
                    index
                ] =
                    best
            }
        }

        for (
            y in
                height -
                    1 downTo 0
        ) {
            for (
                x in
                    width -
                        1 downTo 0
            ) {
                val index =
                    y *
                        width +
                        x

                if (
                    !raster.mask[
                        index
                    ]
                ) {
                    continue
                }

                var best =
                    distance[
                        index
                    ]

                if (
                    x <
                        width -
                            1
                ) {
                    best =
                        minOf(
                            best,
                            distance[
                                index +
                                    1
                            ] +
                                1f
                        )
                }

                if (
                    y <
                        height -
                            1
                ) {
                    best =
                        minOf(
                            best,
                            distance[
                                index +
                                    width
                            ] +
                                1f
                        )

                    if (
                        x >
                            0
                    ) {
                        best =
                            minOf(
                                best,
                                distance[
                                    index +
                                        width -
                                        1
                                ] +
                                    diagonal
                            )
                    }

                    if (
                        x <
                            width -
                                1
                    ) {
                        best =
                            minOf(
                                best,
                                distance[
                                    index +
                                        width +
                                        1
                                ] +
                                    diagonal
                            )
                    }
                }

                distance[
                    index
                ] =
                    best
            }
        }

        return FloatArray(
            distance.size
        ) {
                index ->
            distance[
                index
            ] *
                raster.step
        }
    }

    private fun findJunctionCenters(
        raster: RasterGlyph,
        skeleton: BooleanArray,
        distanceField: FloatArray
    ): List<Int> {
        val candidateMask =
            BooleanArray(
                skeleton.size
            )

        skeleton.indices.forEach {
                index ->
            candidateMask[
                index
            ] =
                skeleton[
                    index
                ] &&
                    skeletonNeighbors(
                        index =
                            index,
                        raster =
                            raster,
                        skeleton =
                            skeleton
                    ).size >=
                        3
        }

        val visited =
            BooleanArray(
                skeleton.size
            )

        val result =
            mutableListOf<Int>()

        candidateMask.indices.forEach {
                seed ->
            if (
                !candidateMask[
                    seed
                ] ||
                visited[
                    seed
                ]
            ) {
                return@forEach
            }

            val queue =
                java.util.ArrayDeque<Int>()

            val cluster =
                mutableListOf<Int>()

            queue.add(
                seed
            )

            visited[
                seed
            ] =
                true

            while (
                queue.isNotEmpty()
            ) {
                val current =
                    queue.removeFirst()

                cluster +=
                    current

                val x =
                    current %
                        raster.width

                val y =
                    current /
                        raster.width

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
                            candidateMask[
                                neighbor
                            ] &&
                            !visited[
                                neighbor
                            ]
                        ) {
                            visited[
                                neighbor
                            ] =
                                true

                            queue.add(
                                neighbor
                            )
                        }
                    }
                }
            }

            val representative =
                cluster.maxWithOrNull(
                    compareBy<Int> {
                        distanceField
                            .getOrElse(
                                it
                            ) {
                                0f
                            }
                    }.thenByDescending {
                        -it
                    }
                )

            if (
                representative !=
                    null
            ) {
                result +=
                    representative
            }
        }

        return result
    }

    private fun estimateJunctionTangent(
        junction: Int,
        raster: RasterGlyph,
        skeleton: BooleanArray,
        clearanceUnits: Float
    ): FPoint {
        val center =
            raster.center(
                junction
            )

        val minimumRadius =
            max(
                clearanceUnits *
                    0.75f,
                raster.step *
                    2f
            )

        val maximumRadius =
            max(
                clearanceUnits *
                    2.2f,
                minimumRadius +
                    raster.step *
                        4f
            )

        val vectors =
            skeleton.indices
                .asSequence()
                .filter {
                    skeleton[
                        it
                    ]
                }
                .map {
                    raster.center(
                        it
                    )
                }
                .map {
                        point ->
                    FPoint(
                        point.x -
                            center.x,
                        point.y -
                            center.y
                    )
                }
                .filter {
                        vector ->
                    val length =
                        kotlin.math.hypot(
                            vector.x,
                            vector.y
                        )

                    length in
                        minimumRadius..maximumRadius
                }
                .map {
                    normalize(
                        it
                    )
                }
                .toList()

        if (
            vectors.isEmpty()
        ) {
            return FPoint(
                1f,
                0f
            )
        }

        var bestFirst =
            vectors.first()

        var bestSecond =
            FPoint(
                -bestFirst.x,
                -bestFirst.y
            )

        var bestDot =
            1f

        for (
            firstIndex in
                vectors.indices
        ) {
            for (
                secondIndex in
                    firstIndex +
                        1 until vectors.size
            ) {
                val first =
                    vectors[
                        firstIndex
                    ]

                val second =
                    vectors[
                        secondIndex
                    ]

                val dot =
                    first.x *
                        second.x +
                        first.y *
                            second.y

                if (
                    dot <
                        bestDot
                ) {
                    bestDot =
                        dot

                    bestFirst =
                        first

                    bestSecond =
                        second
                }
            }
        }

        val tangent =
            normalize(
                FPoint(
                    bestFirst.x -
                        bestSecond.x,
                    bestFirst.y -
                        bestSecond.y
                )
            )

        return if (
            kotlin.math.abs(
                tangent.x
            ) <
                0.0001f &&
            kotlin.math.abs(
                tangent.y
            ) <
                0.0001f
        ) {
            bestFirst
        } else {
            tangent
        }
    }

    private fun buildJunctionSatinBlock(
        junction: Int,
        raster: RasterGlyph,
        skeleton: BooleanArray,
        distanceField: FloatArray,
        pitchUnits: Float,
        maxSatinWidthUnits: Float,
        pullUnits: Float
    ): SatinColumn? {
        val center =
            raster.center(
                junction
            )

        val localRadius =
            distanceField
                .getOrElse(
                    junction
                ) {
                    pitchUnits
                }
                .coerceAtLeast(
                    pitchUnits
                )

        val clearanceUnits =
            max(
                localRadius *
                    JUNCTION_CLEARANCE_FACTOR,
                pitchUnits *
                    1.5f
            )

        val tangent =
            estimateJunctionTangent(
                junction =
                    junction,
                raster =
                    raster,
                skeleton =
                    skeleton,
                clearanceUnits =
                    clearanceUnits
            )

        val normal =
            FPoint(
                -tangent.y,
                tangent.x
            )

        val rows =
            mutableListOf<SatinRow>()

        val rowStep =
            (
                pitchUnits *
                    0.82f
                ).coerceAtLeast(
                raster.step
            )

        var offset =
            -clearanceUnits

        while (
            offset <=
                clearanceUnits +
                    0.001f
        ) {
            val rowCenter =
                FPoint(
                    center.x +
                        tangent.x *
                            offset,
                    center.y +
                        tangent.y *
                            offset
                )

            if (
                raster.contains(
                    rowCenter
                )
            ) {
                val maxHalfWidth =
                    minOf(
                        maxSatinWidthUnits /
                            2f,
                        clearanceUnits +
                            raster.step
                    )

                val positive =
                    rayToRasterBoundary(
                        center =
                            rowCenter,
                        direction =
                            normal,
                        raster =
                            raster,
                        maxDistance =
                            maxHalfWidth
                    )

                val negative =
                    rayToRasterBoundary(
                        center =
                            rowCenter,
                        direction =
                            FPoint(
                                -normal.x,
                                -normal.y
                            ),
                        raster =
                            raster,
                        maxDistance =
                            maxHalfWidth
                    )

                val width =
                    positive +
                        negative

                if (
                    positive >
                        0.25f &&
                    negative >
                        0.25f &&
                    width <=
                        maxSatinWidthUnits *
                            ADAPTIVE_MAX_RAW_WIDTH_FACTOR
                ) {
                    var row =
                        SatinRow(
                            a =
                                FPoint(
                                    rowCenter.x -
                                        normal.x *
                                            (
                                                negative +
                                                    pullUnits
                                                ),
                                    rowCenter.y -
                                        normal.y *
                                            (
                                                negative +
                                                    pullUnits
                                                )
                                ),
                            b =
                                FPoint(
                                    rowCenter.x +
                                        normal.x *
                                            (
                                                positive +
                                                    pullUnits
                                                ),
                                    rowCenter.y +
                                        normal.y *
                                            (
                                                positive +
                                                    pullUnits
                                                )
                                )
                        )

                    val previous =
                        rows.lastOrNull()

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
                }
            }

            offset +=
                rowStep
        }

        return if (
            rows.size >=
                2
        ) {
            SatinColumn(
                rows =
                    rows
            )
        } else {
            null
        }
    }

    private fun clearJunctionNeighborhoods(
        raster: RasterGlyph,
        skeleton: BooleanArray,
        distanceField: FloatArray,
        pitchUnits: Float,
        junctionCenters: List<Int>
    ): BooleanArray {
        val result =
            skeleton.copyOf()

        if (
            junctionCenters.isEmpty()
        ) {
            return result
        }

        junctionCenters.forEach {
                junction ->
            val localRadiusUnits =
                distanceField
                    .getOrElse(
                        junction
                    ) {
                        pitchUnits
                    }
                    .coerceAtLeast(
                        pitchUnits
                    )

            val clearanceCells =
                ceil(
                    (
                        max(
                            localRadiusUnits *
                                JUNCTION_CLEARANCE_FACTOR,
                            pitchUnits *
                                1.5f
                        ) /
                            raster.step
                        ).toDouble()
                )
                    .toInt()
                    .coerceIn(
                        2,
                        12
                    )

            val centerX =
                junction %
                    raster.width

            val centerY =
                junction /
                    raster.width

            for (
                dy in
                    -clearanceCells..clearanceCells
            ) {
                for (
                    dx in
                        -clearanceCells..clearanceCells
                ) {
                    if (
                        dx *
                            dx +
                            dy *
                                dy >
                            clearanceCells *
                                clearanceCells
                    ) {
                        continue
                    }

                    val x =
                        centerX +
                            dx

                    val y =
                        centerY +
                            dy

                    if (
                        x in
                            0 until raster.width &&
                        y in
                            0 until raster.height
                    ) {
                        result[
                            y *
                                raster.width +
                                x
                        ] =
                            false
                    }
                }
            }
        }

        return result
    }

    private fun smoothedLocalRadius(
        path: List<Int>,
        centerIndex: Int,
        distanceField: FloatArray
    ): Float {
        val start =
            (
                centerIndex -
                    BLOCK_RADIUS_SMOOTH_WINDOW
                ).coerceAtLeast(
                0
            )

        val end =
            (
                centerIndex +
                    BLOCK_RADIUS_SMOOTH_WINDOW
                ).coerceAtMost(
                path.lastIndex
            )

        val values =
            (
                start..end
            )
                .mapNotNull {
                        index ->
                    distanceField
                        .getOrNull(
                            path[
                                index
                            ]
                        )
                        ?.takeIf {
                            it.isFinite() &&
                                it >
                                    0f
                        }
                }

        return if (
            values.isEmpty()
        ) {
            0f
        } else {
            values
                .sorted()[
                    values.size /
                        2
                ]
        }
    }

    private fun splitSkeletonComponents(
        raster: RasterGlyph,
        skeleton: BooleanArray
    ): List<List<Int>> {
        val visited =
            BooleanArray(
                skeleton.size
            )

        val result =
            mutableListOf<
                List<Int>
            >()

        skeleton.indices.forEach {
                seed ->
            if (
                !skeleton[
                    seed
                ] ||
                visited[
                    seed
                ]
            ) {
                return@forEach
            }

            val queue =
                java.util.ArrayDeque<Int>()

            val component =
                mutableListOf<Int>()

            queue.add(
                seed
            )

            visited[
                seed
            ] =
                true

            while (
                queue.isNotEmpty()
            ) {
                val current =
                    queue.removeFirst()

                component +=
                    current

                skeletonNeighbors(
                    index =
                        current,
                    raster =
                        raster,
                    skeleton =
                        skeleton
                ).forEach {
                        neighbor ->
                    if (
                        !visited[
                            neighbor
                        ]
                    ) {
                        visited[
                            neighbor
                        ] =
                            true

                        queue.add(
                            neighbor
                        )
                    }
                }
            }

            if (
                component.isNotEmpty()
            ) {
                result +=
                    component
            }
        }

        return result
    }

    private fun filledComponentFromSeed(
        raster: RasterGlyph,
        seed: Int
    ): List<Int> {
        if (
            seed !in
                raster.mask.indices ||
            !raster.mask[
                seed
            ]
        ) {
            return emptyList()
        }

        val visited =
            BooleanArray(
                raster.mask.size
            )

        val queue =
            java.util.ArrayDeque<Int>()

        val component =
            mutableListOf<Int>()

        queue.add(
            seed
        )

        visited[
            seed
        ] =
            true

        while (
            queue.isNotEmpty()
        ) {
            val current =
                queue.removeFirst()

            component +=
                current

            val x =
                current %
                    raster.width

            val y =
                current /
                    raster.width

            val candidates =
                intArrayOf(
                    x -
                        1,
                    y,
                    x +
                        1,
                    y,
                    x,
                    y -
                        1,
                    x,
                    y +
                        1
                )

            var offset =
                0

            while (
                offset <
                    candidates.size
            ) {
                val nx =
                    candidates[
                        offset
                    ]

                val ny =
                    candidates[
                        offset +
                            1
                    ]

                offset +=
                    2

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
                    raster.mask[
                        neighbor
                    ] &&
                    !visited[
                        neighbor
                    ]
                ) {
                    visited[
                        neighbor
                    ] =
                        true

                    queue.add(
                        neighbor
                    )
                }
            }
        }

        return component
    }

    private fun compactComponentColumn(
        raster: RasterGlyph,
        skeletonComponent: List<Int>,
        pitchUnits: Float,
        maxWidthUnits: Float,
        pullUnits: Float
    ): SatinColumn? {
        val seed =
            skeletonComponent
                .firstOrNull()
                ?: return null

        val filled =
            filledComponentFromSeed(
                raster =
                    raster,
                seed =
                    seed
            )

        if (
            filled.isEmpty()
        ) {
            return null
        }

        val xs =
            filled.map {
                it %
                    raster.width
            }

        val ys =
            filled.map {
                it /
                    raster.width
            }

        val minX =
            xs.minOrNull()
                ?: return null

        val maxX =
            xs.maxOrNull()
                ?: return null

        val minY =
            ys.minOrNull()
                ?: return null

        val maxY =
            ys.maxOrNull()
                ?: return null

        val widthUnits =
            (
                maxX -
                    minX +
                    1
                ) *
                raster.step

        val heightUnits =
            (
                maxY -
                    minY +
                    1
                ) *
                raster.step

        if (
            max(
                widthUnits,
                heightUnits
            ) >
                maxWidthUnits *
                    COMPACT_COMPONENT_MAX_WIDTH_FACTOR
        ) {
            return null
        }

        val filledSet =
            filled.toHashSet()

        val progressAlongX =
            widthUnits >=
                heightUnits

        val pitchCells =
            max(
                1,
                (
                    pitchUnits /
                        raster.step
                    ).roundToInt()
            )

        val rows =
            mutableListOf<
                SatinRow
            >()

        if (
            progressAlongX
        ) {
            var x =
                minX

            while (
                x <=
                    maxX
            ) {
                val cross =
                    (
                        minY..maxY
                    ).filter {
                            y ->
                        y *
                            raster.width +
                            x in
                            filledSet
                    }

                if (
                    cross.isNotEmpty()
                ) {
                    var contiguous =
                        true

                    for (
                        index in
                            1 until cross.size
                    ) {
                        if (
                            cross[
                                index
                            ] -
                                cross[
                                    index -
                                        1
                                ] >
                                1
                        ) {
                            contiguous =
                                false
                            break
                        }
                    }

                    if (
                        !contiguous
                    ) {
                        return null
                    }

                    val a =
                        raster.center(
                            cross.first() *
                                raster.width +
                                x
                        )

                    val b =
                        raster.center(
                            cross.last() *
                                raster.width +
                                x
                        )

                    rows +=
                        SatinRow(
                            a =
                                FPoint(
                                    a.x,
                                    a.y -
                                        pullUnits
                                ),
                            b =
                                FPoint(
                                    b.x,
                                    b.y +
                                        pullUnits
                                )
                        )
                }

                x +=
                    pitchCells
            }
        } else {
            var y =
                minY

            while (
                y <=
                    maxY
            ) {
                val cross =
                    (
                        minX..maxX
                    ).filter {
                            x ->
                        y *
                            raster.width +
                            x in
                            filledSet
                    }

                if (
                    cross.isNotEmpty()
                ) {
                    var contiguous =
                        true

                    for (
                        index in
                            1 until cross.size
                    ) {
                        if (
                            cross[
                                index
                            ] -
                                cross[
                                    index -
                                        1
                                ] >
                                1
                        ) {
                            contiguous =
                                false
                            break
                        }
                    }

                    if (
                        !contiguous
                    ) {
                        return null
                    }

                    val a =
                        raster.center(
                            y *
                                raster.width +
                                cross.first()
                        )

                    val b =
                        raster.center(
                            y *
                                raster.width +
                                cross.last()
                        )

                    rows +=
                        SatinRow(
                            a =
                                FPoint(
                                    a.x -
                                        pullUnits,
                                    a.y
                                ),
                            b =
                                FPoint(
                                    b.x +
                                        pullUnits,
                                    b.y
                                )
                        )
                }

                y +=
                    pitchCells
            }
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

    private fun prepareAdaptiveSkeletonPath(
        path: List<Int>,
        raster: RasterGlyph,
        skeleton: BooleanArray,
        pitchUnits: Float
    ): List<Int>? {
        if (
            path.size <
                3
        ) {
            return null
        }

        var start =
            0

        var end =
            path.lastIndex

        val trimCells =
            max(
                JUNCTION_TRIM_MIN_CELLS,
                ceil(
                    (
                        pitchUnits /
                            raster.step
                        ).toDouble()
                )
                    .toInt()
                    .coerceAtMost(
                        4
                    )
            )

        val firstDegree =
            skeletonNeighbors(
                index =
                    path.first(),
                raster =
                    raster,
                skeleton =
                    skeleton
            ).size

        val lastDegree =
            skeletonNeighbors(
                index =
                    path.last(),
                raster =
                    raster,
                skeleton =
                    skeleton
            ).size

        if (
            firstDegree >=
                3
        ) {
            start +=
                trimCells
        }

        if (
            lastDegree >=
                3
        ) {
            end -=
                trimCells
        }

        if (
            end -
                start <
                2
        ) {
            return null
        }

        return path.subList(
            start,
            end +
                1
        )
    }

    private fun smoothCenterline(
        source: List<FPoint>,
        radius: Int = 2,
        passes: Int = 2
    ): List<FPoint> {
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

                        val window =
                            current.subList(
                                start,
                                end +
                                    1
                            )

                        FPoint(
                            x =
                                window
                                    .map {
                                        it.x
                                    }
                                    .average()
                                    .toFloat(),
                            y =
                                window
                                    .map {
                                        it.y
                                    }
                                    .average()
                                    .toFloat()
                        )
                    }
                }
        }

        return current
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

    private fun satinColumnsFromSkeletonPath(
        path: List<Int>,
        raster: RasterGlyph,
        distanceField: FloatArray,
        pitchUnits: Float,
        maxRayUnits: Float,
        maxSatinWidthUnits: Float,
        pullUnits: Float
    ): List<SatinColumn> {
        if (
            path.size <
                2
        ) {
            return emptyList()
        }

        val centers =
            smoothCenterline(
                path.map {
                    raster.center(
                        it
                    )
                }
            )

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
            return emptyList()
        }

        val result =
            mutableListOf<
                SatinColumn
            >()

        var currentRows =
            mutableListOf<
                SatinRow
            >()

        var previousWidth:
            Float? =
            null

        var previousDirection:
            FPoint? =
            null

        var previousCenter:
            FPoint? =
            null

        fun flushCurrent() {
            if (
                currentRows.size >=
                    2
            ) {
                result +=
                    SatinColumn(
                        rows =
                            currentRows
                    )
            }

            currentRows =
                mutableListOf()

            previousWidth =
                null

            previousDirection =
                null

            previousCenter =
                null
        }

        val minimumDirectionDot =
            kotlin.math.cos(
                Math.toRadians(
                    MAX_ADAPTIVE_DIRECTION_TURN_DEGREES
                        .toDouble()
                )
            )
                .toFloat()

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
                flushCurrent()
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

            val localRadius =
                smoothedLocalRadius(
                    path =
                        path,
                    centerIndex =
                        centerIndex,
                    distanceField =
                        distanceField
                )

            if (
                localRadius <=
                    raster.step *
                        0.55f
            ) {
                flushCurrent()
                return@forEach
            }

            if (
                !raster.contains(
                    center
                )
            ) {
                flushCurrent()
                return@forEach
            }

            val localRayLimit =
                minOf(
                    maxRayUnits,
                    localRadius *
                        BLOCK_RADIUS_CAP_FACTOR +
                        raster.step
                )

            val positive =
                rayToRasterBoundary(
                    center =
                        center,
                    direction =
                        normal,
                    raster =
                        raster,
                    maxDistance =
                        localRayLimit
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
                        localRayLimit
                )

            if (
                positive <=
                    0.25f ||
                negative <=
                    0.25f
            ) {
                flushCurrent()
                return@forEach
            }

            val cappedPositive =
                minOf(
                    positive,
                    localRadius *
                        BLOCK_RADIUS_CAP_FACTOR
                )

            val cappedNegative =
                minOf(
                    negative,
                    localRadius *
                        BLOCK_RADIUS_CAP_FACTOR
                )

            val rawWidth =
                cappedPositive +
                    cappedNegative

            if (
                rawWidth >
                    maxSatinWidthUnits *
                        ADAPTIVE_MAX_RAW_WIDTH_FACTOR ||
                rawWidth <
                    raster.step
            ) {
                flushCurrent()
                return@forEach
            }

            var row =
                SatinRow(
                    a =
                        FPoint(
                            center.x -
                                normal.x *
                                    (
                                        cappedNegative +
                                            pullUnits
                                        ),
                            center.y -
                                normal.y *
                                    (
                                        cappedNegative +
                                            pullUnits
                                        )
                        ),
                    b =
                        FPoint(
                            center.x +
                                normal.x *
                                    (
                                        cappedPositive +
                                            pullUnits
                                        ),
                            center.y +
                                normal.y *
                                    (
                                        cappedPositive +
                                            pullUnits
                                        )
                        )
                )

            val previousRow =
                currentRows
                    .lastOrNull()

            if (
                previousRow !=
                    null
            ) {
                val direct =
                    distance(
                        previousRow.a,
                        row.a
                    ) +
                        distance(
                            previousRow.b,
                            row.b
                        )

                val swapped =
                    distance(
                        previousRow.a,
                        row.b
                    ) +
                        distance(
                            previousRow.b,
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

            val direction =
                normalize(
                    FPoint(
                        row.b.x -
                            row.a.x,
                        row.b.y -
                            row.a.y
                    )
                )

            val oldDirection =
                previousDirection

            val oldWidth =
                previousWidth

            val oldCenter =
                previousCenter

            val directionDot =
                if (
                    oldDirection ==
                        null
                ) {
                    1f
                } else {
                    kotlin.math.abs(
                        oldDirection.x *
                            direction.x +
                            oldDirection.y *
                                direction.y
                    )
                }

            val widthJump =
                oldWidth !=
                    null &&
                    rawWidth >
                        oldWidth *
                            MAX_ADAPTIVE_WIDTH_GROWTH_FACTOR &&
                    rawWidth -
                        oldWidth >
                        pitchUnits *
                            1.5f

            val centerJump =
                oldCenter !=
                    null &&
                    distance(
                        oldCenter,
                        center
                    ) >
                        max(
                            pitchUnits *
                                2.8f,
                            raster.step *
                                5f
                        )

            if (
                directionDot <
                    minimumDirectionDot ||
                widthJump ||
                centerJump
            ) {
                /*
                 * Uma coluna profissional pode curvar, mas a orientação não
                 * muda de forma instantânea. Quando isso acontece estamos em
                 * uma junção/ramo do esqueleto. Encerrar a coluna aqui evita
                 * o leque e permite que o próximo trecho seja costurado como
                 * outro bloco, com transição escondida ou JUMP quando preciso.
                 */
                flushCurrent()
            }

            currentRows +=
                row

            previousWidth =
                rawWidth

            previousDirection =
                direction

            previousCenter =
                center
        }

        flushCurrent()

        return result
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

        private fun firstVisualChoice(
            columns: List<SatinColumn>,
            startHint: FPoint?
        ): RoutedChoice? {
            val anchor =
                startHint
                    ?: return null

            var best:
                RoutedChoice? =
                null

            columns.forEachIndexed {
                    index,
                    column ->
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
                        previous ==
                            null ||
                        entryDistance <
                            previous.entryDistance -
                                0.001f ||
                        (
                            kotlin.math.abs(
                                entryDistance -
                                    previous.entryDistance
                            ) <=
                                0.001f &&
                            index <
                                previous.index
                        )
                    ) {
                        best =
                            RoutedChoice(
                                index =
                                    index,
                                oriented =
                                    candidate,
                                entryDistance =
                                    entryDistance
                            )
                    }
                }
            }

            return best
        }

        private fun terminalVisualChoice(
            columns: List<SatinColumn>,
            endHint: FPoint?
        ): RoutedChoice? {
            val anchor =
                endHint
                    ?: return null

            if (
                columns.size <
                    2
            ) {
                return null
            }

            val maxRows =
                columns.maxOfOrNull {
                    it.rows.size
                }
                    ?: return null

            val minimumStructuralRows =
                max(
                    2,
                    (
                        maxRows *
                            0.35f
                        ).roundToInt()
                )

            var best:
                RoutedChoice? =
                null

            columns.forEachIndexed {
                    index,
                    column ->
                if (
                    column.rows.size <
                        minimumStructuralRows
                ) {
                    return@forEachIndexed
                }

                orientationCandidates(
                    column
                ).forEach {
                        candidate ->
                    val last =
                        candidate.rows
                            .lastOrNull()
                            ?: return@forEach

                    val exitDistance =
                        minOf(
                            distance(
                                anchor,
                                last.a
                            ),
                            distance(
                                anchor,
                                last.b
                            )
                        )

                    val previous =
                        best

                    if (
                        previous ==
                            null ||
                        exitDistance <
                            previous.entryDistance -
                                0.001f ||
                        (
                            kotlin.math.abs(
                                exitDistance -
                                    previous.entryDistance
                            ) <=
                                0.001f &&
                            column.rows.size >
                                columns[
                                    previous.index
                                ].rows.size
                        )
                    ) {
                        best =
                            RoutedChoice(
                                index =
                                    index,
                                oriented =
                                    candidate,
                                entryDistance =
                                    exitDistance
                            )
                    }
                }
            }

            return best
        }

        fun emitGlyph(
            columns: List<SatinColumn>,
            polygons: List<Polygon>,
            startHint: FPoint?,
            includeUnderlay: Boolean,
            densityMm: Float,
            strictVisualOrder: Boolean =
                false,
            endHint: FPoint? =
                null
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

            val terminalChoice =
                terminalVisualChoice(
                    columns =
                        remaining,
                    endHint =
                        endHint
                )

            val reservedTerminal =
                terminalChoice?.let {
                        choice ->
                    remaining.removeAt(
                        choice.index
                    )

                    choice.oriented
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
                        !strictVisualOrder &&
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

                val firstVisual =
                    if (
                        firstColumn
                    ) {
                        firstVisualChoice(
                            columns =
                                remaining,
                            startHint =
                                startHint
                        )
                    } else {
                        null
                    }

                val column =
                    when {
                        firstVisual !=
                            null -> {
                            remaining.removeAt(
                                firstVisual.index
                            )

                            firstVisual.oriented
                        }

                        connected !=
                            null -> {
                            remaining.removeAt(
                                connected.index
                            )

                            connected.oriented
                        }

                        else -> {
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
                    emitRegionZigzagUnderlay(
                        column =
                            column,
                        polygons =
                            polygons,
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

            reservedTerminal?.let {
                    terminal ->
                val entry =
                    terminal.rows
                        .first()
                        .a

                travelTo(
                    target =
                        entry,
                    polygons =
                        polygons,
                    firstColumn =
                        false
                )

                if (
                    includeUnderlay
                ) {
                    emitRegionZigzagUnderlay(
                        column =
                            terminal,
                        polygons =
                            polygons,
                        densityMm =
                            densityMm
                    )
                }

                emitSatinColumn(
                    column =
                        terminal,
                    endHint =
                        endHint
                )
            }
        }

        fun emitUnderlayForTest(
            column: SatinColumn,
            polygons: List<Polygon>,
            densityMm: Float
        ) {
            emitRegionZigzagUnderlay(
                column =
                    column,
                polygons =
                    polygons,
                densityMm =
                    densityMm
            )
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

        private fun emitRegionZigzagUnderlay(
            column: SatinColumn,
            polygons: List<Polygon>,
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

            /*
             * O underlay continua espaçado em regiões retas, mas em curvas
             * fechadas não pode "cortar caminho" pelo vazado da letra.
             *
             * Para cada avanço tentamos primeiro o passo espaçado desejado.
             * Se a ligação sair do glifo, reduzimos progressivamente o passo
             * até encontrar uma conexão totalmente escondida dentro do traço.
             */
            val desiredStep =
                max(
                    2,
                    (
                        20f /
                            pitchUnits
                                .coerceAtLeast(
                                    1f
                                )
                        ).roundToInt()
                )

            var rowIndex =
                0

            var currentPoint =
                rows.first().a

            var preferA =
                false

            stitchTo(
                currentPoint
            )

            while (
                rowIndex <
                    rows.lastIndex
            ) {
                var candidateIndex =
                    minOf(
                        rows.lastIndex,
                        rowIndex +
                            desiredStep
                    )

                var selectedIndex =
                    -1

                var selectedPoint:
                    FPoint? =
                    null

                var selectedSideIsA =
                    preferA

                while (
                    candidateIndex >
                        rowIndex
                ) {
                    val row =
                        rows[
                            candidateIndex
                        ]

                    val preferred =
                        if (
                            preferA
                        ) {
                            row.a
                        } else {
                            row.b
                        }

                    val alternate =
                        if (
                            preferA
                        ) {
                            row.b
                        } else {
                            row.a
                        }

                    val preferredInside =
                        segmentInsideGlyph(
                            from =
                                currentPoint,
                            to =
                                preferred,
                            polygons =
                                polygons,
                            sampleUnits =
                                ROUTING_SAMPLE_UNITS
                        )

                    val alternateInside =
                        segmentInsideGlyph(
                            from =
                                currentPoint,
                            to =
                                alternate,
                            polygons =
                                polygons,
                            sampleUnits =
                                ROUTING_SAMPLE_UNITS
                        )

                    when {
                        preferredInside -> {
                            selectedIndex =
                                candidateIndex

                            selectedPoint =
                                preferred

                            selectedSideIsA =
                                preferA
                        }

                        alternateInside -> {
                            selectedIndex =
                                candidateIndex

                            selectedPoint =
                                alternate

                            selectedSideIsA =
                                !preferA
                        }
                    }

                    if (
                        selectedPoint !=
                            null
                    ) {
                        break
                    }

                    candidateIndex -=
                        1
                }

                if (
                    selectedPoint ==
                        null
                ) {
                    /*
                     * A geometria ficou tão fechada que nem a próxima seção
                     * possui ligação interna segura. Não desenhamos uma
                     * diagonal visível pelo buraco: encerramos esta camada e
                     * deixamos o Satin principal continuar o bloco.
                     */
                    break
                }

                stitchTo(
                    selectedPoint
                )

                currentPoint =
                    selectedPoint

                rowIndex =
                    selectedIndex

                preferA =
                    !selectedSideIsA
            }
        }

        private fun columnUsesTatami(
            column: SatinColumn
        ): Boolean {
            val rows =
                column.rows

            if (
                rows.size <
                    3
            ) {
                return false
            }

            val limitUnits =
                DEFAULT_MAX_SATIN_WIDTH_MM *
                    10f

            val widths =
                rows.map {
                    distance(
                        it.a,
                        it.b
                    )
                }

            val requiredWideRows =
                max(
                    2,
                    ceil(
                        rows.size *
                            TATAMI_MIN_WIDE_ROW_RATIO
                    ).toInt()
                )

            return widths.count {
                it >
                    limitUnits
            } >=
                requiredWideRows &&
                widths.average() >=
                    limitUnits *
                        0.82
        }

        private fun alignTatamiRows(
            rows: List<SatinRow>
        ): List<SatinRow> {
            if (
                rows.size <
                    2
            ) {
                return rows
            }

            val aligned =
                mutableListOf(
                    rows.first()
                )

            rows.drop(
                1
            ).forEach {
                    row ->
                val previous =
                    aligned.last()

                val same =
                    distance(
                        previous.a,
                        row.a
                    ) +
                        distance(
                            previous.b,
                            row.b
                        )

                val flipped =
                    distance(
                        previous.a,
                        row.b
                    ) +
                        distance(
                            previous.b,
                            row.a
                        )

                aligned +=
                    if (
                        flipped <
                            same
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

            return aligned
        }

        private fun densifyTatamiRows(
            rows: List<SatinRow>
        ): List<SatinRow> {
            val aligned =
                alignTatamiRows(
                    rows
                )

            if (
                aligned.size <
                    2
            ) {
                return aligned
            }

            val maxPitchUnits =
                TATAMI_ROW_PITCH_MM *
                    10f

            val result =
                mutableListOf<SatinRow>()

            aligned
                .zipWithNext()
                .forEachIndexed {
                        index,
                        pair ->
                    if (
                        index ==
                            0
                    ) {
                        result +=
                            pair.first
                    }

                    val firstCenter =
                        FPoint(
                            (
                                pair.first.a.x +
                                    pair.first.b.x
                                ) /
                                2f,
                            (
                                pair.first.a.y +
                                    pair.first.b.y
                                ) /
                                2f
                        )

                    val secondCenter =
                        FPoint(
                            (
                                pair.second.a.x +
                                    pair.second.b.x
                                ) /
                                2f,
                            (
                                pair.second.a.y +
                                    pair.second.b.y
                                ) /
                                2f
                        )

                    val gap =
                        distance(
                            firstCenter,
                            secondCenter
                        )

                    val pieces =
                        max(
                            1,
                            ceil(
                                gap /
                                    maxPitchUnits
                                        .coerceAtLeast(
                                            1f
                                        )
                            ).toInt()
                        )

                    for (
                        part in
                            1 until pieces
                    ) {
                        val ratio =
                            part.toFloat() /
                                pieces

                        result +=
                            SatinRow(
                                a =
                                    lerp(
                                        pair.first.a,
                                        pair.second.a,
                                        ratio
                                    ),
                                b =
                                    lerp(
                                        pair.first.b,
                                        pair.second.b,
                                        ratio
                                    )
                            )
                    }

                    result +=
                        pair.second
                }

            return result
        }

        private fun expandTatamiRow(
            row: SatinRow
        ): SatinRow {
            val dx =
                row.b.x -
                    row.a.x

            val dy =
                row.b.y -
                    row.a.y

            val length =
                hypot(
                    dx.toDouble(),
                    dy.toDouble()
                )
                    .toFloat()

            if (
                length <
                    0.001f
            ) {
                return row
            }

            val overlap =
                TATAMI_EDGE_OVERLAP_MM *
                    10f

            val ux =
                dx /
                    length

            val uy =
                dy /
                    length

            return SatinRow(
                a =
                    FPoint(
                        row.a.x -
                            ux *
                                overlap,
                        row.a.y -
                            uy *
                                overlap
                    ),
                b =
                    FPoint(
                        row.b.x +
                            ux *
                                overlap,
                        row.b.y +
                            uy *
                                overlap
                    )
            )
        }

        private fun emitTatamiSegmented(
            from: FPoint,
            to: FPoint,
            maxSegmentUnits: Float,
            staggered: Boolean
        ) {
            val total =
                distance(
                    from,
                    to
                )

            if (
                total <
                    0.001f
            ) {
                return
            }

            val step =
                maxSegmentUnits
                    .coerceAtLeast(
                        1f
                    )

            var travelled =
                if (
                    staggered
                ) {
                    step *
                        TATAMI_STAGGER_FACTOR
                } else {
                    step
                }

            while (
                travelled <
                    total
            ) {
                emit(
                    lerp(
                        from,
                        to,
                        travelled /
                            total
                    ),
                    StitchCommand.STITCH
                )

                travelled +=
                    step
            }

            emit(
                to,
                StitchCommand.STITCH
            )
        }

        private fun emitTatamiColumn(
            column: SatinColumn,
            endHint: FPoint? =
                null
        ) {
            val sourceRows =
                densifyTatamiRows(
                    column.rows
                )
                    .map {
                        expandTatamiRow(
                            it
                        )
                    }

            if (
                sourceRows.isEmpty()
            ) {
                return
            }

            val before =
                current

            val rows =
                if (
                    endHint !=
                        null &&
                    sourceRows.size >=
                        2
                ) {
                    val normalDistance =
                        minOf(
                            distance(
                                endHint,
                                sourceRows.last().a
                            ),
                            distance(
                                endHint,
                                sourceRows.last().b
                            )
                        )

                    val reversedDistance =
                        minOf(
                            distance(
                                endHint,
                                sourceRows.first().a
                            ),
                            distance(
                                endHint,
                                sourceRows.first().b
                            )
                        )

                    if (
                        reversedDistance <
                            normalDistance
                    ) {
                        sourceRows
                            .asReversed()
                    } else {
                        sourceRows
                    }
                } else if (
                    before !=
                        null &&
                    sourceRows.size >=
                        2
                ) {
                    val firstDistance =
                        minOf(
                            distance(
                                before,
                                sourceRows.first().a
                            ),
                            distance(
                                before,
                                sourceRows.first().b
                            )
                        )

                    val lastDistance =
                        minOf(
                            distance(
                                before,
                                sourceRows.last().a
                            ),
                            distance(
                                before,
                                sourceRows.last().b
                            )
                        )

                    if (
                        lastDistance <
                            firstDistance
                    ) {
                        sourceRows
                            .asReversed()
                    } else {
                        sourceRows
                    }
                } else {
                    sourceRows
                }

            var startOnA =
                if (
                    endHint !=
                        null
                ) {
                    val oddRowCount =
                        rows.size %
                            2 ==
                            1

                    val endIfStartA =
                        if (
                            oddRowCount
                        ) {
                            rows.last().b
                        } else {
                            rows.last().a
                        }

                    val endIfStartB =
                        if (
                            oddRowCount
                        ) {
                            rows.last().a
                        } else {
                            rows.last().b
                        }

                    distance(
                        endHint,
                        endIfStartA
                    ) <=
                        distance(
                            endHint,
                            endIfStartB
                        )
                } else if (
                    before ==
                        null
                ) {
                    true
                } else {
                    distance(
                        before,
                        rows.first().a
                    ) <=
                        distance(
                            before,
                            rows.first().b
                        )
                }

            val maxSegmentUnits =
                DEFAULT_TATAMI_STITCH_LENGTH_MM *
                    10f

            rows.forEach {
                    row ->
                val start =
                    if (
                        startOnA
                    ) {
                        row.a
                    } else {
                        row.b
                    }

                val end =
                    if (
                        startOnA
                    ) {
                        row.b
                    } else {
                        row.a
                    }

                stitchTo(
                    start
                )

                emitTatamiSegmented(
                    from =
                        current
                            ?: start,
                    to =
                        end,
                    maxSegmentUnits =
                        maxSegmentUnits,
                    staggered =
                        !startOnA
                )

                startOnA =
                    !startOnA
            }
        }

        private fun emitSatinColumn(
            column: SatinColumn,
            endHint: FPoint? =
                null
        ) {
            if (
                columnUsesTatami(
                    column
                )
            ) {
                emitTatamiColumn(
                    column =
                        column,
                    endHint =
                        endHint
                )

                return
            }

            val sourceRows =
                column.rows

            if (
                sourceRows.isEmpty()
            ) {
                return
            }

            val before =
                current

            val rows =
                if (
                    endHint !=
                        null &&
                    sourceRows.size >=
                        2
                ) {
                    val normalLast =
                        sourceRows.last()

                    val reversedLast =
                        sourceRows.first()

                    val normalExitDistance =
                        minOf(
                            distance(
                                endHint,
                                normalLast.a
                            ),
                            distance(
                                endHint,
                                normalLast.b
                            )
                        )

                    val reversedExitDistance =
                        minOf(
                            distance(
                                endHint,
                                reversedLast.a
                            ),
                            distance(
                                endHint,
                                reversedLast.b
                            )
                        )

                    if (
                        reversedExitDistance <
                            normalExitDistance
                    ) {
                        sourceRows
                            .asReversed()
                    } else {
                        sourceRows
                    }
                } else if (
                    before ==
                        null ||
                    sourceRows.size <
                        2
                ) {
                    sourceRows
                } else {
                    val first =
                        sourceRows.first()

                    val last =
                        sourceRows.last()

                    val firstDistance =
                        minOf(
                            distance(
                                before,
                                first.a
                            ),
                            distance(
                                before,
                                first.b
                            )
                        )

                    val lastDistance =
                        minOf(
                            distance(
                                before,
                                last.a
                            ),
                            distance(
                                before,
                                last.b
                            )
                        )

                    if (
                        lastDistance <
                            firstDistance
                    ) {
                        sourceRows
                            .asReversed()
                    } else {
                        sourceRows
                    }
                }

            val start =
                rows.first()

            var nextIsA =
                if (
                    endHint !=
                        null
                ) {
                    val oddRowCount =
                        rows.size %
                            2 ==
                            1

                    val endIfStartA =
                        if (
                            oddRowCount
                        ) {
                            rows.last().a
                        } else {
                            rows.last().b
                        }

                    val endIfStartB =
                        if (
                            oddRowCount
                        ) {
                            rows.last().b
                        } else {
                            rows.last().a
                        }

                    distance(
                        endHint,
                        endIfStartA
                    ) <=
                        distance(
                            endHint,
                            endIfStartB
                        )
                } else if (
                    before ==
                        null
                ) {
                    false
                } else {
                    distance(
                        before,
                        start.a
                    ) >=
                        distance(
                            before,
                            start.b
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

    internal fun debugGlyphVisualStart(
        polygon:
            List<Pair<Float, Float>>
    ): Pair<Float, Float>? =
        glyphVisualStartPoint(
            listOf(
                Polygon(
                    polygon.map {
                        FPoint(
                            it.first,
                            it.second
                        )
                    }
                )
            )
        )?.let {
            Pair(
                it.x,
                it.y
            )
        }

    internal fun debugWideObjectUsesTatami():
        List<EmbroideryPoint> {
        val output =
            mutableListOf<EmbroideryPoint>()

        val emitter =
            SatinEmitter(
                output
            )

        val rows =
            mutableListOf<SatinRow>()

        for (
            index in
                0 until 7
        ) {
            rows +=
                SatinRow(
                    a =
                        FPoint(
                            0f,
                            index *
                                4f
                        ),
                    b =
                        FPoint(
                            100f,
                            index *
                                4f
                        )
                )
        }

        emitter.emitGlyph(
            columns =
                listOf(
                    SatinColumn(
                        rows
                    )
                ),
            polygons =
                listOf(
                    Polygon(
                        listOf(
                            FPoint(
                                -2f,
                                -2f
                            ),
                            FPoint(
                                102f,
                                -2f
                            ),
                            FPoint(
                                102f,
                                30f
                            ),
                            FPoint(
                                -2f,
                                30f
                            )
                        )
                    )
                ),
            startHint =
                FPoint(
                    0f,
                    0f
                ),
            includeUnderlay =
                false,
            densityMm =
                0.4f
        )

        return output
    }

    internal fun debugTerminalStructuralLegPath():
        List<EmbroideryPoint> {
        val output =
            mutableListOf<EmbroideryPoint>()

        val emitter =
            SatinEmitter(
                output
            )

        fun column(
            left: Float,
            bottom: Float,
            rowsCount: Int
        ): SatinColumn {
            val rows =
                mutableListOf<SatinRow>()

            for (
                index in
                    0 until rowsCount
            ) {
                val y =
                    bottom +
                        index *
                            4f

                rows +=
                    SatinRow(
                        a =
                            FPoint(
                                left,
                                y
                            ),
                        b =
                            FPoint(
                                left +
                                    12f,
                                y
                            )
                    )
            }

            return SatinColumn(
                rows
            )
        }

        val body =
            column(
                left =
                    45f,
                bottom =
                    10f,
                rowsCount =
                    8
            )

        val structuralLeg =
            column(
                left =
                    76f,
                bottom =
                    4f,
                rowsCount =
                    9
            )

        val thinFlourish =
            column(
                left =
                    100f,
                bottom =
                    2f,
                rowsCount =
                    2
            )

        emitter.emitGlyph(
            columns =
                listOf(
                    body,
                    thinFlourish,
                    structuralLeg
                ),
            polygons =
                listOf(
                    Polygon(
                        listOf(
                            FPoint(
                                40f,
                                0f
                            ),
                            FPoint(
                                116f,
                                0f
                            ),
                            FPoint(
                                116f,
                                45f
                            ),
                            FPoint(
                                40f,
                                45f
                            )
                        )
                    )
                ),
            startHint =
                FPoint(
                    45f,
                    40f
                ),
            includeUnderlay =
                false,
            densityMm =
                0.4f,
            strictVisualOrder =
                true,
            endHint =
                FPoint(
                    82f,
                    6f
                )
        )

        return output
    }

    internal fun debugFirstVisualBlockPath():
        List<EmbroideryPoint> {
        val output =
            mutableListOf<EmbroideryPoint>()

        val emitter =
            SatinEmitter(
                output
            )

        val lowerFlourish =
            SatinColumn(
                mutableListOf(
                    SatinRow(
                        FPoint(
                            0f,
                            0f
                        ),
                        FPoint(
                            16f,
                            0f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            0f,
                            14f
                        ),
                        FPoint(
                            16f,
                            14f
                        )
                    )
                )
            )

        val upperStem =
            SatinColumn(
                mutableListOf(
                    SatinRow(
                        FPoint(
                            8f,
                            82f
                        ),
                        FPoint(
                            24f,
                            82f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            8f,
                            100f
                        ),
                        FPoint(
                            24f,
                            100f
                        )
                    )
                )
            )

        emitter.emitGlyph(
            columns =
                listOf(
                    lowerFlourish,
                    upperStem
                ),
            polygons =
                listOf(
                    Polygon(
                        listOf(
                            FPoint(
                                -2f,
                                -2f
                            ),
                            FPoint(
                                30f,
                                -2f
                            ),
                            FPoint(
                                30f,
                                104f
                            ),
                            FPoint(
                                -2f,
                                104f
                            )
                        )
                    )
                ),
            startHint =
                FPoint(
                    8f,
                    100f
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
                    0f,
                    10f
                ),
            includeUnderlay =
                false,
            densityMm =
                0.4f
        )

        return output
    }

    internal fun debugCurvedUnderlayAllSegmentsInside():
        Boolean {
        val output =
            mutableListOf<EmbroideryPoint>()

        val emitter =
            SatinEmitter(
                output
            )

        val center =
            FPoint(
                60f,
                60f
            )

        fun ringPoint(
            radius: Float,
            degrees: Float
        ): FPoint {
            val radians =
                Math.toRadians(
                    degrees.toDouble()
                )

            return FPoint(
                center.x +
                    kotlin.math.cos(
                        radians
                    ).toFloat() *
                        radius,
                center.y +
                    kotlin.math.sin(
                        radians
                    ).toFloat() *
                        radius
            )
        }

        val outerContour =
            Polygon(
                (0 until 72).map {
                        index ->
                    ringPoint(
                        radius =
                            50f,
                        degrees =
                            index *
                                5f
                    )
                }
            )

        val innerContour =
            Polygon(
                (0 until 72).map {
                        index ->
                    ringPoint(
                        radius =
                            34f,
                        degrees =
                            index *
                                5f
                    )
                }
            )

        val polygons =
            listOf(
                outerContour,
                innerContour
            )

        val rows =
            (0..18).map {
                    index ->
                val degrees =
                    90f +
                        index *
                            5f

                SatinRow(
                    a =
                        ringPoint(
                            radius =
                                50f,
                            degrees =
                                degrees
                        ),
                    b =
                        ringPoint(
                            radius =
                                34f,
                            degrees =
                                degrees
                        )
                )
            }
                .toMutableList()

        emitter.emitUnderlayForTest(
            column =
                SatinColumn(
                    rows
                ),
            polygons =
                polygons,
            densityMm =
                0.4f
        )

        val stitched =
            output.filter {
                it.command ==
                    StitchCommand.STITCH
            }

        return stitched
            .zipWithNext()
            .all {
                    pair ->
                segmentInsideGlyphGeometry(
                    from =
                        FPoint(
                            pair.first.xUnits.toFloat(),
                            pair.first.yUnits.toFloat()
                        ),
                    to =
                        FPoint(
                            pair.second.xUnits.toFloat(),
                            pair.second.yUnits.toFloat()
                        ),
                    polygons =
                        polygons,
                    sampleUnits =
                        ROUTING_SAMPLE_UNITS
                )
            }
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

    internal fun debugNearestSatinRowCenterDistance(
        polygon:
            List<Pair<Float, Float>>,
        targetX: Float,
        targetY: Float,
        densityMm: Float =
            0.4f,
        maxWidthMm: Float =
            7f
    ): Float =
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
                    row ->
                val center =
                    FPoint(
                        (
                            row.a.x +
                                row.b.x
                            ) /
                            2f,
                        (
                            row.a.y +
                                row.b.y
                            ) /
                            2f
                    )

                distance(
                    center,
                    FPoint(
                        targetX,
                        targetY
                    )
                )
            }
            .minOrNull()
            ?: Float.MAX_VALUE

    internal fun debugAdaptiveMaxTurnDegrees(
        polygon:
            List<Pair<Float, Float>>,
        densityMm: Float =
            0.4f,
        maxWidthMm: Float =
            7f
    ): Float {
        var maximum =
            0f

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
            .forEach {
                    column ->
                column.rows
                    .zipWithNext()
                    .forEach {
                            pair ->
                        val first =
                            normalize(
                                FPoint(
                                    pair.first.b.x -
                                        pair.first.a.x,
                                    pair.first.b.y -
                                        pair.first.a.y
                                )
                            )

                        val second =
                            normalize(
                                FPoint(
                                    pair.second.b.x -
                                        pair.second.a.x,
                                    pair.second.b.y -
                                        pair.second.a.y
                                )
                            )

                        val dot =
                            kotlin.math.abs(
                                first.x *
                                    second.x +
                                    first.y *
                                        second.y
                            )
                                .coerceIn(
                                    -1f,
                                    1f
                                )

                        val degrees =
                            Math.toDegrees(
                                kotlin.math.acos(
                                    dot.toDouble()
                                )
                            )
                                .toFloat()

                        maximum =
                            max(
                                maximum,
                                degrees
                            )
                    }
            }

        return maximum
    }

    internal fun debugContourEndCapCount():
        Int {
        val polygon =
            Polygon(
                listOf(
                    FPoint(
                        0f,
                        0f
                    ),
                    FPoint(
                        40f,
                        0f
                    ),
                    FPoint(
                        40f,
                        80f
                    ),
                    FPoint(
                        0f,
                        80f
                    )
                )
            )

        val rows =
            listOf(
                SatinRow(
                    a =
                        FPoint(
                            8f,
                            24f
                        ),
                    b =
                        FPoint(
                            32f,
                            24f
                        )
                ),
                SatinRow(
                    a =
                        FPoint(
                            8f,
                            48f
                        ),
                    b =
                        FPoint(
                            32f,
                            48f
                        )
                )
            )

        return addContourEndCaps(
            rows =
                rows,
            polygons =
                listOf(
                    polygon
                ),
            pitchUnits =
                4f
        ).size
    }

    internal fun debugPrimaryRowVectors(
        polygon:
            List<Pair<Float, Float>>,
        densityMm: Float =
            0.4f,
        maxWidthMm: Float =
            7f
    ): List<Pair<Float, Float>> =
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
                    row ->
                Pair(
                    row.b.x -
                        row.a.x,
                    row.b.y -
                        row.a.y
                )
            }

    internal fun debugCoverageRepairCount():
        Int {
        val polygons =
            listOf(
                Polygon(
                    listOf(
                        FPoint(
                            0f,
                            0f
                        ),
                        FPoint(
                            100f,
                            0f
                        ),
                        FPoint(
                            100f,
                            20f
                        ),
                        FPoint(
                            60f,
                            20f
                        ),
                        FPoint(
                            60f,
                            100f
                        ),
                        FPoint(
                            40f,
                            100f
                        ),
                        FPoint(
                            40f,
                            20f
                        ),
                        FPoint(
                            0f,
                            20f
                        )
                    )
                )
            )

        val intentionallySparse =
            listOf(
                SatinColumn(
                    mutableListOf(
                        SatinRow(
                            FPoint(
                                0f,
                                4f
                            ),
                            FPoint(
                                32f,
                                4f
                            )
                        ),
                        SatinRow(
                            FPoint(
                                0f,
                                12f
                            ),
                            FPoint(
                                32f,
                                12f
                            )
                        )
                    )
                ),
                SatinColumn(
                    mutableListOf(
                        SatinRow(
                            FPoint(
                                68f,
                                4f
                            ),
                            FPoint(
                                100f,
                                4f
                            )
                        ),
                        SatinRow(
                            FPoint(
                                68f,
                                12f
                            ),
                            FPoint(
                                100f,
                                12f
                            )
                        )
                    )
                )
            )

        return buildCoverageRepairColumns(
            polygons =
                polygons,
            primary =
                intentionallySparse,
            densityMm =
                0.4f,
            pullCompensationMm =
                0f
        )
            .sumOf {
                it.rows.size
            }
    }

    internal fun debugPrimaryMaxTurnDegrees(
        polygon:
            List<Pair<Float, Float>>,
        densityMm: Float =
            0.4f,
        maxWidthMm: Float =
            7f
    ): Float {
        var maximum =
            0f

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
                0f,
            digitizingMode =
                ImportedFontDigitizingMode.PROFESSIONAL_BLOCKS
        )
            .forEach {
                    column ->
                column.rows
                    .zipWithNext()
                    .forEach {
                            pair ->
                        val first =
                            normalize(
                                FPoint(
                                    pair.first.b.x -
                                        pair.first.a.x,
                                    pair.first.b.y -
                                        pair.first.a.y
                                )
                            )

                        val second =
                            normalize(
                                FPoint(
                                    pair.second.b.x -
                                        pair.second.a.x,
                                    pair.second.b.y -
                                        pair.second.a.y
                                )
                            )

                        val dot =
                            kotlin.math.abs(
                                first.x *
                                    second.x +
                                    first.y *
                                        second.y
                            )
                                .coerceIn(
                                    -1f,
                                    1f
                                )

                        maximum =
                            max(
                                maximum,
                                Math.toDegrees(
                                    kotlin.math.acos(
                                        dot.toDouble()
                                    )
                                )
                                    .toFloat()
                            )
                    }
            }

        return maximum
    }

    internal fun debugPrimaryMaxCenterGap(
        polygon:
            List<Pair<Float, Float>>,
        densityMm: Float =
            0.4f,
        maxWidthMm: Float =
            7f
    ): Float =
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
                    column ->
                column.rows
                    .zipWithNext()
                    .map {
                            pair ->
                        val firstCenter =
                            FPoint(
                                (
                                    pair.first.a.x +
                                        pair.first.b.x
                                    ) /
                                    2f,
                                (
                                    pair.first.a.y +
                                        pair.first.b.y
                                    ) /
                                    2f
                            )

                        val secondCenter =
                            FPoint(
                                (
                                    pair.second.a.x +
                                        pair.second.b.x
                                    ) /
                                    2f,
                                (
                                    pair.second.a.y +
                                        pair.second.b.y
                                    ) /
                                    2f
                            )

                        distance(
                            firstCenter,
                            secondCenter
                        )
                    }
            }
            .maxOrNull()
            ?: 0f

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
        sampleColumnsByAxis(
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

    internal fun debugAdaptiveColumnWidths(
        polygon:
            List<Pair<Float, Float>>,
        densityMm: Float =
            0.4f,
        maxWidthMm: Float =
            7f
    ): List<Float> =
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
