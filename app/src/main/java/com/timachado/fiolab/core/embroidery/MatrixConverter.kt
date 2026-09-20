package com.timachado.fiolab.core.embroidery

import java.io.ByteArrayOutputStream
import java.util.Locale
import org.embroideryio.embroideryio.EmbConstant
import org.embroideryio.embroideryio.EmbPattern
import org.embroideryio.embroideryio.EmbThread
import org.embroideryio.embroideryio.EmbroideryIO

object MatrixConverter {
    val supportedFormats = listOf("DST", "PES", "JEF")

    private val fallbackColors = listOf(
        0xE6BE70,
        0xE76F51,
        0x2A9D8F,
        0x264653,
        0x9B5DE5,
        0xF4A261,
        0x457B9D,
        0xE63946
    )

    fun convert(
        design: EmbroideryDesign,
        targetFormat: String
    ): Result<ConvertedMatrix> = runCatching {
        val format = targetFormat.uppercase(Locale.ROOT)
        require(format in supportedFormats) {
            "Formato de saída ainda não suportado."
        }

        val pattern = EmbPattern().apply {
            name = design.label
                ?: design.fileName.substringBeforeLast('.')
        }

        val wantedColors = maxOf(design.colorCount, 1)
        repeat(wantedColors) { index ->
            val color =
                design.threadColors.getOrNull(index)
                    ?: fallbackColors[index % fallbackColors.size]

            pattern.addThread(
                EmbThread().apply {
                    setColor(color)
                }
            )
        }

        design.points.forEach { point ->
            val command = when (point.command) {
                StitchCommand.STITCH -> EmbConstant.STITCH
                StitchCommand.JUMP -> EmbConstant.JUMP
                StitchCommand.TRIM -> EmbConstant.TRIM
                StitchCommand.STOP -> EmbConstant.STOP
                StitchCommand.COLOR_CHANGE -> EmbConstant.COLOR_CHANGE
                StitchCommand.SEQUIN -> EmbConstant.SEQUIN_EJECT
                StitchCommand.END -> EmbConstant.END
            }

            pattern.addStitchAbs(
                point.xUnits.toFloat(),
                point.yUnits.toFloat(),
                command
            )
        }

        if (!design.endFound) {
            pattern.end()
        }

        pattern.fixColorCount()

        val extension = format.lowercase(Locale.ROOT)
        val baseName = design.fileName
            .substringBeforeLast('.')
            .ifBlank { "matriz" }

        val outputName = "$baseName-convertido.$extension"

        val output = ByteArrayOutputStream()
        EmbroideryIO.writeStream(
            pattern,
            outputName,
            output
        )

        val bytes = output.toByteArray()
        require(bytes.isNotEmpty()) {
            "A conversão não gerou dados."
        }

        ConvertedMatrix(
            fileName = outputName,
            format = format,
            bytes = bytes
        )
    }
}
