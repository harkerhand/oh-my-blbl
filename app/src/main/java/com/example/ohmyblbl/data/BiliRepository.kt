package cn.harkerhand.ohmyblbl.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class BiliRepository(
    private val client: BiliClient,
) {
    fun checkLogin(): Boolean = client.checkLogin().bool("is_login")

    fun clearAuth() {
        client.clearAuth()
    }

    fun getFeed(offset: String): FeedPage {
        val data = client.getApiData(
            pathOrUrl = "/x/polymer/web-dynamic/v1/feed/all",
            query = mapOf(
                "type" to "video",
                "offset" to offset,
                "timezone_offset" to "-480",
            ),
        )
        val items = data.arr("items").mapNotNull { item ->
            val row = item.jsonObject
            val modules = row.obj("modules")
            val author = modules.obj("module_author")
            val dynamic = modules.obj("module_dynamic")
            val archive = dynamic.obj("major").obj("archive")
            val stat = modules.obj("module_stat")
            val bvid = archive.str("bvid")
            if (bvid.isBlank()) return@mapNotNull null
            VideoItem(
                bvid = bvid,
                title = archive.str("title"),
                cover = coverUrl(archive.str("cover")),
                author = author.str("name").ifBlank { "未知UP" },
                authorMid = author.str("mid"),
                play = countText(archive.obj("stat")["play"]).ifBlank { countText(archive.obj("stat")["view"]) },
                danmaku = archive.obj("stat").str("danmaku"),
                durationText = archive.str("duration_text"),
                shareUrl = row.str("id_str").takeIf { it.isNotBlank() }
                    ?.let { "https://t.bilibili.com/$it" }
                    ?: "https://www.bilibili.com/video/$bvid",
                authorFace = coverUrl(author.str("face")),
                authorPubTime = author.str("pub_time"),
                shareCount = countText(stat["forward"]),
                commentCount = countText(stat["comment"]).ifBlank { countText(stat["reply"]) },
                likeCount = countText(stat["like"]),
            )
        }
        return FeedPage(
            offset = data.str("offset"),
            hasMore = data.bool("has_more"),
            items = items,
        )
    }

    fun getWatchLater(page: Int, pageSize: Int = 20): List<VideoItem> {
        val data = client.getApiData(
            pathOrUrl = "/x/v2/history/toview/web",
            query = mapOf("pn" to page.toString(), "ps" to pageSize.toString()),
        )
        return data.arr("list").map { element ->
            val row = element.jsonObject
            val bvid = row.str("bvid").ifBlank { parseBvid(row.str("jump_url")) }
            VideoItem(
                bvid = bvid,
                title = row.str("title"),
                cover = coverUrl(row.str("pic").ifBlank { row.str("cover") }),
                author = row.obj("owner").str("name")
                    .ifBlank { row.str("author").ifBlank { "未知UP" } },
                authorMid = row.obj("owner").str("mid"),
                play = row.obj("stat").str("view").ifBlank { row.str("play") },
                danmaku = row.obj("stat").str("danmaku"),
                durationText = row.str("duration_text").ifBlank { row.str("length") },
                shareUrl = "https://www.bilibili.com/video/$bvid",
            )
        }.filter { it.bvid.isNotBlank() }
    }

    fun getFavoriteFolders(): List<FolderItem> {
        val navData = client.getApiData("/x/web-interface/nav", emptyMap())
        val mid = navData.str("mid")
        if (mid.isBlank()) return emptyList()
        val data = client.getApiData(
            pathOrUrl = "/x/v3/fav/folder/created/list-all",
            query = mapOf("up_mid" to mid, "jsonp" to "jsonp"),
        )
        return data.arr("list").map { item ->
            val row = item.jsonObject
            FolderItem(
                id = row.str("id").ifBlank { row.str("media_id") }.toLongOrNull() ?: 0L,
                title = row.str("title").ifBlank { "未命名收藏夹" },
                count = row.str("media_count").toIntOrNull() ?: 0,
            )
        }.filter { it.id > 0 }
    }

    fun getFavoriteVideos(mediaId: Long, page: Int, pageSize: Int = 20): FavoritePage {
        val data = client.getApiData(
            pathOrUrl = "/x/v3/fav/resource/list",
            query = mapOf(
                "media_id" to mediaId.toString(),
                "pn" to page.toString(),
                "ps" to pageSize.toString(),
                "platform" to "web",
            ),
        )
        val videos = data.arr("medias").map { item ->
            val row = item.jsonObject
            VideoItem(
                bvid = row.str("bvid").ifBlank { row.str("bv_id") },
                title = row.str("title"),
                cover = coverUrl(row.str("cover")),
                author = row.obj("upper").str("name").ifBlank { "未知UP" },
                authorMid = row.obj("upper").str("mid"),
                play = row.obj("cnt_info").str("play"),
                danmaku = row.obj("cnt_info").str("danmaku"),
                durationText = row.str("duration"),
                shareUrl = "https://www.bilibili.com/video/${
                    row.str("bvid").ifBlank { row.str("bv_id") }
                }",
            )
        }.filter { it.bvid.isNotBlank() }
        return FavoritePage(
            hasMore = data.bool("has_more"),
            videos = videos,
        )
    }

    fun getQrcode(): QrCodeData {
        val data = client.getApiData(
            "https://passport.bilibili.com/x/passport-login/web/qrcode/generate",
            emptyMap(),
        )
        return QrCodeData(
            url = data.str("url"),
            qrcodeKey = data.str("qrcode_key"),
        )
    }

    fun pollQrcode(qrcodeKey: String): QrPollResult {
        val data = client.getApiData(
            "https://passport.bilibili.com/x/passport-login/web/qrcode/poll",
            mapOf("qrcode_key" to qrcodeKey),
        )
        if ((data.str("code").toIntOrNull() ?: -1) == 0) {
            val refreshToken = data.str("refresh_token")
            if (refreshToken.isNotBlank()) client.setRefreshToken(refreshToken)
        }
        return QrPollResult(
            code = data.str("code").toIntOrNull() ?: -1,
            message = data.str("message"),
        )
    }

    fun getLoginCaptcha(): SmsCaptchaInit {
        val data = client.getApiData(
            "https://passport.bilibili.com/x/passport-login/captcha",
            emptyMap(),
        )
        return SmsCaptchaInit(
            token = data.str("token"),
            gt = data.obj("geetest").str("gt"),
            challenge = data.obj("geetest").str("challenge"),
        )
    }

    fun sendSmsCode(request: SmsSendRequest): String {
        val data = client.postFormApiData(
            pathOrUrl = "https://passport.bilibili.com/x/passport-login/web/sms/send",
            form = mapOf(
                "cid" to request.cid.toString(),
                "tel" to request.tel,
                "source" to "main_web",
                "token" to request.token,
                "challenge" to request.challenge,
                "validate" to request.validate,
                "seccode" to request.seccode,
            ),
        )
        return data.str("captcha_key")
    }

    fun loginBySms(request: SmsLoginRequest): Boolean {
        val data = client.postFormApiData(
            pathOrUrl = "https://passport.bilibili.com/x/passport-login/web/login/sms",
            form = mapOf(
                "cid" to request.cid.toString(),
                "tel" to request.tel,
                "code" to request.code,
                "captcha_key" to request.captchaKey,
                "source" to "main_web",
                "keep" to "true",
            ),
        )
        val refreshToken = data.str("refresh_token")
        if (refreshToken.isNotBlank()) client.setRefreshToken(refreshToken)
        return true
    }

    fun getFollowingUps(): List<UpItem> {
        val navData = client.getApiData("/x/web-interface/nav", emptyMap())
        val mid = navData.str("mid").toLongOrNull() ?: return emptyList()
        val pageSize = 50
        var page = 1
        val list = mutableListOf<UpItem>()
        while (page <= 100) {
            val data = client.getApiData(
                "/x/relation/followings",
                mapOf(
                    "vmid" to mid.toString(),
                    "pn" to page.toString(),
                    "ps" to pageSize.toString(),
                    "order" to "desc",
                ),
            )
            val rows = data.arr("list")
            if (rows.isEmpty()) break
            rows.forEach { row ->
                val obj = row.jsonObject
                val upMid = obj.str("mid")
                if (upMid.isBlank()) return@forEach
                list.add(
                    UpItem(
                        mid = upMid,
                        name = obj.str("uname").ifBlank { obj.str("name").ifBlank { "UP" } },
                        face = coverUrl(obj.str("face")),
                    )
                )
            }
            val total = data.str("total").toLongOrNull() ?: 0L
            if (total > 0 && list.size.toLong() >= total) break
            if (rows.size < pageSize) break
            page += 1
        }
        return list.distinctBy { it.mid }
    }

    fun getUpVideos(mid: String, page: Int, pageSize: Int = 30): List<VideoItem> {
        val keys = client.ensureWbiKeys()
        val query = WbiSigner.sign(
            params = mapOf(
                "mid" to mid,
                "pn" to page.toString(),
                "ps" to pageSize.toString(),
                "order" to "pubdate",
            ),
            imgKey = keys.imgKey,
            subKey = keys.subKey,
        )
        val data = client.getApiData("/x/space/wbi/arc/search", query)
        return data.obj("list").arr("vlist").map { item ->
            val row = item.jsonObject
            VideoItem(
                bvid = row.str("bvid"),
                title = row.str("title"),
                cover = coverUrl(row.str("pic")),
                author = row.str("author"),
                authorMid = mid,
                play = row.str("play"),
                danmaku = row.str("video_review"),
                durationText = row.str("length"),
                shareUrl = "https://www.bilibili.com/video/${row.str("bvid")}",
                authorPubTime = formatUpCreatedTime(row.str("created")),
            )
        }.filter { it.bvid.isNotBlank() }
    }

    fun getVideoStat(bvid: String): VideoStat {
        val data = client.getApiData("/x/web-interface/archive/stat", mapOf("bvid" to bvid))
        return VideoStat(
            like = data.str("like"),
            share = data.str("share"),
            reply = data.str("reply"),
        )
    }

    fun getUpInfo(mid: String): UpInfo {
        val keys = client.ensureWbiKeys()
        val query = WbiSigner.sign(
            params = mapOf("mid" to mid),
            imgKey = keys.imgKey,
            subKey = keys.subKey,
        )
        val data = client.getApiData("/x/space/wbi/acc/info", query)
        return UpInfo(
            mid = mid,
            name = data.str("name").ifBlank { data.str("uname") },
            sign = data.str("sign"),
            face = coverUrl(data.str("face")),
        )
    }

    fun getUpStat(mid: String): UpStat {
        val data = client.getApiData("/x/space/upstat", mapOf("mid" to mid))
        return UpStat(
            follower = data.str("follower").toLongOrNull() ?: 0L,
            totalView = data.obj("archive").str("view").toLongOrNull() ?: 0L,
            likes = data.str("likes").toLongOrNull() ?: 0L,
        )
    }

    fun getVideoInfo(bvid: String): VideoInfo {
        val data = client.getApiData("/x/web-interface/view", mapOf("bvid" to bvid))
        val firstCid = data.arr("pages").firstOrNull()?.jsonObject?.str("cid")?.toLongOrNull() ?: 0L
        return VideoInfo(
            bvid = bvid,
            aid = data.str("aid").toLongOrNull() ?: 0L,
            cid = firstCid,
            title = data.str("title"),
        )
    }

    fun getPlayUrl(bvid: String, cid: Long, qn: Int = 80, dash: Boolean = true): PlayUrlResult {
        val keys = client.ensureWbiKeys()
        val query = WbiSigner.sign(
            params = mapOf(
                "bvid" to bvid,
                "cid" to cid.toString(),
                "qn" to qn.toString(),
                "fnver" to "0",
                "fourk" to "1",
                "fnval" to if (dash) "4048" else "0",
            ),
            imgKey = keys.imgKey,
            subKey = keys.subKey,
        )
        val data = client.getApiData("/x/player/wbi/playurl", query)
        val durl = data.arr("durl").firstOrNull()?.jsonObject?.str("url").orEmpty()
        val dashVideo =
            data.obj("dash").arr("video").firstOrNull()?.jsonObject?.str("baseUrl").orEmpty()
        val dashAudio =
            data.obj("dash").arr("audio").firstOrNull()?.jsonObject?.str("baseUrl").orEmpty()
        return PlayUrlResult(
            directUrl = durl,
            dashVideoUrl = dashVideo,
            dashAudioUrl = dashAudio,
        )
    }

    fun addToFavorite(aid: Long, folderIds: List<Long>) {
        if (folderIds.isEmpty()) return
        val csrf = client.cookieValue("bili_jct")
        if (csrf.isBlank()) throw IllegalStateException("缺少 bili_jct，无法收藏")
        client.postFormApiData(
            "/x/v3/fav/resource/deal",
            form = mapOf(
                "rid" to aid.toString(),
                "type" to "2",
                "add_media_ids" to folderIds.joinToString(","),
                "del_media_ids" to "",
                "csrf" to csrf,
            ),
        )
    }

    fun likeVideo(aid: Long, bvid: String, like: Int = 1) {
        val csrf = client.cookieValue("bili_jct")
        if (csrf.isBlank()) throw IllegalStateException("缺少 bili_jct，无法点赞")
        client.postFormApiData(
            "/x/web-interface/archive/like",
            form = mapOf(
                "aid" to aid.toString(),
                "bvid" to bvid,
                "like" to like.toString(),
                "csrf" to csrf,
            ),
        )
    }

}

