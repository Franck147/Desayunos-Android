package com.example.desayuno.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.desayuno.model.*

@Composable
fun CancelOrderButton(enabled: Boolean = true,onCancel: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    TextButton(onClick={ confirm=true },enabled=enabled,modifier=Modifier.fillMaxWidth()) { Text("Cancelar pedido") }
    if(confirm) AlertDialog(onDismissRequest={ confirm=false },title={ Text("Cancelar pedido") },
        text={ Text("El pedido saldrá de la cola y se devolverá su reserva de stock.") },
        confirmButton={ TextButton(enabled=enabled,onClick={ confirm=false; onCancel() }) { Text("Confirmar") } },
        dismissButton={ TextButton(onClick={ confirm=false }) { Text("Volver") } })
}

@Composable
fun PaymentDialog(order: Order,complete: Boolean,busy: Boolean,onDismiss: () -> Unit,onPay: (String,String) -> Unit) {
    var method by rememberSaveable(order.id) { mutableStateOf("CASH") }
    var reference by rememberSaveable(order.id) { mutableStateOf("") }
    AlertDialog(onDismissRequest={ if(!busy) onDismiss() },title={ Text(if(complete) "Cobrar y completar" else "Registrar pago") },
        text={ Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Pedido #${order.orderNumber}",style=MaterialTheme.typography.titleMedium)
            Text(order.customerName)
            Text(money(order.totalAmount),style=MaterialTheme.typography.headlineSmall)
            Text(if(complete) "Confirme que recibió el dinero y entregó el pedido al cliente." else "Registre únicamente el dinero que ya verificó.")
            listOf("CASH","YAPE","PLIN").forEach { value ->
                FilterChip(selected=method==value,onClick={ method=value },enabled=!busy,
                    label={ Text(paymentLabel(value)) },modifier=Modifier.fillMaxWidth().testTag("payment-$value"))
            }
            if(method!="CASH") OutlinedTextField(value=reference,onValueChange={ reference=it },enabled=!busy,
                label={ Text("Referencia del pago") },supportingText={ Text("Número de operación verificado en ${paymentLabel(method)}") },
                singleLine=true,modifier=Modifier.fillMaxWidth().testTag("payment-reference"))
        } },confirmButton={ TextButton(enabled=!busy && (method=="CASH" || reference.isNotBlank()),
            modifier=Modifier.testTag("confirm-payment"),onClick={ onPay(method,if(method=="CASH") "" else reference.trim()); onDismiss() }) {
            Text(if(complete) "Cobrar y entregar" else "Confirmar pago")
        } },dismissButton={ TextButton(enabled=!busy,onClick=onDismiss) { Text("Volver") } })
}

@Composable
fun OrderPaymentControls(order: Order,isAdmin: Boolean,busy: Boolean=false,onPay: (String,String,String) -> Unit) {
    var show by remember(order.id) { mutableStateOf(false) }
    if(!order.isPaid && order.status!=OrderStatus.CANCELLED && !order.isCompleted) {
        if(isAdmin && order.status!=OrderStatus.READY) OutlinedButton(onClick={ show=true },enabled=!busy,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Registrar pago") }
        else if(!isAdmin) Text("El cobro debe registrarlo administración.",style=MaterialTheme.typography.bodyMedium,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(show) PaymentDialog(order,false,busy,{ show=false }) { method,reference -> onPay(order.id,method,reference) }
}

@Composable
fun CompleteOrderButton(order: Order,isAdmin: Boolean,busy: Boolean,onComplete: (String,String?,String?) -> Unit) {
    var confirm by remember(order.id) { mutableStateOf(false) }
    if(order.status!=OrderStatus.READY) return
    Button(onClick={ confirm=true },enabled=!busy && (order.isPaid || isAdmin),
        modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("complete-order")) {
        Text(if(order.isPaid) "Entregar y completar" else "Cobrar y completar")
    }
    if(!order.isPaid && !isAdmin) Text("Pendiente de pago: solicite el cobro antes de entregar.",color=MaterialTheme.colorScheme.error)
    if(confirm) {
        if(!order.isPaid) PaymentDialog(order,true,busy,{ confirm=false }) { method,reference -> onComplete(order.id,method,reference) }
        else AlertDialog(onDismissRequest={ if(!busy) confirm=false },title={ Text("Completar pedido #${order.orderNumber}") },
            text={ Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Confirme que ${order.customerName} recibió su pedido.")
                Text("Pagado: ${money(order.paidAmount ?: order.totalAmount)} · ${paymentLabel(order.paymentMethod)}")
            } },confirmButton={ TextButton(enabled=!busy,onClick={ confirm=false; onComplete(order.id,null,null) }) { Text("Confirmar entrega") } },
            dismissButton={ TextButton(enabled=!busy,onClick={ confirm=false }) { Text("Volver") } })
    }
}

@Composable
fun ProfileDialog(name: String,phone: String,busy: Boolean,onDismiss: () -> Unit,onSave: (String,String) -> Unit) {
    var editedName by rememberSaveable(name) { mutableStateOf(name) }
    var editedPhone by rememberSaveable(phone) { mutableStateOf(phone) }
    AlertDialog(onDismissRequest=onDismiss,title={ Text("Mi perfil") },text={ Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value=editedName,onValueChange={ editedName=it },label={ Text("Nombre completo") },modifier=Modifier.fillMaxWidth(),enabled=!busy)
        OutlinedTextField(value=editedPhone,onValueChange={ editedPhone=it },label={ Text("Teléfono") },modifier=Modifier.fillMaxWidth(),enabled=!busy,
            keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Phone))
    } },confirmButton={ TextButton(enabled=!busy && editedName.isNotBlank() && editedPhone.isNotBlank(),onClick={ onSave(editedName.trim(),editedPhone.trim()); onDismiss() }) { Text("Guardar") } },
        dismissButton={ TextButton(onClick=onDismiss) { Text("Volver") } })
}
