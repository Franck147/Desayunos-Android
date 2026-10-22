package com.example.desayuno.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

val BusinessZone: ZoneId = ZoneId.of("America/Lima")
fun businessDate(timestamp: Long): LocalDate = Instant.ofEpochMilli(timestamp).atZone(BusinessZone).toLocalDate()
fun money(amount: Double): String = "S/ " + String.format(Locale.forLanguageTag("es-PE"), "%,.2f", amount)
fun paymentLabel(method: String?): String = when (method) {
    "CASH" -> "Efectivo"
    "YAPE" -> "Yape"
    "PLIN" -> "Plin"
    null -> "Por cobrar"
    else -> method
}
val Order.isCompleted: Boolean get() = status == OrderStatus.DELIVERED
val Order.isPaid: Boolean get() = paymentMethod != null && paidAt != null
val Order.isActive: Boolean get() = status in setOf(OrderStatus.PENDING, OrderStatus.PREPARING, OrderStatus.READY)

data class ProductSale(val id: String, val name: String, val units: Int, val amount: BigDecimal)
data class SalesReport(
    val date: LocalDate,
    val payments: List<Order>,
    val revenue: BigDecimal,
    val paidUnits: Int,
    val completedOrders: Int,
    val cancelledOrders: Int,
    val unpaidOrders: Int,
    val unpaidAmount: BigDecimal,
    val byMethod: Map<String, BigDecimal>,
    val products: List<ProductSale>
)

fun buildSalesReport(orders: List<Order>, date: LocalDate): SalesReport {
    val paid = orders.filter { it.isPaid && businessDate(it.paidAt!!) == date }.sortedByDescending { it.paidAt }
    val created = orders.filter { businessDate(it.timestamp) == date }
    val unpaid = created.filter { !it.isPaid && it.status != OrderStatus.CANCELLED }
    fun sum(rows: List<Order>) = rows.fold(BigDecimal.ZERO) { total, order -> total + BigDecimal.valueOf(order.paidAmount ?: order.totalAmount) }
    val products = paid.flatMap { it.items }.groupBy { it.menuItemId.ifBlank { it.menuItemName } }.map { (id, items) ->
        ProductSale(id, items.first().menuItemName, items.sumOf { it.quantity }, items.fold(BigDecimal.ZERO) { total, item ->
            total + BigDecimal.valueOf(item.unitPrice).multiply(BigDecimal.valueOf(item.quantity.toLong()))
        })
    }.sortedByDescending { it.units }
    return SalesReport(
        date, paid, sum(paid), paid.sumOf { it.items.sumOf { item -> item.quantity } },
        orders.count { it.isCompleted && businessDate(it.updatedAt) == date },
        orders.count { it.status == OrderStatus.CANCELLED && businessDate(it.updatedAt) == date },
        unpaid.size, unpaid.fold(BigDecimal.ZERO) { total, order -> total + BigDecimal.valueOf(order.totalAmount) },
        listOf("CASH", "YAPE", "PLIN").associateWith { method -> sum(paid.filter { it.paymentMethod == method }) }, products
    )
}

/** CSV compatible con Excel; protege los nombres de fórmulas al abrir la hoja. */
fun SalesReport.toCsv(): String {
    fun cell(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
    val lines = mutableListOf(
        listOf("Reporte de desayunos", date.toString()),
        listOf("Zona horaria", "America/Lima"),
        listOf("Cobros confirmados PEN", revenue.toPlainString()),
        listOf("Pedidos cobrados", payments.size.toString()),
        listOf("Pedidos completados", completedOrders.toString()),
        listOf("Unidades cobradas", paidUnits.toString()),
        listOf("Cancelados", cancelledOrders.toString()),
        listOf("Pedido", "Cliente", "Método", "Importe PEN", "Estado")
    )
    payments.forEach { lines.add(listOf(it.orderNumber.toString(), it.customerName, paymentLabel(it.paymentMethod),
        BigDecimal.valueOf(it.paidAmount ?: it.totalAmount).toPlainString(), it.status.displayName)) }
    lines.add(listOf("Producto", "Unidades cobradas", "Importe PEN"))
    products.forEach { lines.add(listOf(it.name, it.units.toString(), it.amount.toPlainString())) }
    return "\uFEFF" + lines.joinToString("\r\n") { row -> row.joinToString(";") { cell(it) } } + "\r\n"
}
