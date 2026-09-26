package com.franluz.seller.data

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ApiException(val statusCode: Int, override val message: String) : Exception(message)

class ApiClient(private val baseUrl: String) {

    private fun call(
        method: String,
        path: String,
        token: String? = null,
        body: JSONObject? = null
    ): JSONObject {
        val url = URL(baseUrl.trimEnd('/') + "/" + path.trimStart('/'))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("User-Agent", "FranLuzSellerNative/2.0.0 Android")
            token?.let {
                setRequestProperty("Authorization", "Bearer $it")
                setRequestProperty("X-FranLuz-Token", it)
            }
            if (body != null) {
                doOutput = true
                outputStream.use { stream ->
                    stream.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }
        }

        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = if (stream != null) {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            } else {
                ""
            }

            val json = if (text.isBlank()) JSONObject() else JSONObject(text)

            if (status !in 200..299) {
                val message = json.optString("message").ifBlank { "Falha de comunicação com o FranLuz Seller." }
                throw ApiException(status, message)
            }

            return json
        } finally {
            connection.disconnect()
        }
    }

    fun health(): Boolean = call("GET", "health").optBoolean("ok", false)

    fun login(username: String, password: String, deviceName: String): Session {
        val body = JSONObject()
            .put("username", username)
            .put("password", password)
            .put("device_name", deviceName)

        return parseSession(call("POST", "auth/login", body = body))
    }

    fun refresh(refreshToken: String): Session {
        val body = JSONObject().put("refresh_token", refreshToken)
        return parseSession(call("POST", "auth/refresh", body = body))
    }

    fun logout(accessToken: String) {
        call("POST", "auth/logout", token = accessToken)
    }

    fun me(accessToken: String): SellerUser {
        return parseUser(call("GET", "me", token = accessToken).getJSONObject("user"))
    }

    fun dashboard(accessToken: String): Dashboard {
        val root = call("GET", "dashboard", token = accessToken)
        val orders = root.getJSONObject("orders")
        val products = root.getJSONObject("products")

        return Dashboard(
            pendingOrders = orders.optInt("pending"),
            onHoldOrders = orders.optInt("on_hold"),
            processingOrders = orders.optInt("processing"),
            completedOrders = orders.optInt("completed"),
            publishedProducts = products.optInt("published"),
            draftProducts = products.optInt("draft"),
            lowStockProducts = products.optInt("low_stock")
        )
    }

    fun products(accessToken: String): PagedProducts {
        val root = call("GET", "products?per_page=50", token = accessToken)
        val array = root.optJSONArray("items")
        val items = buildList {
            if (array != null) {
                for (i in 0 until array.length()) {
                    add(parseProduct(array.getJSONObject(i)))
                }
            }
        }
        return PagedProducts(items, root.optInt("total", items.size))
    }

    fun lowStock(accessToken: String): List<Product> {
        val root = call("GET", "stock/low", token = accessToken)
        val array = root.optJSONArray("items")
        return buildList {
            if (array != null) {
                for (i in 0 until array.length()) {
                    add(parseProduct(array.getJSONObject(i)))
                }
            }
        }
    }

    fun orders(accessToken: String): PagedOrders {
        val root = call("GET", "orders?per_page=50", token = accessToken)
        val array = root.optJSONArray("items")
        val items = buildList {
            if (array != null) {
                for (i in 0 until array.length()) {
                    add(parseOrder(array.getJSONObject(i)))
                }
            }
        }
        return PagedOrders(items, root.optInt("total", items.size))
    }

    private fun parseSession(root: JSONObject): Session {
        return Session(
            accessToken = root.getString("access_token"),
            refreshToken = root.getString("refresh_token"),
            user = parseUser(root.getJSONObject("user"))
        )
    }

    private fun parseUser(json: JSONObject): SellerUser = SellerUser(
        id = json.optInt("id"),
        name = json.optString("name"),
        email = json.optString("email")
    )

    private fun parseProduct(json: JSONObject): Product {
        val images = json.optJSONArray("images")
        val image = if (images != null && images.length() > 0) {
            images.optJSONObject(0)?.optString("src")?.takeIf { it.isNotBlank() }
        } else null

        val stock = if (json.has("stock_quantity") && !json.isNull("stock_quantity")) {
            json.optInt("stock_quantity")
        } else null

        return Product(
            id = json.optInt("id"),
            name = json.optString("name"),
            sku = json.optString("sku"),
            price = json.optString("price"),
            stockStatus = json.optString("stock_status"),
            stockQuantity = stock,
            imageUrl = image
        )
    }

    private fun parseOrder(json: JSONObject): Order {
        val customer = json.optJSONObject("customer") ?: JSONObject()
        return Order(
            id = json.optInt("id"),
            number = json.optString("number"),
            status = json.optString("status"),
            statusName = json.optString("status_name"),
            total = json.optString("total"),
            currency = json.optString("currency"),
            customerName = customer.optString("name"),
            itemCount = json.optInt("item_count"),
            dateCreated = json.optString("date_created").takeIf { it.isNotBlank() }
        )
    }
}
