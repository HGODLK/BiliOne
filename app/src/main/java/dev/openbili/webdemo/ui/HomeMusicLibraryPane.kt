package dev.openbili.webdemo.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.music.HomeMusicPlayerViewModel
import dev.openbili.webdemo.music.HomeMusicUiState
import dev.openbili.webdemo.music.MusicFavoriteFolderTitle
import dev.openbili.webdemo.music.MusicLibraryStatus
import kotlinx.coroutines.launch

@Composable
internal fun MusicLibraryPane(
  state: HomeMusicUiState,
  viewModel: HomeMusicPlayerViewModel,
  onLoginClick: (Rect) -> Unit,
  onFavoriteFolderSelected: (Long) -> Unit,
  backdropLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
  backdropBounds: Rect,
  underlayLayer: androidx.compose.ui.graphics.layer.GraphicsLayer?,
  underlayBounds: Rect,
  controlFocusRequest: Int,
  controlDismissTransientRequest: Int,
  controlFocusable: Boolean,
  controlFocusActive: Boolean,
  onControlReturnToPlayer: () -> Unit,
  onControlHideLibrary: () -> Unit,
  onControlFocused: () -> Unit,
  onControlTransientOpenChanged: (Boolean) -> Unit,
  modifier: Modifier,
) {
  var loginBounds by remember { mutableStateOf(Rect.Zero) }
  val controlMode = LocalControlMode.current
  val controlEntryFocusRequester = remember { FocusRequester() }
  val searchFocusRequester = remember { FocusRequester() }
  val scope = rememberCoroutineScope()
  val keyboardController = LocalSoftwareKeyboardController.current
  var searchEditing by remember { mutableStateOf(false) }
  var trackTransientOpen by remember { mutableStateOf(false) }
  var folderTransientOpen by remember { mutableStateOf(false) }
  var trackFocusRequest by remember { mutableIntStateOf(0) }
  val transientOpen = searchEditing || trackTransientOpen || folderTransientOpen
  LaunchedEffect(transientOpen) { onControlTransientOpenChanged(transientOpen) }
  LaunchedEffect(controlDismissTransientRequest) {
    if (controlDismissTransientRequest <= 0) return@LaunchedEffect
    searchEditing = false
    keyboardController?.hide()
  }
  LaunchedEffect(
    controlFocusRequest,
    controlMode,
  ) {
    if (!controlMode || !controlFocusable || controlFocusRequest <= 0) return@LaunchedEffect
    if (state.libraryStatus == MusicLibraryStatus.READY && state.items.isNotEmpty()) {
      trackFocusRequest++
      return@LaunchedEffect
    }
    withFrameNanos {}
    val requester =
      if (state.libraryStatus == MusicLibraryStatus.READY && state.folder != null) {
        searchFocusRequester
      } else {
        controlEntryFocusRequester
      }
    runCatching { requester.requestFocus() }
  }
  LaunchedEffect(
    controlMode,
    controlFocusActive,
    controlFocusable,
    state.libraryStatus,
    state.currentItem?.id,
    state.items.size,
  ) {
    if (!controlMode || !controlFocusActive || !controlFocusable || transientOpen) {
      return@LaunchedEffect
    }
    withFrameNanos {}
    if (state.libraryStatus == MusicLibraryStatus.READY && state.items.isNotEmpty()) {
      trackFocusRequest++
    } else {
      val requester =
        if (state.libraryStatus == MusicLibraryStatus.READY && state.folder != null) {
          searchFocusRequester
        } else {
          controlEntryFocusRequester
        }
      runCatching { requester.requestFocus() }
    }
  }
  BackHandler(enabled = controlMode && searchEditing) {
    searchEditing = false
    keyboardController?.hide()
    runCatching { searchFocusRequester.requestFocus() }
  }
  val darkTheme = MaterialTheme.colorScheme.background.luminance() < .5f
  val containerAlpha =
    if (darkTheme) HomeGlassTokens.DarkContainerAlpha else HomeGlassTokens.LightContainerAlpha
  val borderAlpha =
    if (darkTheme) HomeGlassTokens.DarkBorderAlpha else HomeGlassTokens.LightBorderAlpha
  BackdropGlassSurface(
    backdropLayer = backdropLayer,
    backdropBounds = backdropBounds,
    underlayLayer = underlayLayer,
    underlayBounds = underlayBounds,
    modifier =
      modifier.then(
        if (controlMode) {
          Modifier.onPreviewKeyEvent { event ->
            if (
              !controlFocusable ||
                transientOpen ||
                event.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_DPAD_RIGHT
            ) {
              return@onPreviewKeyEvent false
            }
            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
              onControlHideLibrary()
            }
            true
          }
        } else {
          Modifier
        }
      ),
    shape = MusicPaneShape,
    blurRadius = HomeGlassTokens.BlurRadius,
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = containerAlpha),
    fallbackColor = MaterialTheme.colorScheme.surface,
    border =
      BorderStroke(
        .75.dp,
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = borderAlpha),
      ),
  ) {
    Column(Modifier.fillMaxSize().padding(14.dp)) {
      if (state.libraryStatus != MusicLibraryStatus.SIGNED_OUT && state.folder != null) {
        TextField(
          value = state.query,
          onValueChange = viewModel::updateQuery,
          modifier =
            Modifier.fillMaxWidth()
              .height(52.dp)
              .focusRequester(searchFocusRequester)
              .focusProperties { canFocus = !controlMode || controlFocusable }
              .musicFocusChrome(
                shape = RoundedCornerShape(17.dp),
                color = MaterialTheme.colorScheme.primary,
                width = 3.dp,
              )
              .onFocusChanged {
                if (it.isFocused) {
                  onControlFocused()
                } else if (controlMode) {
                  searchEditing = false
                }
              }
              .onPreviewKeyEvent { event ->
                if (!controlMode || !controlFocusable) return@onPreviewKeyEvent false
                val keyCode = event.nativeKeyEvent.keyCode
                if (!searchEditing && isControlConfirmKey(keyCode)) {
                  if (event.type == KeyEventType.KeyUp) {
                    searchEditing = true
                    scope.launch {
                      withFrameNanos {}
                      keyboardController?.show()
                    }
                  }
                  return@onPreviewKeyEvent true
                }
                if (searchEditing || event.type != KeyEventType.KeyDown) {
                  return@onPreviewKeyEvent false
                }
                when (keyCode) {
                  KeyEvent.KEYCODE_DPAD_LEFT -> {
                    onControlReturnToPlayer()
                    true
                  }
                  KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (state.items.isNotEmpty()) trackFocusRequest++
                    true
                  }
                  KeyEvent.KEYCODE_DPAD_UP,
                  KeyEvent.KEYCODE_DPAD_RIGHT -> true
                  else -> false
                }
              },
          singleLine = true,
          readOnly = controlMode && !searchEditing,
          shape = RoundedCornerShape(17.dp),
          placeholder = {
            Text(
              "搜索收藏夹中的视频",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索音乐") },
          trailingIcon = {
            if (state.query.isNotBlank()) {
              androidx.compose.material3.IconButton(
                onClick = { viewModel.updateQuery("") },
                modifier = Modifier.focusProperties { canFocus = !controlMode },
              ) {
                Icon(Icons.Default.Close, contentDescription = "清空音乐搜索")
              }
            }
          },
          colors =
            TextFieldDefaults.colors(
              focusedTextColor = MaterialTheme.colorScheme.onSurface,
              unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
              focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
              unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
              focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
              unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
              focusedTrailingIconColor = MaterialTheme.colorScheme.onSurface,
              unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
              focusedContainerColor =
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .58f),
              unfocusedContainerColor =
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .42f),
              focusedIndicatorColor = Color.Transparent,
              unfocusedIndicatorColor = Color.Transparent,
              disabledIndicatorColor = Color.Transparent,
              cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Spacer(Modifier.height(10.dp))
      }
      when (state.libraryStatus) {
        MusicLibraryStatus.SIGNED_OUT ->
          MusicLibraryMessage(
            title = "登录后才能读取个人收藏夹",
            action = "去登录",
            onAction = { onLoginClick(loginBounds) },
            actionModifier =
              Modifier.onGloballyPositioned { loginBounds = it.boundsInRoot() }
                .focusRequester(controlEntryFocusRequester)
                .focusProperties { canFocus = !controlMode || controlFocusable }
                .onFocusChanged { if (it.isFocused) onControlFocused() },
          )
        MusicLibraryStatus.LOADING ->
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
          }
        MusicLibraryStatus.MISSING ->
          MusicFolderSetupMessage(
            state = state,
            controlFocusRequester = controlEntryFocusRequester,
            controlFocusable = controlFocusable,
            controlDismissTransientRequest = controlDismissTransientRequest,
            onControlFocused = onControlFocused,
            onControlTransientOpenChanged = { folderTransientOpen = it },
            onFolderSelected = onFavoriteFolderSelected,
            onCreateMusicFolder = {
              viewModel.createMusicFolder(onCreated = onFavoriteFolderSelected)
            },
          )
        MusicLibraryStatus.ERROR ->
          MusicLibraryMessage(
            title = state.libraryError ?: "音乐收藏夹加载失败",
            action = "重新加载",
            onAction = viewModel::retryLibrary,
            actionModifier =
              Modifier.focusRequester(controlEntryFocusRequester)
                .focusProperties { canFocus = !controlMode || controlFocusable }
                .onFocusChanged { if (it.isFocused) onControlFocused() },
          )
        MusicLibraryStatus.READY -> {
          if (state.items.isEmpty() && state.query.isBlank()) {
            MusicLibraryMessage(
              title = "“${state.folder?.title ?: MusicFavoriteFolderTitle}”收藏夹还是空的",
              subtitle = "先在收藏夹中加入一些视频吧。",
            )
          } else {
            MusicTrackList(
              state = state,
              viewModel = viewModel,
              controlFocusRequest = trackFocusRequest,
              controlDismissTransientRequest = controlDismissTransientRequest,
              controlFocusable = controlFocusable,
              searchFocusRequester = searchFocusRequester,
              onControlReturnToPlayer = onControlReturnToPlayer,
              onControlFocused = onControlFocused,
              onControlTransientOpenChanged = { trackTransientOpen = it },
            )
          }
        }
      }
      state.libraryError
        ?.takeIf {
          state.libraryStatus == MusicLibraryStatus.READY ||
            state.libraryStatus == MusicLibraryStatus.MISSING
        }
        ?.let { error ->
          Text(
            error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
          )
        }
    }
  }
}

