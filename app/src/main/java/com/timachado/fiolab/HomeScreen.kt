package com.timachado.fiolab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.fiolab.core.embroidery.EmbroideryDesign
import com.timachado.fiolab.ui.theme.FioBackground
import com.timachado.fiolab.ui.theme.FioGold
import com.timachado.fiolab.ui.theme.FioGoldSoft
import com.timachado.fiolab.ui.theme.FioSurface
import com.timachado.fiolab.ui.theme.FioSurfaceAlt
import com.timachado.fiolab.ui.theme.FioText
import com.timachado.fiolab.ui.theme.FioTextMuted
import java.util.Locale

@Composable
fun HomeScreen(
    recent: EmbroideryDesign?,
    onCreateName: () -> Unit,
    onOpen: () -> Unit,
    onRecent: () -> Unit,
    onSimulate: () -> Unit,
    onEdit: () -> Unit,
    onConvert: () -> Unit,
    onUnavailable: (String) -> Unit
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal =
                        20.dp
                ),
        contentPadding =
            PaddingValues(
                top = 24.dp,
                bottom = 32.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                18.dp
            )
    ) {
        item {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .background(
                            FioGold,
                            CircleShape
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {
                    Text(
                        "F",
                        color = FioBackground,
                        fontWeight =
                            FontWeight.Black,
                        fontSize = 28.sp
                    )
                }

                Spacer(
                    Modifier.width(12.dp)
                )

                Column {
                    Text(
                        "FioLab",
                        color = FioGoldSoft,
                        fontSize = 28.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        "Do nome à máquina, direto pelo celular.",
                        color = FioTextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            FioSurface
                    ),
                shape =
                    RoundedCornerShape(
                        24.dp
                    )
            ) {
                Column(
                    Modifier.padding(
                        20.dp
                    )
                ) {
                    Text(
                        "O que vamos bordar hoje?",
                        color = FioText,
                        fontSize = 21.sp,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    Spacer(
                        Modifier.height(6.dp)
                    )

                    Text(
                        "FioLab 0.10 cria nomes em Satin refinado, edita, simula e converte DST, PES e JEF.",
                        color = FioTextMuted
                    )
                }
            }
        }

        item {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {
                FeatureCard(
                    modifier =
                        Modifier.weight(1f),
                    icon = "Aa",
                    title =
                        "Criar nome",
                    subtitle =
                        "Fonte de bordado",
                    enabled = true,
                    onClick =
                        onCreateName
                )

                FeatureCard(
                    modifier =
                        Modifier.weight(1f),
                    icon = "↗",
                    title =
                        "Abrir matriz",
                    subtitle =
                        "DST • JEF • PES",
                    enabled = true,
                    onClick = onOpen
                )
            }
        }

        item {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {
                FeatureCard(
                    modifier =
                        Modifier.weight(1f),
                    icon = "▶",
                    title =
                        "Simulador",
                    subtitle =
                        "Sequência real",
                    enabled =
                        recent != null
                ) {
                    if (
                        recent != null
                    ) {
                        onSimulate()
                    } else {
                        onUnavailable(
                            "Crie ou abra uma matriz primeiro."
                        )
                    }
                }

                FeatureCard(
                    modifier =
                        Modifier.weight(1f),
                    icon = "✎",
                    title =
                        "Editor",
                    subtitle =
                        "Mover • girar • cores",
                    enabled =
                        recent != null
                ) {
                    if (
                        recent != null
                    ) {
                        onEdit()
                    } else {
                        onUnavailable(
                            "Crie ou abra uma matriz primeiro."
                        )
                    }
                }
            }
        }

        item {
            FeatureCard(
                modifier =
                    Modifier.fillMaxWidth(),
                icon = "⇄",
                title = "Converter",
                subtitle =
                    "DST ⇄ PES ⇄ JEF",
                enabled =
                    recent != null
            ) {
                if (
                    recent != null
                ) {
                    onConvert()
                } else {
                    onUnavailable(
                        "Crie ou abra uma matriz primeiro."
                    )
                }
            }
        }

        item {
            Text(
                "Recente",
                color = FioText,
                fontWeight =
                    FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        item {
            if (recent == null) {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                FioSurface
                        ),
                    shape =
                        RoundedCornerShape(
                            20.dp
                        )
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Nenhuma matriz criada ou aberta nesta sessão.",
                            color =
                                FioTextMuted
                        )

                        Spacer(
                            Modifier.height(
                                12.dp
                            )
                        )

                        Button(
                            onClick =
                                onCreateName,
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
                                "Criar primeiro nome",
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                Card(
                    onClick =
                        onRecent,
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                FioSurface
                        ),
                    shape =
                        RoundedCornerShape(
                            20.dp
                        )
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Text(
                            recent.fileName,
                            color = FioText,
                            fontWeight =
                                FontWeight.SemiBold
                        )

                        Text(
                            recent.format +
                                " • " +
                                formatMm(
                                    recent.bounds
                                        .widthMm
                                ) +
                                " × " +
                                formatMm(
                                    recent.bounds
                                        .heightMm
                                ) +
                                " mm • " +
                                recent.stitchCount +
                                " pontos" +
                                when {
                                    recent.sourceBytes
                                        .isEmpty() ->
                                        " • criada"

                                    recent.isModified ->
                                        " • editada"

                                    else ->
                                        ""
                                },
                            color =
                                FioTextMuted,
                            fontSize =
                                12.sp
                        )
                    }
                }
            }
        }

        item {
            Text(
                "FioLab 0.10.0 • Android",
                modifier =
                    Modifier.fillMaxWidth(),
                color = FioTextMuted,
                textAlign =
                    TextAlign.Center,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun FeatureCard(
    modifier: Modifier,
    icon: String,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (enabled) {
                        FioSurfaceAlt
                    } else {
                        FioSurface
                    }
            ),
        shape =
            RoundedCornerShape(
                20.dp
            )
    ) {
        Column(
            Modifier.padding(
                18.dp
            )
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .background(
                        if (enabled) {
                            FioGold
                        } else {
                            FioSurfaceAlt
                        },
                        CircleShape
                    ),
                contentAlignment =
                    Alignment.Center
            ) {
                Text(
                    icon,
                    color =
                        if (enabled) {
                            FioBackground
                        } else {
                            FioGold
                        },
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Spacer(
                Modifier.height(
                    18.dp
                )
            )

            Text(
                title,
                color = FioText,
                fontWeight =
                    FontWeight.SemiBold
            )

            Text(
                subtitle,
                color = FioTextMuted,
                fontSize = 11.sp
            )
        }
    }
}

private fun formatMm(
    value: Float
): String =
    String.format(
        Locale.forLanguageTag(
            "pt-BR"
        ),
        "%.1f",
        value
    )
