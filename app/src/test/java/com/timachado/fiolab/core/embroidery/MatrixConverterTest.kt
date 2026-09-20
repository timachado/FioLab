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
    fun jefAndPesReaderKeepPositiveYConvention() {
        val source = sampleDesign()

        val pes =
            MatrixConverter.convert(
                source,
                "PES"
            ).getOrThrow()

        val parsed =
            EmbroideryIoParser.parse(
                pes.fileName,
                pes.bytes
            ) as
                EmbroideryLoadResult.Success

        assertTrue(
            parsed.design.bounds.maxYUnits > 0
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
