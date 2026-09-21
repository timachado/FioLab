package com.timachado.fiolab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FioLabAboutTest {
    @Test
    fun developerCreditUsesOfficialBranding() {
        assertEquals(
            "T.I. Machado",
            FioLabAbout
                .developerName
        )

        assertTrue(
            FioLabAbout
                .developerCredit
                .contains(
                    "Soluções em Tecnologia"
                )
        )
    }

    @Test
    fun developerWebsiteUsesHttps() {
        assertTrue(
            FioLabAbout
                .developerWebsite
                .startsWith(
                    "https://"
                )
        )
    }
}
