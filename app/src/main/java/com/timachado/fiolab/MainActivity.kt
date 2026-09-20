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
import com.timachado.fiolab.core.embroidery.EmbroideryDesign
import com.timachado.fiolab.core.embroidery.EmbroideryLoadResult
import com.timachado.fiolab.core.embroidery.EmbroideryLoader
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
        setContent { FioLabTheme { FioLabApp() } }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data class Viewer(val design: EmbroideryDesign) : Screen
    data class Simulator(val design: EmbroideryDesign) : Screen
}

@Composable
private fun FioLabApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var recent by remember { mutableStateOf<EmbroideryDesign?>(null) }
    var pendingSave by remember { mutableStateOf<EmbroideryDesign?>(null) }
    var loading by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        scope.launch {
            loading = true
            val result = withContext(Dispatchers.IO) {
                EmbroideryLoader.load(context.contentResolver, uri)
            }
            loading = false

            when (result) {
                is EmbroideryLoadResult.Success -> {
                    recent = result.design
                    screen = Screen.Viewer(result.design)
                }
                is EmbroideryLoadResult.Error -> {
                    snackbar.showSnackbar(result.userMessage)
                }
            }
        }
    }

    val saveCopyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { destination: Uri? ->
        val design = pendingSave
        pendingSave = null

        if (destination == null || design == null) {
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            loading = true
            val result = withContext(Dispatchers.IO) {
                MatrixExporter.saveCopy(
                    contentResolver = context.contentResolver,
                    destination = destination,
                    design = design
                )
            }
            loading = false

            result.fold(
                onSuccess = {
                    snackbar.showSnackbar("Cópia salva com sucesso.")
                },
                onFailure = {
                    snackbar.showSnackbar("Não foi possível salvar a cópia.")
                }
            )
        }
    }

    fun requestSave(design: EmbroideryDesign) {
        pendingSave = design
        saveCopyLauncher.launch(MatrixExporter.safeFileName(design.fileName))
    }

    fun requestShare(design: EmbroideryDesign) {
        scope.launch {
            loading = true
            val result = withContext(Dispatchers.IO) {
                MatrixExporter.createShareIntent(context, design)
            }
            loading = false

            result.fold(
                onSuccess = { shareIntent ->
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(
                                shareIntent,
                                "Compartilhar matriz"
                            )
                        )
                    }.onFailure {
                        snackbar.showSnackbar("Nenhum aplicativo disponível para compartilhar.")
                    }
                },
                onFailure = {
                    snackbar.showSnackbar("Não foi possível preparar o compartilhamento.")
                }
            )
        }
    }

    Scaffold(
        containerColor = FioBackground,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = screen) {
                Screen.Home -> HomeScreen(
                    recent = recent,
                    onOpen = { picker.launch(arrayOf("*/*")) },
                    onRecent = { recent?.let { screen = Screen.Viewer(it) } },
                    onSimulate = { recent?.let { screen = Screen.Simulator(it) } },
                    onUnavailable = { message ->
                        scope.launch { snackbar.showSnackbar(message) }
                    }
                )

                is Screen.Viewer -> ViewerScreen(
                    design = current.design,
                    onBack = { screen = Screen.Home },
                    onOpen = { picker.launch(arrayOf("*/*")) },
                    onSimulate = {
                        screen = Screen.Simulator(current.design)
                    },
                    onSaveCopy = {
                        requestSave(current.design)
                    },
                    onShare = {
                        requestShare(current.design)
                    }
                )

                is Screen.Simulator -> SimulatorScreen(
                    design = current.design,
                    onBack = { screen = Screen.Viewer(current.design) }
                )
            }

            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = FioGold
                )
            }
        }
    }
}
