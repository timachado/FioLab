package com.timachado.fiolab

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
fun HomeScreen(
    recent: EmbroideryDesign?,
    onCreateName: () -> Unit,
    onCreateMonogram: () -> Unit,
    onCreateDrawing: () -> Unit,
    onTransfer: () -> Unit,
    onOpen: () -> Unit,
    onRecent: () -> Unit,
    onSimulate: () -> Unit,
    onEdit: () -> Unit,
    onConvert: () -> Unit,
    onUnavailable: (String) -> Unit
) {
    LazyVerticalGrid(
        columns =
            GridCells.Fixed(2),
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal =
                        16.dp
                ),
        contentPadding =
            PaddingValues(
                top = 16.dp,
                bottom = 24.dp
            ),
        horizontalArrangement =
            Arrangement.spacedBy(
                12.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                12.dp
            )
    ) {
        item(
            span = {
                GridItemSpan(
                    maxLineSpan
                )
            }
        ) {
            Column(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {
                Image(
                    painter =
                        painterResource(
                            R.drawable
                                .fiolab_brand_logo
                        ),
                    contentDescription =
                        "FioLab Matrizes",
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(
                                126.dp
                            ),
                    contentScale =
                        ContentScale.Fit
                )

                Text(
                    "Do nome à máquina, direto pelo celular.",
                    color =
                        FioTextMuted,
                    fontSize =
                        12.sp,
                    textAlign =
                        TextAlign.Center
                )
            }
        }

        item(
            span = {
                GridItemSpan(
                    maxLineSpan
                )
            }
        ) {
            Card(
                colors =
                    CardDefaults.cardColors(
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
                        "O que vamos bordar hoje?",
                        color = FioText,
                        fontSize = 20.sp,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    Spacer(
                        Modifier.height(6.dp)
                    )

                    Text(
                        "Acesso rápido às ferramentas principais. Conta, matrizes e fontes ficam sempre na barra inferior.",
                        color =
                            FioTextMuted,
                        fontSize =
                            12.sp
                    )
                }
            }
        }

        item(
            span = {
                GridItemSpan(
                    maxLineSpan
                )
            }
        ) {
            Text(
                "Criar e trabalhar",
                color = FioText,
                fontWeight =
                    FontWeight.Bold,
                fontSize = 17.sp,
                modifier =
                    Modifier.padding(
                        top = 2.dp,
                        bottom = 2.dp
                    )
            )
        }

        item {
            FeatureCard(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 138.dp
                        ),
                icon = "Aa",
                title =
                    "Criar nome",
                subtitle =
                    "Satin ou ponto corrido",
                enabled = true,
                onClick =
                    onCreateName
            )
        }

        item {
            FeatureCard(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 138.dp
                        ),
                icon = "ABC",
                title =
                    "Monograma",
                subtitle =
                    "1 • 2 • 3 iniciais",
                enabled = true,
                onClick =
                    onCreateMonogram
            )
        }

        item {
            FeatureCard(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 138.dp
                        ),
                icon = "✦",
                title =
                    "Desenho/logo",
                subtitle =
                    "SVG • desenhar com o dedo",
                enabled = true,
                onClick =
                    onCreateDrawing
            )
        }

        item {
            FeatureCard(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 138.dp
                        ),
                icon = "↗",
                title =
                    "Abrir matriz",
                subtitle =
                    "DST • JEF • PES",
                enabled = true,
                onClick =
                    onOpen
            )
        }

        item {
            FeatureCard(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 138.dp
                        ),
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
        }

        item {
            FeatureCard(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 138.dp
                        ),
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

        item {
            FeatureCard(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 138.dp
                        ),
                icon = "⇄",
                title =
                    "Converter",
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
            FeatureCard(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 138.dp
                        ),
                icon = "⇧",
                title =
                    "Enviar",
                subtitle =
                    "OTG • Wi-Fi/app",
                enabled =
                    recent != null
            ) {
                if (
                    recent != null
                ) {
                    onTransfer()
                } else {
                    onUnavailable(
                        "Crie ou abra uma matriz primeiro."
                    )
                }
            }
        }

        item(
            span = {
                GridItemSpan(
                    maxLineSpan
                )
            }
        ) {
            Text(
                "Recente",
                color = FioText,
                fontWeight =
                    FontWeight.Bold,
                fontSize = 17.sp,
                modifier =
                    Modifier.padding(
                        top = 4.dp
                    )
            )
        }

        item(
            span = {
                GridItemSpan(
                    maxLineSpan
                )
            }
        ) {
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
                            .padding(20.dp),
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Nenhuma matriz criada ou aberta nesta sessão.",
                            color =
                                FioTextMuted,
                            textAlign =
                                TextAlign.Center
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

        item(
            span = {
                GridItemSpan(
                    maxLineSpan
                )
            }
        ) {
            Text(
                "FioLab 0.21.6 • Android",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 4.dp
                        ),
                color =
                    FioTextMuted,
                textAlign =
                    TextAlign.Center,
                fontSize =
                    11.sp
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
                16.dp
            )
        ) {
            Box(
                Modifier
                    .size(40.dp)
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
                    14.dp
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
                color =
                    FioTextMuted,
                fontSize =
                    11.sp
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
