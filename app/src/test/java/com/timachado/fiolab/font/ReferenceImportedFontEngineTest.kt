package com.timachado.fiolab.font

import com.timachado.fiolab.core.embroidery.StitchCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceImportedFontEngineTest {

    @Test
    fun adaptiveSatinChangesDirectionInsideBentStroke() {
        val vectors =
            ReferenceImportedFontEngine
                .debugAdaptiveRowVectors(
                    polygon =
                        listOf(
                            0f to 0f,
                            22f to 0f,
                            22f to 78f,
                            82f to 78f,
                            82f to 100f,
                            0f to 100f
                        )
                )

        assertTrue(
            "O Satin adaptativo deve gerar linhas de direção válidas.",
            vectors.isNotEmpty()
        )

        assertTrue(
            "O trecho vertical deve produzir travessas majoritariamente horizontais.",
            vectors.any {
                    vector ->
                kotlin.math.abs(
                    vector.first
                ) >
                    kotlin.math.abs(
                        vector.second
                    ) *
                        1.5f
            }
        )

        assertTrue(
            "O trecho horizontal deve produzir travessas majoritariamente verticais.",
            vectors.any {
                    vector ->
                kotlin.math.abs(
                    vector.second
                ) >
                    kotlin.math.abs(
                        vector.first
                    ) *
                        1.5f
            }
        )
    }

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

    @Test
    fun compactDetachedDotUsesSingleSatinColumn() {
        val count =
            ReferenceImportedFontEngine
                .debugColumnCount(
                    polygons =
                        listOf(
                            listOf(
                                0f to 0f,
                                18f to 0f,
                                18f to 80f,
                                0f to 80f
                            ),
                            listOf(
                                2f to 100f,
                                16f to 100f,
                                16f to 114f,
                                2f to 114f
                            )
                        )
                )

        assertEquals(
            "Uma haste e um ponto destacado devem gerar duas colunas, sem transformar o ponto em vários ramos radiais.",
            2,
            count
        )
    }


    @Test
    fun branchJunctionDoesNotCreateAbruptSatinFan() {
        val maxTurn =
            ReferenceImportedFontEngine
                .debugAdaptiveMaxTurnDegrees(
                    polygon =
                        listOf(
                            40f to 0f,
                            60f to 0f,
                            60f to 45f,
                            98f to 84f,
                            84f to 100f,
                            51f to 66f,
                            18f to 100f,
                            4f to 86f,
                            40f to 49f
                        )
                )

        assertTrue(
            "Uma bifurcação não pode criar mudança instantânea em leque dentro da mesma coluna Satin.",
            maxTurn <=
                53f
        )
    }


    @Test
    fun directionalBlocksKeepJunctionWidthLocal() {
        val widths =
            ReferenceImportedFontEngine
                .debugColumnWidths(
                    polygon =
                        listOf(
                            0f to 0f,
                            100f to 0f,
                            100f to 20f,
                            60f to 20f,
                            60f to 100f,
                            40f to 100f,
                            40f to 20f,
                            0f to 20f
                        )
                )

        assertTrue(
            "O encontro de dois traços não pode virar uma travessa que atravesse a barra inteira.",
            widths.isNotEmpty() &&
                widths.maxOrNull()!! <=
                    35f
        )
    }


    @Test
    fun junctionCenterIsCoveredByDedicatedSatinBlock() {
        val distance =
            ReferenceImportedFontEngine
                .debugNearestSatinRowCenterDistance(
                    polygon =
                        listOf(
                            0f to 0f,
                            100f to 0f,
                            100f to 20f,
                            60f to 20f,
                            60f to 100f,
                            40f to 100f,
                            40f to 20f,
                            0f to 20f
                        ),
                    targetX =
                        50f,
                    targetY =
                        20f
                )

        assertTrue(
            "A região central da junção deve receber um bloco Satin próprio, sem ficar vazia.",
            distance <=
                12f
        )
    }


    @Test
    fun primarySavedFontEngineChangesDirectionWithoutSkeleton() {
        val vectors =
            ReferenceImportedFontEngine
                .debugPrimaryRowVectors(
                    polygon =
                        listOf(
                            0f to 0f,
                            22f to 0f,
                            22f to 78f,
                            82f to 78f,
                            82f to 100f,
                            0f to 100f
                        )
                )

        assertTrue(
            "O motor principal de fontes salvas deve gerar travessas vetoriais.",
            vectors.isNotEmpty()
        )

        assertTrue(
            "O braço vertical precisa de travessas horizontais.",
            vectors.any {
                    vector ->
                kotlin.math.abs(
                    vector.first
                ) >
                    kotlin.math.abs(
                        vector.second
                    ) *
                        1.4f
            }
        )

        assertTrue(
            "O braço horizontal precisa de travessas verticais.",
            vectors.any {
                    vector ->
                kotlin.math.abs(
                    vector.second
                ) >
                    kotlin.math.abs(
                        vector.first
                    ) *
                        1.4f
            }
        )
    }

}