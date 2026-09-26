package com.franluz.seller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.franluz.seller.SellerScreen
import com.franluz.seller.SellerUiState
import com.franluz.seller.SellerViewModel
import com.franluz.seller.data.Order
import com.franluz.seller.data.Product

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerApp(viewModel: SellerViewModel = viewModel()) {
    val state = viewModel.state

    Box(Modifier.fillMaxSize().background(Ivory)) {
        if (!state.authenticated) {
            LoginScreen(state, viewModel::login, viewModel::clearError)
        } else {
            Scaffold(
                containerColor = Ivory,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Ivory, titleContentColor = Brown),
                        title = {
                            Column {
                                Text("FranLuz Seller", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(state.user?.name ?: "Central de vendas", color = Muted, fontSize = 11.sp)
                            }
                        },
                        actions = {
                            if (state.screen != SellerScreen.ACCOUNT) {
                                IconButton(onClick = viewModel::refreshCurrent) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Atualizar")
                                }
                            }
                        }
                    )
                },
                bottomBar = { SellerBottomBar(state.screen, viewModel::selectScreen) }
            ) { inner ->
                Column(Modifier.fillMaxSize().padding(inner)) {
                    ErrorBanner(state.error, viewModel::clearError)
                    when (state.screen) {
                        SellerScreen.HOME -> DashboardScreen(state, viewModel::selectScreen)
                        SellerScreen.ORDERS -> OrdersScreen(state.orders)
                        SellerScreen.PRODUCTS -> ProductsScreen(state.products, false)
                        SellerScreen.STOCK -> ProductsScreen(state.lowStock, true)
                        SellerScreen.ACCOUNT -> AccountScreen(state, viewModel::logout)
                    }
                }
            }
        }

        if (state.loading) LoadingOverlay()
    }
}

@Composable
private fun LoginScreen(state: SellerUiState, onLogin: (String, String) -> Unit, onClearError: () -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 42.dp),
        verticalArrangement = Arrangement.Center
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Card(
                    modifier = Modifier.size(76.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Brown)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("F", color = Gold, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("FranLuz Seller", color = Brown, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Aplicativo nativo exclusivo para gestão da FranLuz Home Decor.",
                    Modifier.padding(top = 8.dp, bottom = 22.dp),
                    color = Muted,
                    fontSize = 14.sp
                )

                ErrorBanner(state.error, onClearError)

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; if (state.error != null) onClearError() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Usuário ou e-mail") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; if (state.error != null) onClearError() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Senha") },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (visible) "Ocultar senha" else "Mostrar senha"
                            )
                        }
                    }
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { onLogin(username, password) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Entrar no Seller")
                }
                Text(
                    "Sem WebView • Sessão protegida no Android",
                    Modifier.padding(top = 18.dp),
                    color = Muted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String?, onDismiss: () -> Unit) {
    if (message.isNullOrBlank()) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Red.copy(alpha = 0.10f))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), color = Red, fontSize = 13.sp)
            TextButton(onClick = onDismiss) { Text("OK", color = Red) }
        }
    }
}

