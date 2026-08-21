package dev.openbili.webdemo.video

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent as AndroidKeyEvent
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.BitmapImage
import coil3.compose.AsyncImage
import coil3.request.allowHardware
import coil3.request.ImageRequest
import coil3.size.Precision
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.BiliArticleApi
import dev.openbili.webdemo.api.BiliEmote
import dev.openbili.webdemo.api.BiliVideoApi
import dev.openbili.webdemo.api.CommentImage
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.article.ArticleCard
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.LoadedFeedImageRegistry
import dev.openbili.webdemo.feed.LocalFeedImageLoadPolicy
import dev.openbili.webdemo.ui.controlFocusOutline
import dev.openbili.webdemo.ui.isControlConfirmKey
import dev.openbili.webdemo.ui.NavigationCardBottomClearance
import dev.openbili.webdemo.ui.OfficialVerificationIcon
import dev.openbili.webdemo.ui.OfficialVerificationIconSize
import dev.openbili.webdemo.ui.LocalColorfulCardsEnabled
import dev.openbili.webdemo.ui.VideoPageSurfaceTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap

/**
 * 布局坐标由点击/转场回调消费，而非视觉树自身。把它们放在 Compose Snapshot 状态
 * 之外，避免每次滚动摆放时所有可见评论都重组。
 */
private class CommentBoundsHolder {
  var coordinates: LayoutCoordinates? = null

  fun rect(): Rect = coordinates?.takeIf(LayoutCoordinates::isAttached)?.boundsInRoot() ?: Rect.Zero
}

private class CommentRowBounds {
  val card = CommentBoundsHolder()
  val avatar = CommentBoundsHolder()
}

private val commentEmoteTokenPattern = Regex("\\[[^\\[\\]\\r\\n]{1,32}]")

private fun copyCommentToClipboard(
  context: Context,
  fallbackView: android.view.View,
  comment: CommentItem,
): Boolean {
  val payload = commentCopyPayload(comment.content).trim()
  if (payload.isBlank()) return false
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    ?: return false
  clipboard.setPrimaryClip(ClipData.newPlainText("评论", payload))
  val vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
  if (vibrator?.hasVibrator() == true) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      vibrator.vibrate(VibrationEffect.createOneShot(42L, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
      @Suppress("DEPRECATION") vibrator.vibrate(42L)
    }
  } else {
    fallbackView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
  }
  return true
}

private data class ResolvedCommentVideoLink(
  val referenceId: String,
  val item: FeedItem?,
)

/** 评论媒体的进程内有界缓存，保证父视频恢复时卡片可以同步重新进入组合树。 */
private object CommentMediaResolutionCache {
  private const val MAX_ENTRIES = 160
  private val videoCache = LinkedHashMap<String, FeedItem?>(MAX_ENTRIES, .75f, true)
  private val articleCache = LinkedHashMap<Long, ArticleItem?>(MAX_ENTRIES, .75f, true)

  @Synchronized fun hasVideo(key: String): Boolean = videoCache.containsKey(key)
  @Synchronized fun video(key: String): FeedItem? = videoCache[key]
  @Synchronized fun putVideo(key: String, value: FeedItem?) {
    videoCache[key] = value
    while (videoCache.size > MAX_ENTRIES) videoCache.remove(videoCache.entries.first().key)
  }

  @Synchronized fun hasArticle(key: Long): Boolean = articleCache.containsKey(key)
  @Synchronized fun article(key: Long): ArticleItem? = articleCache[key]
  @Synchronized fun putArticle(key: Long, value: ArticleItem?) {
    articleCache[key] = value
    while (articleCache.size > MAX_ENTRIES) articleCache.remove(articleCache.entries.first().key)
  }
}

@Composable
internal fun CommentImageGallery(
  images: List<CommentImage>,
  onPreview: (CommentImage, Rect) -> Unit,
  trackBounds: Boolean = true,
  modifier: Modifier = Modifier,
) {
  if (images.isEmpty()) return
  val imageHeightLimit = minOf(360.dp, LocalConfiguration.current.screenHeightDp.dp * (2f / 3f))
  BoxWithConstraints(modifier.widthIn(max = 640.dp)) {
    val availableWidth = maxWidth
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      if (images.size == 1) {
        val image = images.first()
        val ratio =
          if (image.width > 0 && image.height > 0) image.width.toFloat() / image.height else 1.5f
        CommentImageThumbnail(
          image = image,
          contentDescription = "评论图片",
          onPreview = onPreview,
          trackBounds = trackBounds,
          contentScale = ContentScale.Fit,
          modifier =
            Modifier.fillMaxWidth()
              .aspectRatio(ratio.coerceIn(.55f, 2.4f))
              .heightIn(max = imageHeightLimit),
        )
      } else {
        val columns = if (images.size == 2 || availableWidth < 420.dp) 2 else 3
        images.chunked(columns).forEachIndexed { rowIndex, rowImages ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            rowImages.forEachIndexed { columnIndex, image ->
              CommentImageThumbnail(
                image = image,
                contentDescription = "评论图片 ${rowIndex * columns + columnIndex + 1}",
                onPreview = onPreview,
                trackBounds = trackBounds,
                modifier = Modifier.weight(1f).aspectRatio(1f),
              )
            }
            repeat(columns - rowImages.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
          }
        }
      }
    }
  }
}

