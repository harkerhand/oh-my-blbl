package cn.harkerhand.ohmyblbl

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cn.harkerhand.ohmyblbl.data.FolderItem
import cn.harkerhand.ohmyblbl.data.UpItem
import cn.harkerhand.ohmyblbl.data.VideoItem
import cn.harkerhand.ohmyblbl.ui.theme.BlblBorder
import cn.harkerhand.ohmyblbl.ui.theme.BlblIcon
import cn.harkerhand.ohmyblbl.ui.theme.BlblPink
import cn.harkerhand.ohmyblbl.ui.theme.BlblPinkLine
import cn.harkerhand.ohmyblbl.ui.theme.BlblPinkSoft
import cn.harkerhand.ohmyblbl.ui.theme.BlblPinkStrong
import cn.harkerhand.ohmyblbl.ui.theme.BlblSurface
import cn.harkerhand.ohmyblbl.ui.theme.BlblSurfaceAlt
import cn.harkerhand.ohmyblbl.ui.theme.BlblText
import cn.harkerhand.ohmyblbl.ui.theme.BlblTextSoft
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun FeedHeader(
    onScrollToTop: () -> Unit,
    onFavorite: () -> Unit,
    onWatchLater: () -> Unit,
    onLogout: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Oh My BiliBili ! ! !",
            style = MaterialTheme.typography.headlineMedium,
            color = BlblPinkStrong,
            modifier = Modifier
                .weight(1f)
                .noRippleClickable(onClick = onScrollToTop),
        )
        HeaderIcon(icon = Icons.Outlined.StarOutline, onClick = onFavorite, contentDescription = "收藏")
        HeaderIcon(icon = Icons.Outlined.BookmarkBorder, onClick = onWatchLater, contentDescription = "稍后再看")
        HeaderIcon(icon = Icons.AutoMirrored.Rounded.Logout, onClick = onLogout, contentDescription = "退出")
    }
}

@Composable
fun SecondaryHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderIcon(icon = Icons.AutoMirrored.Rounded.ArrowBack, onClick = onBack, contentDescription = "返回")
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = BlblPinkStrong,
        )
    }
}

@Composable
private fun HeaderIcon(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = BlblIcon)
    }
}

@Composable
fun FeedHomeScreen(
    listState: LazyListState,
    items: List<VideoItem>,
    likedBvids: Set<String>,
    ups: List<UpItem>,
    selectedUpMid: String,
    autoScrollFilterToken: Int,
    loading: Boolean,
    hasMore: Boolean,
    onOpenManager: () -> Unit,
    onSelectUp: (String) -> Unit,
    onClearUp: () -> Unit,
    onLoadMore: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    onOpenUp: (String) -> Unit,
    onLike: (VideoItem) -> Unit,
    onShare: (String) -> Unit,
) {
    LaunchedEffect(listState, hasMore, loading, items.size, selectedUpMid) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val total = layout.totalItemsCount
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            total > 0 && lastVisible >= total - 3
        }
            .distinctUntilChanged()
            .collect { nearBottom ->
                if (nearBottom && hasMore && !loading) {
                    onLoadMore()
                }
            }
    }

    LaunchedEffect(selectedUpMid, items.size, hasMore, loading) {
        if (selectedUpMid.isNotBlank() && items.isEmpty() && hasMore && !loading) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            UpFilterStrip(
                ups = ups,
                selectedUpMid = selectedUpMid,
                autoScrollFilterToken = autoScrollFilterToken,
                onOpenManager = onOpenManager,
                onSelectUp = onSelectUp,
                onClearUp = onClearUp,
            )
        }

        if (items.isEmpty() && !loading) {
            item {
                EmptyStateCard(
                    when {
                        selectedUpMid.isBlank() -> "还没有动态内容"
                        hasMore -> "当前页暂未命中该 UP，正在自动继续加载"
                        else -> "这个 UP 暂时没有可显示的动态"
                    }
                )
            }
        }

        items(items, key = { it.shareUrl.ifBlank { it.bvid } }) { item ->
            DynamicFeedCard(
                item = item,
                liked = likedBvids.contains(item.bvid),
                onPlay = { onPlay(item) },
                onOpenUp = { onOpenUp(item.authorMid) },
                onLike = { onLike(item) },
                onShare = { onShare(item.shareUrl) },
            )
        }

        if (loading && items.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = BlblPink)
                }
            }
        }

    }
}

