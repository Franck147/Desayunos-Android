package com.example.desayuno.repository

import android.content.Context
import com.example.desayuno.model.Announcement
import com.example.desayuno.model.MenuItem
import com.example.desayuno.model.Order
import com.example.desayuno.model.OrderItem
import com.example.desayuno.model.OrderStatus
import com.example.desayuno.model.ProductCategory
import com.example.desayuno.model.StockInventory
import com.example.desayuno.model.User
import com.example.desayuno.model.UserRole
import com.example.desayuno.notification.NotificationHelper
import com.example.desayuno.widget.DesayunoWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object DesayunoRepository {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUserState: StateFlow<User?> = _currentUser.asStateFlow()

    private val _stock = MutableStateFlow(StockInventory())
    val stockState: StateFlow<StockInventory> = _stock.asStateFlow()

    private val _menu = MutableStateFlow(
        listOf(
            MenuItem(
                id = "1",
                name = "Quinua con Manzana",
                description = "Jarra/Vaso de quinua caliente aromatizada con manzana y canela",
                price = 2.50,
                category = ProductCategory.BEVERAGE,
                availableStock = 20,
                imageUrl = "https://images.unsplash.com/photo-1544787219-7f47ccb76574?w=500&auto=format&fit=crop"
            ),
            MenuItem(
                id = "2",
                name = "Maca Especial",
                description = "Nutritiva maca andina caliente con leche",
                price = 2.50,
                category = ProductCategory.BEVERAGE,
                availableStock = 15,
                imageUrl = "https://images.unsplash.com/photo-1517256064527-09c73fc73e38?w=500&auto=format&fit=crop"
            ),
            MenuItem(
                id = "3",
                name = "Soya Caliente",
                description = "Bebida de soya natural reconfortante",
                price = 2.00,
                category = ProductCategory.BEVERAGE,
                availableStock = 15,
                imageUrl = "https://images.unsplash.com/photo-1556881286-fc6915169721?w=500&auto=format&fit=crop"
            ),
            MenuItem(
                id = "4",
                name = "Pan con Chicharrón",
                description = "Chicharrón crujiente con camote frito y salsa criolla",
                price = 7.00,
                category = ProductCategory.BREAD,
                availableStock = 25,
                imageUrl = "https://images.unsplash.com/photo-1528735602780-2552fd46c7af?w=500&auto=format&fit=crop"
            ),
            MenuItem(
                id = "5",
                name = "Pan con Lomo Saltado",
                description = "Jugoso lomo saltado con cebolla y tomate al jugo",
                price = 8.00,
                category = ProductCategory.BREAD,
                availableStock = 20,
                imageUrl = "https://images.unsplash.com/photo-1509722747041-616f39b57569?w=500&auto=format&fit=crop"
            ),
            MenuItem(
                id = "6",
                name = "Pan con Huevo y Palta",
                description = "Huevo frito o revuelto con láminas de palta fresca",
                price = 4.00,
                category = ProductCategory.BREAD,
                availableStock = 35,
                imageUrl = "https://images.unsplash.com/photo-1525351484163-7529414344d8?w=500&auto=format&fit=crop"
            ),
            MenuItem(
                id = "7",
                name = "Tamal Criollo de Pollo",
                description = "Tamal artesanal con salsa criolla picantita",
                price = 4.50,
                category = ProductCategory.EXTRA,
                availableStock = 20,
                imageUrl = "https://images.unsplash.com/photo-1565299585323-38d6b0865b47?w=500&auto=format&fit=crop"
            ),
            MenuItem(
                id = "8",
                name = "Combo Mañanero",
                description = "Quinua + Pan con Chicharrón especial",
                price = 9.00,
                category = ProductCategory.COMBO,
                availableStock = 20,
                imageUrl = "https://images.unsplash.com/photo-1533089860892-a7c6f0a88666?w=500&auto=format&fit=crop"
            )
        )
    )
    val menuState: StateFlow<List<MenuItem>> = _menu.asStateFlow()

    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val ordersState: StateFlow<List<Order>> = _orders.asStateFlow()

    private val _announcements = MutableStateFlow(
        listOf(
            Announcement(
                title = "¡Bienvenidos!",
                message = "Desayunos frescos todos los días desde las 6:00 AM."
            )
        )
    )
    val announcementsState: StateFlow<List<Announcement>> = _announcements.asStateFlow()

    private var nextOrderNumber = 101

    fun setCurrentUser(user: User?) {
        _currentUser.value = user
    }

    fun logout() {
        _currentUser.value = null
    }

    fun placeOrder(
        context: Context,
        customerName: String,
        customerPhone: String,
        items: List<OrderItem>
    ): Order? {
        if (items.isEmpty()) return null

        val currentMenuList = _menu.value.toMutableList()
        for (orderItem in items) {
            val index = currentMenuList.indexOfFirst { it.id == orderItem.menuItemId }
            if (index != -1) {
                val currentItem = currentMenuList[index]
                val newStock = (currentItem.availableStock - orderItem.quantity).coerceAtLeast(0)
                currentMenuList[index] = currentItem.copy(availableStock = newStock)
            }
        }
        _menu.value = currentMenuList

        val activeQueueCount = _orders.value.count {
            it.status == OrderStatus.PENDING || it.status == OrderStatus.PREPARING
        }
        val estimatedTime = (activeQueueCount + 1) * 3

        val totalAmount = items.sumOf { it.subtotal }

        val newOrder = Order(
            orderNumber = nextOrderNumber++,
            customerName = customerName,
            customerPhone = customerPhone,
            items = items,
            totalAmount = totalAmount,
            status = OrderStatus.PENDING,
            estimatedMinutes = estimatedTime
        )

        val updatedOrders = _orders.value.toMutableList()
        updatedOrders.add(0, newOrder)
        _orders.value = updatedOrders

        val currentStock = _stock.value
        val breadCount = items.filter { item ->
            val cat = currentMenuList.find { it.id == item.menuItemId }?.category
            cat == ProductCategory.BREAD || cat == ProductCategory.COMBO
        }.sumOf { it.quantity }

        val updatedStock = currentStock.copy(
            breadUnits = (currentStock.breadUnits - breadCount).coerceAtLeast(0)
        )
        _stock.value = updatedStock

        DesayunoWidgetProvider.updateAllWidgets(context)

        // Sincronizar en segundo plano con Supabase Database y Storage
        CoroutineScope(Dispatchers.IO).launch {
            com.example.desayuno.network.SupabaseService.saveOrderToSupabase(
                orderId = newOrder.id,
                orderNumber = newOrder.orderNumber,
                customerName = newOrder.customerName,
                customerPhone = newOrder.customerPhone,
                totalAmount = newOrder.totalAmount,
                status = newOrder.status.name,
                estimatedMinutes = newOrder.estimatedMinutes,
                items = newOrder.items
            )
            com.example.desayuno.network.SupabaseService.saveStockInventoryToSupabase(updatedStock)
        }

        return newOrder
    }

    fun updateOrderStatus(context: Context, orderId: String, newStatus: OrderStatus) {
        val list = _orders.value.toMutableList()
        val index = list.indexOfFirst { it.id == orderId }
        if (index != -1) {
            val order = list[index]
            val updated = order.copy(status = newStatus)
            list[index] = updated
            _orders.value = list

            if (newStatus == OrderStatus.READY) {
                NotificationHelper.showOrderReadyNotification(
                    context,
                    order.orderNumber,
                    order.customerName
                )
            }

            recalculateQueueTimes()

            DesayunoWidgetProvider.updateAllWidgets(context)
        }
    }

    private fun recalculateQueueTimes() {
        val list = _orders.value.toMutableList()
        val pendingAndPreparing = list.filter {
            it.status == OrderStatus.PENDING || it.status == OrderStatus.PREPARING
        }.sortedBy { it.timestamp }

        pendingAndPreparing.forEachIndexed { queueIndex, order ->
            val orderIdx = list.indexOfFirst { it.id == order.id }
            if (orderIdx != -1) {
                list[orderIdx] = list[orderIdx].copy(estimatedMinutes = (queueIndex + 1) * 3)
            }
        }
        _orders.value = list
    }

    fun updateStockInventory(context: Context, newStock: StockInventory) {
        _stock.value = newStock
        DesayunoWidgetProvider.updateAllWidgets(context)
    }

    fun addOrUpdateMenuItem(context: Context, item: MenuItem) {
        val list = _menu.value.toMutableList()
        val index = list.indexOfFirst { it.id == item.id }
        if (index != -1) {
            list[index] = item
        } else {
            list.add(item)
        }
        _menu.value = list
        DesayunoWidgetProvider.updateAllWidgets(context)
    }

    suspend fun uploadAndSaveMenuItem(context: Context, item: MenuItem, imageUri: android.net.Uri?) {
        val finalImageUrl = if (imageUri != null) {
            com.example.desayuno.network.SupabaseService.uploadImageToSupabase(context, imageUri) ?: item.imageUrl
        } else {
            item.imageUrl
        }

        val updatedItem = item.copy(imageUrl = finalImageUrl)
        addOrUpdateMenuItem(context, updatedItem)

        // Sincronizar en Supabase Database
        com.example.desayuno.network.SupabaseService.saveMenuItemToSupabase(
            id = updatedItem.id,
            name = updatedItem.name,
            description = updatedItem.description,
            price = updatedItem.price,
            category = updatedItem.category.name,
            availableStock = updatedItem.availableStock,
            imageUrl = updatedItem.imageUrl
        )
    }

    fun postAnnouncement(context: Context, title: String, message: String) {
        val newAnnouncement = Announcement(title = title, message = message)
        val list = _announcements.value.toMutableList()
        list.add(0, newAnnouncement)
        _announcements.value = list

        DesayunoWidgetProvider.updateAllWidgets(context)
    }

    fun getSalesSummary(): Pair<Int, Double> {
        val deliveredOrders = _orders.value.filter {
            it.status == OrderStatus.DELIVERED || it.status == OrderStatus.READY
        }
        val totalBreakfasts = deliveredOrders.sumOf { order -> order.items.sumOf { it.quantity } }
        val totalRevenue = deliveredOrders.sumOf { it.totalAmount }
        return Pair(totalBreakfasts, totalRevenue)
    }
}
