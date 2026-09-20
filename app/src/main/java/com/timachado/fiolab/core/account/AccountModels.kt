package com.timachado.fiolab.core.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FioLabProfileRow(
    @SerialName("user_id")
    val userId: String,
    @SerialName("display_name")
    val displayName: String,
    @SerialName("avatar_url")
    val avatarUrl: String? = null
)

@Serializable
data class FioLabProfileUpsert(
    @SerialName("user_id")
    val userId: String,
    @SerialName("display_name")
    val displayName: String
)

@Serializable
data class FioLabSubscriptionRow(
    @SerialName("user_id")
    val userId: String,
    @SerialName("plan_code")
    val planCode: String,
    val status: String,
    val source: String,
    @SerialName("current_period_end")
    val currentPeriodEnd: String? = null
)

data class AccountSnapshot(
    val userId: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val planCode: String,
    val subscriptionStatus: String,
    val currentPeriodEnd: String?
) {
    val isPaid: Boolean
        get() =
            planCode.lowercase() !=
                "free"

    val isSubscriptionUsable: Boolean
        get() =
            subscriptionStatus in
                setOf(
                    "active",
                    "trialing"
                )
}

sealed interface SignUpOutcome {
    data class SignedIn(
        val account: AccountSnapshot
    ) : SignUpOutcome

    data class ConfirmationRequired(
        val email: String
    ) : SignUpOutcome
}

object AccountPresentation {
    fun planLabel(
        planCode: String
    ): String =
        when (
            planCode
                .trim()
                .lowercase()
        ) {
            "free" ->
                "Gratuito"

            "pro" ->
                "FioLab Pro"

            "premium" ->
                "FioLab Premium"

            else ->
                planCode
                    .trim()
                    .replaceFirstChar {
                        if (
                            it.isLowerCase()
                        ) {
                            it.titlecase()
                        } else {
                            it.toString()
                        }
                    }
        }

    fun statusLabel(
        status: String
    ): String =
        when (
            status
                .trim()
                .lowercase()
        ) {
            "active" ->
                "Ativa"

            "trialing" ->
                "Período de teste"

            "past_due" ->
                "Pagamento pendente"

            "canceled" ->
                "Cancelada"

            "expired" ->
                "Expirada"

            else ->
                status
                    .trim()
                    .replaceFirstChar {
                        if (
                            it.isLowerCase()
                        ) {
                            it.titlecase()
                        } else {
                            it.toString()
                        }
                    }
        }
}
