package com.example.desayuno.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.desayuno.model.MenuItem
import com.example.desayuno.model.Order
import com.example.desayuno.model.OrderStatus
import com.example.desayuno.model.ProductCategory
import com.example.desayuno.model.StockInventory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookMainScreen(
    viewModel: DesayunoViewModel
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val ordersList by viewModel.orders.collectAsState()
    val menuItems by viewModel.menu.collectAsState()
    val stockInventory by viewModel.stock.collectAsState()

    val pendingOrdersCount = remember(ordersList) {
        ordersList.count { it.status == OrderStatus.PENDING || it.status == OrderStatus.PREPARING }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "👨‍🍳 Panel de Cocina y Control",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Gestión de Comandas y Carta en Tiempo Real",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.8f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Cerrar Sesión",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Comandas") },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (pendingOrdersCount > 0) {
                                    Badge { Text("$pendingOrdersCount") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Restaurant, contentDescription = null)
                        }
                    }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Insumos y Menú") },
                    icon = { Icon(Icons.Default.Inventory, contentDescription = null) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("Reporte Ventas") },
                    icon = { Icon(Icons.Default.PointOfSale, contentDescription = null) }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> CookOrdersTab(
                    orders = ordersList,
                    onUpdateStatus = { orderId, newStatus ->
                        viewModel.updateOrderStatus(context, orderId, newStatus)
                    }
                )
                1 -> CookStockAndMenuTab(
                    stockInventory = stockInventory,
                    menuItems = menuItems,
                    onUpdateStock = { newStock -> viewModel.updateStock(context, newStock) },
                    onAddOrUpdateItem = { item, uri -> viewModel.addOrUpdateMenuItem(context, item, uri) },
                    onPostAnnouncement = { title, msg -> viewModel.postAnnouncement(context, title, msg) }
                )
                2 -> CookSalesReportTab(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun CookOrdersTab(
    orders: List<Order>,
    onUpdateStatus: (String, OrderStatus) -> Unit
) {
    var statusFilter by remember { mutableStateOf<OrderStatus?>(null) }

    val filteredOrders = remember(orders, statusFilter) {
        if (statusFilter == null) orders else orders.filter { it.status == statusFilter }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = statusFilter == null,
                onClick = { statusFilter = null },
                label = { Text("Todas") }
            )
            FilterChip(
                selected = statusFilter == OrderStatus.PENDING,
                onClick = { statusFilter = OrderStatus.PENDING },
                label = { Text("En Cola") }
            )
            FilterChip(
                selected = statusFilter == OrderStatus.PREPARING,
                onClick = { statusFilter = OrderStatus.PREPARING },
                label = { Text("En Preparación") }
            )
            FilterChip(
                selected = statusFilter == OrderStatus.READY,
                onClick = { statusFilter = OrderStatus.READY },
                label = { Text("Listas") }
            )
        }

        if (filteredOrders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No hay comandas en este estado",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(filteredOrders) { order ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Comanda #${order.orderNumber}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        text = "Cliente: ${order.customerName} (${order.customerPhone})",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                val (statusText, statusBg) = when (order.status) {
                                    OrderStatus.PENDING -> Pair("En Cola (${order.estimatedMinutes} min)", Color(0xFFFF9800))
                                    OrderStatus.PREPARING -> Pair("En Preparación 👨‍🍳", Color(0xFF2196F3))
                                    OrderStatus.READY -> Pair("¡Listo! 🔔", Color(0xFF4CAF50))
                                    OrderStatus.DELIVERED -> Pair("Entregado", Color.Gray)
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(statusBg)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = statusText,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Ítems a Preparar:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )

                            order.items.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "• ${item.quantity}x ${item.menuItemName}",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "S/ ${String.format("%.2f", item.subtotal)}",
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when (order.status) {
                                    OrderStatus.PENDING -> {
                                        Button(
                                            onClick = { onUpdateStatus(order.id, OrderStatus.PREPARING) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Iniciar Preparación")
                                        }
                                    }
                                    OrderStatus.PREPARING -> {
                                        Button(
                                            onClick = { onUpdateStatus(order.id, OrderStatus.READY) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Marcar LISTO (Notificar)")
                                        }
                                    }
                                    OrderStatus.READY -> {
                                        Button(
                                            onClick = { onUpdateStatus(order.id, OrderStatus.DELIVERED) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Entregar a Cliente")
                                        }
                                    }
                                    OrderStatus.DELIVERED -> {
                                        Text("✓ Entregado", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CookStockAndMenuTab(
    stockInventory: StockInventory,
    menuItems: List<MenuItem>,
    onUpdateStock: (StockInventory) -> Unit,
    onAddOrUpdateItem: (MenuItem, Uri?) -> Unit,
    onPostAnnouncement: (title: String, msg: String) -> Unit
) {
    var breadUnits by remember(stockInventory) { mutableIntStateOf(stockInventory.breadUnits) }
    var quinuaLiters by remember(stockInventory) { mutableIntStateOf(stockInventory.quinuaLiters) }
    var macaLiters by remember(stockInventory) { mutableIntStateOf(stockInventory.macaLiters) }
    var soyaLiters by remember(stockInventory) { mutableIntStateOf(stockInventory.soyaLiters) }

    var annTitle by remember { mutableStateOf("") }
    var annMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📦 Control de Insumos Globales (Widget)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                StockAdjusterRow("Panes Disponibles (unid):", breadUnits) { breadUnits = (breadUnits + it).coerceAtLeast(0) }
                StockAdjusterRow("Quinua Caliente (Litros):", quinuaLiters) { quinuaLiters = (quinuaLiters + it).coerceAtLeast(0) }
                StockAdjusterRow("Maca Especial (Litros):", macaLiters) { macaLiters = (macaLiters + it).coerceAtLeast(0) }
                StockAdjusterRow("Soya Caliente (Litros):", soyaLiters) { soyaLiters = (soyaLiters + it).coerceAtLeast(0) }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        onUpdateStock(
                            StockInventory(
                                breadUnits = breadUnits,
                                quinuaLiters = quinuaLiters,
                                macaLiters = macaLiters,
                                soyaLiters = soyaLiters
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Guardar Stock y Actualizar Widget")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📢 Publicar Anuncio a Clientes y Widget",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = annTitle,
                    onValueChange = { annTitle = it },
                    label = { Text("Título del Anuncio") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = annMessage,
                    onValueChange = { annMessage = it },
                    label = { Text("Mensaje / Promoción") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (annTitle.isNotBlank() && annMessage.isNotBlank()) {
                            onPostAnnouncement(annTitle, annMessage)
                            annTitle = ""
                            annMessage = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Campaign, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Publicar Anuncio En Vivo")
                }
            }
        }

        var showDialog by remember { mutableStateOf(false) }
        var editingItem by remember { mutableStateOf<MenuItem?>(null) }

        if (showDialog) {
            AddOrEditMenuItemDialog(
                itemToEdit = editingItem,
                onDismiss = {
                    showDialog = false
                    editingItem = null
                },
                onSave = { item, uri ->
                    onAddOrUpdateItem(item, uri)
                    showDialog = false
                    editingItem = null
                }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🍳 Carta & Fotos de Desayunos",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            editingItem = null
                            showDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Nuevo Platillo",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                menuItems.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.imageUrl != null) {
                                AsyncImage(
                                    model = item.imageUrl,
                                    contentDescription = item.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Fastfood,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "S/ ${String.format("%.2f", item.price)} • Stock: ${item.availableStock}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                editingItem = item
                                showDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddOrEditMenuItemDialog(
    itemToEdit: MenuItem?,
    onDismiss: () -> Unit,
    onSave: (MenuItem, Uri?) -> Unit
) {
    var name by remember { mutableStateOf(itemToEdit?.name ?: "") }
    var description by remember { mutableStateOf(itemToEdit?.description ?: "") }
    var priceText by remember { mutableStateOf(itemToEdit?.price?.toString() ?: "5.00") }
    var stockText by remember { mutableStateOf(itemToEdit?.availableStock?.toString() ?: "20") }
    var category by remember { mutableStateOf(itemToEdit?.category ?: ProductCategory.BREAD) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (itemToEdit == null) "🍳 Agregar Nuevo Desayuno" else "✏️ Editar Desayuno")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val currentDisplayImage = selectedImageUri ?: itemToEdit?.imageUrl
                    if (currentDisplayImage != null) {
                        AsyncImage(
                            model = currentDisplayImage,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Fastfood,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("Sin Imagen", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (selectedImageUri == null) "Seleccionar Foto de Galería 🖼️" else "Cambiar Foto 🖼️")
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del Platillo *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("Precio (S/)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = stockText,
                        onValueChange = { stockText = it },
                        label = { Text("Stock inicial") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = priceText.toDoubleOrNull() ?: 5.0
                    val stock = stockText.toIntOrNull() ?: 20
                    val newItem = itemToEdit?.copy(
                        name = name,
                        description = description,
                        price = price,
                        category = category,
                        availableStock = stock
                    ) ?: MenuItem(
                        name = name.ifBlank { "Desayuno Delicioso" },
                        description = description,
                        price = price,
                        category = category,
                        availableStock = stock
                    )
                    onSave(newItem, selectedImageUri)
                }
            ) {
                Text("Guardar Platillo")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun StockAdjusterRow(label: String, value: Int, onDelta: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 14.sp)

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onDelta(-1) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Remove, contentDescription = null)
            }
            Text(
                text = "$value",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = { onDelta(+1) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    }
}

@Composable
fun CookSalesReportTab(viewModel: DesayunoViewModel) {
    val (totalBreakfasts, totalRevenue) = viewModel.getSalesSummary()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "📊 Reporte Financiero & Resumen de Ventas",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.RestaurantMenu,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Desayunos Vendidos", fontSize = 12.sp, textAlign = TextAlign.Center)
                    Text(
                        text = "$totalBreakfasts unid.",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.MonetizationOn,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color(0xFF2E7D32)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Ingresos Totales", fontSize = 12.sp, textAlign = TextAlign.Center)
                    Text(
                        text = "S/ ${String.format("%.2f", totalRevenue)}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }
    }
}