data class FeedPage(
    val offset: String,
    val hasMore: Boolean,
    val items: List<VideoItem>,
)

data class FavoritePage(
    val hasMore: Boolean,
    val videos: List<VideoItem>,
)

data class FolderItem(
    val id: Long,
    val title: String,
    val count: Int,
)

data class VideoItem(
    val bvid: String,
    val title: String,
    val cover: String,
    val author: String,
    val authorMid: String,
    val play: String,
    val danmaku: String,
    val durationText: String,
    val shareUrl: String,
    val authorFace: String = "",
    val authorPubTime: String = "",
    val shareCount: String = "",
    val commentCount: String = "",
    val likeCount: String = "",
)

data class UpItem(
    val mid: String,
    val name: String,
    val face: String,
)

data class QrCodeData(
    val url: String,
    val qrcodeKey: String,
)

data class QrPollResult(
    val code: Int,
    val message: String,
)

data class SmsCaptchaInit(
    val token: String,
    val gt: String,
    val challenge: String,
)

data class SmsSendRequest(
    val tel: String,
    val token: String,
    val challenge: String,
    val validate: String,
    val seccode: String,
    val cid: Int = 86,
)

data class SmsLoginRequest(
    val tel: String,
    val code: String,
    val captchaKey: String,
    val cid: Int = 86,
)

