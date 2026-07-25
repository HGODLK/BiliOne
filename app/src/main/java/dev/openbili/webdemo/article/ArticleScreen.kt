package dev.openbili.webdemo.article

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import dev.openbili.webdemo.api.ArticleBlock
import dev.openbili.webdemo.api.ArticleDetail
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.BiliApi
import dev.openbili.webdemo.api.BiliEmote
import dev.openbili.webdemo.api.BiliEmotePackage
import dev.openbili.webdemo.api.CommentImage
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.CommentNavigationTarget
import dev.openbili.webdemo.api.CommentSort
import dev.openbili.webdemo.api.MentionSuggestion
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.FeedImageLoadMode
import dev.openbili.webdemo.feed.LocalFeedImageLoadPolicy
import dev.openbili.webdemo.feed.rememberListFeedImageLoadPolicy
import dev.openbili.webdemo.feed.FeedViewModel
import dev.openbili.webdemo.ui.VideoShapeTokens
import dev.openbili.webdemo.video.BiliRichText
import dev.openbili.webdemo.video.CommentComposer
import dev.openbili.webdemo.video.CommentImagePreviewOverlay
import dev.openbili.webdemo.video.CommentImagePreviewSession
import dev.openbili.webdemo.video.CommentProfileAnchor
import dev.openbili.webdemo.video.CommentRow
import dev.openbili.webdemo.video.RecommendationCard
import dev.openbili.webdemo.video.ReplyThreadPanel
import dev.openbili.webdemo.video.ReplyThreadTransitionContainer
import dev.openbili.webdemo.video.toCommentVideoFeedItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.withFrameNanos

