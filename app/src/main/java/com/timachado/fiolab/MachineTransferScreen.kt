package com.timachado.fiolab

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.fiolab.core.embroidery.EmbroideryDesign
import com.timachado.fiolab.core.embroidery.MatrixConverter
import com.timachado.fiolab.ui.theme.FioBackground
import com.timachado.fiolab.ui.theme.FioGold
import com.timachado.fiolab.ui.theme.FioSurface
import com.timachado.fiolab.ui.theme.FioText
import com.timachado.fiolab.ui.theme.FioTextMuted

@Composable
fun MachineTransferScreen(
    design: EmbroideryDesign,
    onBack: () -> Unit,
    onUsbOtg: (String) -> Unit,
    onWifi: (String) -> Unit
) {
    val formats =
        MatrixConverter
            .supportedFormats

    var format by remember(
        design.fileName
    ) {
        mutableStateOf(
            design.format
                .uppercase()
                .takeIf {
                    it in formats
                }
                ?: formats.first()
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(
                horizontal =
                    18.dp
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical =
                        6.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack
            ) {
                Text(
                    "‹ Voltar",
                    color = FioGold
                )
            }

            Column(
                Modifier.weight(
                    1f
                )
            ) {
                Text(
                    "Enviar para a máquina",
                    color = FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        20.sp
                )

                Text(
                    "Escolha o formato e o caminho.",
                    color =
                        FioTextMuted,
                    fontSize =
                        11.sp
                )
            }
        }

        Spacer(
            Modifier.height(
                18.dp
            )
        )

        Card(
            modifier =
                Modifier
                    .fillMaxWidth(),
            colors =
                CardDefaults
                    .cardColors(
                        containerColor =
                            FioSurface
                    ),
            shape =
                RoundedCornerShape(
                    22.dp
                )
        ) {
            Column(
                Modifier.padding(
                    18.dp
                )
            ) {
                Text(
                    design.label
                        ?: design.fileName,
                    color = FioText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Text(
                    design.stitchCount
                        .toString() +
                        " pontos • " +
                        design.colorCount +
                        " bloco(s)",
                    color =
                        FioTextMuted,
                    fontSize =
                        11.sp
                )

                Spacer(
                    Modifier.height(
                        18.dp
                    )
                )

                Text(
                    "Formato da máquina",
                    color = FioText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical =
                                10.dp
                        ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    formats.forEach {
                            option ->
                        val selected =
                            format ==
                                option

                        OutlinedButton(
                            onClick = {
                                format =
                                    option
                            },
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        ) {
                            Text(
                                if (
                                    selected
                                ) {
                                    "● $option"
                                } else {
                                    option
                                },
                                color =
                                    if (
                                        selected
                                    ) {
                                        FioGold
                                    } else {
                                        FioText
                                    }
                            )
                        }
                    }
                }
            }
        }

        Spacer(
            Modifier.height(
                16.dp
            )
        )

        Card(
            modifier =
                Modifier
                    .fillMaxWidth(),
            colors =
                CardDefaults
                    .cardColors(
                        containerColor =
                            FioSurface
                    ),
            shape =
                RoundedCornerShape(
                    22.dp
                )
        ) {
            Column(
                Modifier.padding(
                    18.dp
                )
            ) {
                Text(
                    "Pendrive OTG",
                    color = FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        17.sp
                )

                Text(
                    "Conecte o pendrive ao celular. O Android abrirá o seletor de arquivos; escolha o armazenamento USB como destino.",
                    color =
                        FioTextMuted,
                    fontSize =
                        11.sp
                )

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                Button(
                    onClick = {
                        onUsbOtg(
                            format
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    colors =
                        ButtonDefaults
                            .buttonColors(
                                containerColor =
                                    FioGold,
                                contentColor =
                                    FioBackground
                            )
                ) {
                    Text(
                        "Salvar no pendrive OTG",
                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }
        }

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        Card(
            modifier =
                Modifier
                    .fillMaxWidth(),
            colors =
                CardDefaults
                    .cardColors(
                        containerColor =
                            FioSurface
                    ),
            shape =
                RoundedCornerShape(
                    22.dp
                )
        ) {
            Column(
                Modifier.padding(
                    18.dp
                )
            ) {
                Text(
                    "Wi-Fi / app da máquina",
                    color = FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        17.sp
                )

                Text(
                    "Gera o arquivo e abre os apps compatíveis instalados no Android. Se a bordadeira usa um app próprio ou pasta de rede, selecione esse destino.",
                    color =
                        FioTextMuted,
                    fontSize =
                        11.sp
                )

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                OutlinedButton(
                    onClick = {
                        onWifi(
                            format
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                ) {
                    Text(
                        "Enviar via Wi-Fi / app"
                    )
                }
            }
        }

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        Text(
            "O FioLab prepara e entrega o arquivo. O envio Wi‑Fi direto depende do protocolo/app suportado pela bordadeira.",
            color =
                FioTextMuted,
            fontSize =
                10.sp
        )
    }
}
