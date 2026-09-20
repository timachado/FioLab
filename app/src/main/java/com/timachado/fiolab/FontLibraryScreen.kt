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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.fiolab.core.embroidery.EmbroideryFontPreset
import com.timachado.fiolab.core.embroidery.SatinUnderlayMode
import com.timachado.fiolab.core.embroidery.TextMatrixGenerator
import com.timachado.fiolab.core.embroidery.TextMatrixOptions
import com.timachado.fiolab.core.embroidery.TextStitchStyle
import com.timachado.fiolab.ui.theme.FioGold
import com.timachado.fiolab.ui.theme.FioSurface
import com.timachado.fiolab.ui.theme.FioText
import com.timachado.fiolab.ui.theme.FioTextMuted

@Composable
fun FontLibraryScreen(
    onBack: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(
                horizontal =
                    14.dp
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical =
                        4.dp
                )
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
                Modifier.weight(1f)
            ) {
                Text(
                    "Biblioteca de Fontes",
                    color = FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 19.sp
                )

                Text(
                    "Famílias desenhadas para gerar pontadas",
                    color =
                        FioTextMuted,
                    fontSize =
                        10.sp
                )
            }
        }

        LazyColumn(
            modifier =
                Modifier.fillMaxSize(),
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {
            items(
                EmbroideryFontPreset
                    .entries
            ) {
                    font ->
                val preview =
                    remember(font) {
                        TextMatrixGenerator
                            .generate(
                                TextMatrixOptions(
                                    text = "FIO",
                                    heightMm =
                                        13f,
                                    style =
                                        TextStitchStyle
                                            .SATIN,
                                    satinWidthMm =
                                        2.2f,
                                    satinDensityMm =
                                        0.5f,
                                    satinUnderlayMode =
                                        SatinUnderlayMode
                                            .CENTER,
                                    font =
                                        font
                                )
                            )
                            .getOrNull()
                    }

                Card(
                    colors =
                        CardDefaults
                            .cardColors(
                                containerColor =
                                    FioSurface
                            ),
                    shape =
                        RoundedCornerShape(
                            20.dp
                        )
                ) {
                    Column(
                        Modifier.padding(
                            14.dp
                        )
                    ) {
                        Text(
                            font.displayName,
                            color = FioText,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            font.category
                                .displayName +
                                " • " +
                                font.description,
                            color =
                                FioTextMuted,
                            fontSize =
                                11.sp
                        )

                        Spacer(
                            Modifier.height(
                                10.dp
                            )
                        )

                        if (
                            preview != null
                        ) {
                            EmbroideryCanvas(
                                design =
                                    preview,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(
                                            110.dp
                                        )
                                        .background(
                                            Color(
                                                0xFF071017
                                            ),
                                            RoundedCornerShape(
                                                16.dp
                                            )
                                        )
                            )

                            Spacer(
                                Modifier.height(
                                    6.dp
                                )
                            )

                            Text(
                                preview
                                    .stitchCount
                                    .toString() +
                                    " pontos na amostra",
                                color =
                                    FioTextMuted,
                                fontSize =
                                    10.sp
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "As famílias usam geometrias próprias do FioLab; não são TTFs convertidas automaticamente.",
                    modifier =
                        Modifier.padding(
                            bottom =
                                24.dp
                        ),
                    color =
                        FioTextMuted,
                    fontSize =
                        10.sp
                )
            }
        }
    }
}
