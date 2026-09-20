package com.timachado.fiolab.core.embroidery

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale

object EmbroideryLoader {
    private val plannedExtensions = setOf(
        "dst",
        "pes",
        "jef",
        "vp3",
        "exp",
        "xxx",
        "u01",
        "tbf"
    )

    private val enabledExtensions = setOf(
        "dst",
        "pes",
        "jef"
    )

    fun load(
        contentResolver: ContentResolver,
        uri: Uri
    ): EmbroideryLoadResult {
        val displayName =
            resolveDisplayName(
                contentResolver,
                uri
            ) ?: "matriz"

        val declaredExtension =
            displayName
                .substringAfterLast(
                    '.',
                    ""
                )
                .lowercase(
                    Locale.ROOT
                )
                .takeIf {
                    it.isNotBlank()
                }

        val bytes =
            try {
                contentResolver
                    .openInputStream(
                        uri
                    )
                    ?.use {
                        it.readBytes()
                    }
            } catch (
                error:
                    SecurityException
            ) {
                return EmbroideryLoadResult.Error(
                    "O Android não permitiu acessar esse arquivo.",
                    error.message
                )
            } catch (
                error:
                    Exception
            ) {
                return EmbroideryLoadResult.Error(
                    "Não foi possível ler o arquivo selecionado.",
                    error.message
                )
            } ?: return EmbroideryLoadResult.Error(
                "O arquivo não pôde ser aberto."
            )

        if (
            bytes.isEmpty()
        ) {
            return EmbroideryLoadResult.Error(
                "O arquivo selecionado está vazio."
            )
        }

        if (
            declaredExtension !=
                null &&
            declaredExtension in
                plannedExtensions &&
            declaredExtension !in
                enabledExtensions
        ) {
            return EmbroideryLoadResult.Error(
                "O formato " +
                    declaredExtension
                        .uppercase(
                            Locale.ROOT
                        ) +
                    " ainda não está habilitado.",
                "Suporte ativo: DST, JEF e PES"
            )
        }

        val candidates =
            buildCandidates(
                declaredExtension =
                    declaredExtension,
                bytes =
                    bytes
            )

        candidates.forEach {
                extension ->
            val parseName =
                normalizedFileName(
                    displayName,
                    extension
                )

            val result =
                parseByExtension(
                    extension =
                        extension,
                    fileName =
                        parseName,
                    bytes =
                        bytes
                )

            if (
                result is
                    EmbroideryLoadResult.Success
            ) {
                return result
            }
        }

        return EmbroideryLoadResult.Error(
            "Selecione uma matriz DST, PES ou JEF válida."
        )
    }

    private fun buildCandidates(
        declaredExtension: String?,
        bytes: ByteArray
    ): List<String> {
        val candidates =
            linkedSetOf<String>()

        if (
            declaredExtension in
                enabledExtensions
        ) {
            candidates +=
                declaredExtension!!
        }

        detectSignature(
            bytes
        )?.let {
            candidates +=
                it
        }

        // JEF não possui uma assinatura textual simples tão confiável quanto
        // PES/DST. Quando o Android omite a extensão, o parser JEF é tentado
        // antes de desistir.
        candidates +=
            listOf(
                "pes",
                "jef",
                "dst"
            )

        return candidates.toList()
    }

    private fun detectSignature(
        bytes: ByteArray
    ): String? {
        if (
            bytes.size >=
                4
        ) {
            val prefix =
                bytes.copyOfRange(
                    0,
                    4
                )
                    .toString(
                        Charsets.US_ASCII
                    )

            if (
                prefix ==
                    "#PES"
            ) {
                return "pes"
            }
        }

        if (
            bytes.size >=
                512
        ) {
            val header =
                bytes.copyOfRange(
                    0,
                    512
                )
                    .toString(
                        Charsets.US_ASCII
                    )

            if (
                header.startsWith(
                    "LA:"
                ) ||
                header.contains(
                    "\nLA:"
                )
            ) {
                return "dst"
            }
        }

        return null
    }

    private fun parseByExtension(
        extension: String,
        fileName: String,
        bytes: ByteArray
    ): EmbroideryLoadResult =
        when (
            extension
        ) {
            "dst" ->
                DstParser.parse(
                    fileName,
                    bytes
                )

            "pes",
            "jef" ->
                EmbroideryIoParser
                    .parse(
                        fileName,
                        bytes
                    )

            else ->
                EmbroideryLoadResult.Error(
                    "Formato não habilitado."
                )
        }

    private fun normalizedFileName(
        displayName: String,
        extension: String
    ): String {
        val currentExtension =
            displayName
                .substringAfterLast(
                    '.',
                    ""
                )
                .lowercase(
                    Locale.ROOT
                )

        return if (
            currentExtension ==
                extension
        ) {
            displayName
        } else {
            displayName
                .substringBeforeLast(
                    '.',
                    displayName
                )
                .ifBlank {
                    "matriz"
                } +
                "." +
                extension
        }
    }

    private fun resolveDisplayName(
        contentResolver: ContentResolver,
        uri: Uri
    ): String? =
        runCatching {
            contentResolver.query(
                uri,
                arrayOf(
                    OpenableColumns
                        .DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use {
                    cursor ->
                val index =
                    cursor.getColumnIndex(
                        OpenableColumns
                            .DISPLAY_NAME
                    )

                if (
                    index >=
                        0 &&
                    cursor.moveToFirst()
                ) {
                    cursor.getString(
                        index
                    )
                } else {
                    null
                }
            }
        }.getOrNull()
}
