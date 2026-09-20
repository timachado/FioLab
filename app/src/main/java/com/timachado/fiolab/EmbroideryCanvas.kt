package com.timachado.fiolab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.timachado.fiolab.core.embroidery.EmbroideryDesign
import com.timachado.fiolab.core.embroidery.HoopProfile
import com.timachado.fiolab.core.embroidery.StitchCommand
import com.timachado.fiolab.ui.theme.FioGold

private val fallbackPalette = listOf(
    Color(0xFFE6BE70),
    Color(0xFFFF9F9A),
    Color(0xFF7ED6DF),
    Color(0xFFB8E994),
    Color(0xFFD6A2E8),
    Color(0xFFFFD56B),
    Color(0xFF82CCDD),
    Color(0xFFF8C291)
)

@Composable
fun EmbroideryCanvas(
    design: EmbroideryDesign,
    modifier: Modifier = Modifier,
    pointLimit: Int = design.points.size,
    interactive: Boolean = false,
    hoop: HoopProfile? = null
) {
    var zoom by remember(
        design.fileName,
        design.isModified
    ) {
        mutableFloatStateOf(1f)
    }

    var offset by remember(
        design.fileName,
        design.isModified
    ) {
        mutableStateOf(Offset.Zero)
    }

    val gestures = if (interactive) {
        Modifier.pointerInput(
            design.fileName,
            design.isModified
        ) {
            detectTransformGestures {
                    _,
                    pan,
                    change,
                    _ ->
                zoom =
                    (
                        zoom *
                            change
                        ).coerceIn(
                            0.5f,
                            8f
                        )
                offset += pan
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier
            .border(
                1.dp,
                Color(0xFF243340),
                RoundedCornerShape(24.dp)
            )
            .then(gestures)
    ) {
        Canvas(
            Modifier.fillMaxSize()
        ) {
            drawGrid()

            if (hoop != null) {
                drawHoopPreview(
                    hoop = hoop
                )
            }

            drawDesign(
                design = design,
                pointLimit = pointLimit,
                userScale = zoom,
                userOffset = offset,
                hoop = hoop
            )
        }
    }
}


private fun DrawScope.drawHoopPreview(
    hoop: HoopProfile
) {
    val padding =
        36.dp.toPx()

    val hoopWidthUnits =
        hoop.widthMm *
            10f

    val hoopHeightUnits =
        hoop.heightMm *
            10f

    val scale =
        minOf(
            (
                size.width -
                    padding *
                        2f
                ) /
                hoopWidthUnits,
            (
                size.height -
                    padding *
                        2f
                ) /
                hoopHeightUnits
        )

    val width =
        hoopWidthUnits *
            scale

    val height =
        hoopHeightUnits *
            scale

    val left =
        (
            size.width -
                width
            ) /
            2f

    val top =
        (
            size.height -
                height
            ) /
            2f

    drawRoundRect(
        color =
            Color(
                0xFF6B7780
            ),
        topLeft =
            Offset(
                left,
                top
            ),
        size =
            androidx.compose.ui.geometry.Size(
                width,
                height
            ),
        cornerRadius =
            androidx.compose.ui.geometry.CornerRadius(
                20.dp.toPx(),
                20.dp.toPx()
            ),
        style =
            Stroke(
                width =
                    2.dp.toPx()
            )
    )

    val safeInset =
        hoop.safeMarginMm *
            10f *
            scale

    drawRoundRect(
        color =
            Color(
                0x886D7C87
            ),
        topLeft =
            Offset(
                left +
                    safeInset,
                top +
                    safeInset
            ),
        size =
            androidx.compose.ui.geometry.Size(
                (
                    width -
                        safeInset *
                            2f
                    ).coerceAtLeast(
                        1f
                    ),
                (
                    height -
                        safeInset *
                            2f
                    ).coerceAtLeast(
                        1f
                    )
            ),
        cornerRadius =
            androidx.compose.ui.geometry.CornerRadius(
                14.dp.toPx(),
                14.dp.toPx()
            ),
        style =
            Stroke(
                width =
                    1.dp.toPx(),
                pathEffect =
                    PathEffect
                        .dashPathEffect(
                            floatArrayOf(
                                8.dp.toPx(),
                                6.dp.toPx()
                            )
                        )
            )
    )
}

private fun DrawScope.drawGrid() {
    val step =
        32.dp.toPx()

    var x = 0f
    while (x < size.width) {
        drawLine(
            Color(0x152F4858),
            Offset(x, 0f),
            Offset(x, size.height),
            1f
        )

        x += step
    }

    var y = 0f
    while (y < size.height) {
        drawLine(
            Color(0x152F4858),
            Offset(0f, y),
            Offset(size.width, y),
            1f
        )

        y += step
    }
}

private fun DrawScope.drawDesign(
    design: EmbroideryDesign,
    pointLimit: Int,
    userScale: Float,
    userOffset: Offset,
    hoop: HoopProfile?
) {
    val bounds =
        design.bounds

    val widthUnits =
        (
            bounds.maxXUnits -
                bounds.minXUnits
            ).coerceAtLeast(1)

    val heightUnits =
        (
            bounds.maxYUnits -
                bounds.minYUnits
            ).coerceAtLeast(1)

    val padding =
        36.dp.toPx()

    val frameWidthUnits =
        hoop
            ?.let {
                it.widthMm *
                    10f
            }
            ?: widthUnits.toFloat()

    val frameHeightUnits =
        hoop
            ?.let {
                it.heightMm *
                    10f
            }
            ?: heightUnits.toFloat()

    val baseScale =
        minOf(
            (
                size.width -
                    padding * 2
                ) /
                frameWidthUnits,
            (
                size.height -
                    padding * 2
                ) /
                frameHeightUnits
        ).coerceAtLeast(
            0.01f
        )

    val scale =
        baseScale *
            userScale

    val centerXUnits =
        (
            bounds.minXUnits +
                bounds.maxXUnits
            ) /
            2f

    val centerYUnits =
        (
            bounds.minYUnits +
                bounds.maxYUnits
            ) /
            2f

    val originX =
        size.width /
            2f -
            centerXUnits *
                scale +
            userOffset.x

    val originY =
        size.height /
            2f +
            centerYUnits *
                scale +
            userOffset.y

    var previous: Offset? =
        null

    var colorIndex = 0

    design.points
        .take(
            pointLimit.coerceIn(
                0,
                design.points.size
            )
        )
        .forEach { point ->
            val current =
                Offset(
                    originX +
                        point.xUnits *
                            scale,
                    originY -
                        point.yUnits *
                            scale
                )

            when (point.command) {
                StitchCommand.COLOR_CHANGE -> {
                    colorIndex =
                        point.colorIndex
                    previous = current
                }

                StitchCommand.END,
                StitchCommand.TRIM,
                StitchCommand.STOP -> {
                    previous = current
                }

                StitchCommand.STITCH -> {
                    previous?.let {
                        drawLine(
                            color =
                                threadColor(
                                    design,
                                    colorIndex
                                ),
                            start = it,
                            end = current,
                            strokeWidth =
                                1.8.dp.toPx(),
                            cap =
                                StrokeCap.Round
                        )
                    }

                    previous = current
                }

                StitchCommand.JUMP -> {
                    previous?.let {
                        drawLine(
                            color =
                                Color(
                                    0x556D7C87
                                ),
                            start = it,
                            end = current,
                            strokeWidth =
                                1.dp.toPx()
                        )
                    }

                    previous = current
                }

                StitchCommand.SEQUIN -> {
                    drawCircle(
                        color = FioGold,
                        radius =
                            2.2.dp.toPx(),
                        center = current,
                        style =
                            Stroke(
                                1.dp.toPx()
                            )
                    )

                    previous = current
                }
            }
        }
}

private fun threadColor(
    design: EmbroideryDesign,
    index: Int
): Color {
    val raw =
        design.threadColors
            .getOrNull(index)

    return if (raw != null) {
        Color(
            0xFF000000 or
                raw.toLong()
        )
    } else {
        fallbackPalette[
            index %
                fallbackPalette.size
        ]
    }
}
