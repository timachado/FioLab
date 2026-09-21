package com.timachado.fiolab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.timachado.fiolab.core.embroidery.EmbroideryDesign
import com.timachado.fiolab.core.embroidery.EmbroideryPoint
import com.timachado.fiolab.core.embroidery.HoopProfile
import com.timachado.fiolab.core.embroidery.StitchCommand

private val simulationPalette =
    listOf(
        Color(0xFFE6BE70),
        Color(0xFFE76F51),
        Color(0xFF2A9D8F),
        Color(0xFF457B9D),
        Color(0xFF9B5DE5),
        Color(0xFFF4A261),
        Color(0xFFF4A7B9),
        Color(0xFF6D597A)
    )

@Composable
fun MachineSimulationCanvas(
    design: EmbroideryDesign,
    pointLimit: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier =
            modifier
                .background(
                    Color(
                        0xFFF3EFE5
                    ),
                    RoundedCornerShape(
                        24.dp
                    )
                )
    ) {
        Canvas(
            Modifier.fillMaxSize()
        ) {
            val transform =
                SimulationTransform(
                    design =
                        design,
                    canvasWidth =
                        size.width,
                    canvasHeight =
                        size.height,
                    padding =
                        38.dp.toPx()
                )

            drawFabricGrid(
                transform
            )

            drawHoop(
                hoop =
                    design.hoopProfile
            )

            if (
                design.guidePoints
                    .isNotEmpty()
            ) {
                drawReferenceGuide(
                    design =
                        design,
                    transform =
                        transform
                )
            } else {
                drawStitches(
                    design =
                        design,
                    transform =
                        transform,
                    pointLimit =
                        design.points.size,
                    ghost =
                        true
                )
            }

            drawStitches(
                design =
                    design,
                transform =
                    transform,
                pointLimit =
                    pointLimit,
                ghost = false
            )

            val visiblePoints =
                design.points
                    .filter {
                        it.command !=
                            StitchCommand.END
                    }

            val current =
                visiblePoints
                    .getOrNull(
                        (
                            pointLimit -
                                1
                            ).coerceAtLeast(
                                0
                            )
                    )

            if (
                pointLimit > 0 &&
                current != null
            ) {
                drawNeedle(
                    point = current,
                    transform =
                        transform,
                    color =
                        threadColor(
                            design,
                            current
                                .colorIndex
                        )
                )
            }
        }
    }
}

private data class SimulationTransform(
    val design: EmbroideryDesign,
    val canvasWidth: Float,
    val canvasHeight: Float,
    val padding: Float
) {
    private val widthUnits =
        (
            design.bounds
                .maxXUnits -
                design.bounds
                    .minXUnits
            ).coerceAtLeast(1)

    private val heightUnits =
        (
            design.bounds
                .maxYUnits -
                design.bounds
                    .minYUnits
            ).coerceAtLeast(1)

    private val availableWidth =
        (
            canvasWidth -
                padding *
                    2f
            ).coerceAtLeast(
                1f
            )

    private val availableHeight =
        (
            canvasHeight -
                padding *
                    2f
            ).coerceAtLeast(
                1f
            )

    private val designScale =
        minOf(
            availableWidth /
                (
                    widthUnits *
                        1.18f
                    ),
            availableHeight /
                (
                    heightUnits *
                        1.18f
                    )
        )

    private val hoopScale =
        design.hoopProfile
            ?.let {
                hoop ->
                minOf(
                    availableWidth /
                        (
                            hoop.widthMm *
                                10f
                            ),
                    availableHeight /
                        (
                            hoop.heightMm *
                                10f
                            )
                )
            }

    val scale: Float =
        (
            hoopScale
                ?.let {
                    minOf(
                        designScale,
                        it *
                            8f
                    )
                }
                ?: designScale
            ).coerceAtLeast(
            0.01f
        )

    private val centerXUnits =
        (
            design.bounds
                .minXUnits +
                design.bounds
                    .maxXUnits
            ) /
            2f

    private val centerYUnits =
        (
            design.bounds
                .minYUnits +
                design.bounds
                    .maxYUnits
            ) /
            2f

    private val originX =
        canvasWidth /
            2f -
            centerXUnits *
                scale

    private val originY =
        canvasHeight /
            2f +
            centerYUnits *
                scale

    fun point(
        embroideryPoint:
            EmbroideryPoint
    ): Offset =
        Offset(
            x =
                originX +
                    embroideryPoint
                        .xUnits *
                    scale,
            y =
                originY -
                    embroideryPoint
                        .yUnits *
                    scale
        )

    fun unitsToPx(
        units: Float
    ): Float =
        units *
            scale
}

