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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.fiolab.core.embroidery.EmbroideryDesign
import com.timachado.fiolab.ui.theme.FioBackground
import com.timachado.fiolab.ui.theme.FioGold
import com.timachado.fiolab.ui.theme.FioSurface
import com.timachado.fiolab.ui.theme.FioSurfaceAlt
import com.timachado.fiolab.ui.theme.FioText
import com.timachado.fiolab.ui.theme.FioTextMuted
import java.util.Locale

@Composable
fun ViewerScreen(
    design: EmbroideryDesign,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onSimulate: () -> Unit,
    onSaveCopy: () -> Unit,
    onShare: () -> Unit
) {
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
                    design.fileName,
                    color = FioText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    "DST • original protegido",
                    color = FioTextMuted,
                    fontSize = 11.sp
                )
            }

            TextButton(onClick = onOpen) {
                Text("Abrir", color = FioGold)
            }
        }

        EmbroideryCanvas(
            design = design,
            interactive = true,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .background(
                    Color(0xFF071017),
                    RoundedCornerShape(24.dp)
                )
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = FioSurface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "Informações da matriz",
                    color = FioText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                design.label?.let {
                    Text(
                        "Identificação: " + it,
                        color = FioTextMuted,
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoChip(
                        design.stitchCount.toString(),
                        "pontos",
                        Modifier.weight(1f)
                    )
                    InfoChip(
                        mm(design.bounds.widthMm) + " × " +
                            mm(design.bounds.heightMm),
                        "mm",
                        Modifier.weight(1f)
                    )
                    InfoChip(
                        design.colorCount.toString(),
                        "blocos",
                        Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onSimulate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FioGold,
                        contentColor = FioBackground
                    )
                ) {
                    Text(
                        "▶ Simular bordado",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onSaveCopy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Salvar cópia")
                    }

                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Compartilhar")
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    "Salvar cópia abre o seletor do Android. Nele você pode escolher memória interna, cartão SD ou pendrive OTG disponível.",
                    color = FioTextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun InfoChip(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = FioSurfaceAlt),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(
                vertical = 10.dp,
                horizontal = 6.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                color = FioText,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                label,
                color = FioTextMuted,
                fontSize = 10.sp
            )
        }
    }
}

private fun mm(value: Float): String =
    String.format(Locale("pt", "BR"), "%.1f", value)