@Composable
internal fun CommentImageThumbnail(
  image: CommentImage,
  contentDescription: String,
  onPreview: (CommentImage, Rect) -> Unit,
  trackBounds: Boolean = true,
  contentScale: ContentScale = ContentScale.Crop,
  modifier: Modifier = Modifier,
) {
  val bounds = remember(image.url) { CommentBoundsHolder() }
  val context = LocalContext.current
  BoxWithConstraints(
    modifier =
      modifier
        .then(
          if (trackBounds) Modifier.onGloballyPositioned { bounds.coordinates = it } else Modifier
        )
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
  ) {
    val targetWidthPx = constraints.maxWidth.coerceAtLeast(1)
    val targetHeightPx =
      if (constraints.hasBoundedHeight) constraints.maxHeight.coerceAtLeast(1) else targetWidthPx
    val thumbnail =
      remember(image, targetWidthPx, targetHeightPx, contentScale) {
        commentImageThumbnailSpec(
          rawUrl = image.url,
          imageWidth = image.width,
          imageHeight = image.height,
          targetWidthPx = targetWidthPx,
          targetHeightPx = targetHeightPx,
          crop = contentScale == ContentScale.Crop,
        )
      }
    val request =
      remember(context, thumbnail) {
        ImageRequest.Builder(context)
          .data(thumbnail.url)
          .size(thumbnail.widthPx, thumbnail.heightPx)
          .precision(Precision.INEXACT)
          .build()
      }
    AsyncImage(
      model = request,
      contentDescription = contentDescription,
      modifier =
        Modifier.fillMaxSize().clickable {
          onPreview(image.copy(url = thumbnail.url), bounds.rect())
        },
      contentScale = contentScale,
    )
  }
}

data class CommentProfileAnchor(
  val initialCardBounds: Rect,
  val initialAvatarBounds: Rect?,
  val currentCardBounds: () -> Rect,
  val currentAvatarBounds: () -> Rect,
)

internal fun commentUpActionLabel(upLiked: Boolean, upReplied: Boolean): String? =
  when {
    upLiked && upReplied -> "UP主觉得很赞并回复了此条评论"
    upReplied -> "UP主回复了此条评论"
    upLiked -> "UP主觉得很赞"
    else -> null
  }

