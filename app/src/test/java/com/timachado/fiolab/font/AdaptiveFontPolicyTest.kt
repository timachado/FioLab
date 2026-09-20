package com.timachado.fiolab.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveFontPolicyTest {

    @Test
    fun thinCurvedGlyphUsesAxisSatin() {
        val technique =
            AdaptiveFontPolicy
                .chooseTechnique(
                    ImportedGlyphMetrics(
                        areaPixels = 1800,
                        skeletonPixels = 220,
                        branchPixels = 8,
                        widthPixels = 120,
                        heightPixels = 160
                    )
                )

        assertEquals(
            ImportedGlyphTechnique.AXIS_SATIN,
            technique
        )
    }

    @Test
    fun boldDisplayGlyphFallsBackToAreaFill() {
        val technique =
            AdaptiveFontPolicy
                .chooseTechnique(
                    ImportedGlyphMetrics(
                        areaPixels = 9000,
                        skeletonPixels = 250,
                        branchPixels = 12,
                        widthPixels = 180,
                        heightPixels = 180
                    )
                )

        assertEquals(
            ImportedGlyphTechnique.AREA_FILL,
            technique
        )
    }

    @Test
    fun highlyBranchedDecorativeGlyphFallsBackToAreaFill() {
        val technique =
            AdaptiveFontPolicy
                .chooseTechnique(
                    ImportedGlyphMetrics(
                        areaPixels = 2600,
                        skeletonPixels = 200,
                        branchPixels = 50,
                        widthPixels = 150,
                        heightPixels = 150
                    )
                )

        assertEquals(
            ImportedGlyphTechnique.AREA_FILL,
            technique
        )
    }

    @Test
    fun degenerateGlyphNeverUsesAxisSatin() {
        val technique =
            AdaptiveFontPolicy
                .chooseTechnique(
                    ImportedGlyphMetrics(
                        areaPixels = 20,
                        skeletonPixels = 0,
                        branchPixels = 0,
                        widthPixels = 3,
                        heightPixels = 3
                    )
                )

        assertEquals(
            ImportedGlyphTechnique.AREA_FILL,
            technique
        )
    }

    @Test
    fun healthyStitchPathPassesPreflight() {
        val quality =
            AdaptiveFontPolicy
                .qualityFromCoordinates(
                    listOf(
                        Triple(0, 0, false),
                        Triple(10, 0, true),
                        Triple(20, 10, true),
                        Triple(30, 10, true),
                        Triple(40, 20, true)
                    )
                )

        assertTrue(
            AdaptiveFontPolicy
                .isAcceptable(
                    quality
                )
        )
    }

    @Test
    fun pathologicalLongStitchFailsPreflight() {
        val quality =
            AdaptiveFontPolicy
                .qualityFromCoordinates(
                    listOf(
                        Triple(0, 0, false),
                        Triple(10, 0, true),
                        Triple(250, 0, true)
                    )
                )

        assertFalse(
            AdaptiveFontPolicy
                .isAcceptable(
                    quality
                )
        )
    }

    @Test
    fun repeatedZeroLengthStitchesFailPreflight() {
        val quality =
            AdaptiveFontPolicy
                .qualityFromCoordinates(
                    listOf(
                        Triple(0, 0, false),
                        Triple(10, 10, true),
                        Triple(10, 10, true),
                        Triple(10, 10, true),
                        Triple(10, 10, true),
                        Triple(12, 12, true)
                    )
                )

        assertFalse(
            AdaptiveFontPolicy
                .isAcceptable(
                    quality
                )
        )
    }
}
