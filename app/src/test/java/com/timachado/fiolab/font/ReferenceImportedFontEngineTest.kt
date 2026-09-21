package com.timachado.fiolab.font

import com.timachado.fiolab.core.embroidery.StitchCommand
import org.junit.Assert.assertEquals
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
    fun satinObjectsFollowStandardLeftToRightOrder() {
        val points =
            ReferenceImportedFontEngine
                .debugStandardSewingOrderPath()

        val jumpTargets =
            points
                .mapIndexedNotNull {
                        index,
                        point ->
                    if (
                        point.command !=
                            StitchCommand.JUMP
                    ) {
                        return@mapIndexedNotNull null
                    }

                    val next =
                        points.getOrNull(
                            index +
                                1
                        )

                    if (
                        next?.command ==
                            StitchCommand.JUMP
                    ) {
                        null
                    } else {
                        point
                    }
                }

        assertEquals(
            3,
            jumpTargets.size
        )

        assertTrue(
            jumpTargets[0].xUnits <
                jumpTargets[1].xUnits
        )

        assertTrue(
            jumpTargets[1].xUnits <
                jumpTargets[2].xUnits
        )
    }

    @Test
    fun connectedContinuationIsSewnBeforeDisconnectedRegion() {
        val points =
            ReferenceImportedFontEngine
                .debugConnectivityAwareSewingPath()

        val jumpTargets =
            points
                .mapIndexedNotNull {
                        index,
                        point ->
                    if (
                        point.command !=
                            StitchCommand.JUMP
                    ) {
                        return@mapIndexedNotNull null
                    }

                    val next =
                        points.getOrNull(
                            index +
                                1
                        )

                    if (
                        next?.command ==
                            StitchCommand.JUMP
                    ) {
                        null
                    } else {
                        point
                    }
                }

        assertEquals(
            "Deve existir apenas o posicionamento inicial e um reposicionamento para a região desconectada.",
            2,
            jumpTargets.size
        )

        assertTrue(
            "A primeira região deve continuar começando na esquerda.",
            jumpTargets.first()
                .xUnits <=
                20
        )

        val firstDisconnectedJumpIndex =
            points.indexOfFirst {
                    point ->
                point.command ==
                    StitchCommand.JUMP &&
                    point.yUnits >=
                        30
            }

        assertTrue(
            "A região conectada à direita deve ser bordada antes do salto para a região superior.",
            firstDisconnectedJumpIndex >
                0 &&
                points
                    .take(
                        firstDisconnectedJumpIndex
                    )
                    .any {
                        it.command ==
                            StitchCommand.STITCH &&
                        it.xUnits >=
                            45
                    }
        )

        assertTrue(
            "O segundo destino deve ser a região realmente desconectada.",
            jumpTargets.last()
                .yUnits >=
                95
        )
    }

    @Test
    fun firstSatinRegionStartsAtLeftEdgeEvenWhenHintIsMisleading() {
        val points =
            ReferenceImportedFontEngine
                .debugVisualStartPath()

        assertTrue(
            points.isNotEmpty()
        )

        val first =
            points.first()

        assertEquals(
            StitchCommand.JUMP,
            first.command
        )

        assertTrue(
            "O primeiro ponto deve começar na região esquerda da letra.",
            first.xUnits <=
                20
        )
    }

    @Test
    fun connectedColumnsUseHiddenRunningConnector() {
        val points =
            ReferenceImportedFontEngine
                .debugReferencePath(
                    connected =
                        true,
                    includeUnderlay =
                        false
                )

        assertEquals(
            StitchCommand.JUMP,
            points.first()
                .command
        )

        assertTrue(
            points
                .drop(
                    1
                )
                .none {
                    it.command ==
                        StitchCommand.JUMP ||
                        it.command ==
                        StitchCommand.TRIM
                }
        )
    }

    @Test
    fun disconnectedColumnsJumpInsteadOfCrossingEmptyArea() {
        val points =
            ReferenceImportedFontEngine
                .debugReferencePath(
                    connected =
                        false,
                    includeUnderlay =
                        false
                )

        assertTrue(
            points.count {
                it.command ==
                    StitchCommand.JUMP
            } >=
                2
        )
    }

    @Test
    fun satinUsesOneAlternatingStitchPerSampleRow() {
        val points =
            ReferenceImportedFontEngine
                .debugReferencePath(
                    connected =
                        true,
                    includeUnderlay =
                        false
                )

        val stitches =
            points.count {
                it.command ==
                    StitchCommand.STITCH
            }

        // 4 linhas em cada coluna + ligação contínua entre as colunas.
        assertTrue(
            stitches in
                9..12
        )
    }

    @Test
    fun edgeRunUnderlayAddsSingleOutAndBackPass() {
        val without =
            ReferenceImportedFontEngine
                .debugReferencePath(
                    connected =
                        true,
                    includeUnderlay =
                        false
                )

        val with =
            ReferenceImportedFontEngine
                .debugReferencePath(
                    connected =
                        true,
                    includeUnderlay =
                        true
                )

        assertTrue(
            with.count {
                it.command ==
                    StitchCommand.STITCH
            } >
                without.count {
                    it.command ==
                        StitchCommand.STITCH
                }
        )

        assertTrue(
            with.none {
                it.command ==
                    StitchCommand.TRIM
            }
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