private fun DrawScope.drawFabricGrid(
    transform:
        SimulationTransform
) {
    val minor =
        transform
            .unitsToPx(
                50f
            )
            .coerceIn(
                18.dp.toPx(),
                54.dp.toPx()
            )

    val major =
        minor *
            2f

    var x = 0f
    var index = 0

    while (
        x <= size.width
    ) {
        drawLine(
            color =
                if (
                    index %
                        2 ==
                        0
                ) {
                    Color(
                        0x2A8C877C
                    )
                } else {
                    Color(
                        0x168C877C
                    )
                },
            start =
                Offset(
                    x,
                    0f
                ),
            end =
                Offset(
                    x,
                    size.height
                ),
            strokeWidth =
                if (
                    index %
                        2 ==
                        0
                ) {
                    1.2f
                } else {
                    0.8f
                }
        )

        index++
        x += minor
    }

    var y = 0f
    index = 0

    while (
        y <= size.height
    ) {
        drawLine(
            color =
                if (
                    index %
                        2 ==
                        0
                ) {
                    Color(
                        0x2A8C877C
                    )
                } else {
                    Color(
                        0x168C877C
                    )
                },
            start =
                Offset(
                    0f,
                    y
                ),
            end =
                Offset(
                    size.width,
                    y
                ),
            strokeWidth =
                if (
                    index %
                        2 ==
                        0
                ) {
                    1.2f
                } else {
                    0.8f
                }
        )

        index++
        y += minor
    }

    drawLine(
        color =
            Color(
                0x33958E80
            ),
        start =
            Offset(
                0f,
                size.height /
                    2f
            ),
        end =
            Offset(
                size.width,
                size.height /
                    2f
            ),
        strokeWidth =
            1.2f
    )

    drawLine(
        color =
            Color(
                0x33958E80
            ),
        start =
            Offset(
                size.width /
                    2f,
                0f
            ),
        end =
            Offset(
                size.width /
                    2f,
                size.height
            ),
        strokeWidth =
            1.2f
    )
}

private fun DrawScope.drawHoop(
    hoop: HoopProfile?
) {
    val fallbackInset =
        13.dp.toPx()

    val frameWidth =
        if (
            hoop == null
        ) {
            size.width -
                fallbackInset *
                    2f
        } else {
            val ratio =
                hoop.widthMm /
                    hoop.heightMm

            minOf(
                size.width -
                    fallbackInset *
                        2f,
                (
                    size.height -
                        fallbackInset *
                            2f
                    ) *
                    ratio
            )
        }

    val frameHeight =
        if (
            hoop == null
        ) {
            size.height -
                fallbackInset *
                    2f
        } else {
            val ratio =
                hoop.widthMm /
                    hoop.heightMm

            minOf(
                size.height -
                    fallbackInset *
                        2f,
                (
                    size.width -
                        fallbackInset *
                            2f
                    ) /
                    ratio
            )
        }

    val left =
        (
            size.width -
                frameWidth
            ) /
            2f

    val top =
        (
            size.height -
                frameHeight
            ) /
            2f

    drawRoundRect(
        color =
            Color(
                0xAA34373B
            ),
        topLeft =
            Offset(
                left,
                top
            ),
        size =
            androidx.compose.ui.geometry.Size(
                width =
                    frameWidth,
                height =
                    frameHeight
            ),
        cornerRadius =
            CornerRadius(
                18.dp.toPx(),
                18.dp.toPx()
            ),
        style =
            Stroke(
                width =
                    1.5.dp.toPx(),
                pathEffect =
                    PathEffect
                        .dashPathEffect(
                            floatArrayOf(
                                9.dp.toPx(),
                                7.dp.toPx()
                            )
                        )
            )
    )
}

private fun DrawScope.drawReferenceGuide(
    design: EmbroideryDesign,
    transform:
        SimulationTransform
) {
    val path =
        Path().apply {
            fillType =
                PathFillType.EvenOdd
        }

    var contourOpen =
        false

    design.guidePoints
        .forEach {
                point ->
            val position =
                transform.point(
                    point
                )

            when (
                point.command
            ) {
                StitchCommand.JUMP -> {
                    if (
                        contourOpen
                    ) {
                        path.close()
                    }

                    path.moveTo(
                        position.x,
                        position.y
                    )

                    contourOpen =
                        true
                }

                StitchCommand.STITCH -> {
                    if (
                        !contourOpen
                    ) {
                        path.moveTo(
                            position.x,
                            position.y
                        )

                        contourOpen =
                            true
                    } else {
                        path.lineTo(
                            position.x,
                            position.y
                        )
                    }
                }

                StitchCommand.TRIM,
                StitchCommand.STOP,
                StitchCommand.COLOR_CHANGE,
                StitchCommand.SEQUIN,
                StitchCommand.END -> {
                    if (
                        contourOpen
                    ) {
                        path.close()

                        contourOpen =
                            false
                    }
                }
            }
        }

    if (
        contourOpen
    ) {
        path.close()
    }

    val guideColor =
        Color(
            0xFFD96B79
        )

    drawPath(
        path =
            path,
        color =
            guideColor.copy(
                alpha =
                    0.12f
            )
    )

    drawPath(
        path =
            path,
        color =
            guideColor.copy(
                alpha =
                    0.34f
            ),
        style =
            Stroke(
                width =
                    1.15.dp.toPx()
            )
    )
}

