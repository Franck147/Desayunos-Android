package com.example.desayuno.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.desayuno.model.CartItem
import com.example.desayuno.model.MenuItem
import com.example.desayuno.model.Order
import com.example.desayuno.model.OrderItem
import com.example.desayuno.model.OrderStatus
import com.example.desayuno.model.StockInventory
import com.example.desayuno.model.User
import com.example.desayuno.model.UserRole
import com.example.desayuno.repository.DesayunoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class DesayunoViewModel : ViewModel() {

    val currentUser: StateFlow<User?> = DesayunoRepository.currentUserState
    val menu: StateFlow<List<MenuItem>> = DesayunoRepository.menuState
    val orders: StateFlow<List<Order>> = DesayunoRepository.ordersState
    val stock: StateFlow<StockInventory> = DesayunoRepository.stockState
    val announcements = DesayunoRepository.announcementsState

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private val _orderSuccessMessage = MutableStateFlow<String?>(null)
    val orderSuccessMessage: StateFlow<String?> = _orderSuccessMessage.asStateFlow()

    fun loginUser(name: String, phone: String, role: UserRole) {
        val user = User(
            name = name.ifBlank { if (role == UserRole.COOK) "Cocinero Principal" else "Cliente Consumidor" },
            phone = phone.ifBlank { "987654321" },
            role = role
        )
        DesayunoRepository.setCurrentUser(user)
    }

    fun loginAdminWithSupabase(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val (success, errorMsg) = com.example.desayuno.network.SupabaseService.loginAdminWithSupabase(email, password)
            if (success) {
                loginUser(email.ifBlank { "Cocinero Administrador" }, "987654321", UserRole.COOK)
                onResult(true, null)
            } else {
                onResult(false, errorMsg)
            }
        }
    }

    fun logout() {
        DesayunoRepository.logout()
        _cart.value = emptyList()
    }

    fun switchRole() {
        val current = currentUser.value
        if (current != null) {
            val newRole = if (current.role == UserRole.CLIENT) UserRole.COOK else UserRole.CLIENT
            loginUser(current.name, current.phone, newRole)
        } else {
            loginUser("Usuario", "987654321", UserRole.CLIENT)
        }
    }

    fun addToCart(menuItem: MenuItem) {
        val currentCart = _cart.value.toMutableList()
        val index = currentCart.indexOfFirst { it.menuItem.id == menuItem.id }
        if (index != -1) {
            val existing = currentCart[index]
            if (existing.quantity < menuItem.availableStock) {
                currentCart[index] = existing.copy(quantity = existing.quantity + 1)
            }
        } else {
            if (menuItem.availableStock > 0) {
                currentCart.add(CartItem(menuItem = menuItem, quantity = 1))
            }
        }
        _cart.value = currentCart
    }

    fun removeFromCart(menuItem: MenuItem) {
        val currentCart = _cart.value.toMutableList()
        val index = currentCart.indexOfFirst { it.menuItem.id == menuItem.id }
        if (index != -1) {
            val existing = currentCart[index]
            if (existing.quantity > 1) {
                currentCart[index] = existing.copy(quantity = existing.quantity - 1)
            } else {
                currentCart.removeAt(index)
            }
        }
        _cart.value = currentCart
    }

    fun clearCart() {
        _cart.value = emptyList()
    }

    fun placeOrder(context: Context): Order? {
        val user = currentUser.value ?: return null
        val cartItems = _cart.value
        if (cartItems.isEmpty()) return null

        val orderItems = cartItems.map { cartItem ->
            OrderItem(
                menuItemId = cartItem.menuItem.id,
                menuItemName = cartItem.menuItem.name,
                unitPrice = cartItem.menuItem.price,
                quantity = cartItem.quantity,
                notes = cartItem.notes
            )
        }

        val createdOrder = DesayunoRepository.placeOrder(
            context = context,
            customerName = user.name,
            customerPhone = user.phone,
            items = orderItems
        )

        if (createdOrder != null) {
            _cart.value = emptyList()
            _orderSuccessMessage.value = "¡Pedido #${createdOrder.orderNumber} realizado con éxito! Tiempo aprox: ${createdOrder.estimatedMinutes} min."
        }

        return createdOrder
    }

    fun clearSuccessMessage() {
        _orderSuccessMessage.value = null
    }

    fun updateOrderStatus(context: Context, orderId: String, newStatus: OrderStatus) {
        DesayunoRepository.updateOrderStatus(context, orderId, newStatus)
    }

    fun updateStock(context: Context, newStock: StockInventory) {
        DesayunoRepository.updateStockInventory(context, newStock)
    }

    fun addOrUpdateMenuItem(context: Context, menuItem: MenuItem, imageUri: android.net.Uri? = null) {
        viewModelScope.launch {
            DesayunoRepository.uploadAndSaveMenuItem(context, menuItem, imageUri)
        }
    }

    fun postAnnouncement(context: Context, title: String, message: String) {
        DesayunoRepository.postAnnouncement(context, title, message)
    }

    fun getSalesSummary(): Pair<Int, Double> {
        return DesayunoRepository.getSalesSummary()
    }
}
