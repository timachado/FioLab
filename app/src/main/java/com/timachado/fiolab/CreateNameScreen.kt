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
import androidx.compose.foundation.layout.weight
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.fiolab.core.embroidery.EmbroideryDesign
import com.timachado.fiolab.core.embroidery.EmbroideryFontPreset
import com.timachado.fiolab.core.embroidery.FabricProfile
import com.timachado.fiolab.core.embroidery.HoopProfile
import com.timachado.fiolab.core.embroidery.HoopValidator
import com.timachado.fiolab.core.embroidery.SatinUnderlayMode
import com.timachado.fiolab.core.embroidery.TextGlyphProvider
import com.timachado.fiolab.core.embroidery.TextLayoutGenerator
import com.timachado.fiolab.core.embroidery.TextLayoutMode
import com.timachado.fiolab.core.embroidery.TextLayoutOptions
import com.timachado.fiolab.core.embroidery.TextMatrixOptions
import com.timachado.fiolab.core.embroidery.TextStitchStyle
import com.timachado.fiolab.font.ImportedFontMatrixGenerator
import com.timachado.fiolab.font.ImportedFontStore
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
    val context =
        LocalContext.current

    val importedFonts =
        remember {
            ImportedFontStore
                .list(context)
        }

    var selectedTab by remember {
        mutableStateOf("Texto")
    }

    var importedFontId by remember {
        mutableStateOf<String?>(
            null
        )
    }

    var text by remember {
        mutableStateOf("Maria")
    }

    var heightMm by remember {
        mutableFloatStateOf(18f)
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

    var satinPullCompensationMm by remember {
        mutableFloatStateOf(0.2f)
    }

    var satinShortStitches by remember {
        mutableStateOf(true)
    }

    var satinUnderlayMode by remember {
        mutableStateOf(
            SatinUnderlayMode.CENTER
        )
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
            0xE63946
        )
    }

    var hoopProfile by remember {
        mutableStateOf(
            HoopProfile.H100X100
        )
    }

    var fabricProfile by remember {
        mutableStateOf(
            FabricProfile.COTTON
        )
    }

    var layoutMode by remember {
        mutableStateOf(
            TextLayoutMode.STRAIGHT
        )
    }

    var arcHeightMm by remember {
        mutableFloatStateOf(8f)
    }

    val importedFont =
        importedFonts
            .firstOrNull {
                it.id ==
                    importedFontId
            }

    val glyphProvider =
        importedFont
            ?.let {
                    selected ->
                TextGlyphProvider(
                    preserveCase =
                        true,
                    spacingScale =
                        1f,
                    generate = {
                            char,
                            glyphOptions ->
                        ImportedFontMatrixGenerator
                            .generateGlyph(
                                font =
                                    selected,
                                char =
                                    char,
                                options =
                                    glyphOptions
                            )
                    },
                    generateText = {
                            sourceText,
                            textOptions ->
                        ImportedFontMatrixGenerator
                            .generateText(
                                font =
                                    selected,
                                text =
                                    sourceText,
                                options =
                                    textOptions
                            )
                    }
                )
            }

    val result =
        TextLayoutGenerator.generate(
            TextLayoutOptions(
                textOptions =
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
                        satinPullCompensationMm =
                            satinPullCompensationMm,
                        satinShortStitches =
                            satinShortStitches,
                        satinUnderlayMode =
                            satinUnderlayMode,
                        color = color,
                        font = font,
                        outputFormat =
                            outputFormat,
                        hoopProfile =
                            hoopProfile,
                        fabricProfile =
                            fabricProfile
                    ),
                layoutMode =
                    layoutMode,
                arcHeightMm =
                    arcHeightMm,
                glyphProvider =
                    glyphProvider
            )
        )

    val preview =
        result.getOrNull()

    val hoopFit =
        preview?.let {
            HoopValidator.validate(
                it,
                hoopProfile
            )
        }

    val fitsHoop =
        hoopFit?.fits ==
            true

    Column(
        Modifier
            .fillMaxSize()
            .padding(
                horizontal =
                    10.dp
            )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical =
                            4.dp
                    ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            TextButton(
                onClick =
                    onBack
            ) {
                Text(
                    "‹ Voltar",
                    color =
                        FioGold
                )
            }

            Column(
                Modifier.weight(
                    1f
                )
            ) {
                Text(
                    "Criar Nome",
                    color =
                        FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        19.sp
                )

                Text(
                    "Edite por etapas • a prévia atualiza na hora",
                    color =
                        FioTextMuted,
                    fontSize =
                        10.sp
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(.92f)
                    .background(
                        Color(
                            0xFF071017
                        ),
                        RoundedCornerShape(
                            22.dp
                        )
                    )
        ) {
            if (
                preview !=
                    null
            ) {
                EmbroideryCanvas(
                    design =
                        preview,
                    hoop =
                        hoopProfile,
                    modifier =
                        Modifier.fillMaxSize()
                )
            } else {
                Text(
                    result
                        .exceptionOrNull()
                        ?.message
                        ?: "Digite um nome para gerar a prévia.",
                    modifier =
                        Modifier.align(
                            Alignment.Center
                        )
                        .padding(
                            18.dp
                        ),
                    color =
                        Color(
                            0xFFFF9F9A
                        ),
                    fontSize =
                        11.sp
                )
            }
        }

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1.25f)
                    .padding(
                        top = 10.dp,
                        bottom = 8.dp
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
                    .fillMaxSize()
                    .padding(
                        12.dp
                    )
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(
                                rememberScrollState()
                            ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            6.dp
                        )
                ) {
                    listOf(
                        "Texto",
                        "Fonte",
                        "Tamanho",
                        "Cor",
                        "Mais"
                    ).forEach {
                            tab ->
                        val selected =
                            selectedTab ==
                                tab

                        Card(
                            modifier =
                                Modifier.clickable {
                                    selectedTab =
                                        tab
                                },
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        if (
                                            selected
                                        ) {
                                            FioGold
                                        } else {
                                            FioSurfaceAlt
                                        }
                                ),
                            shape =
                                RoundedCornerShape(
                                    14.dp
                                )
                        ) {
                            Text(
                                tab,
                                modifier =
                                    Modifier.padding(
                                        horizontal =
                                            14.dp,
                                        vertical =
                                            9.dp
                                    ),
                                color =
                                    if (
                                        selected
                                    ) {
                                        FioBackground
                                    } else {
                                        FioTextMuted
                                    },
                                fontWeight =
                                    if (
                                        selected
                                    ) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Medium
                                    },
                                fontSize =
                                    11.sp
                            )
                        }
                    }
                }

                Spacer(
                    Modifier.height(
                        10.dp
                    )
                )

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .verticalScroll(
                                rememberScrollState()
                            )
                ) {
                    when (
                        selectedTab
                    ) {
                        "Texto" -> {
                            OutlinedTextField(
                                value =
                                    text,
                                onValueChange = {
                                    text =
                                        it.take(
                                            24
                                        )
                                },
                                modifier =
                                    Modifier.fillMaxWidth(),
                                label = {
                                    Text(
                                        "Digite o nome"
                                    )
                                },
                                singleLine =
                                    true
                            )

                            Spacer(
                                Modifier.height(
                                    10.dp
                                )
                            )

                            Text(
                                "Formato do texto",
                                color =
                                    FioText,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(
                                            rememberScrollState()
                                        )
                                        .padding(
                                            vertical =
                                                6.dp
                                        ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        7.dp
                                    )
                            ) {
                                TextLayoutMode
                                    .entries
                                    .forEach {
                                            option ->
                                        ChoiceButton(
                                            text =
                                                option.displayName,
                                            selected =
                                                layoutMode ==
                                                    option,
                                            onClick = {
                                                layoutMode =
                                                    option
                                            }
                                        )
                                    }
                            }

                            if (
                                layoutMode !=
                                    TextLayoutMode.STRAIGHT
                            ) {
                                Text(
                                    "Curvatura " +
                                        mm(
                                            arcHeightMm
                                        ) +
                                        " mm",
                                    color =
                                        FioTextMuted,
                                    fontSize =
                                        11.sp
                                )

                                Slider(
                                    value =
                                        arcHeightMm,
                                    onValueChange = {
                                        arcHeightMm =
                                            it
                                    },
                                    valueRange =
                                        0f..30f
                                )
                            }
                        }

                        "Fonte" -> {
                            Text(
                                "Fontes FioLab",
                                color =
                                    FioText,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(
                                            rememberScrollState()
                                        )
                                        .padding(
                                            vertical =
                                                6.dp
                                        ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        7.dp
                                    )
                            ) {
                                EmbroideryFontPreset
                                    .entries
                                    .forEach {
                                            option ->
                                        ChoiceButton(
                                            text =
                                                option.displayName,
                                            selected =
                                                importedFontId ==
                                                    null &&
                                                    font ==
                                                        option,
                                            onClick = {
                                                font =
                                                    option
                                                importedFontId =
                                                    null
                                            }
                                        )
                                    }
                            }

                            Spacer(
                                Modifier.height(
                                    8.dp
                                )
                            )

                            Text(
                                "Fontes importadas",
                                color =
                                    FioText,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            if (
                                importedFonts
                                    .isEmpty()
                            ) {
                                Text(
                                    "Nenhuma TTF/OTF importada. Use a aba Fontes na barra inferior para adicionar.",
                                    color =
                                        FioTextMuted,
                                    fontSize =
                                        10.sp
                                )
                            } else {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(
                                                rememberScrollState()
                                            )
                                            .padding(
                                                vertical =
                                                    6.dp
                                            ),
                                    horizontalArrangement =
                                        Arrangement.spacedBy(
                                            7.dp
                                        )
                                ) {
                                    importedFonts
                                        .forEach {
                                                imported ->
                                            ChoiceButton(
                                                text =
                                                    imported.displayName,
                                                selected =
                                                    importedFontId ==
                                                        imported.id,
                                                onClick = {
                                                    importedFontId =
                                                        imported.id
                                                }
                                            )
                                        }
                                }
                            }

                            Text(
                                "A fonte selecionada é convertida em pontos reais de bordado.",
                                color =
                                    FioTextMuted,
                                fontSize =
                                    10.sp
                            )
                        }

                        "Tamanho" -> {
                            Text(
                                "Altura " +
                                    mm(
                                        heightMm
                                    ) +
                                    " mm",
                                color =
                                    FioText,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Slider(
                                value =
                                    heightMm,
                                onValueChange = {
                                    heightMm =
                                        it
                                },
                                valueRange =
                                    4f..40f
                            )

                            Text(
                                "Espaçamento " +
                                    mm(
                                        spacingMm
                                    ) +
                                    " mm",
                                color =
                                    FioTextMuted,
                                fontSize =
                                    11.sp
                            )

                            Slider(
                                value =
                                    spacingMm,
                                onValueChange = {
                                    spacingMm =
                                        it
                                },
                                valueRange =
                                    0f..8f
                            )

                            Text(
                                "Tipo de ponto",
                                color =
                                    FioText,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            vertical =
                                                6.dp
                                        ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        7.dp
                                    )
                            ) {
                                TextStitchStyle
                                    .entries
                                    .forEach {
                                            option ->
                                        ChoiceButton(
                                            modifier =
                                                Modifier.weight(
                                                    1f
                                                ),
                                            text =
                                                option.displayName,
                                            selected =
                                                stitchStyle ==
                                                    option,
                                            onClick = {
                                                stitchStyle =
                                                    option
                                            }
                                        )
                                    }
                            }

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
                                    color =
                                        FioTextMuted,
                                    fontSize =
                                        11.sp
                                )

                                Slider(
                                    value =
                                        stitchLengthMm,
                                    onValueChange = {
                                        stitchLengthMm =
                                            it
                                    },
                                    valueRange =
                                        1f..5f
                                )
                            } else {
                                Text(
                                    "Densidade " +
                                        mm(
                                            satinDensityMm
                                        ) +
                                        " mm",
                                    color =
                                        FioTextMuted,
                                    fontSize =
                                        11.sp
                                )

                                Slider(
                                    value =
                                        satinDensityMm,
                                    onValueChange = {
                                        satinDensityMm =
                                            it
                                    },
                                    valueRange =
                                        .3f..1.2f
                                )
                            }
                        }

                        "Cor" -> {
                            Text(
                                "Cor da linha",
                                color =
                                    FioText,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(
                                            rememberScrollState()
                                        )
                                        .padding(
                                            vertical =
                                                8.dp
                                        ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        9.dp
                                    )
                            ) {
                                namePalette
                                    .forEach {
                                            rawColor ->
                                        val selected =
                                            rawColor ==
                                                color

                                        Box(
                                            Modifier
                                                .size(
                                                    38.dp
                                                )
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
                                "A prévia e a simulação usam a mesma cor selecionada.",
                                color =
                                    FioTextMuted,
                                fontSize =
                                    10.sp
                            )
                        }

                        else -> {
                            Text(
                                "Bastidor",
                                color =
                                    FioText,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(
                                            rememberScrollState()
                                        )
                                        .padding(
                                            vertical =
                                                6.dp
                                        ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        7.dp
                                    )
                            ) {
                                HoopProfile
                                    .entries
                                    .forEach {
                                            option ->
                                        ChoiceButton(
                                            text =
                                                option.displayName,
                                            selected =
                                                hoopProfile ==
                                                    option,
                                            onClick = {
                                                hoopProfile =
                                                    option
                                            }
                                        )
                                    }
                            }

                            Text(
                                "Tecido",
                                color =
                                    FioText,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(
                                            rememberScrollState()
                                        )
                                        .padding(
                                            vertical =
                                                6.dp
                                        ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        7.dp
                                    )
                            ) {
                                FabricProfile
                                    .entries
                                    .forEach {
                                            option ->
                                        ChoiceButton(
                                            text =
                                                option.displayName,
                                            selected =
                                                fabricProfile ==
                                                    option,
                                            onClick = {
                                                fabricProfile =
                                                    option
                                                satinDensityMm =
                                                    option
                                                        .satinDensityMm
                                                satinPullCompensationMm =
                                                    option
                                                        .pullCompensationMm
                                                satinUnderlayMode =
                                                    option
                                                        .underlayMode
                                                satinShortStitches =
                                                    option
                                                        .shortStitches
                                            }
                                        )
                                    }
                            }

                            Text(
                                "Formato",
                                color =
                                    FioText,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            vertical =
                                                6.dp
                                        ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        7.dp
                                    )
                            ) {
                                listOf(
                                    "DST",
                                    "PES",
                                    "JEF"
                                ).forEach {
                                        format ->
                                    ChoiceButton(
                                        modifier =
                                            Modifier.weight(
                                                1f
                                            ),
                                        text =
                                            format,
                                        selected =
                                            outputFormat ==
                                                format,
                                        onClick = {
                                            outputFormat =
                                                format
                                        }
                                    )
                                }
                            }

                            if (
                                stitchStyle ==
                                    TextStitchStyle.SATIN
                            ) {
                                Text(
                                    "Compensação de repuxo " +
                                        mm(
                                            satinPullCompensationMm
                                        ) +
                                        " mm",
                                    color =
                                        FioTextMuted,
                                    fontSize =
                                        11.sp
                                )

                                Slider(
                                    value =
                                        satinPullCompensationMm,
                                    onValueChange = {
                                        satinPullCompensationMm =
                                            it
                                    },
                                    valueRange =
                                        0f..1f
                                )

                                Text(
                                    "Underlay",
                                    color =
                                        FioText,
                                    fontWeight =
                                        FontWeight.SemiBold
                                )

                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(
                                                rememberScrollState()
                                            )
                                            .padding(
                                                vertical =
                                                    6.dp
                                            ),
                                    horizontalArrangement =
                                        Arrangement.spacedBy(
                                            7.dp
                                        )
                                ) {
                                    SatinUnderlayMode
                                        .entries
                                        .forEach {
                                                option ->
                                            ChoiceButton(
                                                text =
                                                    option.displayName,
                                                selected =
                                                    satinUnderlayMode ==
                                                        option,
                                                onClick = {
                                                    satinUnderlayMode =
                                                        option
                                                }
                                            )
                                        }
                                }

                                OutlinedButton(
                                    onClick = {
                                        satinShortStitches =
                                            !satinShortStitches
                                    },
                                    modifier =
                                        Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (
                                            satinShortStitches
                                        ) {
                                            "✓ Short stitches"
                                        } else {
                                            "Short stitches desativados"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    OutlinedButton(
                        onClick = {
                            preview?.let(
                                onSimulate
                            )
                        },
                        enabled =
                            preview !=
                                null &&
                                fitsHoop,
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {
                        Text(
                            "▶ Simular"
                        )
                    }

                    Button(
                        onClick = {
                            preview?.let(
                                onCreate
                            )
                        },
                        enabled =
                            preview !=
                                null &&
                                fitsHoop,
                        modifier =
                            Modifier.weight(
                                1.3f
                            ),
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
                }

                Spacer(
                    Modifier.height(
                        4.dp
                    )
                )

                if (
                    preview !=
                        null
                ) {
                    Text(
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
                            " pontos" +
                            if (
                                fitsHoop
                            ) {
                                " • ✓ cabe no bastidor"
                            } else {
                                " • ⚠ fora da área segura"
                            },
                        modifier =
                            Modifier.fillMaxWidth(),
                        color =
                            if (
                                fitsHoop
                            ) {
                                FioTextMuted
                            } else {
                                Color(
                                    0xFFFF9F9A
                                )
                            },
                        fontSize =
                            10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick =
            onClick,
        modifier =
            modifier,
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
                "● $text"
            } else {
                text
            },
            fontSize =
                11.sp
        )
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
