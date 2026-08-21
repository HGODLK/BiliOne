package dev.openbili.webdemo.my

/** 设置面板：主题/画质/弹幕/手势/存储与关于等全部设置项。 */
import android.content.Intent
import android.view.KeyEvent as AndroidKeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import dev.openbili.webdemo.BuildConfig
import dev.openbili.webdemo.api.FavoriteFolder
import dev.openbili.webdemo.settings.AdvancedAudioPriority
import dev.openbili.webdemo.settings.AppCacheManager
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.settings.CdnRegionPreference
import dev.openbili.webdemo.settings.DeviceMediaCapabilities
import dev.openbili.webdemo.settings.PreferredResolutionMode
import dev.openbili.webdemo.settings.SimAvailability
import dev.openbili.webdemo.settings.ThemeAccent
import dev.openbili.webdemo.settings.ThemeMode
import dev.openbili.webdemo.settings.canSelectPreferredResolution
import dev.openbili.webdemo.settings.detectSimAvailability
import dev.openbili.webdemo.ui.HomeHubTab
import dev.openbili.webdemo.ui.LocalControlMode
import dev.openbili.webdemo.ui.NavigationCardBottomClearance
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class SettingsOption<T>(
  val value: T,
  val title: String,
  val description: String,
)

/** 设置面板组合体。 */
@Composable
internal fun SettingsPane(
  settings: AppSettings,
  favoriteFolders: List<FavoriteFolder>,
  favoriteFoldersLoading: Boolean,
  vipActive: Boolean,
  profileIpAuthorized: Boolean,
  onAuthorizeProfileIp: () -> Unit,
  onChange: ((AppSettings) -> AppSettings) -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val scope = rememberCoroutineScope()
  val mediaCapabilities =
    remember(context.applicationContext) {
      DeviceMediaCapabilities.detect(context.applicationContext)
    }
  var cacheSizeBytes by remember { mutableStateOf<Long?>(null) }
  var clearingCache by remember { mutableStateOf(false) }
  var showResetDialog by remember { mutableStateOf(false) }
  var showMusicFolderPicker by remember { mutableStateOf(false) }
  var simAvailability by
    remember(context.applicationContext) {
      mutableStateOf(detectSimAvailability(context))
    }
  val homeBackgroundPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        runCatching {
          context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        }
        onChange { it.copy(homeBackgroundUri = uri.toString()) }
      }
    }
  val videoBackgroundPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        runCatching {
          context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        }
        onChange { it.copy(videoBackgroundUri = uri.toString()) }
      }
    }
  val startupMaskPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        runCatching {
          context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        }
        onChange { it.copy(startupMaskUri = uri.toString()) }
      }
    }

  LaunchedEffect(Unit) {
    cacheSizeBytes = withContext(Dispatchers.IO) { AppCacheManager.sizeBytes(context) }
  }
  DisposableEffect(lifecycleOwner, context.applicationContext) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        simAvailability = detectSimAvailability(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  if (showResetDialog) {
    AlertDialog(
      onDismissRequest = { showResetDialog = false },
      title = { Text("恢复默认设置？") },
      text = { Text("主题、播放、手势和弹幕选项都会恢复默认值，缓存与登录状态不会清除。") },
      confirmButton = {
        TextButton(
          onClick = {
            showResetDialog = false
            onChange { AppSettings() }
          }
        ) {
          Text("恢复")
        }
      },
      dismissButton = {
        TextButton(onClick = { showResetDialog = false }) { Text("取消") }
      },
    )
  }
  if (showMusicFolderPicker) {
    MusicFavoriteFolderPicker(
      folders = favoriteFolders,
      selectedFolderId = settings.musicFavoriteFolderId,
      loading = favoriteFoldersLoading,
      onDismiss = { showMusicFolderPicker = false },
      onSelected = { folderId ->
        showMusicFolderPicker = false
        onChange {
          it.copy(
            musicFavoriteFolderId = folderId,
            musicFavoriteFolderConfigured = true,
          )
        }
      },
    )
  }

  fun selectResolution(mode: PreferredResolutionMode, cellular: Boolean) {
    if (canSelectPreferredResolution(mode, vipActive)) {
      onChange { value ->
        if (cellular) value.copy(cellularPreferredResolutionMode = mode)
        else value.copy(preferredResolutionMode = mode)
      }
    } else {
      Toast.makeText(context, "只有大会员可以选择~", Toast.LENGTH_SHORT).show()
    }
  }

  fun selectMusicResolution(mode: PreferredResolutionMode) {
    if (mode != PreferredResolutionMode.EXTREME && canSelectPreferredResolution(mode, vipActive)) {
      onChange { value -> value.copy(musicPreferredResolutionMode = mode) }
    } else {
      Toast.makeText(context, "非大会员最高可选择 1080P", Toast.LENGTH_SHORT).show()
    }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = NavigationCardBottomClearance),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    item { SettingsTitle("播放与网络") }
    item {
      PreferredResolutionSetting(
        title = "Wi-Fi 优先分辨率",
        selected = settings.preferredResolutionMode,
        onSelected = { selectResolution(it, false) },
      )
    }
    if (simAvailability != SimAvailability.ABSENT) {
      item {
        PreferredResolutionSetting(
          title = "移动网络优先分辨率",
          selected = settings.cellularPreferredResolutionMode,
          onSelected = { selectResolution(it, true) },
        )
      }
    }
    item {
      SettingsSwitch("播放页阻止休眠", "视频或番剧播放页可见时保持屏幕常亮", settings.keepScreenOn) {
        onChange { value -> value.copy(keepScreenOn = it) }
      }
    }
    item {
      SettingsSwitch(
        "离开应用时暂停",
        "关闭后可在后台继续听视频",
        settings.pauseWhenLeavingApp,
      ) {
        onChange { value -> value.copy(pauseWhenLeavingApp = it) }
      }
    }
    item {
      SettingsSwitch(
        "自动播放下一集",
        "视频结束后按设定倒计时继续播放，也可以手动立即播放",
        settings.autoPlayNext,
      ) {
        onChange { value -> value.copy(autoPlayNext = it) }
      }
    }
    if (settings.autoPlayNext) {
      item {
        SettingsSlider(
          title = "连播倒计时",
          valueText = "${settings.autoNextCountdownSeconds} 秒",
          value = settings.autoNextCountdownSeconds.toFloat(),
          range = 3f..10f,
          steps = 6,
        ) { next ->
          onChange { it.copy(autoNextCountdownSeconds = next.roundToInt()) }
        }
      }
    }
    item {
      SettingsSwitch(
        "默认开启字幕",
        "进入有字幕的新视频时自动选择首个字幕轨道，播放中仍可临时关闭",
        settings.defaultShowSubtitles,
      ) {
        onChange { value -> value.copy(defaultShowSubtitles = it) }
      }
    }
    item {
      SettingsSlider(
        title = "播放器控件隐藏时间",
        valueText = "${settings.controlsTimeoutSeconds} 秒",
        value = settings.controlsTimeoutSeconds.toFloat(),
        range = 2f..5f,
        steps = 2,
      ) { next ->
        onChange { it.copy(controlsTimeoutSeconds = next.roundToInt()) }
      }
    }
    item {
      SettingsSwitch(
        "高级音质",
        "自动启用当前视频可用的 Dolby 或 HiRes 音轨",
        settings.advancedAudioEnabled,
      ) {
        onChange { value -> value.copy(advancedAudioEnabled = it) }
      }
    }
    if (settings.advancedAudioEnabled) {
      item {
        AdvancedAudioPrioritySetting(settings.advancedAudioPriority) { priority ->
          onChange { value -> value.copy(advancedAudioPriority = priority) }
        }
      }
    }
    if (!mediaCapabilities.supportsDolbyVision) {
      item {
        SettingsSwitch(
          "解锁杜比视界",
          "当前设备未报告支持，仅建议用于兼容性测试",
          settings.unlockDolbyVision,
        ) {
          onChange { value -> value.copy(unlockDolbyVision = it) }
        }
      }
    }
    if (!mediaCapabilities.supportsDolbyAtmos) {
      item {
        SettingsSwitch(
          "解锁杜比全景声",
          "当前设备未报告支持，仅建议用于兼容性测试",
          settings.unlockDolbyAtmos,
        ) {
          onChange { value -> value.copy(unlockDolbyAtmos = it) }
        }
      }
    }
    if (!mediaCapabilities.supportsHiRes) {
      item {
        SettingsSwitch(
          "解锁 Hi-Res",
          "当前设备未报告支持 FLAC 高解析音频，仅建议用于兼容性测试",
          settings.unlockHiRes,
        ) {
          onChange { value -> value.copy(unlockHiRes = it) }
        }
      }
    }

    item { SettingsTitle("音乐播放器") }
    item {
      val selectedFolder = favoriteFolders.firstOrNull { it.id == settings.musicFavoriteFolderId }
      SettingsAction(
        title = "音乐播放器收藏夹",
        subtitle =
          when {
            !settings.musicFavoriteFolderConfigured -> "首次进入音乐页时选择收藏夹"
            settings.musicFavoriteFolderId <= 0L -> "按名称自动查找“音乐”收藏夹"
            selectedFolder != null ->
              "当前使用“${selectedFolder.title}” · ${selectedFolder.mediaCount} 个内容"
            favoriteFoldersLoading -> "正在读取个人收藏夹…"
            else -> "已选择的收藏夹不可用，请重新选择"
          },
        action = if (favoriteFoldersLoading) "加载中" else "选择",
        enabled = !favoriteFoldersLoading,
        onClick = { showMusicFolderPicker = true },
      )
    }

    item {
      PreferredResolutionSetting(
        title = "音乐播放器视频规格（非大会员最高 1080P）",
        selected =
          if (
            !vipActive &&
              settings.musicPreferredResolutionMode == PreferredResolutionMode.ULTRA_HIGH
          ) {
            PreferredResolutionMode.HIGH
          } else {
            settings.musicPreferredResolutionMode
          },
        options = PreferredResolutionMode.entries.filter { it != PreferredResolutionMode.EXTREME },
        onSelected = ::selectMusicResolution,
      )
    }

    item { SettingsTitle("播放器手势") }
    item {
      SettingsSwitch("左侧滑动调节亮度", "只调整当前播放窗口", settings.brightnessGesture) {
        onChange { value -> value.copy(brightnessGesture = it) }
      }
    }
    item {
      SettingsSwitch("右侧滑动调节音量", "调整系统媒体音量", settings.volumeGesture) {
        onChange { value -> value.copy(volumeGesture = it) }
      }
    }
    item {
      SettingsSwitch("横向滑动调整进度", "全屏时左右边缘保留系统手势安全区", settings.horizontalSeekGesture) {
        onChange { value -> value.copy(horizontalSeekGesture = it) }
      }
    }
    item {
      SettingsSwitch(
        "双指捏合切换全屏",
        "张开进入全屏，捏合退出全屏",
        settings.twoFingerFullscreenGesture,
      ) {
        onChange { value -> value.copy(twoFingerFullscreenGesture = it) }
      }
    }
    item {
      SettingsSwitch(
        "双指双击快进/快退",
        "双指双击播放器左侧快退 5 秒，右侧快进 5 秒",
        settings.twoFingerSeekGesture,
      ) {
        onChange { value -> value.copy(twoFingerSeekGesture = it) }
      }
    }

    item { SettingsTitle("搜索") }
    item {
      SettingsSwitch(
        "返回保留上次搜索内容",
        "关闭时退出搜索页会立即清空首页搜索框",
        settings.retainLastSearchQuery,
      ) {
        onChange { value -> value.copy(retainLastSearchQuery = it) }
      }
    }

    item { SettingsTitle("外观") }
    item {
      SettingsSlider(
        title = "首页推荐列数",
        valueText = "${settings.homeGridColumns} 列",
        value = settings.homeGridColumns.toFloat(),
        range = 3f..6f,
        steps = 2,
      ) { next ->
        onChange { it.copy(homeGridColumns = next.roundToInt().coerceIn(3, 6)) }
      }
    }
    item {
      SettingsSwitch(
        "播放页显示设备信息",
        "控制普通视频、番剧、影视和直播页右上角的时间、网络与电量",
        settings.showPlaybackDeviceStatus,
      ) {
        onChange { value -> value.copy(showPlaybackDeviceStatus = it) }
      }
    }
    item {
      BackgroundImageSetting(
        title = "自定义启动遮罩图",
        selected = settings.startupMaskUri.isNotBlank(),
        selectedDescription = "已选择；下次启动全程使用并裁剪覆盖全屏",
        defaultDescription = "未设置，使用当前默认遮罩图",
        onPick = { startupMaskPicker.launch(arrayOf("image/*")) },
        onClear = { onChange { it.copy(startupMaskUri = "") } },
      )
    }
    item { SettingsTitle("页面背景") }
    item {
      BackgroundImageSetting(
        title = "首页背景图",
        selected = settings.homeBackgroundUri.isNotBlank(),
        onPick = { homeBackgroundPicker.launch(arrayOf("image/*")) },
        onClear = { onChange { it.copy(homeBackgroundUri = "") } },
      )
    }
    if (settings.homeBackgroundUri.isNotBlank()) {
      item {
        SettingsSwitch(
          "模糊首页背景图",
          "预先生成静态模糊图；开启后背景透明度不生效",
          settings.homeBackgroundBlur,
        ) { checked ->
          onChange { it.copy(homeBackgroundBlur = checked) }
        }
      }
      item {
        SettingsSwitch(
          "用于音乐播放页",
          "音乐页使用首页背景图，可单独设置是否模糊",
          settings.useHomeBackgroundForMusic,
        ) { checked ->
          onChange { it.copy(useHomeBackgroundForMusic = checked) }
        }
      }
      if (settings.useHomeBackgroundForMusic) {
        item {
          SettingsSwitch(
            "模糊音乐播放页背景图",
            "开启时使用静态模糊版本；关闭后直接使用自定义原图",
            settings.homeBackgroundMusicBlur,
          ) { checked ->
            onChange { it.copy(homeBackgroundMusicBlur = checked) }
          }
        }
      }
      if (!settings.homeBackgroundBlur) {
        item {
          SettingsSlider(
            title = "首页背景透明度",
            valueText = "${(settings.homeBackgroundTransparency * 100).roundToInt()}%",
            value = settings.homeBackgroundTransparency,
            range = 0f..1f,
            steps = 9,
          ) { next ->
            onChange { it.copy(homeBackgroundTransparency = next) }
          }
        }
      }
    }
    item {
      BackgroundImageSetting(
        title = "播放页背景图",
        selected = settings.videoBackgroundUri.isNotBlank(),
        onPick = { videoBackgroundPicker.launch(arrayOf("image/*")) },
        onClear = { onChange { it.copy(videoBackgroundUri = "") } },
      )
    }
    item {
      SettingsSwitch(
        "使用当前视频封面作为播放页背景",
        "默认开启；番剧和分 P 会跟随当前播放集。设置自定义播放页背景图后不生效",
        settings.useVideoCoverBackground,
      ) { checked ->
        onChange { it.copy(useVideoCoverBackground = checked) }
      }
    }
    if (settings.videoBackgroundUri.isNotBlank()) {
      item {
        SettingsSwitch(
          "模糊播放页背景图",
          "预先生成静态模糊图；开启后背景透明度不生效",
          settings.videoBackgroundBlur,
        ) { checked ->
          onChange { it.copy(videoBackgroundBlur = checked) }
        }
      }
      if (!settings.videoBackgroundBlur) {
        item {
          SettingsSlider(
            title = "播放页背景透明度",
            valueText = "${(settings.videoBackgroundTransparency * 100).roundToInt()}%",
            value = settings.videoBackgroundTransparency,
            range = 0f..1f,
            steps = 9,
          ) { next ->
            onChange { it.copy(videoBackgroundTransparency = next) }
          }
        }
      }
    }
    item {
      SettingsRadioGroup(
        title = "主题",
        selected = settings.themeMode,
        options =
          ThemeMode.entries.map { mode ->
            SettingsOption(mode, mode.title, mode.description)
          },
      ) { mode ->
        onChange { it.copy(themeMode = mode) }
      }
    }
    item {
      SettingsRadioGroup(
        title = "主题色",
        selected = settings.themeAccent,
        options =
          ThemeAccent.entries.map { accent ->
            SettingsOption(accent, accent.title, accent.description)
          },
      ) { accent ->
        onChange { it.copy(themeAccent = accent) }
      }
    }
    item {
      SettingsRadioGroup(
        title = "首页默认页面",
        selected = settings.homeDefaultTab,
        options =
          HomeHubTab.entries.map { tab ->
            SettingsOption(tab.ordinal, tab.label, "开屏后首页停在该信息流")
          },
      ) { index ->
        onChange { it.copy(homeDefaultTab = index) }
      }
    }
    item {
      SettingsSwitch(
        "在控制器模式中启用触屏播放页",
        "播放页保留触屏布局、评论区与推荐轨并启用方向键焦点链；体验可能不佳",
        settings.controllerTouchPlaybackPage,
      ) {
        onChange { value -> value.copy(controllerTouchPlaybackPage = it) }
      }
    }
    item {
      SettingsSwitch(
        "始终启用控制器播放页面",
        "无论是否连接控制器都使用专用播放页；连接控制器时页面保持常亮",
        settings.alwaysControllerPlaybackPage,
      ) {
        onChange { value -> value.copy(alwaysControllerPlaybackPage = it) }
      }
    }
    item {
      SettingsSwitch(
        "减少动态效果",
        "缩短或关闭页面切换、共享元素和播放器动效",
        settings.reduceMotion,
      ) {
        onChange { value -> value.copy(reduceMotion = it) }
      }
    }
    item {
      SettingsSwitch(
        "实时毛玻璃",
        "关闭后改用不透明主题表面，降低合成与模糊开销",
        settings.glassEffects,
      ) {
        onChange { value -> value.copy(glassEffects = it) }
      }
    }
    item {
      SettingsSwitch(
        "关闭彩色卡片",
        "默认关闭视频卡片、评论卡片和头像卡片的双色渐变；关闭此开关后恢复",
        settings.disableColorfulCards,
      ) {
        onChange { value -> value.copy(disableColorfulCards = it) }
      }
    }
    item {
      SettingsSwitch(
        "限制加载速度",
        "打开后会在快速滑动时分批加载封面、头像和卡片渐变，可以减少掉帧，但内容显示会稍晚",
        settings.limitImageLoadingSpeed,
      ) {
        onChange { value -> value.copy(limitImageLoadingSpeed = it) }
      }
    }
    item {
      SettingsSlider(
        title = "全屏视频背景亮度",
        valueText =
          if (settings.fullscreenBackgroundBrightness <= .005f) "完全黑"
          else "${(settings.fullscreenBackgroundBrightness * 100).roundToInt()}%",
        value = settings.fullscreenBackgroundBrightness,
        range = 0f..1f,
        steps = 9,
      ) { next ->
        onChange { it.copy(fullscreenBackgroundBrightness = next) }
      }
    }
    item { SettingsTitle("评论与弹幕") }
    item {
      SettingsSwitch("显示评论 IP 属地", "仅显示接口公开返回的信息", settings.showCommentLocation) {
        onChange { value -> value.copy(showCommentLocation = it) }
      }
    }
    item {
      SettingsSwitch("显示评论表情", "关闭后评论正文仍显示表情代码", settings.showCommentEmotes) {
        onChange { value -> value.copy(showCommentEmotes = it) }
      }
    }
    item {
      SettingsSwitch(
        "默认开启弹幕",
        "进入新视频时自动显示弹幕，播放中仍可临时关闭",
        settings.defaultShowDanmaku,
      ) {
        onChange { value -> value.copy(defaultShowDanmaku = it) }
      }
    }
    item {
      SettingsSwitch(
        "弹幕智能屏蔽",
        "优先隐藏重复、低质量和高密度弹幕",
        settings.danmakuSmartBlocking,
      ) {
        onChange { value -> value.copy(danmakuSmartBlocking = it) }
      }
    }
    item {
      SettingsSlider(
        title = "弹幕显示区域",
        valueText = "${(settings.danmakuDisplayArea * 100).roundToInt()}%",
        value = settings.danmakuDisplayArea,
        range = .1f..1f,
        steps = 8,
      ) { next ->
        onChange { it.copy(danmakuDisplayArea = next) }
      }
    }
    item {
      SettingsSlider(
        title = "同屏弹幕密度",
        valueText = "${settings.danmakuDensity} 级",
        value = settings.danmakuDensity.toFloat(),
        range = 1f..5f,
        steps = 3,
      ) { next ->
        onChange { it.copy(danmakuDensity = next.roundToInt()) }
      }
    }
    item {
      SettingsSlider(
        title = "弹幕屏蔽等级",
        valueText = "${settings.danmakuBlockLevel} 级",
        value = settings.danmakuBlockLevel.toFloat(),
        range = 1f..5f,
        steps = 3,
      ) { next ->
        onChange { it.copy(danmakuBlockLevel = next.roundToInt()) }
      }
    }
    item {
      SettingsSlider(
        title = "弹幕不透明度",
        valueText = "${(settings.danmakuOpacity * 100).roundToInt()}%",
        value = settings.danmakuOpacity,
        range = .2f..1f,
        steps = 7,
      ) { next ->
        onChange { it.copy(danmakuOpacity = next) }
      }
    }
    item {
      SettingsSlider(
        title = "弹幕字号",
        valueText = "${(settings.danmakuFontScale * 100).roundToInt()}%",
        value = settings.danmakuFontScale,
        range = .7f..1.5f,
        steps = 7,
      ) { next ->
        onChange { it.copy(danmakuFontScale = next) }
      }
    }
    item {
      SettingsSlider(
        title = "弹幕速度",
        valueText = String.format(java.util.Locale.US, "%.1f×", settings.danmakuSpeed),
        value = settings.danmakuSpeed,
        range = .5f..2f,
        steps = 14,
      ) { next ->
        onChange { it.copy(danmakuSpeed = next) }
      }
    }

    item {
      SettingsRadioGroup(
        title = "CDN 地区偏好",
        selected = settings.cdnRegionPreference,
        options = CdnRegionPreference.entries.map { region ->
          SettingsOption(region, region.title, region.description)
        },
        onSelected = { region -> onChange { value -> value.copy(cdnRegionPreference = region) } },
      )
    }
    item { SettingsTitle("存储与关于") }
    item {
      SettingsAction(
        title = "个人主页 IP 属地",
        subtitle =
          if (profileIpAuthorized) "已授权，可显示个人主页接口返回的公开 IP 属地" else "授权后可显示个人主页接口返回的公开 IP 属地",
        action = if (profileIpAuthorized) "已授权" else "去授权",
        enabled = !profileIpAuthorized,
        onClick = onAuthorizeProfileIp,
      )
    }
    item {
      SettingsAction(
        title = "播放与图片缓存",
        subtitle =
          when {
            clearingCache -> "正在清理可重新生成的缓存…"
            cacheSizeBytes == null -> "正在计算占用空间…"
            else -> "当前占用 ${AppCacheManager.formatSize(cacheSizeBytes!!)}"
          },
        action = if (clearingCache) "清理中" else "清理",
        enabled = !clearingCache,
      ) {
        clearingCache = true
        scope.launch {
          withContext(Dispatchers.IO) { AppCacheManager.clear(context) }
          cacheSizeBytes = withContext(Dispatchers.IO) { AppCacheManager.sizeBytes(context) }
          clearingCache = false
          Toast.makeText(context, "缓存已清理", Toast.LENGTH_SHORT).show()
        }
      }
    }
    item {
      BiliOneAboutCard()
    }
    item {
      SettingsAction(
        title = "恢复默认设置",
        subtitle = "保留登录状态和缓存，仅重置本页选项",
        action = "恢复",
      ) {
        showResetDialog = true
      }
    }
  }
}

