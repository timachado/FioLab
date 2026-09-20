package com.timachado.fiolab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.timachado.fiolab.core.embroidery.EmbroideryFontPreset
import com.timachado.fiolab.core.embroidery.TextMatrixGenerator
import com.timachado.fiolab.core.embroidery.TextMatrixOptions
import com.timachado.fiolab.core.embroidery.TextStitchStyle
import com.timachado.fiolab.ui.theme.FioBackground
import com.timachado.fiolab.ui.theme.FioGold
import com.timachado.fiolab.ui.theme.FioSurface
import com.timachado.fiolab.ui.theme.FioSurfaceAlt
import com.timachado.fiolab.ui.theme.FioText
import com.timachado.fiolab.ui.theme.FioTextMuted
import java.util.Locale

private val namePalette =
    listOf(
        0xE6BE70,
        0xFFFFFF,
        0x111111,
        0xE63946,
        0xF4A261,
        0x2A9D8F,
        0x457B9D,
        0x9B5DE5,
        0xF4A7B9,
        0x6D597A
    )

@Composable
fun CreateNameScreen(
    onBack: () -> Unit,
    onCreate: (EmbroideryDesign) -> Unit,
    onSimulate: (EmbroideryDesign) -> Unit
) {
    var text by remember {
        mutableStateOf("Maria")
    }

    var heightMm by remember {
        mutableFloatStateOf(10f)
    }

    var spacingMm by remember {
        mutableFloatStateOf(1.2f)
    }

    var stitchLengthMm by remember {
        mutableFloatStateOf(2.5f)
    }

    var stitchStyle by remember {
        mutableStateOf(
            TextStitchStyle.SATIN
        )
    }

    var satinWidthMm by remember {
        mutableFloatStateOf(2.4f)
    }

    var satinDensityMm by remember {
        mutableFloatStateOf(0.45f)
    }

    var satinUnderlay by remember {
        mutableStateOf(true)
    }

    var font by remember {
        mutableStateOf(
            EmbroideryFontPreset.LINE
        )
    }

    var outputFormat by remember {
        mutableStateOf("DST")
    }

    var color by remember {
        mutableIntStateOf(
            0xE6BE70
        )
    }

    val result =
        TextMatrixGenerator.generate(
            TextMatrixOptions(
                text = text,
                heightMm = heightMm,
                spacingMm = spacingMm,
                stitchLengthMm =
                    stitchLengthMm,
                style =
                    stitchStyle,
                satinWidthMm =
                    satinWidthMm,
                satinDensityMm =
                    satinDensityMm,
                satinUnderlay =
                    satinUnderlay,
                color = color,
                font = font,
                outputFormat =
                    outputFormat
            )
        )

    val preview =
        result.getOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .padding(
                horizontal = 14.dp
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 4.dp
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
                Modifier.weight(1f)
            ) {
                Text(
                    "Criar Nome",
                    color = FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 19.sp
                )

                Text(
                    "Texto vira pontadas reais",
                    color = FioTextMuted,
                    fontSize = 10.sp
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(.82f)
                    .background(
                        Color(0xFF071017),
                        RoundedCornerShape(
                            24.dp
                        )
                    )
        ) {
            if (preview != null) {
                EmbroideryCanvas(
                    design = preview,
                    modifier =
                        Modifier.fillMaxSize()
                )
            } else {
                Text(
                    "Digite um nome para gerar a prévia.",
                    modifier =
                        Modifier.align(
                            Alignment.Center
                        ),
                    color = FioTextMuted
                )
            }
        }

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1.35f)
                    .padding(
                        vertical = 12.dp
                    ),
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
                Modifier
                    .padding(16.dp)
                    .verticalScroll(
                        rememberScrollState()
                    )
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it.take(24)
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            "Nome ou palavra"
                        )
                    },
                    supportingText = {
                        Text(
                            "Até 24 caracteres • acentos do português aceitos"
                        )
                    },
                    singleLine = true
                )

                Spacer(
                    Modifier.height(14.dp)
                )

                Text(
                    "Fonte de bordado",
                    color = FioText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        )
                        .padding(
                            vertical = 8.dp
                        ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    EmbroideryFontPreset
                        .entries
                        .forEach {
                                option ->
                            val selected =
                                font == option

                            Card(
                                onClick = {
                                    font = option
                                },
                                colors =
                                    CardDefaults
                                        .cardColors(
                                            containerColor =
                                                if (
                                                    selected
                                                ) {
                                                    FioSurfaceAlt
                                                } else {
                                                    FioSurface
                                                }
                                        ),
                                shape =
                                    RoundedCornerShape(
                                        14.dp
                                    )
                            ) {
                                Text(
                                    option.displayName,
                                    modifier =
                                        Modifier.padding(
                                            horizontal =
                                                14.dp,
                                            vertical =
                                                10.dp
                                        ),
                                    color =
                                        if (
                                            selected
                                        ) {
                                            FioGold
                                        } else {
                                            FioText
                                        },
                                    fontWeight =
                                        if (
                                            selected
                                        ) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        }
                                )
                            }
                        }
                }

                Text(
                    "Tipo de ponto",
                    color = FioText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = 8.dp
                        ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    TextStitchStyle
                        .entries
                        .forEach {
                                option ->
                            val selected =
                                stitchStyle ==
                                    option

                            OutlinedButton(
                                onClick = {
                                    stitchStyle =
                                        option
                                },
                                modifier =
                                    Modifier.weight(
                                        1f
                                    ),
                                colors =
                                    ButtonDefaults
                                        .outlinedButtonColors(
                                            contentColor =
                                                if (
                                                    selected
                                                ) {
                                                    FioGold
                                                } else {
                                                    FioText
                                                }
                                        )
                            ) {
                                Text(
                                    if (
                                        selected
                                    ) {
                                        "● " +
                                            option
                                                .displayName
                                    } else {
                                        option
                                            .displayName
                                    }
                                )
                            }
                        }
                }

                Text(
                    "Altura " +
                        mm(heightMm) +
                        " mm",
                    color = FioText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Slider(
                    value = heightMm,
                    onValueChange = {
                        heightMm = it
                    },
                    valueRange =
                        4f..30f
                )

                Text(
                    "Espaçamento " +
                        mm(spacingMm) +
                        " mm",
                    color = FioText,
                    fontSize = 12.sp
                )

                Slider(
                    value = spacingMm,
                    onValueChange = {
                        spacingMm = it
                    },
                    valueRange =
                        0f..6f
                )

                if (
                    stitchStyle ==
                        TextStitchStyle.RUNNING
                ) {
                    Text(
                        "Comprimento do ponto " +
                            mm(
                                stitchLengthMm
                            ) +
                            " mm",
                        color = FioText,
                        fontSize = 12.sp
                    )

                    Slider(
                        value =
                            stitchLengthMm,
                        onValueChange = {
                            stitchLengthMm = it
                        },
                        valueRange =
                            1f..4f
                    )
                } else {
                    Text(
                        "Largura Satin " +
                            mm(
                                satinWidthMm
                            ) +
                            " mm",
                        color = FioText,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    Slider(
                        value =
                            satinWidthMm,
                        onValueChange = {
                            satinWidthMm = it
                        },
                        valueRange =
                            1.2f..5f
                    )

                    Text(
                        "Densidade " +
                            mm(
                                satinDensityMm
                            ) +
                            " mm",
                        color = FioText,
                        fontSize = 12.sp
                    )

                    Slider(
                        value =
                            satinDensityMm,
                        onValueChange = {
                            satinDensityMm = it
                        },
                        valueRange =
                            0.35f..0.9f
                    )

                    OutlinedButton(
                        onClick = {
                            satinUnderlay =
                                !satinUnderlay
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                    ) {
                        Text(
                            if (
                                satinUnderlay
                            ) {
                                "✓ Underlay central ativado"
                            } else {
                                "Underlay central desativado"
                            }
                        )
                    }

                    Text(
                        "Menor densidade = mais pontadas e cobertura mais fechada.",
                        color =
                            FioTextMuted,
                        fontSize =
                            10.sp
                    )
                }

                Spacer(
                    Modifier.height(8.dp)
                )

                Text(
                    "Cor da linha",
                    color = FioText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        )
                        .padding(
                            vertical = 8.dp
                        ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            9.dp
                        )
                ) {
                    namePalette.forEach {
                            rawColor ->
                        val selected =
                            rawColor == color

                        Box(
                            Modifier
                                .size(38.dp)
                                .background(
                                    Color(
                                        0xFF000000 or
                                            rawColor
                                                .toLong()
                                    ),
                                    CircleShape
                                )
                                .border(
                                    if (
                                        selected
                                    ) {
                                        3.dp
                                    } else {
                                        1.dp
                                    },
                                    if (
                                        selected
                                    ) {
                                        FioGold
                                    } else {
                                        Color(
                                            0xFF52616B
                                        )
                                    },
                                    CircleShape
                                )
                                .clickable {
                                    color =
                                        rawColor
                                }
                        )
                    }
                }

                Text(
                    "Formato de saída",
                    color = FioText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = 8.dp
                        ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    listOf(
                        "DST",
                        "PES",
                        "JEF"
                    ).forEach {
                            format ->
                        val selected =
                            outputFormat ==
                                format

                        OutlinedButton(
                            onClick = {
                                outputFormat =
                                    format
                            },
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            colors =
                                ButtonDefaults
                                    .outlinedButtonColors(
                                        contentColor =
                                            if (
                                                selected
                                            ) {
                                                FioGold
                                            } else {
                                                FioText
                                            }
                                    )
                        ) {
                            Text(
                                if (
                                    selected
                                ) {
                                    "● $format"
                                } else {
                                    format
                                }
                            )
                        }
                    }
                }

                if (preview != null) {
                    Text(
                        "Prévia: " +
                            mm(
                                preview.bounds
                                    .widthMm
                            ) +
                            " × " +
                            mm(
                                preview.bounds
                                    .heightMm
                            ) +
                            " mm • " +
                            preview.stitchCount +
                            " pontos",
                        color = FioTextMuted,
                        fontSize = 11.sp
                    )
                } else {
                    Text(
                        result.exceptionOrNull()
                            ?.message
                            ?: "Não foi possível gerar a prévia.",
                        color = Color(
                            0xFFFF9F9A
                        ),
                        fontSize = 11.sp
                    )
                }

                Spacer(
                    Modifier.height(12.dp)
                )

                Button(
                    onClick = {
                        preview?.let(
                            onCreate
                        )
                    },
                    enabled =
                        preview != null,
                    modifier =
                        Modifier.fillMaxWidth(),
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
                        "Criar matriz",
                        fontWeight =
                            FontWeight.Bold
                    )
                }

                Spacer(
                    Modifier.height(8.dp)
                )

                OutlinedButton(
                    onClick = {
                        preview?.let(
                            onSimulate
                        )
                    },
                    enabled =
                        preview != null,
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        "▶ Simular agora"
                    )
                }

                Spacer(
                    Modifier.height(10.dp)
                )

                Text(
                    if (
                        stitchStyle ==
                            TextStitchStyle.SATIN
                    ) {
                        "Satin gera underlay e zigue-zague real de pontadas. A simulação mostra o preenchimento exatamente na ordem criada."
                    } else {
                        "Ponto corrido segue o centro do traço da letra. Não é uma fonte TTF comum convertida automaticamente."
                    },
                    color = FioTextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private fun mm(
    value: Float
): String =
    String.format(
        Locale.forLanguageTag(
            "pt-BR"
        ),
        "%.1f",
        value
    )
