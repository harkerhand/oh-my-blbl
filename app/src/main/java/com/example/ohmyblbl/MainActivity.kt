package cn.harkerhand.ohmyblbl

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.harkerhand.ohmyblbl.ui.theme.BlblBgBottom
import cn.harkerhand.ohmyblbl.ui.theme.BlblBgTop
import cn.harkerhand.ohmyblbl.ui.theme.BlblPinkSoft
import cn.harkerhand.ohmyblbl.ui.theme.OhMyBLBLTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

private enum class CaptchaAction {
    SmsSend,
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OhMyBLBLTheme {
                CompositionLocalProvider(LocalRippleConfiguration provides null) {
                    App(
                        viewModel = viewModel,
                        openVideo = ::openVideo,
                        shareUrl = ::shareUrl,
                    )
                }
            }
        }
    }

    private fun openVideo(bvid: String) {
        if (bvid.isBlank()) return
        val appIntent = Intent(Intent.ACTION_VIEW, "bilibili://video/$bvid".toUri())
        val webIntent = Intent(Intent.ACTION_VIEW, "https://www.bilibili.com/video/$bvid".toUri())
        try {
            startActivity(appIntent)
        } catch (_: ActivityNotFoundException) {
            startActivity(webIntent)
        }
    }

    private fun shareUrl(url: String) {
        if (url.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(intent, "分享链接"))
    }
}