@Composable
private fun DashboardScreen(state: SellerUiState, onNavigate: (SellerScreen) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Brown)) {
                Column(Modifier.padding(20.dp)) {
                    Text("SELLER CENTER 2.0", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Gestão da FranLuz sem abrir o site",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        "Pedidos, catálogo e estoque chegam direto do WooCommerce pela API segura.",
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.82f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 7.dp)
                    )
                }
            }
        }
        item { Text("Pedidos", color = Brown, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Novos", state.dashboard.pendingOrders, Modifier.weight(1f))
                MetricCard("Preparando", state.dashboard.processingOrders, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Em espera", state.dashboard.onHoldOrders, Modifier.weight(1f))
                MetricCard("Concluídos", state.dashboard.completedOrders, Modifier.weight(1f))
            }
        }
        item { Text("Atalhos", color = Brown, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        item {
            QuickAction(Icons.Default.ShoppingBag, "Gerenciar pedidos", "Pagamento, separação e andamento") {
                onNavigate(SellerScreen.ORDERS)
            }
        }
        item {
            QuickAction(
                Icons.Default.Inventory2,
                "Produtos",
                "${state.dashboard.publishedProducts} publicados • ${state.dashboard.draftProducts} rascunhos"
            ) { onNavigate(SellerScreen.PRODUCTS) }
        }
        item {
            QuickAction(
                Icons.Default.WarningAmber,
                "Estoque baixo",
                "${state.dashboard.lowStockProducts} produtos precisam de atenção"
            ) { onNavigate(SellerScreen.STOCK) }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Paper)) {
        Column(Modifier.padding(16.dp)) {
            Text(value.toString(), color = Brown, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Paper)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Brown, modifier = Modifier.size(30.dp))
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = Ink)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ProductsScreen(products: List<Product>, lowStockOnly: Boolean) {
    if (products.isEmpty()) {
        EmptyState(if (lowStockOnly) "Nenhum produto com estoque baixo." else "Nenhum produto encontrado.")
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(products, key = { it.id }) { product -> ProductCard(product, lowStockOnly) }
    }
}

@Composable
private fun ProductCard(product: Product, lowStock: Boolean) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Paper)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Card(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Soft)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Inventory2, null, tint = Brown)
                }
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(product.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(product.sku.ifBlank { "Sem SKU" }, color = Muted, fontSize = 11.sp)
                Text(
                    "R$ ${product.price.ifBlank { "0,00" }.replace('.', ',')}",
                    color = Brown,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                val qty = product.stockQuantity?.toString() ?: "—"
                Text(
                    if (lowStock) "Estoque: $qty" else qty,
                    color = if (lowStock) Red else Muted,
                    fontSize = 11.sp,
                    fontWeight = if (lowStock) FontWeight.Bold else FontWeight.Normal
                )
                Text(product.stockStatus, color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun OrdersScreen(orders: List<Order>) {
    if (orders.isEmpty()) {
        EmptyState("Nenhum pedido encontrado.")
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(orders, key = { it.id }) { order ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Paper)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Pedido #${order.number}",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            color = Brown
                        )
                        Text(order.statusName, color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    Text(order.customerName.ifBlank { "Cliente" }, fontWeight = FontWeight.SemiBold)
                    Text("${order.itemCount} item(ns)", color = Muted, fontSize = 12.sp)
                    Text(
                        "${order.currency} ${order.total}",
                        color = Brown,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountScreen(state: SellerUiState, onLogout: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Paper)) {
                Column(Modifier.padding(20.dp)) {
                    Icon(Icons.Default.AccountCircle, null, tint = Brown, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(state.user?.name ?: "Vendedor", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(state.user?.email ?: "", color = Muted, fontSize = 13.sp)
                    Text(
                        "Sessão protegida pelo Android Keystore",
                        Modifier.padding(top = 10.dp),
                        color = Green,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        item {
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Logout, null)
                Spacer(Modifier.size(8.dp))
                Text("Sair do FranLuz Seller")
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun SellerBottomBar(selected: SellerScreen, onSelect: (SellerScreen) -> Unit) {
    NavigationBar(containerColor = Paper) {
        BottomItem(SellerScreen.HOME, selected, Icons.Default.Home, "Início", onSelect)
        BottomItem(SellerScreen.ORDERS, selected, Icons.Default.ShoppingBag, "Pedidos", onSelect)
        BottomItem(SellerScreen.PRODUCTS, selected, Icons.Default.Inventory2, "Produtos", onSelect)
        BottomItem(SellerScreen.STOCK, selected, Icons.Default.WarningAmber, "Estoque", onSelect)
        BottomItem(SellerScreen.ACCOUNT, selected, Icons.Default.AccountCircle, "Conta", onSelect)
    }
}

@Composable
private fun RowScope.BottomItem(
    screen: SellerScreen,
    selected: SellerScreen,
    icon: ImageVector,
    label: String,
    onSelect: (SellerScreen) -> Unit
) {
    NavigationBarItem(
        selected = selected == screen,
        onClick = { onSelect(screen) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, fontSize = 9.sp) }
    )
}

@Composable
private fun LoadingOverlay() {
    Box(
        Modifier.fillMaxSize().background(Ivory.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center
    ) {
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Paper)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(26.dp), color = Brown, strokeWidth = 3.dp)
                Text(
                    "Atualizando Seller…",
                    Modifier.padding(start = 12.dp),
                    color = Brown,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
