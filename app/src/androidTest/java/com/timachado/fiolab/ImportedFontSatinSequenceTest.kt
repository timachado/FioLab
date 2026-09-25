package com.timachado.fiolab

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timachado.fiolab.core.embroidery.SatinUnderlayMode
import com.timachado.fiolab.core.embroidery.StitchCommand
import com.timachado.fiolab.core.embroidery.TextMatrixOptions
import com.timachado.fiolab.core.embroidery.TextStitchStyle
import com.timachado.fiolab.font.ImportedFont
import com.timachado.fiolab.font.ImportedFontMatrixGenerator
import java.io.File
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportedFontSatinSequenceTest {

    private fun systemFont(): ImportedFont {
        val fontFile =
            listOf(
                File("/system/fonts/Roboto-Regular.ttf"),
                File("/system/fonts/NotoSans-Regular.ttf")
            ).firstOrNull {
                it.isFile
            } ?: error(
                "Fonte de sistema não encontrada no emulador."
            )

        return ImportedFont(
            id = fontFile.name,
            displayName = "Sistema",
            fileName = fontFile.name,
            extension = "ttf",
            absolutePath = fontFile.absolutePath
        )
    }

    @Test
    fun importedSatinNeverReturnsToPreviousGlyphAfterAdvancing() {
        val design =
            ImportedFontMatrixGenerator
                .generateText(
                    font = systemFont(),
                    text = "AB",
                    options =
                        TextMatrixOptions(
                            text = "AB",
                            heightMm = 25f,
                            spacingMm = 15f,
                            style = TextStitchStyle.SATIN,
                            satinUnderlayMode =
                                SatinUnderlayMode.CENTER,
                            enforceHoop = false
                        )
                )
                .getOrThrow()

        val sewn =
            design.points.filter {
                it.command ==
                    StitchCommand.STITCH
            }

        assertTrue(
            "A matriz precisa conter pontos Satin.",
            sewn.isNotEmpty()
        )

        val sortedX =
            sewn
                .map {
                    it.xUnits
                }
                .distinct()
                .sorted()

        val largestGap =
            sortedX
                .zipWithNext()
                .maxByOrNull {
                    it.second -
                        it.first
                }
                ?: error(
                    "Não foi possível separar os dois glifos."
                )

        assertTrue(
            "O teste precisa manter separação clara entre os glifos.",
            largestGap.second -
                largestGap.first >=
                50
        )

        val boundary =
            (
                largestGap.first +
                    largestGap.second
                ) /
                2

        var enteredSecondGlyph =
            false

        sewn.forEach {
                point ->
            if (
                point.xUnits >
                    boundary
            ) {
                enteredSecondGlyph =
                    true
            } else if (
                enteredSecondGlyph
            ) {
                error(
                    "A sequência voltou ao primeiro glifo depois de iniciar o segundo."
                )
            }
        }

        assertTrue(
            "A sequência deve alcançar o segundo glifo.",
            enteredSecondGlyph
        )
    }

    @Test
    fun importedSatinUsesReferenceStartUnderlayAndFinalLock() {
        val withUnderlay =
            ImportedFontMatrixGenerator
                .generateText(
                    font = systemFont(),
                    text = "I",
                    options =
                        TextMatrixOptions(
                            text = "I",
                            heightMm = 25f,
                            style = TextStitchStyle.SATIN,
                            satinDensityMm = 0.4f,
                            satinPullCompensationMm = 0.2f,
                            satinUnderlayMode =
                                SatinUnderlayMode.CENTER,
                            enforceHoop = false
                        )
                )
                .getOrThrow()

        val withoutUnderlay =
            ImportedFontMatrixGenerator
                .generateText(
                    font = systemFont(),
                    text = "I",
                    options =
                        TextMatrixOptions(
                            text = "I",
                            heightMm = 25f,
                            style = TextStitchStyle.SATIN,
                            satinDensityMm = 0.4f,
                            satinPullCompensationMm = 0.2f,
                            satinUnderlayMode =
                                SatinUnderlayMode.NONE,
                            enforceHoop = false
                        )
                )
                .getOrThrow()

        val commands =
            withUnderlay.points.filter {
                it.command !=
                    StitchCommand.END
            }

        assertTrue(
            "O primeiro comando do bloco Satin deve ser um salto até a entrada.",
            commands.firstOrNull()?.command ==
                StitchCommand.JUMP
        )

        assertTrue(
            "O underlay central de referência deve adicionar pontos antes da cobertura.",
            withUnderlay.stitchCount >
                withoutUnderlay.stitchCount
        )

        val sewn =
            withUnderlay.points.filter {
                it.command ==
                    StitchCommand.STITCH
            }

        assertTrue(
            "A matriz precisa terminar com a trava de três pontos.",
            sewn.size >=
                3
        )

        val lockA =
            sewn[
                sewn.size -
                    3
            ]

        val lockInside =
            sewn[
                sewn.size -
                    2
            ]

        val lockBack =
            sewn.last()

        assertEquals(
            "A trava final deve voltar exatamente ao ponto A.",
            lockA.xUnits,
            lockBack.xUnits
        )

        assertEquals(
            "A trava final deve voltar exatamente ao ponto A.",
            lockA.yUnits,
            lockBack.yUnits
        )

        val lockLength =
            hypot(
                (
                    lockInside.xUnits -
                        lockA.xUnits
                    ).toDouble(),
                (
                    lockInside.yUnits -
                        lockA.yUnits
                    ).toDouble()
            )

        assertTrue(
            "A trava interna deve medir aproximadamente 0,6 mm.",
            lockLength in
                4.0..8.0
        )

        assertTrue(
            "Depois da trava final não deve existir uma passada de contorno.",
            withUnderlay.points
                .dropWhile {
                    it !==
                        lockBack
                }
                .drop(1)
                .all {
                    it.command ==
                        StitchCommand.END
                }
        )
    }

    @Test
    fun importedSatinDoesNotForceTrimBetweenNearbyLetters() {
        val design =
            ImportedFontMatrixGenerator
                .generateText(
                    font = systemFont(),
                    text = "II",
                    options =
                        TextMatrixOptions(
                            text = "II",
                            heightMm = 12f,
                            spacingMm = 0f,
                            style = TextStitchStyle.SATIN,
                            satinUnderlayMode =
                                SatinUnderlayMode.CENTER,
                            enforceHoop = false
                        )
                )
                .getOrThrow()

        val trims =
            design.points.count {
                it.command ==
                    StitchCommand.TRIM
            }

        assertFalse(
            "Letras próximas não devem receber TRIM obrigatório só por mudar de glifo.",
            trims >
                0
        )
    }
}
