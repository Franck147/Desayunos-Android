package com.example.desayuno.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.desayuno.model.*

@Composable
fun ClientMainScreen(viewModel: DesayunoViewModel) {
    val context=LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var profile by rememberSaveable { mutableStateOf(false) }
    var notifications by rememberSaveable { mutableStateOf(false) }
    var focusedOrder by rememberSaveable { mutableStateOf<String?>(null) }
    val notices by viewModel.notices.collectAsState()
    val read by viewModel.readNotices.collectAsState()
    val destination by viewModel.orderToOpen.collectAsState()
    val user by viewModel.currentUser.collectAsState()
    val menu by viewModel.menu.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val allOrders by viewModel.orders.collectAsState()
    val announcements by viewModel.announcements.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val pending by viewModel.pendingCheckout.collectAsState()
    val accepting by viewModel.acceptingOrders.collectAsState()
    val success by viewModel.orderSuccessMessage.collectAsState()
    val snackbar=remember { SnackbarHostState() }
    val mine=allOrders.filter { it.customerId==user?.id }
    LaunchedEffect(destination,mine) {
        destination?.let { target ->
            if(target.userId!=user?.id) viewModel.clearOrderDestination()
            else if(mine.any { it.id==target.orderId }) {
                focusedOrder=target.orderId; tab=2
                viewModel.markNoticesRead(notices.filter { it.orderId==target.orderId }.map { it.id }.toSet())
                viewModel.clearOrderDestination()
            }
        }
    }
    LaunchedEffect(success) { success?.let { tab=2; snackbar.showSnackbar(it); viewModel.clearSuccessMessage() } }
    if(profile) ProfileDialog(user?.name.orEmpty(),user?.phone.orEmpty(),busy,{ profile=false },viewModel::updateProfile)
    if(notifications) NotificationCenterSheet(viewModel) { notifications=false }
    Scaffold(topBar={ AppToolbar(listOf("Desayunos","Mi carrito","Mis pedidos")[tab],user?.name.orEmpty(),busy,
        viewModel::refresh,viewModel::logout,{ profile=true },{ notifications=true },notices.count { it.id !in read }) },
        snackbarHost={ SnackbarHost(snackbar) },bottomBar={
            NavigationBar {
                listOf("Carta" to Icons.Default.RestaurantMenu,"Carrito" to Icons.Default.ShoppingCart,"Pedidos" to Icons.Default.ReceiptLong).forEachIndexed { index,(label,icon) ->
                    NavigationBarItem(selected=tab==index,onClick={ tab=index },icon={
                        BadgedBox(badge={ if(index==1 && cart.isNotEmpty()) Badge { Text(cart.sumOf { it.quantity }.toString()) } }) {
                            Icon(icon,null)
                        }
                    },label={ Text(label) })
                }
            }
        }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when(tab) {
                0 -> ClientMenuTab(menu,announcements,cart,accepting,busy,viewModel::addToCart) { tab=1 }
                1 -> ClientCartTab(cart,busy,pending,accepting,{ viewModel.addToCart(it.menuItem) },
                    { viewModel.removeFromCart(it.menuItem) },viewModel::updateCartNotes,viewModel::clearCart) { viewModel.placeOrder(context) }
                2 -> ClientOrdersTab(mine,busy,focusedOrder,{ focusedOrder=null }) { viewModel.updateOrderStatus(context,it,OrderStatus.CANCELLED,"Cancelado por el cliente antes de preparar") }
            }
        }
    }
}

private fun categoryLabel(category: ProductCategory) = when(category) {
    ProductCategory.BEVERAGE -> "Bebidas"
    ProductCategory.BREAD -> "Panes"
    ProductCategory.EXTRA -> "Extras"
    ProductCategory.COMBO -> "Combos"
}

@Composable
fun ClientMenuTab(menuItems: List<MenuItem>,announcements: List<Announcement>,cartItems: List<CartItem>,
                  accepting: Boolean,busy: Boolean,onAdd: (MenuItem) -> Unit,onCart: () -> Unit) {
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    val visible=menuItems.filter { it.isAvailable && (category==null || it.category.name==category) && it.name.contains(search,true) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {
            SectionTitle("Elige tu desayuno",if(accepting) "Pedidos abiertos para recoger en el local" else "La recepción de pedidos está cerrada")
        }
        if(cartItems.isNotEmpty()) item {
            Button(onClick=onCart,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                Text("Ver carrito · ${cartItems.sumOf { it.quantity }} unidades")
            }
        }
        announcements.firstOrNull()?.let { announcement -> item { InfoCard {
            Text(announcement.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            Text(announcement.message,style=MaterialTheme.typography.bodyMedium)
        } } }
        item { OutlinedTextField(search,{ search=it },label={ Text("Buscar un producto") },modifier=Modifier.fillMaxWidth(),singleLine=true) }
        item {
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected=category==null,onClick={ category=null },label={ Text("Todos") }) }
                items(ProductCategory.entries) { c -> FilterChip(selected=category==c.name,onClick={ category=c.name },label={ Text(categoryLabel(c)) }) }
            }
        }
        if(visible.isEmpty()) item { EmptyContent("Sin productos para mostrar","Actualiza la carta o cambia los filtros.") }
        items(visible,key={ it.id }) { item ->
            InfoCard {
                item.imageUrl?.let { url -> AsyncImage(url,item.name,Modifier.fillMaxWidth().height(140.dp),contentScale=ContentScale.Crop) }
                Text(item.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                if(item.description.isNotBlank()) Text(item.description,style=MaterialTheme.typography.bodyMedium)
                DetailValue("Precio por unidad",money(item.price),true)
                Text(if(item.availableStock>0) "Disponibles: ${item.availableStock}" else "Agotado",style=MaterialTheme.typography.bodyMedium)
                val quantity=cartItems.find { it.menuItem.id==item.id }?.quantity ?: 0
                Button(onClick={ onAdd(item) },enabled=!busy && accepting && quantity<item.availableStock,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                    Text(if(quantity>0) "Agregar otra unidad · $quantity en carrito" else "Agregar al carrito")
                }
            }
        }
    }
}

