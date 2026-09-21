package com.timachado.fiolab.font

import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceImportedFontEngineTest {

    @Test
    fun samplerChoosesNarrowerAxisForWideRectangle() {
        val widths =
            ReferenceImportedFontEngine
                .debugColumnWidths(
                    polygon =
                        listOf(
                            0f to 0f,
                            100f to 0f,
                            100f to 20f,
                            0f to 20f
                        )
                )

        assertTrue(
            widths.isNotEmpty()
        )

        assertTrue(
            widths.maxOrNull()!! <=
                21f
        )
    }

    @Test
    fun wideSatinBandIsSplitBelowMaximumWidth() {
        val widths =
            ReferenceImportedFontEngine
                .debugColumnWidths(
                    polygon =
                        listOf(
                            0f to 0f,
                            100f to 0f,
                            100f to 100f,
                            0f to 100f
                        ),
                    maxWidthMm =
                        7f
                )

        assertTrue(
            widths.isNotEmpty()
        )

        assertTrue(
            widths.maxOrNull()!! <=
                70.1f
        )
    }

    @Test
    fun shortTravelUsesContinuousStitches() {
        assertTrue(
            ReferenceImportedFontEngine
                .debugTravelCommandForDistance(
                    30f
                ) ==
                com.timachado.fiolab
                    .core.embroidery
                    .StitchCommand.STITCH
        )
    }

    @Test
    fun separatedTravelKeepsJump() {
        assertTrue(
            ReferenceImportedFontEngine
                .debugTravelCommandForDistance(
                    80f
                ) ==
                com.timachado.fiolab
                    .core.embroidery
                    .StitchCommand.JUMP
        )
    }

    @Test
    fun disconnectedShapesCreateMoreThanOneColumn() {
        val count =
            ReferenceImportedFontEngine
                .debugColumnCount(
                    polygons =
                        listOf(
                            listOf(
                                0f to 0f,
                                20f to 0f,
                                20f to 80f,
                                0f to 80f
                            ),
                            listOf(
                                40f to 0f,
                                60f to 0f,
                                60f to 80f,
                                40f to 80f
                            )
                        )
                )

        assertTrue(
            count >=
                2
        )
    }
}
