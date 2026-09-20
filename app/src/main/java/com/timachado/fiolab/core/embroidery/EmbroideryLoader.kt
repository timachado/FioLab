package com.timachado.fiolab.core.embroidery

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale

object EmbroideryLoader {
    private val plannedExtensions = setOf("dst", "pes", "jef", "vp3", "exp", "xxx", "u01", "tbf")

    fun load(contentResolver: ContentResolver, uri: Uri): EmbroideryLoadResult {
        val fileName = resolveDisplayName(contentResolver, uri) ?: "matriz"
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)

        if (extension !in plannedExtensions) {
            return EmbroideryLoadResult.Error("Selecione uma matriz de bordado compatível.")
        }
        if (extension != "dst") {
            return EmbroideryLoadResult.Error(
                "O formato " + extension.uppercase() + " está no roteiro, mas ainda não foi liberado na versão 0.2.",
                "Suporte ativo: DST"
            )
        }

        val bytes = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (error: SecurityException) {
            return EmbroideryLoadResult.Error("O Android não permitiu acessar esse arquivo.", error.message)
        } catch (error: Exception) {
            return EmbroideryLoadResult.Error("Não foi possível ler o arquivo selecionado.", error.message)
        } ?: return EmbroideryLoadResult.Error("O arquivo não pôde ser aberto.")

        return DstParser.parse(fileName, bytes)
    }

    private fun resolveDisplayName(contentResolver: ContentResolver, uri: Uri): String? =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        }.getOrNull()
}