@Composable
fun ArticleScreen(
  article: ArticleItem,
  detail: ArticleDetail?,
  loading: Boolean,
  error: String?,
  contentLoadEnabled: Boolean,
  currentAccountMid: Long,
  showCommentEmotes: Boolean,
  showCommentLocation: Boolean,
  sharedEmotes: List<BiliEmote>,
  sharedEmotePackages: List<BiliEmotePackage>,
  mentionSuggestions: List<MentionSuggestion>,
  mentionSuggestionsLoading: Boolean,
  hiddenVideoCoverItemId: String?,
  hiddenLinkedArticleItemId: String?,
  commentNavigationTarget: CommentNavigationTarget?,
  onBack: () -> Unit,
  onHome: () -> Unit,
  onRetry: () -> Unit,
  onMentionQuery: (String) -> Unit,
  onAuthorProfile: (Long, String, String, Rect) -> Unit,
  onProfile: (Long) -> Unit,
  onCommentProfile: (Long, CommentItem, CommentProfileAnchor) -> Unit,
  onCommentNavigationConsumed: () -> Unit,
  onVideo: (FeedItem, Rect) -> Unit,
  onVideoLongClick: (FeedItem) -> Unit,
  onVideoBoundsChanged: (FeedItem, Rect) -> Unit,
  onArticle: (ArticleItem, Rect) -> Unit,
  onHeroBoundsChanged: (Rect) -> Unit,
  heroVisible: Boolean = true,
  interactionEnabled: Boolean = true,
  backEnabled: Boolean = true,
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  val listState = rememberLazyListState()
  val imageLoadPolicy = rememberListFeedImageLoadPolicy(listState)
  val shownArticle = detail?.article ?: article
  val commentOid = detail?.commentOid ?: article.id
  val commentType = detail?.commentType ?: 12
  var rootBounds by remember(article.id) { mutableStateOf(Rect.Zero) }
  var heroBounds by remember(article.id) { mutableStateOf(Rect.Zero) }
  var authorAvatarBounds by remember(article.id) { mutableStateOf(Rect.Zero) }
  var imagePreview by remember(article.id) { mutableStateOf<CommentImagePreviewSession?>(null) }
  var comments by remember(article.id) { mutableStateOf(emptyList<CommentItem>()) }
  var commentsPage by remember(article.id) { mutableIntStateOf(1) }
  var commentsHasMore by remember(article.id) { mutableStateOf(false) }
  var commentsLoading by remember(article.id) { mutableStateOf(false) }
  var commentTotal by remember(article.id) { mutableLongStateOf(article.replyCount) }
  var commentSort by remember(article.id) { mutableStateOf(CommentSort.DEFAULT) }
  var commentLoadToken by remember(article.id) { mutableLongStateOf(0L) }
  var replyRoot by remember(article.id) { mutableStateOf<CommentItem?>(null) }
  var displayedReplyRoot by remember(article.id) { mutableStateOf<CommentItem?>(null) }
  var replySourceBounds by remember(article.id) { mutableStateOf(Rect.Zero) }
  var navigationRootBounds by remember(article.id) { mutableStateOf(Rect.Zero) }
  var openedNavigationRequestId by remember(article.id) { mutableStateOf<Long?>(null) }
  var replyTargetBounds by remember(article.id) { mutableStateOf(Rect.Zero) }
  val replyTransitionProgress = remember(article.id) { Animatable(0f) }
  var replyContentReady by remember(article.id) { mutableStateOf(false) }
  var replies by remember(article.id) { mutableStateOf(emptyList<CommentItem>()) }
  var repliesPage by remember(article.id) { mutableIntStateOf(1) }
  var repliesHasMore by remember(article.id) { mutableStateOf(false) }
  var repliesLoading by remember(article.id) { mutableStateOf(false) }
  var replyTarget by remember(article.id) { mutableStateOf<CommentItem?>(null) }
  var replyTargetRoot by remember(article.id) { mutableStateOf<CommentItem?>(null) }
  var composerHeightPx by remember(article.id) { mutableFloatStateOf(0f) }
  var localEmotePackages by remember(article.id) { mutableStateOf(sharedEmotePackages) }
  val availableEmotes =
    remember(sharedEmotes, localEmotePackages) {
      (sharedEmotes + localEmotePackages.flatMap { it.emotes }).distinctBy { it.text }
    }
  val emoteCatalog = remember(availableEmotes) { availableEmotes.associateBy { it.text } }

  fun openImage(image: CommentImage, bounds: Rect) {
    if (!interactionEnabled || bounds.width <= 0f || bounds.height <= 0f) return
    imagePreview = CommentImagePreviewSession(image, bounds).also { session ->
      scope.launch { session.progress.animateTo(1f, tween(280)) }
    }
  }

  fun closeImage() {
    val session = imagePreview ?: return
    scope.launch {
      session.progress.animateTo(0f, tween(220))
      imagePreview = null
    }
  }

  fun requestBack() {
    if (!backEnabled || !interactionEnabled) return
    scope.launch {
      if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
        listState.animateScrollToItem(0)
      }
      androidx.compose.runtime.withFrameNanos {}
      onBack()
    }
  }

  fun loadComments(page: Int) {
    if (commentsLoading || commentOid <= 0L) return
    val token = ++commentLoadToken
    commentsLoading = true
    scope.launch {
      runCatching {
          withContext(Dispatchers.IO) {
            BiliApi.getComments(commentOid, page, commentSort.apiValue, commentType)
          }
        }
        .onSuccess { response ->
          if (token != commentLoadToken) return@onSuccess
          comments =
            if (page == 1) response.items
            else (comments + response.items).distinctBy { it.rpid }
          commentsPage = page
          commentsHasMore = response.hasMore
          commentTotal = response.totalCount
        }
        .onFailure {
          if (token == commentLoadToken) {
            Toast.makeText(context, it.message ?: "评论加载失败", Toast.LENGTH_SHORT).show()
          }
        }
      if (token == commentLoadToken) commentsLoading = false
    }
  }

  fun updateCommentLike(target: CommentItem, liked: Boolean) {
    fun update(comment: CommentItem) =
        if (comment.rpid == target.rpid) {
          comment.copy(
            liked = liked,
            likeCount = (comment.likeCount + if (liked) 1 else -1).coerceAtLeast(0),
          )
        } else comment
    comments = comments.map(::update)
    replies = replies.map(::update)
  }

  fun loadReplies(root: CommentItem, page: Int) {
    if (repliesLoading || commentOid <= 0L) return
    repliesLoading = true
    scope.launch {
      runCatching {
          withContext(Dispatchers.IO) {
            BiliApi.getCommentReplies(commentOid, root.rpid, page, commentType)
          }
        }
        .onSuccess { response ->
          replies =
            if (page == 1) response.items else (replies + response.items).distinctBy { it.rpid }
          repliesPage = page
          repliesHasMore = response.hasMore
        }
        .onFailure {
          Toast.makeText(context, it.message ?: "回复加载失败", Toast.LENGTH_SHORT).show()
        }
      repliesLoading = false
    }
  }

  fun likeComment(comment: CommentItem) {
    val liked = !comment.liked
    updateCommentLike(comment, liked)
    scope.launch {
      runCatching {
          withContext(Dispatchers.IO) {
            BiliApi.setCommentLike(commentOid, comment.rpid, liked, commentType)
          }
        }
        .onFailure {
          updateCommentLike(comment.copy(liked = liked), !liked)
          Toast.makeText(context, it.message ?: "操作失败", Toast.LENGTH_SHORT).show()
        }
    }
  }

  LaunchedEffect(contentLoadEnabled, detail, commentOid, commentType, commentSort) {
    commentLoadToken += 1
    comments = emptyList()
    commentsHasMore = false
    commentsPage = 1
    commentsLoading = false
    if (contentLoadEnabled && detail != null) loadComments(1)
  }
  LaunchedEffect(article.id, contentLoadEnabled, sharedEmotePackages.isEmpty()) {
    if (!contentLoadEnabled) return@LaunchedEffect
    if (sharedEmotePackages.isEmpty()) {
      localEmotePackages =
        runCatching { withContext(Dispatchers.IO) { BiliApi.getReplyEmotes() } }.getOrDefault(
          emptyList()
        )
    } else {
      localEmotePackages = sharedEmotePackages
    }
  }
  LaunchedEffect(
    listState,
    commentsHasMore,
    commentsLoading,
    comments.size,
    imageLoadPolicy.mode,
  ) {
    snapshotFlow {
        val layout = listState.layoutInfo
        (layout.visibleItemsInfo.lastOrNull()?.index ?: 0) to layout.totalItemsCount
      }
      .distinctUntilChanged()
      .filter { (last, total) ->
        contentLoadEnabled &&
          commentsHasMore &&
          !commentsLoading &&
          imageLoadPolicy.mode != FeedImageLoadMode.PAUSED &&
          total > 0 &&
          last >= total - 4
      }
      .collect { loadComments(commentsPage + 1) }
  }
  LaunchedEffect(replyRoot?.rpid) {
    val root = replyRoot
    if (root != null) {
      displayedReplyRoot = root
      replyContentReady = false
      replyTransitionProgress.snapTo(0f)
      repeat(3) { withFrameNanos {} }
      replyTransitionProgress.animateTo(
        1f,
        tween(320, easing = FastOutSlowInEasing),
      )
      withFrameNanos {}
      replyContentReady = true
    } else if (displayedReplyRoot != null) {
      replyContentReady = false
      replyTransitionProgress.animateTo(
        0f,
        tween(280, easing = FastOutSlowInEasing),
      )
      delay(16)
      displayedReplyRoot = null
      replyTargetBounds = Rect.Zero
    }
  }

  val commentsVisible by
    remember(listState) {
      derivedStateOf {
        listState.layoutInfo.visibleItemsInfo.any { item ->
          item.key.toString().startsWith("article_comments") ||
            item.key.toString().startsWith("article_comment_")
        }
      }
    }
  val bodyItemCount = if (loading || error != null) 1 else detail?.blocks.orEmpty().size
  val commentsHeaderIndex = 1 + bodyItemCount
  LaunchedEffect(
    commentNavigationTarget?.requestId,
    commentNavigationTarget?.targetRpid,
    commentNavigationTarget?.rootRpid,
    commentOid,
    contentLoadEnabled,
  ) {
    val target = commentNavigationTarget ?: return@LaunchedEffect
    if (!contentLoadEnabled || detail == null) return@LaunchedEffect
    if (openedNavigationRequestId == target.requestId) return@LaunchedEffect
    while (commentsLoading) delay(24)
    var rootIndex = comments.indexOfFirst { it.rpid == target.rootRpid }
    if (rootIndex < 0) {
      val thread =
        runCatching {
            withContext(Dispatchers.IO) {
              BiliApi.getCommentThread(commentOid, target.rootRpid, commentType)
            }
          }
          .getOrNull()
      if (thread == null) {
        Toast.makeText(context, "这条评论可能已被删除", Toast.LENGTH_SHORT).show()
        onCommentNavigationConsumed()
        return@LaunchedEffect
      }
      comments = (listOf(thread.root) + comments).distinctBy { it.rpid }
      commentTotal = maxOf(commentTotal, comments.size.toLong())
      rootIndex = comments.indexOfFirst { it.rpid == target.rootRpid }
    }
    if (rootIndex < 0) return@LaunchedEffect
    listState.animateScrollToItem(commentsHeaderIndex + 1 + rootIndex)
    repeat(8) {
      withFrameNanos {}
      if (navigationRootBounds.width > 0f && navigationRootBounds.height > 0f) return@repeat
    }
    openedNavigationRequestId = target.requestId
    if (target.targetRpid == target.rootRpid) {
      onCommentNavigationConsumed()
    } else {
      replySourceBounds =
        navigationRootBounds.takeIf { it.width > 0f && it.height > 0f } ?: rootBounds
      val root = comments[rootIndex]
      replies = emptyList()
      repliesPage = 1
      repliesHasMore = false
      replyRoot = root
      loadReplies(root, 1)
    }
  }
  val composerClearance =
    with(density) { composerHeightPx.toDp() }.coerceAtLeast(if (commentsVisible) 150.dp else 96.dp)

  BackHandler(
    enabled =
      backEnabled && interactionEnabled && imagePreview == null && displayedReplyRoot == null,
    onBack = ::requestBack,
  )
  BoxWithConstraints(
    Modifier.fillMaxSize().onGloballyPositioned { rootBounds = it.boundsInRoot() }
  ) {
    val viewportHeight = maxHeight
    val horizontalPadding = if (maxWidth < 700.dp) 16.dp else 28.dp
    val heroWidth =
      minOf(
        (maxWidth - horizontalPadding * 2).coerceAtLeast(1.dp),
        920.dp,
        viewportHeight / 2f * (16f / 9f),
      )
    val heroHeight = heroWidth / (16f / 9f)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Column(Modifier.fillMaxSize()) {
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(onClick = ::requestBack, enabled = backEnabled && interactionEnabled) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
          }
          IconButton(onClick = onHome, enabled = interactionEnabled) {
            Icon(Icons.Default.Home, contentDescription = "返回首页")
          }
          Text(
            "专栏",
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
          )
        }
        CompositionLocalProvider(LocalFeedImageLoadPolicy provides imageLoadPolicy) {
          LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          item(key = "article_header_${article.id}") {
            Column(
              Modifier.fillMaxWidth().widthIn(max = 980.dp).padding(horizontal = horizontalPadding),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              ArticleVisual(
                article = shownArticle,
                visible = heroVisible,
                enforceAspectRatio = false,
                alwaysLoad = true,
                modifier =
                  Modifier.width(heroWidth)
                    .height(heroHeight)
                    .align(Alignment.CenterHorizontally)
                    .onGloballyPositioned {
                      heroBounds = it.boundsInRoot()
                      onHeroBoundsChanged(heroBounds)
                    }
                    .clickable(
                      enabled = interactionEnabled && shownArticle.coverUrl.isNotBlank()
                    ) {
                      openImage(CommentImage(shownArticle.coverUrl), heroBounds)
                    },
              )
              Text(
                shownArticle.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
              )
              Row(
                modifier =
                  Modifier.clip(RoundedCornerShape(14.dp)).clickable(
                    enabled = interactionEnabled && shownArticle.authorMid > 0L
                  ) {
                    onAuthorProfile(
                      shownArticle.authorMid,
                      shownArticle.authorFace,
                      shownArticle.authorName,
                      authorAvatarBounds,
                    )
                  }.padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                if (shownArticle.authorFace.isNotBlank()) {
                  AsyncImage(
                    model = shownArticle.authorFace,
                    contentDescription = shownArticle.authorName,
                    modifier =
                      Modifier.size(38.dp)
                        .onGloballyPositioned { authorAvatarBounds = it.boundsInRoot() }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                  )
                }
                Column(
                  Modifier.padding(start = if (shownArticle.authorFace.isBlank()) 0.dp else 10.dp)
                ) {
                  Text(
                    shownArticle.authorName.ifBlank { "专栏作者" },
                    style = MaterialTheme.typography.titleSmall,
                  )
                  Text(
                    articleMeta(shownArticle),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
          }
          if (loading) {
            item(key = "article_loading") {
              CircularProgressIndicator(Modifier.padding(32.dp).size(28.dp), strokeWidth = 2.dp)
            }
          } else if (error != null) {
            item(key = "article_error") {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("重试") }
              }
            }
          } else {
            itemsIndexed(
              items = detail?.blocks.orEmpty(),
              key = { index, block -> "${article.id}_${block::class.simpleName}_$index" },
            ) { _, block ->
              ArticleBlockContent(
                block = block,
                viewportHeight = viewportHeight,
                horizontalPadding = horizontalPadding,
                emoteCatalog = emoteCatalog,
                hiddenVideoCoverItemId = hiddenVideoCoverItemId,
                interactionEnabled = interactionEnabled,
                onImage = ::openImage,
                onProfile = onProfile,
                onVideo = onVideo,
                onVideoLongClick = onVideoLongClick,
                onVideoBoundsChanged = onVideoBoundsChanged,
              )
            }
          }
          item(key = "article_comments_header") {
            Row(
              Modifier.fillMaxWidth()
                .widthIn(max = 900.dp)
                .padding(horizontal = horizontalPadding, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                "评论 ${FeedViewModel.formatCount(commentTotal)}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
              CommentSort.entries.forEach { sort ->
                FilterChip(
                  selected = commentSort == sort,
                  onClick = { if (sort != commentSort) commentSort = sort },
                  enabled = interactionEnabled,
                  label = { Text(sort.label) },
                  modifier = Modifier.padding(start = 8.dp),
                )
              }
            }
          }
          itemsIndexed(
            items = comments,
            key = { _, comment -> "article_comment_${comment.rpid}" },
            contentType = { _, _ -> "article_comment" },
          ) { _, comment ->
            Box(
              Modifier.fillMaxWidth()
                .widthIn(max = 900.dp)
                .padding(horizontal = horizontalPadding)
                .onGloballyPositioned {
                  if (commentNavigationTarget?.rootRpid == comment.rpid) {
                    navigationRootBounds = it.boundsInRoot()
                  }
                }
            ) {
              CommentRow(
                comment = comment,
                showEmotes = showCommentEmotes,
                emoteCatalog = emoteCatalog,
                showLocation = showCommentLocation,
                onLike = ::likeComment,
                uploaderMid = shownArticle.authorMid,
                onProfileClick = onCommentProfile,
                onImagePreview = ::openImage,
                onReplies = { target, bounds ->
                  replySourceBounds = bounds
                  replies = emptyList()
                  repliesPage = 1
                  repliesHasMore = false
                  replyRoot = target
                  loadReplies(target, 1)
                },
                onReply = { target ->
                  replyTargetRoot = target
                  replyTarget = target
                },
                bottomClearancePx = composerHeightPx,
                viewportHeightPx = rootBounds.height,
                hiddenLinkedVideoCoverItemId = hiddenVideoCoverItemId,
                onLinkedVideoClick = onVideo,
                onLinkedVideoBoundsChanged = onVideoBoundsChanged,
                onLinkedVideoLongClick = onVideoLongClick,
                onLinkedArticleClick = onArticle,
                hiddenLinkedArticleItemId = hiddenLinkedArticleItemId,
                largeText = true,
              )
            }
          }
          if (commentsLoading) {
            item(key = "article_comments_loading") {
              CircularProgressIndicator(Modifier.padding(18.dp).size(24.dp), strokeWidth = 2.dp)
            }
          } else if (comments.isEmpty()) {
            item(key = "article_comments_empty") {
              Text(
                "还没有评论，来坐第一排吧~",
                modifier = Modifier.padding(vertical = 18.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          item(key = "article_comments_safe_space") { Spacer(Modifier.height(composerClearance + 28.dp)) }
          }
        }
      }
    }

    AnimatedVisibility(
      visible = (commentsVisible || displayedReplyRoot != null) && interactionEnabled,
      modifier = Modifier.align(Alignment.BottomEnd).zIndex(30f),
      enter = fadeIn(tween(220)),
      exit = fadeOut(tween(150)),
    ) {
      CommentComposer(
        emotes = availableEmotes,
        emotePackages = localEmotePackages,
        mentionSuggestions = mentionSuggestions,
        mentionSuggestionsLoading = mentionSuggestionsLoading,
        onMentionQuery = onMentionQuery,
        targetName = replyTarget?.name,
        onClearTarget = {
          replyTarget = null
          replyTargetRoot = null
        },
        imageEnabled = false,
        onSend = { message, _ ->
          val target = replyTarget
          scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                  if (target == null) BiliApi.addComment(commentOid, message, commentType)
                  else
                    BiliApi.addReply(
                      commentOid,
                      (replyTargetRoot ?: target).rpid,
                      target.rpid,
                      message,
                      commentType,
                    )
                }
              }
              .onSuccess { added ->
                if (target == null) {
                  comments = listOf(added) + comments
                  commentTotal += 1
                } else {
                  val root = replyTargetRoot ?: target
                  comments =
                    comments.map { comment ->
                      if (comment.rpid == root.rpid) {
                        comment.copy(replyCount = comment.replyCount + 1)
                      } else comment
                    }
                  replies = (replies + added).distinctBy { it.rpid }
                }
                replyTarget = null
                replyTargetRoot = null
              }
              .onFailure {
                Toast.makeText(context, it.message ?: "发送失败", Toast.LENGTH_SHORT).show()
              }
          }
        },
        modifier =
          Modifier.width(maxWidth * .4f)
            .navigationBarsPadding()
            .imePadding()
            .padding(12.dp)
            .onGloballyPositioned { composerHeightPx = it.size.height.toFloat() },
      )
    }

    if (displayedReplyRoot == null) {
      Column(
        modifier =
          Modifier.align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(end = 16.dp, bottom = composerClearance + 12.dp)
            .zIndex(22f),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        SmallFloatingActionButton(
          onClick = { scope.launch { listState.animateScrollToItem(0) } },
          containerColor = MaterialTheme.colorScheme.surfaceVariant,
          contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
          Icon(Icons.Default.KeyboardArrowUp, contentDescription = "返回专栏顶部")
        }
        SmallFloatingActionButton(
          onClick = { scope.launch { listState.animateScrollToItem(commentsHeaderIndex) } },
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
          Icon(ArticleCommentBubbleIcon, contentDescription = "前往评论区")
        }
      }
    }

    displayedReplyRoot?.let { root ->
      Box(
        Modifier.fillMaxSize()
          .padding(top = 56.dp)
          .zIndex(25f)
          .onGloballyPositioned { replyTargetBounds = it.boundsInRoot() }
      ) {
        ReplyThreadTransitionContainer(
          sourceBounds = replySourceBounds,
          targetBounds = replyTargetBounds,
          progress = { replyTransitionProgress.value },
          contentReady = replyContentReady,
          modifier = Modifier.fillMaxSize(),
        ) {
          ReplyThreadPanel(
            root = root,
            replies = replies,
            hasMore = repliesHasMore,
            loading = repliesLoading,
            showEmotes = showCommentEmotes,
            emoteCatalog = emoteCatalog,
            showLocation = showCommentLocation,
            onLike = ::likeComment,
            uploaderMid = shownArticle.authorMid,
            onProfileClick = onCommentProfile,
            onImagePreview = ::openImage,
            onReply = { rootComment, target ->
              replyTargetRoot = rootComment
              replyTarget = target
            },
            onLoadMore = { loadReplies(root, repliesPage + 1) },
            navigationTargetRpid =
              commentNavigationTarget
                ?.targetRpid
                ?.takeIf { commentNavigationTarget.rootRpid == root.rpid },
            navigationRequestId =
              commentNavigationTarget
                ?.requestId
                ?.takeIf { commentNavigationTarget.rootRpid == root.rpid },
            onNavigationTargetReached = onCommentNavigationConsumed,
            onRefresh = { loadReplies(root, 1) },
            onDismiss = {
              replyRoot = null
              replyTarget = null
              replyTargetRoot = null
            },
            bottomClearancePx = composerHeightPx,
            hiddenLinkedVideoCoverItemId = hiddenVideoCoverItemId,
            onLinkedVideoClick = onVideo,
            onLinkedVideoLongClick = onVideoLongClick,
            onLinkedArticleClick = onArticle,
            hiddenLinkedArticleItemId = hiddenLinkedArticleItemId,
            largeCommentText = true,
            backHandlingEnabled = interactionEnabled,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }

    imagePreview?.let { session ->
      CommentImagePreviewOverlay(
        session = session,
        rootBounds = rootBounds,
        onDismiss = ::closeImage,
        modifier = Modifier.fillMaxSize().zIndex(40f),
      )
    }
  }
}

@Composable
private fun ArticleBlockContent(
  block: ArticleBlock,
  viewportHeight: Dp,
  horizontalPadding: Dp,
  emoteCatalog: Map<String, BiliEmote>,
  hiddenVideoCoverItemId: String?,
  interactionEnabled: Boolean,
  onImage: (CommentImage, Rect) -> Unit,
  onProfile: (Long) -> Unit,
  onVideo: (FeedItem, Rect) -> Unit,
  onVideoLongClick: (FeedItem) -> Unit,
  onVideoBoundsChanged: (FeedItem, Rect) -> Unit,
) {
  when (block) {
    is ArticleBlock.Text -> {
      val background =
        if (block.quote) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)
        else Color.Transparent
      val blockEmotes =
        remember(block.emotes, emoteCatalog) {
          emoteCatalog + block.emotes.mapValues { (token, url) -> BiliEmote(token, url) }
        }
      BiliRichText(
        text = block.content,
        emotes = blockEmotes,
        mentions = block.mentions,
        onMentionClick = onProfile,
        modifier =
          Modifier.fillMaxWidth()
            .widthIn(max = 860.dp)
            .padding(horizontal = horizontalPadding)
            .clip(RoundedCornerShape(if (block.quote) 14.dp else 0.dp))
            .background(background)
            .padding(if (block.quote) 16.dp else 0.dp),
        style =
          if (block.heading) MaterialTheme.typography.headlineSmall
          else MaterialTheme.typography.bodyLarge,
        maxLines = Int.MAX_VALUE,
      )
    }
    is ArticleBlock.Image ->
      ArticleBodyImage(block, viewportHeight, horizontalPadding, interactionEnabled, onImage)
    is ArticleBlock.Code -> {
      Surface(
        modifier = Modifier.fillMaxWidth().widthIn(max = 860.dp).padding(horizontal = horizontalPadding),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
      ) {
        Text(
          block.content,
          modifier =
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
          style = MaterialTheme.typography.bodyMedium,
          fontFamily = FontFamily.Monospace,
        )
      }
    }
    is ArticleBlock.Video ->
      ArticleVideoBlock(
        block = block,
        horizontalPadding = horizontalPadding,
        hiddenVideoCoverItemId = hiddenVideoCoverItemId,
        interactionEnabled = interactionEnabled,
        onVideo = onVideo,
        onVideoLongClick = onVideoLongClick,
        onVideoBoundsChanged = onVideoBoundsChanged,
      )
    ArticleBlock.Divider ->
      HorizontalDivider(
        Modifier.fillMaxWidth().widthIn(max = 860.dp).padding(horizontal = horizontalPadding),
        color = MaterialTheme.colorScheme.outlineVariant,
      )
  }
}

@Composable
private fun ArticleBodyImage(
  block: ArticleBlock.Image,
  viewportHeight: Dp,
  horizontalPadding: Dp,
  interactionEnabled: Boolean,
  onImage: (CommentImage, Rect) -> Unit,
) {
  var imageBounds by remember(block.url) { mutableStateOf(Rect.Zero) }
  BoxWithConstraints(
    Modifier.fillMaxWidth().widthIn(max = 940.dp).padding(horizontal = horizontalPadding),
    contentAlignment = Alignment.Center,
  ) {
    val sourceRatio =
      if (block.width > 0 && block.height > 0) block.width.toFloat() / block.height.toFloat()
      else 16f / 9f
    val layout =
      calculateArticleImageLayout(
        sourceRatio = sourceRatio,
        maxWidth = maxWidth.value,
        maxHeight = (viewportHeight / 2f).value,
        dimensionsKnown = block.width > 0 && block.height > 0,
      )
    val displayWidth = layout.width.dp
    val displayHeight = layout.height.dp
    Column(
      modifier = Modifier.width(displayWidth),
      verticalArrangement = Arrangement.spacedBy(6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      AsyncImage(
        model = block.url,
        contentDescription = block.caption.ifBlank { "专栏图片" },
        modifier =
          Modifier.width(displayWidth)
            .height(displayHeight)
            .onGloballyPositioned { imageBounds = it.boundsInRoot() }
            .clip(VideoShapeTokens.Card)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = interactionEnabled) {
              onImage(CommentImage(block.url, block.width, block.height), imageBounds)
            },
        contentScale = if (layout.crop) ContentScale.Crop else ContentScale.Fit,
        alignment = Alignment.Center,
      )
      if (block.caption.isNotBlank()) {
        Text(
          block.caption,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

internal data class ArticleImageLayout(val width: Float, val height: Float, val crop: Boolean)

internal fun calculateArticleImageLayout(
  sourceRatio: Float,
  maxWidth: Float,
  maxHeight: Float,
  dimensionsKnown: Boolean = true,
): ArticleImageLayout {
  val ratio = sourceRatio.takeIf { it.isFinite() && it > 0f }?.coerceIn(.08f, 12f) ?: 16f / 9f
  val safeWidth = maxWidth.coerceAtLeast(1f)
  val safeHeight = maxHeight.coerceAtLeast(1f)
  // Ordinary landscape and portrait images keep their complete frame. Only an exceptionally
  // long source is centre-cropped; otherwise height is constrained solely by scaling it down.
  val crop = dimensionsKnown && ratio < .32f
  val preferredWidth =
    when {
      ratio >= 1.35f -> safeWidth * .84f
      ratio >= .8f -> safeWidth * .74f
      else -> safeWidth * .64f
    }
  return if (crop) {
    ArticleImageLayout(preferredWidth, safeHeight, crop = true)
  } else {
    val width = minOf(preferredWidth, safeHeight * ratio).coerceAtLeast(1f)
    ArticleImageLayout(width, (width / ratio).coerceAtMost(safeHeight), crop = false)
  }
}

@Composable
private fun ArticleVideoBlock(
  block: ArticleBlock.Video,
  horizontalPadding: Dp,
  hiddenVideoCoverItemId: String?,
  interactionEnabled: Boolean,
  onVideo: (FeedItem, Rect) -> Unit,
  onVideoLongClick: (FeedItem) -> Unit,
  onVideoBoundsChanged: (FeedItem, Rect) -> Unit,
) {
  var video by remember(block.bvid) { mutableStateOf<FeedItem?>(null) }
  LaunchedEffect(block.bvid) {
    video =
      withContext(Dispatchers.IO) {
        runCatching { BiliApi.getVideoInfo(block.bvid)?.toCommentVideoFeedItem() }.getOrNull()
      }
  }
  BoxWithConstraints(
    Modifier.fillMaxWidth().widthIn(max = 860.dp).padding(horizontal = horizontalPadding)
  ) {
    val item = video
    if (item == null) {
      Surface(
        modifier = Modifier.fillMaxWidth().height(92.dp),
        shape = VideoShapeTokens.Card,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f),
      ) {}
    } else {
      RecommendationCard(
        item = item,
        onClick = { bounds -> if (interactionEnabled) onVideo(item, bounds) },
        onLongClick = { if (interactionEnabled) onVideoLongClick(item) },
        coverVisible = item.id != hiddenVideoCoverItemId,
        onCoverBoundsChanged = { onVideoBoundsChanged(item, it) },
        cardWidth = maxWidth,
        compactHorizontal = true,
        compactHeight = 92.dp,
      )
    }
  }
}

private fun articleMeta(article: ArticleItem): String {
  val values = mutableListOf<String>()
  if (article.publishedAt > 0L) {
    values +=
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(article.publishedAt))
  }
  if (article.viewCount > 0L) values += "${FeedViewModel.formatCount(article.viewCount)} 点击"
  if (article.likeCount > 0L) values += "${FeedViewModel.formatCount(article.likeCount)} 喜欢"
  if (article.replyCount > 0L) values += "${FeedViewModel.formatCount(article.replyCount)} 评论"
  return values.joinToString(" · ").ifBlank { article.categoryName.ifBlank { "专栏" } }
}

private val ArticleCommentBubbleIcon: ImageVector by lazy {
  ImageVector.Builder(
      name = "ArticleCommentBubble",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    )
    .apply {
      path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
        moveTo(20f, 2f)
        horizontalLineTo(4f)
        curveTo(2.9f, 2f, 2f, 2.9f, 2f, 4f)
        verticalLineTo(22f)
        lineTo(6f, 18f)
        horizontalLineTo(20f)
        curveTo(21.1f, 18f, 22f, 17.1f, 22f, 16f)
        verticalLineTo(4f)
        curveTo(22f, 2.9f, 21.1f, 2f, 20f, 2f)
        close()
        moveTo(20f, 16f)
        horizontalLineTo(5.17f)
        lineTo(4f, 17.17f)
        verticalLineTo(4f)
        horizontalLineTo(20f)
        verticalLineTo(16f)
        close()
      }
    }
    .build()
}
