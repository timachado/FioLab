package com.timachado.fiolab.core.project

import android.content.Context
import com.timachado.fiolab.core.embroidery.EmbroideryDesign
import java.io.File

object ActiveDesignStore {
    private const val FILE_NAME =
        "fiolab-active-project.flp"

    fun save(
        context: Context,
        design: EmbroideryDesign
    ): Result<Unit> =
        runCatching {
            activeFile(
                context
            ).writeBytes(
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
                ProjectCodec.decode(
                    file.readBytes()
                )
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
                file.delete()
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
