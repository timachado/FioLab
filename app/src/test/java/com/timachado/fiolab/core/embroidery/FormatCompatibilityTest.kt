package com.timachado.fiolab.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatCompatibilityTest {

    @Test
    fun readsRealJanomeJefSample() {
        val bytes =
            resourceBytes(
                "/samples/random1.jef"
            )

        val result =
            EmbroideryIoParser.parse(
                "random1.jef",
                bytes
            )

        assertTrue(
            result is
                EmbroideryLoadResult.Success
        )

        val design =
            (result as
                EmbroideryLoadResult.Success)
                .design

        assertEquals(
            "JEF",
            design.format
        )

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

    @Test
    fun readsRealBrotherPesSample() {
        val bytes =
            resourceBytes(
                "/samples/random1.pes"
            )

        val result =
            EmbroideryIoParser.parse(
                "random1.pes",
                bytes
            )

        assertTrue(
            result is
                EmbroideryLoadResult.Success
        )

        val design =
            (result as
                EmbroideryLoadResult.Success)
                .design

        assertEquals(
            "PES",
            design.format
        )

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

    private fun resourceBytes(
        path: String
    ): ByteArray =
        javaClass
            .getResourceAsStream(path)
            ?.use {
                it.readBytes()
            }
            ?: error(
                "Recurso de teste ausente: " +
                    path
            )
}
