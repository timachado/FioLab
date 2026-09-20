package com.timachado.fiolab.core.embroidery

import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import org.embroideryio.embroideryio.EmbConstant
import org.embroideryio.embroideryio.EmbPattern
import org.embroideryio.embroideryio.EmbThread
import org.embroideryio.embroideryio.EmbroideryIO

object MatrixConverter {
    val supportedFormats =
        listOf("DST", "PES", "JEF")

    private val fallbackColors =
        listOf(
            0xE6BE70,
            0xE76F51,
            0x2A9D8F,
            0x264653,
            0x9B5DE5,
            0xF4A261,
            0x457B9D,
            0xE63946
        )

    private const val SAFE_DELTA_UNITS = 120

    fun convert(
        design: EmbroideryDesign,
        targetFormat: String
    ): Result<ConvertedMatrix> =
        runCatching {
            val format =
                targetFormat.uppercase(
                    Locale.ROOT
                )

            require(
                format in supportedFormats
            ) {
                "Formato de saída ainda não suportado."
            }

            val pattern =
                buildOutputPattern(
                    design
                )

            val extension =
                format.lowercase(
                    Locale.ROOT
                )

            val baseName =
                design.fileName
                    .substringBeforeLast('.')
                    .ifBlank { "matriz" }

            val outputName =
                baseName +
                    "-convertido." +
                    extension

            val writer =
                EmbroideryIO
                    .getWriterByFilename(
                        outputName
                    )
                    ?: error(
                        "Writer indisponível para $format."
                    )

            if (format == "PES") {
                writer.set(
                    "pes version",
                    6
                )
            }

            val output =
                ByteArrayOutputStream()

            writer.write(
                pattern,
                output
            )

            val bytes =
                output.toByteArray()

            require(
                bytes.isNotEmpty()
            ) {
                "A conversão não gerou dados."
            }

            ConvertedMatrix(
                fileName = outputName,
                format = format,
                bytes = bytes
            )
        }

    private fun buildOutputPattern(
        design: EmbroideryDesign
    ): EmbPattern {
        val pattern =
            EmbPattern().apply {
                name =
                    design.label
                        ?: design.fileName
                            .substringBeforeLast('.')
            }

        addThreads(
            pattern,
            design
        )

        var currentX = 0
        var currentY = 0

        design.points.forEach { point ->
            when (point.command) {
                StitchCommand.STITCH -> {
                    addSegmentedMove(
                        pattern = pattern,
                        fromX = currentX,
                        fromY = currentY,
                        toX = point.xUnits,
                        toY = point.yUnits,
                        command =
                            EmbConstant.STITCH
                    )
                }

                StitchCommand.JUMP -> {
                    addSegmentedMove(
                        pattern = pattern,
                        fromX = currentX,
                        fromY = currentY,
                        toX = point.xUnits,
                        toY = point.yUnits,
                        command =
                            EmbConstant.JUMP
                    )
                }

                StitchCommand.TRIM -> {
                    pattern.addStitchAbs(
                        point.xUnits.toFloat(),
                        point.yUnits.toFloat(),
                        EmbConstant.TRIM
                    )
                }

                StitchCommand.STOP -> {
                    pattern.addStitchAbs(
                        point.xUnits.toFloat(),
                        point.yUnits.toFloat(),
                        EmbConstant.COLOR_CHANGE
                    )
                }

                StitchCommand.COLOR_CHANGE -> {
                    pattern.addStitchAbs(
                        point.xUnits.toFloat(),
                        point.yUnits.toFloat(),
                        EmbConstant.COLOR_CHANGE
                    )
                }

                StitchCommand.SEQUIN -> {
                    pattern.addStitchAbs(
                        point.xUnits.toFloat(),
                        point.yUnits.toFloat(),
                        EmbConstant.STITCH
                    )
                }

                StitchCommand.END -> {
                    pattern.addStitchAbs(
                        point.xUnits.toFloat(),
                        point.yUnits.toFloat(),
                        EmbConstant.END
                    )
                }
            }

            currentX = point.xUnits
            currentY = point.yUnits
        }

        if (!design.endFound) {
            pattern.end()
        }

        pattern.fixColorCount()

        return pattern
    }

    private fun addThreads(
        pattern: EmbPattern,
        design: EmbroideryDesign
    ) {
        val wantedColors =
            max(
                design.colorCount,
                1
            )

        repeat(
            wantedColors
        ) { index ->
            val color =
                design.threadColors
                    .getOrNull(index)
                    ?: fallbackColors[
                        index %
                            fallbackColors.size
                    ]

            pattern.addThread(
                EmbThread().apply {
                    setColor(color)
                }
            )
        }
    }

    private fun addSegmentedMove(
        pattern: EmbPattern,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
        command: Int
    ) {
        val dx =
            toX - fromX

        val dy =
            toY - fromY

        val greatestDelta =
            max(
                abs(dx),
                abs(dy)
            )

        val segments =
            max(
                1,
                ceil(
                    greatestDelta.toDouble() /
                        SAFE_DELTA_UNITS
                ).toInt()
            )

        for (
            part in
                1..segments
        ) {
            val ratio =
                part.toDouble() /
                    segments

            val x =
                (
                    fromX +
                        dx * ratio
                    ).roundToInt()

            val y =
                (
                    fromY +
                        dy * ratio
                    ).roundToInt()

            pattern.addStitchAbs(
                x.toFloat(),
                y.toFloat(),
                command
            )
        }
    }
}
