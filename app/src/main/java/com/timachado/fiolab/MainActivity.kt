package com.timachado.fiolab

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.timachado.fiolab.core.project.ProjectBackupStore
import com.timachado.fiolab.core.project.ProjectStore
import com.timachado.fiolab.core.project.SavedProjectSummary
import com.timachado.fiolab.ui.theme.FioBackground
import com.timachado.fiolab.ui.theme.FioGold
import com.timachado.fiolab.ui.theme.FioLabTheme
import com.timachado.fiolab.ui.theme.FioSurface
import com.timachado.fiolab.ui.theme.FioTextMuted
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
    data object ProjectLibrary : Screen
    data object Account : Screen

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

    var savedProjects by remember {
        mutableStateOf<
            List<SavedProjectSummary>
        >(emptyList())
    }

    var loading by remember {
        mutableStateOf(false)
    }

    fun goBack() {
        screen =
            when (
                val current =
                    screen
            ) {
                Screen.Home ->
                    Screen.Home

                Screen.Account,
                Screen.ProjectLibrary,
                Screen.FontLibrary,
                Screen.CreateName,
                Screen.CreateMonogram,
                Screen.CreateDrawing ->
                    Screen.Home

                is Screen.Transfer ->
                    Screen.Viewer(
                        current.design
                    )

                is Screen.Viewer ->
                    Screen.Home

                is Screen.Simulator ->
                    Screen.Viewer(
                        current.design
                    )

                is Screen.Converter ->
                    Screen.Viewer(
                        current.design
                    )

                is Screen.Editor ->
                    Screen.Viewer(
                        current.design
                    )
            }
    }

    BackHandler(
        enabled =
            screen !=
                Screen.Home
    ) {
        goBack()
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

    val restoreBackupLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts
                .OpenDocument()
        ) {
                uri: Uri? ->
            if (
                uri ==
                    null
            ) {
                return@rememberLauncherForActivityResult
            }

            scope.launch {
                loading = true

                val result =
                    withContext(
                        Dispatchers.IO
                    ) {
                        runCatching {
                            val bytes =
                                context
                                    .contentResolver
                                    .openInputStream(
                                        uri
                                    )
                                    ?.use {
                                        it.readBytes()
                                    }
                                    ?: error(
                                        "Não foi possível ler o backup."
                                    )

                            val count =
                                ProjectBackupStore
                                    .restoreBackup(
                                        context,
                                        bytes
                                    )
                                    .getOrThrow()

                            val projects =
                                ProjectStore
                                    .list(
                                        context
                                    )
                                    .getOrThrow()

                            Pair(
                                count,
                                projects
                            )
                        }
                    }

                loading = false

                result.fold(
                    onSuccess = {
                            restored ->
                        savedProjects =
                            restored.second

                        snackbar.showSnackbar(
                            restored.first
                                .toString() +
                                " matriz(es) restaurada(s)."
                        )
                    },
                    onFailure = {
                        snackbar.showSnackbar(
                            "Não foi possível restaurar este backup."
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

    fun createLibraryBackup() {
        scope.launch {
            loading = true

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    ProjectBackupStore
                        .exportBackup(
                            context
                        )
                }

            loading = false

            result.fold(
                onSuccess = {
                        bytes ->
                    val fileName =
                        "FioLab-backup.fiolab-backup"

                    pendingDocument =
                        PendingDocument(
                            fileName =
                                fileName,
                            bytes =
                                bytes,
                            successMessage =
                                "Backup de Minhas Matrizes salvo com sucesso."
                        )

                    saveDocumentLauncher
                        .launch(
                            fileName
                        )
                },
                onFailure = {
                    snackbar.showSnackbar(
                        it.message
                            ?: "Não foi possível criar o backup."
                    )
                }
            )
        }
    }

    fun openProjectLibrary() {
        scope.launch {
            loading = true

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    ProjectStore.list(
                        context
                    )
                }

            loading = false

            result.fold(
                onSuccess = {
                        projects ->
                    savedProjects =
                        projects

                    screen =
                        Screen.ProjectLibrary
                },
                onFailure = {
                    snackbar.showSnackbar(
                        "Não foi possível abrir Minhas Matrizes."
                    )
                }
            )
        }
    }

    fun saveProject(
        design: EmbroideryDesign
    ) {
        scope.launch {
            loading = true

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    ProjectStore.save(
                        context,
                        design
                    )
                }

            loading = false

            result.fold(
                onSuccess = {
                        saved ->
                    savedProjects =
                        listOf(
                            saved
                        ) +
                            savedProjects
                                .filter {
                                    it.id !=
                                        saved.id
                                }

                    snackbar.showSnackbar(
                        "Matriz salva em Minhas Matrizes."
                    )
                },
                onFailure = {
                    snackbar.showSnackbar(
                        "Não foi possível salvar a matriz no app."
                    )
                }
            )
        }
    }

    fun loadProject(
        project: SavedProjectSummary,
        transferDirectly: Boolean
    ) {
        scope.launch {
            loading = true

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    ProjectStore.load(
                        context,
                        project.id
                    )
                }

            loading = false

            result.fold(
                onSuccess = {
                        design ->
                    recent =
                        design

                    screen =
                        if (
                            transferDirectly
                        ) {
                            Screen.Transfer(
                                design
                            )
                        } else {
                            Screen.Viewer(
                                design
                            )
                        }
                },
                onFailure = {
                    snackbar.showSnackbar(
                        "Não foi possível reabrir esta matriz."
                    )
                }
            )
        }
    }

    fun deleteProject(
        project: SavedProjectSummary
    ) {
        scope.launch {
            loading = true

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    ProjectStore.delete(
                        context,
                        project.id
                    )
                }

            loading = false

            result.fold(
                onSuccess = {
                    savedProjects =
                        savedProjects
                            .filter {
                                it.id !=
                                    project.id
                            }

                    snackbar.showSnackbar(
                        "Matriz removida de Minhas Matrizes."
                    )
                },
                onFailure = {
                    snackbar.showSnackbar(
                        "Não foi possível excluir esta matriz."
                    )
                }
            )
        }
    }

    Scaffold(
        containerColor =
            FioBackground,
        snackbarHost = {
            SnackbarHost(
                snackbar
            )
        },
        bottomBar = {
            val topLevelScreen =
                screen == Screen.Home ||
                    screen == Screen.CreateName ||
                    screen == Screen.ProjectLibrary ||
                    screen == Screen.FontLibrary ||
                    screen == Screen.Account

            if (topLevelScreen) {
                NavigationBar(
                    containerColor =
                        FioSurface
                ) {
                    val navigationColors =
                        NavigationBarItemDefaults
                            .colors(
                                selectedIconColor =
                                    FioBackground,
                                selectedTextColor =
                                    FioGold,
                                indicatorColor =
                                    FioGold,
                                unselectedIconColor =
                                    FioTextMuted,
                                unselectedTextColor =
                                    FioTextMuted
                            )

                    NavigationBarItem(
                        selected =
                            screen ==
                                Screen.Home,
                        onClick = {
                            screen =
                                Screen.Home
                        },
                        icon = {
                            Text("⌂")
                        },
                        label = {
                            Text("Início")
                        },
                        colors =
                            navigationColors
                    )

                    NavigationBarItem(
                        selected =
                            screen ==
                                Screen.CreateName,
                        onClick = {
                            screen =
                                Screen.CreateName
                        },
                        icon = {
                            Text("Aa")
                        },
                        label = {
                            Text("Criar")
                        },
                        colors =
                            navigationColors
                    )

                    NavigationBarItem(
                        selected =
                            screen ==
                                Screen.ProjectLibrary,
                        onClick = {
                            if (
                                screen !=
                                    Screen.ProjectLibrary
                            ) {
                                openProjectLibrary()
                            }
                        },
                        icon = {
                            Text("▣")
                        },
                        label = {
                            Text("Matrizes")
                        },
                        colors =
                            navigationColors
                    )

                    NavigationBarItem(
                        selected =
                            screen ==
                                Screen.FontLibrary,
                        onClick = {
                            screen =
                                Screen.FontLibrary
                        },
                        icon = {
                            Text("Ff")
                        },
                        label = {
                            Text("Fontes")
                        },
                        colors =
                            navigationColors
                    )

                    NavigationBarItem(
                        selected =
                            screen ==
                                Screen.Account,
                        onClick = {
                            screen =
                                Screen.Account
                        },
                        icon = {
                            Text("☺")
                        },
                        label = {
                            Text("Conta")
                        },
                        colors =
                            navigationColors
                    )
                }
            }
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

                Screen.Account -> {
                    AccountHostScreen(
                        onBack = {
                            goBack()
                        }
                    )
                }

                Screen.ProjectLibrary -> {
                    ProjectLibraryScreen(
                        projects =
                            savedProjects,
                        onBack = {
                            goBack()
                        },
                        onOpen = {
                                project ->
                            loadProject(
                                project,
                                transferDirectly =
                                    false
                            )
                        },
                        onTransfer = {
                                project ->
                            loadProject(
                                project,
                                transferDirectly =
                                    true
                            )
                        },
                        onDelete = {
                                project ->
                            deleteProject(
                                project
                            )
                        },
                        onBackup = {
                            createLibraryBackup()
                        },
                        onRestore = {
                            restoreBackupLauncher
                                .launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/octet-stream",
                                        "*/*"
                                    )
                                )
                        }
                    )
                }

                Screen.FontLibrary -> {
                    FontLibraryScreen(
                        onBack = {
                            goBack()
                        }
                    )
                }

                Screen.CreateName -> {
                    CreateNameScreen(
                        onBack = {
                            goBack()
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
                            goBack()
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
                            goBack()
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
                            goBack()
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
                            goBack()
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
                        onSaveProject = {
                            saveProject(
                                current
                                    .design
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
                            goBack()
                        }
                    )
                }

                is Screen.Converter -> {
                    ConverterScreen(
                        design =
                            current.design,
                        onBack = {
                            goBack()
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
                            goBack()
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
