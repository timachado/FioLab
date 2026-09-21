package com.timachado.fiolab

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.fiolab.core.account.AccountPlanOption
import com.timachado.fiolab.core.account.AccountPresentation
import com.timachado.fiolab.core.account.AccountSnapshot
import com.timachado.fiolab.core.account.AccountSubscriptionEvent
import com.timachado.fiolab.ui.theme.FioBackground
import com.timachado.fiolab.ui.theme.FioGold
import com.timachado.fiolab.ui.theme.FioSurface
import com.timachado.fiolab.ui.theme.FioText
import com.timachado.fiolab.ui.theme.FioTextMuted
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private enum class AccountMode(
    val label: String
) {
    SIGN_IN("Entrar"),
    SIGN_UP("Criar conta")
}

@Composable
fun AccountScreen(
    account: AccountSnapshot?,
    onBack: () -> Unit,
    onSignIn: (
        email: String,
        password: String
    ) -> Unit,
    onSignUp: (
        displayName: String,
        email: String,
        password: String
    ) -> Unit,
    onSaveName: (
        displayName: String
    ) -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(
                horizontal =
                    16.dp
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
                    "Minha Conta",
                    color =
                        FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        20.sp
                )

                Text(
                    if (
                        account ==
                            null
                    ) {
                        "Entre para acompanhar sua conta."
                    } else {
                        "Perfil e assinatura FioLab."
                    },
                    color =
                        FioTextMuted,
                    fontSize =
                        10.sp
                )
            }
        }

        if (
            account ==
                null
        ) {
            SignedOutAccount(
                onSignIn =
                    onSignIn,
                onSignUp =
                    onSignUp
            )
        } else {
            SignedInAccount(
                account =
                    account,
                onSaveName =
                    onSaveName,
                onSignOut =
                    onSignOut
            )
        }
    }
}