@Composable
internal fun CommentRow(
  comment: CommentItem,
  showEmotes: Boolean = true,
  emoteCatalog: Map<String, BiliEmote> = emptyMap(),
  showLocation: Boolean,
  onLike: (CommentItem) -> Unit,
  uploaderMid: Long,
  onProfileClick: (Long, CommentItem, CommentProfileAnchor) -> Unit,
  onImagePreview: (CommentImage, Rect) -> Unit,
  onReplies: (CommentItem, Rect) -> Unit,
  onReply: (CommentItem) -> Unit,
  replyEnabled: Boolean = true,
  bottomClearancePx: Float = 0f,
  viewportHeightPx: Float = 0f,
  flat: Boolean = false,
  avatarVisible: Boolean = true,
  trackBounds: Boolean = true,
  hiddenLinkedVideoCoverItemId: String? = null,
  onLinkedVideoClick: (FeedItem, Rect) -> Unit = { _, _ -> },
  onLinkedVideoBoundsChanged: (FeedItem, Rect) -> Unit = { _, _ -> },
  onLinkedVideoLongClick: (FeedItem) -> Unit = {},
  onTimestampClick: (Long, Int) -> Unit = { _, _ -> },
  hiddenLinkedArticleItemId: String? = null,
  onLinkedArticleClick: (ArticleItem, Rect) -> Unit = { _, _ -> },
  onLinkedArticleBoundsChanged: (ArticleItem, Rect) -> Unit = { _, _ -> },
  headerLabel: String = "",
  quotedContent: String = "",
  linkedMediaVisible: Boolean = true,
  linkedArticleCompactHeight: Dp? = null,
  /** 保留应用自己的视频/专栏卡片，并把链接正文替换为卡片。 */
  renderLinkedMediaCards: Boolean = true,
  deletionSelected: Boolean = false,
  onDeleteRequest: ((Rect) -> Unit)? = null,
  onDeleteConfirm: (() -> Unit)? = null,
  onDeleteCancel: (() -> Unit)? = null,
  onDeletionBoundsChanged: ((Rect) -> Unit)? = null,
  pinActionAvailable: Boolean = false,
  pinActionLabel: String = "置顶",
  onPinRequest: (() -> Unit)? = null,
  largeText: Boolean = false,
  controlEnabled: Boolean = false,
  controlFocusRequester: FocusRequester? = null,
  controlReturnFocusRequester: FocusRequester? = null,
  controlPlayerFocusRequester: FocusRequester? = null,
  controlUpFocusRequester: FocusRequester? = null,
  controlAtListEnd: Boolean = false,
  onControlOpenReplies: ((CommentItem, Rect) -> Unit)? = null,
) {
  val context = LocalContext.current
  val fallbackView = LocalView.current
  val measuredBounds = remember(comment.rpid) { CommentRowBounds() }
  var openingProfile by remember(comment.rpid) { mutableStateOf(false) }
  var openingLinkedVideo by remember(comment.rpid) { mutableStateOf(false) }
  var openingLinkedArticle by remember(comment.rpid) { mutableStateOf(false) }
  var mutedLinkedArticleId by remember(comment.rpid) { mutableStateOf<String?>(null) }
  val linkedVideoBounds = remember(comment.rpid) { mutableMapOf<String, Rect>() }
  val linkedArticleBounds = remember(comment.rpid) { mutableMapOf<String, Rect>() }
  val bringIntoViewRequester = remember(comment.rpid) { BringIntoViewRequester() }
  // 读取活动的 Material 配色而不是系统标志，让应用的深色模式设置与系统深色
  // 模式使用同一套调色板。
  val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  val colorfulCardsEnabled = LocalColorfulCardsEnabled.current
  val paletteScope = rememberCoroutineScope()
  val fallbackBottomClearancePx =
    with(LocalDensity.current) { NavigationCardBottomClearance.toPx() }
  val bringIntoViewMarginPx = with(LocalDensity.current) { 16.dp.toPx() }
  suspend fun revealCommentForNavigation() {
    val clearance = maxOf(bottomClearancePx, fallbackBottomClearancePx)
    val bounds = measuredBounds.card.rect()
    if (bounds.width > 0f && bounds.height > 0f) {
      val requestedCardHeight =
        if (viewportHeightPx > 0f) {
          minOf(
            bounds.height,
            (viewportHeightPx - clearance - bringIntoViewMarginPx).coerceAtLeast(
              minOf(bounds.height, fallbackBottomClearancePx)
            ),
          )
        } else {
          bounds.height
        }
      runCatching {
        bringIntoViewRequester.bringIntoView(
          Rect(
            left = 0f,
            top = 0f,
            right = bounds.width,
            bottom = requestedCardHeight + clearance,
          )
        )
      }
      // 第一帧应用列表移动；第二帧让封面在共享元素会话捕获源矩形之前发布其
      // 最终根边界。
      withFrameNanos {}
      withFrameNanos {}
    }
  }
  fun openProfileAfterReveal() {
    if (openingProfile || openingLinkedVideo || openingLinkedArticle) return
    openingProfile = true
    paletteScope.launch {
      revealCommentForNavigation()
      onProfileClick(
        comment.mid,
        comment,
        CommentProfileAnchor(
          initialCardBounds = measuredBounds.card.rect(),
          initialAvatarBounds = measuredBounds.avatar.rect(),
          currentCardBounds = { measuredBounds.card.rect() },
          currentAvatarBounds = { measuredBounds.avatar.rect() },
        ),
      )
      openingProfile = false
    }
  }
  fun openLinkedVideo(video: FeedItem, clickedBounds: Rect) {
    if (openingLinkedVideo || openingLinkedArticle || openingProfile) return
    openingLinkedVideo = true
    paletteScope.launch {
      // 紧凑卡片已经记录了自己的根边界；行内节点则使用 TextLayoutResult 的边界。
      val bounds =
        linkedVideoBounds[video.id]?.takeIf { it.width > 0f && it.height > 0f }
          ?: clickedBounds.takeIf { it.width > 0f && it.height > 0f }
          ?: measuredBounds.card.rect()
      if (bounds.width > 0f && bounds.height > 0f) onLinkedVideoClick(video, bounds)
      openingLinkedVideo = false
    }
  }
  fun openLinkedArticleAfterReveal(article: ArticleItem, clickedBounds: Rect) {
    if (openingLinkedArticle || openingLinkedVideo || openingProfile) return
    openingLinkedArticle = true
    mutedLinkedArticleId = article.stableId
    paletteScope.launch {
      // 先让标题装饰淡出，再捕获专栏封面的共享元素起点。
      kotlinx.coroutines.delay(160)
      revealCommentForNavigation()
      val bounds =
        linkedArticleBounds[article.stableId]?.takeIf { it.width > 0f && it.height > 0f }
          ?: clickedBounds.takeIf { it.width > 0f && it.height > 0f }
          ?: measuredBounds.card.rect()
      if (bounds.width > 0f && bounds.height > 0f) onLinkedArticleClick(article, bounds)
      // 转场目标接管来源后再恢复卡片装饰，避免返回时出现残留遮罩。
      kotlinx.coroutines.delay(900)
      mutedLinkedArticleId = null
      openingLinkedArticle = false
    }
  }
  fun openMentionProfile(mid: Long) {
    if (openingProfile) return
    openingProfile = true
    paletteScope.launch {
      onProfileClick(
        mid,
        comment,
        CommentProfileAnchor(
          initialCardBounds = measuredBounds.card.rect(),
          initialAvatarBounds = null,
          currentCardBounds = { measuredBounds.card.rect() },
          currentAvatarBounds = { Rect.Zero },
        ),
      )
      openingProfile = false
    }
  }
  var paletteLoading by remember(comment.face) { mutableStateOf(false) }
  var avatarColors by
    remember(comment.face, darkTheme, colorfulCardsEnabled) {
      mutableStateOf(
        if (colorfulCardsEnabled) CommentAvatarPaletteCache.get(comment.face).orEmpty()
        else emptyList()
      )
    }
  val opaqueSurfaceColor = MaterialTheme.colorScheme.surface
  val surfaceColor =
    opaqueSurfaceColor.copy(
      alpha =
        if (darkTheme) VideoPageSurfaceTokens.DarkCommentCardAlpha
        else VideoPageSurfaceTokens.LightCommentCardAlpha
    )
  val gradientAlpha =
    if (darkTheme) VideoPageSurfaceTokens.DarkCommentGradientAlpha
    else VideoPageSurfaceTokens.LightCommentGradientAlpha
  val cardGradientColors =
    remember(avatarColors, opaqueSurfaceColor, darkTheme, gradientAlpha, colorfulCardsEnabled) {
      if (!colorfulCardsEnabled || avatarColors.isEmpty()) emptyList()
      else
        avatarColors.take(2).map {
          readableCommentCardColor(it, opaqueSurfaceColor, darkTheme).copy(alpha = gradientAlpha)
        }
    }
  val cardGradient =
    remember(cardGradientColors) {
      cardGradientColors.takeIf { it.isNotEmpty() }?.let(Brush::horizontalGradient)
    }
  val deleteTint =
    if (deletionSelected) {
      val animatedTint by
        animateColorAsState(
          targetValue = MaterialTheme.colorScheme.errorContainer.copy(alpha = .92f),
          label = "commentDeleteTint",
        )
      animatedTint
    } else {
      Color.Transparent
    }
  val rowEmotes =
    remember(showEmotes, comment.emotes, emoteCatalog) {
      if (!showEmotes) emptyMap()
      else
        buildMap {
          // 接口的逐评论表情映射是权威来源：token 查找只为省略 content.emote 的
          // 旧响应保留兼容路径。
          comment.emotes.forEach { (text, url) -> put(text, BiliEmote(text, url)) }
          commentEmoteTokenPattern.findAll(comment.content).forEach { match ->
            val token = match.value
            if (!containsKey(token)) emoteCatalog[token]?.let { put(token, it) }
          }
        }
    }
  val parsedVideoLinks = remember(comment.content) { parseCommentVideoLinks(comment.content) }
  val linkedBvids =
    remember(parsedVideoLinks) { parsedVideoLinks.links.map { it.bvid }.distinct() }
  val linkedArticleRefs =
    remember(parsedVideoLinks) { parsedVideoLinks.articleLinks.distinctBy { it.sourceUrl } }
  var linkedVideos by
    remember(comment.rpid, comment.content) {
      mutableStateOf(
        linkedBvids
          .filter(CommentMediaResolutionCache::hasVideo)
          .map { referenceId ->
            ResolvedCommentVideoLink(referenceId, CommentMediaResolutionCache.video(referenceId))
          }
          .takeIf { it.size == linkedBvids.size }
          ?: emptyList()
      )
    }
  var linkedVideosResolved by
    remember(comment.rpid, comment.content) {
      mutableStateOf(linkedBvids.isEmpty() || linkedBvids.all(CommentMediaResolutionCache::hasVideo))
    }
  var linkedArticles by
    remember(comment.rpid, comment.content) {
      mutableStateOf(
        linkedArticleRefs
          .filter { CommentMediaResolutionCache.hasArticle(it.articleId) }
          .mapNotNull { CommentMediaResolutionCache.article(it.articleId) }
          .takeIf { it.size == linkedArticleRefs.size }
          ?: emptyList()
      )
    }
  var linkedArticlesResolved by
    remember(comment.rpid, comment.content) {
      mutableStateOf(
        linkedArticleRefs.isEmpty() ||
          linkedArticleRefs.all { CommentMediaResolutionCache.hasArticle(it.articleId) }
      )
    }
  LaunchedEffect(linkedBvids) {
    if (linkedBvids.isEmpty()) {
      linkedVideos = emptyList()
      linkedVideosResolved = true
      return@LaunchedEffect
    }
    linkedVideosResolved = false
    linkedVideos =
      withContext(Dispatchers.IO) {
        linkedBvids.map { referenceId ->
          val resolved =
            if (CommentMediaResolutionCache.hasVideo(referenceId)) {
              CommentMediaResolutionCache.video(referenceId)
            } else {
              runCatching {
                  if (referenceId.startsWith("BV", ignoreCase = true)) {
                    BiliVideoApi.getVideoInfo(referenceId)?.toCommentVideoFeedItem()
                  } else {
                    BiliVideoApi
                      .getVideoInfoByAid(referenceId.removePrefix("av").toLongOrNull() ?: 0L)
                      ?.toCommentVideoFeedItem()
                  }
                }
                .getOrNull()
                .also { CommentMediaResolutionCache.putVideo(referenceId, it) }
            }
          ResolvedCommentVideoLink(referenceId, resolved)
        }
      }
    linkedVideosResolved = true
  }
  LaunchedEffect(linkedArticleRefs) {
    if (linkedArticleRefs.isEmpty()) {
      linkedArticles = emptyList()
      linkedArticlesResolved = true
      return@LaunchedEffect
    }
    linkedArticlesResolved = false
    linkedArticles =
      withContext(Dispatchers.IO) {
        linkedArticleRefs.mapNotNull { link ->
          if (CommentMediaResolutionCache.hasArticle(link.articleId)) {
            return@mapNotNull CommentMediaResolutionCache.article(link.articleId)
          }
          val fallback =
            ArticleItem(
              id = link.articleId,
              title = "专栏",
              sourceUrl = link.sourceUrl,
            )
          runCatching { BiliArticleApi.getArticleDetail(fallback).article }.getOrNull().also {
            CommentMediaResolutionCache.putArticle(link.articleId, it)
          }
        }
      }
    linkedArticlesResolved = true
  }
  val mappedBvids = remember(linkedVideos) { linkedVideos.mapTo(linkedSetOf()) { it.referenceId } }
  val mappedArticleIds = remember(linkedArticles) { linkedArticles.mapTo(linkedSetOf()) { it.id } }
  // 评论正文保留普通文字，已解析的视频/专栏链接统一替换为应用自己的卡片。
  val displayedCommentText =
    if (renderLinkedMediaCards) {
      parsedVideoLinks.textWithMappedLinksRemoved(mappedBvids, mappedArticleIds)
    } else {
      comment.content
    }
  val textContentKey = if (renderLinkedMediaCards) comment.content else displayedCommentText
  val mediaLinks =
    remember(parsedVideoLinks, linkedVideos, linkedArticles, comment.jumpLinks, renderLinkedMediaCards) {
      if (renderLinkedMediaCards) return@remember emptyList()
      val jumpLinks = comment.jumpLinks
      val videosByReference = linkedVideos.associateBy { it.referenceId }
      val articlesByReference = linkedArticles.associateBy { it.id.toString() }
      buildList {
        parsedVideoLinks.links.forEach { link ->
          val jump =
            jumpLinks.firstOrNull {
              it.key == link.rawUrl || it.key.equals(link.bvid, ignoreCase = true)
            }
          val video = videosByReference[link.bvid]?.item
          add(
            BiliRichMediaLink(
              id = "video:${link.bvid}:${link.startIndex}",
              kind = CommentMediaKind.VIDEO,
              sourceKey = link.bvid,
              startIndex = link.startIndex,
              endIndex = link.endIndex,
              title = jump?.title.orEmpty().ifBlank { video?.title.orEmpty().ifBlank { link.bvid } },
              iconUrl = jump?.prefixIconUrl.orEmpty(),
            )
          )
        }
        parsedVideoLinks.articleLinks.forEach { link ->
          val jump = jumpLinks.firstOrNull { it.key == link.rawUrl }
          val article = articlesByReference[link.articleId.toString()]
          add(
            BiliRichMediaLink(
              id = "article:${link.articleId}:${link.startIndex}",
              kind = CommentMediaKind.ARTICLE,
              sourceKey = link.articleId.toString(),
              startIndex = link.startIndex,
              endIndex = link.endIndex,
              title =
                jump?.title.orEmpty().ifBlank {
                  article?.title.orEmpty().ifBlank { "专栏 ${link.articleId}" }
                },
              iconUrl = jump?.prefixIconUrl.orEmpty(),
            )
          )
        }
      }.sortedWith(compareBy<BiliRichMediaLink> { it.startIndex }.thenBy { it.endIndex })
    }
  var textExpanded by
    remember(comment.rpid, textContentKey, quotedContent) { mutableStateOf(false) }
  var textOverflowed by
    remember(comment.rpid, textContentKey, quotedContent) { mutableStateOf(false) }
  fun reportTextOverflow(overflowed: Boolean) {
    if (!textExpanded && overflowed) textOverflowed = true
  }
  // 分段渲染后，每个文字片段都有自己的 Text 布局。除了读取真实布局的溢出结果，
  // 这里再按正文长度做一次保守估算，避免长评论恰好被多个片段分摊后没有“展开”。
  val estimatedTextLines =
    remember(textContentKey, quotedContent, largeText) {
      val lineCapacity = if (largeText) 28 else 38
      fun estimate(value: String): Int =
        value.split('\n').sumOf { line -> maxOf(1, (line.length + lineCapacity - 1) / lineCapacity) }
      estimate(textContentKey) + if (quotedContent.isBlank()) 0 else estimate(quotedContent)
    }
  LaunchedEffect(comment.rpid, textContentKey, quotedContent, largeText) {
    if (!textExpanded && estimatedTextLines > 6) textOverflowed = true
  }
  val videosByReference = remember(linkedVideos) { linkedVideos.associateBy { it.referenceId } }
  val articlesByReference = remember(linkedArticles) { linkedArticles.associateBy { it.id.toString() } }

  @Composable
  fun RenderCommentTextSegment(segment: String) {
    if (segment.isEmpty()) return
    BiliRichText(
      text = segment,
      emotes = rowEmotes,
      mentions = comment.mentions,
      mediaLinks = if (renderLinkedMediaCards) emptyList() else mediaLinks,
      onMentionClick = ::openMentionProfile,
      onMediaClick = { media, bounds ->
        when (media.kind) {
          CommentMediaKind.VIDEO ->
            linkedVideos
              .firstOrNull { it.referenceId == media.sourceKey }
              ?.item
              ?.let { openLinkedVideo(it, bounds) }
          CommentMediaKind.ARTICLE ->
            linkedArticles
              .firstOrNull { it.id.toString() == media.sourceKey }
              ?.let { openLinkedArticleAfterReveal(it, bounds) }
        }
      },
      onMediaLongClick = { media ->
        if (media.kind == CommentMediaKind.VIDEO) {
          linkedVideos
            .firstOrNull { it.referenceId == media.sourceKey }
            ?.item
            ?.let(onLinkedVideoLongClick)
        }
      },
      onTimestampClick = onTimestampClick,
      onTextClick = { if (replyEnabled) onReply(comment) },
      style = if (largeText) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
      maxLines = if (textExpanded) Int.MAX_VALUE else 6,
      onOverflowChanged = ::reportTextOverflow,
    )
  }

  @Composable
  fun RenderLinkedMediaCard(reference: CommentMediaReference) {
    when (reference.kind) {
      CommentMediaKind.ARTICLE -> {
        val article = articlesByReference[reference.sourceKey]
        if (!linkedArticlesResolved) {
          CommentMediaCardPlaceholder()
        } else if (article == null) {
          MissingCommentArticleCard(reference.sourceKey)
        } else {
          ArticleCard(
            article = article,
            coverVisible = linkedMediaVisible && hiddenLinkedArticleItemId != article.stableId,
            decorationVisible =
              linkedMediaVisible &&
                mutedLinkedArticleId != article.stableId &&
                hiddenLinkedArticleItemId != article.stableId,
            onClick = { bounds -> openLinkedArticleAfterReveal(article, bounds) },
            onBoundsChanged = {
              linkedArticleBounds[article.stableId] = it
              onLinkedArticleBoundsChanged(article, it)
            },
            loadKey = "${comment.rpid}:${reference.startIndex}",
            compact = true,
            compactHeight = linkedArticleCompactHeight,
          )
        }
      }
      CommentMediaKind.VIDEO -> {
        val resolved = videosByReference[reference.sourceKey]
        val video = resolved?.item
        if (!linkedVideosResolved) {
          CommentMediaCardPlaceholder()
        } else if (video == null) {
          MissingCommentVideoCard(reference.sourceKey)
        } else {
          BoxWithConstraints(Modifier.fillMaxWidth()) {
            RecommendationCard(
              item = video,
              onClick = { openLinkedVideo(video, linkedVideoBounds[video.id] ?: Rect.Zero) },
              onLongClick = { onLinkedVideoLongClick(video) },
              coverVisible = linkedMediaVisible && hiddenLinkedVideoCoverItemId != video.id,
              onCoverBoundsChanged = {
                linkedVideoBounds[video.id] = it
                onLinkedVideoBoundsChanged(video, it)
              },
              cardWidth = minOf(maxWidth, 360.dp),
              compactHorizontal = true,
              compactHeight = 82.dp,
            )
          }
        }
      }
    }
  }
  Box(
    Modifier.fillMaxWidth()
      .padding(horizontal = 2.dp, vertical = 5.dp)
      .bringIntoViewRequester(bringIntoViewRequester)
      .then(
        if (trackBounds) {
          Modifier.onGloballyPositioned {
            measuredBounds.card.coordinates = it
            if (deletionSelected) onDeletionBoundsChanged?.invoke(measuredBounds.card.rect())
          }
        } else Modifier
      )
  ) {
  Surface(
      modifier =
        Modifier.fillMaxWidth()
          .then(
            if (controlEnabled) {
              Modifier.then(
                  if (controlReturnFocusRequester != null) {
                    Modifier.focusRequester(controlReturnFocusRequester)
                  } else Modifier
                )
                .then(
                  if (controlFocusRequester != null) {
                    Modifier.focusRequester(controlFocusRequester)
                  } else Modifier
                )
                .focusProperties {
                  left = controlPlayerFocusRequester ?: FocusRequester.Cancel
                  right = FocusRequester.Cancel
                  controlUpFocusRequester?.let { up = it }
                  if (controlAtListEnd) down = FocusRequester.Cancel
                }
                .onPreviewKeyEvent { event ->
                  val keyCode = event.nativeKeyEvent.keyCode
                  if (
                    event.type == KeyEventType.KeyDown &&
                      event.nativeKeyEvent.repeatCount == 0 &&
                      keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT
                  ) {
                    controlPlayerFocusRequester?.let { requester ->
                      paletteScope.launch { runCatching { requester.requestFocus() } }
                    }
                    return@onPreviewKeyEvent true
                  }
                  if (!isControlConfirmKey(keyCode)) return@onPreviewKeyEvent false
                  if (event.type == KeyEventType.KeyUp && comment.replyCount > 0) {
                    val bounds = measuredBounds.card.rect()
                    if (onControlOpenReplies != null) onControlOpenReplies(comment, bounds)
                    else onReplies(comment, bounds)
                  }
                  true
                }
                .controlFocusOutline(
                  RoundedCornerShape(18.dp),
                  MaterialTheme.colorScheme.primary,
                  width = 3.dp,
                  enabled = true,
                )
                .focusable()
            } else Modifier
          ),
      shape = RoundedCornerShape(18.dp),
      color = if (cardGradient == null) surfaceColor else Color.Transparent,
      // 逐行阴影在评论列表滚动时强制额外离屏工作：渐变、圆角裁剪与描边以低得多的
      // 成本保持同样的卡片分隔。
      tonalElevation = 0.dp,
      shadowElevation = 0.dp,
      border =
        androidx.compose.foundation.BorderStroke(
          .75.dp,
          MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (flat) .52f else .72f),
        ),
    ) {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .then(if (cardGradient != null) Modifier.background(cardGradient) else Modifier)
      ) {
        Box(
          Modifier.matchParentSize()
            .then(if (deletionSelected) Modifier.background(deleteTint) else Modifier)
        )
        Column(
          modifier =
            Modifier.fillMaxWidth()
                .combinedClickable(
                  enabled = !deletionSelected,
                  onClick = { if (replyEnabled) onReply(comment) },
                  onLongClick = {
                    if (copyCommentToClipboard(context, fallbackView, comment)) {
                      android.widget.Toast.makeText(context, "已复制评论内容", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    onDeleteRequest?.invoke(measuredBounds.card.rect())
                  },
                )
        ) {
          Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            CommentAvatar(
              face = comment.face,
              name = comment.name,
              loadKey = comment.rpid.toString(),
              onBitmapReady =
                if (!colorfulCardsEnabled || avatarColors.isNotEmpty() || paletteLoading) null
                else
                  { bitmap ->
                    paletteLoading = true
                    paletteScope.launch {
                      val extracted =
                        CommentAvatarPaletteCache.resolve(comment.face) {
                          withContext(Dispatchers.Default) { extractAvatarDominantColors(bitmap) }
                        }
                      if (extracted.isNotEmpty()) avatarColors = extracted
                      paletteLoading = false
                    }
                  },
              modifier =
                Modifier.size(34.dp)
                  .then(
                    if (trackBounds) {
                      Modifier.onGloballyPositioned { measuredBounds.avatar.coordinates = it }
                    } else Modifier
                  )
                  .then(if (avatarVisible) Modifier else Modifier.graphicsLayer { alpha = 0f })
                  .clip(CircleShape)
                  .clickable(onClick = ::openProfileAfterReveal),
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                  OfficialVerificationIcon(
                    verification = comment.officialVerification,
                    modifier = Modifier.size(OfficialVerificationIconSize),
                  )
                  Text(
                    text = comment.name,
                    modifier =
                      Modifier.weight(1f, fill = false)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = ::openProfileAfterReveal)
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                    style =
                      if (largeText) MaterialTheme.typography.titleSmall
                      else MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color =
                      if (comment.vipActive) Color(0xFFF06A94)
                      else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  CommentLevelIcon(comment.level, loadKey = comment.rpid.toString())
                  if (comment.vipActive)
                    CommentAuthorBadge(comment.vipLabel.ifBlank { "大会员" }, true)
                  if (comment.mid == uploaderMid) CommentAuthorBadge("UP")
                  if (comment.isPinned) CommentAuthorBadge("置顶", true)
                }
                Text(
                  buildList {
                      headerLabel.takeIf(String::isNotBlank)?.let(::add)
                      formatPublishDate(comment.ctime)?.let(::add)
                      if (showLocation && comment.location.isNotBlank())
                        add("IP属地：${comment.location}")
                    }
                    .joinToString("  "),
                  style =
                    if (largeText) MaterialTheme.typography.bodySmall
                    else MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              if (renderLinkedMediaCards && parsedVideoLinks.orderedReferences.isNotEmpty()) {
                // 按链接在原文中的范围切分正文，卡片插回对应位置；不能把所有卡片统一追加到末尾。
                var textCursor = 0
                parsedVideoLinks.orderedReferences.forEach { reference ->
                  val start = reference.startIndex.coerceIn(textCursor, comment.content.length)
                  if (start > textCursor) {
                    RenderCommentTextSegment(comment.content.substring(textCursor, start))
                  }
                  RenderLinkedMediaCard(reference)
                  textCursor = reference.endIndex.coerceIn(start, comment.content.length)
                }
                if (textCursor < comment.content.length) {
                  RenderCommentTextSegment(comment.content.substring(textCursor))
                }
              } else if (displayedCommentText.isNotBlank()) {
                RenderCommentTextSegment(displayedCommentText)
              }
              if (quotedContent.isNotBlank()) {
                Surface(
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(10.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
                  tonalElevation = 0.dp,
                ) {
                  Text(
                    text = "你的评论：$quotedContent",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    style =
                      if (largeText) MaterialTheme.typography.bodySmall
                      else MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (textExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { reportTextOverflow(it.hasVisualOverflow) },
                  )
                }
              }
              CommentImageGallery(comment.images, onImagePreview, trackBounds = trackBounds)
              Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                  modifier =
                    Modifier.clip(RoundedCornerShape(8.dp))
                      .clickable { onLike(comment) }
                      .padding(4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    Icons.Default.ThumbUp,
                    null,
                    modifier = Modifier.size(14.dp),
                    tint =
                      if (comment.liked) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Spacer(Modifier.width(3.dp))
                  Text(
                    "${comment.likeCount}",
                    style =
                      if (largeText) MaterialTheme.typography.bodySmall
                      else MaterialTheme.typography.labelSmall,
                  )
                }
                val upActionLabel =
                  commentUpActionLabel(
                    upLiked = comment.upLiked,
                    upReplied = comment.upReplied,
                  )
                if (comment.replyCount > 0 || upActionLabel != null) {
                  val replySummary = buildString {
                    if (comment.replyCount > 0) append("${comment.replyCount} 条回复")
                    if (upActionLabel != null) {
                      if (isNotEmpty()) append("（$upActionLabel）") else append(upActionLabel)
                    }
                  }
                  Text(
                    replySummary,
                    modifier =
                      Modifier.padding(start = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                          if (comment.replyCount > 0) {
                            Modifier.clickable {
                              onReplies(comment, measuredBounds.card.rect())
                            }
                          } else {
                            Modifier
                          }
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    style =
                      if (largeText) MaterialTheme.typography.bodySmall
                      else MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
              if (textOverflowed) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.End,
                ) {
                  Text(
                    if (textExpanded) "收起" else "展开",
                    modifier =
                      Modifier.clip(RoundedCornerShape(10.dp))
                        .clickable { textExpanded = !textExpanded }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    style =
                      if (largeText) MaterialTheme.typography.bodySmall
                      else MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
        if (deletionSelected) {
          Row(
            modifier =
              Modifier.matchParentSize()
                .background(deleteTint)
                .clickable {}
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
          ) {
            Text(
              "删除这条评论？",
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = { onDeleteCancel?.invoke() }) { Text("取消") }
            if (pinActionAvailable) {
              TextButton(onClick = { onPinRequest?.invoke() }) { Text(pinActionLabel) }
            }
            TextButton(onClick = { onDeleteConfirm?.invoke() }) {
              Text("删除", color = MaterialTheme.colorScheme.error)
            }
          }
        }
      }
    }
  }
}

@Composable
internal fun CommentAvatar(
  face: String,
  name: String,
  loadKey: String = face,
  onBitmapReady: ((Bitmap) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val previouslyLoaded = remember(face) { LoadedFeedImageRegistry.contains(face) }
  var displayed by remember(face) { mutableStateOf(false) }
  val loadPolicy = LocalFeedImageLoadPolicy.current
  val imageAlpha by
    androidx.compose.animation.core.animateFloatAsState(
      targetValue = if (displayed) 1f else 0f,
      animationSpec = androidx.compose.animation.core.tween(180),
      label = "commentAvatarAlpha",
    )
  val context = androidx.compose.ui.platform.LocalContext.current
  val request =
    remember(face, context, previouslyLoaded, displayed, loadPolicy) {
      if (!previouslyLoaded && !displayed && !loadPolicy.permits(loadKey)) null
      else ImageRequest.Builder(context).data(face).size(64, 64).allowHardware(false).build()
    }
  Box(
    modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = name.trim().take(1).ifBlank { "?" },
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (face.isNotBlank()) {
      AsyncImage(
        model = request,
        contentDescription = null,
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = imageAlpha },
        contentScale = ContentScale.Crop,
        onSuccess = { state ->
          displayed = true
          LoadedFeedImageRegistry.markLoaded(face)
          (state.result.image as? BitmapImage)?.bitmap?.let { onBitmapReady?.invoke(it) }
        },
      )
    }
  }
}

@Composable
internal fun CommentLevelIcon(level: Int, loadKey: String = "comment_level_$level") {
  val fileName = if (level >= 6) "level_h.svg" else "level_${level.coerceIn(0, 5)}.svg"
  val imageUrl = "https://i0.hdslb.com/bfs/seed/jinkela/short/webui/user-profile/img/$fileName"
  val policy = LocalFeedImageLoadPolicy.current
  val previouslyLoaded = remember(imageUrl) { LoadedFeedImageRegistry.contains(imageUrl) }
  var displayed by remember(imageUrl) { mutableStateOf(false) }
  AsyncImage(
    model = if (previouslyLoaded || displayed || policy.permits(loadKey)) imageUrl else null,
    contentDescription = "等级 $level",
    modifier = Modifier.size(30.dp),
    contentScale = ContentScale.Fit,
    onSuccess = {
      displayed = true
      LoadedFeedImageRegistry.markLoaded(imageUrl)
    },
  )
}

/** 媒体还在解析时保留原位置和卡片高度，避免正文重排时出现跳动。 */
@Composable
private fun CommentMediaCardPlaceholder() {
  Surface(
    modifier = Modifier.fillMaxWidth().height(82.dp),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f),
    tonalElevation = 0.dp,
  ) {}
}

/** 专栏资料失效时仍保留卡片占位，避免正文中的媒体位置消失。 */
@Composable
private fun MissingCommentArticleCard(referenceId: String) {
  Surface(
    modifier = Modifier.fillMaxWidth().height(82.dp),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .66f),
    tonalElevation = 0.dp,
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        "这个专栏好像不见啦 (´；ω；`)",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        "cv$referenceId",
        modifier = Modifier.padding(top = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** 视频资料失效时仍保留卡片占位，避免评论正文在异步解析后塌陷。 */
@Composable
private fun MissingCommentVideoCard(referenceId: String) {
  Surface(
    modifier = Modifier.fillMaxWidth().height(82.dp),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .66f),
    tonalElevation = 0.dp,
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        "这个视频好像不见啦 (´；ω；`)",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        referenceId,
        modifier = Modifier.padding(top = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
internal fun CommentAuthorBadge(label: String, vip: Boolean = false) {
  Surface(
    shape = RoundedCornerShape(6.dp),
    color =
      if (vip) Color(0xFFF06A94).copy(alpha = .13f) else MaterialTheme.colorScheme.surfaceVariant,
    contentColor = if (vip) Color(0xFFF06A94) else MaterialTheme.colorScheme.onSurfaceVariant,
    tonalElevation = 0.dp,
  ) {
    Text(
      label,
      modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
    )
  }
}
