package com.example.desayuno.network

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.desayuno.BuildConfig
import com.example.desayuno.model.*
import com.google.gson.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant
import java.util.UUID

data class AuthSession(val accessToken: String, val refreshToken: String, val expiresAt: Long)
interface SessionStorage {
    fun read(): AuthSession?
    fun write(session: AuthSession?)
}
class SessionExpiredException : IllegalStateException("Su sesión expiró. Inicie sesión nuevamente.")
class SupabaseException(val statusCode: Int, message: String) : IllegalStateException(message)

/** Transporte inyectable. Solo el servidor confirma identidad, permisos y operaciones. */
class SupabaseApi(
    private val baseUrl: String,
    private val publicKey: String,
    private val storage: SessionStorage,
    private val client: OkHttpClient = OkHttpClient()
) {
    private val gson = Gson()
    private val refreshLock = Mutex()
    @Volatile private var session: AuthSession? = storage.read()
    private val sessionGuard = Any()
    @Volatile private var generation = 0
    fun clearSession() = synchronized(sessionGuard) { generation++; session = null; storage.write(null) }
    fun hasSession() = session != null
    private fun saveSession(json: JsonObject, expectedGeneration: Int) = synchronized(sessionGuard) {
        if (generation != expectedGeneration) throw SessionExpiredException()
        val next = AuthSession(json.get("access_token").asString,json.get("refresh_token").asString,
            json.get("expires_at")?.asLong ?: (Instant.now().epochSecond + json.get("expires_in").asLong))
        storage.write(next); session = next
    }
    private fun execute(path: String, method: String, body: RequestBody?, token: String?): JsonElement {
        val endpoint = baseUrl.toHttpUrl()
        check(endpoint.isHttps || endpoint.host in setOf("localhost","127.0.0.1","::1")) { "Configure SUPABASE_URL con HTTPS" }
        check(publicKey.isNotBlank()) { "Configure SUPABASE_ANON_KEY en local.properties" }
        val builder = Request.Builder().url(baseUrl.trimEnd('/') + path).header("apikey",publicKey)
        if (token != null) builder.header("Authorization","Bearer $token")
        client.newCall(builder.method(method,body).build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val error = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                val serverMessage = error?.get("msg")?.asString ?: error?.get("message")?.asString
                    ?: error?.get("error_description")?.asString ?: "Error del servidor (${response.code})"
                val message = if (path.startsWith("/auth/") && serverMessage.contains("Database error querying schema",ignoreCase=true))
                    "Supabase Auth no pudo consultar sus datos (HTTP ${response.code}). Revise Logs → Auth y ejecute DIAGNOSTICO_LOGIN.sql."
                else serverMessage
                throw SupabaseException(response.code,message)
            }
            return if(text.isBlank()) JsonNull.INSTANCE else JsonParser.parseString(text)
        }
    }
    private fun body(value: Any) = gson.toJson(value).toRequestBody("application/json".toMediaType())
    private suspend fun accessToken(forceFrom: String? = null): String = refreshLock.withLock {
        val current = session ?: throw SessionExpiredException()
        val epoch = generation
        if(current.expiresAt > Instant.now().epochSecond+60 && (forceFrom == null || current.accessToken != forceFrom))
            return@withLock current.accessToken
        try {
            saveSession(execute("/auth/v1/token?grant_type=refresh_token","POST",
                body(mapOf("refresh_token" to current.refreshToken)),null).asJsonObject,epoch)
        } catch(e: SupabaseException) {
            if(e.statusCode in listOf(400,401,403)) { clearSession(); throw SessionExpiredException() }; throw e
        }
        session!!.accessToken
    }
    private suspend fun authorized(path: String, method: String = "GET", payload: RequestBody? = null): JsonElement = withContext(Dispatchers.IO) {
        val token = accessToken()
        try { execute(path,method,payload,token) } catch(e: SupabaseException) {
            if(e.statusCode != 401) throw e
            val renewed = accessToken(forceFrom=token)
            try { execute(path,method,payload,renewed) } catch(retry: SupabaseException) {
                if(retry.statusCode == 401) { clearSession(); throw SessionExpiredException() }; throw retry
            }
        }
    }
    suspend fun login(email: String,password: String): User = withContext(Dispatchers.IO) {
        require(email.isNotBlank() && password.isNotBlank()) { "Ingrese correo y contraseña" }
        val epoch = generation
        saveSession(execute("/auth/v1/token?grant_type=password","POST",body(mapOf("email" to email,"password" to password)),null).asJsonObject,epoch)
        try { profile() } catch(e: Exception) { clearSession(); throw e }
    }
    /** true = sesión emitida; false = revisar correo para confirmar, sin iniciar sesión. */
    suspend fun signUp(email: String,password: String,name: String,phone: String): Boolean = withContext(Dispatchers.IO) {
        require(name.isNotBlank() && phone.isNotBlank() && email.isNotBlank() && password.length >= 6) { "Complete los datos; contraseña de al menos 6 caracteres" }
        val epoch = generation
        val response = execute("/auth/v1/signup","POST",body(mapOf("email" to email,"password" to password,
            "data" to mapOf("full_name" to name,"phone" to phone))),null).asJsonObject
        if(response.has("access_token") && !response.get("access_token").isJsonNull) { saveSession(response,epoch); true } else false
    }
    suspend fun profile(): User {
        val id = authorized("/auth/v1/user").asJsonObject.get("id").asString
        val rows = authorized("/rest/v1/profiles?id=eq.$id&select=*").asJsonArray
        check(rows.size() == 1) { "Perfil ausente. Aplique la migración de Supabase." }
        val row = rows[0].asJsonObject
        return User(id=id,name=row.string("full_name"),phone=row.string("phone"),role=UserRole.valueOf(row.string("role")),email=row.string("email"))
    }
    private suspend fun rows(resource: String,query: String): List<JsonObject> {
        val result = mutableListOf<JsonObject>(); var offset = 0
        while(true) {
            val page = authorized("/rest/v1/$resource?$query&limit=200&offset=$offset").asJsonArray
            result.addAll(page.map { it.asJsonObject }); if(page.size() < 200) return result; offset += page.size()
        }
    }
    suspend fun menu() = rows("menu_items","select=*&order=name.asc,id.asc").map(::parseMenuItem)
    suspend fun orders() = rows("orders","select=*,order_items(*),payments(*)&order=created_at.desc,id.desc").map(::parseOrder)
    suspend fun announcements() = rows("announcements","select=*&order=created_at.desc,id.desc").map {
        Announcement(it.string("id"),it.string("title"),it.string("message"),it.time("created_at"))
    }
    suspend fun acceptingOrders() = authorized("/rest/v1/business_settings?select=accepting_orders&id=eq.true")
        .asJsonArray.first().asJsonObject.get("accepting_orders").asBoolean
    private suspend fun rpc(name: String,args: Any) = authorized("/rest/v1/rpc/$name","POST",body(args))
    suspend fun createOrder(requestId: String,items: List<CartItem>): Order = parseOrder(rpc("create_order",mapOf(
        "p_request_id" to requestId,"p_items" to items.map { mapOf("menu_item_id" to it.menuItem.id,"quantity" to it.quantity,"notes" to it.notes) })).asJsonObject)
    suspend fun transition(id: String,status: OrderStatus,reason: String? = null): Order =
        parseOrder(rpc("transition_order",mapOf("p_order_id" to id,"p_status" to status.name,"p_reason" to reason)).asJsonObject)
    suspend fun completeOrder(id: String, method: String? = null, reference: String? = null): Order =
        parseOrder(rpc("complete_order",mapOf("p_order_id" to id,"p_method" to method,"p_reference" to reference)).asJsonObject)
    suspend fun saveMenu(item: MenuItem) {
        val args = JsonObject().apply {
            add("p_item",JsonObject().apply {
                addProperty("id",item.id); addProperty("name",item.name); addProperty("description",item.description)
                addProperty("price",java.math.BigDecimal.valueOf(item.price)); addProperty("category",item.category.name)
                addProperty("available_stock",item.availableStock); addProperty("is_available",item.isAvailable); addProperty("image_url",item.imageUrl)
            }); add("p_expected_updated_at",item.updatedAt?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
        }; rpc("save_menu_item",args)
    }
    suspend fun publish(title: String,message: String) { rpc("publish_announcement",mapOf("p_title" to title,"p_message" to message)) }
    suspend fun recordPayment(id: String,method: String,reference: String) { rpc("record_payment",mapOf("p_order_id" to id,"p_method" to method,"p_reference" to reference)) }
    suspend fun setAccepting(accepting: Boolean) { rpc("set_accepting_orders",mapOf("p_accepting" to accepting)) }
    suspend fun closeCashDay(date: java.time.LocalDate,total: Double): CashClosure =
        parseClosure(rpc("close_cash_day",mapOf("p_date" to date.toString(),"p_expected_total" to java.math.BigDecimal.valueOf(total))).asJsonObject)
    suspend fun cashClosures(): List<CashClosure> = rows("daily_cash_closures","select=*&order=business_date.desc").map(::parseClosure)
    suspend fun updateProfile(name: String,phone: String): User {
        require(name.isNotBlank() && phone.isNotBlank()) { "Nombre y teléfono obligatorios" }
        val id = profile().id
        authorized("/rest/v1/profiles?id=eq.$id","PATCH",body(mapOf("full_name" to name,"phone" to phone)))
        return profile()
    }
    suspend fun uploadImage(bytes: ByteArray,mimeType: String): String {
        require(bytes.size <= 5*1024*1024) { "La imagen supera 5 MB" }
        val extension = when(mimeType) { "image/png" -> "png"; "image/webp" -> "webp"; "image/jpeg" -> "jpg"; else -> error("Use JPG, PNG o WebP") }
        val filename = "${UUID.randomUUID()}.$extension"
        authorized("/storage/v1/object/desayunos-imagenes/$filename","POST",bytes.toRequestBody(mimeType.toMediaType()))
        return "${baseUrl.trimEnd('/')}/storage/v1/object/public/desayunos-imagenes/$filename"
    }
    companion object {
        internal fun parseMenuItem(row: JsonObject) = MenuItem(id=row.string("id"),name=row.string("name"),description=row.string("description"),
            price=row.get("price").asDouble,category=ProductCategory.valueOf(row.string("category")),availableStock=row.get("available_stock").asInt,
            isAvailable=row.get("is_available").asBoolean,imageUrl=row.string("image_url").ifBlank { null },updatedAt=row.string("updated_at").ifBlank { null })
        internal fun parseOrder(row: JsonObject): Order {
            val paymentElement = row.get("payments")
            val payment = when {
                paymentElement == null || paymentElement.isJsonNull -> null
                paymentElement.isJsonArray -> paymentElement.asJsonArray.firstOrNull()?.asJsonObject
                else -> paymentElement.asJsonObject
            }
            return Order(id=row.string("id"),orderNumber=row.get("order_number").asInt,customerName=row.string("customer_name"),customerPhone=row.string("customer_phone"),
                customerId=row.string("customer_id"),totalAmount=row.get("total_amount").asDouble,status=OrderStatus.valueOf(row.string("status")),
                estimatedMinutes=row.get("estimated_minutes")?.takeUnless { it.isJsonNull }?.asInt ?: 0,timestamp=row.time("created_at"),
                paymentMethod=payment?.string("method"),paidAt=payment?.time("created_at"),
                paidAmount=payment?.get("amount")?.takeUnless { it.isJsonNull }?.asDouble,
                paymentReference=payment?.string("reference"),updatedAt=row.time("updated_at").takeIf { it != 0L } ?: row.time("created_at"),
                items=row.getAsJsonArray("order_items")?.map { entry ->
                    val it=entry.asJsonObject; OrderItem(it.string("menu_item_id"),it.string("menu_item_name"),it.get("unit_price").asDouble,it.get("quantity").asInt,it.string("notes")) } ?: emptyList())
        }
        private fun parseClosure(row: JsonObject) = CashClosure(row.string("business_date"),row.get("total_amount").asDouble,row.get("payment_count").asInt)
        private fun JsonObject.string(name: String) = get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
        private fun JsonObject.time(name: String) = string(name).takeIf { it.isNotBlank() }?.let { Instant.parse(it).toEpochMilli() } ?: 0L
    }
}

object SupabaseService {
    lateinit var api: SupabaseApi
        private set
    fun initialize(context: Context) {
        if(::api.isInitialized) return
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(context,"supabase_session",masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        val gson = Gson()
        api = SupabaseApi(BuildConfig.SUPABASE_URL,BuildConfig.SUPABASE_ANON_KEY,object : SessionStorage {
            override fun read(): AuthSession? = prefs.getString("session",null)?.let { gson.fromJson(it,AuthSession::class.java) }
            override fun write(session: AuthSession?) { check(prefs.edit().apply {
                if(session == null) remove("session") else putString("session",gson.toJson(session))
            }.commit()) { "No se pudo guardar la sesión" } }
        })
    }
    suspend fun uploadImage(context: Context,uri: Uri): String = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri) ?: error("No se pudo identificar la imagen")
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = java.io.ByteArrayOutputStream(); val chunk=ByteArray(8192)
            while(true) { val count=stream.read(chunk); if(count < 0) break
                require(buffer.size()+count <= 5*1024*1024) { "La imagen supera 5 MB" }; buffer.write(chunk,0,count) }; buffer.toByteArray()
        } ?: error("No se pudo leer la imagen")
        api.uploadImage(bytes,mime)
    }
}
