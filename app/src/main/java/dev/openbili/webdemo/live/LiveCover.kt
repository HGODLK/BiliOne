package dev.openbili.webdemo.live

internal fun LiveSearchRoom.currentDisplayCoverUrl(): String =
  keyframeUrl?.takeIf(String::isNotBlank) ?: coverUrl.orEmpty()
