package cn.harkerhand.ohmyblbl.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

class BiliClient(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private var wbiKeys: WbiKeys? = null

    fun getCookie(): String = prefs.getString(KEY_COOKIE, "") ?: ""

    fun clearAuth() {
        prefs.edit { remove(KEY_COOKIE).remove(KEY_REFRESH_TOKEN) }
        wbiKeys = null
    }

    fun setRefreshToken(token: String?) {
        if (token.isNullOrBlank()) {
            prefs.edit { remove(KEY_REFRESH_TOKEN) }
        } else {
            prefs.edit { putString(KEY_REFRESH_TOKEN, token) }
        }
    }

    fun cookieValue(key: String): String {
        val cookie = getCookie()
        if (cookie.isBlank()) return ""
        val row = cookie.split(";").map { it.trim() }.firstOrNull { it.startsWith("$key=") } ?: return ""
        return row.substringAfter('=', "")
    }

    fun checkLogin(): JsonObject {
        val data = getApiData("/x/web-interface/nav", emptyMap())
        val isLogin = data.bool("isLogin")
        return JsonObject(
            mapOf(
                "is_login" to json.parseToJsonElement(if (isLogin) "true" else "false"),
                "data" to data,
            )
        )
    }

    fun getApiData(pathOrUrl: String, query: Map<String, String>): JsonObject {
        var raw = getJson(pathOrUrl, query)
        if (raw.int("code") == -101 && tryRefreshCookie()) {
            raw = getJson(pathOrUrl, query)
        }
        val code = raw.int("code")
        if (code == 0) return raw.obj("data")
        val message = raw.str("message").ifBlank { "Request failed: code=$code" }
        if (code == -101) throw AuthExpiredException(message)
        throw IllegalStateException(message)
    }

    fun getJson(pathOrUrl: String, query: Map<String, String>): JsonObject {
        val urlBuilder = normalizeUrl(pathOrUrl).toHttpUrl().newBuilder()
        query.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("Referer", "https://www.bilibili.com")
            .header("Origin", "https://www.bilibili.com")
            .header("User-Agent", UA)
            .apply {
                val cookie = getCookie()
                if (cookie.isNotBlank()) header("Cookie", cookie)
            }
            .build()
        return executeRequest(request)
    }

    fun postFormJson(pathOrUrl: String, form: Map<String, String>): JsonObject {
        val formBody = FormBody.Builder().apply {
            form.forEach { (k, v) -> add(k, v) }
        }.build()
        val request = Request.Builder()
            .url(normalizeUrl(pathOrUrl))
            .post(formBody)
            .header("Referer", "https://www.bilibili.com")
            .header("Origin", "https://www.bilibili.com")
            .header("User-Agent", UA)
            .apply {
                val cookie = getCookie()
                if (cookie.isNotBlank()) header("Cookie", cookie)
            }
            .build()
        return executeRequest(request)
    }

    fun postFormApiData(pathOrUrl: String, form: Map<String, String>): JsonObject {
        var raw = postFormJson(pathOrUrl, form)
        if (raw.int("code") == -101 && tryRefreshCookie()) {
            raw = postFormJson(pathOrUrl, form)
        }
        val code = raw.int("code")
        if (code == 0) return raw.obj("data")
        val message = raw.str("message").ifBlank { "Request failed: code=$code" }
        if (code == -101) throw AuthExpiredException(message)
        throw IllegalStateException(message)
    }

    fun ensureWbiKeys(): WbiKeys {
        wbiKeys?.let { return it }
        val navData = getApiData("/x/web-interface/nav", emptyMap())
        val wbi = navData.obj("wbi_img")
        val imgUrl = wbi.str("img_url")
        val subUrl = wbi.str("sub_url")
        val keys = WbiKeys(
            imgKey = WbiSigner.fileStem(imgUrl),
            subKey = WbiSigner.fileStem(subUrl),
        )
        wbiKeys = keys
        return keys
    }

    private fun executeRequest(request: Request): JsonObject {
        client.newCall(request).execute().use { response ->
            captureSetCookie(response.headers.values("Set-Cookie"))
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val parsed = json.parseToJsonElement(body)
            return parsed.jsonObject
        }
    }

    private fun captureSetCookie(setCookies: List<String>) {
        if (setCookies.isEmpty()) return
        val merged = mergeSetCookie(getCookie(), setCookies)
        if (merged.isNotBlank()) {
            prefs.edit { putString(KEY_COOKIE, ensureBuvidCookie(merged)) }
        }
    }

    private fun normalizeUrl(pathOrUrl: String): String =
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            pathOrUrl
        } else {
            "$API_BASE$pathOrUrl"
        }

    private fun tryRefreshCookie(): Boolean {
        val token = prefs.getString(KEY_REFRESH_TOKEN, "").orEmpty()
        if (token.isBlank()) return false
        val csrf = cookieValue("bili_jct")
        val raw = postFormJson(
            "https://passport.bilibili.com/x/passport-login/web/cookie/refresh",
            mapOf(
                "csrf" to csrf,
                "refresh_csrf" to "",
                "refresh_token" to token,
                "source" to "main_web",
            ),
        )
        val code = raw.int("code")
        if (code != 0) return false
        val newToken = raw.obj("data").str("refresh_token")
        if (newToken.isNotBlank()) setRefreshToken(newToken)
        return true
    }

    private fun mergeSetCookie(currentCookie: String, setCookies: List<String>): String {
        val map = linkedMapOf<String, String>()
        currentCookie.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .forEach { segment ->
                val idx = segment.indexOf('=')
                val k = segment.substring(0, idx).trim()
                val v = segment.substring(idx + 1).trim()
                if (k.isNotBlank()) map[k] = v
            }
        setCookies.forEach { row ->
            val first = row.substringBefore(';')
            val idx = first.indexOf('=')
            if (idx > 0) {
                val k = first.substring(0, idx).trim()
                val v = first.substring(idx + 1).trim()
                if (k.isNotBlank()) map[k] = v
            }
        }
        return map.entries.joinToString("; ") { (k, v) -> "$k=$v" }
    }

    private fun ensureBuvidCookie(cookie: String): String {
        val hasBuvid = cookie.contains("buvid3=")
        if (hasBuvid) return cookie
        val generated = UUID.randomUUID().toString().uppercase() + "infoc"
        return if (cookie.isBlank()) "buvid3=$generated" else "$cookie; buvid3=$generated"
    }

    data class WbiKeys(val imgKey: String, val subKey: String)

    companion object {
        private const val PREFS_AUTH = "ohmyblbl_auth"
        private const val KEY_COOKIE = "cookie"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val API_BASE = "https://api.bilibili.com"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
    }
}

class AuthExpiredException(
    message: String = "登录状态已失效",
) : IllegalStateException(message)

private fun JsonObject.obj(key: String): JsonObject =
    this[key]?.jsonObject ?: JsonObject(emptyMap())

private fun JsonObject.str(key: String): String {
    val raw = this[key] ?: return ""
    val text = raw.toString().trim('"')
    return text
}

private fun JsonObject.int(key: String): Int = str(key).toIntOrNull() ?: 0

private fun JsonObject.bool(key: String): Boolean {
    val element: JsonElement = this[key] ?: return false
    return when (element.toString().trim('"')) {
        "true", "1" -> true
        else -> false
    }
}