@Composable
private fun App(
    viewModel: MainViewModel,
    openVideo: (String) -> Unit,
    shareUrl: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var smsCaptchaVisible by remember { mutableStateOf(false) }
    var upManagerVisible by remember { mutableStateOf(false) }
    var logoutConfirmVisible by remember { mutableStateOf(false) }
    var captchaAction by remember { mutableStateOf(CaptchaAction.SmsSend) }
    var filterAutoScrollToken by remember { mutableIntStateOf(0) }
    val feedListState = rememberLazyListState()
    val inUpMode = state.selectedUpMid.isNotBlank()
    val visibleUps = state.followedUps.filterNot { state.blacklistedUpMids.contains(it.mid) }
    val feedItems = if (!inUpMode) {
        state.feedItems.filterNot { state.blacklistedUpMids.contains(it.authorMid) }
    } else {
        state.selectedUpItems
    }
    val feedHasMore = if (!inUpMode) state.feedHasMore else state.selectedUpHasMore

    LaunchedEffect(state.message) {
        if (state.message.isNotBlank()) snackbarHostState.showSnackbar(state.message)
    }

    LaunchedEffect(state.isLoggedIn, state.activeTab, state.followedUps.size) {
        if (state.isLoggedIn && state.activeTab == HomeTab.Feed && state.followedUps.isEmpty()) {
            viewModel.loadFollowingUps()
        }
    }

    ScreenBackdrop {
        if (!state.isLoggedIn) {
            LoginScreen(
                state = state,
                onSwitchLoginMode = viewModel::switchLoginMode,
                onSmsTelChange = viewModel::onSmsTelChange,
                onSmsCodeChange = viewModel::onSmsCodeChange,
                onPrepareSmsSend = {
                    captchaAction = CaptchaAction.SmsSend
                    viewModel.initSmsCaptcha()
                    smsCaptchaVisible = true
                },
                onSmsLogin = viewModel::loginWithSms,
                onStartQrLogin = viewModel::startQrLogin,
                onCancelQrLogin = viewModel::cancelQrLogin,
                onSaveQrAndOpenBiliScanner = {
                    coroutineScope.launch {
                        val msg = saveQrAndOpenBiliScanner(context, state.qrUrl)
                        snackbarHostState.showSnackbar(msg)
                    }
                },
                snackbarHostState = snackbarHostState,
            )
            if (smsCaptchaVisible && state.smsGt.isNotBlank() && state.smsInitChallenge.isNotBlank()) {
                SmsCaptchaDialog(
                    gt = state.smsGt,
                    challenge = state.smsInitChallenge,
                    onDismiss = { smsCaptchaVisible = false },
                    onSolved = { challenge, validate, seccode ->
                        smsCaptchaVisible = false
                        viewModel.onSmsCaptchaSolved(challenge, validate, seccode)
                        when (captchaAction) {
                            CaptchaAction.SmsSend -> viewModel.sendSmsCode()
                        }
                    },
                )
            }
            return@ScreenBackdrop
        }

        if (logoutConfirmVisible) {
            LogoutConfirmDialog(
                onDismiss = { logoutConfirmVisible = false },
                onConfirm = {
                    logoutConfirmVisible = false
                    viewModel.logout()
                },
            )
        }

        BackHandler(enabled = upManagerVisible) {
            upManagerVisible = false
        }

        BackHandler(enabled = !upManagerVisible && state.activeTab != HomeTab.Feed) {
            viewModel.switchTab(HomeTab.Feed)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.statusBars,
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    AnimatedContent(
                        targetState = state.activeTab,
                        transitionSpec = {
                            val forward = initialState == HomeTab.Feed && targetState != HomeTab.Feed
                            if (forward) {
                                (slideInHorizontally(
                                    animationSpec = tween(220),
                                    initialOffsetX = { full -> full },
                                ) + fadeIn(animationSpec = tween(180)))
                                    .togetherWith(
                                        slideOutHorizontally(
                                            animationSpec = tween(200),
                                            targetOffsetX = { full -> -full / 4 },
                                        ) + fadeOut(animationSpec = tween(140))
                                    ).using(SizeTransform(clip = false))
                            } else {
                                (slideInHorizontally(
                                    animationSpec = tween(220),
                                    initialOffsetX = { full -> -full / 4 },
                                ) + fadeIn(animationSpec = tween(180)))
                                    .togetherWith(
                                        slideOutHorizontally(
                                            animationSpec = tween(200),
                                            targetOffsetX = { full -> full },
                                        ) + fadeOut(animationSpec = tween(140))
                                    ).using(SizeTransform(clip = false))
                            }
                        },
                        label = "home-tab-transition",
                    ) { tab ->
                        when (tab) {
                            HomeTab.Feed -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    FeedHeader(
                                        onScrollToTop = {
                                            coroutineScope.launch {
                                                smoothScrollToTop(feedListState)
                                            }
                                        },
                                        onFavorite = { viewModel.switchTab(HomeTab.Favorite) },
                                        onWatchLater = { viewModel.switchTab(HomeTab.WatchLater) },
                                        onLogout = { logoutConfirmVisible = true },
                                    )
                                    FeedHomeScreen(
                                        listState = feedListState,
                                        items = feedItems,
                                        likedBvids = state.likedBvids,
                                        ups = visibleUps,
                                        selectedUpMid = state.selectedUpMid,
                                        autoScrollFilterToken = filterAutoScrollToken,
                                        loading = state.loading,
                                        hasMore = feedHasMore,
                                        onOpenManager = { upManagerVisible = true },
                                        onSelectUp = { mid ->
                                            viewModel.selectUpFilter(mid)
                                        },
                                        onClearUp = viewModel::clearUpFilter,
                                        onLoadMore = {
                                            if (inUpMode) {
                                                viewModel.loadSelectedUpVideos(refresh = false)
                                            } else {
                                                viewModel.loadFeed(refresh = false)
                                            }
                                        },
                                        onPlay = { openVideo(it.bvid) },
                                        onOpenUp = { mid ->
                                            if (mid.isBlank()) return@FeedHomeScreen
                                            filterAutoScrollToken += 1
                                            viewModel.selectUpFilter(mid)
                                            if (state.activeTab != HomeTab.Feed) viewModel.switchTab(HomeTab.Feed)
                                            coroutineScope.launch {
                                                smoothScrollToTop(feedListState)
                                            }
                                        },
                                        onLike = viewModel::likeVideo,
                                        onShare = shareUrl,
                                    )
                                }
                            }

                            HomeTab.WatchLater -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    SecondaryHeader(
                                        title = titleForTab(tab),
                                        onBack = { viewModel.switchTab(HomeTab.Feed) },
                                    )
                                    CollectionScreen(
                                        items = state.laterItems,
                                        loading = state.loading,
                                        hasMore = state.laterHasMore,
                                        onLoadMore = { viewModel.loadWatchLater(refresh = false) },
                                        openVideo = openVideo,
                                        header = null,
                                        emptyText = "暂无稍后再看视频",
                                    )
                                }
                            }

                            HomeTab.Favorite -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    SecondaryHeader(
                                        title = titleForTab(tab),
                                        onBack = { viewModel.switchTab(HomeTab.Feed) },
                                    )
                                    CollectionScreen(
                                        items = state.favoriteItems,
                                        loading = state.loading,
                                        hasMore = state.favoriteHasMore,
                                        onLoadMore = {
                                            state.selectedFolderId?.let { viewModel.loadFavoriteVideos(it, refresh = false) }
                                        },
                                        openVideo = openVideo,
                                        header = {
                                            FavoriteFolderStrip(
                                                folders = state.folders,
                                                selectedFolderId = state.selectedFolderId,
                                                onSelectFolder = viewModel::switchFolder,
                                                onLoadFolders = viewModel::loadFavoriteFolders,
                                                loading = state.loading,
                                            )
                                        },
                                        emptyText = "暂无收藏视频",
                                    )
                                }
                            }

                            HomeTab.Up -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    SecondaryHeader(
                                        title = titleForTab(tab),
                                        onBack = { viewModel.switchTab(HomeTab.Feed) },
                                    )
                                    UpScreen(
                                        midInput = state.upMidInput,
                                        upName = state.upInfo?.name.orEmpty(),
                                        upSign = state.upInfo?.sign.orEmpty(),
                                        follower = state.upStat?.follower ?: 0L,
                                        totalView = state.upStat?.totalView ?: 0L,
                                        items = state.upItems,
                                        loading = state.loading,
                                        hasMore = state.upHasMore,
                                        onMidChange = viewModel::onUpMidInputChange,
                                        onLoad = { viewModel.loadUpHome(refresh = true) },
                                        onLoadMore = { viewModel.loadUpHome(refresh = false) },
                                        openVideo = openVideo,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = upManagerVisible,
                enter = fadeIn(animationSpec = tween(120)),
                exit = fadeOut(animationSpec = tween(120)),
                label = "up-manager-scrim",
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f))
                        .noRippleClickable { upManagerVisible = false },
                )
            }

            AnimatedVisibility(
                visible = upManagerVisible,
                enter = slideInHorizontally(
                    animationSpec = tween(220),
                    initialOffsetX = { full -> -full },
                ),
                exit = slideOutHorizontally(
                    animationSpec = tween(200),
                    targetOffsetX = { full -> -full },
                ),
                label = "up-manager-panel",
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    UpManagerScreen(
                        ups = state.followedUps,
                        pinned = state.pinnedUpMids,
                        blacklisted = state.blacklistedUpMids,
                        onBack = { upManagerVisible = false },
                        onSetStatus = viewModel::setUpGroupStatus,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenBackdrop(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(BlblBgTop, BlblBgBottom))),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(BlblPinkSoft.copy(alpha = 0.52f), Color.Transparent),
                        center = Offset(140f, 120f),
                        radius = 520f,
                    ),
                ),
        )
        content()
    }
}