@Composable
private fun MusicFavoriteFolderPicker(
  folders: List<FavoriteFolder>,
  selectedFolderId: Long,
  loading: Boolean,
  onDismiss: () -> Unit,
  onSelected: (Long) -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("选择音乐播放器收藏夹") },
    text = {
      if (loading && folders.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          item(key = "default_music_folder") {
            SettingsRadioRow(
              selected = selectedFolderId <= 0L,
              title = "音乐（默认）",
              description = "每次进入时按名称精确查找“音乐”收藏夹；没有则提示创建",
              onClick = { onSelected(0L) },
            )
          }
          items(folders, key = FavoriteFolder::id) { folder ->
            SettingsRadioRow(
              selected = selectedFolderId == folder.id,
              title = folder.title,
              description = "${folder.mediaCount} 个内容 · ${if (folder.isPublic) "公开" else "私密"}",
              onClick = { onSelected(folder.id) },
            )
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}

@Composable
private fun PreferredResolutionSetting(
  title: String,
  selected: PreferredResolutionMode,
  options: List<PreferredResolutionMode> = PreferredResolutionMode.entries,
  onSelected: (PreferredResolutionMode) -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(Modifier.padding(vertical = 8.dp)) {
      Text(
        title,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        style = MaterialTheme.typography.titleSmall,
      )
      options.forEach { mode ->
        SettingsRadioRow(
          selected = selected == mode,
          title = mode.title,
          description = mode.description,
          onClick = { onSelected(mode) },
        )
      }
    }
  }
}

@Composable
private fun <T> SettingsRadioGroup(
  title: String,
  selected: T,
  options: List<SettingsOption<T>>,
  onSelected: (T) -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(Modifier.padding(vertical = 8.dp)) {
      Text(
        title,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        style = MaterialTheme.typography.titleSmall,
      )
      options.forEach { option ->
        SettingsRadioRow(
          selected = selected == option.value,
          title = option.title,
          description = option.description,
          onClick = { onSelected(option.value) },
        )
      }
    }
  }
}

