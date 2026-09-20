package com.timachado.fiolab

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.timachado.fiolab.core.embroidery.ConvertedMatrix
import com.timachado.fiolab.core.embroidery.EmbroideryDesign
import com.timachado.fiolab.core.embroidery.EmbroideryLoadResult
import com.timachado.fiolab.core.embroidery.EmbroideryLoader
import com.timachado.fiolab.core.embroidery.MatrixConverter
import com.timachado.fiolab.core.embroidery.MatrixExporter
import com.timachado.fiolab.ui.theme.FioBackground
import com.timachado.fiolab.ui.theme.FioGold
import com.timachado.fiolab.ui.theme.FioLabTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FioLabTheme {
                FioLabApp()
            }
        }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object CreateName : Screen
    data object CreateMonogram : Screen
    data object CreateDrawing : Screen
    data object FontLibrary : Screen

    data class Transfer(
        val design: EmbroideryDesign
    ) : Screen

    data class Viewer(
        val design: EmbroideryDesign
    ) : Screen

    data class Simulator(
        val design: EmbroideryDesign
    ) : Screen

    data class Converter(
        val design: EmbroideryDesign
    ) : Screen

    data class Editor(
        val design: EmbroideryDesign
    ) : Screen
}

private data class PendingDocument(
    val fileName: String,
    val bytes: ByteArray,
    val successMessage: String
)