private fun DrawScope.drawStitches(
    design: EmbroideryDesign,
    transform:
        SimulationTransform,
    pointLimit: Int,
    ghost: Boolean
) {
    var previous:
        EmbroideryPoint? =
        null

    val limit =
        pointLimit
            .coerceIn(
                0,
                design.points.size
            )

    for (
        index in
            0 until limit
    ) {
        val point =
            design.points[
                index
            ]

        when (
            point.command
        ) {
            StitchCommand.COLOR_CHANGE,
            StitchCommand.TRIM,
            StitchCommand.STOP,
            StitchCommand.END -> {
                previous =
                    null
            }

            StitchCommand.JUMP -> {
                // JUMP é apenas deslocamento da máquina sem costura.
                // Não desenhar uma linha evita mostrar um fio que não existe.
                previous =
                    point
            }

            StitchCommand.SEQUIN -> {
                val color =
                    threadColor(
                        design,
                        point.colorIndex
                    )

                drawCircle(
                    color =
                        if (ghost) {
                            color.copy(
                                alpha =
                                    0.16f
                            )
                        } else {
                            color
                        },
                    radius =
                        if (ghost) {
                            1.2.dp
                                .toPx()
                        } else {
                            2.4.dp
                                .toPx()
                        },
                    center =
                        transform
                            .point(
                                point
                            )
                )

                previous =
                    point
            }

            StitchCommand.STITCH -> {
                val before =
                    previous

                if (
                    before != null
                ) {
                    val baseColor =
                        threadColor(
                            design,
                            point
                                .colorIndex
                        )

                    val color =
                        if (ghost) {
                            baseColor
                                .copy(
                                    alpha =
                                        0.10f
                                )
                        } else {
                            baseColor
                                .copy(
                                    alpha =
                                        0.98f
                                )
                        }

                    val start =
                        transform
                            .point(
                                before
                            )

                    val end =
                        transform
                            .point(
                                point
                            )

                    if (
                        ghost
                    ) {
                        drawLine(
                            color =
                                color,
                            start =
                                start,
                            end =
                                end,
                            strokeWidth =
                                1.05.dp
                                    .toPx(),
                            cap =
                                StrokeCap.Round
                        )
                    } else {
                        drawLine(
                            color =
                                Color.Black
                                    .copy(
                                        alpha =
                                            0.22f
                                    ),
                            start =
                                start +
                                    Offset(
                                        0.7.dp.toPx(),
                                        0.7.dp.toPx()
                                    ),
                            end =
                                end +
                                    Offset(
                                        0.7.dp.toPx(),
                                        0.7.dp.toPx()
                                    ),
                            strokeWidth =
                                2.9.dp
                                    .toPx(),
                            cap =
                                StrokeCap.Round
                        )

                        drawLine(
                            color =
                                color,
                            start =
                                start,
                            end =
                                end,
                            strokeWidth =
                                2.35.dp
                                    .toPx(),
                            cap =
                                StrokeCap.Round
                        )

                        drawLine(
                            color =
                                Color.White
                                    .copy(
                                        alpha =
                                            0.18f
                                    ),
                            start =
                                start,
                            end =
                                end,
                            strokeWidth =
                                0.55.dp
                                    .toPx(),
                            cap =
                                StrokeCap.Round
                        )
                    }
                }

                previous =
                    point
            }
        }
    }
}

private fun DrawScope.drawNeedle(
    point: EmbroideryPoint,
    transform:
        SimulationTransform,
    color: Color
) {
    val center =
        transform.point(
            point
        )

    drawCircle(
        color =
            Color.White,
        radius =
            5.dp.toPx(),
        center =
            center
    )

    drawCircle(
        color =
            color,
        radius =
            3.6.dp.toPx(),
        center =
            center
    )

    drawCircle(
        color =
            Color.White,
        radius =
            1.2.dp.toPx(),
        center =
            center
    )
}

private fun threadColor(
    design: EmbroideryDesign,
    colorIndex: Int
): Color {
    val raw =
        design.threadColors
            .getOrNull(
                colorIndex
            )

    return if (
        raw != null
    ) {
        Color(
            0xFF000000 or
                raw.toLong()
        )
    } else {
        simulationPalette[
            colorIndex %
                simulationPalette.size
        ]
    }
}
