package dev.openbili.webdemo.api

internal const val COMMENT_PAGE_SIZE = 20

internal fun commentTimeStartPage(): Int = 1

internal fun commentTimeNextPage(currentPage: Int): Int = currentPage + 1

internal fun commentTimeHasMore(
  currentPage: Int,
  totalCount: Long,
  pageSize: Int = COMMENT_PAGE_SIZE,
): Boolean = currentPage.toLong() * pageSize < totalCount

internal fun orderCommentsByTime(comments: List<CommentItem>): List<CommentItem> =
  comments.sortedByDescending {
    it.ctime
  }
