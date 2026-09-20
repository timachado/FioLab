package com.timachado.fiolab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.timachado.fiolab.core.project.SavedProjectSummary
import com.timachado.fiolab.ui.theme.FioBackground
import com.timachado.fiolab.ui.theme.FioGold
import com.timachado.fiolab.ui.theme.FioSurface
import com.timachado.fiolab.ui.theme.FioText
import com.timachado.fiolab.ui.theme.FioTextMuted
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectLibraryScreen(
    projects: List<SavedProjectSummary>,
    onBack: () -> Unit,
    onOpen: (SavedProjectSummary) -> Unit,
    onTransfer: (SavedProjectSummary) -> Unit,
    onDelete: (SavedProjectSummary) -> Unit
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
                    "Minhas Matrizes",
                    color = FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        20.sp
                )

                Text(
                    projects.size
                        .toString() +
                        " projeto(s) salvo(s) no celular",
                    color =
                        FioTextMuted,
                    fontSize =
                        10.sp
                )
            }
        }

        if (
            projects.isEmpty()
        ) {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            top =
                                24.dp
                        ),
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
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            24.dp
                        ),
                    horizontalAlignment =
                        Alignment
                            .CenterHorizontally
                ) {
                    Text(
                        "Nenhuma matriz salva ainda.",
                        color =
                            FioText,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )

                    Text(
                        "Abra ou crie uma matriz e toque em “Salvar em Minhas Matrizes”.",
                        color =
                            FioTextMuted,
                        fontSize =
                            11.sp
                    )
                }
            }

            return
        }

        LazyColumn(
            modifier =
                Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top =
                        12.dp,
                    bottom =
                        30.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {
            items(
                projects,
                key = {
                    it.id
                }
            ) {
                    project ->
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
                            16.dp
                        )
                    ) {
                        Text(
                            project.title,
                            color =
                                FioText,
                            fontWeight =
                                FontWeight.Bold,
                            fontSize =
                                17.sp
                        )

                        Text(
                            project.format +
                                " • " +
                                oneDecimalProject(
                                    project.widthMm
                                ) +
                                " × " +
                                oneDecimalProject(
                                    project.heightMm
                                ) +
                                " mm",
                            color =
                                FioTextMuted,
                            fontSize =
                                11.sp
                        )

                        Text(
                            project.stitchCount
                                .toString() +
                                " pontos • " +
                                project.colorCount +
                                " bloco(s) • " +
                                formattedDate(
                                    project
                                        .updatedAtMillis
                                ),
                            color =
                                FioTextMuted,
                            fontSize =
                                10.sp
                        )

                        Spacer(
                            Modifier.height(
                                12.dp
                            )
                        )

                        Row(
                            horizontalArrangement =
                                Arrangement
                                    .spacedBy(
                                        8.dp
                                    )
                        ) {
                            Button(
                                onClick = {
                                    onOpen(
                                        project
                                    )
                                },
                                modifier =
                                    Modifier
                                        .weight(
                                            1f
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
                                    "Abrir",
                                    fontWeight =
                                        FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    onTransfer(
                                        project
                                    )
                                },
                                modifier =
                                    Modifier
                                        .weight(
                                            1f
                                        )
                            ) {
                                Text(
                                    "⇧ Máquina"
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                onDelete(
                                    project
                                )
                            },
                            modifier =
                                Modifier
                                    .align(
                                        Alignment.End
                                    )
                        ) {
                            Text(
                                "Excluir",
                                color =
                                    Color(
                                        0xFFFF9F9A
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formattedDate(
    millis: Long
): String =
    DateFormat
        .getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT,
            Locale.getDefault()
        )
        .format(
            Date(
                millis
            )
        )

private fun oneDecimalProject(
    value: Float
): String =
    String.format(
        Locale.forLanguageTag(
            "pt-BR"
        ),
        "%.1f",
        value
    )
