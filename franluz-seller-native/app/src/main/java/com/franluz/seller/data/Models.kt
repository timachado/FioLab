package com.franluz.seller.data

data class SellerUser(
    val id: Int,
    val name: String,
    val email: String
)

data class Session(
    val accessToken: String,
    val refreshToken: String,
    val user: SellerUser
)

data class Dashboard(
    val pendingOrders: Int = 0,
    val onHoldOrders: Int = 0,
    val processingOrders: Int = 0,
    val completedOrders: Int = 0,
    val publishedProducts: Int = 0,
    val draftProducts: Int = 0,
    val lowStockProducts: Int = 0
)

data class Product(
    val id: Int,
    val name: String,
    val sku: String,
    val price: String,
    val stockStatus: String,
    val stockQuantity: Int?,
    val imageUrl: String?
)

data class Order(
    val id: Int,
    val number: String,
    val status: String,
    val statusName: String,
    val total: String,
    val currency: String,
    val customerName: String,
    val itemCount: Int,
    val dateCreated: String?
)

data class PagedProducts(
    val items: List<Product>,
    val total: Int
)

data class PagedOrders(
    val items: List<Order>,
    val total: Int
)
