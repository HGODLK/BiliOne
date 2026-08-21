package dev.openbili.webdemo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyThreadRequestGateTest {
  @Test
  fun `dismiss invalidates an old reply request before the next open`() {
    val gate = ReplyThreadRequestGate()
    val firstOpen = gate.begin()

    gate.invalidate()
    val secondOpen = gate.begin()

    assertFalse(gate.isCurrent(firstOpen))
    assertTrue(gate.isCurrent(secondOpen))
  }
}
