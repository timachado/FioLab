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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.timachado.fiolab.core.embroidery.EmbroideryDesign
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
    interactive: Boolean = false
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

            drawDesign(
                design = design,
                pointLimit = pointLimit,
                userScale = zoom,
                userOffset = offset
            )
        }
    }
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
    userOffset: Offset
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

    val baseScale =
        minOf(
            (
                size.width -
                    padding * 2
                ) / widthUnits,
            (
                size.height -
                    padding * 2
                ) / heightUnits
        ).coerceAtLeast(
            0.01f
        )

    val scale =
        baseScale *
            userScale

    val contentWidth =
        widthUnits *
            scale

    val contentHeight =
        heightUnits *
            scale

    val originX =
        (
            size.width -
                contentWidth
            ) / 2f -
            bounds.minXUnits *
                scale +
            userOffset.x

    val originY =
        (
            size.height -
                contentHeight
            ) / 2f +
            bounds.maxYUnits *
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
