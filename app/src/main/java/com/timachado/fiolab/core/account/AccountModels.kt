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
data class FioLabPlanRow(
    val code: String,
    val name: String,
    @SerialName("billing_type")
    val billingType: String,
    @SerialName("is_paid")
    val isPaid: Boolean,
    @SerialName("is_lifetime")
    val isLifetime: Boolean,
    @SerialName("is_promotional")
    val isPromotional: Boolean,
    val description: String,
    @SerialName("price_cents")
    val priceCents: Int? = null,
    val currency: String = "BRL",
    val active: Boolean = true,
    @SerialName("available_from")
    val availableFrom: String? = null,
    @SerialName("available_until")
    val availableUntil: String? = null,
    @SerialName("display_order")
    val displayOrder: Int = 0
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
    val currentPeriodEnd: String? = null,
    @SerialName("purchased_at")
    val purchasedAt: String? = null,
    @SerialName("purchase_price_cents")
    val purchasePriceCents: Int? = null,
    val currency: String = "BRL",
    val provider: String? = null,
    @SerialName("external_reference")
    val externalReference: String? = null
)

@Serializable
data class FioLabSubscriptionHistoryRow(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("plan_code")
    val planCode: String,
    @SerialName("event_type")
    val eventType: String,
    val status: String,
    @SerialName("amount_cents")
    val amountCents: Int? = null,
    val currency: String = "BRL",
    val provider: String? = null,
    @SerialName("external_reference")
    val externalReference: String? = null,
    @SerialName("occurred_at")
    val occurredAt: String
)

data class AccountPlanOption(
    val code: String,
    val name: String,
    val billingType: String,
    val isPaid: Boolean,
    val isLifetime: Boolean,
    val isPromotional: Boolean,
    val description: String,
    val priceCents: Int?,
    val currency: String,
    val active: Boolean,
    val availableFrom: String?,
    val availableUntil: String?,
    val displayOrder: Int
)

data class AccountSubscriptionEvent(
    val planCode: String,
    val eventType: String,
    val status: String,
    val amountCents: Int?,
    val currency: String,
    val provider: String?,
    val occurredAt: String
)

data class AccountSnapshot(
    val userId: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val planCode: String,
    val subscriptionStatus: String,
    val currentPeriodEnd: String?,
    val purchasedAt: String?,
    val purchasePriceCents: Int?,
    val currency: String,
    val provider: String?,
    val availablePlans: List<AccountPlanOption>,
    val subscriptionHistory: List<AccountSubscriptionEvent>
) {
    val currentPlan: AccountPlanOption?
        get() =
            availablePlans
                .firstOrNull {
                    it.code ==
                        planCode
                }

    val isPaid: Boolean
        get() =
            currentPlan
                ?.isPaid
                ?: (
                    planCode
                        .lowercase() !=
                        "free"
                    )

    val isLifetime: Boolean
        get() =
            currentPlan
                ?.isLifetime
                ?: planCode.lowercase() in
                    setOf(
                        "lifetime",
                        "lifetime_launch"
                    )

    val isLaunchLifetime: Boolean
        get() =
            currentPlan
                ?.isPromotional ==
                true ||
                planCode
                    .equals(
                        "lifetime_launch",
                        ignoreCase =
                            true
                    )

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

            "pro",
            "premium",
            "pro_monthly" ->
                "FioLab Pro Mensal"

            "pro_annual" ->
                "FioLab Pro Anual"

            "lifetime" ->
                "FioLab Vitalício"

            "lifetime_launch" ->
                "FioLab Vitalício • Lançamento"

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

    fun billingLabel(
        billingType: String
    ): String =
        when (
            billingType
                .trim()
                .lowercase()
        ) {
            "free" ->
                "Gratuito"

            "monthly" ->
                "Mensal"

            "annual" ->
                "Anual"

            "lifetime" ->
                "Acesso permanente"

            else ->
                billingType
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

    fun eventLabel(
        eventType: String
    ): String =
        when (
            eventType
                .trim()
                .lowercase()
        ) {
            "created" ->
                "Cadastro"

            "activated" ->
                "Ativação"

            "renewed" ->
                "Renovação"

            "upgraded" ->
                "Upgrade"

            "downgraded" ->
                "Alteração de plano"

            "canceled" ->
                "Cancelamento"

            "expired" ->
                "Expiração"

            "payment_failed" ->
                "Falha no pagamento"

            "refunded" ->
                "Reembolso"

            "lifetime_purchase" ->
                "Compra vitalícia"

            else ->
                eventType
        }
}