@Composable
private fun MusicFolderSetupMessage(
  state: HomeMusicUiState,
  controlFocusRequester: FocusRequester,
  controlFocusable: Boolean,
  controlDismissTransientRequest: Int,
  onControlFocused: () -> Unit,
  onControlTransientOpenChanged: (Boolean) -> Unit,
  onFolderSelected: (Long) -> Unit,
  onCreateMusicFolder: () -> Unit,
) {
  val controlMode = LocalControlMode.current
  var folderMenuExpanded by remember { mutableStateOf(false) }
  LaunchedEffect(folderMenuExpanded) { onControlTransientOpenChanged(folderMenuExpanded) }
  LaunchedEffect(controlDismissTransientRequest) {
    if (controlDismissTransientRequest > 0) folderMenuExpanded = false
  }
  val exactMusicFolder =
    remember(state.availableFolders) {
      state.availableFolders.firstOrNull { it.title.trim() == MusicFavoriteFolderTitle }
    }
  Column(
    Modifier.fillMaxSize().padding(20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      if (state.folderSelectionConfigured) "原先选择的收藏夹已不可用" else "选择音乐收藏夹",
      color = MaterialTheme.colorScheme.onSurface,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
      "可以选择一个已有的个人收藏夹，或新建私密“音乐”收藏夹。",
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(16.dp))
    Box {
      TextButton(
        modifier =
          Modifier.then(
              if (state.availableFolders.isNotEmpty()) {
                Modifier.focusRequester(controlFocusRequester)
              } else {
                Modifier
              }
            )
            .focusProperties {
              canFocus = !controlMode || (controlFocusable && state.availableFolders.isNotEmpty())
            }
            .musicFocusChrome(
              shape = RoundedCornerShape(20.dp),
              color = MaterialTheme.colorScheme.primary,
              width = 3.dp,
            )
            .onFocusChanged { if (it.isFocused) onControlFocused() },
        onClick = { folderMenuExpanded = true },
        enabled = state.availableFolders.isNotEmpty() && !state.creatingFolder,
      ) {
        Text(if (state.availableFolders.isEmpty()) "暂无已有收藏夹" else "选择已有收藏夹")
      }
      DropdownMenu(
        expanded = folderMenuExpanded,
        onDismissRequest = { folderMenuExpanded = false },
      ) {
        state.availableFolders.forEach { folder ->
          DropdownMenuItem(
            text = {
              Column {
                Text(folder.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                  "${folder.mediaCount} 个内容 · ${if (folder.isPublic) "公开" else "私密"}",
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            },
            onClick = {
              folderMenuExpanded = false
              onFolderSelected(folder.id)
            },
          )
        }
      }
    }
    if (exactMusicFolder == null) {
      Button(
        onClick = onCreateMusicFolder,
        enabled = !state.creatingFolder,
        modifier =
          Modifier.then(
              if (state.availableFolders.isEmpty()) {
                Modifier.focusRequester(controlFocusRequester)
              } else {
                Modifier
              }
            )
            .focusProperties { canFocus = !controlMode || controlFocusable }
            .musicFocusChrome(
              shape = RoundedCornerShape(20.dp),
              color = MaterialTheme.colorScheme.primary,
              width = 3.dp,
            )
            .onFocusChanged { if (it.isFocused) onControlFocused() },
      ) {
        Text(if (state.creatingFolder) "正在创建" else "创建“音乐”收藏夹")
      }
    }
    Spacer(Modifier.height(14.dp))
    Text(
      "之后可随时在“我的 > 设置 > 音乐播放器收藏夹”中修改。",
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodySmall,
    )
  }
}

@Composable
internal fun MusicLibraryMessage(
  title: String,
  subtitle: String? = null,
  action: String? = null,
  actionEnabled: Boolean = true,
  onAction: () -> Unit = {},
  actionModifier: Modifier = Modifier,
) {
  Column(
    Modifier.fillMaxSize().padding(20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      title,
      color = MaterialTheme.colorScheme.onSurface,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )
    subtitle?.let {
      Spacer(Modifier.height(8.dp))
      Text(
        it,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
      )
    }
    action?.let {
      Spacer(Modifier.height(18.dp))
      Button(
        onClick = onAction,
        enabled = actionEnabled,
        modifier =
          actionModifier.musicFocusChrome(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primary,
            width = 3.dp,
          ),
      ) {
        Text(it)
      }
    }
  }
}
