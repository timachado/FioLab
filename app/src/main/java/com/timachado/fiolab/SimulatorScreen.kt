package com.timachado.fiolab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.fiolab.core.embroidery.EmbroideryDesign
import com.timachado.fiolab.core.embroidery.StitchCommand
import com.timachado.fiolab.core.embroidery.estimateThreadMeters
import com.timachado.fiolab.ui.theme.FioBackground
import com.timachado.fiolab.ui.theme.FioGold
import com.timachado.fiolab.ui.theme.FioSurface
import com.timachado.fiolab.ui.theme.FioSurfaceAlt
import com.timachado.fiolab.ui.theme.FioText
import com.timachado.fiolab.ui.theme.FioTextMuted
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun SimulatorScreen(
    design: EmbroideryDesign,
    onBack: () -> Unit
) {
    val points = remember(design) {
        design.points.filter { it.command != StitchCommand.END }
    }
    var index by remember(design.fileName) { mutableIntStateOf(0) }
    var playing by remember(design.fileName) { mutableStateOf(false) }
    var speed by remember(design.fileName) { mutableFloatStateOf(1f) }

    LaunchedEffect(playing, speed, design.fileName) {
        while (playing && index < points.size) {
            val step = when {
                speed >= 4f -> 20
                speed >= 2f -> 8
                else -> 2
            }
            index = (index + step).coerceAtMost(points.size)
            delay((35f / speed).toLong().coerceAtLeast(8))
        }
        if (index >= points.size) {
            playing = false
        }
    }

    val progress = if (points.isEmpty()) 0f else index.toFloat() / points.size
    val point = points.getOrNull((index - 1).coerceAtLeast(0))
    val block = (point?.colorIndex ?: 0) + 1
    val thread = remember(design) { estimateThreadMeters(design) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("‹ Voltar", color = FioGold)
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Simular bordado",
                    color = FioText,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    ((progress * 100).toInt()).toString() + "% concluído",
                    color = FioTextMuted,
                    fontSize = 11.sp
                )
            }
            Text(
                block.toString() + "/" + design.colorCount,
                color = FioGold,
                fontWeight = FontWeight.Bold
            )
        }

        EmbroideryCanvas(
            design = design,
            pointLimit = index,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .background(Color(0xFF071017), RoundedCornerShape(24.dp))
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = FioSurface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Slider(
                    value = progress,
                    onValueChange = {
                        playing = false
                        index = (it * points.size).toInt().coerceIn(0, points.size)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = FioGold,
                        activeTrackColor = FioGold,
                        inactiveTrackColor = FioSurfaceAlt
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            playing = false
                            index = (index - 50).coerceAtLeast(0)
                        }
                    ) {
                        Text("−50", color = FioGold)
                    }

                    Button(
                        onClick = {
                            if (index >= points.size) {
                                index = 0
                            }
                            playing = !playing
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FioGold,
                            contentColor = FioBackground
                        )
                    ) {
                        Text(
                            if (playing) "Ⅱ Pausar" else "▶ Reproduzir",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(
                        onClick = {
                            playing = false
                            index = (index + 50).coerceAtMost(points.size)
                        }
                    ) {
                        Text("+50", color = FioGold)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Velocidade " + oneDecimal(speed) + "×",
                    color = FioTextMuted,
                    fontSize = 11.sp
                )
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    valueRange = 0.5f..5f,
                    colors = SliderDefaults.colors(
                        thumbColor = FioGold,
                        activeTrackColor = FioGold,
                        inactiveTrackColor = FioSurfaceAlt
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoChip(
                        index.toString() + "/" + points.size,
                        "eventos",
                        Modifier.weight(1f)
                    )
                    InfoChip(
                        block.toString() + "/" + design.colorCount,
                        "bloco",
                        Modifier.weight(1f)
                    )
                    InfoChip(
                        twoDecimals(thread) + " m",
                        "linha est.",
                        Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    "Estimativa de linha: trajeto de costura × 2,2. O consumo real varia por tecido, tensão e máquina.",
                    color = FioTextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private fun oneDecimal(value: Float): String =
    String.format(Locale("pt", "BR"), "%.1f", value)

private fun twoDecimals(value: Double): String =
    String.format(Locale("pt", "BR"), "%.2f", value)
