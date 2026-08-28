package dev.openbili.webdemo.ui

/** 视频评论楼中楼的可恢复界面状态。 */
data class ReplyThreadUiState(
  val openedRootRpid: Long? = null,
  val firstVisibleItemIndex: Int = 0,
  val firstVisibleItemScrollOffset: Int = 0,
  val restoreExpanded: Boolean = false,
)

/** 生成进入子视频前的保留状态；返回时楼中楼应直接保持展开。 */
internal fun retainedReplyThreadUiState(
  state: ReplyThreadUiState,
  openedRootRpid: Long?,
): ReplyThreadUiState =
  if (openedRootRpid == null) {
    ReplyThreadUiState()
  } else {
    state.copy(openedRootRpid = openedRootRpid, restoreExpanded = true)
  }

/** 仅接收当前楼中楼上报的合法滚动位置，防止旧列表回调覆盖新会话。 */
internal fun ReplyThreadUiState.withScrollPosition(
  rootRpid: Long,
  firstVisibleItemIndex: Int,
  firstVisibleItemScrollOffset: Int,
): ReplyThreadUiState =
  if (openedRootRpid != rootRpid) {
    this
  } else {
    copy(
      firstVisibleItemIndex = firstVisibleItemIndex.coerceAtLeast(0),
      firstVisibleItemScrollOffset = firstVisibleItemScrollOffset.coerceAtLeast(0),
    )
  }
