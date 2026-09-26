package com.timachado.fiolab.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbroideryPreviewOptimizerTest {
    @Test
    fun smallMatrixIsReturnedUntouched() {
        val points =
            listOf(
                EmbroideryPoint(
                    0,
                    0,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    10,
                    0,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    10,
                    0,
                    StitchCommand.END,
                    0
                )
            )

        val optimized =
            EmbroideryPreviewOptimizer
                .optimize(
                    points
                )

        assertSame(
            points,
            optimized
        )
    }

    @Test
    fun hugeMatrixKeepsWholeRouteWithoutLeavingRunsOpen() {
        val points =
            buildList {
                repeat(
                    100_000
                ) {
                        index ->
                    add(
                        EmbroideryPoint(
                            xUnits =
                                index,
                            yUnits =
                                index %
                                    300,
                            command =
                                StitchCommand.STITCH,
                            colorIndex =
                                0
                        )
                    )
                }

                add(
                    EmbroideryPoint(
                        100_000,
                        0,
                        StitchCommand.JUMP,
                        0
                    )
                )

                repeat(
                    100_000
                ) {
                        index ->
                    add(
                        EmbroideryPoint(
                            xUnits =
                                100_000 +
                                    index,
                            yUnits =
                                300 -
                                    index %
                                        300,
                            command =
                                StitchCommand.STITCH,
                            colorIndex =
                                0
                        )
                    )
                }

                add(
                    EmbroideryPoint(
                        199_999,
                        0,
                        StitchCommand.END,
                        0
                    )
                )
            }

        val optimized =
            EmbroideryPreviewOptimizer
                .optimize(
                    points,
                    maxCommands =
                        25_000
                )

        assertTrue(
            optimized.size <
                points.size /
                    4
        )

        assertEquals(
            points.first(),
            optimized.first()
        )

        assertEquals(
            points.last(),
            optimized.last()
        )

        assertTrue(
            optimized.any {
                it.command ==
                    StitchCommand.JUMP
            }
        )

        val jumpIndex =
            optimized.indexOfFirst {
                it.command ==
                    StitchCommand.JUMP
            }

        assertTrue(
            jumpIndex >
                0
        )

        assertEquals(
            points[
                99_999
            ],
            optimized[
                jumpIndex -
                    1
            ]
        )

        assertEquals(
            points[
                100_001
            ],
            optimized[
                jumpIndex +
                    1
            ]
        )
    }
}
