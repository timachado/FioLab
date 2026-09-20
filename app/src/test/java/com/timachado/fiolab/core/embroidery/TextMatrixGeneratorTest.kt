package com.timachado.fiolab.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatrixGeneratorTest {

    @Test
    fun generatesRealStitchesForName() {
        val design =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "MARIA",
                        heightMm = 10f,
                        outputFormat =
                            "DST"
                    )
                )
                .getOrThrow()

        assertEquals(
            "MARIA",
            design.label
        )

        assertEquals(
            "DST",
            design.format
        )

        assertTrue(
            design.stitchCount > 20
        )

        assertTrue(
            design.jumpCount > 0
        )

        assertTrue(
            design.bounds.widthMm >
                20f
        )

        assertTrue(
            design.isModified
        )
    }

    @Test
    fun supportsPortugueseAccents() {
        val design =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "JOÃO Ç",
                        heightMm = 12f,
                        outputFormat =
                            "PES"
                    )
                )
                .getOrThrow()

        assertEquals(
            "PES",
            design.format
        )

        assertTrue(
            design.bounds.maxYUnits >
                120
        )

        assertTrue(
            design.bounds.minYUnits <
                0
        )
    }

    @Test
    fun presetsChangeWidth() {
        val compact =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "ANA",
                        font =
                            EmbroideryFontPreset
                                .COMPACT
                    )
                )
                .getOrThrow()

        val wide =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "ANA",
                        font =
                            EmbroideryFontPreset
                                .WIDE
                    )
                )
                .getOrThrow()

        assertTrue(
            wide.bounds.widthMm >
                compact.bounds.widthMm
        )
    }

    @Test
    fun generatedNameExportsToAllReleasedFormats() {
        val source =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "FIO",
                        heightMm = 8f
                    )
                )
                .getOrThrow()

        MatrixConverter
            .supportedFormats
            .forEach { format ->
                val output =
                    MatrixConverter
                        .convert(
                            source,
                            format,
                            "nome-teste"
                        )
                        .getOrThrow()

                assertTrue(
                    output.bytes
                        .isNotEmpty()
                )
            }
    }
}
