package com.timachado.fiolab.core.project

import android.content.Context
import com.timachado.fiolab.core.embroidery.EmbroideryDesign
import com.timachado.fiolab.core.storage.AtomicFileWriter
import java.io.File

object ActiveDesignStore {
    private const val FILE_NAME =
        "fiolab-active-project.flp"

    fun save(
        context: Context,
        design: EmbroideryDesign
    ): Result<Unit> =
        runCatching {
            AtomicFileWriter.write(
                target =
                    activeFile(
                        context
                    ),
                bytes =
                    ProjectCodec.encode(
                        design
                    )
            )
        }

    fun load(
        context: Context
    ): Result<EmbroideryDesign?> =
        runCatching {
            val file =
                activeFile(
                    context
                )

            if (
                !file.exists()
            ) {
                null
            } else {
                runCatching {
                    ProjectCodec.decode(
                        file.readBytes()
                    )
                }.getOrElse {
                        error ->
                    val quarantine =
                        File(
                            file.parentFile,
                            FILE_NAME +
                                ".corrupt"
                        )

                    runCatching {
                        if (
                            quarantine.exists()
                        ) {
                            quarantine.delete()
                        }

                        file.renameTo(
                            quarantine
                        )
                    }

                    throw IllegalStateException(
                        "O trabalho automático anterior estava corrompido e foi isolado.",
                        error
                    )
                }
            }
        }

    fun clear(
        context: Context
    ): Result<Unit> =
        runCatching {
            val file =
                activeFile(
                    context
                )

            if (
                file.exists()
            ) {
                check(
                    file.delete()
                ) {
                    "Não foi possível limpar o trabalho automático."
                }
            }
        }

    private fun activeFile(
        context: Context
    ): File =
        File(
            context.filesDir,
            FILE_NAME
        )
}
