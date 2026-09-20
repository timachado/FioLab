package com.timachado.fiolab.core.embroidery

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object MatrixExporter {
    fun saveCopy(
        contentResolver: ContentResolver,
        destination: Uri,
        design: EmbroideryDesign
    ): Result<Unit> = runCatching {
        val output = contentResolver.openOutputStream(destination, "w")
            ?: error("Não foi possível abrir o destino selecionado.")
        output.use { it.write(design.sourceBytes) }
    }

    fun createShareIntent(
        context: Context,
        design: EmbroideryDesign
    ): Result<Intent> = runCatching {
        val sharedDir = File(context.cacheDir, "shared").apply {
            if (!exists() && !mkdirs()) {
                error("Não foi possível preparar a pasta temporária.")
            }
        }

        val file = File(sharedDir, safeFileName(design.fileName))
        file.outputStream().use { it.write(design.sourceBytes) }

        val contentUri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )

        Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, design.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun safeFileName(input: String): String {
        val cleaned = input
            .replace(Regex("[\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .trim('.')
            .take(120)

        return cleaned.ifBlank { "matriz.dst" }
    }
}
