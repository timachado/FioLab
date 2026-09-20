package com.timachado.fiolab.core.project

import android.content.Context
import com.timachado.fiolab.core.embroidery.EmbroideryBounds
import com.timachado.fiolab.core.embroidery.EmbroideryDesign
import com.timachado.fiolab.core.embroidery.EmbroideryPoint
import com.timachado.fiolab.core.embroidery.FabricProfile
import com.timachado.fiolab.core.embroidery.HoopProfile
import com.timachado.fiolab.core.embroidery.MachineFinishingInfo
import com.timachado.fiolab.core.embroidery.StitchCommand
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale

data class SavedProjectSummary(
    val id: String,
    val title: String,
    val fileName: String,
    val format: String,
    val stitchCount: Int,
    val colorCount: Int,
    val widthMm: Float,
    val heightMm: Float,
    val updatedAtMillis: Long
)

object ProjectCodec {
    private const val MAGIC =
        "FIOLAB_PROJECT"

    private const val VERSION =
        1

    private const val MAX_POINTS =
        2_000_000

    private const val MAX_COLORS =
        512

    fun encode(
        design: EmbroideryDesign
    ): ByteArray {
        val buffer =
            ByteArrayOutputStream()

        DataOutputStream(
            buffer
        ).use {
                output ->
            output.writeUTF(
                MAGIC
            )

            output.writeInt(
                VERSION
            )

            output.writeUTF(
                design.fileName
                    .take(
                        500
                    )
            )

            output.writeUTF(
                design.format
                    .take(
                        32
                    )
            )

            writeNullableString(
                output,
                design.label
            )

            output.writeInt(
                design.threadColors
                    .size
            )

            design.threadColors
                .forEach {
                    output.writeInt(
                        it
                    )
                }

            writeNullableString(
                output,
                design.hoopProfile
                    ?.name
            )

            writeNullableString(
                output,
                design.fabricProfile
                    ?.name
            )

            val finishing =
                design.machineFinishing

            output.writeBoolean(
                finishing !=
                    null
            )

            if (
                finishing !=
                    null
            ) {
                output.writeBoolean(
                    finishing
                        .tieInEnabled
                )

                output.writeBoolean(
                    finishing
                        .tieOffEnabled
                )

                output.writeBoolean(
                    finishing
                        .autoTrimLongJumps
                )

                output.writeFloat(
                    finishing
                        .trimJumpThresholdMm
                )

                output.writeBoolean(
                    finishing
                        .optimizeTravel
                )
            }

            require(
                design.points
                    .size <=
                    MAX_POINTS
            ) {
                "Projeto grande demais para salvar."
            }

            output.writeInt(
                design.points
                    .size
            )

            design.points
                .forEach {
                        point ->
                    output.writeInt(
                        point.xUnits
                    )

                    output.writeInt(
                        point.yUnits
                    )

                    output.writeUTF(
                        point.command
                            .name
                    )

                    output.writeInt(
                        point.colorIndex
                    )
                }
        }

        return buffer
            .toByteArray()
    }

    fun decode(
        bytes: ByteArray
    ): EmbroideryDesign {
        return DataInputStream(
            ByteArrayInputStream(
                bytes
            )
        ).use {
                input ->
            require(
                input.readUTF() ==
                    MAGIC
            ) {
                "Arquivo de projeto inválido."
            }

            require(
                input.readInt() ==
                    VERSION
            ) {
                "Versão de projeto ainda não suportada."
            }

            val fileName =
                input.readUTF()

            val format =
                input.readUTF()
                    .uppercase(
                        Locale.ROOT
                    )

            val label =
                readNullableString(
                    input
                )

            val colorCount =
                input.readInt()

            require(
                colorCount in
                    0..MAX_COLORS
            ) {
                "Paleta inválida."
            }

            val colors =
                buildList {
                    repeat(
                        colorCount
                    ) {
                        add(
                            input.readInt()
                        )
                    }
                }

            val hoop =
                readNullableString(
                    input
                )?.let {
                        name ->
                    runCatching {
                        HoopProfile
                            .valueOf(
                                name
                            )
                    }.getOrNull()
                }

            val fabric =
                readNullableString(
                    input
                )?.let {
                        name ->
                    runCatching {
                        FabricProfile
                            .valueOf(
                                name
                            )
                    }.getOrNull()
                }

            val finishing =
                if (
                    input.readBoolean()
                ) {
                    MachineFinishingInfo(
                        tieInEnabled =
                            input.readBoolean(),
                        tieOffEnabled =
                            input.readBoolean(),
                        autoTrimLongJumps =
                            input.readBoolean(),
                        trimJumpThresholdMm =
                            input.readFloat(),
                        optimizeTravel =
                            input.readBoolean()
                    )
                } else {
                    null
                }

            val pointCount =
                input.readInt()

            require(
                pointCount in
                    1..MAX_POINTS
            ) {
                "Quantidade de pontos inválida."
            }

            val points =
                buildList {
                    repeat(
                        pointCount
                    ) {
                        add(
                            EmbroideryPoint(
                                xUnits =
                                    input.readInt(),
                                yUnits =
                                    input.readInt(),
                                command =
                                    StitchCommand
                                        .valueOf(
                                            input.readUTF()
                                        ),
                                colorIndex =
                                    input.readInt()
                            )
                        )
                    }
                }

            val coordinates =
                points.filter {
                    it.command !=
                        StitchCommand.END
                }

            val bounds =
                if (
                    coordinates
                        .isEmpty()
                ) {
                    EmbroideryBounds(
                        0,
                        0,
                        0,
                        0
                    )
                } else {
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
                }

            EmbroideryDesign(
                fileName =
                    fileName,
                format =
                    format,
                label =
                    label,
                points =
                    points,
                bounds =
                    bounds,
                stitchCount =
                    points.count {
                        it.command ==
                            StitchCommand.STITCH
                    },
                jumpCount =
                    points.count {
                        it.command ==
                            StitchCommand.JUMP
                    },
                colorChanges =
                    points.count {
                        it.command ==
                            StitchCommand.COLOR_CHANGE
                    },
                endFound =
                    points.any {
                        it.command ==
                            StitchCommand.END
                    },
                sourceBytes =
                    ByteArray(0),
                threadColors =
                    colors,
                isModified =
                    true,
                hoopProfile =
                    hoop,
                fabricProfile =
                    fabric,
                machineFinishing =
                    finishing
            )
        }
    }

