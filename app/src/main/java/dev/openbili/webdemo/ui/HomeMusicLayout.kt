package dev.openbili.webdemo.ui

/**
 * 音乐页右侧氛围动效的安全横向区域。
 *
 * 频谱和时钟都使用这个区域的中心，区域左边界优先让开实际播放器，右边界则让开
 * 窗口边缘。这样布局比例变化时会缩窄动效，而不是让后续绘制层把它盖住。
 */
internal data class MusicSpectrumRegion(
  val leftPx: Float,
  val rightPx: Float,
) {
  val widthPx: Float
    get() = (rightPx - leftPx).coerceAtLeast(1f)

  val centerPx: Float
    get() = (leftPx + rightPx) / 2f
}

internal fun musicSpectrumRegion(
  windowWidthPx: Float,
  ambientLeftPx: Float,
  ambientWidthPx: Float,
  playerRightPx: Float,
  edgeClearancePx: Float,
  desiredWidthPx: Float,
): MusicSpectrumRegion {
  val safeWindowWidth = windowWidthPx.coerceAtLeast(1f)
  val ambientWidth = ambientWidthPx.takeIf { it > 0f } ?: safeWindowWidth
  val edge = edgeClearancePx.coerceAtLeast(0f)
  val right = (ambientWidth - edge).coerceAtLeast(1f)
  val playerBoundary =
    if (playerRightPx.isFinite() && playerRightPx > ambientLeftPx) {
      (playerRightPx - ambientLeftPx + edge).coerceIn(0f, right - 1f)
    } else {
      (safeWindowWidth * .62f).coerceIn(0f, right - 1f)
    }
  val availableWidth = (right - playerBoundary).coerceAtLeast(1f)
  val regionWidth = desiredWidthPx.coerceAtLeast(1f).coerceAtMost(availableWidth)
  return MusicSpectrumRegion(
    leftPx = right - regionWidth,
    rightPx = right,
  )
}
