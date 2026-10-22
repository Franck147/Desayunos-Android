package com.example.desayuno.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.desayuno.model.*
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AppToolbar(title: String, subtitle: String, busy: Boolean, onRefresh: () -> Unit, onLogout: () -> Unit,
               onProfile: (() -> Unit)? = null,onNotifications: (() -> Unit)? = null,unreadCount: Int = 0) {
    var menu by remember { mutableStateOf(false) }
    Surface(color=MaterialTheme.colorScheme.surface, tonalElevation=2.dp) {
        Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal=16.dp, vertical=8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style=MaterialTheme.typography.titleLarge,
                    fontWeight=FontWeight.Bold, maxLines=1, overflow=TextOverflow.Ellipsis)
                if(onNotifications!=null) IconButton(onClick=onNotifications,modifier=Modifier.testTag("open-notifications")) {
                    BadgedBox(badge={ if(unreadCount>0) Badge { Text(if(unreadCount>99) "99+" else unreadCount.toString()) } }) {
                        Icon(Icons.Default.NotificationsNone,"Notificaciones, $unreadCount sin leer")
                    }
                }
                Box {
                    IconButton(onClick={ menu=true }) { Icon(Icons.Default.MoreVert,"Opciones de cuenta") }
                    DropdownMenu(expanded=menu,onDismissRequest={ menu=false }) {
                        if(onProfile!=null) DropdownMenuItem(text={ Text("Mi perfil") },leadingIcon={ Icon(Icons.Default.Person,null) },
                            onClick={ menu=false; onProfile() },enabled=!busy)
                        DropdownMenuItem(text={ Text("Actualizar datos") },leadingIcon={ Icon(Icons.Default.Refresh,null) },
                            onClick={ menu=false; onRefresh() },enabled=!busy)
                        DropdownMenuItem(text={ Text("Cerrar sesión") },leadingIcon={ Icon(Icons.AutoMirrored.Filled.ExitToApp,null) },
                            onClick={ menu=false; onLogout() },enabled=!busy)
                    }
                }
            }
            Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines=2,overflow=TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun InfoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    if(subtitle != null) Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun DetailValue(label: String, value: String, emphasized: Boolean = false) {
    val scale=LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if(maxWidth < 350.dp || scale > 1.2f) {
            Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(label,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value,style=if(emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                    fontWeight=FontWeight.SemiBold)
            }
        } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value,Modifier.weight(1f),textAlign=TextAlign.End,fontWeight=FontWeight.SemiBold,
                style=if(emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun StatePill(text: String, alert: Boolean = false) {
    Surface(color=if(alert) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor=if(alert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape=RoundedCornerShape(10.dp)) {
        Text(text,Modifier.padding(horizontal=10.dp,vertical=6.dp),style=MaterialTheme.typography.labelLarge)
    }
}

fun orderStatusLabel(status: OrderStatus): String = when(status) {
    OrderStatus.PENDING -> "En cola"
    OrderStatus.PREPARING -> "En preparación"
    OrderStatus.READY -> "Listo para recoger"
    OrderStatus.DELIVERED -> "Completado · entregado"
    OrderStatus.CANCELLED -> "Cancelado"
}
fun orderDate(timestamp: Long): String = Instant.ofEpochMilli(timestamp).atZone(BusinessZone)
    .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm",Locale.forLanguageTag("es-PE")))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrderBadges(order: Order) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        StatePill(orderStatusLabel(order.status),order.status==OrderStatus.CANCELLED)
        StatePill(if(order.isPaid) "Pagado · ${paymentLabel(order.paymentMethod)}" else "Pago pendiente",!order.isPaid && order.status!=OrderStatus.CANCELLED)
    }
}

@Composable
fun OrderLines(items: List<OrderItem>) {
    items.forEach { item ->
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("${item.quantity} × ${item.menuItemName}",style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.Medium)
            Text("${money(item.unitPrice)} por unidad · Subtotal ${money(item.subtotal)}",
                style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(item.notes.isNotBlank()) Text("Indicaciones: ${item.notes}",style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun EmptyContent(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(vertical=32.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        Text(message,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
