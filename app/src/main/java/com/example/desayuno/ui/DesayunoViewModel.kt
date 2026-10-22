package com.example.desayuno.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.desayuno.model.*
import com.example.desayuno.network.*
import com.example.desayuno.repository.DesayunoRepository
import com.example.desayuno.widget.DesayunoWidgetProvider
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import com.example.desayuno.notification.*
import java.util.UUID

data class PendingCheckout(val requestId: String,val userId: String,val items: List<CartItem>)
data class OrderDestination(val userId: String,val orderId: String)

class DesayunoViewModel : ViewModel() {
    val currentUser = DesayunoRepository.currentUserState
    val menu = DesayunoRepository.menuState
    val orders = DesayunoRepository.ordersState
    val announcements = DesayunoRepository.announcementsState
    val acceptingOrders = DesayunoRepository.acceptingOrders
    val cashClosures = DesayunoRepository.cashClosures
    private val _readNotices = MutableStateFlow<Set<String>>(emptySet())
    val readNotices = _readNotices.asStateFlow()
    val notices = combine(currentUser,orders,announcements) { user,rows,news ->
        if(user == null) emptyList() else buildNotices(user,rows,news)
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val _orderToOpen=MutableStateFlow<OrderDestination?>(null)
    val orderToOpen=_orderToOpen.asStateFlow()
    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart = _cart.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _orderSuccessMessage = MutableStateFlow<String?>(null)
    val orderSuccessMessage = _orderSuccessMessage.asStateFlow()
    private val _pending = MutableStateFlow(false)
    val pendingCheckout = _pending.asStateFlow()
    private var pending: PendingCheckout? = null
    private var syncJob: Job? = null
    private lateinit var appContext: Context
    private var networkInitialized = false
    private val gson = Gson()
    private val checkoutPrefs get() = appContext.getSharedPreferences("pending_checkout",Context.MODE_PRIVATE)

    fun clearMessage() { _message.value=null }
    fun requestOrderOpen(orderId: String,userId: String) {
        if(orderId.isNotBlank() && userId.isNotBlank()) _orderToOpen.value=OrderDestination(userId,orderId)
    }
    fun clearOrderDestination() { _orderToOpen.value=null }
    fun deliverReadyAlerts() {
        val user=currentUser.value ?: return
        if(user.role==UserRole.CLIENT) orders.value.filter { it.customerId==user.id && it.status==OrderStatus.READY }
            .forEach { NotificationHelper.show(appContext,user.id,orderNotice(user,it)) }
    }
    fun markNoticesRead(ids: Set<String>) {
        val user=currentUser.value ?: return
        _readNotices.value=(_readNotices.value + ids).toList().takeLast(1000).toSet()
        appContext.getSharedPreferences("notice_reads",Context.MODE_PRIVATE).edit()
            .putStringSet("read_${user.id}",_readNotices.value).apply()
    }
    fun clearSuccessMessage() { _orderSuccessMessage.value=null }
    private fun notice(message: String) { _message.value=message }
    private fun error(error: Exception) {
        if(error is SessionExpiredException) {
            syncJob?.cancel(); NotificationHelper.clear(appContext); DesayunoRepository.logout(); _cart.value=emptyList(); pending=null; _pending.value=false; _readNotices.value=emptySet()
        }
        notice(if(error is java.io.IOException) "No se pudo conectar. Revise su conexión y vuelva a intentar." else error.message ?: "No se pudo completar la operación")
    }
    private fun perform(block: suspend () -> Unit) {
        if(_busy.value) return
        _busy.value=true
        viewModelScope.launch {
            try { block() } catch(e: CancellationException) { throw e } catch(e: Exception) { error(e) }
            finally { _busy.value=false }
        }
    }
    fun initialize(context: Context) {
        appContext=context.applicationContext
        perform {
            if(!networkInitialized) { SupabaseService.initialize(appContext); networkInitialized=true }
            if(currentUser.value == null && SupabaseService.api.hasSession()) establishUser(SupabaseService.api.profile())
        }
    }
    private fun loadPending(user: User) {
        pending=checkoutPrefs.getString("pending_${user.id}",null)?.let { gson.fromJson(it,PendingCheckout::class.java) }
        _pending.value=pending != null
        _cart.value=pending?.items ?: emptyList()
    }
    private fun savePending(value: PendingCheckout?) {
        val id=currentUser.value?.id ?: error("Sesión requerida")
        check(checkoutPrefs.edit().apply { if(value == null) remove("pending_$id") else putString("pending_$id",gson.toJson(value)) }.commit()) {
            "No se pudo guardar el intento de pedido"
        }
        pending=value; _pending.value=value != null
    }
    private suspend fun establishUser(user: User) {
        _readNotices.value=appContext.getSharedPreferences("notice_reads",Context.MODE_PRIVATE)
            .getStringSet("read_${user.id}",emptySet()).orEmpty().toSet()
        DesayunoRepository.setCurrentUser(user); loadPending(user)
        refreshAfterWrite()
    }
    private fun authenticate(email: String,password: String,staff: Boolean,onResult: (Boolean,String?) -> Unit) {
        perform {
            try {
                check(networkInitialized) { "La configuración todavía no está disponible. Reintente." }
                val user=SupabaseService.api.login(email,password)
                if(staff && user.role !in listOf(UserRole.COOK,UserRole.ADMIN)) {
                    SupabaseService.api.clearSession(); error("Su cuenta no tiene permisos de cocina")
                }
                establishUser(user); onResult(true,null)
            } catch(e: CancellationException) { throw e } catch(e: Exception) { onResult(false,e.message ?: "No se pudo iniciar sesión") }
        }
    }
    fun loginClientWithSupabase(email: String,password: String,onResult: (Boolean,String?) -> Unit) = authenticate(email,password,false,onResult)
    fun loginAdminWithSupabase(email: String,password: String,onResult: (Boolean,String?) -> Unit) = authenticate(email,password,true,onResult)
    fun registerClientWithSupabase(name: String,phone: String,email: String,password: String,onResult: (Boolean,String?) -> Unit) {
        perform {
            try {
                check(networkInitialized) { "La configuración todavía no está disponible" }
                if(SupabaseService.api.signUp(email,password,name,phone)) establishUser(SupabaseService.api.profile())
                else notice("Revise su correo para confirmar la cuenta y luego inicie sesión.")
                onResult(true,null)
            } catch(e: CancellationException) { throw e } catch(e: Exception) { onResult(false,e.message ?: "No se pudo registrar") }
        }
    }
    fun logout() {
        if(_busy.value) { notice("Espere a que termine la operación."); return }
        syncJob?.cancel()
        if(networkInitialized) SupabaseService.api.clearSession()
        NotificationHelper.clear(appContext)
        _readNotices.value=emptySet(); _orderToOpen.value=null
        DesayunoRepository.logout(); _cart.value=emptyList(); pending=null; _pending.value=false
        DesayunoWidgetProvider.updateAllWidgets(appContext)
    }
    fun startSync() {
        syncJob?.cancel()
        if(currentUser.value == null) return
        syncJob=viewModelScope.launch {
            while(isActive) { try { DesayunoRepository.refresh(appContext) }
                catch(e: CancellationException) { throw e } catch(e: Exception) { error(e) }; delay(10_000) }
        }
    }
    fun stopSync() { syncJob?.cancel(); syncJob=null }
    fun refresh() {
        if(!networkInitialized || currentUser.value == null) { initialize(appContext); return }
        perform { DesayunoRepository.refresh(appContext) }
    }
    private suspend fun refreshAfterWrite() {
        try { DesayunoRepository.refresh(appContext) }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { notice("Operación confirmada. No se pudo actualizar la vista; pulse Actualizar.") }
    }
    private fun cartEditable(): Boolean {
        if(_busy.value) return false
        if(pending != null) { notice("Hay un envío sin confirmar. Reintente el mismo pedido antes de modificar el carrito."); return false }
        return true
    }
    fun addToCart(item: MenuItem) {
        if(!cartEditable()) return
        val live=menu.value.find { it.id == item.id } ?: return
        if(!live.isAvailable) return
        val list=_cart.value.toMutableList(); val index=list.indexOfFirst { it.menuItem.id == item.id }
        if(index < 0 && live.availableStock > 0) list.add(CartItem(live,1))
        else if(index >= 0 && list[index].quantity < live.availableStock) list[index]=list[index].copy(quantity=list[index].quantity+1)
        _cart.value=list
    }
    fun removeFromCart(item: MenuItem) {
        if(!cartEditable()) return
        _cart.value=_cart.value.mapNotNull { if(it.menuItem.id != item.id) it else if(it.quantity > 1) it.copy(quantity=it.quantity-1) else null }
    }
    fun clearCart() { if(cartEditable()) _cart.value=emptyList() }
    fun updateCartNotes(itemId: String, notes: String) {
        if (cartEditable()) _cart.value = _cart.value.map { if (it.menuItem.id == itemId) it.copy(notes=notes.take(500)) else it }
    }
    fun placeOrder(context: Context) {
        if(_cart.value.isEmpty() || currentUser.value == null) return
        perform {
            val attempt=pending ?: PendingCheckout(UUID.randomUUID().toString(),currentUser.value!!.id,_cart.value.map { it.copy() }).also { savePending(it) }
            try {
                val order=SupabaseService.api.createOrder(attempt.requestId,attempt.items)
                savePending(null); _cart.value=emptyList()
                _orderSuccessMessage.value="Pedido #${order.orderNumber} confirmado. Total S/ ${"%.2f".format(java.util.Locale.forLanguageTag("es-PE"),order.totalAmount)}. Estimado: ${order.estimatedMinutes} min."
                refreshAfterWrite()
            } catch(e: SupabaseException) {
                // 4xx definitivos no confirmaron la operación; errores de transporte conservan la misma solicitud.
                if(e.statusCode in 400..499 && e.statusCode !in listOf(408,429)) savePending(null)
                throw e
            }
        }
    }
    fun updateOrderStatus(context: Context,id: String,status: OrderStatus,reason: String? = null) {
        perform {
            val order=SupabaseService.api.transition(id,status,reason)
            DesayunoRepository.applyConfirmedOrder(order, withPayment=false)
            notice(if(status==OrderStatus.CANCELLED) "Pedido cancelado" else "Estado del pedido actualizado")
            refreshAfterWrite()
        }
    }
    fun completeOrder(id: String, method: String? = null, reference: String? = null) {
        perform {
            val order=SupabaseService.api.completeOrder(id,method,reference)
            DesayunoRepository.applyConfirmedOrder(order)
            notice("Pedido #${order.orderNumber} completado y entregado")
            refreshAfterWrite()
        }
    }
    fun addOrUpdateMenuItem(context: Context,item: MenuItem,imageUri: Uri? = null,onSaved: () -> Unit = {}) {
        perform {
            val url=if(imageUri != null) SupabaseService.uploadImage(context,imageUri) else item.imageUrl
            SupabaseService.api.saveMenu(item.copy(imageUrl=url)); notice("Producto guardado"); onSaved(); refreshAfterWrite()
        }
    }
    fun postAnnouncement(context: Context,title: String,message: String) {
        perform { SupabaseService.api.publish(title,message); notice("Aviso publicado"); refreshAfterWrite() }
    }
    fun recordPayment(id: String,method: String,reference: String) {
        perform { SupabaseService.api.recordPayment(id,method,reference); notice("Pago confirmado"); refreshAfterWrite() }
    }
    fun setAcceptingOrders(value: Boolean) { perform { SupabaseService.api.setAccepting(value); refreshAfterWrite() } }
    fun updateProfile(name: String,phone: String) { perform {
        DesayunoRepository.setCurrentUser(SupabaseService.api.updateProfile(name,phone)); notice("Perfil actualizado")
    } }
    fun closeCashDay(date: java.time.LocalDate,total: Double) {
        perform {
            val closure=SupabaseService.api.closeCashDay(date,total)
            DesayunoRepository.applyConfirmedClosure(closure)
            notice("Caja del $date cerrada. Cobros confirmados: ${money(closure.amount)}")
        }
    }
    fun getSalesSummary(date: java.time.LocalDate = java.time.LocalDate.now(java.time.ZoneId.of("America/Lima"))) = DesayunoRepository.getSalesSummary(date)
}
