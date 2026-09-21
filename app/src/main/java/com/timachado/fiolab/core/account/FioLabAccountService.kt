package com.timachado.fiolab.core.account

import android.content.Intent
import com.timachado.fiolab.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object FioLabAccountService {

    private val client:
        SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl =
                BuildConfig
                    .SUPABASE_URL,
            supabaseKey =
                BuildConfig
                    .SUPABASE_PUBLISHABLE_KEY
        ) {
            install(
                Auth
            ) {
                flowType =
                    FlowType.PKCE
                scheme =
                    AUTH_SCHEME
                host =
                    AUTH_HOST
            }

            install(
                Postgrest
            )
        }
    }

    suspend fun currentAccount():
        Result<AccountSnapshot?> =
        runCatching {
            val session =
                client.auth
                    .currentSessionOrNull()
                    ?: return@runCatching null

            val user =
                session.user
                    ?: return@runCatching null

            accountFor(
                userId =
                    user.id,
                email =
                    user.email
                        ?: ""
            )
        }

    suspend fun signInWithGoogle():
        Result<Unit> =
        runCatching {
            client.auth
                .signInWith(
                    Google
                )
        }

    fun handleAuthRedirect(
        intent: Intent,
        onSuccess: () -> Unit,
        onError: (
            Throwable
        ) -> Unit
    ) {
        client.handleDeeplinks(
            intent =
                intent,
            onSessionSuccess = {
                onSuccess()
            },
            onError =
                onError
        )
    }

    suspend fun signIn(
        email: String,
        password: String
    ): Result<AccountSnapshot> =
        runCatching {
            require(
                email.isNotBlank()
            ) {
                "Informe o e-mail."
            }

            require(
                password.length >=
                    6
            ) {
                "A senha precisa ter pelo menos 6 caracteres."
            }

            client.auth
                .signInWith(
                    Email
                ) {
                    this.email =
                        email.trim()

                    this.password =
                        password
                }

            currentAccount()
                .getOrThrow()
                ?: error(
                    "A sessão não foi iniciada."
                )
        }

    suspend fun signUp(
        displayName: String,
        email: String,
        password: String
    ): Result<SignUpOutcome> =
        runCatching {
            val name =
                displayName
                    .trim()

            require(
                name.length in
                    2..80
            ) {
                "Informe um nome de 2 a 80 caracteres."
            }

            require(
                email.isNotBlank() &&
                    '@' in
                    email
            ) {
                "Informe um e-mail válido."
            }

            require(
                password.length >=
                    8
            ) {
                "Use uma senha com pelo menos 8 caracteres."
            }

            client.auth
                .signUpWith(
                    Email
                ) {
                    this.email =
                        email.trim()

                    this.password =
                        password

                    data =
                        buildJsonObject {
                            put(
                                "display_name",
                                name
                            )

                            put(
                                "app_slug",
                                "fiolab"
                            )
                        }
                }

            val account =
                currentAccount()
                    .getOrThrow()

            if (
                account ==
                    null
            ) {
                SignUpOutcome
                    .ConfirmationRequired(
                        email =
                            email.trim()
                    )
            } else {
                SignUpOutcome
                    .SignedIn(
                        account
                    )
            }
        }

    suspend fun updateDisplayName(
        displayName: String
    ): Result<AccountSnapshot> =
        runCatching {
            val name =
                displayName
                    .trim()

            require(
                name.length in
                    2..80
            ) {
                "Informe um nome de 2 a 80 caracteres."
            }

            val session =
                client.auth
                    .currentSessionOrNull()
                    ?: error(
                        "Entre na sua conta primeiro."
                    )

            val user =
                session.user
                    ?: error(
                        "Usuário não encontrado."
                    )

            client.from(
                "fiolab_profiles"
            ).update(
                {
                    set(
                        "display_name",
                        name
                    )
                }
            ) {
                filter {
                    eq(
                        "user_id",
                        user.id
                    )
                }
            }

            accountFor(
                userId =
                    user.id,
                email =
                    user.email
                        ?: ""
            )
        }

    suspend fun signOut():
        Result<Unit> =
        runCatching {
            client.auth
                .signOut()
        }

    private suspend fun accountFor(
        userId: String,
        email: String
    ): AccountSnapshot {
        val profile =
            client.from(
                "fiolab_profiles"
            ).select {
                filter {
                    eq(
                        "user_id",
                        userId
                    )
                }
            }
                .decodeList<
                    FioLabProfileRow
                >()
                .firstOrNull()
                ?: run {
                    val fallbackName =
                        email
                            .substringBefore(
                                '@'
                            )
                            .take(
                                80
                            )
                            .ifBlank {
                                "Usuário FioLab"
                            }

                    client.from(
                        "fiolab_profiles"
                    ).insert(
                        FioLabProfileUpsert(
                            userId =
                                userId,
                            displayName =
                                fallbackName
                        )
                    )

                    client.from(
                        "fiolab_profiles"
                    ).select {
                        filter {
                            eq(
                                "user_id",
                                userId
                            )
                        }
                    }
                        .decodeList<
                            FioLabProfileRow
                        >()
                        .firstOrNull()
                        ?: error(
                            "Não foi possível criar o perfil."
                        )
                }

        val subscription =
            client.from(
                "fiolab_subscriptions"
            ).select {
                filter {
                    eq(
                        "user_id",
                        userId
                    )
                }
            }
                .decodeList<
                    FioLabSubscriptionRow
                >()
                .firstOrNull()

        val plans =
            runCatching {
                client.from(
                    "fiolab_plans"
                ).select()
                    .decodeList<
                        FioLabPlanRow
                    >()
                    .sortedBy {
                        it.displayOrder
                    }
                    .map {
                        AccountPlanOption(
                            code =
                                it.code,
                            name =
                                it.name,
                            billingType =
                                it.billingType,
                            isPaid =
                                it.isPaid,
                            isLifetime =
                                it.isLifetime,
                            isPromotional =
                                it.isPromotional,
                            description =
                                it.description,
                            priceCents =
                                it.priceCents,
                            currency =
                                it.currency,
                            active =
                                it.active,
                            availableFrom =
                                it.availableFrom,
                            availableUntil =
                                it.availableUntil,
                            displayOrder =
                                it.displayOrder
                        )
                    }
            }.getOrElse {
                emptyList()
            }

        val history =
            runCatching {
                client.from(
                    "fiolab_subscription_history"
                ).select {
                    filter {
                        eq(
                            "user_id",
                            userId
                        )
                    }
                }
                    .decodeList<
                        FioLabSubscriptionHistoryRow
                    >()
                    .sortedByDescending {
                        it.occurredAt
                    }
                    .take(
                        20
                    )
                    .map {
                        AccountSubscriptionEvent(
                            planCode =
                                it.planCode,
                            eventType =
                                it.eventType,
                            status =
                                it.status,
                            amountCents =
                                it.amountCents,
                            currency =
                                it.currency,
                            provider =
                                it.provider,
                            occurredAt =
                                it.occurredAt
                        )
                    }
            }.getOrElse {
                emptyList()
            }

        return AccountSnapshot(
            userId =
                userId,
            email =
                email,
            displayName =
                profile.displayName,
            avatarUrl =
                profile.avatarUrl,
            planCode =
                subscription
                    ?.planCode
                    ?: "free",
            subscriptionStatus =
                subscription
                    ?.status
                    ?: "active",
            currentPeriodEnd =
                subscription
                    ?.currentPeriodEnd,
            purchasedAt =
                subscription
                    ?.purchasedAt,
            purchasePriceCents =
                subscription
                    ?.purchasePriceCents,
            currency =
                subscription
                    ?.currency
                    ?: "BRL",
            provider =
                subscription
                    ?.provider,
            availablePlans =
                plans,
            subscriptionHistory =
                history
        )
    }

    const val AUTH_SCHEME =
        "com.timachado.fiolab"

    const val AUTH_HOST =
        "login-callback"

    const val AUTH_REDIRECT_URL =
        "$AUTH_SCHEME://$AUTH_HOST"
}