private fun titleForTab(tab: HomeTab): String = when (tab) {
    HomeTab.Feed -> "首页"
    HomeTab.WatchLater -> "稍后再看"
    HomeTab.Favorite -> "我的收藏"
    HomeTab.Up -> "UP 主页"
}

private suspend fun smoothScrollToTop(
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    if (listState.firstVisibleItemIndex > 24) {
        listState.scrollToItem(24)
    }
    listState.animateScrollToItem(index = 0, scrollOffset = 0)
    if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
        listState.scrollToItem(0)
    }
}

private suspend fun saveQrAndOpenBiliScanner(context: Context, qrUrl: String): String {
    if (qrUrl.isBlank()) return "二维码为空，请先生成二维码"
    val savedUri = runCatching { saveQrToGallery(context, qrUrl) }
        .getOrElse { return "保存二维码失败: ${it.message ?: "unknown"}" }
    val launched = runCatching { openBiliScanActivity(context) }.getOrDefault(false)
    return if (launched) {
        "二维码已保存到相册并打开B站扫码"
    } else {
        "二维码已保存到相册：$savedUri，未找到B站扫码页"
    }
}

private suspend fun saveQrToGallery(context: Context, qrUrl: String): android.net.Uri = withContext(Dispatchers.IO) {
    val imageUrl = "https://quickchart.io/qr?size=720&text=${android.net.Uri.encode(qrUrl)}"
    val bitmap = URL(imageUrl).openStream().use { stream ->
        BitmapFactory.decodeStream(stream) ?: error("二维码图片解码失败")
    }

    val resolver = context.contentResolver
    val fileName = "ohmyblbl_qr_${System.currentTimeMillis()}.png"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OhMyBLBL")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("无法创建相册文件")
    try {
        resolver.openOutputStream(uri)?.use { out ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)) { "写入二维码失败" }
        } ?: error("无法打开输出流")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }
    } catch (t: Throwable) {
        resolver.delete(uri, null, null)
        throw t
    }
    uri
}

private fun openBiliScanActivity(context: Context): Boolean {
    val intent = Intent().apply {
        component = ComponentName("tv.danmaku.bili", "com.bilibili.app.qrcode.QRcodeCaptureActivity")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: Throwable) {
        false
    }
}