data class UpInfo(
    val mid: String,
    val name: String,
    val sign: String,
    val face: String,
)

data class UpStat(
    val follower: Long,
    val totalView: Long,
    val likes: Long,
)

data class VideoInfo(
    val bvid: String,
    val aid: Long,
    val cid: Long,
    val title: String,
)

data class PlayUrlResult(
    val directUrl: String,
    val dashVideoUrl: String,
    val dashAudioUrl: String,
)

data class VideoStat(
    val like: String,
    val share: String,
    val reply: String,
)

private fun JsonObject.obj(key: String): JsonObject =
    this[key]?.jsonObject ?: JsonObject(emptyMap())

private fun JsonObject.arr(key: String): JsonArray = this[key]?.jsonArray ?: JsonArray(emptyList())

private fun JsonObject.str(key: String): String {
    val element: JsonElement = this[key] ?: return ""
    return element.toString().trim('"')
}

private fun JsonObject.bool(key: String): Boolean {
    val text = str(key)
    return text == "true" || text == "1"
}

private fun countText(element: JsonElement?): String {
    if (element == null) return ""
    return when (element) {
        is JsonObject -> {
            val count = element["count"] ?: element["value"] ?: element["num"] ?: element["num_str"]
            count?.toString()?.trim('"').orEmpty()
        }

        else -> element.toString().trim('"')
    }
}

private fun parseBvid(jumpUrl: String): String {
    val regex = Regex("/video/(BV[0-9A-Za-z]+)")
    return regex.find(jumpUrl)?.groupValues?.getOrNull(1).orEmpty()
}

private fun coverUrl(raw: String): String {
    if (raw.isBlank()) return ""
    return when {
        raw.startsWith("https://") -> raw
        raw.startsWith("http://") -> "https://${raw.removePrefix("http://")}"
        raw.startsWith("//") -> "https:$raw"
        else -> raw
    }
}

private fun formatUpCreatedTime(raw: String): String {
    val seconds = raw.toLongOrNull() ?: return ""
    if (seconds <= 0L) return ""
    val instant = java.time.Instant.ofEpochSecond(seconds)
    val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(java.time.ZoneId.systemDefault())
    return formatter.format(instant)
}

