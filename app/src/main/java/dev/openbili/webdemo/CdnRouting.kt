package dev.openbili.webdemo

import android.content.Context
import dev.openbili.webdemo.settings.CdnRegionPreference
import java.net.URI

/** 从设置读取当前 CDN 地区偏好，供各个播放 ViewModel 共享。 */
internal fun readCdnRegionPreference(context: Context): CdnRegionPreference {
  val prefs = context.applicationContext.getSharedPreferences("app_settings", 0)
  return runCatching {
      CdnRegionPreference.valueOf(
        prefs.getString("cdn_region_preference", null).orEmpty()
      )
    }
    .getOrDefault(CdnRegionPreference.MAINLAND_CHINA)
}

internal fun configuredCdnHost(context: Context): String =
  readCdnRegionPreference(context).preferredHost

internal data class PrioritizedCdnUrls(val primary: String, val backups: List<String>)

/** API 主地址通常是上游按当前出口运营商调度的边缘节点。固定地区主线仍优先，换线时再优先同运营商节点。 */
internal fun prioritizeCdnUrls(
  primary: String,
  backups: List<String>,
  preferredHost: String,
): PrioritizedCdnUrls {
  val candidates = (listOf(primary) + backups).filter(String::isNotBlank).distinct()
  if (candidates.isEmpty()) return PrioritizedCdnUrls(primary, emptyList())

  val selected =
    preferredHost.takeIf(String::isNotBlank)?.let { expected ->
      candidates.firstOrNull { url -> cdnHost(url).equals(expected, ignoreCase = true) }
    } ?: candidates.first()
  val currentCarrier = candidates.firstNotNullOfOrNull(::cdnCarrier)
  val orderedBackups =
    candidates
      .filterNot { it == selected }
      .withIndex()
      .sortedWith(
        compareBy<IndexedValue<String>> {
            if (currentCarrier != null && cdnCarrier(it.value) == currentCarrier) 0 else 1
          }
          .thenBy { it.index }
      )
      .map(IndexedValue<String>::value)
  return PrioritizedCdnUrls(selected, orderedBackups)
}

private enum class CdnCarrier {
  CHINA_TELECOM,
  CHINA_UNICOM,
  CHINA_MOBILE,
  CHINA_BROADNET,
}

/** 只匹配主机名中的独立运营商标记，避免把地区或节点编号误判成线路类型。 */
private fun cdnCarrier(url: String): CdnCarrier? {
  val tokens = cdnHost(url).split('.', '-').filter(String::isNotBlank).toSet()
  return when {
    tokens.any { it in TELECOM_TOKENS } -> CdnCarrier.CHINA_TELECOM
    tokens.any { it in UNICOM_TOKENS } -> CdnCarrier.CHINA_UNICOM
    tokens.any { it in MOBILE_TOKENS } -> CdnCarrier.CHINA_MOBILE
    tokens.any { it in BROADNET_TOKENS } -> CdnCarrier.CHINA_BROADNET
    else -> null
  }
}

private fun cdnHost(url: String): String =
  runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")

private val TELECOM_TOKENS = setOf("ct", "ctcc", "dx", "telecom")
private val UNICOM_TOKENS = setOf("cu", "cucc", "lt", "unicom")
private val MOBILE_TOKENS = setOf("cm", "cmcc", "yd", "mobile")
private val BROADNET_TOKENS = setOf("cbn", "cbtn", "broadnet")