    private fun writeNullableString(
        output: DataOutputStream,
        value: String?
    ) {
        output.writeBoolean(
            value !=
                null
        )

        if (
            value !=
                null
        ) {
            output.writeUTF(
                value.take(
                    2000
                )
            )
        }
    }

    private fun readNullableString(
        input: DataInputStream
    ): String? =
        if (
            input.readBoolean()
        ) {
            input.readUTF()
        } else {
            null
        }
}

object ProjectStore {
    private const val EXTENSION =
        ".flp"

    fun save(
        context: Context,
        design: EmbroideryDesign
    ): Result<SavedProjectSummary> =
        runCatching {
            val directory =
                projectDirectory(
                    context
                )

            if (
                !directory.exists()
            ) {
                check(
                    directory.mkdirs()
                ) {
                    "Não foi possível criar a biblioteca local."
                }
            }

            val id =
                projectId(
                    design
                )

            val file =
                File(
                    directory,
                    id +
                        EXTENSION
                )

            file.writeBytes(
                ProjectCodec.encode(
                    design
                )
            )

            file.setLastModified(
                System.currentTimeMillis()
            )

            summary(
                id =
                    id,
                design =
                    design,
                updatedAtMillis =
                    file.lastModified()
            )
        }

    fun list(
        context: Context
    ): Result<List<SavedProjectSummary>> =
        runCatching {
            val directory =
                projectDirectory(
                    context
                )

            if (
                !directory.exists()
            ) {
                return@runCatching emptyList()
            }

            directory
                .listFiles()
                .orEmpty()
                .filter {
                    it.isFile &&
                        it.extension
                            .equals(
                                "flp",
                                ignoreCase =
                                    true
                            )
                }
                .mapNotNull {
                        file ->
                    runCatching {
                        val design =
                            ProjectCodec
                                .decode(
                                    file.readBytes()
                                )

                        summary(
                            id =
                                file.nameWithoutExtension,
                            design =
                                design,
                            updatedAtMillis =
                                file.lastModified()
                        )
                    }.getOrNull()
                }
                .sortedByDescending {
                    it.updatedAtMillis
                }
        }

    fun load(
        context: Context,
        id: String
    ): Result<EmbroideryDesign> =
        runCatching {
            require(
                id.matches(
                    Regex(
                        "[a-f0-9]{24}"
                    )
                )
            ) {
                "Projeto inválido."
            }

            val file =
                File(
                    projectDirectory(
                        context
                    ),
                    id +
                        EXTENSION
                )

            require(
                file.exists()
            ) {
                "Projeto não encontrado."
            }

            ProjectCodec.decode(
                file.readBytes()
            )
        }

    fun delete(
        context: Context,
        id: String
    ): Result<Unit> =
        runCatching {
            require(
                id.matches(
                    Regex(
                        "[a-f0-9]{24}"
                    )
                )
            ) {
                "Projeto inválido."
            }

            val file =
                File(
                    projectDirectory(
                        context
                    ),
                    id +
                        EXTENSION
                )

            if (
                file.exists()
            ) {
                check(
                    file.delete()
                ) {
                    "Não foi possível excluir o projeto."
                }
            }
        }

    private fun projectDirectory(
        context: Context
    ): File =
        File(
            context.filesDir,
            "fiolab-projects"
        )

    private fun projectId(
        design: EmbroideryDesign
    ): String {
        val key =
            design.fileName
                .lowercase(
                    Locale.ROOT
                ) +
                "|" +
                (
                    design.label
                        ?: ""
                    )
                    .trim()
                    .lowercase(
                        Locale.ROOT
                    )

        return MessageDigest
            .getInstance(
                "SHA-256"
            )
            .digest(
                key.toByteArray(
                    Charsets.UTF_8
                )
            )
            .joinToString(
                separator = ""
            ) {
                "%02x".format(
                    it
                )
            }
            .take(
                24
            )
    }

    private fun summary(
        id: String,
        design: EmbroideryDesign,
        updatedAtMillis: Long
    ): SavedProjectSummary =
        SavedProjectSummary(
            id = id,
            title =
                design.label
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: design.fileName
                        .substringBeforeLast(
                            '.'
                        ),
            fileName =
                design.fileName,
            format =
                design.format,
            stitchCount =
                design.stitchCount,
            colorCount =
                design.colorCount,
            widthMm =
                design.bounds
                    .widthMm,
            heightMm =
                design.bounds
                    .heightMm,
            updatedAtMillis =
                updatedAtMillis
        )
}
