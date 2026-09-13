package dev.openbili.webdemo.api

/** 可在本机快速切换的账号摘要；登录凭据不会暴露给界面层。 */
data class SavedAccount(
  val mid: Long,
  val name: String,
  val face: String,
  val vipActive: Boolean,
  val lastUsedAt: Long,
)
