package com.timachado.fiolab.core.embroidery

import org.junit.Assert.assertTrue
import org.junit.Test

class TextHoopAutoFitTest {

    @Test
    fun fillsSafeAreaWithoutOverflowing100Hoop() {
        val hoop =
            HoopProfile.H100X100

        val fitted =
            TextHoopAutoFit
                .fit(
                    hoop
                ) {
                        height ->
                    TextLayoutGenerator
                        .generate(
                            TextLayoutOptions(
                                textOptions =
                                    TextMatrixOptions(
                                        text =
                                            "MARIA",
                                        heightMm =
                                            height,
                                        spacingMm =
                                            0f,
                                        style =
                                            TextStitchStyle.RUNNING,
                                        hoopProfile =
                                            hoop
                                    )
                            )
                        )
                }
                .getOrThrow()

        assertTrue(
            fitted.heightMm >
                18f
        )

        assertTrue(
            HoopValidator
                .validate(
                    fitted.design,
                    hoop
                )
                .fits
        )

        assertTrue(
            TextHoopAutoFit
                .dominantFillRatio(
                    fitted
                ) >=
                0.94f
        )
    }

    @Test
    fun largerHoopProducesLargerAutomaticText() {
        fun fitted(
            hoop: HoopProfile
        ) =
            TextHoopAutoFit
                .fit(
                    hoop
                ) {
                        height ->
                    TextLayoutGenerator
                        .generate(
                            TextLayoutOptions(
                                textOptions =
                                    TextMatrixOptions(
                                        text =
                                            "MARIA",
                                        heightMm =
                                            height,
                                        spacingMm =
                                            0f,
                                        style =
                                            TextStitchStyle.RUNNING,
                                        hoopProfile =
                                            hoop
                                    )
                            )
                        )
                }
                .getOrThrow()

        val small =
            fitted(
                HoopProfile.H100X100
            )

        val large =
            fitted(
                HoopProfile.H130X180
            )

        assertTrue(
            large.heightMm >
                small.heightMm
        )

        assertTrue(
            HoopValidator
                .validate(
                    large.design,
                    HoopProfile.H130X180
                )
                .fits
        )
    }

    @Test
    fun automaticFitAvoidsRepeatedExpensiveGeneration() {
        val hoop =
            HoopProfile.H100X100

        var calls =
            0

        val fitted =
            TextHoopAutoFit
                .fit(
                    hoop
                ) {
                        height ->
                    calls++

                    TextLayoutGenerator
                        .generate(
                            TextLayoutOptions(
                                textOptions =
                                    TextMatrixOptions(
                                        text =
                                            "MARIA",
                                        heightMm =
                                            height,
                                        spacingMm =
                                            0f,
                                        style =
                                            TextStitchStyle.RUNNING,
                                        hoopProfile =
                                            hoop
                                    )
                            )
                        )
                }
                .getOrThrow()

        assertTrue(
            "O auto-fit deve evitar multiplicar um gerador caro.",
            calls <=
                7
        )

        assertTrue(
            HoopValidator
                .validate(
                    fitted.design,
                    hoop
                )
                .fits
        )
    }


    @Test
    fun narrowInterpolationWindowDoesNotThrow() {
        val hoop =
            HoopProfile.H100X100

        fun designFor(
            height: Float
        ): EmbroideryDesign {
            val halfWidth =
                (
                    height *
                        2.65f *
                        10f /
                        2f
                    ).toInt()

            val halfHeight =
                (
                    height *
                        10f /
                        2f
                    ).toInt()

            val points =
                listOf(
                    EmbroideryPoint(
                        -halfWidth,
                        -halfHeight,
                        StitchCommand.STITCH,
                        0
                    ),
                    EmbroideryPoint(
                        halfWidth,
                        halfHeight,
                        StitchCommand.STITCH,
                        0
                    ),
                    EmbroideryPoint(
                        halfWidth,
                        halfHeight,
                        StitchCommand.END,
                        0
                    )
                )

            return EmbroideryDesign(
                fileName =
                    "fit.dst",
                format =
                    "DST",
                label =
                    "FIT",
                points =
                    points,
                bounds =
                    EmbroideryBounds(
                        minXUnits =
                            -halfWidth,
                        maxXUnits =
                            halfWidth,
                        minYUnits =
                            -halfHeight,
                        maxYUnits =
                            halfHeight
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
                    ByteArray(
                        0
                    ),
                isModified =
                    true,
                hoopProfile =
                    hoop
            )
        }

        val fitted =
            TextHoopAutoFit
                .fit(
                    hoop =
                        hoop,
                    minHeightMm =
                        16.5f,
                    maxHeightMm =
                        17.0f
                ) {
                        height ->
                    Result.success(
                        designFor(
                            height
                        )
                    )
                }

        assertTrue(
            "Uma janela estreita de interpolação não pode gerar coerceIn com faixa vazia.",
            fitted.isSuccess
        )
    }

}