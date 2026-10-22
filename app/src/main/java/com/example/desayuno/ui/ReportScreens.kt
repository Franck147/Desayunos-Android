package com.example.desayuno.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.desayuno.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CookSalesReportTab(viewModel: DesayunoViewModel) {
    val orders by viewModel.orders.collectAsState()
    val closures by viewModel.cashClosures.collectAsState()
    val user by viewModel.currentUser.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now(BusinessZone).toString()) }
    val date=LocalDate.parse(selectedDate)
    val report=remember(orders,date) { buildSalesReport(orders,date) }
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var export by remember { mutableStateOf<String?>(null) }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val csv=export
        if(uri!=null && csv!=null) scope.launch {
            val result=runCatching { withContext(Dispatchers.IO) {
                val stream=context.contentResolver.openOutputStream(uri) ?: error("No se pudo abrir el documento")
                stream.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
            } }
            Toast.makeText(context,if(result.isSuccess) "Reporte guardado" else "No se pudo guardar. Inténtelo nuevamente.",Toast.LENGTH_LONG).show()
        }
        export=null
    }
    ReportContent(report,closures.find { it.date==selectedDate },user?.role==UserRole.ADMIN,busy,
        { selectedDate=it.toString() },{ viewModel.closeCashDay(date,report.revenue.toDouble()) },{
            export=report.toCsv(); save.launch("Desayunos_reporte_$selectedDate.csv")
        })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportContent(report: SalesReport,closure: CashClosure?,isAdmin: Boolean,busy: Boolean,
    onDate: (LocalDate) -> Unit,onClose: () -> Unit,onExport: () -> Unit) {
    var datePicker by remember { mutableStateOf(false) }
    var close by remember { mutableStateOf(false) }
    if(datePicker) {
        val state=rememberDatePickerState(initialSelectedDateMillis=report.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest={ datePicker=false },confirmButton={ TextButton(onClick={
            state.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }; datePicker=false
        }) { Text("Ver reporte") } },dismissButton={ TextButton(onClick={ datePicker=false }) { Text("Volver") } }) {
            DatePicker(state,showModeToggle=true)
        }
    }
    if(close) AlertDialog(onDismissRequest={ close=false },title={ Text("Cerrar caja de la jornada") },
        text={ Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(report.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
            Text("Total a conciliar: ${money(report.revenue.toDouble())}")
            Text("Confirme el dinero recibido. Después del cierre no se podrán agregar más cobros a esta jornada.")
        } },confirmButton={ TextButton(onClick={ close=false; onClose() },enabled=!busy) { Text("Confirmar cierre") } },
        dismissButton={ TextButton(onClick={ close=false }) { Text("Volver") } })
    LazyColumn(Modifier.fillMaxSize().testTag("report-screen"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { SectionTitle("Resumen de la jornada","Los cobros se agrupan por su fecha de pago en Lima.") }
        item { OutlinedButton(onClick={ datePicker=true },enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
            Text(report.date.format(DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy",Locale.forLanguageTag("es-PE"))))
        } }
        item { ReportMetrics(report) }
        item { InfoCard {
            Text("Cobros por método",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            report.byMethod.forEach { (method,total) -> DetailValue(paymentLabel(method),money(total.toDouble())) }
            if(report.payments.isEmpty()) Text("No hay pagos confirmados para esta fecha.")
        } }
        item { InfoCard {
            Text("Pendientes de pago",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            Text("${report.unpaidOrders} pedidos creados en esta fecha aún no tienen un cobro registrado.")
            Text(money(report.unpaidAmount.toDouble()),style=MaterialTheme.typography.titleLarge)
            Text("Este importe no se suma a los ingresos cobrados.",style=MaterialTheme.typography.bodyMedium)
        } }
        if(isAdmin) item { InfoCard {
            Text("Cierre de caja",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            if(closure!=null) {
                StatePill("Jornada cerrada")
                DetailValue("Total conciliado",money(closure.amount),true)
                Text("${closure.paymentCount} pagos en el cierre")
            } else {
                Text("La jornada todavía no tiene un cierre registrado.")
                OutlinedButton(onClick={ close=true },enabled=!busy && !report.date.isAfter(LocalDate.now(BusinessZone)),
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("close-cash-day")) { Text("Cerrar caja") }
            }
        } }
        item { Button(onClick=onExport,enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text("Guardar reporte CSV") } }
        item { SectionTitle("Productos cobrados") }
        if(report.products.isEmpty()) item { Text("No hay productos cobrados en esta jornada.") }
        items(report.products,key={ it.id }) { product -> InfoCard {
            Text(product.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            Text("${product.units} unidades cobradas")
            Text(money(product.amount.toDouble()),style=MaterialTheme.typography.titleMedium)
        } }
        item { SectionTitle("Detalle de cobros","Pedidos cuyo pago se confirmó en esta fecha.") }
        items(report.payments,key={ it.id }) { order -> InfoCard {
            Text("Pedido #${order.orderNumber}",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            Text(order.customerName)
            Text("${paymentLabel(order.paymentMethod)} · ${orderDate(order.paidAt!!)}")
            Text(money(order.paidAmount ?: order.totalAmount),style=MaterialTheme.typography.titleLarge)
            Text(orderStatusLabel(order.status))
        } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportMetrics(report: SalesReport) {
    val scale=LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width=if(maxWidth>=500.dp && scale<=1.2f) (maxWidth-12.dp)/2 else maxWidth
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            listOf("Cobros confirmados" to money(report.revenue.toDouble()),"Pedidos cobrados" to report.payments.size.toString(),
                "Completados y entregados" to report.completedOrders.toString(),"Unidades cobradas" to report.paidUnits.toString(),
                "Cancelados" to report.cancelledOrders.toString()).forEach { (label,value) ->
                InfoCard(Modifier.width(width)) {
                    Text(label,style=MaterialTheme.typography.bodyMedium)
                    Text(value,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                }
            }
        }
    }
}
