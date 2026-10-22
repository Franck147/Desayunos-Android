package com.example.desayuno.notification

import com.example.desayuno.model.*

enum class NoticeKind { ORDER, ANNOUNCEMENT }
data class AppNotice(val id: String,val title: String,val message: String,val timestamp: Long,
    val kind: NoticeKind,val orderId: String? = null,val ready: Boolean = false)

/** A current update per order, not a fabricated status history. */
fun orderNotice(user: User, order: Order): AppNotice {
    val client = user.role == UserRole.CLIENT
    val title = when(order.status) {
        OrderStatus.PENDING -> if(client) "Recibimos tu pedido" else "Nuevo pedido por preparar"
        OrderStatus.PREPARING -> if(client) "Estamos preparando tu desayuno" else "Pedido en preparación"
        OrderStatus.READY -> if(client) "Tu desayuno está listo" else "Pedido listo para entregar"
        OrderStatus.DELIVERED -> "Entrega completada"
        OrderStatus.CANCELLED -> "Pedido cancelado"
    }
    val message = "Pedido #${order.orderNumber} · " + when(order.status) {
        OrderStatus.PENDING -> if(client) "Ya está en la cola de atención." else "${order.customerName}. Revisa la comanda."
        OrderStatus.PREPARING -> if(client) "Cocina está trabajando en tu pedido." else "${order.customerName}. Preparación en curso."
        OrderStatus.READY -> if(client) "Acércate al local para recogerlo." else if(order.isPaid) "Pago confirmado. Puedes completar la entrega." else "Falta confirmar el pago antes de entregar."
        OrderStatus.DELIVERED -> if(client) "Gracias por elegirnos. ¡Buen provecho!" else "Pedido entregado al cliente."
        OrderStatus.CANCELLED -> "Puedes consultar su detalle en el historial."
    }
    return AppNotice("order:${order.id}:${order.status.name}",title,message,order.updatedAt,
        NoticeKind.ORDER,order.id,order.status == OrderStatus.READY)
}

fun buildNotices(user: User, orders: List<Order>, announcements: List<Announcement>): List<AppNotice> {
    val accessible = if(user.role == UserRole.CLIENT) orders.filter { it.customerId == user.id } else orders
    return (accessible.map { orderNotice(user,it) } + announcements.map {
        AppNotice("announcement:${it.id}:${it.timestamp}",it.title,it.message,it.timestamp,NoticeKind.ANNOUNCEMENT)
    }).sortedByDescending { it.timestamp }.take(100)
}

/** The first staff snapshot must not alert all past orders. */
fun orderAlerts(user: User, previous: List<Order>, current: List<Order>, hasSnapshot: Boolean): List<AppNotice> {
    val old = previous.associateBy { it.id }
    return current.filter { order ->
        if(user.role == UserRole.CLIENT) order.customerId == user.id &&
            (order.status == OrderStatus.READY && old[order.id]?.status != order.status ||
                old[order.id] != null && old[order.id]?.status != order.status && order.status in setOf(OrderStatus.PREPARING,OrderStatus.DELIVERED,OrderStatus.CANCELLED))
        else hasSnapshot && order.id !in old && order.status == OrderStatus.PENDING
    }.map { orderNotice(user,it) }
}