@Composable
private fun SettingsRadioRow(
  selected: Boolean,
  title: String,
  description: String,
  onClick: () -> Unit,
) {
  Row(
    Modifier.fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(start = 10.dp, end = 18.dp, top = 7.dp, bottom = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = onClick)
    Column(Modifier.weight(1f)) {
      Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color =
          if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
      )
      Text(
        description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SettingsTitle(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.titleLarge,
    color = MaterialTheme.colorScheme.onBackground,
    modifier = Modifier.padding(top = 6.dp),
  )
}

@Composable
private fun BackgroundImageSetting(
  title: String,
  selected: Boolean,
  selectedDescription: String = "已选择；仅作为页面最底层背景",
  defaultDescription: String = "未选择，使用主题背景",
  onPick: () -> Unit,
  onClear: () -> Unit,
) {
  Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
          if (selected) selectedDescription else defaultDescription,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (selected) TextButton(onClick = onClear) { Text("清除") }
      Button(onClick = onPick) { Text(if (selected) "更换" else "选择") }
    }
  }
}

@Composable
private fun SettingsSwitch(
  title: String,
  subtitle: String,
  checked: Boolean,
  onChecked: (Boolean) -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth().clickable { onChecked(!checked) },
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
          subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(checked = checked, onCheckedChange = onChecked)
    }
  }
}

