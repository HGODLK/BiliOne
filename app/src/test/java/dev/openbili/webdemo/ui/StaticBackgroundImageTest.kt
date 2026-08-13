package dev.openbili.webdemo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticBackgroundImageTest {
  @Test
  fun incomingLayerKeepsTheSameKeyWhenPromoted() {
    val loading = backgroundImageLayers(displayedModel = "old", incomingModel = "new")
    assertEquals(listOf("old", "new"), loading.map { it.model })
    assertFalse(loading.first().incoming)
    assertTrue(loading.last().incoming)

    val promoted = backgroundImageLayers(displayedModel = "new", incomingModel = "new")
    assertEquals(1, promoted.size)
    assertEquals("new", promoted.single().model)
    assertFalse(promoted.single().incoming)
  }

  @Test
  fun firstBackgroundStartsAsTheIncomingLayer() {
    val layers = backgroundImageLayers(displayedModel = null, incomingModel = "first")
    assertEquals(1, layers.size)
    assertEquals("first", layers.single().model)
    assertTrue(layers.single().incoming)
  }
}
