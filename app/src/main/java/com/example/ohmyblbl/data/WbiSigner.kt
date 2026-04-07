package cn.harkerhand.ohmyblbl.data

import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.math.min

private val MIXIN_KEY_ENC_TAB = intArrayOf(
    46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49, 33, 9, 42, 19,
    29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
    22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
)

object WbiSigner {
    fun sign(
        params: Map<String, String>,
        imgKey: String,
        subKey: String,
        nowTs: Long = System.currentTimeMillis() / 1000L,
    ): Map<String, String> {
        val withTs = params.toMutableMap()
        withTs["wts"] = nowTs.toString()
        val sorted = withTs.toSortedMap()
        val mixinKey = getMixinKey(imgKey, subKey)
        val query = sorted.entries.joinToString("&") { (k, v) ->
            val cleaned = sanitizeWbiValue(v)
            "${urlEncode(k)}=${urlEncode(cleaned)}"
        }
        val digest = md5(query + mixinKey)
        withTs["w_rid"] = digest
        return withTs.toMap()
    }

    fun fileStem(url: String): String {
        val file = url.substringAfterLast('/')
        return file.substringBefore('.')
    }

    private fun getMixinKey(imgKey: String, subKey: String): String {
        val full = imgKey + subKey
        val chars = full.toCharArray()
        val sb = StringBuilder()
        for (idx in MIXIN_KEY_ENC_TAB) {
            if (idx in chars.indices) {
                sb.append(chars[idx])
                if (sb.length >= 32) break
            }
        }
        return sb.toString().substring(0, min(32, sb.length))
    }

    private fun sanitizeWbiValue(value: String): String =
        value.filterNot { it == '!' || it == '\'' || it == '(' || it == ')' || it == '*' }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun md5(text: String): String {
        val md = MessageDigest.getInstance("MD5")
        val hash = md.digest(text.toByteArray())
        return buildString(hash.size * 2) {
            for (b in hash) {
                append(String.format("%02x", b))
            }
        }
    }
}


