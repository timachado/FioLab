package com.timachado.fiolab.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatrixConverterTest {

    @Test
    fun convertsNormalizedDesignToAllReleasedFormats() {
        val source = sampleDesign()

        for (format in MatrixConverter.supportedFormats) {
            val converted =
                MatrixConverter.convert(
                    source,
                    format
                ).getOrThrow()

            assertEquals(
                format,
                converted.format
            )

            assertTrue(
                converted.bytes.isNotEmpty()
            )

            val parsed =
                when (format) {
                    "DST" ->
                        DstParser.parse(
                            converted.fileName,
                            converted.bytes
                        )

                    "PES",
                    "JEF" ->
                        EmbroideryIoParser.parse(
                            converted.fileName,
                            converted.bytes
                        )

                    else ->
                        error(
                            "Formato inesperado."
                        )
                }

            assertTrue(
                parsed is
                    EmbroideryLoadResult.Success
            )

            val design =
                (parsed as
                    EmbroideryLoadResult.Success)
                    .design

            assertTrue(
                design.stitchCount > 0
            )

            assertTrue(
                design.bounds.widthMm > 0f
            )

            assertTrue(
                design.bounds.heightMm > 0f
            )
        }
    }

    @Test
    fun conversionPreservesVisualOrientationForCartesianFioLabDesigns() {
        assertVisualOrientationPreserved(
            asymmetricDesign(
                sourceYAxisDown = false
            )
        )
    }

    @Test
    fun conversionPreservesVisualOrientationForImportedScreenCoordinates() {
        assertVisualOrientationPreserved(
            asymmetricDesign(
                sourceYAxisDown = true
            )
        )
    }

    private fun assertVisualOrientationPreserved(
        source: EmbroideryDesign
    ) {
        val sourceStitches =
            source.points
                .filter {
                    it.command ==
                        StitchCommand.STITCH
                }

        for (
            format in
                MatrixConverter.supportedFormats
        ) {
            val converted =
                MatrixConverter.convert(
                    source,
                    format,
                    outputSuffix =
                        "orientacao"
                ).getOrThrow()

            val parsed =
                when (format) {
                    "DST" ->
                        DstParser.parse(
                            converted.fileName,
                            converted.bytes
                        )

                    "PES",
                    "JEF" ->
                        EmbroideryIoParser.parse(
                            converted.fileName,
                            converted.bytes
                        )

                    else ->
                        error(
                            "Formato inesperado."
                        )
                }

            assertTrue(
                parsed is
                    EmbroideryLoadResult.Success
            )

            val target =
                (parsed as
                    EmbroideryLoadResult.Success)
                    .design

            val targetStitches =
                target.points
                    .filter {
                        it.command ==
                            StitchCommand.STITCH
                    }

            assertEquals(
                "Quantidade de pontos mudou em $format",
                sourceStitches.size,
                targetStitches.size
            )

            assertEquals(
                "A geometria foi refletida/espelhada ao gerar $format",
                normalizedVisualPoints(
                    design = source,
                    points = sourceStitches
                ),
                normalizedVisualPoints(
                    design = target,
                    points = targetStitches
                )
            )
        }
    }

    private fun normalizedVisualPoints(
        design: EmbroideryDesign,
        points: List<EmbroideryPoint>
    ): List<Pair<Int, Int>> {
        if (points.isEmpty()) {
            return emptyList()
        }

        val minX =
            points.minOf {
                it.xUnits
            }

        val visualYs =
            points.map {
                visualY(
                    design,
                    it.yUnits
                )
            }

        val minVisualY =
            visualYs.min()

        return points
            .mapIndexed {
                    index,
                    point ->
                Pair(
                    point.xUnits -
                        minX,
                    visualYs[index] -
                        minVisualY
                )
            }
            .sortedWith(
                compareBy<Pair<Int, Int>> {
                    it.first
                }.thenBy {
                    it.second
                }
            )
    }

    private fun visualY(
        design: EmbroideryDesign,
        yUnits: Int
    ): Int =
        if (
            design.sourceYAxisDown
        ) {
            yUnits
        } else {
            -yUnits
        }

    private fun asymmetricDesign(
        sourceYAxisDown: Boolean
    ): EmbroideryDesign {
        val points =
            listOf(
                EmbroideryPoint(
                    0,
                    0,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    30,
                    90,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    90,
                    -20,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    20,
                    40,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    20,
                    40,
                    StitchCommand.END,
                    0
                )
            )

        return EmbroideryDesign(
            fileName =
                if (
                    sourceYAxisDown
                ) {
                    "importada.pes"
                } else {
                    "criada-no-fiolab.dst"
                },
            format =
                if (
                    sourceYAxisDown
                ) {
                    "PES"
                } else {
                    "DST"
                },
            label = "ORIENTACAO",
            points = points,
            bounds =
                EmbroideryBounds(
                    minXUnits = 0,
                    maxXUnits = 90,
                    minYUnits = -20,
                    maxYUnits = 90
                ),
            stitchCount = 4,
            jumpCount = 0,
            colorChanges = 0,
            endFound = true,
            sourceBytes =
                ByteArray(0),
            threadColors =
                listOf(
                    0xE6BE70
                ),
            sourceYAxisDown =
                sourceYAxisDown
        )
    }

    private fun sampleDesign(): EmbroideryDesign {
        val points =
            listOf(
                EmbroideryPoint(
                    0,
                    0,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    120,
                    0,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    120,
                    80,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    0,
                    80,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    0,
                    0,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    0,
                    0,
                    StitchCommand.COLOR_CHANGE,
                    1
                ),
                EmbroideryPoint(
                    60,
                    40,
                    StitchCommand.JUMP,
                    1
                ),
                EmbroideryPoint(
                    80,
                    60,
                    StitchCommand.STITCH,
                    1
                ),
                EmbroideryPoint(
                    80,
                    60,
                    StitchCommand.END,
                    1
                )
            )

        return EmbroideryDesign(
            fileName = "teste.dst",
            format = "DST",
            label = "FIOLAB",
            points = points,
            bounds =
                EmbroideryBounds(
                    minXUnits = 0,
                    maxXUnits = 120,
                    minYUnits = 0,
                    maxYUnits = 80
                ),
            stitchCount = 6,
            jumpCount = 1,
            colorChanges = 1,
            endFound = true,
            sourceBytes =
                ByteArray(0),
            threadColors =
                listOf(
                    0xE6BE70,
                    0x2A9D8F
                )
        )
    }
}
