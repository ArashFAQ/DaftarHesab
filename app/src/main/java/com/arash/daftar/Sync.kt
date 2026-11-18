package com.arash.daftar

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

/**
 * بکاپ روی Supabase.
 * هر تغییر اول روی گوشی ذخیره می‌شود (dirty) و بعد این‌جا فرستاده می‌شود.
 * اگر اینترنت نباشد، WorkManager به محض وصل شدن دوباره امتحان می‌کند.
 * بعد از فرستادن، تغییرهای سرور هم گرفته می‌شود (برای بازیابی روی گوشی جدید).
 */
object Sync {
    data class Status(val running: Boolean = false, val lastOk: Long = 0, val error: String? = null)

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> get() = _status

    private val mutex = Mutex()
    private const val PREFS = "sync"

    val configured: Boolean
        get() = BuildConfig.SB_URL.isNotBlank() && BuildConfig.SB_KEY.isNotBlank() &&
            BuildConfig.SB_EMAIL.isNotBlank() && BuildConfig.SB_PASSWORD.isNotBlank()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        val p = prefs(ctx)
        _status.value = Status(lastOk = p.getLong("lastOk", 0), error = p.getString("lastError", null))
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "sync-periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(net).build()
        )
    }

    /** بعد از هر تغییر صدا زده می‌شود */
    fun request(ctx: Context) {
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "sync",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(net)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        )
    }

    /** دفعه‌ی بعد همه‌ی اطلاعات سرور از اول گرفته شود */
    fun resetPull(ctx: Context) {
        prefs(ctx).edit().putLong("lastPull", 0).apply()
    }

    class ConfigError(msg: String) : Exception(msg)

    /** true یعنی موفق؛ خطای شبکه IOException پرتاب می‌کند */
    suspend fun run(ctx: Context): Boolean = mutex.withLock {
        if (!configured) {
            setError(ctx, "اطلاعات Supabase در این نسخه تنظیم نشده")
            return false
        }
        _status.value = _status.value.copy(running = true)
        try {
            withContext(Dispatchers.IO) {
                val dao = DaftarDb.get(ctx).dao()
                val api = Api(ctx)
                push(dao, api)
                pull(ctx, dao, api)
            }
            val t = System.currentTimeMillis()
            prefs(ctx).edit().putLong("lastOk", t).remove("lastError").apply()
            _status.value = Status(running = false, lastOk = t, error = null)
            true
        } catch (e: IOException) {
            setError(ctx, "اینترنت در دسترس نیست؛ بعدا خودکار ارسال می‌شود")
            throw e
        } catch (e: Exception) {
            setError(ctx, e.message ?: "خطای ناشناخته")
            false
        }
    }

    private fun setError(ctx: Context, msg: String) {
        prefs(ctx).edit().putString("lastError", msg).apply()
        _status.value = _status.value.copy(running = false, error = msg)
    }

    // ------------------------------------------------------------------ push / pull

    private fun iso(ms: Long): String = Instant.ofEpochMilli(ms).toString()

    private fun ms(s: String): Long = OffsetDateTime.parse(s).toInstant().toEpochMilli()

    private fun JSONObject.str(k: String): String = if (isNull(k)) "" else getString(k)

    private suspend fun push(dao: DaftarDao, api: Api) {
        val cs = dao.dirtyCustomers()
        for (chunk in cs.chunked(500)) {
            val arr = JSONArray()
            chunk.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("name", it.name)
                        .put("phone", it.phone)
                        .put("note", it.note)
                        .put("created_at", iso(it.createdAt))
                        .put("updated_at", iso(it.updatedAt))
                        .put("deleted", it.deleted)
                )
            }
            api.upsert("customers", arr)
            chunk.forEach { dao.markCustomerClean(it.id, it.updatedAt) }
        }
        val ts = dao.dirtyTxs()
        for (chunk in ts.chunked(500)) {
            val arr = JSONArray()
            chunk.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("customer_id", it.customerId)
                        .put("kind", it.kind)
                        .put("amount", it.amount)
                        .put("note", it.note)
                        .put("created_at", iso(it.date))
                        .put("updated_at", iso(it.updatedAt))
                        .put("deleted", it.deleted)
                )
            }
            api.upsert("transactions", arr)
            chunk.forEach { dao.markTxClean(it.id, it.updatedAt) }
        }
    }

    private suspend fun pull(ctx: Context, dao: DaftarDao, api: Api) {
        val since = prefs(ctx).getLong("lastPull", 0)
        var newest = since

        api.selectSince("customers", since).forEach { j ->
            val r = Customer(
                id = j.getString("id"),
                name = j.str("name"),
                phone = j.str("phone"),
                note = j.str("note"),
                createdAt = ms(j.getString("created_at")),
                updatedAt = ms(j.getString("updated_at")),
                deleted = j.getBoolean("deleted"),
                dirty = false
            )
            newest = maxOf(newest, r.updatedAt)
            val local = dao.customer(r.id)
            if (local == null || r.updatedAt > local.updatedAt) dao.upsertCustomer(r)
        }

        val txs = api.selectSince("transactions", since).map { j ->
            Tx(
                id = j.getString("id"),
                customerId = j.getString("customer_id"),
                kind = j.getString("kind"),
                amount = j.getLong("amount"),
                note = j.str("note"),
                date = ms(j.getString("created_at")),
                updatedAt = ms(j.getString("updated_at")),
                deleted = j.getBoolean("deleted"),
                dirty = false
            )
        }
        val toSave = ArrayList<Tx>()
        for (r in txs) {
            newest = maxOf(newest, r.updatedAt)
            val local = dao.tx(r.id)
            if (local == null || r.updatedAt > local.updatedAt) toSave.add(r)
        }
        toSave.chunked(500).forEach { dao.upsertTxs(it) }

        prefs(ctx).edit().putLong("lastPull", newest).apply()
    }

    // ------------------------------------------------------------------ HTTP

    private class Api(private val ctx: Context) {
        private val base = BuildConfig.SB_URL
        private var token: String? = null

        private fun open(url: String, method: String): HttpURLConnection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("apikey", BuildConfig.SB_KEY)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

        private fun call(url: String, method: String, body: String?, headers: Map<String, String>): Pair<Int, String> {
            val c = open(url, method)
            try {
                headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                if (body != null) {
                    c.doOutput = true
                    c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                return code to text
            } finally {
                c.disconnect()
            }
        }

        private fun login(force: Boolean): String {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!force) {
                token?.let { return it }
                val saved = p.getString("token", null)
                if (saved != null && p.getLong("tokenExp", 0) > System.currentTimeMillis() + 60_000) {
                    token = saved
                    return saved
                }
            }
            val body = JSONObject()
                .put("email", BuildConfig.SB_EMAIL)
                .put("password", BuildConfig.SB_PASSWORD)
                .toString()
            val (code, text) = call("$base/auth/v1/token?grant_type=password", "POST", body, emptyMap())
            if (code !in 200..299) {
                throw ConfigError(
                    if (code == 400 || code == 401) "ورود به Supabase ناموفق بود (ایمیل یا رمز حساب ثابت)"
                    else "خطای سرور در ورود ($code)"
                )
            }
            val j = JSONObject(text)
            val t = j.getString("access_token")
            val exp = System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000
            p.edit().putString("token", t).putLong("tokenExp", exp).apply()
            token = t
            return t
        }

        /** با یک بار تلاش دوباره در صورت منقضی شدن توکن */
        private fun authed(url: String, method: String, body: String?, extra: Map<String, String>): String {
            var (code, text) = call(url, method, body, extra + ("Authorization" to "Bearer ${login(false)}"))
            if (code == 401) {
                val r = call(url, method, body, extra + ("Authorization" to "Bearer ${login(true)}"))
                code = r.first
                text = r.second
            }
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).optString("message") }.getOrNull().orEmpty()
                throw Exception("خطای سرور ($code) $msg".trim())
            }
            return text
        }

        fun upsert(table: String, rows: JSONArray) {
            authed(
                "$base/rest/v1/$table?on_conflict=id", "POST", rows.toString(),
                mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
            )
        }

        fun selectSince(table: String, sinceMs: Long): List<JSONObject> {
            val out = ArrayList<JSONObject>()
            val since = URLEncoder.encode(Instant.ofEpochMilli(sinceMs).toString(), "UTF-8")
            val page = 1000
            var offset = 0
            while (true) {
                val url = "$base/rest/v1/$table?select=*&updated_at=gte.$since&order=updated_at.asc,id.asc" +
                    "&limit=$page&offset=$offset"
                val arr = JSONArray(authed(url, "GET", null, emptyMap()))
                for (i in 0 until arr.length()) out.add(arr.getJSONObject(i))
                if (arr.length() < page) break
                offset += page
            }
            return out
        }
    }
}

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        if (Sync.run(applicationContext)) Result.success()
        else if (Sync.configured && runAttemptCount < 5) Result.retry()
        else Result.failure()
    } catch (e: IOException) {
        Result.retry()
    }
}
