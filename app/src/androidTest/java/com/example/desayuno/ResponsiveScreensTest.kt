package com.example.desayuno

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.desayuno.model.*
import com.example.desayuno.ui.*
import com.example.desayuno.notification.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ResponsiveScreensTest {
    @get:Rule val compose=createComposeRule()
    private val longName="Pan especial con pollo deshilachado, palta y ensalada de verduras frescas"
    private val order=Order(id="fixture-order",orderNumber=123456,
        customerName="María Fernanda de los Ángeles Apellidos Largos",customerPhone="+51 999 888 777",
        items=listOf(OrderItem("fixture-product",longName,7.5,2,"Sin salsa, sin cebolla y con el acompañamiento separado, por favor.")),
        totalAmount=15.0,status=OrderStatus.READY,customerId="fixture-user",
        timestamp=Instant.parse("2026-10-22T13:00:00Z").toEpochMilli())

    private fun phone(fontScale: Float=1f,content: @Composable () -> Unit) {
        compose.setContent {
            val current=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(current.density,fontScale)) {
                DesayunoTheme(darkTheme=false) {
                    Surface(Modifier.requiredWidth(320.dp).fillMaxHeight().testTag("test-phone")) { content() }
                }
            }
        }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.getExternalFilesDir(null),"ui-review").apply { mkdirs() }
        val node=if(name.startsWith("05-")) compose.onNode(isDialog()) else compose.onNodeWithTag("test-phone")
        val image=node.captureToImage().asAndroidBitmap()
        File(directory,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        image.recycle()
    }
    private fun textFits(text: String) {
        val results=mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue("No se encontró el texto: $text",results.isNotEmpty())
        // Compose 1.7 can retain the paragraph's maximum width after wrapping the node
        // to its intrinsic width. Check actual line bounds rather than that allocation.
        results.forEach { layout ->
            assertFalse("Texto recortado verticalmente: $text",layout.didOverflowHeight)
            for(line in 0 until layout.lineCount) {
                assertFalse("Texto abreviado: $text",layout.isLineEllipsized(line))
                assertTrue("Texto fuera del ancho: $text",layout.getLineLeft(line)>=-1f && layout.getLineRight(line)<=layout.size.width+1f)
            }
        }
    }

    @Test fun clientOrdersAt320dpWrapLongProductAndAmounts() {
        phone { ClientOrdersTab(listOf(order),onCancel={}) }
        compose.onNodeWithText("Pedido #123456").assertIsDisplayed()
        screenshot("01-mis-pedidos-320")
        textFits("Pedido #123456")
        textFits("2 × $longName")
        screenshot("01-mis-pedidos-320")
        compose.onNodeWithText("Total del pedido").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("S/ 15.00").performScrollTo().assertIsDisplayed()
        textFits("S/ 15.00")
        screenshot("02-mis-pedidos-total-320")
    }
    @Test fun clientOrdersWithLargeFontKeepCardReadable() {
        phone(1.6f) { ClientOrdersTab(listOf(order),onCancel={}) }
        compose.onNodeWithText("Pedido #123456").assertIsDisplayed()
        screenshot("03-mis-pedidos-letra-grande")
        textFits("Pedido #123456")
        screenshot("03-mis-pedidos-letra-grande")
        compose.onNodeWithText("Total del pedido").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("S/ 15.00").performScrollTo().assertIsDisplayed()
        textFits("2 × $longName")
        textFits("S/ 15.00")
        screenshot("04-mis-pedidos-letra-grande-total")
    }
    @Test fun yapeNeedsReferenceBeforeChargingAndCompleting() {
        var completed: Triple<String,String?,String?>?=null
        phone(1.5f) { CompleteOrderButton(order,true,false) { id,method,reference -> completed=Triple(id,method,reference) } }
        compose.onNodeWithTag("complete-order").performClick()
        compose.onNodeWithTag("payment-YAPE").performScrollTo().performClick()
        compose.onNodeWithTag("confirm-payment").assertIsNotEnabled()
        compose.onNodeWithTag("payment-reference").performScrollTo().performTextInput("987654321")
        compose.onNodeWithTag("confirm-payment").assertIsEnabled()
        screenshot("05-cobrar-y-completar-yape")
        assertNull(completed)
        compose.onNodeWithTag("confirm-payment").performClick()
        compose.runOnIdle { assertEquals(Triple("fixture-order","YAPE","987654321"),completed) }
    }
    @Test fun cookCannotCompleteUnpaidOrder() {
        phone { CompleteOrderButton(order,false,false) { _,_,_ -> fail("No debe completar sin pago") } }
        compose.onNodeWithTag("complete-order").assertIsNotEnabled()
    }
    @Test fun deliveredOrderLeavesActiveQueueAndAppearsInCompleted() {
        var rows by mutableStateOf(listOf(order.copy(paymentMethod="CASH",paidAt=order.timestamp,paidAmount=15.0)))
        phone {
            CookOrdersTab(rows,true,false,{ _,_,_ -> },{ id,_,_ -> rows=rows.map { if(it.id==id) it.copy(status=OrderStatus.DELIVERED) else it } },{}, { _,_ -> })
        }
        compose.onNodeWithTag("complete-order").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar entrega").performClick()
        compose.onNodeWithTag("staff-order-fixture-order").assertDoesNotExist()
        compose.onNodeWithTag("staff-order-filters").performScrollToNode(hasTestTag("filter-DELIVERED"))
        compose.onNodeWithTag("filter-DELIVERED").performClick()
        compose.onNodeWithTag("staff-order-fixture-order").assertExists()
        screenshot("06-pedidos-completados")
    }
    @Test fun reportsAt320dpAndLargeFontDisplayFullAmounts() {
        val paid=order.copy(paymentMethod="YAPE",paidAt=order.timestamp,paidAmount=1234.5,totalAmount=1234.5,status=OrderStatus.DELIVERED)
        val report=buildSalesReport(listOf(paid),LocalDate.parse("2026-10-22"))
        phone(1.6f) { ReportContent(report,null,true,false,{}, {}, {}) }
        compose.onNodeWithText("Cobros confirmados").performScrollTo().assertIsDisplayed()
        screenshot("07-reporte-letra-grande")
        textFits("S/ 1,234.50")
        screenshot("07-reporte-letra-grande")
        compose.onNodeWithTag("report-screen").performScrollToNode(hasText("Cobros por método"))
        compose.onNodeWithText("Cobros por método").assertIsDisplayed()
        screenshot("08-reporte-metodos")
    }
    @Test fun registrationAt320dpRemainsScrollableWithLargeFont() {
        phone(1.6f) { LoginForm(false,{ _,_,_,_ -> },{ _,_,_,_,_ -> }) }
        compose.onNodeWithText("Crear una cuenta de cliente").performScrollTo().performClick()
        compose.onNodeWithText("Nombre completo").performScrollTo().assertIsDisplayed()
        screenshot("09-registro-letra-grande")
        compose.onNodeWithText("Repetir contraseña").performScrollTo().assertIsDisplayed()
    }

    @Test fun loginValidatesAndShowsServerErrorWithoutLosingEmail() {
        var attempts=0
        phone { LoginForm(false,{ _,_,_,result -> attempts++; result(false,"No se pudo iniciar sesión con estos datos.") },{ _,_,_,_,_ -> }) }
        screenshot("10-login-redisenado")
        compose.onNodeWithTag("auth-submit").performScrollTo().performClick()
        compose.onNodeWithTag("auth-error").assertExists()
        compose.runOnIdle { assertEquals(0,attempts) }
        compose.onNodeWithTag("auth-email").performScrollTo().performTextInput("cliente@test.invalid")
        compose.onNodeWithTag("auth-password").performScrollTo().performTextInput("secret-test")
        compose.onNodeWithTag("auth-submit").performScrollTo().performClick()
        compose.onNodeWithText("No se pudo iniciar sesión con estos datos.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("auth-email").assertTextContains("cliente@test.invalid")
        compose.runOnIdle { assertEquals(1,attempts) }
        screenshot("11-login-error")
    }

    @Test fun staffAccessCannotRegisterAndUsesStaffLogin() {
        var staffLogin=false
        phone { LoginForm(false,{ _,_,staff,_ -> staffLogin=staff },{ _,_,_,_,_ -> fail("No debe registrar personal") }) }
        compose.onNodeWithTag("access-Personal").performScrollTo().performClick()
        compose.onNodeWithText("Crear una cuenta de cliente").assertDoesNotExist()
        compose.onNodeWithTag("auth-email").performScrollTo().performTextInput("cocina@test.invalid")
        compose.onNodeWithTag("auth-password").performScrollTo().performTextInput("secret-test")
        compose.onNodeWithTag("auth-submit").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(staffLogin) }
        screenshot("12-login-personal")
    }

    @Test fun notificationCanBeReadAndOpensItsOrder() {
        val user=User("fixture-user","Ana","999888777",UserRole.CLIENT)
        val notice=orderNotice(user,order)
        var read by mutableStateOf(emptySet<String>())
        var opened: String?=null
        phone(1.6f) {
            NotificationCenter(listOf(notice),read,false,false,{ read=read+it },{ opened=it },{},{},{},Modifier.fillMaxHeight())
        }
        screenshot("13-notificaciones-letra-grande")
        compose.onNodeWithTag("notice-list").performScrollToNode(hasTestTag("notice-${notice.id}"))
        compose.onNodeWithTag("notice-${notice.id}").performClick()
        compose.runOnIdle { assertEquals(setOf(notice.id),read); assertEquals(order.id,opened) }
        screenshot("14-notificacion-pedido")
        compose.onNodeWithTag("notice-list").performScrollToNode(hasTestTag("filter-unread"))
        compose.onNodeWithTag("filter-unread").performClick()
        compose.onNodeWithText("Todo leído").assertExists()
    }

    @Test fun markAllReadAlsoKeepsAnnouncementsAccessible() {
        val notices=listOf(AppNotice("news","Hoy tenemos un nuevo horario de atención","Atendemos desde las seis de la mañana.",order.timestamp,NoticeKind.ANNOUNCEMENT))
        var read by mutableStateOf(emptySet<String>())
        phone {
            NotificationCenter(notices,read,true,false,{ read=read+it },{ fail("Un aviso no abre pedidos") },{},{},{},Modifier.fillMaxHeight())
        }
        compose.onNodeWithTag("notice-list").performScrollToNode(hasTestTag("read-all"))
        compose.onNodeWithTag("read-all").performClick()
        compose.runOnIdle { assertEquals(setOf("news"),read) }
        compose.onNodeWithTag("notice-list").performScrollToNode(hasTestTag("notice-news"))
        compose.onNodeWithTag("notice-news").performClick()
        screenshot("15-avisos-negocio")
    }

    @Test fun darkLoginWithBusyStateRemainsReadable() {
        compose.setContent {
            DesayunoTheme(darkTheme=true) {
                Surface(Modifier.requiredWidth(320.dp).fillMaxHeight().testTag("test-phone")) {
                    LoginForm(true,{ _,_,_,_ -> fail("Acceso ocupado") },{ _,_,_,_,_ -> })
                }
            }
        }
        compose.onNodeWithTag("auth-submit").performScrollTo().assertIsNotEnabled()
        screenshot("16-login-oscuro")
    }

    @Test fun notificationEmptyStateAndOptInButtonWorkWithoutRequestingOnOpen() {
        var requested=false
        phone {
            NotificationCenter(emptyList(),emptySet(),false,false,{},{},{ requested=true },{},{},Modifier.fillMaxHeight())
        }
        compose.runOnIdle { assertFalse(requested) }
        compose.onNodeWithTag("enable-notifications").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(requested) }
        compose.onNodeWithTag("notice-list").performScrollToNode(hasText("Aquí llegan las novedades"))
        compose.onNodeWithText("Aquí llegan las novedades").assertIsDisplayed()
        screenshot("17-notificaciones-vacio")
    }
}
