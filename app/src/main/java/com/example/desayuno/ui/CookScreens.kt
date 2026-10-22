package com.example.desayuno.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.desayuno.model.*

@Composable
fun CookMainScreen(viewModel: DesayunoViewModel) {
    val context=LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var notifications by rememberSaveable { mutableStateOf(false) }
    var focusedOrder by rememberSaveable { mutableStateOf<String?>(null) }
    val notices by viewModel.notices.collectAsState()
    val read by viewModel.readNotices.collectAsState()
    val destination by viewModel.orderToOpen.collectAsState()
    val orders by viewModel.orders.collectAsState()
    val menu by viewModel.menu.collectAsState()
    val user by viewModel.currentUser.collectAsState()
    val accepting by viewModel.acceptingOrders.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val admin=user?.role==UserRole.ADMIN
    LaunchedEffect(destination,orders) {
        destination?.let { target ->
            if(target.userId!=user?.id) viewModel.clearOrderDestination()
            else if(orders.any { it.id==target.orderId }) {
                focusedOrder=target.orderId; tab=0
                viewModel.markNoticesRead(notices.filter { it.orderId==target.orderId }.map { it.id }.toSet())
                viewModel.clearOrderDestination()
            }
        }
    }
    if(notifications) NotificationCenterSheet(viewModel) { notifications=false }
    Scaffold(topBar={ AppToolbar(listOf("Pedidos","Carta y stock","Reportes")[tab],
        "${if(admin) "Administración" else "Cocina"} · ${user?.name.orEmpty()}",busy,viewModel::refresh,viewModel::logout,
        onNotifications={ notifications=true },unreadCount=notices.count { it.id !in read }) },bottomBar={
        NavigationBar {
            listOf("Pedidos" to Icons.Default.Restaurant,"Carta" to Icons.Default.Inventory2,"Reportes" to Icons.Default.BarChart).forEachIndexed { index,(label,icon) ->
                NavigationBarItem(selected=tab==index,onClick={ tab=index },label={ Text(label) },icon={
                    BadgedBox(badge={ if(index==0 && orders.any { it.isActive }) Badge { Text(orders.count { it.isActive }.toString()) } }) { Icon(icon,null) }
                })
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when(tab) {
                0 -> CookOrdersTab(orders,admin,busy,viewModel::recordPayment,viewModel::completeOrder,
                    { viewModel.updateOrderStatus(context,it,OrderStatus.CANCELLED,"Cancelado por personal antes de preparar") },
                    { id,status -> viewModel.updateOrderStatus(context,id,status) },focusedOrder,{ focusedOrder=null })
                1 -> CookStockAndMenuTab(menu,admin,accepting,busy,viewModel::setAcceptingOrders,
                    { item,uri,saved -> viewModel.addOrUpdateMenuItem(context,item,uri,saved) },
                    { title,message -> viewModel.postAnnouncement(context,title,message) })
                2 -> CookSalesReportTab(viewModel)
            }
        }
    }
}

@Composable
fun CookOrdersTab(orders: List<Order>,isAdmin: Boolean,busy: Boolean,
    onPay: (String,String,String) -> Unit,onComplete: (String,String?,String?) -> Unit,
    onCancel: (String) -> Unit,onUpdateStatus: (String,OrderStatus) -> Unit,focusOrderId: String?=null,onFocused: () -> Unit={}) {
    var filter by rememberSaveable { mutableStateOf("ACTIVE") }
    var search by rememberSaveable { mutableStateOf("") }
    val list=rememberLazyListState()
    LaunchedEffect(focusOrderId) {
        orders.find { it.id==focusOrderId }?.let {
            filter="ALL"; search=it.orderNumber.toString(); list.scrollToItem(0); onFocused()
        }
    }
    val filters=listOf("ACTIVE" to "En atención","PENDING" to "En cola","PREPARING" to "Preparando",
        "READY" to "Listos","UNPAID" to "Por cobrar","DELIVERED" to "Completados","CANCELLED" to "Cancelados","ALL" to "Todos")
    val visible=orders.filter { order ->
        val matches=when(filter) {
            "ACTIVE" -> order.isActive
            "UNPAID" -> !order.isPaid && order.status!=OrderStatus.CANCELLED
            "ALL" -> true
            else -> order.status.name==filter
        }
        matches && (search.isBlank() || order.orderNumber.toString().contains(search) || order.customerName.contains(search,true) || order.customerPhone.contains(search))
    }.let { if(filter in listOf("ACTIVE","PENDING","PREPARING","READY")) it.sortedBy { order -> order.timestamp } else it.sortedByDescending { order -> order.updatedAt } }
    LazyColumn(Modifier.fillMaxSize(),state=list,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { SectionTitle("Atención de pedidos","${orders.count { it.isActive }} en atención · ${orders.count { it.isCompleted }} completados") }
        item { OutlinedTextField(search,{ search=it },modifier=Modifier.fillMaxWidth(),label={ Text("Número, cliente o teléfono") },singleLine=true) }
        item { LazyRow(Modifier.testTag("staff-order-filters"),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(filters) { (value,label) -> FilterChip(filter==value,{ filter=value },label={ Text(label) },modifier=Modifier.testTag("filter-$value")) }
        } }
        if(visible.isEmpty()) item { EmptyContent("No hay pedidos en esta vista","Cambia el filtro o actualiza los datos.") }
        items(visible,key={ it.id }) { order -> CookOrderCard(order,isAdmin,busy,onPay,onComplete,{ onCancel(order.id) },onUpdateStatus) }
    }
}

@Composable
fun CookOrderCard(order: Order,isAdmin: Boolean,busy: Boolean,onPay: (String,String,String) -> Unit,
    onComplete: (String,String?,String?) -> Unit,onCancel: () -> Unit,onUpdateStatus: (String,OrderStatus) -> Unit) {
    InfoCard(Modifier.testTag("staff-order-${order.id}")) {
        Text("Pedido #${order.orderNumber}",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Text(order.customerName,style=MaterialTheme.typography.titleMedium)
        Text("Teléfono: ${order.customerPhone}",style=MaterialTheme.typography.bodyMedium)
        Text(orderDate(order.timestamp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OrderBadges(order)
        HorizontalDivider()
        OrderLines(order.items)
        HorizontalDivider()
        DetailValue("Total del pedido",money(order.totalAmount),true)
        if(order.isPaid) Text("Cobrado: ${money(order.paidAmount ?: order.totalAmount)} por ${paymentLabel(order.paymentMethod)}")
        OrderPaymentControls(order,isAdmin,busy,onPay)
        when(order.status) {
            OrderStatus.PENDING -> {
                Button(onClick={ onUpdateStatus(order.id,OrderStatus.PREPARING) },enabled=!busy,
                    modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text("Iniciar preparación") }
                if(!order.isPaid) CancelOrderButton(!busy,onCancel)
            }
            OrderStatus.PREPARING -> Button(onClick={ onUpdateStatus(order.id,OrderStatus.READY) },enabled=!busy,
                modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text("Marcar como listo") }
            OrderStatus.READY -> CompleteOrderButton(order,isAdmin,busy,onComplete)
            OrderStatus.DELIVERED -> Text("Pedido completado y entregado al cliente.",style=MaterialTheme.typography.bodyLarge)
            OrderStatus.CANCELLED -> Text("Pedido cancelado. No requiere preparación.")
        }
    }
}

@Composable
fun CookStockAndMenuTab(menuItems: List<MenuItem>,canManage: Boolean,acceptingOrders: Boolean,busy: Boolean,
    onSetAccepting: (Boolean) -> Unit,onSave: (MenuItem,Uri?,() -> Unit) -> Unit,onPost: (String,String) -> Unit) {
    var editing by remember { mutableStateOf<MenuItem?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var showAnnouncement by rememberSaveable { mutableStateOf(false) }
    if(showEditor) AddOrEditMenuItemDialog(editing,busy,{ showEditor=false }) { item,uri -> onSave(item,uri) { showEditor=false } }
    if(showAnnouncement) AnnouncementDialog(busy,{ showAnnouncement=false },onPost)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { SectionTitle("Carta y disponibilidad","${menuItems.count { it.isAvailable }} productos habilitados") }
        if(canManage) item {
            InfoCard {
                StatePill(if(acceptingOrders) "Recepción abierta" else "Recepción cerrada",!acceptingOrders)
                OutlinedButton(onClick={ onSetAccepting(!acceptingOrders) },enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                    Text(if(acceptingOrders) "Cerrar recepción" else "Abrir recepción")
                }
                Button(onClick={ editing=null; showEditor=true },enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Agregar producto") }
                TextButton(onClick={ showAnnouncement=true },enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("Publicar un aviso") }
            }
        }
        if(menuItems.isEmpty()) item { EmptyContent("La carta está vacía",if(canManage) "Agrega el primer producto para recibir pedidos." else "Administración debe cargar los productos.") }
        items(menuItems,key={ it.id }) { item -> InfoCard {
            Text(item.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            DetailValue("Precio",money(item.price))
            StatePill(if(!item.isAvailable) "No disponible" else if(item.availableStock<=0) "Agotado" else "${item.availableStock} unidades disponibles",
                !item.isAvailable || item.availableStock<=0)
            if(item.availableStock in 1..5) Text("Quedan pocas unidades.",style=MaterialTheme.typography.bodyMedium)
            if(canManage) OutlinedButton(onClick={ editing=item; showEditor=true },enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("Editar producto y stock") }
        } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddOrEditMenuItemDialog(itemToEdit: MenuItem?,busy: Boolean,onDismiss: () -> Unit,onSave: (MenuItem,Uri?) -> Unit) {
    var name by rememberSaveable(itemToEdit?.id) { mutableStateOf(itemToEdit?.name.orEmpty()) }
    var description by rememberSaveable(itemToEdit?.id) { mutableStateOf(itemToEdit?.description.orEmpty()) }
    var price by rememberSaveable(itemToEdit?.id) { mutableStateOf(itemToEdit?.price?.toString().orEmpty()) }
    var stock by rememberSaveable(itemToEdit?.id) { mutableStateOf(itemToEdit?.availableStock?.toString() ?: "0") }
    var category by rememberSaveable(itemToEdit?.id) { mutableStateOf(itemToEdit?.category ?: ProductCategory.BREAD) }
    var available by rememberSaveable(itemToEdit?.id) { mutableStateOf(itemToEdit?.isAvailable ?: true) }
    var image by rememberSaveable(itemToEdit?.id) { mutableStateOf<String?>(null) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { image=it.toString() } }
    val amount=price.replace(',','.').toBigDecimalOrNull()
    val valid=name.isNotBlank() && amount!=null && amount.signum()>=0 && amount.stripTrailingZeros().scale()<=2 && stock.toIntOrNull()?.let { it>=0 }==true
    AlertDialog(onDismissRequest={ if(!busy) onDismiss() },title={ Text(if(itemToEdit==null) "Nuevo producto" else "Editar producto") },text={
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            val picture=image ?: itemToEdit?.imageUrl
            if(picture!=null) AsyncImage(picture,"Imagen del producto",Modifier.fillMaxWidth().height(120.dp),contentScale=ContentScale.Crop)
            OutlinedButton(onClick={ picker.launch("image/*") },enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("Seleccionar imagen") }
            OutlinedTextField(name,{ name=it },enabled=!busy,label={ Text("Nombre") },modifier=Modifier.fillMaxWidth())
            OutlinedTextField(description,{ description=it },enabled=!busy,label={ Text("Descripción") },modifier=Modifier.fillMaxWidth())
            Text("Categoría",style=MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                ProductCategory.entries.forEach { c -> FilterChip(category==c,{ category=c },enabled=!busy,label={ Text(when(c) {
                    ProductCategory.BREAD -> "Panes"; ProductCategory.BEVERAGE -> "Bebidas"; ProductCategory.EXTRA -> "Extras"; ProductCategory.COMBO -> "Combos"
                }) }) }
            }
            OutlinedTextField(price,{ price=it },enabled=!busy,label={ Text("Precio en soles") },supportingText={ Text("Máximo dos decimales") },
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(stock,{ stock=it },enabled=!busy,label={ Text("Unidades disponibles") },
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth())
            Text("Disponibilidad",style=MaterialTheme.typography.labelLarge)
            Switch(available,{ available=it },enabled=!busy)
            Text(if(available) "Se ofrece en la carta" else "Oculto para nuevos pedidos")
        }
    },confirmButton={ TextButton(enabled=valid && !busy,onClick={
        val result=itemToEdit?.copy(name=name.trim(),description=description,price=amount!!.toDouble(),category=category,availableStock=stock.toInt(),isAvailable=available)
            ?: MenuItem(name=name.trim(),description=description,price=amount!!.toDouble(),category=category,availableStock=stock.toInt(),isAvailable=available)
        onSave(result,image?.let(Uri::parse))
    }) { Text(if(busy) "Guardando…" else "Guardar") } },dismissButton={ TextButton(onClick=onDismiss,enabled=!busy) { Text("Volver") } })
}

@Composable
private fun AnnouncementDialog(busy: Boolean,onDismiss: () -> Unit,onPost: (String,String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={ Text("Publicar aviso") },text={
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title,{ title=it.take(150) },label={ Text("Título") },modifier=Modifier.fillMaxWidth(),enabled=!busy)
            OutlinedTextField(message,{ message=it.take(2000) },label={ Text("Mensaje") },modifier=Modifier.fillMaxWidth(),enabled=!busy)
        }
    },confirmButton={ TextButton(enabled=!busy && title.isNotBlank() && message.isNotBlank(),onClick={ onPost(title.trim(),message.trim()); onDismiss() }) { Text("Publicar") } },
        dismissButton={ TextButton(onClick=onDismiss) { Text("Volver") } })
}
