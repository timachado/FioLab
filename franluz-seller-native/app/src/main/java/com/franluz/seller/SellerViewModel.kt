package com.franluz.seller

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.franluz.seller.data.ApiClient
import com.franluz.seller.data.ApiException
import com.franluz.seller.data.Dashboard
import com.franluz.seller.data.Order
import com.franluz.seller.data.Product
import com.franluz.seller.data.SellerUser
import com.franluz.seller.data.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SellerScreen { HOME, ORDERS, PRODUCTS, STOCK, ACCOUNT }

data class SellerUiState(
    val authenticated: Boolean = false,
    val loading: Boolean = true,
    val screen: SellerScreen = SellerScreen.HOME,
    val user: SellerUser? = null,
    val dashboard: Dashboard = Dashboard(),
    val products: List<Product> = emptyList(),
    val orders: List<Order> = emptyList(),
    val lowStock: List<Product> = emptyList(),
    val error: String? = null
)

class SellerViewModel(application: Application) : AndroidViewModel(application) {
    private val api = ApiClient(BuildConfig.API_BASE_URL)
    private val tokens = TokenStore(application)

    var state by mutableStateOf(SellerUiState())
        private set

    init { restoreSession() }

    private fun restoreSession() {
        val access = tokens.access()
        if (access.isNullOrBlank()) {
            state = SellerUiState(loading = false)
            return
        }

        viewModelScope.launch {
            state = state.copy(loading = true, error = null)
            try {
                val user = withSession { token -> api.me(token) }
                val dashboard = withSession { token -> api.dashboard(token) }
                state = state.copy(
                    authenticated = true,
                    loading = false,
                    user = user,
                    dashboard = dashboard,
                    screen = SellerScreen.HOME
                )
            } catch (e: Exception) {
                tokens.clear()
                state = SellerUiState(loading = false, error = friendlyMessage(e))
            }
        }
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            state = state.copy(error = "Informe o usuário/e-mail e a senha.")
            return
        }

        viewModelScope.launch {
            state = state.copy(loading = true, error = null)
            try {
                val device = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                val session = withContext(Dispatchers.IO) {
                    api.login(username.trim(), password, device)
                }
                tokens.save(session.accessToken, session.refreshToken)

                val dashboard = withContext(Dispatchers.IO) {
                    api.dashboard(session.accessToken)
                }

                state = SellerUiState(
                    authenticated = true,
                    loading = false,
                    user = session.user,
                    dashboard = dashboard,
                    screen = SellerScreen.HOME
                )
            } catch (e: Exception) {
                state = state.copy(
                    authenticated = false,
                    loading = false,
                    error = friendlyMessage(e)
                )
            }
        }
    }

    fun selectScreen(screen: SellerScreen) {
        state = state.copy(screen = screen, error = null)
        when (screen) {
            SellerScreen.HOME -> refreshDashboard()
            SellerScreen.ORDERS -> refreshOrders()
            SellerScreen.PRODUCTS -> refreshProducts()
            SellerScreen.STOCK -> refreshLowStock()
            SellerScreen.ACCOUNT -> Unit
        }
    }

    fun refreshCurrent() {
        when (state.screen) {
            SellerScreen.HOME -> refreshDashboard()
            SellerScreen.ORDERS -> refreshOrders()
            SellerScreen.PRODUCTS -> refreshProducts()
            SellerScreen.STOCK -> refreshLowStock()
            SellerScreen.ACCOUNT -> Unit
        }
    }

    private fun refreshDashboard() = viewModelScope.launch {
        runLoading {
            val dashboard = withSession { token -> api.dashboard(token) }
            state = state.copy(dashboard = dashboard)
        }
    }

    private fun refreshProducts() = viewModelScope.launch {
        runLoading {
            val products = withSession { token -> api.products(token) }
            state = state.copy(products = products.items)
        }
    }

    private fun refreshOrders() = viewModelScope.launch {
        runLoading {
            val orders = withSession { token -> api.orders(token) }
            state = state.copy(orders = orders.items)
        }
    }

    private fun refreshLowStock() = viewModelScope.launch {
        runLoading {
            val low = withSession { token -> api.lowStock(token) }
            state = state.copy(lowStock = low)
        }
    }

    private suspend fun runLoading(block: suspend () -> Unit) {
        state = state.copy(loading = true, error = null)
        try {
            block()
            state = state.copy(loading = false)
        } catch (e: Exception) {
            state = state.copy(loading = false, error = friendlyMessage(e))
        }
    }

    private suspend fun <T> withSession(block: (String) -> T): T {
        val access = tokens.access() ?: throw ApiException(401, "Sessão encerrada. Entre novamente.")

        return try {
            withContext(Dispatchers.IO) { block(access) }
        } catch (e: ApiException) {
            if (e.statusCode != 401) throw e

            val refresh = tokens.refresh() ?: throw e
            val session = withContext(Dispatchers.IO) { api.refresh(refresh) }
            tokens.save(session.accessToken, session.refreshToken)
            state = state.copy(user = session.user)

            withContext(Dispatchers.IO) { block(session.accessToken) }
        }
    }

    fun logout() {
        val access = tokens.access()
        tokens.clear()
        state = SellerUiState(loading = false)

        if (!access.isNullOrBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { api.logout(access) }
            }
        }
    }

    fun clearError() {
        state = state.copy(error = null)
    }

    private fun friendlyMessage(error: Exception): String = when (error) {
        is ApiException -> error.message
        else -> "Não foi possível conectar ao FranLuz Seller. Verifique sua internet e tente novamente."
    }
}
