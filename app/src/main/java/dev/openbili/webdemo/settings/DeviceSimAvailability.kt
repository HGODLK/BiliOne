package dev.openbili.webdemo.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager

internal enum class SimAvailability {
  PRESENT,
  ABSENT,
  UNKNOWN,
}

internal fun detectSimAvailability(context: Context): SimAvailability {
  val applicationContext = context.applicationContext
  if (!applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
    return SimAvailability.ABSENT
  }

  val telephonyManager =
    applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
      ?: return SimAvailability.UNKNOWN
  return runCatching {
      val states =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          val slotCount =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
              telephonyManager.activeModemCount
            } else {
              @Suppress("DEPRECATION") telephonyManager.phoneCount
            }
          if (slotCount <= 0) emptyList()
          else (0 until slotCount).map(telephonyManager::getSimState)
        } else {
          @Suppress("DEPRECATION") listOf(telephonyManager.simState)
        }
      classifySimAvailability(hasTelephony = true, simStates = states)
    }
    .getOrDefault(SimAvailability.UNKNOWN)
}

internal fun classifySimAvailability(
  hasTelephony: Boolean,
  simStates: List<Int>,
): SimAvailability {
  if (!hasTelephony) return SimAvailability.ABSENT
  if (simStates.isEmpty()) return SimAvailability.UNKNOWN
  if (
    simStates.any {
      it != TelephonyManager.SIM_STATE_ABSENT && it != TelephonyManager.SIM_STATE_UNKNOWN
    }
  ) {
    return SimAvailability.PRESENT
  }
  return if (simStates.all { it == TelephonyManager.SIM_STATE_ABSENT }) {
    SimAvailability.ABSENT
  } else {
    SimAvailability.UNKNOWN
  }
}
