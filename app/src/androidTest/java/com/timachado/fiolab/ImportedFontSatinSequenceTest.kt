package com.timachado.fiolab

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timachado.fiolab.core.embroidery.SatinUnderlayMode
import com.timachado.fiolab.core.embroidery.StitchCommand
import com.timachado.fiolab.core.embroidery.TextMatrixOptions
import com.timachado.fiolab.core.embroidery.TextStitchStyle
import com.timachado.fiolab.font.ImportedFont
import com.timachado.fiolab.font.ImportedFontMatrixGenerator
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportedFontSatinSequenceTest {

    @Test
    fun importedSatinNeverReturnsToPreviousGlyphAfterAdvancing() {
        val fontFile =
            listOf(
                File("/system/fonts/Roboto-Regular.ttf"),
                File("/system/fonts/NotoSans-Regular.ttf")
            ).firstOrNull {
                it.isFile
            } ?: error(
                "Fonte de sistema não encontrada no emulador."
            )

        val font =
            ImportedFont(
                id = fontFile.name,
                displayName = "Sistema",
                fileName = fontFile.name,
                extension = "ttf",
                absolutePath = fontFile.absolutePath
            )

        val design =
            ImportedFontMatrixGenerator
                .generateText(
                    font = font,
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

        val stitches =
            design.points.filter {
                it.command ==
                    StitchCommand.STITCH
            }

        assertTrue(
            "A matriz precisa conter pontos Satin.",
            stitches.isNotEmpty()
        )

        val sortedX =
            stitches
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

        val gapUnits =
            largestGap.second -
                largestGap.first

        assertTrue(
            "O teste precisa manter uma separação clara entre os glifos.",
            gapUnits >=
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

        stitches.forEach {
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
                    "A sequência voltou para o primeiro glifo depois de iniciar o segundo."
                )
            }
        }

        assertTrue(
            "A sequência deve alcançar o segundo glifo.",
            enteredSecondGlyph
        )
    }
}
