package com.example.desayuno.model

import java.util.UUID

enum class UserRole {
    CLIENT,
    COOK,
    ADMIN
}

data class User(
    val id: String,
    val name: String,
    val phone: String,
    val role: UserRole,
    val email: String = ""
)

enum class ProductCategory(val displayName: String) {
    BEVERAGE("Bebidas (Quinua / Maca / Soya)"),
    BREAD("Panes Especiales"),
    EXTRA("Extras y Acompañamientos"),
    COMBO("Combos de Desayuno")
}

data class MenuItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val price: Double,
    val category: ProductCategory,
    val availableStock: Int,
    val isAvailable: Boolean = true,
    val imageUrl: String? = null,
    val updatedAt: String? = null
)

data class CartItem(
    val menuItem: MenuItem,
    var quantity: Int,
    var notes: String = ""
)

enum class OrderStatus(val displayName: String) {
    PENDING("En cola"),
    PREPARING("En preparación"),
    READY("¡Listo para recoger!"),
    DELIVERED("Entregado"),
    CANCELLED("Cancelado")
}

data class OrderItem(
    val menuItemId: String,
    val menuItemName: String,
    val unitPrice: Double,
    val quantity: Int,
    val notes: String = ""
) {
    val subtotal: Double get() = unitPrice * quantity
}

data class Order(
    val id: String = UUID.randomUUID().toString(),
    val orderNumber: Int,
    val customerName: String,
    val customerPhone: String,
    val items: List<OrderItem>,
    val totalAmount: Double,
    var status: OrderStatus = OrderStatus.PENDING,
    var estimatedMinutes: Int = 3,
    val timestamp: Long = System.currentTimeMillis(),
    val customerId: String = "",
    val paymentMethod: String? = null,
    val paidAt: Long? = null,
    val paidAmount: Double? = null,
    val paymentReference: String? = null,
    val updatedAt: Long = timestamp
)

data class CashClosure(val date: String, val amount: Double, val paymentCount: Int)

data class StockInventory(
    var breadUnits: Int = 0,
    var quinuaLiters: Int = 0,
    var macaLiters: Int = 0,
    var soyaLiters: Int = 0,
    var extrasCount: Int = 0
)

data class Announcement(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
