package dev.openbili.webdemo.my

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** “我的”根页控制器层级：根胶囊、左侧栏目、右侧内容。 */
enum class MyControlLevel {
  ROOT,
  SECTIONS,
  CONTENT,
}

/**
 * 只保存跨根层共享的控制器状态；栏目与内容的具体焦点仍由 [MyScreen] 管理。
 */
@Stable
internal class MyControllerState {
  var level by mutableStateOf(MyControlLevel.ROOT)
  var secondLevelRequest by mutableIntStateOf(0)

  fun requestSections() {
    secondLevelRequest += 1
  }
}

internal fun myControlBackTarget(level: MyControlLevel): MyControlLevel =
  when (level) {
    MyControlLevel.CONTENT -> MyControlLevel.SECTIONS
    MyControlLevel.SECTIONS -> MyControlLevel.ROOT
    MyControlLevel.ROOT -> MyControlLevel.ROOT
  }
