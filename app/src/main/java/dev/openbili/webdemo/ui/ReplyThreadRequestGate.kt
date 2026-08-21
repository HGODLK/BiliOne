package dev.openbili.webdemo.ui

/**
 * 为当前楼中楼请求分配代次。关闭、切视频或恢复父页都会使旧请求失效，避免迟到结果
 * 修改新一轮楼中楼的列表与 loading 状态。
 */
internal class ReplyThreadRequestGate {
  private var generation = 0L

  fun begin(): Long = ++generation

  fun invalidate() {
    generation += 1L
  }

  fun isCurrent(requestGeneration: Long): Boolean = requestGeneration == generation
}
