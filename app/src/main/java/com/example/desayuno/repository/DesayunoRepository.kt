package com.example.desayuno.repository

import android.content.Context
import com.example.desayuno.model.*
import com.example.desayuno.network.SupabaseService
import com.example.desayuno.notification.NotificationHelper
import com.example.desayuno.notification.orderAlerts
import com.example.desayuno.widget.DesayunoWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object DesayunoRepository {
    private val refreshLock = Mutex()
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUserState = _currentUser.asStateFlow()
    private val _menu = MutableStateFlow<List<MenuItem>>(emptyList())
    val menuState = _menu.asStateFlow()
    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val ordersState = _orders.asStateFlow()
    private val _announcements = MutableStateFlow<List<Announcement>>(emptyList())
    val announcementsState = _announcements.asStateFlow()
    private val _accepting = MutableStateFlow(false)
    val acceptingOrders = _accepting.asStateFlow()
    private val _closures = MutableStateFlow<List<CashClosure>>(emptyList())
    val cashClosures = _closures.asStateFlow()
    private var hasSnapshot = false

    fun setCurrentUser(user: User?) {
        if (_currentUser.value?.id != user?.id) clearData()
        _currentUser.value = user
    }
    private fun clearData() { _menu.value=emptyList(); _orders.value=emptyList(); _announcements.value=emptyList(); _accepting.value=false; _closures.value=emptyList(); hasSnapshot=false }
    fun logout() { _currentUser.value=null; clearData() }

    suspend fun refresh(context: Context) = refreshLock.withLock {
        val user = _currentUser.value ?: return@withLock
        val api = SupabaseService.api
        // Publicar la instantánea cuando terminen las consultas y siga la misma sesión.
        val menu = api.menu(); val orders = api.orders(); val announcements = api.announcements(); val accepting=api.acceptingOrders()
        val closures = if (user.role == UserRole.ADMIN) api.cashClosures() else emptyList()
        if (_currentUser.value?.id != user.id) return@withLock
        val alerts=orderAlerts(user,_orders.value,orders,hasSnapshot)
        _menu.value=menu; _orders.value=orders; _announcements.value=announcements; _accepting.value=accepting
        _closures.value=closures
        hasSnapshot=true
        alerts.forEach { NotificationHelper.show(context,user.id,it) }
        DesayunoWidgetProvider.updateAllWidgets(context)
    }

    fun applyConfirmedOrder(order: Order, withPayment: Boolean = true) {
        val old = _orders.value.find { it.id == order.id }
        val merged = order.copy(items = order.items.ifEmpty { old?.items.orEmpty() },
            paymentMethod = if (withPayment) order.paymentMethod else old?.paymentMethod,
            paidAt = if (withPayment) order.paidAt else old?.paidAt,
            paidAmount = if (withPayment) order.paidAmount else old?.paidAmount,
            paymentReference = if (withPayment) order.paymentReference else old?.paymentReference)
        _orders.value = _orders.value.filterNot { it.id == order.id } + merged
    }
    fun applyConfirmedClosure(closure: CashClosure) {
        _closures.value = (_closures.value.filterNot { it.date == closure.date } + closure).sortedByDescending { it.date }
    }

    /** Revenue is confirmed payments on a Lima business date, not READY orders. */
    fun getSalesSummary(date: LocalDate = LocalDate.now(ZoneId.of("America/Lima"))): Pair<Int,Double> {
        val paid = _orders.value.filter { order -> order.paidAt?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.of("America/Lima")).toLocalDate() == date
        } == true }
        return paid.sumOf { it.items.sumOf { item -> item.quantity } } to
            paid.fold(BigDecimal.ZERO) { sum,order -> sum + BigDecimal.valueOf(order.totalAmount) }.toDouble()
    }
}
