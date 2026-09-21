package com.timachado.fiolab.core.embroidery

data class EmbroideryIntegrityReport(
    val design: EmbroideryDesign,
    val warnings: List<String>
)

object EmbroideryIntegrity {
    private const val MAX_POINTS =
        2_000_000

    fun normalize(
        design: EmbroideryDesign
    ): Result<EmbroideryIntegrityReport> =
        runCatching {
            require(
                design.points
                    .isNotEmpty()
            ) {
                "A matriz não possui comandos."
            }

            require(
                design.points
                    .size <=
                    MAX_POINTS
            ) {
                "A matriz excede o limite seguro de comandos."
            }

            require(
                design.points
                    .all {
                        it.colorIndex >=
                            0
                    }
            ) {
                "A matriz possui índice de cor inválido."
            }

            val stitches =
                design.points
                    .count {
                        it.command ==
                            StitchCommand.STITCH
                    }

            require(
                stitches >
                    0
            ) {
                "A matriz não possui pontos de costura."
            }

            val coordinates =
                design.points
                    .filter {
                        it.command !=
                            StitchCommand.END
                    }

            require(
                coordinates
                    .isNotEmpty()
            ) {
                "A matriz não possui coordenadas válidas."
            }

            val bounds =
                EmbroideryBounds(
                    minXUnits =
                        coordinates
                            .minOf {
                                it.xUnits
                            },
                    maxXUnits =
                        coordinates
                            .maxOf {
                                it.xUnits
                            },
                    minYUnits =
                        coordinates
                            .minOf {
                                it.yUnits
                            },
                    maxYUnits =
                        coordinates
                            .maxOf {
                                it.yUnits
                            }
                )

            val jumps =
                design.points
                    .count {
                        it.command ==
                            StitchCommand.JUMP
                    }

            val colorChanges =
                design.points
                    .count {
                        it.command ==
                            StitchCommand.COLOR_CHANGE
                    }

            val endFound =
                design.points
                    .any {
                        it.command ==
                            StitchCommand.END
                    }

            val warnings =
                buildList {
                    if (
                        !endFound
                    ) {
                        add(
                            "O arquivo não informou comando END."
                        )
                    }

                    if (
                        design.stitchCount !=
                            stitches
                    ) {
                        add(
                            "A contagem de pontos foi recalculada."
                        )
                    }

                    if (
                        design.jumpCount !=
                            jumps
                    ) {
                        add(
                            "A contagem de saltos foi recalculada."
                        )
                    }

                    if (
                        design.colorChanges !=
                            colorChanges
                    ) {
                        add(
                            "A contagem de trocas de cor foi recalculada."
                        )
                    }

                    if (
                        design.bounds !=
                            bounds
                    ) {
                        add(
                            "Os limites da matriz foram recalculados."
                        )
                    }
                }

            val normalized =
                design.copy(
                    format =
                        design.format
                            .trim()
                            .uppercase(),
                    bounds =
                        bounds,
                    stitchCount =
                        stitches,
                    jumpCount =
                        jumps,
                    colorChanges =
                        colorChanges,
                    endFound =
                        endFound
                )

            EmbroideryIntegrityReport(
                design =
                    normalized,
                warnings =
                    warnings
            )
        }
}
