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

    @Test
    fun importedSatinStartsWithSparseFixationAndEndsOnVectorOutline() {
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
                    text = "O",
                    options =
                        TextMatrixOptions(
                            text = "O",
                            heightMm = 25f,
                            style = TextStitchStyle.SATIN,
                            satinUnderlayMode =
                                SatinUnderlayMode.ZIGZAG,
                            enforceHoop = false
                        )
                )
                .getOrThrow()

        val segments =
            mutableListOf<
                MutableList<
                    com.timachado.fiolab
                        .core
                        .embroidery
                        .EmbroideryPoint
                >
            >()

        var current =
            mutableListOf<
                com.timachado.fiolab
                    .core
                    .embroidery
                    .EmbroideryPoint
            >()

        design.points.forEach {
                point ->
            if (
                point.command ==
                    StitchCommand.TRIM
            ) {
                if (
                    current.isNotEmpty()
                ) {
                    segments +=
                        current

                    current =
                        mutableListOf()
                }
            } else if (
                point.command ==
                    StitchCommand.STITCH
            ) {
                current +=
                    point
            }
        }

        if (
            current.isNotEmpty()
        ) {
            segments +=
                current
        }

        assertTrue(
            "O Satin em camadas precisa gerar fixação, suporte, cobertura e contorno.",
            segments.size >=
                4
        )

        val fixation =
            segments.first()

        assertTrue(
            "A camada inicial de fixação precisa conter pontos corridos.",
            fixation.size >=
                2
        )

        val fixationAverageStep =
            fixation
                .zipWithNext()
                .map {
                    (first, second) ->
                    kotlin.math.hypot(
                        (
                            second.xUnits -
                                first.xUnits
                            ).toDouble(),
                        (
                            second.yUnits -
                                first.yUnits
                            ).toDouble()
                    )
                }
                .average()

        val laterAverageSteps =
            segments
                .drop(1)
                .mapNotNull {
                        segment ->
                    val distances =
                        segment
                            .zipWithNext()
                            .map {
                                (first, second) ->
                                kotlin.math.hypot(
                                    (
                                        second.xUnits -
                                            first.xUnits
                                        ).toDouble(),
                                    (
                                        second.yUnits -
                                            first.yUnits
                                        ).toDouble()
                                )
                            }

                    if (
                        distances.isEmpty()
                    ) {
                        null
                    } else {
                        distances.average()
                    }
                }

        val densestLaterAverage =
            laterAverageSteps
                .minOrNull()
                ?: error(
                    "As camadas posteriores não geraram pontos suficientes."
                )

        assertTrue(
            "A fixação deve ser mais esparsa que a cobertura Satin.",
            fixationAverageStep >
                densestLaterAverage *
                    1.35
        )

        assertTrue(
            "A fixação deve usar menos pontos que a camada mais densa.",
            fixation.size <
                segments
                    .drop(1)
                    .maxOf {
                        it.size
                    }
        )

        val outline =
            segments.last()

        val guide =
            design.guidePoints

        assertTrue(
            "O contorno vetorial precisa existir para validar o fechamento.",
            guide.isNotEmpty()
        )

        val nearGuide =
            outline.count {
                    point ->
                guide.any {
                        guidePoint ->
                    kotlin.math.hypot(
                        (
                            point.xUnits -
                                guidePoint.xUnits
                            ).toDouble(),
                        (
                            point.yUnits -
                                guidePoint.yUnits
                            ).toDouble()
                    ) <=
                        14.0
                }
            }

        assertTrue(
            "A última camada deve acompanhar o contorno vetorial da letra.",
            nearGuide.toDouble() /
                outline.size
                    .coerceAtLeast(
                        1
                    ) >=
                0.70
        )
    }

}
