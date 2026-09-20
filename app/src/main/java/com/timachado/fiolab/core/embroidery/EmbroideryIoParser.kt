package com.timachado.fiolab.core.embroidery

import java.io.ByteArrayInputStream
import kotlin.math.roundToInt
import org.embroideryio.embroideryio.EmbConstant
import org.embroideryio.embroideryio.EmbPattern

object EmbroideryIoParser {
    private val enabledFormats = setOf("jef", "pes")

    fun parse(fileName: String, bytes: ByteArray): EmbroideryLoadResult {
        val extension = fileName
            .substringAfterLast('.', "")
            .lowercase()

        if (extension !in enabledFormats) {
            return EmbroideryLoadResult.Error(
                "Formato ainda não habilitado nesta camada."
            )
        }

        val pattern = try {
            ByteArrayInputStream(bytes).use { input ->
                EmbPattern.readStream(fileName, input)
            }
        } catch (error: Exception) {
            return EmbroideryLoadResult.Error(
                "Não foi possível interpretar a matriz " +
                    extension.uppercase() +
                    ".",
                error.message
            )
        } ?: return EmbroideryLoadResult.Error(
            "O arquivo " +
                extension.uppercase() +
                " não contém uma matriz reconhecível."
        )

        if (pattern.size() <= 0) {
            return EmbroideryLoadResult.Error(
                "Nenhuma pontada foi encontrada no arquivo."
            )
        }

        val points = ArrayList<EmbroideryPoint>(pattern.size())
        var colorIndex = 0
        var stitchCount = 0
        var jumpCount = 0
        var colorChanges = 0
        var endFound = false
        var minX = 0
        var maxX = 0
        var minY = 0
        var maxY = 0
        var hasCoordinate = false

        for (index in 0 until pattern.size()) {
            val rawCommand =
                pattern.getData(index) and
                    EmbConstant.COMMAND_MASK

            val x =
                pattern.getX(index).roundToInt()

            val y =
                -pattern.getY(index).roundToInt()

            val command = when (rawCommand) {
                EmbConstant.STITCH ->
                    StitchCommand.STITCH
                EmbConstant.JUMP ->
                    StitchCommand.JUMP
                EmbConstant.TRIM ->
                    StitchCommand.TRIM
                EmbConstant.STOP ->
                    StitchCommand.STOP
                EmbConstant.COLOR_CHANGE,
                EmbConstant.NEEDLE_SET ->
                    StitchCommand.COLOR_CHANGE
                EmbConstant.SEQUIN_MODE,
                EmbConstant.SEQUIN_EJECT ->
                    StitchCommand.SEQUIN
                EmbConstant.END ->
                    StitchCommand.END
                else -> null
            } ?: continue

            when (command) {
                StitchCommand.STITCH ->
                    stitchCount++
                StitchCommand.JUMP ->
                    jumpCount++
                StitchCommand.COLOR_CHANGE -> {
                    colorChanges++
                    colorIndex++
                }
                StitchCommand.END ->
                    endFound = true
                else -> Unit
            }

            points += EmbroideryPoint(
                xUnits = x,
                yUnits = y,
                command = command,
                colorIndex = colorIndex
            )

            if (command != StitchCommand.END) {
                if (!hasCoordinate) {
                    minX = x
                    maxX = x
                    minY = y
                    maxY = y
                    hasCoordinate = true
                } else {
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
        }

        if (points.none {
                it.command == StitchCommand.STITCH
            }) {
            return EmbroideryLoadResult.Error(
                "A matriz foi reconhecida, mas nenhuma pontada de costura válida foi encontrada."
            )
        }

        return EmbroideryLoadResult.Success(
            EmbroideryDesign(
                fileName = fileName,
                format = extension.uppercase(),
                label = pattern.name,
                points = points,
                bounds = EmbroideryBounds(
                    minXUnits = minX,
                    maxXUnits = maxX,
                    minYUnits = minY,
                    maxYUnits = maxY
                ),
                stitchCount = stitchCount,
                jumpCount = jumpCount,
                colorChanges = colorChanges,
                endFound = endFound,
                sourceBytes = bytes.copyOf()
            )
        )
    }
}
