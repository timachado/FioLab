package com.timachado.fiolab

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timachado.fiolab.core.embroidery.SatinUnderlayMode
import com.timachado.fiolab.core.embroidery.StitchCommand
import com.timachado.fiolab.core.embroidery.TextMatrixOptions
import com.timachado.fiolab.core.embroidery.TextStitchStyle
import com.timachado.fiolab.font.ImportedFont
import com.timachado.fiolab.font.ImportedFontMatrixGenerator
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportedFontSatinSequenceTest {

    @Test
    fun wholeWordFinishesFirstGlyphBeforeAdvancing() {
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

        val base =
            TextMatrixOptions(
                text = "Ma",
                heightMm = 25f,
                style = TextStitchStyle.SATIN,
                satinUnderlayMode = SatinUnderlayMode.CENTER,
                enforceHoop = false
            )

        val firstGlyph =
            ImportedFontMatrixGenerator
                .generateGlyph(
                    font = font,
                    char = 'M',
                    options =
                        base.copy(
                            text = "M"
                        )
                )
                .getOrThrow()
                .points
                .filter {
                    it.command !=
                        StitchCommand.END
                }

        val word =
            ImportedFontMatrixGenerator
                .generateText(
                    font = font,
                    text = "Ma",
                    options = base
                )
                .getOrThrow()
                .points

        val firstSeparator =
            word.indexOfFirst {
                it.command ==
                    StitchCommand.TRIM
            }

        assertTrue(
            "A palavra precisa separar o primeiro glifo antes do segundo.",
            firstSeparator >
                0
        )

        val firstWordBlock =
            word.take(
                firstSeparator
            )

        assertEquals(
            "O primeiro glifo da palavra deve terminar completamente antes do TRIM.",
            firstGlyph.map {
                it.command
            },
            firstWordBlock.map {
                it.command
            }
        )

        assertTrue(
            "Depois do TRIM devem existir pontos da próxima letra.",
            word.drop(
                firstSeparator +
                    1
            ).any {
                it.command ==
                    StitchCommand.STITCH
            }
        )
    }
}
