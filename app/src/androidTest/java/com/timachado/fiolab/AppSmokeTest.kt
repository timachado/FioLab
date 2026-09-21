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
    @Test
    fun fontLibraryMakesLocalStorageScopeExplicit() {
        composeRule
            .onNodeWithText(
                "Fontes"
            )
            .assertIsDisplayed()
            .performClick()

        composeRule
            .onNodeWithText(
                "Biblioteca de Fontes"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "Fontes FioLab + TTF/OTF deste aparelho"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "Minhas fontes neste aparelho"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "A fonte TTF/OTF fica salva somente neste aparelho e disponível em Criar Nome até você excluir. Ela não é enviada nem sincronizada com sua conta FioLab. Limite: 12 MB por fonte."
            )
            .assertIsDisplayed()
    }

}
