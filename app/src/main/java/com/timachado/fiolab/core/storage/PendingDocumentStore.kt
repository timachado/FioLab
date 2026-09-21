package com.timachado.fiolab.core.storage

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

data class DurablePendingDocument(
    val fileName: String,
    val bytes: ByteArray,
    val successMessage: String,
    val machineMethod: String? = null,
    val machineFormat: String? = null
)

object PendingDocumentCodec {
    private const val MAGIC =
        "FIOLAB_PENDING_DOCUMENT"

    private const val VERSION =
        1

    private const val MAX_BYTES =
        64 * 1024 * 1024

    fun encode(
        document: DurablePendingDocument
    ): ByteArray {
        require(
            document.fileName
                .isNotBlank()
        ) {
            "Nome do arquivo pendente inválido."
        }

        require(
            document.bytes
                .isNotEmpty()
        ) {
            "Arquivo pendente vazio."
        }

        require(
            document.bytes
                .size <=
                MAX_BYTES
        ) {
            "Arquivo pendente grande demais."
        }

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
                document.fileName
                    .take(
                        200
                    )
            )

            output.writeUTF(
                document.successMessage
                    .take(
                        500
                    )
            )

            writeNullableString(
                output,
                document.machineMethod
            )

            writeNullableString(
                output,
                document.machineFormat
            )

            output.writeInt(
                document.bytes
                    .size
            )

            output.write(
                document.bytes
            )
        }

        return buffer
            .toByteArray()
    }

    fun decode(
        bytes: ByteArray
    ): DurablePendingDocument =
        DataInputStream(
            ByteArrayInputStream(
                bytes
            )
        ).use {
                input ->
            require(
                input.readUTF() ==
                    MAGIC
            ) {
                "Documento pendente inválido."
            }

            require(
                input.readInt() ==
                    VERSION
            ) {
                "Versão de documento pendente não suportada."
            }

            val fileName =
                input.readUTF()

            val successMessage =
                input.readUTF()

            val machineMethod =
                readNullableString(
                    input
                )

            val machineFormat =
                readNullableString(
                    input
                )

            val size =
                input.readInt()

            require(
                size in
                    1..MAX_BYTES
            ) {
                "Tamanho do documento pendente inválido."
            }

            val payload =
                ByteArray(
                    size
                )

            input.readFully(
                payload
            )

            require(
                input.read() ==
                    -1
            ) {
                "Documento pendente contém dados extras."
            }

            DurablePendingDocument(
                fileName =
                    fileName,
                bytes =
                    payload,
                successMessage =
                    successMessage,
                machineMethod =
                    machineMethod,
                machineFormat =
                    machineFormat
            )
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
                    200
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

object PendingDocumentStore {
    private const val FILE_NAME =
        "fiolab-pending-document.bin"

    fun save(
        context: Context,
        document: DurablePendingDocument
    ): Result<Unit> =
        runCatching {
            AtomicFileWriter.write(
                target =
                    file(
                        context
                    ),
                bytes =
                    PendingDocumentCodec
                        .encode(
                            document
                        )
            )
        }

    fun load(
        context: Context
    ): Result<DurablePendingDocument?> =
        runCatching {
            val file =
                file(
                    context
                )

            if (
                !file.exists()
            ) {
                null
            } else {
                PendingDocumentCodec
                    .decode(
                        file.readBytes()
                    )
            }
        }

    fun clear(
        context: Context
    ): Result<Unit> =
        runCatching {
            val file =
                file(
                    context
                )

            if (
                file.exists()
            ) {
                check(
                    file.delete()
                ) {
                    "Não foi possível limpar o documento pendente."
                }
            }
        }

    private fun file(
        context: Context
    ): File =
        File(
            context.filesDir,
            FILE_NAME
        )
}