@Composable
private fun UpFilterStrip(
    ups: List<UpItem>,
    selectedUpMid: String,
    autoScrollFilterToken: Int,
    onOpenManager: () -> Unit,
    onSelectUp: (String) -> Unit,
    onClearUp: () -> Unit,
) {
    val rowState = rememberLazyListState()
    LaunchedEffect(autoScrollFilterToken, ups) {
        if (autoScrollFilterToken <= 0) return@LaunchedEffect
        val targetIndex = if (selectedUpMid.isBlank()) {
            1
        } else {
            (ups.indexOfFirst { it.mid == selectedUpMid }.takeIf { it >= 0 } ?: 0) + 2
        }
        rowState.animateScrollToItem(index = targetIndex.coerceAtLeast(0))
    }

    LazyRow(
        state = rowState,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
    ) {
        item {
            CircleFilterButton(
                selected = false,
                onClick = onOpenManager,
                content = { Icon(Icons.Rounded.Settings, contentDescription = "编辑UP筛选", tint = BlblPink) },
            )
        }
        item {
            CircleFilterButton(
                selected = selectedUpMid.isBlank(),
                onClick = onClearUp,
                content = {
                    Text(
                        "All",
                        color = if (selectedUpMid.isBlank()) BlblPink else BlblTextSoft,
                        fontWeight = FontWeight.Bold,
                    )
                },
            )
        }
        items(ups, key = { it.mid }) { up ->
            val selected = selectedUpMid == up.mid
            CircleFilterButton(
                selected = selected,
                onClick = { onSelectUp(up.mid) },
                content = {
                    if (up.face.isNotBlank()) {
                        AsyncImage(
                            model = up.face,
                            contentDescription = up.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(up.name.take(1), fontWeight = FontWeight.Bold, color = BlblPink)
                    }
                },
            )
        }
    }
}

@Composable
private fun CircleFilterButton(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val borderColor = if (selected) BlblPink else BlblPinkLine
    Surface(
        modifier = Modifier
            .size(50.dp)
            .noRippleClickable(onClick = onClick),
        color = BlblSurface.copy(alpha = 0.92f),
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Composable
private fun DynamicFeedCard(
    item: VideoItem,
    liked: Boolean,
    onPlay: () -> Unit,
    onOpenUp: () -> Unit,
    onLike: () -> Unit,
    onShare: () -> Unit,
) {
    Surface(
        color = BlblSurface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BlblBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = BlblSurfaceAlt,
                    modifier = Modifier
                        .size(42.dp)
                        .noRippleClickable(onClick = onOpenUp),
                ) {
                    if (item.authorFace.isNotBlank()) {
                        AsyncImage(
                            model = item.authorFace,
                            contentDescription = item.author,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = item.author.take(1).ifBlank { "UP" },
                                color = BlblPink,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.author.ifBlank { "未知UP" }, color = BlblText, fontWeight = FontWeight.Bold)
                    Text(item.authorPubTime.ifBlank { "刚刚" }, color = BlblTextSoft, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeaderStatAction(
                        icon = Icons.Outlined.ThumbUp,
                        value = formatCompactCount(item.likeCount),
                        active = liked,
                        onClick = onLike,
                    )
                    HeaderStatAction(
                        icon = Icons.Outlined.Share,
                        value = formatCompactCount(item.shareCount),
                        onClick = onShare,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(208.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .noRippleClickable(onClick = onPlay),
            ) {
                AsyncImage(
                    model = item.cover,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.58f))))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OverlayMetric(Icons.Outlined.PlayCircleOutline, formatCompactCount(item.play))
                        OverlayMetric(Icons.Rounded.Schedule, item.durationText.ifBlank { "--:--" })
                    }
                }
            }

            Text(
                text = item.title.ifBlank { "未命名视频" },
                color = BlblText,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HeaderStatAction(
    icon: ImageVector,
    value: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.noRippleClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) BlblPink else BlblTextSoft,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(value, color = BlblTextSoft, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OverlayMetric(
    icon: ImageVector,
    value: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(2.dp))
        Text(value, color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FooterMetric(
    icon: ImageVector,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.noRippleClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = BlblTextSoft, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(value, color = BlblTextSoft, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun FavoriteFolderStrip(
    folders: List<FolderItem>,
    selectedFolderId: Long?,
    onSelectFolder: (Long) -> Unit,
    onLoadFolders: () -> Unit,
    loading: Boolean,
) {
    if (folders.isEmpty()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            PillButton(
                text = "加载收藏夹",
                onClick = onLoadFolders,
                enabled = !loading,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .height(46.dp),
            )
        }
        return
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        items(folders, key = { it.id }) { folder ->
            Surface(
                color = if (selectedFolderId == folder.id) BlblPinkSoft else BlblSurface,
                shape = RoundedCornerShape(999.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedFolderId == folder.id) BlblPink else BlblBorder),
                modifier = Modifier.noRippleClickable { onSelectFolder(folder.id) },
            ) {
                Text(
                    text = folder.title,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = if (selectedFolderId == folder.id) BlblPink else BlblText,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
fun CollectionScreen(
    items: List<VideoItem>,
    loading: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    openVideo: (String) -> Unit,
    header: (@Composable (() -> Unit))?,
    emptyText: String,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (header != null) item { header() }

        if (items.isEmpty() && !loading) {
            item { EmptyStateCard(emptyText) }
        }

        items(items, key = { it.bvid }) { item ->
            LibraryVideoCard(
                item = item,
                openVideo = { openVideo(item.bvid) },
            )
        }

        if (loading && items.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BlblPink)
                }
            }
        }

        if (items.isNotEmpty() && hasMore) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.noRippleClickable(enabled = !loading, onClick = onLoadMore),
                        color = BlblSurface,
                        shape = RoundedCornerShape(999.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BlblBorder),
                    ) {
                        Text(
                            text = if (loading) "加载中..." else "加载更多",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            color = BlblText,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryVideoCard(
    item: VideoItem,
    openVideo: () -> Unit,
) {
    Surface(
        color = BlblSurface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BlblBorder),
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = openVideo),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                AsyncImage(
                    model = item.cover,
                    contentDescription = item.title,
                    modifier = Modifier
                        .width(132.dp)
                        .height(82.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .noRippleClickable(onClick = openVideo),
                    contentScale = ContentScale.Crop,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = item.title.ifBlank { "未命名视频" },
                        color = BlblText,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(item.author.ifBlank { "未知UP" }, color = BlblTextSoft, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "播放 ${formatCompactCount(item.play)}  弹幕 ${formatCompactCount(item.danmaku)}",
                        color = BlblTextSoft,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
fun UpScreen(
    midInput: String,
    upName: String,
    upSign: String,
    follower: Long,
    totalView: Long,
    items: List<VideoItem>,
    loading: Boolean,
    hasMore: Boolean,
    onMidChange: (String) -> Unit,
    onLoad: () -> Unit,
    onLoadMore: () -> Unit,
    openVideo: (String) -> Unit,
) {
    CollectionScreen(
        items = items,
        loading = loading,
        hasMore = hasMore,
        onLoadMore = onLoadMore,
        openVideo = openVideo,
        header = {
            Surface(
                color = BlblSurface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BlblBorder),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        PinkInput(
                            value = midInput,
                            onValueChange = onMidChange,
                            placeholder = "输入 MID",
                            modifier = Modifier.weight(1f),
                        )
                        PillButton(
                            text = "加载",
                            onClick = onLoad,
                            enabled = !loading,
                            modifier = Modifier.height(54.dp),
                        )
                    }
                    if (upName.isNotBlank()) {
                        Text(upName, color = BlblText, style = MaterialTheme.typography.titleLarge)
                        if (upSign.isNotBlank()) Text(upSign, color = BlblTextSoft, style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "粉丝 ${formatCompactCount(follower.toString())}  总播放 ${formatCompactCount(totalView.toString())}",
                            color = BlblTextSoft,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        emptyText = "暂无投稿视频",
    )
}

@Composable
private fun EmptyStateCard(text: String) {
    Surface(
        color = BlblSurface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BlblBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = BlblTextSoft, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun UpManagerScreen(
    ups: List<UpItem>,
    pinned: List<String>,
    blacklisted: List<String>,
    onBack: () -> Unit,
    onSetStatus: (String, UpGroupStatus) -> Unit,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val statusMap = remember(pinned, blacklisted) {
        buildMap<String, UpGroupStatus> {
            blacklisted.forEach { put(it, UpGroupStatus.Blacklisted) }
            pinned.forEach { put(it, UpGroupStatus.Pinned) }
        }
    }

    val pinnedUps = ups.filter { statusMap[it.mid] == UpGroupStatus.Pinned }
    val blacklistedUps = ups.filter { statusMap[it.mid] == UpGroupStatus.Blacklisted }
    val normalUps = ups.filter { statusMap[it.mid] == null }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BlblSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusTop)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SecondaryHeader(title = "UP 分组管理", onBack = onBack)
            UpGroupSection(
                title = "置顶",
                ups = pinnedUps,
                statusMap = statusMap,
                onSetStatus = onSetStatus,
            )
            UpGroupSection(
                title = "普通",
                ups = normalUps,
                statusMap = statusMap,
                onSetStatus = onSetStatus,
            )
            UpGroupSection(
                title = "黑名单",
                ups = blacklistedUps,
                statusMap = statusMap,
                onSetStatus = onSetStatus,
            )
        }
    }
}

@Composable
private fun UpGroupSection(
    title: String,
    ups: List<UpItem>,
    statusMap: Map<String, UpGroupStatus>,
    onSetStatus: (String, UpGroupStatus) -> Unit,
) {
    var keyword by remember(title, ups) { mutableStateOf("") }
    val filtered = remember(keyword, ups) {
        val q = keyword.trim()
        if (q.isBlank()) ups else ups.filter {
            it.name.contains(q, ignoreCase = true) || it.mid.contains(q, ignoreCase = true)
        }
    }
    Surface(
        color = BlblSurface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BlblBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("$title (${ups.size})", color = BlblText, fontWeight = FontWeight.Bold)
            PinkInput(
                value = keyword,
                onValueChange = { keyword = it },
                placeholder = "搜索 $title 中的 UP",
            )
            if (filtered.isEmpty()) {
                Text("没有匹配项", color = BlblTextSoft)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.mid }) { up ->
                        UpStatusRow(
                            up = up,
                            status = statusMap[up.mid] ?: UpGroupStatus.Normal,
                            onSetStatus = { onSetStatus(up.mid, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpStatusRow(
    up: UpItem,
    status: UpGroupStatus,
    onSetStatus: (UpGroupStatus) -> Unit,
) {
    Surface(
        color = BlblSurfaceAlt,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(shape = CircleShape, color = BlblSurface, modifier = Modifier.size(34.dp)) {
                if (up.face.isNotBlank()) {
                    AsyncImage(
                        model = up.face,
                        contentDescription = up.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(up.name, color = BlblText, fontWeight = FontWeight.SemiBold)
                Text("UID ${up.mid}", color = BlblTextSoft, style = MaterialTheme.typography.bodySmall)
            }
            UpStatusToggle(status = status, onChange = onSetStatus)
        }
    }
}

@Composable
private fun UpStatusToggle(
    status: UpGroupStatus,
    onChange: (UpGroupStatus) -> Unit,
) {
    Surface(
        color = BlblSurface,
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BlblPinkLine),
        modifier = Modifier
            .width(176.dp)
            .height(34.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val segmentWidth = maxWidth / 3
            val target = when (status) {
                UpGroupStatus.Pinned -> 0.dp
                UpGroupStatus.Normal -> segmentWidth
                UpGroupStatus.Blacklisted -> segmentWidth * 2
            }
            val offsetX by animateDpAsState(
                targetValue = target,
                animationSpec = tween(durationMillis = 220),
                label = "up-status-toggle",
            )
            Box(
                modifier = Modifier
                    .offset(x = offsetX)
                    .width(segmentWidth)
                    .height(34.dp)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(BlblPinkSoft),
            )
            Row(Modifier.fillMaxSize()) {
                ToggleSegment("置顶", status == UpGroupStatus.Pinned) { onChange(UpGroupStatus.Pinned) }
                ToggleSegment("普通", status == UpGroupStatus.Normal) { onChange(UpGroupStatus.Normal) }
                ToggleSegment("黑名单", status == UpGroupStatus.Blacklisted) { onChange(UpGroupStatus.Blacklisted) }
            }
        }
    }
}

@Composable
private fun RowScope.ToggleSegment(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) BlblPink else BlblTextSoft,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun LogoutConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Icon(Icons.Rounded.Check, contentDescription = "确认退出", tint = BlblPink)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "取消", tint = BlblTextSoft)
            }
        },
        title = { Text("退出登录", color = BlblText) },
        containerColor = BlblSurface,
        shape = RoundedCornerShape(28.dp),
    )
}

@SuppressLint("DefaultLocale")
private fun formatCompactCount(value: String): String {
    val num = value.toDoubleOrNull() ?: return value.ifBlank { "0" }
    return when {
        num >= 100000000 -> "${String.format("%.1f", num / 100000000).trimEnd('0').trimEnd('.')}亿"
        num >= 10000 -> "${String.format("%.1f", num / 10000).trimEnd('0').trimEnd('.')}万"
        else -> num.toInt().toString()
    }
}

