package com.timachado.fiolab.core.embroidery

import org.junit.Assert.assertTrue
import org.junit.Test

class MatrixStressTest {
    @Test
    fun maximumBuiltInNameStillGeneratesWithinSafeBudget() {
        val text =
            "ABCDEFGHIJKLMNOPQRSTUVWX"

        val design =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text =
                            text,
                        heightMm =
                            40f,
                        spacingMm =
                            8f,
                        style =
                            TextStitchStyle.SATIN,
                        satinWidthMm =
                            6f,
                        satinDensityMm =
                            0.3f,
                        satinPullCompensationMm =
                            1f,
                        satinUnderlayMode =
                            SatinUnderlayMode.BOTH,
                        outputFormat =
                            "DST"
                    )
                )
                .getOrThrow()

        assertTrue(
            design.stitchCount >
                0
        )

        assertTrue(
            design.points
                .size <=
                EmbroideryStressPolicy
                    .MAX_GENERATED_COMMANDS
        )
    }

    @Test
    fun denseNameSurvivesConvertAndReparseInEveryReleasedFormat() {
        val source =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text =
                            "FIOLABTESTE1234567890ABC",
                        heightMm =
                            22f,
                        style =
                            TextStitchStyle.SATIN,
                        satinDensityMm =
                            0.3f,
                        satinUnderlayMode =
                            SatinUnderlayMode.BOTH
                    )
                )
                .getOrThrow()

        MatrixConverter
            .supportedFormats
            .forEach {
                    format ->
                val converted =
                    MatrixConverter
                        .convert(
                            source,
                            format,
                            "stress"
                        )
                        .getOrThrow()

                val reparsed =
                    when (
                        format
                    ) {
                        "DST" ->
                            DstParser
                                .parse(
                                    converted.fileName,
                                    converted.bytes
                                )

                        "PES",
                        "JEF" ->
                            EmbroideryIoParser
                                .parse(
                                    converted.fileName,
                                    converted.bytes
                                )

                        else ->
                            error(
                                "Formato inesperado."
                            )
                    }

                assertTrue(
                    reparsed is
                        EmbroideryLoadResult.Success
                )

                val design =
                    (
                        reparsed as
                            EmbroideryLoadResult.Success
                        ).design

                assertTrue(
                    design.stitchCount >
                        0
                )
            }
    }

    @Test
    fun pathologicalMoveIsRejectedBeforeHugeExpansion() {
        val source =
            EmbroideryDesign(
                fileName =
                    "patologico.dst",
                format =
                    "DST",
                label =
                    "Patológico",
                points =
                    listOf(
                        EmbroideryPoint(
                            Int.MIN_VALUE /
                                2,
                            0,
                            StitchCommand.STITCH,
                            0
                        ),
                        EmbroideryPoint(
                            Int.MAX_VALUE /
                                2,
                            0,
                            StitchCommand.STITCH,
                            0
                        ),
                        EmbroideryPoint(
                            Int.MAX_VALUE /
                                2,
                            0,
                            StitchCommand.END,
                            0
                        )
                    ),
                bounds =
                    EmbroideryBounds(
                        Int.MIN_VALUE /
                            2,
                        Int.MAX_VALUE /
                            2,
                        0,
                        0
                    ),
                stitchCount =
                    2,
                jumpCount =
                    0,
                colorChanges =
                    0,
                endFound =
                    true,
                sourceBytes =
                    ByteArray(0)
            )

        assertTrue(
            MatrixConverter
                .convert(
                    source,
                    "DST"
                )
                .isFailure
        )
    }
}
