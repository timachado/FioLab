package com.timachado.fiolab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.timachado.fiolab.core.account.AccountSnapshot
import com.timachado.fiolab.core.account.FioLabAccountService
import com.timachado.fiolab.core.account.SignUpOutcome
import com.timachado.fiolab.ui.theme.FioGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AccountHostScreen(
    onBack: () -> Unit
) {
    val scope =
        rememberCoroutineScope()

    val snackbar =
        remember {
            SnackbarHostState()
        }

    var account by remember {
        mutableStateOf<
            AccountSnapshot?
        >(null)
    }

    var loading by remember {
        mutableStateOf(true)
    }

    LaunchedEffect(
        Unit
    ) {
        val result =
            withContext(
                Dispatchers.IO
            ) {
                FioLabAccountService
                    .currentAccount()
            }

        loading =
            false

        result.fold(
            onSuccess = {
                    current ->
                account =
                    current
            },
            onFailure = {
                snackbar
                    .showSnackbar(
                        "Não foi possível atualizar sua conta."
                    )
            }
        )
    }

    fun refreshAccount() {
        scope.launch {
            loading =
                true

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    FioLabAccountService
                        .currentAccount()
                }

            loading =
                false

            result.fold(
                onSuccess = {
                        current ->
                    account =
                        current

                    snackbar
                        .showSnackbar(
                            "Assinatura atualizada."
                        )
                },
                onFailure = {
                    snackbar
                        .showSnackbar(
                            "Não foi possível atualizar sua assinatura."
                        )
                }
            )
        }
    }

    fun signIn(
        email: String,
        password: String
    ) {
        scope.launch {
            loading =
                true

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    FioLabAccountService
                        .signIn(
                            email,
                            password
                        )
                }

            loading =
                false

            result.fold(
                onSuccess = {
                        signedIn ->
                    account =
                        signedIn

                    snackbar
                        .showSnackbar(
                            "Conta conectada."
                        )
                },
                onFailure = {
                        error ->
                    snackbar
                        .showSnackbar(
                            error.message
                                ?: "Não foi possível entrar."
                        )
                }
            )
        }
    }

    fun signUp(
        displayName: String,
        email: String,
        password: String
    ) {
        scope.launch {
            loading =
                true

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    FioLabAccountService
                        .signUp(
                            displayName,
                            email,
                            password
                        )
                }

            loading =
                false

            result.fold(
                onSuccess = {
                        outcome ->
                    when (
                        outcome
                    ) {
                        is SignUpOutcome
                            .SignedIn -> {
                            account =
                                outcome.account

                            snackbar
                                .showSnackbar(
                                    "Conta criada e conectada."
                                )
                        }

                        is SignUpOutcome
                            .ConfirmationRequired -> {
                            account =
                                null

                            snackbar
                                .showSnackbar(
                                    "Conta criada. Confira o e-mail " +
                                        outcome.email +
                                        " antes de entrar."
                                )
                        }
                    }
                },
                onFailure = {
                        error ->
                    snackbar
                        .showSnackbar(
                            error.message
                                ?: "Não foi possível criar a conta."
                        )
                }
            )
        }
    }

    fun saveName(
        displayName: String
    ) {
        scope.launch {
            loading =
                true

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    FioLabAccountService
                        .updateDisplayName(
                            displayName
                        )
                }

            loading =
                false

            result.fold(
                onSuccess = {
                        updated ->
                    account =
                        updated

                    snackbar
                        .showSnackbar(
                            "Nome atualizado."
                        )
                },
                onFailure = {
                        error ->
                    snackbar
                        .showSnackbar(
                            error.message
                                ?: "Não foi possível atualizar o nome."
                        )
                }
            )
        }
    }

    fun signOut() {
        scope.launch {
            loading =
                true

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    FioLabAccountService
                        .signOut()
                }

            loading =
                false

            result.fold(
                onSuccess = {
                    account =
                        null

                    snackbar
                        .showSnackbar(
                            "Você saiu da conta."
                        )
                },
                onFailure = {
                    snackbar
                        .showSnackbar(
                            "Não foi possível sair da conta."
                        )
                }
            )
        }
    }

    Box(
        Modifier.fillMaxSize()
    ) {
        AccountScreen(
            account =
                account,
            onBack =
                onBack,
            onSignIn = {
                    email,
                    password ->
                signIn(
                    email,
                    password
                )
            },
            onSignUp = {
                    name,
                    email,
                    password ->
                signUp(
                    name,
                    email,
                    password
                )
            },
            onSaveName = {
                    name ->
                saveName(
                    name
                )
            },
            onRefresh = {
                refreshAccount()
            },
            onSignOut = {
                signOut()
            }
        )

        SnackbarHost(
            hostState =
                snackbar,
            modifier =
                Modifier
                    .align(
                        Alignment
                            .BottomCenter
                    )
        )

        if (
            loading
        ) {
            CircularProgressIndicator(
                modifier =
                    Modifier
                        .align(
                            Alignment.Center
                        ),
                color =
                    FioGold
            )
        }
    }
}
