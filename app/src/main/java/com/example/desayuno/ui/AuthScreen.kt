package com.example.desayuno.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AuthScreen(viewModel: DesayunoViewModel) {
    val busy by viewModel.busy.collectAsState()
    LoginForm(busy=busy,onLogin={ email,password,staff,result ->
        if(staff) viewModel.loginAdminWithSupabase(email,password,result)
        else viewModel.loginClientWithSupabase(email,password,result)
    },onRegister=viewModel::registerClientWithSupabase)
}

@Composable
fun LoginForm(busy: Boolean,onLogin: (String,String,Boolean,(Boolean,String?) -> Unit) -> Unit,
    onRegister: (String,String,String,String,(Boolean,String?) -> Unit) -> Unit) {
    var staff by rememberSaveable { mutableStateOf(false) }
    var register by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var errorField by remember { mutableStateOf<String?>(null) }
    val focus=LocalFocusManager.current
    val next=KeyboardActions(onNext={ focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) })
    fun clearError() { error=null; errorField=null }
    fun submit() {
        if(busy) return
        val validation=when {
            register && name.isBlank() -> "name" to "Escribe tu nombre completo."
            register && phone.isBlank() -> "phone" to "Escribe tu teléfono de contacto."
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "email" to "Escribe un correo válido, por ejemplo nombre@correo.com."
            password.isBlank() -> "password" to "Escribe tu contraseña."
            register && password.length<6 -> "password" to "Usa una contraseña de al menos 6 caracteres."
            register && password!=confirmation -> "confirmation" to "Las contraseñas no coinciden. Revísalas e inténtalo otra vez."
            else -> null
        }
        if(validation!=null) { errorField=validation.first; error=validation.second; return }
        focus.clearFocus()
        clearError()
        val result: (Boolean,String?) -> Unit={ success,message ->
            if(success) { register=false; password=""; confirmation=""; visible=false }
            else error=message ?: "No pudimos completar el acceso. Inténtalo nuevamente."
        }
        if(register && !staff) onRegister(name.trim(),phone.trim(),email.trim(),password,result)
        else onLogin(email.trim(),password,staff,result)
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.secondaryContainer.copy(alpha=0.32f),MaterialTheme.colorScheme.background)))) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal=20.dp,vertical=24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max=480.dp).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(22.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Surface(color=MaterialTheme.colorScheme.secondary,shape=RoundedCornerShape(18.dp)) {
                        Icon(Icons.Default.FreeBreakfast,null,Modifier.padding(14.dp).size(28.dp),tint=MaterialTheme.colorScheme.onSecondary)
                    }
                    Column {
                        Text("Desayunos",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                        Text("HECHO PARA TU MAÑANA",style=MaterialTheme.typography.labelSmall,letterSpacing=1.2.sp,color=MaterialTheme.colorScheme.secondary)
                    }
                }
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(if(staff) "Una buena mañana empieza en equipo." else "Tu mañana,\na tu gusto.",
                        fontSize=34.sp,lineHeight=39.sp,fontWeight=FontWeight.Bold,letterSpacing=(-0.8).sp,color=MaterialTheme.colorScheme.onBackground)
                    Text(if(staff) "Organiza la cocina, atiende pedidos y acompaña cada entrega." else "Elige tu desayuno favorito. Nosotros lo preparamos para recoger en el local.",
                        style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AccessChoices(staff,busy) { selected -> staff=selected; register=false; password=""; confirmation=""; visible=false; clearError() }
                Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface,
                    border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),shadowElevation=2.dp) {
                    Column(Modifier.padding(22.dp).animateContentSize(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(if(register) "Crea tu cuenta" else "Qué gusto verte",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                            Text(if(staff) "Ingresa con tu cuenta de administración o cocina." else if(register) "Unos datos y empezamos tu mañana." else "Ingresa para pedir y seguir tu desayuno.",
                                style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if(register && !staff) {
                            OutlinedTextField(name,{ name=it; clearError() },Modifier.fillMaxWidth().testTag("auth-name"),enabled=!busy,
                                isError=errorField=="name",label={ Text("Nombre completo") },singleLine=true,
                                leadingIcon={ Icon(Icons.Default.Person,null) },shape=RoundedCornerShape(14.dp),
                                keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.Words,imeAction=ImeAction.Next),keyboardActions=next)
                            OutlinedTextField(phone,{ phone=it; clearError() },Modifier.fillMaxWidth().testTag("auth-phone"),enabled=!busy,
                                isError=errorField=="phone",label={ Text("Teléfono") },singleLine=true,
                                leadingIcon={ Icon(Icons.Default.Phone,null) },shape=RoundedCornerShape(14.dp),
                                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone,imeAction=ImeAction.Next),keyboardActions=next)
                        }
                        OutlinedTextField(email,{ email=it; clearError() },Modifier.fillMaxWidth().testTag("auth-email"),enabled=!busy,
                            isError=errorField=="email",label={ Text("Correo electrónico") },singleLine=true,
                            leadingIcon={ Icon(Icons.Default.MailOutline,null) },shape=RoundedCornerShape(14.dp),
                            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email,imeAction=ImeAction.Next),keyboardActions=next)
                        OutlinedTextField(password,{ password=it; clearError() },Modifier.fillMaxWidth().testTag("auth-password"),enabled=!busy,
                            isError=errorField=="password",label={ Text("Contraseña") },singleLine=true,
                            supportingText=if(register) {{ Text("Al menos 6 caracteres") }} else null,
                            leadingIcon={ Icon(Icons.Default.Lock,null) },shape=RoundedCornerShape(14.dp),
                            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,imeAction=if(register) ImeAction.Next else ImeAction.Done),
                            keyboardActions=KeyboardActions(onNext={ focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },onDone={ submit() }),
                            visualTransformation=if(visible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon={ IconButton(enabled=!busy,onClick={ visible=!visible }) {
                                Icon(if(visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,if(visible) "Ocultar contraseña" else "Mostrar contraseña")
                            } })
                        if(register && !staff) OutlinedTextField(confirmation,{ confirmation=it; clearError() },Modifier.fillMaxWidth().testTag("auth-confirmation"),
                            enabled=!busy,isError=errorField=="confirmation",label={ Text("Repetir contraseña") },singleLine=true,
                            leadingIcon={ Icon(Icons.Default.Lock,null) },shape=RoundedCornerShape(14.dp),
                            visualTransformation=if(visible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={ submit() }))
                        error?.let { message ->
                            Surface(color=MaterialTheme.colorScheme.errorContainer,shape=RoundedCornerShape(14.dp),modifier=Modifier.testTag("auth-error").semantics { liveRegion=LiveRegionMode.Polite }) {
                                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                    Text("Revisemos los datos",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onErrorContainer)
                                    Text(message,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                        Button(onClick={ submit() },modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("auth-submit"),enabled=!busy,
                            shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.secondary,contentColor=MaterialTheme.colorScheme.onSecondary)) {
                            if(busy) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            else Icon(Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(if(busy) "Ingresando…" else if(register) "Crear cuenta" else if(staff) "Ingresar al equipo" else "Empezar mi mañana")
                        }
                        if(!staff) TextButton(onClick={ register=!register; password=""; confirmation=""; visible=false; clearError() },enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                            Text(if(register) "Ya tengo cuenta" else "Crear una cuenta de cliente")
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Top) {
                    Icon(Icons.Default.VerifiedUser,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.secondary)
                    Text(if(staff) "Cada integrante accede a las funciones de su cuenta." else "Tu cuenta, tus pedidos. Revisa su estado desde un solo lugar.",
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AccessChoices(staff: Boolean,busy: Boolean,onSelect: (Boolean) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
        val stacked=maxWidth<260.dp || LocalDensity.current.fontScale>1.2f
        if(stacked) Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            AccessChoice("Cliente","Quiero desayunar",Icons.Default.Person,!staff,!busy,Modifier.fillMaxWidth()) { onSelect(false) }
            AccessChoice("Personal","Cocina y administración",Icons.Default.Restaurant,staff,!busy,Modifier.fillMaxWidth()) { onSelect(true) }
        } else Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            AccessChoice("Cliente","Quiero desayunar",Icons.Default.Person,!staff,!busy,Modifier.weight(1f)) { onSelect(false) }
            AccessChoice("Personal","Cocina y administración",Icons.Default.Restaurant,staff,!busy,Modifier.weight(1f)) { onSelect(true) }
        }
    }
}

@Composable
private fun AccessChoice(title: String,description: String,icon: ImageVector,active: Boolean,enabled: Boolean,modifier: Modifier,onClick: () -> Unit) {
    OutlinedCard(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=118.dp).semantics { selected=active; role=Role.RadioButton }.testTag("access-$title"),
        shape=RoundedCornerShape(18.dp),border=BorderStroke(if(active) 2.dp else 1.dp,if(active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant),
        colors=CardDefaults.outlinedCardColors(containerColor=if(active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Icon(icon,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.secondary)
            Text(title,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold)
            Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