@Composable
private fun SettingsSlider(
  title: String,
  valueText: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  steps: Int,
  onChange: (Float) -> Unit,
) {
  val controlMode = LocalControlMode.current
  val focusManager = LocalFocusManager.current
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
      Row(Modifier.fillMaxWidth()) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Text(valueText, color = MaterialTheme.colorScheme.primary)
      }
      Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        steps = steps,
        modifier =
          Modifier.onPreviewKeyEvent { event ->
            if (!controlMode) return@onPreviewKeyEvent false
            val direction =
              when (event.nativeKeyEvent.keyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_UP -> FocusDirection.Up
                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> FocusDirection.Down
                else -> return@onPreviewKeyEvent false
              }
            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
              focusManager.moveFocus(direction)
            }
            true
          },
      )
    }
  }
}

@Composable
private fun SettingsAction(
  title: String,
  subtitle: String,
  action: String,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Row(
      modifier = Modifier.padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
          subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      TextButton(onClick = onClick, enabled = enabled) { Text(action) }
    }
  }
}

@Composable
private fun BiliOneAboutCard() {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      AsyncImage(
        model = "https://i1.hdslb.com/bfs/face/c0903076fb89022aef21a99503bd7e79a6774edf.jpg",
        contentDescription = null,
        modifier =
          Modifier.size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentScale = ContentScale.Crop,
      )
      Column(Modifier.weight(1f)) {
        Text("关于 BiliOne", style = MaterialTheme.typography.titleSmall)
        Text(
          "开发者 · ShuyunR",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          "版本 ${BuildConfig.VERSION_NAME.removeSuffix("-debugrelease")} · 缓存清理不会影响登录和设置",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun AdvancedAudioPrioritySetting(
  selected: AdvancedAudioPriority,
  onSelected: (AdvancedAudioPriority) -> Unit,
) {
  Column(
    Modifier.fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .background(MaterialTheme.colorScheme.surface)
      .padding(vertical = 8.dp)
  ) {
    Text(
      "高级音质优先级",
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
      style = MaterialTheme.typography.titleSmall,
    )
    AdvancedAudioPriority.entries.forEach { priority ->
      Row(
        Modifier.fillMaxWidth()
          .clickable { onSelected(priority) }
          .padding(start = 10.dp, end = 18.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        RadioButton(selected = selected == priority, onClick = { onSelected(priority) })
        Column(Modifier.weight(1f)) {
          Text(
            priority.title,
            style = MaterialTheme.typography.titleSmall,
            color =
              if (selected == priority) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurface,
          )
          Text(
            priority.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
