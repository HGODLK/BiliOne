package dev.openbili.webdemo.video

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.BitmapImage
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import dev.openbili.webdemo.api.BiliApi
import dev.openbili.webdemo.api.BiliEmote
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.CommentImage
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.LocalFeedImageLoadPolicy
import dev.openbili.webdemo.feed.LoadedFeedImageRegistry
import dev.openbili.webdemo.article.ArticleCard
import dev.openbili.webdemo.ui.NavigationCardBottomClearance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Layout coordinates are consumed by click/transition callbacks, not by the visual tree itself.
 * Keeping them outside Compose Snapshot state prevents every visible comment from recomposing on
 * every scroll placement pass.
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

private data class ResolvedCommentVideoLink(
  val referenceId: String,
  val item: FeedItem?,
)

@Composable
internal fun CommentImageGallery(
  images: List<CommentImage>,
  onPreview: (CommentImage, Rect) -> Unit,
  trackBounds: Boolean = true,
  modifier: Modifier = Modifier,
) {
  if (images.isEmpty()) return
  val imageHeightLimit =
    minOf(360.dp, LocalConfiguration.current.screenHeightDp.dp * (2f / 3f))
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
  AsyncImage(
    model = image.url,
    contentDescription = contentDescription,
    modifier =
      modifier
        .then(
          if (trackBounds) Modifier.onGloballyPositioned { bounds.coordinates = it } else Modifier
        )
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable { onPreview(image, bounds.rect()) },
    contentScale = contentScale,
  )
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
  bottomClearancePx: Float = 0f,
  viewportHeightPx: Float = 0f,
  flat: Boolean = false,
  avatarVisible: Boolean = true,
  trackBounds: Boolean = true,
  hiddenLinkedVideoCoverItemId: String? = null,
  onLinkedVideoClick: (FeedItem, Rect) -> Unit = { _, _ -> },
  onLinkedVideoBoundsChanged: (FeedItem, Rect) -> Unit = { _, _ -> },
  onLinkedVideoLongClick: (FeedItem) -> Unit = {},
  hiddenLinkedArticleItemId: String? = null,
  onLinkedArticleClick: (ArticleItem, Rect) -> Unit = { _, _ -> },
  onLinkedArticleBoundsChanged: (ArticleItem, Rect) -> Unit = { _, _ -> },
  headerLabel: String = "",
  quotedContent: String = "",
  linkedMediaVisible: Boolean = true,
  linkedArticleCompactHeight: Dp? = null,
  deletionSelected: Boolean = false,
  onDeleteRequest: ((Rect) -> Unit)? = null,
  onDeleteConfirm: (() -> Unit)? = null,
  onDeleteCancel: (() -> Unit)? = null,
  onDeletionBoundsChanged: ((Rect) -> Unit)? = null,
  largeText: Boolean = false,
) {
  val measuredBounds = remember(comment.rpid) { CommentRowBounds() }
  var openingProfile by remember(comment.rpid) { mutableStateOf(false) }
  var openingLinkedVideo by remember(comment.rpid) { mutableStateOf(false) }
  var openingLinkedArticle by remember(comment.rpid) { mutableStateOf(false) }
  var mutedLinkedArticleId by remember(comment.rpid) { mutableStateOf<String?>(null) }
  val linkedVideoBounds = remember(comment.rpid) { mutableMapOf<String, Rect>() }
  val linkedArticleBounds = remember(comment.rpid) { mutableMapOf<String, Rect>() }
  val bringIntoViewRequester = remember(comment.rpid) { BringIntoViewRequester() }
  // Read the active Material scheme instead of the OS flag so the app's manual dark-mode
  // setting follows the same palette as system dark mode.
  val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
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
      // One frame applies the list movement; the second lets the cover publish its final root
      // bounds before the shared-element session captures the source rectangle.
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
  fun openLinkedVideoAfterReveal(video: FeedItem, clickedBounds: Rect) {
    if (openingLinkedVideo || openingLinkedArticle || openingProfile) return
    openingLinkedVideo = true
    paletteScope.launch {
      revealCommentForNavigation()
      val bounds =
        linkedVideoBounds[video.id]?.takeIf { it.width > 0f && it.height > 0f } ?: clickedBounds
      if (bounds.width > 0f && bounds.height > 0f) onLinkedVideoClick(video, bounds)
      openingLinkedVideo = false
    }
  }
  fun openLinkedArticleAfterReveal(article: ArticleItem, clickedBounds: Rect) {
    if (openingLinkedArticle || openingLinkedVideo || openingProfile) return
    openingLinkedArticle = true
    mutedLinkedArticleId = article.stableId
    paletteScope.launch {
      // Let the title and dark readability scrim disappear before the shared cover starts moving.
      kotlinx.coroutines.delay(160)
      revealCommentForNavigation()
      val bounds =
        linkedArticleBounds[article.stableId]?.takeIf { it.width > 0f && it.height > 0f }
          ?: clickedBounds
      if (bounds.width > 0f && bounds.height > 0f) onLinkedArticleClick(article, bounds)
      // The root layer takes over source visibility once its transition target is stable.
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
    remember(comment.face, darkTheme) {
      mutableStateOf(CommentAvatarPaletteCache.get(comment.face).orEmpty())
    }
  val surfaceColor = MaterialTheme.colorScheme.surface
  val cardGradientColors =
    remember(avatarColors, surfaceColor, darkTheme) {
      if (avatarColors.isEmpty()) emptyList()
      else
        avatarColors.take(2).map {
          readableCommentCardColor(it, surfaceColor, darkTheme)
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
          // The API's per-comment map is authoritative. The token lookup only preserves a
          // compatibility path for older responses that omit content.emote.
          comment.emotes.forEach { (text, url) -> put(text, BiliEmote(text, url)) }
          commentEmoteTokenPattern.findAll(comment.content).forEach { match ->
            val token = match.value
            if (!containsKey(token)) emoteCatalog[token]?.let { put(token, it) }
          }
        }
    }
  val parsedVideoLinks = remember(comment.content) { parseCommentVideoLinks(comment.content) }
  val linkedBvids =
    remember(parsedVideoLinks) { parsedVideoLinks.links.map { it.bvid }.distinct().take(3) }
  val linkedArticleRefs =
    remember(parsedVideoLinks) { parsedVideoLinks.articleLinks.distinctBy { it.sourceUrl }.take(3) }
  var linkedVideos by
    remember(comment.rpid, comment.content) {
      mutableStateOf(emptyList<ResolvedCommentVideoLink>())
    }
  var linkedVideosResolved by
    remember(comment.rpid, comment.content) { mutableStateOf(linkedBvids.isEmpty()) }
  var linkedArticles by
    remember(comment.rpid, comment.content) { mutableStateOf(emptyList<ArticleItem>()) }
  var linkedArticlesResolved by
    remember(comment.rpid, comment.content) { mutableStateOf(linkedArticleRefs.isEmpty()) }
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
          val info =
            runCatching {
                if (referenceId.startsWith("BV", ignoreCase = true)) {
                  BiliApi.getVideoInfo(referenceId)
                } else {
                  BiliApi.getVideoInfoByAid(referenceId.removePrefix("av").toLongOrNull() ?: 0L)
                }
              }
              .getOrNull()
          ResolvedCommentVideoLink(referenceId, info?.toCommentVideoFeedItem())
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
          val fallback =
            ArticleItem(
              id = link.articleId,
              title = "专栏",
              sourceUrl = link.sourceUrl,
            )
          runCatching { BiliApi.getArticleDetail(fallback).article }.getOrNull()
        }
      }
    linkedArticlesResolved = true
  }
  val mappedBvids =
    remember(linkedVideos) { linkedVideos.mapTo(linkedSetOf()) { it.referenceId } }
  val mappedArticleIds =
    remember(linkedArticles) { linkedArticles.mapTo(linkedSetOf()) { it.id } }
  val displayedCommentText =
    remember(parsedVideoLinks, mappedBvids, mappedArticleIds) {
      parsedVideoLinks.textWithMappedLinksRemoved(mappedBvids, mappedArticleIds)
    }
  var textExpanded by
    remember(comment.rpid, displayedCommentText, quotedContent) { mutableStateOf(false) }
  var textOverflowed by
    remember(comment.rpid, displayedCommentText, quotedContent) { mutableStateOf(false) }
  fun reportTextOverflow(overflowed: Boolean) {
    if (!textExpanded && overflowed) textOverflowed = true
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
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(18.dp),
      color = if (cardGradient == null) surfaceColor else Color.Transparent,
      // Per-row shadows force additional offscreen work while the comment list scrolls. The
      // gradient, rounded clipping and border preserve the same card separation at far lower cost.
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
              .animateContentSize(
                animationSpec = androidx.compose.animation.core.tween(220),
              )
              .combinedClickable(
                enabled = !deletionSelected,
                onClick = { onReply(comment) },
                onLongClick =
                  onDeleteRequest?.let { request -> { request(measuredBounds.card.rect()) } },
              )
        ) {
          Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            CommentAvatar(
              face = comment.face,
              name = comment.name,
              loadKey = comment.rpid.toString(),
              onBitmapReady =
                if (avatarColors.isNotEmpty() || paletteLoading) null
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
              if (displayedCommentText.isNotBlank()) {
                BiliRichText(
                  text = displayedCommentText,
                  emotes = rowEmotes,
                  mentions = comment.mentions,
                  onMentionClick = ::openMentionProfile,
                  onTextClick = { onReply(comment) },
                  style =
                    if (largeText) MaterialTheme.typography.bodyMedium
                    else MaterialTheme.typography.bodySmall,
                  maxLines = if (textExpanded) Int.MAX_VALUE else 6,
                  onOverflowChanged = ::reportTextOverflow,
                )
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
              if (
                (!linkedVideosResolved && linkedBvids.isNotEmpty()) ||
                  (!linkedArticlesResolved && linkedArticleRefs.isNotEmpty())
              ) {
                repeat(
                  (if (linkedVideosResolved) 0 else linkedBvids.size) +
                    (if (linkedArticlesResolved) 0 else linkedArticleRefs.size)
                ) {
                  Surface(
                    modifier = Modifier.fillMaxWidth().height(82.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f),
                    tonalElevation = 0.dp,
                  ) {}
                }
              } else {
                linkedArticles.forEach { article ->
                  ArticleCard(
                    article = article,
                    coverVisible =
                      linkedMediaVisible && hiddenLinkedArticleItemId != article.stableId,
                    decorationVisible =
                      linkedMediaVisible &&
                        mutedLinkedArticleId != article.stableId &&
                        hiddenLinkedArticleItemId != article.stableId,
                    onClick = { bounds -> openLinkedArticleAfterReveal(article, bounds) },
                    onBoundsChanged = {
                      linkedArticleBounds[article.stableId] = it
                      onLinkedArticleBoundsChanged(article, it)
                    },
                    loadKey = comment.rpid.toString(),
                    compact = true,
                    compactHeight = linkedArticleCompactHeight,
                  )
                }
                linkedVideos.forEach { resolved ->
                  val video = resolved.item
                  if (video == null) {
                    MissingCommentVideoCard(resolved.referenceId)
                  } else {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                      RecommendationCard(
                        item = video,
                        onClick = {
                          openLinkedVideoAfterReveal(
                            video,
                            linkedVideoBounds[video.id] ?: Rect.Zero,
                          )
                        },
                        onLongClick = { onLinkedVideoLongClick(video) },
                        coverVisible =
                          linkedMediaVisible && hiddenLinkedVideoCoverItemId != video.id,
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
    model =
      if (previouslyLoaded || displayed || policy.permits(loadKey)) imageUrl else null,
    contentDescription = "等级 $level",
    modifier = Modifier.size(30.dp),
    contentScale = ContentScale.Fit,
    onSuccess = {
      displayed = true
      LoadedFeedImageRegistry.markLoaded(imageUrl)
    },
  )
}

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
