package com.example.desayuno

import com.example.desayuno.network.*
import com.example.desayuno.model.*
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SupabaseApiTest {
    private lateinit var server: MockWebServer
    private class MemoryStorage(var session: AuthSession? = null) : SessionStorage {
        override fun read()=session
        override fun write(session: AuthSession?) { this.session=session }
    }
    private lateinit var storage: MemoryStorage
    private fun api()=SupabaseApi(server.url("/").toString(),"public-key",storage)
    private fun response(text: String,code: Int = 200) { server.enqueue(MockResponse().setResponseCode(code).setBody(text).setHeader("Content-Type","application/json")) }
    private fun session() { storage.session=AuthSession("user-jwt","refresh",Instant.now().epochSecond+3600) }
    @Before fun setup() { server=MockWebServer(); server.start(); storage=MemoryStorage() }
    @After fun teardown() { server.shutdown() }

    @Test fun wrongPasswordNeverAuthenticatesEvenWithFormerTestPin() = runBlocking {
        response("""{"message":"Invalid login credentials"}""",400)
        val api=api()
        try { api.login("admin@desayunos.com","1234"); fail("Debe fallar") } catch(e: SupabaseException) { assertEquals(400,e.statusCode) }
        assertFalse(api.hasSession())
    }
    @Test fun serverErrorDuringSignupIsAnError() = runBlocking {
        response("""{"message":"Unavailable"}""",503)
        try { api().signUp("a@test.invalid","123456","Ana","999888777"); fail("Debe fallar") }
        catch(e: SupabaseException) { assertEquals(503,e.statusCode) }
    }
    @Test fun authSchemaFailureIsReportedWithoutCreatingSession() = runBlocking {
        response("""{"code":"unexpected_failure","msg":"Database error querying schema"}""",500)
        val api=api()
        try { api.login("franck@desayunos.com","password"); fail("Debe fallar") }
        catch(e: SupabaseException) {
            assertEquals(500,e.statusCode)
            assertTrue(e.message!!.contains("Logs → Auth"))
        }
        assertFalse(api.hasSession())
        assertEquals(1,server.requestCount)
    }
    @Test fun confirmationEmailDoesNotFabricateSession() = runBlocking {
        response("""{"user":{"id":"real-id"}}""")
        val api=api(); assertFalse(api.signUp("a@test.invalid","123456","Ana","999888777")); assertFalse(api.hasSession())
    }
    @Test fun loginLoadsRealIdPhoneAndServerRole() = runBlocking {
        response("""{"access_token":"real-jwt","refresh_token":"real-refresh","expires_in":3600}""")
        response("""{"id":"11111111-1111-4111-8111-111111111111"}""")
        response("""[{"id":"11111111-1111-4111-8111-111111111111","full_name":"Ana","phone":"987000111","email":"a@test.invalid","role":"ADMIN"}]""")
        val user=api().login("a@test.invalid","password")
        assertEquals("11111111-1111-4111-8111-111111111111",user.id); assertEquals("987000111",user.phone); assertEquals(UserRole.ADMIN,user.role)
        server.takeRequest()
        assertEquals("Bearer real-jwt",server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer real-jwt",server.takeRequest().getHeader("Authorization"))
    }
    @Test fun expiredTokenRefreshesAndPersistsRotatedSession() = runBlocking {
        storage.session=AuthSession("expired","old-refresh",1)
        response("""{"access_token":"renewed","refresh_token":"new-refresh","expires_in":3600}""")
        response("[]")
        assertTrue(api().menu().isEmpty())
        assertTrue(server.takeRequest().path!!.contains("grant_type=refresh_token"))
        assertEquals("Bearer renewed",server.takeRequest().getHeader("Authorization"))
        assertEquals("new-refresh",storage.session!!.refreshToken)
    }
    @Test fun invalidRefreshClearsSession() = runBlocking {
        storage.session=AuthSession("expired","old-refresh",1)
        response("""{"message":"Invalid Refresh Token"}""",400)
        try { api().menu(); fail("Debe solicitar login") } catch(e: SessionExpiredException) { assertNull(storage.session) }
    }
    @Test fun stockRejectionDoesNotReturnAnOrder() = runBlocking {
        session(); response("""{"message":"Stock insuficiente"}""",400)
        try { api().createOrder("request",listOf(CartItem(MenuItem(name="Pan",description="",price=0.01,category=ProductCategory.BREAD,availableStock=1),1))); fail("Debe fallar") }
        catch(e: SupabaseException) { assertEquals("Stock insuficiente",e.message) }
    }
    @Test fun checkoutSendsOnlyIdentityQuantityNotesAndStableRequestId() = runBlocking {
        session()
        val response="""{"id":"order","order_number":701,"customer_id":"user","customer_name":"Ana","customer_phone":"999","status":"PENDING","total_amount":5.00,"created_at":"2026-10-22T13:00:00Z"}"""
        response(response); response(response)
        val api=api(); val items=listOf(CartItem(MenuItem(id="product",name="Pan",description="",price=0.01,category=ProductCategory.BREAD,availableStock=9),2,"Sin salsa"))
        assertEquals(701,api.createOrder("stable-request",items).orderNumber)
        api.createOrder("stable-request",items)
        val first=server.takeRequest(); val second=server.takeRequest()
        assertEquals("/rest/v1/rpc/create_order",first.path)
        assertEquals("Bearer user-jwt",first.getHeader("Authorization"))
        val payload=first.body.readUtf8(); assertEquals(payload,second.body.readUtf8())
        val json=JsonParser.parseString(payload).asJsonObject
        assertEquals("stable-request",json.get("p_request_id").asString)
        val item=json.getAsJsonArray("p_items")[0].asJsonObject
        assertFalse(item.has("price")); assertFalse(item.has("unit_price")); assertFalse(json.has("total_amount"))
        assertEquals(2,item.get("quantity").asInt)
    }
    @Test fun storageFailureIsNotReturnedAsLocalImageSuccess() = runBlocking {
        session(); response("""{"message":"Forbidden"}""",403)
        try { api().uploadImage(byteArrayOf(1,2,3),"image/jpeg"); fail("Debe fallar") }
        catch(e: SupabaseException) { assertEquals(403,e.statusCode) }
    }
    @Test fun parsesOneToOnePaymentAndNullableHistoricalProductReference() {
        val row=JsonParser.parseString("""{"id":"order","order_number":1,"customer_name":"Ana","customer_phone":"999","status":"DELIVERED","total_amount":2.50,"created_at":"2026-10-22T13:00:00Z","estimated_minutes":null,"order_items":[{"menu_item_id":null,"menu_item_name":"Pan histórico","unit_price":2.5,"quantity":1}],"payments":{"method":"CASH","created_at":"2026-10-22T14:00:00Z"}}""").asJsonObject
        val order=SupabaseApi.parseOrder(row)
        assertEquals("CASH",order.paymentMethod); assertNotNull(order.paidAt); assertEquals("",order.items[0].menuItemId)
    }
    @Test fun completingOrderUsesAtomicRpcAndReadsConfirmedPayment() = runBlocking {
        session()
        response("""{"id":"order","order_number":12,"customer_name":"Ana","customer_phone":"999","status":"DELIVERED","total_amount":7.0,"created_at":"2026-10-22T13:00:00Z","updated_at":"2026-10-22T14:00:00Z","payments":{"method":"YAPE","amount":7.0,"reference":"operation","created_at":"2026-10-22T14:00:00Z"},"order_items":[]}""")
        val order=api().completeOrder("order","YAPE","operation")
        assertEquals(OrderStatus.DELIVERED,order.status)
        assertEquals(7.0,order.paidAmount!!,0.0)
        assertEquals("operation",order.paymentReference)
        val request=server.takeRequest()
        assertEquals("/rest/v1/rpc/complete_order",request.path)
        val payload=JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("YAPE",payload.get("p_method").asString)
        assertFalse(payload.has("amount"))
    }
}
