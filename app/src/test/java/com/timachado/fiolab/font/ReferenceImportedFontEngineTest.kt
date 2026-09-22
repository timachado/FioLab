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
    fun firstSatinRegionStartsAtLeftEdgeForVisualHint() {
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
            "Com um início visual à esquerda, o primeiro ponto deve entrar na região esquerda da letra.",
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
    fun regionUnderlayAddsSingleSparsePass() {
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
            "Uma haste e um ponto destacado devem gerar duas colunas, sem transformar o ponto em vários ramos radiais. Contagem real: " +
                count,
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
    fun satinRowsReachRealGlyphBoundaryWhenRadiusIsUnderestimated() {
        val width =
            ReferenceImportedFontEngine
                .debugSatinReachesTrueRasterBoundary()

        assertTrue(
            "O Satin deve atravessar até a borda real do glifo, sem parar no meio por causa do raio medial suavizado.",
            width >=
                38f
        )
    }


    @Test
    fun accumulatedSmoothTurnIsSplitBeforeBecomingFan() {
        val blocks =
            ReferenceImportedFontEngine
                .debugAccumulatedFanBlockCount()

        assertTrue(
            "Uma rotação gradual grande não pode permanecer em uma única coluna Satin e formar leque.",
            blocks >=
                2
        )
    }


    @Test
    fun coverageAuditRepairsUncoveredInternalAreas() {
        val repairedRows =
            ReferenceImportedFontEngine
                .debugCoverageRepairCount()

        assertTrue(
            "A auditoria de cobertura deve criar blocos locais quando a geometria principal deixa uma região interna sem travessas.",
            repairedRows >
                0
        )
    }


    @Test
    fun primaryEngineDoesNotCreateAbruptFanAtBranch() {
        val maxTurn =
            ReferenceImportedFontEngine
                .debugPrimaryMaxTurnDegrees(
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
            "O motor principal não pode criar leque abrupto em bifurcações cursivas.",
            maxTurn <=
                53f
        )
    }


    @Test
    fun directionalBlocksKeepJunctionWidthLocal() {
        val widths =
            ReferenceImportedFontEngine
                .debugAdaptiveColumnWidths(
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
    fun junctionCenterIsCoveredWithoutRadialSatinFan() {
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
            "A região central da junção deve continuar coberta sem depender de um bloco Satin radial.",
            distance <=
                12f
        )
    }


    @Test
    fun primarySavedFontEngineProducesStableCenterlineRows() {
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
            "O motor principal por eixo medial deve produzir travessas locais válidas.",
            vectors.isNotEmpty() &&
                vectors.all {
                        vector ->
                    vector.first.isFinite() &&
                        vector.second.isFinite() &&
                        (
                            kotlin.math.abs(
                                vector.first
                            ) +
                                kotlin.math.abs(
                                    vector.second
                                )
                            ) >
                            0.5f
                }
        )
    }


    @Test
    fun vectorSatinDensifiesLargeRowGaps() {
        val maxGap =
            ReferenceImportedFontEngine
                .debugPrimaryMaxCenterGap(
                    polygon =
                        listOf(
                            0f to 0f,
                            20f to 0f,
                            20f to 10f,
                            20f to 20f,
                            20f to 30f,
                            20f to 40f,
                            20f to 50f,
                            20f to 60f,
                            20f to 70f,
                            20f to 80f,
                            20f to 90f,
                            20f to 100f,
                            0f to 100f,
                            0f to 90f,
                            0f to 80f,
                            0f to 70f,
                            0f to 60f,
                            0f to 50f,
                            0f to 40f,
                            0f to 30f,
                            0f to 20f,
                            0f to 10f
                        ),
                    densityMm =
                        0.4f
                )

        assertTrue(
            "Num mesmo traço contínuo, a densificação não pode deixar lacunas grandes entre linhas Satin.",
            maxGap >
                0f &&
                maxGap <=
                    6.2f
        )
    }


    @Test
    fun visualStartPrefersTopLeftLikePedesignOrder() {
        val start =
            ReferenceImportedFontEngine
                .debugGlyphVisualStart(
                    polygon =
                        listOf(
                            0f to 0f,
                            20f to 0f,
                            20f to 100f,
                            0f to 100f
                        )
                )

        assertEquals(
            0f,
            start!!.first,
            0.001f
        )

        assertEquals(
            "Para o mesmo X mais à esquerda, o início deve preferir o ponto visual superior.",
            100f,
            start.second,
            0.001f
        )
    }

    @Test
    fun regionUnderlayDoesNotTraceBothEdgesBeforeMainSatin() {
        val points =
            ReferenceImportedFontEngine
                .debugReferencePath(
                    connected =
                        true,
                    includeUnderlay =
                        true
                )

        val firstJumpIndex =
            points.indexOfFirst {
                it.command ==
                    StitchCommand.JUMP
            }

        val firstStitch =
            points
                .drop(
                    firstJumpIndex +
                        1
                )
                .first {
                    it.command ==
                        StitchCommand.STITCH
                }

        assertTrue(
            "O primeiro underlay deve entrar pela região do bloco, não percorrer toda a mesma borda.",
            firstStitch.xUnits >=
                15
        )
    }


    @Test
    fun firstBlockUsesUpperVisualEntryInsteadOfLowerFlourish() {
        val points =
            ReferenceImportedFontEngine
                .debugFirstVisualBlockPath()

        val first =
            points.first()

        assertEquals(
            StitchCommand.JUMP,
            first.command
        )

        assertTrue(
            "O primeiro bloco deve ser a haste superior próxima do início visual, não o floreio inferior mais à esquerda.",
            first.yUnits >=
                80
        )
    }


    @Test
    fun curvedUnderlayNeverCutsAcrossGlyphHole() {
        assertTrue(
            "O underlay em curva deve reduzir o passo automaticamente e nunca atravessar o vazado da letra.",
            ReferenceImportedFontEngine
                .debugCurvedUnderlayAllSegmentsInside()
        )
    }


    @Test
    fun professionalStraightStemUsesOneColumn() {
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
                            )
                        )
                )

        assertEquals(
            1,
            count
        )
    }

    @Test
    fun professionalCompactDotUsesOneColumn() {
        val count =
            ReferenceImportedFontEngine
                .debugColumnCount(
                    polygons =
                        listOf(
                            listOf(
                                2f to 100f,
                                16f to 100f,
                                16f to 114f,
                                2f to 114f
                            )
                        )
                )

        assertEquals(
            1,
            count
        )
    }


    @Test
    fun professionalBlocksAddSafeEndCaps() {
        assertEquals(
            "Um bloco Satin com espaço interno seguro deve ganhar uma linha curta de fechamento em cada ponta.",
            4,
            ReferenceImportedFontEngine
                .debugContourEndCapCount()
        )
    }


    @Test
    fun wideObjectsUseTatamiInsteadOfLongSatinThrows() {
        val points =
            ReferenceImportedFontEngine
                .debugWideObjectUsesTatami()

        val stitches =
            points.filter {
                it.command ==
                    StitchCommand.STITCH
            }

        assertTrue(
            "Uma região de 10 mm deve receber linhas Tatami densificadas, sem canais largos entre passes.",
            stitches.size >=
                50
        )

        val maximumStep =
            points
                .zipWithNext()
                .filter {
                        pair ->
                    pair.second.command ==
                        StitchCommand.STITCH
                }
                .maxOfOrNull {
                        pair ->
                    kotlin.math.hypot(
                        (
                            pair.second.xUnits -
                                pair.first.xUnits
                            ).toDouble(),
                        (
                            pair.second.yUnits -
                                pair.first.yUnits
                            ).toDouble()
                    )
                }
                ?: 0.0

        assertTrue(
            "O preenchimento de área larga deve limitar o avanço de cada ponto a aproximadamente 2,5 mm.",
            maximumStep <=
                26.5
        )
    }


    @Test
    fun finalGlyphEndsOnStructuralLegInsteadOfThinFlourish() {
        val points =
            ReferenceImportedFontEngine
                .debugTerminalStructuralLegPath()

        val lastStitch =
            points.last {
                it.command ==
                    StitchCommand.STITCH
            }

        assertTrue(
            "O último ponto deve terminar na perna estrutural do glifo, antes do floreio fino à direita.",
            lastStitch.xUnits in
                70..92
        )
    }

}