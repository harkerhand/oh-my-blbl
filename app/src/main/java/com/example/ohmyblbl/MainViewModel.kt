package cn.harkerhand.ohmyblbl

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.harkerhand.ohmyblbl.data.BiliClient
import cn.harkerhand.ohmyblbl.data.BiliRepository
import cn.harkerhand.ohmyblbl.data.AuthExpiredException
import cn.harkerhand.ohmyblbl.data.FolderItem
import cn.harkerhand.ohmyblbl.data.PlayUrlResult
import cn.harkerhand.ohmyblbl.data.SmsLoginRequest
import cn.harkerhand.ohmyblbl.data.SmsSendRequest
import cn.harkerhand.ohmyblbl.data.UpItem
import cn.harkerhand.ohmyblbl.data.UpInfo
import cn.harkerhand.ohmyblbl.data.UpStat
import cn.harkerhand.ohmyblbl.data.VideoItem
import cn.harkerhand.ohmyblbl.data.VideoInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HomeTab { Feed, WatchLater, Favorite, Up }
enum class LoginMode { Sms, Qr }
enum class UpGroupStatus { Pinned, Normal, Blacklisted }

data class UiState(
    val loginMode: LoginMode = LoginMode.Sms,
    val checkingLogin: Boolean = false,
    val isLoggedIn: Boolean = false,
    val qrUrl: String = "",
    val qrKey: String = "",
    val qrRemainSeconds: Int = 0,
    val smsTel: String = "",
    val smsCode: String = "",
    val smsToken: String = "",
    val smsGt: String = "",
    val smsInitChallenge: String = "",
    val smsSolvedChallenge: String = "",
    val smsValidate: String = "",
    val smsSeccode: String = "",
    val smsCaptchaKey: String = "",
    val smsCooldown: Int = 0,
    val activeTab: HomeTab = HomeTab.Feed,
    val loading: Boolean = false,
    val message: String = "",
    val feedOffset: String = "",
    val feedHasMore: Boolean = true,
    val feedItems: List<VideoItem> = emptyList(),
    val selectedUpItems: List<VideoItem> = emptyList(),
    val selectedUpPage: Int = 1,
    val selectedUpHasMore: Boolean = true,
    val likedBvids: Set<String> = emptySet(),
    val followedUps: List<UpItem> = emptyList(),
    val pinnedUpMids: List<String> = emptyList(),
    val blacklistedUpMids: List<String> = emptyList(),
    val selectedUpMid: String = "",
    val laterPage: Int = 1,
    val laterHasMore: Boolean = true,
    val laterItems: List<VideoItem> = emptyList(),
    val folders: List<FolderItem> = emptyList(),
    val selectedFolderId: Long? = null,
    val favoritePage: Int = 1,
    val favoriteHasMore: Boolean = true,
    val favoriteItems: List<VideoItem> = emptyList(),
    val upMidInput: String = "",
    val upInfo: UpInfo? = null,
    val upStat: UpStat? = null,
    val upPage: Int = 1,
    val upHasMore: Boolean = true,
    val upItems: List<VideoItem> = emptyList(),
    val lastPlayUrl: PlayUrlResult? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = BiliRepository(BiliClient(app))
    private val prefs = app.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
    private var qrPollingJob: Job? = null
    private var qrCountdownJob: Job? = null
    private var smsCooldownJob: Job? = null
    private val videoInfoCache = LinkedHashMap<String, VideoInfo>()
    private val avatarCacheByMid = LinkedHashMap<String, String>()
    private val coverCacheByBvid = LinkedHashMap<String, String>()
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        _state.update {
            it.copy(
                pinnedUpMids = loadPinnedUps(),
                blacklistedUpMids = loadBlacklistedUps(),
            )
        }
        if (isFirstLaunch()) {
            _state.update { it.copy(checkingLogin = false, isLoggedIn = false) }
        } else {
            checkLogin()
        }
    }

    fun switchLoginMode(mode: LoginMode) {
        _state.update { it.copy(loginMode = mode, message = "") }
    }

    fun onUpMidInputChange(value: String) {
        _state.update { it.copy(upMidInput = value) }
    }

    fun onSmsTelChange(value: String) {
        _state.update { it.copy(smsTel = value) }
    }

    fun onSmsCodeChange(value: String) {
        _state.update { it.copy(smsCode = value) }
    }

    fun selectUpFilter(mid: String) {
        if (_state.value.blacklistedUpMids.contains(mid)) {
            _state.update { it.copy(message = "该UP已在黑名单") }
            return
        }
        if (mid == _state.value.selectedUpMid) return
        _state.update {
            it.copy(
                selectedUpMid = mid,
                selectedUpItems = emptyList(),
                selectedUpPage = 1,
                selectedUpHasMore = true,
            )
        }
        if (_state.value.isLoggedIn) loadSelectedUpVideos(refresh = true)
    }

    fun togglePinnedUp(mid: String) {
        val current = statusForUp(mid)
        val next = if (current == UpGroupStatus.Pinned) UpGroupStatus.Normal else UpGroupStatus.Pinned
        setUpGroupStatus(mid, next)
    }

    fun statusForUp(mid: String): UpGroupStatus {
        val s = _state.value
        return when {
            s.blacklistedUpMids.contains(mid) -> UpGroupStatus.Blacklisted
            s.pinnedUpMids.contains(mid) -> UpGroupStatus.Pinned
            else -> UpGroupStatus.Normal
        }
    }

    fun setUpGroupStatus(mid: String, status: UpGroupStatus) {
        val normalized = mid.trim()
        if (normalized.isBlank()) return
        val current = _state.value
        val pinned = current.pinnedUpMids.toMutableSet()
        val blacklisted = current.blacklistedUpMids.toMutableSet()
        when (status) {
            UpGroupStatus.Pinned -> {
                pinned.add(normalized)
                blacklisted.remove(normalized)
            }
            UpGroupStatus.Normal -> {
                pinned.remove(normalized)
                blacklisted.remove(normalized)
            }
            UpGroupStatus.Blacklisted -> {
                blacklisted.add(normalized)
                pinned.remove(normalized)
            }
        }
        val pinnedRows = pinned.toList()
        val blackRows = blacklisted.toList()
        savePinnedUps(pinnedRows)
        saveBlacklistedUps(blackRows)
        _state.update {
            it.copy(
                pinnedUpMids = pinnedRows,
                blacklistedUpMids = blackRows,
                selectedUpMid = if (blackRows.contains(it.selectedUpMid)) "" else it.selectedUpMid,
                followedUps = applyPinnedOrder(it.followedUps, pinnedRows),
            )
        }
    }

    fun clearUpFilter() {
        _state.update { it.copy(selectedUpMid = "") }
    }

    fun loadSelectedUpVideos(refresh: Boolean) {
        val mid = _state.value.selectedUpMid.trim()
        if (mid.isBlank()) return
        if (_state.value.loading) return
        if (!refresh && !_state.value.selectedUpHasMore) return
        val nextPage = if (refresh) 1 else _state.value.selectedUpPage + 1
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = "") }
            runCatching {
                withContext(Dispatchers.IO) {
                    coroutineScope {
                        repository.getUpVideos(mid, nextPage).map { row ->
                            async {
                                if (row.bvid.isBlank()) {
                                    row
                                } else {
                                    val stat = runCatching { repository.getVideoStat(row.bvid) }.getOrNull()
                                    if (stat == null) {
                                        row
                                    } else {
                                        row.copy(
                                            likeCount = stat.like.ifBlank { row.likeCount },
                                            shareCount = stat.share.ifBlank { row.shareCount },
                                            commentCount = stat.reply.ifBlank { row.commentCount },
                                        )
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                }
            }.onSuccess { rows ->
                val upFace = _state.value.followedUps.firstOrNull { it.mid == mid }?.face.orEmpty()
                val rowsWithFace = if (upFace.isBlank()) {
                    rows
                } else {
                    rows.map { row ->
                        if (row.authorFace.isBlank()) row.copy(authorFace = upFace) else row
                    }
                }
                val cachedRows = mergeMediaCache(rowsWithFace)
                _state.update {
                    it.copy(
                        loading = false,
                        selectedUpPage = nextPage,
                        selectedUpHasMore = cachedRows.isNotEmpty(),
                        selectedUpItems = if (refresh) cachedRows else it.selectedUpItems + cachedRows,
                    )
                }
            }.onFailure { error ->
                handleCommonFailure("加载 UP 视频失败", error)
            }
        }
    }

    fun switchTab(tab: HomeTab) {
        _state.update { it.copy(activeTab = tab, message = "") }
        when (tab) {
            HomeTab.Feed -> if (_state.value.feedItems.isEmpty()) loadFeed(refresh = true)
            HomeTab.WatchLater -> if (_state.value.laterItems.isEmpty()) loadWatchLater(refresh = true)
            HomeTab.Favorite -> if (_state.value.folders.isEmpty()) loadFavoriteFolders()
            HomeTab.Up -> Unit
        }
    }

    fun checkLogin() {
        viewModelScope.launch {
            _state.update { it.copy(checkingLogin = true, message = "") }
            runCatching {
                withContext(Dispatchers.IO) { repository.checkLogin() }
            }.onSuccess { ok ->
                _state.update { it.copy(checkingLogin = false, isLoggedIn = ok) }
                if (ok && _state.value.feedItems.isEmpty()) {
                    loadFeed(refresh = true)
                }
                if (ok && _state.value.followedUps.isEmpty()) {
                    loadFollowingUps()
                }
            }.onFailure { error ->
                if (handleAuthExpired(error)) return@onFailure
                _state.update {
                    it.copy(
                        checkingLogin = false,
                        isLoggedIn = false,
                        message = "检查登录失败: ${error.message ?: "unknown"}",
                    )
                }
            }
        }
    }

    fun initSmsCaptcha() {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = "") }
            runCatching {
                withContext(Dispatchers.IO) { repository.getLoginCaptcha() }
            }.onSuccess { init ->
                _state.update {
                    it.copy(
                        loading = false,
                        loginMode = LoginMode.Sms,
                        smsToken = init.token,
                        smsGt = init.gt,
                        smsInitChallenge = init.challenge,
                        smsSolvedChallenge = "",
                        smsValidate = "",
                        smsSeccode = "",
                        message = if (init.gt.isNotBlank()) {
                            "验证码初始化完成"
                        } else {
                            "验证码初始化返回异常，稍后重试"
                        },
                    )
                }
            }.onFailure { error ->
                handleCommonFailure("初始化短信验证码失败", error)
            }
        }
    }

    fun sendSmsCode() {
        val state = _state.value
        if (state.smsCooldown > 0) return
        if (state.smsTel.isBlank()) {
            _state.update { it.copy(message = "请输入手机号") }
            return
        }
        if (state.smsToken.isBlank() || state.smsInitChallenge.isBlank()) {
            _state.update { it.copy(message = "请先初始化短信验证码") }
            return
        }
        if (state.smsValidate.isBlank() || state.smsSeccode.isBlank()) {
            _state.update { it.copy(message = "请先完成极验验证") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = "") }
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.sendSmsCode(
                        SmsSendRequest(
                            tel = state.smsTel.trim(),
                            token = state.smsToken.trim(),
                            challenge = state.smsSolvedChallenge.ifBlank { state.smsInitChallenge }.trim(),
                            validate = state.smsValidate.trim(),
                            seccode = state.smsSeccode.trim(),
                        )
                    )
                }
            }.onSuccess { captchaKey ->
                _state.update {
                    it.copy(
                        loading = false,
                        smsCaptchaKey = captchaKey,
                        message = "短信验证码已发送",
                    )
                }
                startSmsCooldown()
            }.onFailure { error ->
                handleCommonFailure("发送短信验证码失败", error)
            }
        }
    }

    fun loginWithSms() {
        val state = _state.value
        if (state.smsTel.isBlank() || state.smsCode.isBlank()) {
            _state.update { it.copy(message = "请输入手机号和短信验证码") }
            return
        }
        if (state.smsCaptchaKey.isBlank()) {
            _state.update { it.copy(message = "请先发送短信验证码") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = "") }
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loginBySms(
                        SmsLoginRequest(
                            tel = state.smsTel.trim(),
                            code = state.smsCode.trim(),
                            captchaKey = state.smsCaptchaKey.trim(),
                        )
                    )
                }
            }.onSuccess {
                _state.update {
                    it.copy(
                        loading = false,
                        isLoggedIn = true,
                        message = "短信登录成功",
                    )
                }
                loadFeed(refresh = true)
                loadFollowingUps()
            }.onFailure { error ->
                handleCommonFailure("短信登录失败", error)
            }
        }
    }

    fun onSmsCaptchaSolved(challenge: String, validate: String, seccode: String) {
        _state.update {
            it.copy(
                smsSolvedChallenge = challenge,
                smsValidate = validate,
                smsSeccode = seccode,
                message = "极验验证完成",
            )
        }
    }

    fun startQrLogin() {
        if (_state.value.loading) return
        stopQrLoop()
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = "") }
            runCatching {
                withContext(Dispatchers.IO) { repository.getQrcode() }
            }.onSuccess { qr ->
                if (qr.qrcodeKey.isBlank() || qr.url.isBlank()) {
                    _state.update { it.copy(loading = false, message = "二维码生成失败") }
                    return@onSuccess
                }
                _state.update {
                    it.copy(
                        loading = false,
                        qrUrl = qr.url,
                        qrKey = qr.qrcodeKey,
                        qrRemainSeconds = 180,
                        loginMode = LoginMode.Qr,
                    )
                }
                startQrCountdown()
                startQrPolling()
            }.onFailure { error ->
                handleCommonFailure("二维码登录失败", error)
            }
        }
    }

    fun cancelQrLogin() {
        stopQrLoop()
        _state.update { it.copy(qrUrl = "", qrKey = "", qrRemainSeconds = 0) }
    }

    fun logout() {
        stopQrLoop()
        stopSmsCooldown()
        viewModelScope.launch(Dispatchers.IO) { repository.clearAuth() }
        _state.update {
            UiState(
                checkingLogin = false,
                isLoggedIn = false,
                message = "已退出登录",
            )
        }
    }

    fun loadFeed(refresh: Boolean) {
        if (_state.value.loading) return
        val hasMore = _state.value.feedHasMore
        if (!refresh && !hasMore) return
        val offset = if (refresh) "" else _state.value.feedOffset
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = "") }
            runCatching {
                withContext(Dispatchers.IO) { repository.getFeed(offset) }
            }.onSuccess { page ->
                val cachedRows = mergeMediaCache(page.items)
                _state.update {
                    it.copy(
                        loading = false,
                        feedOffset = page.offset,
                        feedHasMore = page.hasMore,
                        feedItems = if (refresh) cachedRows else it.feedItems + cachedRows,
                    )
                }
            }.onFailure { error ->
                handleCommonFailure("加载动态失败", error)
            }
        }
    }

    fun loadFollowingUps() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.getFollowingUps() }
            }.onSuccess { rows ->
                rows.forEach { up ->
                    if (up.mid.isNotBlank() && up.face.isNotBlank()) {
                        putCache(avatarCacheByMid, up.mid, up.face)
                    }
                }
                val pinned = loadPinnedUps()
                val blacklisted = loadBlacklistedUps()
                _state.update {
                    it.copy(
                        pinnedUpMids = pinned,
                        blacklistedUpMids = blacklisted,
                        selectedUpMid = if (blacklisted.contains(it.selectedUpMid)) "" else it.selectedUpMid,
                        followedUps = applyPinnedOrder(rows, pinned),
                    )
                }
            }.onFailure { error ->
                handleAuthExpired(error)
            }
        }
    }

    fun loadUpHome(refresh: Boolean) {
        val mid = _state.value.upMidInput.trim()
        if (mid.isBlank()) {
            _state.update { it.copy(message = "请输入 MID") }
            return
        }
        if (_state.value.loading) return
        val nextPage = if (refresh) 1 else _state.value.upPage + 1
        if (!refresh && !_state.value.upHasMore) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = "") }
            runCatching {
                withContext(Dispatchers.IO) {
                    coroutineScope {
                        val current = _state.value
                        val shouldFetchProfile = refresh && current.upInfo?.mid != mid
                        val infoDeferred = async {
                            when {
                                shouldFetchProfile -> repository.getUpInfo(mid)
                                refresh -> current.upInfo
                                else -> current.upInfo
                            }
                        }
                        val statDeferred = async {
                            when {
                                shouldFetchProfile -> repository.getUpStat(mid)
                                refresh -> current.upStat
                                else -> current.upStat
                            }
                        }
                        val videosDeferred = async { repository.getUpVideos(mid, nextPage) }
                        Triple(
                            infoDeferred.await(),
                            statDeferred.await(),
                            videosDeferred.await(),
                        )
                    }
                }
            }.onSuccess { (info, stat, videos) ->
                val cachedRows = mergeMediaCache(videos)
                _state.update {
                    it.copy(
                        loading = false,
                        upInfo = info ?: it.upInfo,
                        upStat = stat ?: it.upStat,
                        upPage = nextPage,
                        upHasMore = cachedRows.isNotEmpty(),
                        upItems = if (refresh) cachedRows else it.upItems + cachedRows,
                    )
                }
            }.onFailure { error ->
                handleCommonFailure("加载 UP 主页失败", error)
            }
        }
    }

    fun loadWatchLater(refresh: Boolean) {
        if (_state.value.loading) return
        if (!refresh && !_state.value.laterHasMore) return
        val nextPage = if (refresh) 1 else _state.value.laterPage + 1
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = "") }
            runCatching {
                withContext(Dispatchers.IO) { repository.getWatchLater(nextPage) }
            }.onSuccess { items ->
                val cachedRows = mergeMediaCache(items)
                _state.update {
                    it.copy(
                        loading = false,
                        laterPage = nextPage,
                        laterHasMore = cachedRows.size >= 20,
                        laterItems = if (refresh) cachedRows else it.laterItems + cachedRows,
                    )
                }
            }.onFailure { error ->
                handleCommonFailure("加载稍后再看失败", error)
            }
        }
    }

    fun loadFavoriteFolders() {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = "") }
            runCatching {
                withContext(Dispatchers.IO) { repository.getFavoriteFolders() }
            }.onSuccess { folders ->
                val selected = folders.firstOrNull()?.id
                _state.update {
                    it.copy(
                        loading = false,
                        folders = folders,
                        selectedFolderId = selected,
                        favoritePage = 1,
                        favoriteItems = emptyList(),
                        favoriteHasMore = true,
                    )
                }
                if (selected != null) {
                    loadFavoriteVideos(selected, refresh = true)
                }
            }.onFailure { error ->
                handleCommonFailure("加载收藏夹失败", error)
            }
        }
    }

    fun switchFolder(folderId: Long) {
        _state.update {
            it.copy(
                selectedFolderId = folderId,
                favoriteItems = emptyList(),
                favoritePage = 1,
                favoriteHasMore = true,
            )
        }
        loadFavoriteVideos(folderId, refresh = true)
    }

    fun loadFavoriteVideos(folderId: Long, refresh: Boolean) {
        if (_state.value.loading) return
        if (!refresh && !_state.value.favoriteHasMore) return
        val nextPage = if (refresh) 1 else _state.value.favoritePage + 1
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = "") }
            runCatching {
                withContext(Dispatchers.IO) { repository.getFavoriteVideos(folderId, nextPage) }
            }.onSuccess { page ->
                val cachedRows = mergeMediaCache(page.videos)
                _state.update {
                    it.copy(
                        loading = false,
                        favoritePage = nextPage,
                        favoriteHasMore = page.hasMore,
                        favoriteItems = if (refresh) cachedRows else it.favoriteItems + cachedRows,
                    )
                }
            }.onFailure { error ->
                handleCommonFailure("加载收藏视频失败", error)
            }
        }
    }

    fun addVideoToSelectedFavorite(video: VideoItem) {
        viewModelScope.launch {
            val folderId = ensureSelectedFolder()
            if (folderId == null) {
                _state.update { it.copy(message = "没有可用收藏夹") }
                return@launch
            }
            _state.update { it.copy(loading = true, message = "") }
            runCatching {
                withContext(Dispatchers.IO) {
                    val info = resolveVideoInfo(video.bvid)
                    if (info.aid <= 0) error("无法获取 aid")
                    repository.addToFavorite(info.aid, listOf(folderId))
                }
            }.onSuccess {
                _state.update { it.copy(loading = false, message = "已收藏到当前收藏夹") }
            }.onFailure { error ->
                handleCommonFailure("收藏失败", error)
            }
        }
    }

    fun likeVideo(video: VideoItem) {
        if (_state.value.loading) return
        val willLike = !_state.value.likedBvids.contains(video.bvid)
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = "") }
            runCatching {
                withContext(Dispatchers.IO) {
                    val info = resolveVideoInfo(video.bvid)
                    if (info.aid <= 0) error("无法获取 aid")
                    repository.likeVideo(
                        aid = info.aid,
                        bvid = video.bvid,
                        like = if (willLike) 1 else 2,
                    )
                }
            }.onSuccess {
                _state.update {
                    val updatedLiked = if (willLike) {
                        it.likedBvids + video.bvid
                    } else {
                        it.likedBvids - video.bvid
                    }
                    it.copy(
                        loading = false,
                        message = if (willLike) "点赞成功" else "已取消点赞",
                        likedBvids = updatedLiked,
                        feedItems = it.feedItems.map { item ->
                            if (item.bvid == video.bvid) {
                                item.copy(likeCount = adjustLikeCountText(item.likeCount, if (willLike) 1 else -1))
                            } else {
                                item
                            }
                        },
                        selectedUpItems = it.selectedUpItems.map { item ->
                            if (item.bvid == video.bvid) {
                                item.copy(likeCount = adjustLikeCountText(item.likeCount, if (willLike) 1 else -1))
                            } else {
                                item
                            }
                        },
                    )
                }
            }.onFailure { error ->
                handleCommonFailure(if (willLike) "点赞失败" else "取消点赞失败", error)
            }
        }
    }

    fun resolvePlayUrl(video: VideoItem) {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = "") }
            runCatching {
                withContext(Dispatchers.IO) {
                    val info = resolveVideoInfo(video.bvid)
                    if (info.cid <= 0) error("无法获取 cid")
                    repository.getPlayUrl(video.bvid, info.cid)
                }
            }.onSuccess { play ->
                _state.update {
                    it.copy(
                        loading = false,
                        lastPlayUrl = play,
                        message = play.directUrl.ifBlank { play.dashVideoUrl }.ifBlank { "未解析到播放地址" },
                    )
                }
            }.onFailure { error ->
                handleCommonFailure("解析播放地址失败", error)
            }
        }
    }

    fun openUp(mid: String) {
        val normalized = mid.trim()
        if (normalized.isBlank()) return
        _state.update {
            it.copy(
                upMidInput = normalized,
                activeTab = HomeTab.Up,
            )
        }
        loadUpHome(refresh = true)
    }

    private fun startQrCountdown() {
        qrCountdownJob?.cancel()
        qrCountdownJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val remain = _state.value.qrRemainSeconds
                if (remain <= 0) {
                    _state.update { it.copy(message = "二维码已过期，请刷新", qrUrl = "", qrKey = "") }
                    stopQrLoop()
                    break
                }
                _state.update { it.copy(qrRemainSeconds = remain - 1) }
            }
        }
    }

    private fun startQrPolling() {
        qrPollingJob?.cancel()
        qrPollingJob = viewModelScope.launch {
            while (true) {
                delay(3000)
                val key = _state.value.qrKey
                if (key.isBlank()) break
                val result = runCatching {
                    withContext(Dispatchers.IO) { repository.pollQrcode(key) }
                }.getOrElse { error ->
                    if (!handleAuthExpired(error)) {
                        _state.update { it.copy(message = "扫码轮询失败: ${error.message}") }
                    }
                    stopQrLoop()
                    break
                }
                when (result.code) {
                    86101 -> Unit
                    86090 -> _state.update { it.copy(message = "请在手机确认登录") }
                    86038 -> {
                        _state.update { it.copy(message = "二维码已过期，请刷新", qrUrl = "", qrKey = "") }
                        stopQrLoop()
                        break
                    }
                    0 -> {
                        stopQrLoop()
                        _state.update {
                            it.copy(
                                isLoggedIn = true,
                                qrUrl = "",
                                qrKey = "",
                                qrRemainSeconds = 0,
                                message = "扫码登录成功",
                            )
                        }
                        loadFeed(refresh = true)
                        loadFollowingUps()
                        break
                    }
                    else -> _state.update { it.copy(message = result.message.ifBlank { "扫码状态异常" }) }
                }
            }
        }
    }

    private fun stopQrLoop() {
        qrPollingJob?.cancel()
        qrPollingJob = null
        qrCountdownJob?.cancel()
        qrCountdownJob = null
    }

    private fun startSmsCooldown(seconds: Int = 60) {
        smsCooldownJob?.cancel()
        _state.update { it.copy(smsCooldown = seconds) }
        smsCooldownJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val remain = _state.value.smsCooldown
                if (remain <= 0) break
                _state.update { it.copy(smsCooldown = remain - 1) }
            }
        }
    }

    private fun stopSmsCooldown() {
        smsCooldownJob?.cancel()
        smsCooldownJob = null
        _state.update { it.copy(smsCooldown = 0) }
    }

    private suspend fun ensureSelectedFolder(): Long? {
        val existing = _state.value.selectedFolderId
        if (existing != null && existing > 0) return existing
        val folders = withContext(Dispatchers.IO) { repository.getFavoriteFolders() }
        val selected = folders.firstOrNull()?.id
        _state.update { it.copy(folders = folders, selectedFolderId = selected) }
        return selected
    }

    private suspend fun resolveVideoInfo(bvid: String): VideoInfo {
        videoInfoCache[bvid]?.let { return it }
        val info = repository.getVideoInfo(bvid)
        if (videoInfoCache.size > 200) {
            val firstKey = videoInfoCache.keys.firstOrNull()
            if (firstKey != null) videoInfoCache.remove(firstKey)
        }
        videoInfoCache[bvid] = info
        return info
    }

    private fun loadPinnedUps(): List<String> {
        val raw = prefs.getString(KEY_PINNED_UPS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    private fun savePinnedUps(rows: List<String>) {
        prefs.edit().putString(KEY_PINNED_UPS, rows.joinToString(",")).apply()
    }

    private fun loadBlacklistedUps(): List<String> {
        val raw = prefs.getString(KEY_BLACKLISTED_UPS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    private fun saveBlacklistedUps(rows: List<String>) {
        prefs.edit().putString(KEY_BLACKLISTED_UPS, rows.joinToString(",")).apply()
    }

    private fun applyPinnedOrder(rows: List<UpItem>, pinned: List<String>): List<UpItem> {
        if (rows.isEmpty()) return rows
        val unique = LinkedHashMap<String, UpItem>()
        rows.forEach { if (!unique.containsKey(it.mid)) unique[it.mid] = it }
        val pinSet = pinned.toSet()
        val result = mutableListOf<UpItem>()
        pinned.forEach { mid -> unique[mid]?.let { result.add(it) } }
        unique.values.forEach { if (!pinSet.contains(it.mid)) result.add(it) }
        return result
    }

    private fun isFirstLaunch(): Boolean {
        val firstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        if (firstLaunch) {
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
        }
        return firstLaunch
    }

    private fun handleCommonFailure(prefix: String, error: Throwable) {
        if (handleAuthExpired(error)) return
        _state.update { it.copy(loading = false, message = "$prefix: ${error.message}") }
    }

    private fun handleAuthExpired(error: Throwable): Boolean {
        if (error !is AuthExpiredException) return false
        stopQrLoop()
        stopSmsCooldown()
        viewModelScope.launch(Dispatchers.IO) { repository.clearAuth() }
        _state.update {
            UiState(
                checkingLogin = false,
                isLoggedIn = false,
                message = "登录状态异常，请重新登录",
            )
        }
        return true
    }

    companion object {
        private const val PREFS_UI = "ohmyblbl_ui"
        private const val KEY_PINNED_UPS = "pinned_ups"
        private const val KEY_BLACKLISTED_UPS = "blacklisted_ups"
        private const val KEY_FIRST_LAUNCH = "first_launch"
    }

    private fun adjustLikeCountText(raw: String, delta: Int): String {
        val source = raw.trim()
        if (source.isBlank()) return if (delta > 0) "1" else "0"
        val base = parseCompactCount(source)
        val next = (base + delta).coerceAtLeast(0L)
        return next.toString()
    }

    private fun parseCompactCount(text: String): Long {
        val t = text.trim()
        return when {
            t.endsWith("亿") -> ((t.dropLast(1).toDoubleOrNull() ?: 0.0) * 100_000_000L).toLong()
            t.endsWith("万") -> ((t.dropLast(1).toDoubleOrNull() ?: 0.0) * 10_000L).toLong()
            else -> t.toLongOrNull() ?: t.toDoubleOrNull()?.toLong() ?: 0L
        }
    }

    private fun mergeMediaCache(rows: List<VideoItem>): List<VideoItem> {
        if (rows.isEmpty()) return rows
        return rows.map { item ->
            val cachedAvatar = avatarCacheByMid[item.authorMid].orEmpty()
            val cachedCover = coverCacheByBvid[item.bvid].orEmpty()
            val avatar = if (item.authorFace.isNotBlank()) item.authorFace else cachedAvatar
            val cover = if (item.cover.isNotBlank()) item.cover else cachedCover
            if (item.authorMid.isNotBlank() && avatar.isNotBlank()) {
                putCache(avatarCacheByMid, item.authorMid, avatar)
            }
            if (item.bvid.isNotBlank() && cover.isNotBlank()) {
                putCache(coverCacheByBvid, item.bvid, cover)
            }
            if (avatar != item.authorFace || cover != item.cover) {
                item.copy(authorFace = avatar, cover = cover)
            } else {
                item
            }
        }
    }

    private fun putCache(cache: LinkedHashMap<String, String>, key: String, value: String) {
        if (key.isBlank() || value.isBlank()) return
        cache[key] = value
        if (cache.size > 4000) {
            val firstKey = cache.keys.firstOrNull() ?: return
            cache.remove(firstKey)
        }
    }
}

