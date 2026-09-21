package com.timachado.fiolab.core.account

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountErrorMessageTest {
    @Test
    fun mapsInvalidCredentialsWithoutLeakingBackendText() {
        assertEquals(
            "E-mail ou senha incorretos.",
            AccountErrorMessage
                .forUser(
                    IllegalStateException(
                        "AuthApiException: Invalid login credentials"
                    ),
                    "Falha."
                )
        )
    }

    @Test
    fun mapsExpiredSession() {
        assertEquals(
            "Sua sessão expirou. Entre novamente para continuar.",
            AccountErrorMessage
                .forUser(
                    IllegalStateException(
                        "JWT expired"
                    ),
                    "Falha."
                )
        )
    }

    @Test
    fun mapsNetworkFailure() {
        assertEquals(
            "Sem conexão com a internet. Confira sua rede e tente novamente.",
            AccountErrorMessage
                .forUser(
                    IllegalStateException(
                        "Unable to resolve host"
                    ),
                    "Falha."
                )
        )
    }

    @Test
    fun unknownErrorUsesSafeFallback() {
        assertEquals(
            "Não foi possível atualizar sua conta.",
            AccountErrorMessage
                .forUser(
                    IllegalStateException(
                        "internal backend detail"
                    ),
                    "Não foi possível atualizar sua conta."
                )
        )
    }
}
