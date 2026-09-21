package com.timachado.fiolab

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class AppSmokeTest {
    @get:Rule
    val composeRule =
        createAndroidComposeRule<
            MainActivity
        >()

    @Test
    fun appStartsAndAccountNavigationKeepsExpectedAboutContent() {
        composeRule
            .onNodeWithText(
                "Início"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "Conta"
            )
            .assertIsDisplayed()
            .performClick()

        composeRule
            .onNodeWithText(
                "Minha Conta"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "Sobre o FioLab"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "Desenvolvido por T.I. Machado — Soluções em Tecnologia"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "Conhecer T.I. Machado"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "Crédito de desenvolvimento exibido de forma discreta dentro do aplicativo."
            )
            .assertDoesNotExist()
    }
}
