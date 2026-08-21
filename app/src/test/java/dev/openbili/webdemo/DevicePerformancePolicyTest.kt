package dev.openbili.webdemo

import org.junit.Assert.assertEquals
import org.junit.Test

class DevicePerformancePolicyTest {

  @Test
  fun `constrained image sizes preserve aspect ratio within legacy budget`() {
    assertEquals(
      ImageRequestSize(width = 960, height = 540),
      DevicePerformancePolicy.constrainedImageRequestSize(1920, 1080, constrained = true),
    )
    assertEquals(
      ImageRequestSize(width = 540, height = 540),
      DevicePerformancePolicy.constrainedImageRequestSize(1200, 1200, constrained = true),
    )
  }

  @Test
  fun `unconstrained image sizes stay unchanged`() {
    assertEquals(
      ImageRequestSize(width = 1600, height = 900),
      DevicePerformancePolicy.constrainedImageRequestSize(1600, 900, constrained = false),
    )
  }
}
