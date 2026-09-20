package com.timachado.fiolab.core.embroidery

import org.junit.Assert.assertTrue
import org.junit.Test

class TextLayoutGeneratorTest {

    private fun base(
        text: String =
            "MARIA"
    ) =
        TextMatrixOptions(
            text = text,
            heightMm = 12f,
            style =
                TextStitchStyle.SATIN,
            font =
                EmbroideryFontPreset
                    .ELEGANT,
            hoopProfile =
                HoopProfile.H140X200
        )

    @Test
    fun arcUpMovesMiddleLettersHigherThanStraight() {
        val straight =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base(),
                        layoutMode =
                            TextLayoutMode
                                .STRAIGHT
                    )
                )
                .getOrThrow()

        val curved =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base(),
                        layoutMode =
                            TextLayoutMode
                                .ARC_UP,
                        arcHeightMm =
                            12f
                    )
                )
                .getOrThrow()

        assertTrue(
            curved.bounds.heightMm >
                straight.bounds.heightMm
        )
    }

    @Test
    fun arcUpAndDownGenerateDifferentGeometry() {
        val up =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base(),
                        layoutMode =
                            TextLayoutMode
                                .ARC_UP,
                        arcHeightMm =
                            10f
                    )
                )
                .getOrThrow()

        val down =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base(),
                        layoutMode =
                            TextLayoutMode
                                .ARC_DOWN,
                        arcHeightMm =
                            10f
                    )
                )
                .getOrThrow()

        assertTrue(
            up.points !=
                down.points
        )
    }

    @Test
    fun individualLetterRotationChangesGeometry() {
        val normal =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base("ANA")
                    )
                )
                .getOrThrow()

        val edited =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base("ANA"),
                        letterAdjustments =
                            listOf(
                                LetterAdjustment(
                                    sourceIndex =
                                        1,
                                    rotationDegrees =
                                        25f
                                )
                            )
                    )
                )
                .getOrThrow()

        assertTrue(
            normal.points !=
                edited.points
        )
    }

    @Test
    fun individualSpacingCanWidenText() {
        val normal =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base("FIO")
                    )
                )
                .getOrThrow()

        val spaced =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base("FIO"),
                        letterAdjustments =
                            listOf(
                                LetterAdjustment(
                                    sourceIndex =
                                        0,
                                    spacingAfterMm =
                                        6f
                                )
                            )
                    )
                )
                .getOrThrow()

        assertTrue(
            spaced.bounds.widthMm >
                normal.bounds.widthMm
        )
    }

    @Test
    fun finalTextIsAutomaticallyCentered() {
        val design =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base("FIOLAB"),
                        layoutMode =
                            TextLayoutMode
                                .ARC_UP,
                        arcHeightMm =
                            8f
                    )
                )
                .getOrThrow()

        val centerX =
            (
                design.bounds.minXUnits +
                    design.bounds.maxXUnits
                ) /
                2f

        val centerY =
            (
                design.bounds.minYUnits +
                    design.bounds.maxYUnits
                ) /
                2f

        assertTrue(
            kotlin.math.abs(
                centerX
            ) <= 1f
        )

        assertTrue(
            kotlin.math.abs(
                centerY
            ) <= 1f
        )
    }

    @Test
    fun curvedTextExportsToReleasedFormats() {
        val source =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base("FIO"),
                        layoutMode =
                            TextLayoutMode
                                .ARC_DOWN,
                        arcHeightMm =
                            6f
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
                            "curva-teste"
                        )
                        .getOrThrow()

                assertTrue(
                    converted.bytes
                        .isNotEmpty()
                )
            }
    }
}
