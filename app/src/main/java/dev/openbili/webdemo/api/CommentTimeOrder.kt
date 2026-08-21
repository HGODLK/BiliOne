package dev.openbili.webdemo.api

/**
 * 评论「按时间排序」分页辅助：承载评论分页大小常量与按时间排序/翻页相关的工具函数。
 */

/** 评论列表每页条数。 */
internal const val COMMENT_PAGE_SIZE = 20

/** 按时间排序的评论首页页码（恒为 1）。 */
internal fun commentTimeStartPage(): Int = 1

/** 返回下一页页码。 */
internal fun commentTimeNextPage(currentPage: Int): Int = currentPage + 1

/**
 * 判断按时间排序的评论是否还有下一页。
 *
 * @param currentPage 当前页码。
 * @param totalCount 评论总数。
 * @param pageSize 每页条数，默认 [COMMENT_PAGE_SIZE]。
 * @return 已加载条数小于总数时返回 true。
 */
internal fun commentTimeHasMore(
  currentPage: Int,
  totalCount: Long,
  pageSize: Int = COMMENT_PAGE_SIZE,
): Boolean = currentPage.toLong() * pageSize < totalCount

/**
 * 按发布时间（ctime）降序排列评论。
 *
 * @param comments 原始评论列表。
 * @return 按时间从新到旧排序后的评论列表。
 */
internal fun orderCommentsByTime(comments: List<CommentItem>): List<CommentItem> =
  comments.sortedByDescending {
    it.ctime
  }