@Composable
fun ClientCartTab(cartItems: List<CartItem>,busy: Boolean,pending: Boolean,accepting: Boolean,
    onAdd: (CartItem) -> Unit,onRemove: (CartItem) -> Unit,onNotes: (String,String) -> Unit,
    onClear: () -> Unit,onConfirm: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { SectionTitle("Revisa tu pedido","El precio y la disponibilidad se confirman al enviar.") }
        if(cartItems.isEmpty()) item { EmptyContent("Tu carrito está vacío","Agrega productos desde la carta para comenzar.") }
        items(cartItems,key={ it.menuItem.id }) { item ->
            InfoCard {
                Text(item.menuItem.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Text("${money(item.menuItem.price)} por unidad")
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedIconButton(onClick={ onRemove(item) },enabled=!busy && !pending) { Icon(Icons.Default.Remove,"Restar unidad") }
                    Text(item.quantity.toString(),style=MaterialTheme.typography.titleLarge)
                    OutlinedIconButton(onClick={ onAdd(item) },enabled=!busy && !pending) { Icon(Icons.Default.Add,"Agregar unidad") }
                }
                DetailValue("Subtotal",money(item.menuItem.price*item.quantity),true)
                OutlinedTextField(item.notes,{ onNotes(item.menuItem.id,it) },label={ Text("Indicaciones (opcional)") },
                    enabled=!busy && !pending,modifier=Modifier.fillMaxWidth(),supportingText={ Text("${item.notes.length}/500 caracteres") })
            }
        }
        if(cartItems.isNotEmpty()) item {
            InfoCard {
                DetailValue("Total estimado",money(cartItems.sumOf { it.menuItem.price*it.quantity }),true)
                if(pending) Text("Tu envío quedó sin confirmar. Reintenta para conocer su resultado sin duplicarlo.")
                else if(!accepting) Text("La recepción de pedidos está cerrada.",color=MaterialTheme.colorScheme.error)
                Button(onClick=onConfirm,enabled=!busy && (pending || accepting),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                    Text(if(busy) "Procesando…" else if(pending) "Reintentar confirmación" else "Confirmar pedido")
                }
                TextButton(onClick=onClear,enabled=!busy && !pending,modifier=Modifier.fillMaxWidth()) { Text("Vaciar carrito") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClientOrdersTab(myOrders: List<Order>,busy: Boolean=false,focusOrderId: String?=null,onFocused: () -> Unit={},onCancel: (String) -> Unit) {
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    val filtered=myOrders.filter { if(showCompleted) !it.isActive else it.isActive }.sortedByDescending { it.timestamp }
    val list=rememberLazyListState()
    LaunchedEffect(focusOrderId,filtered.map { it.id }) {
        val target=myOrders.find { it.id==focusOrderId } ?: return@LaunchedEffect
        showCompleted=!target.isActive
        val index=filtered.indexOfFirst { it.id==target.id }
        if(index>=0) { list.scrollToItem(index+2); onFocused() }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("client-orders"),state=list,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { SectionTitle("Mis pedidos","Sigue tu atención y consulta tus compras anteriores.") }
        item {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                FilterChip(!showCompleted,{ showCompleted=false },label={ Text("En atención (${myOrders.count { it.isActive }})") })
                FilterChip(showCompleted,{ showCompleted=true },label={ Text("Historial (${myOrders.count { !it.isActive }})") },modifier=Modifier.testTag("client-history"))
            }
        }
        if(filtered.isEmpty()) item { EmptyContent(if(showCompleted) "Sin pedidos anteriores" else "No tienes pedidos en atención",
            if(showCompleted) "Los pedidos completados y cancelados aparecerán aquí." else "Puedes hacer un pedido desde la carta o consultar tu historial.") }
        items(filtered,key={ it.id }) { order -> ClientOrderCard(order,busy) { onCancel(order.id) } }
    }
}

@Composable
fun ClientOrderCard(order: Order,busy: Boolean=false,onCancel: () -> Unit) {
    InfoCard(Modifier.testTag("client-order-${order.id}")) {
        Text("Pedido #${order.orderNumber}",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Text(orderDate(order.timestamp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OrderBadges(order)
        if(order.status==OrderStatus.READY) Text("Tu desayuno está listo. Acércate al local para recogerlo.",style=MaterialTheme.typography.bodyLarge)
        if(order.isCompleted) Text("Entrega completada. Gracias por tu compra.",style=MaterialTheme.typography.bodyLarge)
        if(order.status==OrderStatus.PENDING || order.status==OrderStatus.PREPARING) Text("Tiempo orientativo: ${order.estimatedMinutes} minutos. Puede variar según la cola.")
        HorizontalDivider()
        OrderLines(order.items)
        HorizontalDivider()
        DetailValue("Total del pedido",money(order.totalAmount),true)
        if(order.isPaid) {
            Text("Cobro confirmado por ${paymentLabel(order.paymentMethod)}",style=MaterialTheme.typography.bodyMedium)
        } else if(order.status!=OrderStatus.CANCELLED) Text("Paga en caja antes de recoger tu pedido.",style=MaterialTheme.typography.bodyMedium)
        if(order.status==OrderStatus.PENDING && !order.isPaid) CancelOrderButton(enabled=!busy,onCancel=onCancel)
    }
}