@Composable
private fun FioLabApp() {
    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    val snackbar =
        remember {
            SnackbarHostState()
        }

    var screen by remember {
        mutableStateOf<Screen>(
            Screen.Home
        )
    }

    var recent by remember {
        mutableStateOf<
            EmbroideryDesign?
        >(null)
    }

    var pendingDocument by remember {
        mutableStateOf<
            PendingDocument?
        >(null)
    }

    var loading by remember {
        mutableStateOf(false)
    }

    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts
                .OpenDocument()
        ) { uri: Uri? ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }

            runCatching {
                context.contentResolver
                    .takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
            }

            scope.launch {
                loading = true

                val result =
                    withContext(
                        Dispatchers.IO
                    ) {
                        EmbroideryLoader.load(
                            context.contentResolver,
                            uri
                        )
                    }

                loading = false

                when (result) {
                    is EmbroideryLoadResult.Success -> {
                        recent =
                            result.design

                        screen =
                            Screen.Viewer(
                                result.design
                            )
                    }

                    is EmbroideryLoadResult.Error -> {
                        snackbar.showSnackbar(
                            result.userMessage
                        )
                    }
                }
            }
        }

    val saveDocumentLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts
                .CreateDocument(
                    "application/octet-stream"
                )
        ) { destination: Uri? ->
            val document =
                pendingDocument

            pendingDocument = null

            if (
                destination == null ||
                document == null
            ) {
                return@rememberLauncherForActivityResult
            }

            scope.launch {
                loading = true

                val result =
                    withContext(
                        Dispatchers.IO
                    ) {
                        MatrixExporter.saveBytes(
                            contentResolver =
                                context.contentResolver,
                            destination =
                                destination,
                            bytes =
                                document.bytes
                        )
                    }

                loading = false

                result.fold(
                    onSuccess = {
                        snackbar.showSnackbar(
                            document.successMessage
                        )
                    },
                    onFailure = {
                        snackbar.showSnackbar(
                            "Não foi possível salvar o arquivo."
                        )
                    }
                )
            }
        }

    fun openShareIntent(
        matrix: ConvertedMatrix,
        chooserTitle: String =
            "Compartilhar matriz"
    ) {
        scope.launch {
            loading = true

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    MatrixExporter
                        .createShareIntent(
                            context = context,
                            fileName =
                                matrix.fileName,
                            bytes =
                                matrix.bytes
                        )
                }

            loading = false

            result.fold(
                onSuccess = {
                        shareIntent ->
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(
                                shareIntent,
                                chooserTitle
                            )
                        )
                    }.onFailure {
                        snackbar.showSnackbar(
                            "Nenhum aplicativo disponível para compartilhar."
                        )
                    }
                },
                onFailure = {
                    snackbar.showSnackbar(
                        "Não foi possível preparar o compartilhamento."
                    )
                }
            )
        }
    }

    fun convertForSave(
        design: EmbroideryDesign,
        targetFormat: String,
        suffix: String =
            "convertido"
    ) {
        scope.launch {
            loading = true

            val result =
                withContext(
                    Dispatchers.Default
                ) {
                    MatrixConverter.convert(
                        design =
                            design,
                        targetFormat =
                            targetFormat,
                        outputSuffix =
                            suffix
                    )
                }

            loading = false

            result.fold(
                onSuccess = {
                        converted ->
                    val name =
                        MatrixExporter
                            .safeFileName(
                                converted
                                    .fileName
                            )

                    pendingDocument =
                        PendingDocument(
                            fileName = name,
                            bytes =
                                converted.bytes,
                            successMessage =
                                when (suffix) {
                                    "editado" ->
                                        "Matriz editada salva com sucesso."

                                    "criado" ->
                                        "Matriz criada salva com sucesso."

                                    "maquina" ->
                                        "Arquivo da máquina salvo no destino escolhido."

                                    else ->
                                        "Matriz convertida para " +
                                            converted.format +
                                            " e salva com sucesso."
                                }
                        )

                    saveDocumentLauncher
                        .launch(name)
                },
                onFailure = {
                    snackbar.showSnackbar(
                        "Não foi possível gerar o arquivo."
                    )
                }
            )
        }
    }

    fun convertForShare(
        design: EmbroideryDesign,
        targetFormat: String,
        suffix: String =
            "convertido",
        chooserTitle: String =
            "Compartilhar matriz"
    ) {
        scope.launch {
            loading = true

            val result =
                withContext(
                    Dispatchers.Default
                ) {
                    MatrixConverter.convert(
                        design =
                            design,
                        targetFormat =
                            targetFormat,
                        outputSuffix =
                            suffix
                    )
                }

            loading = false

            result.fold(
                onSuccess = {
                    openShareIntent(
                        it,
                        chooserTitle
                    )
                },
                onFailure = {
                    snackbar.showSnackbar(
                        "Não foi possível gerar o arquivo para compartilhar."
                    )
                }
            )
        }
    }

    fun requestSave(
        design: EmbroideryDesign
    ) {
        if (design.isModified) {
            convertForSave(
                design =
                    design,
                targetFormat =
                    design.format,
                suffix =
                    if (
                        design.sourceBytes
                            .isEmpty()
                    ) {
                        "criado"
                    } else {
                        "editado"
                    }
            )

            return
        }

        val name =
            MatrixExporter.safeFileName(
                design.fileName
            )

        pendingDocument =
            PendingDocument(
                fileName = name,
                bytes =
                    design.sourceBytes,
                successMessage =
                    "Cópia salva com sucesso."
            )

        saveDocumentLauncher
            .launch(name)
    }

    fun requestShare(
        design: EmbroideryDesign
    ) {
        if (design.isModified) {
            convertForShare(
                design =
                    design,
                targetFormat =
                    design.format,
                suffix =
                    if (
                        design.sourceBytes
                            .isEmpty()
                    ) {
                        "criado"
                    } else {
                        "editado"
                    }
            )

            return
        }

        openShareIntent(
            ConvertedMatrix(
                fileName =
                    design.fileName,
                format =
                    design.format,
                bytes =
                    design.sourceBytes
            )
        )
    }

    Scaffold(
        containerColor =
            FioBackground,
        snackbarHost = {
            SnackbarHost(
                snackbar
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (
                val current =
                    screen
            ) {
                Screen.Home -> {
                    HomeScreen(
                        recent = recent,
                        onCreateName = {
                            screen =
                                Screen.CreateName
                        },
                        onCreateMonogram = {
                            screen =
                                Screen.CreateMonogram
                        },
                        onCreateDrawing = {
                            screen =
                                Screen.CreateDrawing
                        },
                        onFonts = {
                            screen =
                                Screen.FontLibrary
                        },
                        onTransfer = {
                            recent?.let {
                                screen =
                                    Screen.Transfer(
                                        it
                                    )
                            }
                        },
                        onOpen = {
                            picker.launch(
                                arrayOf("*/*")
                            )
                        },
                        onRecent = {
                            recent?.let {
                                screen =
                                    Screen
                                        .Viewer(it)
                            }
                        },
                        onSimulate = {
                            recent?.let {
                                screen =
                                    Screen
                                        .Simulator(
                                            it
                                        )
                            }
                        },
                        onEdit = {
                            recent?.let {
                                screen =
                                    Screen
                                        .Editor(
                                            it
                                        )
                            }
                        },
                        onConvert = {
                            recent?.let {
                                screen =
                                    Screen
                                        .Converter(
                                            it
                                        )
                            }
                        },
                        onUnavailable = {
                                message ->
                            scope.launch {
                                snackbar
                                    .showSnackbar(
                                        message
                                    )
                            }
                        }
                    )
                }

                Screen.FontLibrary -> {
                    FontLibraryScreen(
                        onBack = {
                            screen =
                                Screen.Home
                        }
                    )
                }

                Screen.CreateName -> {
                    CreateNameScreen(
                        onBack = {
                            screen =
                                Screen.Home
                        },
                        onCreate = {
                                created ->
                            recent =
                                created

                            screen =
                                Screen.Viewer(
                                    created
                                )
                        },
                        onSimulate = {
                                created ->
                            recent =
                                created

                            screen =
                                Screen.Simulator(
                                    created
                                )
                        }
                    )
                }

                Screen.CreateDrawing -> {
                    CreateDrawingScreen(
                        onBack = {
                            screen =
                                Screen.Home
                        },
                        onCreate = {
                                created ->
                            recent =
                                created

                            screen =
                                Screen.Viewer(
                                    created
                                )
                        },
                        onSimulate = {
                                created ->
                            recent =
                                created

                            screen =
                                Screen.Simulator(
                                    created
                                )
                        }
                    )
                }

                Screen.CreateMonogram -> {
                    MonogramScreen(
                        onBack = {
                            screen =
                                Screen.Home
                        },
                        onCreate = {
                                created ->
                            recent =
                                created

                            screen =
                                Screen.Viewer(
                                    created
                                )
                        },
                        onSimulate = {
                                created ->
                            recent =
                                created

                            screen =
                                Screen.Simulator(
                                    created
                                )
                        }
                    )
                }

                is Screen.Transfer -> {
                    MachineTransferScreen(
                        design =
                            current.design,
                        onBack = {
                            screen =
                                Screen.Viewer(
                                    current.design
                                )
                        },
                        onUsbOtg = {
                                format ->
                            convertForSave(
                                design =
                                    current.design,
                                targetFormat =
                                    format,
                                suffix =
                                    "maquina"
                            )
                        },
                        onWifi = {
                                format ->
                            convertForShare(
                                design =
                                    current.design,
                                targetFormat =
                                    format,
                                suffix =
                                    "maquina",
                                chooserTitle =
                                    "Enviar matriz por Wi-Fi / app da máquina"
                            )
                        }
                    )
                }

                is Screen.Viewer -> {
                    ViewerScreen(
                        design =
                            current.design,
                        onBack = {
                            screen =
                                Screen.Home
                        },
                        onOpen = {
                            picker.launch(
                                arrayOf("*/*")
                            )
                        },
                        onSimulate = {
                            screen =
                                Screen
                                    .Simulator(
                                        current
                                            .design
                                    )
                        },
                        onEdit = {
                            screen =
                                Screen
                                    .Editor(
                                        current
                                            .design
                                    )
                        },
                        onConvert = {
                            screen =
                                Screen
                                    .Converter(
                                        current
                                            .design
                                    )
                        },
                        onTransfer = {
                            screen =
                                Screen.Transfer(
                                    current.design
                                )
                        },
                        onSaveCopy = {
                            requestSave(
                                current
                                    .design
                            )
                        },
                        onShare = {
                            requestShare(
                                current
                                    .design
                            )
                        }
                    )
                }

                is Screen.Simulator -> {
                    SimulatorScreen(
                        design =
                            current.design,
                        onBack = {
                            screen =
                                Screen
                                    .Viewer(
                                        current
                                            .design
                                    )
                        }
                    )
                }

                is Screen.Converter -> {
                    ConverterScreen(
                        design =
                            current.design,
                        onBack = {
                            screen =
                                Screen
                                    .Viewer(
                                        current
                                            .design
                                    )
                        },
                        onSave = {
                                format ->
                            convertForSave(
                                design =
                                    current
                                        .design,
                                targetFormat =
                                    format
                            )
                        },
                        onShare = {
                                format ->
                            convertForShare(
                                design =
                                    current
                                        .design,
                                targetFormat =
                                    format
                            )
                        }
                    )
                }

                is Screen.Editor -> {
                    EditorScreen(
                        design =
                            current.design,
                        onBack = {
                            screen =
                                Screen
                                    .Viewer(
                                        current
                                            .design
                                    )
                        },
                        onApply = {
                                edited ->
                            recent =
                                edited

                            screen =
                                Screen
                                    .Viewer(
                                        edited
                                    )
                        }
                    )
                }
            }

            if (loading) {
                CircularProgressIndicator(
                    modifier =
                        Modifier.align(
                            Alignment.Center
                        ),
                    color =
                        FioGold
                )
            }
        }
    }
}
