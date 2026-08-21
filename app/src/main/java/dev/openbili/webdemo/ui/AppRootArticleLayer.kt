package dev.openbili.webdemo.ui

/**
 * 文章层 UI：从根 Box 内渲染专栏页，并处理专栏与视频/资料的层叠可见性。
 */

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.UserInfo
import dev.openbili.webdemo.article.ArticleOrigin
import dev.openbili.webdemo.article.ArticleScreen
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.video.CommentProfileAnchor

/** 文章层组合体（位于根 Box 内、直播层之后）。 */
@Composable
internal fun AppRootArticleLayer(
  ctx: AppRootArticleContext,
  showVideo: Boolean,
  authUserInfo: UserInfo,
  profileStackState: MutableState<List<ProfileStackEntry>>,
  hiddenArticleVideoCoverItemIdState: MutableState<String?>,
  articleVideoBounds: SnapshotStateMap<String, Rect>,
  videoState: AppRootVideoState,
  openAvatarProfileRef: (Long, Rect, String?, String?) -> Unit,
  openProfileRef: (Long) -> Unit,
  openArticleCommentProfileRef: (Long, CommentItem, CommentProfileAnchor) -> Unit,
  returnDirectlyHomeRef: () -> Unit,
  showVideoPreviewRef: (FeedItem) -> Unit,
  loadMentionSuggestionsRef: (String) -> Unit,
) {
  var profileStack by profileStackState
  var hiddenArticleVideoCoverItemId by hiddenArticleVideoCoverItemIdState
  var emotes by videoState::emotes
  var emotePackages by videoState::emotePackages
  var mentionSuggestions by videoState::mentionSuggestions
  var mentionSuggestionsLoading by videoState::mentionSuggestionsLoading
  fun openAvatarProfile(mid: Long, bounds: Rect, face: String?, name: String?) =
    openAvatarProfileRef(mid, bounds, face, name)
  fun openProfile(mid: Long) = openProfileRef(mid)
  fun openArticleCommentProfile(mid: Long, comment: CommentItem, anchor: CommentProfileAnchor) =
    openArticleCommentProfileRef(mid, comment, anchor)
  fun returnDirectlyHome() = returnDirectlyHomeRef()
  fun showVideoPreview(item: FeedItem) = showVideoPreviewRef(item)
  fun loadMentionSuggestions(query: String) = loadMentionSuggestionsRef(query)

  ctx.articleStack.forEachIndexed { frameIndex, frame ->
    key(frame.entryId) {
      val isTopFrame = frameIndex == ctx.articleStack.lastIndex
      val profileCoversArticle = profileStack.isNotEmpty() && !ctx.profileLayerSuppressed
      val returningVideoToArticle =
        ctx.videoStack.firstOrNull()?.parentPage == PageOrigin.Article &&
          ctx.transitionPhase is TransitionPhase.ToFeed
      // 从位于文章之上的资料页打开的视频仍是顶层页面：若这里保持文章不透明，
      // 播放器有声音但画面被压在下面。保留在视频之下的文章帧继续挂载，但不得遮住
      // 该视频的评论卡；只有开在视频上方的新文章段才可见。
      val articleLayerVisible =
        shouldShowArticleFrame(
          showVideo = showVideo,
          returningVideoToArticle = returningVideoToArticle,
          retainedArticleDepth = ctx.articleSuspendedVideo?.retainedArticleDepth,
          frameIndex = frameIndex,
        )
      Box(
        Modifier.fillMaxSize().graphicsLayer {
          alpha =
            if (!articleLayerVisible) 0f
            else if (isTopFrame && ctx.articleRestoringParentEntryId == frame.entryId) 1f
            else if (isTopFrame) ctx.articlePageAlpha.value.coerceIn(0f, 1f) else 1f
        }
      ) {
        val retainedDetail =
          if (isTopFrame) ctx.articleDetail else ctx.articleDetailCache[frame.article.id]
        ArticleScreen(
          article = frame.article,
          detail = retainedDetail,
          loading = if (isTopFrame) ctx.articleLoading else false,
          error = if (isTopFrame) ctx.articleError else null,
          contentLoadEnabled = if (isTopFrame) ctx.articleContentReady else retainedDetail != null,
          currentAccountMid = authUserInfo.mid,
          showCommentEmotes = ctx.settings.showCommentEmotes,
          showCommentLocation = ctx.settings.showCommentLocation,
          sharedEmotes = emotes,
          sharedEmotePackages = emotePackages,
          mentionSuggestions = mentionSuggestions,
          mentionSuggestionsLoading = mentionSuggestionsLoading,
          hiddenVideoCoverItemId = hiddenArticleVideoCoverItemId,
          hiddenLinkedArticleItemId = ctx.hiddenArticleCommentArticleItemId,
          commentNavigationTarget = ctx.pendingArticleCommentTarget.takeIf { isTopFrame },
          onBack = ctx::startExitArticle,
          onHome = ::returnDirectlyHome,
          onRetry = { ctx.loadArticleDetail(frame.article) },
          onMentionQuery = ::loadMentionSuggestions,
          onAuthorProfile = { mid, face, name, bounds ->
            openAvatarProfile(mid, bounds, face, name)
          },
          onProfile = ::openProfile,
          onCommentProfile = ::openArticleCommentProfile,
          onCommentNavigationConsumed = { ctx.pendingArticleCommentTarget = null },
          onVideo = { video, bounds ->
            if (bounds.hasUsableSize()) articleVideoBounds[video.id] = bounds
            ctx.startEnterVideo(video, bounds.takeIf(Rect::hasUsableSize), VideoOrigin.ARTICLE)
          },
          onVideoLongClick = { showVideoPreview(it) },
          onVideoBoundsChanged = { video, bounds ->
            if (bounds.hasUsableSize()) articleVideoBounds[video.id] = bounds
          },
          onArticle = { nestedArticle, bounds ->
            ctx.startEnterArticle(
              nestedArticle,
              bounds.takeIf(Rect::hasUsableSize),
              ArticleOrigin.ARTICLE,
            )
          },
          onHeroBoundsChanged = { bounds ->
            if (isTopFrame && bounds.hasUsableSize()) ctx.articleHeroBounds = bounds
          },
          heroVisible =
            !isTopFrame ||
              ctx.articleTransitionSession == null ||
              ctx.articleTransitionSession?.article?.stableId != frame.article.stableId,
          interactionEnabled =
            isTopFrame &&
              articleLayerVisible &&
              !profileCoversArticle &&
              ctx.articleTransitionSession == null,
          backEnabled =
            isTopFrame &&
              articleLayerVisible &&
              !profileCoversArticle &&
              ctx.articleTransitionSession == null,
        )
        if (isTopFrame && ctx.articleTransitionSession != null) {
          Box(
            Modifier.fillMaxSize().pointerInput(frame.entryId) {
              awaitPointerEventScope {
                while (true) awaitPointerEvent().changes.forEach { it.consume() }
              }
            }
          )
        }
      }
    }
  }
}
