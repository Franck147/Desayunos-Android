package com.example.desayuno.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.desayuno.notification.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterSheet(viewModel: DesayunoViewModel,onDismiss: () -> Unit) {
    val context=LocalContext.current
    val notices by viewModel.notices.collectAsState()
    val read by viewModel.readNotices.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var enabled by remember { mutableStateOf(NotificationHelper.enabled(context)) }
    val prefs=remember { context.getSharedPreferences("notice_permission",0) }
    val request=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        enabled=NotificationHelper.enabled(context)
        if(enabled) viewModel.deliverReadyAlerts()
    }
    LifecycleResumeEffect(Unit) {
        enabled=NotificationHelper.enabled(context)
        if(enabled) viewModel.deliverReadyAlerts()
        onPauseOrDispose { }
    }
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=MaterialTheme.colorScheme.background) {
        NotificationCenter(notices,read,enabled,busy,viewModel::markNoticesRead,{ id ->
            viewModel.currentUser.value?.let { viewModel.requestOrderOpen(id,it.id) }; onDismiss()
        },{
            if(Build.VERSION.SDK_INT>=33 && !prefs.getBoolean("asked",false) &&
                androidx.core.content.ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
                prefs.edit().putBoolean("asked",true).apply()
                request.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                val intent=Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)
                context.startActivity(intent)
            }
        },viewModel::refresh,onDismiss,Modifier.fillMaxHeight(0.94f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationCenter(notices: List<AppNotice>,readIds: Set<String>,enabled: Boolean,busy: Boolean,
    onRead: (Set<String>) -> Unit,onOpenOrder: (String) -> Unit,onEnable: () -> Unit,
    onRefresh: () -> Unit,onClose: () -> Unit,modifier: Modifier=Modifier) {
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    val unread=notices.count { it.id !in readIds }
    val visible=if(unreadOnly) notices.filter { it.id !in readIds } else notices
    Column(modifier.fillMaxWidth().testTag("notification-center")) {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp)) {
            Text("Notificaciones",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Últimas actualizaciones",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick=onRefresh,enabled=!busy) { Icon(Icons.Default.Refresh,"Actualizar avisos") }
                IconButton(onClick=onClose) { Icon(Icons.Default.Close,"Cerrar notificaciones") }
            }
        }
        LazyColumn(Modifier.weight(1f).testTag("notice-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item {
                Text("Todo a su tiempo.",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                Text(if(unread==0) "Estás al día con tus actualizaciones." else "$unread actualizaciones por leer.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Icon(if(enabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,null,tint=MaterialTheme.colorScheme.secondary)
                        Text(if(enabled) "Avisos del teléfono activados" else "Que no se enfríe tu desayuno",fontWeight=FontWeight.Bold)
                        Text(if(enabled) "Verás avisos cuando la app sincronice cambios en tus pedidos." else "Activa los avisos del teléfono para enterarte cuando tu pedido esté listo.",style=MaterialTheme.typography.bodyMedium)
                        if(!enabled) Button(onClick=onEnable,modifier=Modifier.fillMaxWidth().testTag("enable-notifications")) { Text("Activar notificaciones") }
                        Text("Los pedidos se actualizan con la app abierta. Puedes consultar aquí los avisos aunque no concedas el permiso.",style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                FilterChip(!unreadOnly,{ unreadOnly=false },label={ Text("Todas") })
                FilterChip(unreadOnly,{ unreadOnly=true },label={ Text("Sin leer ($unread)") },modifier=Modifier.testTag("filter-unread"))
                if(unread>0) TextButton(onClick={ onRead(notices.map { it.id }.toSet()) },modifier=Modifier.testTag("read-all")) { Text("Marcar todas como leídas") }
            } }
            if(visible.isEmpty()) item { EmptyContent(if(unreadOnly) "Todo leído" else "Aquí llegan las novedades",
                if(unreadOnly) "Puedes volver a Todas para consultar tus avisos." else "Cuando hagas un pedido o publiquemos un aviso, lo encontrarás aquí.") }
            items(visible,key={ it.id }) { notice ->
                val isUnread=notice.id !in readIds
                OutlinedCard(onClick={ onRead(setOf(notice.id)); notice.orderId?.let(onOpenOrder) },
                    modifier=Modifier.fillMaxWidth().testTag("notice-${notice.id}"),shape=RoundedCornerShape(22.dp),
                    colors=CardDefaults.outlinedCardColors(containerColor=if(isUnread) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.35f))) {
                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                            Surface(color=if(notice.ready) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(14.dp)) {
                                Icon(if(notice.ready) Icons.Default.TaskAlt else if(notice.kind==NoticeKind.ANNOUNCEMENT) Icons.Default.Campaign else Icons.Default.ReceiptLong,
                                    null,Modifier.padding(10.dp).size(22.dp))
                            }
                            if(isUnread) StatePill("Sin leer")
                        }
                        Text(notice.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        Text(notice.message,style=MaterialTheme.typography.bodyMedium)
                        Text(orderDate(notice.timestamp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(notice.orderId!=null) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("Ver pedido",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold)
                            Icon(Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