@Composable
private fun SignedOutAccount(
    onSignIn: (
        email: String,
        password: String
    ) -> Unit,
    onSignUp: (
        displayName: String,
        email: String,
        password: String
    ) -> Unit
) {
    var mode by remember {
        mutableStateOf(
            AccountMode.SIGN_IN
        )
    }

    var displayName by remember {
        mutableStateOf("")
    }

    var email by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(
                top =
                    18.dp,
                bottom =
                    30.dp
            )
    ) {
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
                    24.dp
                )
        ) {
            Column(
                Modifier.padding(
                    18.dp
                )
            ) {
                Row(
                    Modifier
                        .fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    AccountMode
                        .entries
                        .forEach {
                                option ->
                            val selected =
                                mode ==
                                    option

                            OutlinedButton(
                                onClick = {
                                    mode =
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
                                        "● " +
                                            option.label
                                    } else {
                                        option.label
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

                Spacer(
                    Modifier.height(
                        14.dp
                    )
                )

                if (
                    mode ==
                        AccountMode
                            .SIGN_UP
                ) {
                    OutlinedTextField(
                        value =
                            displayName,
                        onValueChange = {
                            displayName =
                                it.take(
                                    80
                                )
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                        label = {
                            Text(
                                "Seu nome"
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
                }

                OutlinedTextField(
                    value =
                        email,
                    onValueChange = {
                        email = it
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    label = {
                        Text(
                            "E-mail"
                        )
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType
                                    .Email
                        ),
                    singleLine =
                        true
                )

                Spacer(
                    Modifier.height(
                        10.dp
                    )
                )

                OutlinedTextField(
                    value =
                        password,
                    onValueChange = {
                        password = it
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    label = {
                        Text(
                            "Senha"
                        )
                    },
                    visualTransformation =
                        PasswordVisualTransformation(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType
                                    .Password
                        ),
                    singleLine =
                        true
                )

                if (
                    mode ==
                        AccountMode
                            .SIGN_UP
                ) {
                    Text(
                        "Use pelo menos 8 caracteres. Dependendo da configuração de segurança, você receberá um e-mail para confirmar a conta.",
                        modifier =
                            Modifier.padding(
                                top =
                                    8.dp
                            ),
                        color =
                            FioTextMuted,
                        fontSize =
                            10.sp
                    )
                }

                Spacer(
                    Modifier.height(
                        16.dp
                    )
                )

                Button(
                    onClick = {
                        if (
                            mode ==
                                AccountMode
                                    .SIGN_IN
                        ) {
                            onSignIn(
                                email,
                                password
                            )
                        } else {
                            onSignUp(
                                displayName,
                                email,
                                password
                            )
                        }
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
                        if (
                            mode ==
                                AccountMode
                                    .SIGN_IN
                        ) {
                            "Entrar"
                        } else {
                            "Criar minha conta"
                        },
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

        Text(
            "Suas matrizes continuam salvas localmente no celular. Entrar na conta não envia automaticamente seus projetos para a nuvem.",
            color =
                FioTextMuted,
            fontSize =
                10.sp
        )
    }
}

@Composable
private fun SignedInAccount(
    account: AccountSnapshot,
    onSaveName: (
        displayName: String
    ) -> Unit,
    onSignOut: () -> Unit
) {
    var displayName by remember(
        account.userId,
        account.displayName
    ) {
        mutableStateOf(
            account.displayName
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(
                top =
                    18.dp,
                bottom =
                    30.dp
            )
    ) {
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
                    24.dp
                )
        ) {
            Column(
                Modifier.padding(
                    18.dp
                ),
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(
                            72.dp
                        )
                        .background(
                            FioGold,
                            CircleShape
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {
                    Text(
                        account.displayName
                            .trim()
                            .firstOrNull()
                            ?.uppercase()
                            ?: "F",
                        color =
                            FioBackground,
                        fontWeight =
                            FontWeight.Black,
                        fontSize =
                            30.sp
                    )
                }

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                Text(
                    account.displayName,
                    color =
                        FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        20.sp
                )

                Text(
                    account.email,
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

                OutlinedTextField(
                    value =
                        displayName,
                    onValueChange = {
                        displayName =
                            it.take(
                                80
                            )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    label = {
                        Text(
                            "Nome no FioLab"
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

                Button(
                    onClick = {
                        onSaveName(
                            displayName
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
                        "Salvar nome",
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
                    24.dp
                )
        ) {
            Column(
                Modifier.padding(
                    18.dp
                )
            ) {
                Text(
                    "Minha assinatura",
                    color =
                        FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        18.sp
                )

                Spacer(
                    Modifier.height(
                        10.dp
                    )
                )

                val currentPlan =
                    account.currentPlan

                AccountLine(
                    label =
                        "Plano",
                    value =
                        currentPlan
                            ?.name
                            ?: AccountPresentation
                                .planLabel(
                                    account
                                        .planCode
                                )
                )

                AccountLine(
                    label =
                        "Tipo",
                    value =
                        currentPlan
                            ?.let {
                                AccountPresentation
                                    .billingLabel(
                                        it.billingType
                                    )
                            }
                            ?: if (
                                account.isLifetime
                            ) {
                                "Acesso permanente"
                            } else if (
                                account.isPaid
                            ) {
                                "Assinatura"
                            } else {
                                "Gratuito"
                            }
                )

                AccountLine(
                    label =
                        "Status",
                    value =
                        AccountPresentation
                            .statusLabel(
                                account
                                    .subscriptionStatus
                            )
                )

                when {
                    account.isLifetime -> {
                        AccountLine(
                            label =
                                "Acesso",
                            value =
                                "Permanente"
                        )

                        if (
                            !account.purchasedAt
                                .isNullOrBlank()
                        ) {
                            AccountLine(
                                label =
                                    "Comprado em",
                                value =
                                    formattedDate(
                                        account
                                            .purchasedAt
                                    )
                            )
                        }
                    }

                    account.isPaid -> {
                        AccountLine(
                            label =
                                "Próxima renovação",
                            value =
                                formattedDate(
                                    account
                                        .currentPeriodEnd
                                )
                        )
                    }

                    else -> {
                        AccountLine(
                            label =
                                "Renovação",
                            value =
                                "Não se aplica"
                        )
                    }
                }

                if (
                    account.purchasePriceCents !=
                        null
                ) {
                    AccountLine(
                        label =
                            "Valor pago",
                        value =
                            formattedMoney(
                                account
                                    .purchasePriceCents,
                                account.currency
                            )
                    )
                }

                account.provider
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        AccountLine(
                            label =
                                "Pagamento",
                            value =
                                it
                                    .replaceFirstChar {
                                            first ->
                                        first
                                            .uppercase()
                                    }
                        )
                    }

                if (
                    account.isLaunchLifetime
                ) {
                    Spacer(
                        Modifier.height(
                            10.dp
                        )
                    )

                    Text(
                        "★ Membro de Lançamento • benefício vitalício preservado",
                        color =
                            FioGold,
                        fontWeight =
                            FontWeight.SemiBold,
                        fontSize =
                            11.sp
                    )
                }

                Spacer(
                    Modifier.height(
                        10.dp
                    )
                )

                Text(
                    when {
                        account.isLifetime ->
                            "Este plano não possui renovação nem próxima cobrança."

                        account.isPaid ->
                            "O FioLab acompanha aqui o plano, status e renovação vinculados à sua conta."

                        else ->
                            "Quando uma contratação for confirmada, o plano será vinculado à sua conta automaticamente."
                    },
                    color =
                        FioTextMuted,
                    fontSize =
                        10.sp
                )
            }
        }

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        if (
            account.availablePlans
                .isNotEmpty()
        ) {
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
                        24.dp
                    )
            ) {
                Column(
                    Modifier.padding(
                        18.dp
                    )
                ) {
                    Text(
                        "Planos FioLab",
                        color =
                            FioText,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize =
                            18.sp
                    )

                    Text(
                        "O app consulta o plano vinculado à sua conta; alterações de assinatura não são liberadas pelo próprio APK.",
                        modifier =
                            Modifier.padding(
                                top =
                                    4.dp,
                                bottom =
                                    10.dp
                            ),
                        color =
                            FioTextMuted,
                        fontSize =
                            10.sp
                    )

                    account.availablePlans
                        .filter {
                            it.active ||
                                it.code ==
                                    account.planCode
                        }
                        .sortedBy {
                            it.displayOrder
                        }
                        .forEachIndexed {
                                index,
                                plan ->
                            PlanSummary(
                                plan =
                                    plan,
                                current =
                                    plan.code ==
                                        account.planCode
                            )

                            if (
                                index <
                                    account
                                        .availablePlans
                                        .filter {
                                            it.active ||
                                                it.code ==
                                                    account.planCode
                                        }
                                        .lastIndex
                            ) {
                                Spacer(
                                    Modifier.height(
                                        10.dp
                                    )
                                )
                            }
                        }
                }
            }

            Spacer(
                Modifier.height(
                    14.dp
                )
            )
        }

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
                    24.dp
                )
        ) {
            Column(
                Modifier.padding(
                    18.dp
                )
            ) {
                Text(
                    "Histórico da assinatura",
                    color =
                        FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        18.sp
                )

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )

                if (
                    account.subscriptionHistory
                        .isEmpty()
                ) {
                    Text(
                        "Seu histórico aparecerá aqui após a primeira contratação, renovação ou compra vitalícia.",
                        color =
                            FioTextMuted,
                        fontSize =
                            10.sp
                    )
                } else {
                    account.subscriptionHistory
                        .take(
                            6
                        )
                        .forEachIndexed {
                                index,
                                event ->
                            SubscriptionHistoryLine(
                                event =
                                    event
                            )

                            if (
                                index <
                                    account
                                        .subscriptionHistory
                                        .take(
                                            6
                                        )
                                        .lastIndex
                            ) {
                                Spacer(
                                    Modifier.height(
                                        8.dp
                                    )
                                )
                            }
                        }
                }
            }
        }

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        OutlinedButton(
            onClick =
                onSignOut,
            modifier =
                Modifier
                    .fillMaxWidth()
        ) {
            Text(
                "Sair da conta"
            )
        }
    }
}

@Composable
private fun PlanSummary(
    plan: AccountPlanOption,
    current: Boolean
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                if (
                    current
                ) {
                    FioGold.copy(
                        alpha =
                            .10f
                    )
                } else {
                    FioBackground.copy(
                        alpha =
                            .34f
                    )
                },
                RoundedCornerShape(
                    16.dp
                )
            )
            .padding(
                12.dp
            )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Text(
                plan.name,
                modifier =
                    Modifier.weight(
                        1f
                    ),
                color =
                    if (
                        current
                    ) {
                        FioGold
                    } else {
                        FioText
                    },
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    13.sp
            )

            if (
                current
            ) {
                Text(
                    "● Seu plano",
                    color =
                        FioGold,
                    fontWeight =
                        FontWeight.SemiBold,
                    fontSize =
                        9.sp
                )
            } else if (
                plan.isPromotional
            ) {
                Text(
                    "Lançamento",
                    color =
                        FioGold,
                    fontWeight =
                        FontWeight.SemiBold,
                    fontSize =
                        9.sp
                )
            }
        }

        Text(
            AccountPresentation
                .billingLabel(
                    plan.billingType
                ) +
                " • " +
                when {
                    !plan.isPaid ->
                        "Grátis"

                    plan.priceCents !=
                        null ->
                        formattedMoney(
                            plan.priceCents,
                            plan.currency
                        )

                    else ->
                        "Valor no checkout"
                },
            modifier =
                Modifier.padding(
                    top =
                        3.dp
                ),
            color =
                FioTextMuted,
            fontSize =
                10.sp
        )

        if (
            plan.description
                .isNotBlank()
        ) {
            Text(
                plan.description,
                modifier =
                    Modifier.padding(
                        top =
                            4.dp
                    ),
                color =
                    FioTextMuted,
                fontSize =
                    10.sp
            )
        }
    }
}

@Composable
private fun SubscriptionHistoryLine(
    event:
        AccountSubscriptionEvent
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Column(
            Modifier.weight(
                1f
            )
        ) {
            Text(
                AccountPresentation
                    .eventLabel(
                        event.eventType
                    ),
                color =
                    FioText,
                fontWeight =
                    FontWeight.SemiBold,
                fontSize =
                    12.sp
            )

            Text(
                AccountPresentation
                    .planLabel(
                        event.planCode
                    ) +
                    " • " +
                    formattedDate(
                        event.occurredAt
                    ),
                color =
                    FioTextMuted,
                fontSize =
                    9.sp
            )
        }

        event.amountCents
            ?.let {
                Text(
                    formattedMoney(
                        it,
                        event.currency
                    ),
                    color =
                        FioText,
                    fontWeight =
                        FontWeight.SemiBold,
                    fontSize =
                        10.sp
                )
            }
    }
}

@Composable
private fun AccountLine(
    label: String,
    value: String
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                vertical =
                    4.dp
            )
    ) {
        Text(
            label,
            modifier =
                Modifier.weight(
                    1f
                ),
            color =
                FioTextMuted,
            fontSize =
                12.sp
        )

        Text(
            value,
            color =
                FioText,
            fontWeight =
                FontWeight.SemiBold,
            fontSize =
                12.sp
        )
    }
}

private fun formattedDate(
    value: String?
): String {
    if (
        value.isNullOrBlank()
    ) {
        return "—"
    }

    return runCatching {
        DateTimeFormatter
            .ofPattern(
                "dd/MM/yyyy"
            )
            .withZone(
                ZoneId
                    .systemDefault()
            )
            .format(
                Instant.parse(
                    value
                )
            )
    }.getOrDefault(
        value
            .take(
                10
            )
    )
}

private fun formattedMoney(
    cents: Int,
    currencyCode: String
): String =
    runCatching {
        NumberFormat
            .getCurrencyInstance(
                Locale.forLanguageTag(
                    "pt-BR"
                )
            )
            .apply {
                currency =
                    Currency.getInstance(
                        currencyCode
                    )
            }
            .format(
                cents /
                    100.0
            )
    }.getOrDefault(
        (
            cents /
                100.0
            ).toString() +
            " " +
            currencyCode
    )

