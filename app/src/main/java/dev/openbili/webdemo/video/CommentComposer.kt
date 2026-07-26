package dev.openbili.webdemo.video

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.openbili.webdemo.api.BiliEmote
import dev.openbili.webdemo.api.BiliEmotePackage
import dev.openbili.webdemo.api.MentionSuggestion
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
internal fun CommentComposer(
  emotes: List<BiliEmote>,
  emotePackages: List<BiliEmotePackage>,
  mentionSuggestions: List<MentionSuggestion>,
  mentionSuggestionsLoading: Boolean,
  onMentionQuery: (String) -> Unit,
  targetName: String? = null,
  onClearTarget: () -> Unit = {},
  imageEnabled: Boolean,
  onSend: (String, Uri?) -> Unit,
  onDetachedModeChanged: (Boolean) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val editorState = rememberTextFieldState()
  var showTools by remember { mutableStateOf(false) }
  var toolPage by remember { mutableStateOf(CommentToolPage.EMOTES) }
  var mentionQuery by remember { mutableStateOf("") }
  var visualLineCount by remember { mutableIntStateOf(1) }
  var detachedByVisualLines by remember { mutableStateOf(false) }
  var detachedReleaseLength by remember { mutableIntStateOf(0) }
  var imageUri by remember { mutableStateOf<Uri?>(null) }
  val imagePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selected ->
      if (selected != null) {
        imageUri = selected
        showTools = false
      }
    }
  val text = editorState.text.toString()
  val emoteMarkerRegistry = remember { CommentEmoteMarkerRegistry() }
  val emoteMarkers = remember(emotes, emoteMarkerRegistry) { emoteMarkerRegistry.snapshot(emotes) }
  val focusRequester = remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val effectiveLineCount = maxOf(visualLineCount, text.count { it == '\n' } + 1)
  val longTextMode = text.length >= 48 || effectiveLineCount >= 2
  val detachedMode = detachedByVisualLines
  LaunchedEffect(effectiveLineCount, text.length) {
    if (detachedByVisualLines) {
      // The detached editor is much wider than the comment pane, so remeasuring the same text
      // there can reduce six wrapped lines to one. Release only after an actual deletion margin;
      // layout changes by themselves can no longer bounce the two editors back and forth.
      detachedByVisualLines = text.isNotEmpty() && text.length > detachedReleaseLength
    } else if (commentEditorShouldDetach(text.length, effectiveLineCount)) {
      detachedReleaseLength = (text.length - 12).coerceAtLeast(0)
      detachedByVisualLines = true
    }
  }
  val inputMinHeight by
    animateDpAsState(
      targetValue = if (longTextMode) 116.dp else 54.dp,
      animationSpec = tween(220, easing = FastOutSlowInEasing),
      label = "commentInputHeight",
    )
  val inputCorner by
    animateDpAsState(
      targetValue = if (longTextMode) 20.dp else 27.dp,
      animationSpec = tween(220, easing = FastOutSlowInEasing),
      label = "commentInputCorner",
    )
  val plusRotation by
    animateFloatAsState(
      targetValue = if (showTools) 45f else 0f,
      animationSpec = tween(220, easing = FastOutSlowInEasing),
      label = "commentToolsRotation",
    )
  LaunchedEffect(detachedMode) {
    onDetachedModeChanged(detachedMode)
    if (detachedMode) {
      delay(16)
      focusRequester.requestFocus()
    }
  }
  Column(
    modifier =
      modifier.fillMaxWidth()
        .then(if (detachedMode) Modifier.fillMaxSize().clickable(onClick = {}) else Modifier),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    AnimatedVisibility(
      visible = showTools,
      enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = .97f),
      exit = fadeOut(tween(130)) + scaleOut(tween(160), targetScale = .97f),
    ) {
      CommentToolPanel(
        page = toolPage,
        onPageChanged = { page ->
          toolPage = page
          if (page == CommentToolPage.MENTIONS) onMentionQuery(mentionQuery)
        },
        emotes = emotes,
        emotePackages = emotePackages,
        mentionQuery = mentionQuery,
        onMentionQueryChanged = { query ->
          mentionQuery = query
          onMentionQuery(query)
        },
        mentionSuggestions = mentionSuggestions,
        mentionSuggestionsLoading = mentionSuggestionsLoading,
        onEmoteSelected = { emote ->
          val replacement = emoteMarkers.markerFor(emote)?.toString() ?: emote.text
          replaceCommentSelection(editorState, replacement)
        },
        onMentionSelected = { suggestion ->
          val selectionStart = minOf(editorState.selection.start, editorState.selection.end)
          val spacer =
            if (selectionStart == 0 || text[selectionStart - 1].isWhitespace()) "" else " "
          replaceCommentSelection(editorState, "$spacer@${suggestion.name} ")
          mentionQuery = ""
          showTools = false
          focusRequester.requestFocus()
        },
        imagePickerAvailable = imageEnabled,
        imageUnavailableReason = "图片评论仅大会员可用",
        onImagePick = { imagePicker.launch("image/*") },
        modifier = Modifier.fillMaxWidth(),
      )
    }
    AnimatedVisibility(
      visible = detachedMode,
      modifier = if (detachedMode) Modifier.weight(1f) else Modifier,
      enter = fadeIn(tween(190)) + scaleIn(tween(260), initialScale = .94f),
      exit = fadeOut(tween(140)) + scaleOut(tween(190), targetScale = .96f),
    ) {
      Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
        tonalElevation = 2.dp,
        shadowElevation = 7.dp,
        border =
          androidx.compose.foundation.BorderStroke(
            .75.dp,
            MaterialTheme.colorScheme.outlineVariant,
          ),
      ) {
        Column(
          Modifier.fillMaxSize().padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              "长评论 · ${text.length} 字",
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              "输入框已展开",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          CommentTextEditor(
            state = editorState,
            placeholder = targetName?.let { "回复 @$it" }.orEmpty(),
            emoteMarkers = emoteMarkers.markerToEmote,
            focusRequester = focusRequester,
            onFocused = { showTools = false },
            onLineCountChanged = { visualLineCount = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
            maxLines = 12,
          )
        }
      }
    }
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(30.dp),
      color = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
      shadowElevation = 3.dp,
      border =
        androidx.compose.foundation.BorderStroke(
          0.5.dp,
          MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
      Column(Modifier.fillMaxWidth().animateContentSize().padding(10.dp)) {
        if (targetName != null) {
          Surface(
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          ) {
            Row(
              modifier = Modifier.padding(start = 10.dp, end = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("正在回复 @$targetName", style = MaterialTheme.typography.labelMedium)
              TextButton(onClick = onClearTarget) { Text("关闭") }
            }
          }
        }
        imageUri?.let { uri ->
          Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Box(Modifier.size(82.dp)) {
              AsyncImage(
                model = uri,
                contentDescription = "待发送评论图片",
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop,
              )
              Surface(
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
              ) {
                IconButton(onClick = { imageUri = null }, modifier = Modifier.size(26.dp)) {
                  Icon(
                    Icons.Default.Close,
                    contentDescription = "移除评论图片",
                    modifier = Modifier.size(16.dp),
                  )
                }
              }
            }
            Text(
              if (text.isBlank()) "图片需搭配文字发送" else "将随本条评论发送 1 张图片",
              style = MaterialTheme.typography.labelMedium,
              color =
                if (text.isBlank()) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (!detachedMode) {
            Surface(
              modifier = Modifier.weight(1f).heightIn(min = inputMinHeight, max = 168.dp),
              shape = RoundedCornerShape(inputCorner),
              color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
              CommentTextEditor(
                state = editorState,
                placeholder = targetName?.let { "回复 @$it" }.orEmpty(),
                emoteMarkers = emoteMarkers.markerToEmote,
                focusRequester = focusRequester,
                onFocused = { showTools = false },
                onLineCountChanged = { visualLineCount = it },
                modifier =
                  Modifier.fillMaxWidth()
                    .heightIn(min = inputMinHeight, max = 168.dp)
                    .animateContentSize(),
                maxLines = if (longTextMode) 7 else 2,
              )
            }
          } else {
            Surface(
              modifier =
                Modifier.weight(1f).height(54.dp).clickable { focusRequester.requestFocus() },
              shape = RoundedCornerShape(27.dp),
              color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
              Row(
                Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  "正在编辑长评论",
                  modifier = Modifier.weight(1f),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  "${text.length} 字",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .92f),
          ) {
            IconButton(
              onClick = {
                val opening = !showTools
                showTools = opening
                if (opening && !detachedMode) {
                  focusManager.clearFocus()
                  keyboardController?.hide()
                }
              },
              modifier = Modifier.size(54.dp),
            ) {
              Icon(
                Icons.Default.AddCircle,
                "评论工具",
                modifier = Modifier.graphicsLayer { rotationZ = plusRotation },
              )
            }
          }
          Surface(
            shape = CircleShape,
            color =
              if (text.isNotBlank()) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.surfaceVariant,
          ) {
            IconButton(
              onClick = {
                val message = emoteMarkers.decode(text).trim()
                if (message.isNotEmpty()) {
                  onSend(message, imageUri)
                  editorState.edit { delete(0, length) }
                  imageUri = null
                  showTools = false
                }
              },
              enabled = text.isNotBlank(),
              modifier = Modifier.size(54.dp),
            ) {
              Icon(
                Icons.AutoMirrored.Filled.Send,
                "发表",
                tint =
                  if (text.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

internal enum class CommentToolPage {
  EMOTES,
  MENTIONS,
  IMAGE,
}

@Composable
internal fun CommentTextEditor(
  state: TextFieldState,
  placeholder: String,
  emoteMarkers: Map<Char, BiliEmote>,
  focusRequester: FocusRequester,
  enabled: Boolean = true,
  onFocused: () -> Unit = {},
  onLineCountChanged: (Int) -> Unit = {},
  modifier: Modifier = Modifier,
  maxLines: Int,
) {
  var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
  val text = state.text.toString()
  val placements = remember(text, emoteMarkers) { findCommentEmotePlacements(text, emoteMarkers) }
  val outputTransformation =
    remember(emoteMarkers) { commentEmoteMarkerOutputTransformation(emoteMarkers.keys) }
  val density = LocalDensity.current
  BasicTextField(
    state = state,
    enabled = enabled,
    modifier =
      modifier
        .onFocusChanged { if (it.isFocused) onFocused() }
        .focusRequester(focusRequester)
        .padding(horizontal = 20.dp, vertical = 16.dp),
    textStyle =
      MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
    lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = maxLines),
    outputTransformation = outputTransformation,
    onTextLayout = { getResult ->
      val result = getResult()
      textLayout = result
      result?.let { onLineCountChanged(it.lineCount.coerceAtLeast(1)) }
    },
    decorator = { inner ->
      Box(Modifier.clipToBounds()) {
        if (text.isEmpty() && placeholder.isNotBlank()) {
          Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        inner()
        placements.forEach { placement ->
          val layout = textLayout ?: return@forEach
          val bounds =
            layout
              .takeIf { placement.index < it.layoutInput.text.length }
              ?.getBoundingBox(placement.index) ?: return@forEach
          val iconSize =
            with(density) { minOf(bounds.width, bounds.height).coerceAtLeast(1f).toDp() }
          AsyncImage(
            model = placement.emote.url,
            contentDescription = placement.emote.text,
            modifier =
              Modifier.offset {
                  IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt())
                }
                .size(iconSize),
            contentScale = ContentScale.Fit,
          )
        }
      }
    },
  )
}

internal fun commentEmoteMarkerOutputTransformation(
  emoteMarkers: Collection<Char>
): OutputTransformation {
  val markers = emoteMarkers.toSet()
  return OutputTransformation {
    val source = asCharSequence().toString()
    val markerIndices = source.indices.filter { source[it] in markers }
    markerIndices.asReversed().forEach { index ->
      replace(index, index + 1, COMMENT_EMOTE_LAYOUT_PLACEHOLDER.toString())
    }
    markerIndices.forEach { index ->
      addStyle(SpanStyle(color = Color.Transparent), index, index + 1)
    }
  }
}

internal const val COMMENT_EMOTE_LAYOUT_PLACEHOLDER = '\uFFFC'

internal fun commentEditorShouldDetach(
  characterCount: Int,
  visualLineCount: Int,
): Boolean = characterCount >= 360 || visualLineCount >= 6

private fun replaceCommentSelection(
  state: TextFieldState,
  replacement: String,
) {
  val updated =
    replaceCommentSelection(
      TextFieldValue(state.text.toString(), selection = state.selection),
      replacement,
    )
  state.edit {
    replace(0, length, updated.text)
    selection = updated.selection
  }
}

internal fun replaceCommentSelection(
  value: TextFieldValue,
  replacement: String,
): TextFieldValue {
  val start = minOf(value.selection.start, value.selection.end)
  val end = maxOf(value.selection.start, value.selection.end)
  val updated = value.text.replaceRange(start, end, replacement)
  return TextFieldValue(updated, selection = TextRange(start + replacement.length))
}

internal data class CommentEmotePlacement(
  val index: Int,
  val emote: BiliEmote,
)

internal fun findCommentEmotePlacements(
  source: String,
  emoteMarkers: Map<Char, BiliEmote>,
): List<CommentEmotePlacement> = source.mapIndexedNotNull { index, character ->
  emoteMarkers[character]?.let { CommentEmotePlacement(index, it) }
}

internal class CommentEmoteMarkerRegistry {
  private val tokenToMarker = linkedMapOf<String, Char>()
  private val markerToEmote = linkedMapOf<Char, BiliEmote>()
  private var nextMarkerCode = PRIVATE_USE_START.code

  fun snapshot(emotes: List<BiliEmote>): CommentEmoteMarkerSnapshot {
    emotes
      .filter { it.text.isNotEmpty() }
      .forEach { emote ->
        val marker =
          tokenToMarker[emote.text]
            ?: allocateMarker()?.also { allocated -> tokenToMarker[emote.text] = allocated }
            ?: return@forEach
        markerToEmote[marker] = emote
      }
    return CommentEmoteMarkerSnapshot(tokenToMarker.toMap(), markerToEmote.toMap())
  }

  private fun allocateMarker(): Char? {
    if (nextMarkerCode > PRIVATE_USE_END.code) return null
    return (nextMarkerCode++).toChar()
  }

  private companion object {
    const val PRIVATE_USE_START = '\uE000'
    const val PRIVATE_USE_END = '\uF8FF'
  }
}

internal data class CommentEmoteMarkerSnapshot(
  val tokenToMarker: Map<String, Char>,
  val markerToEmote: Map<Char, BiliEmote>,
) {
  fun markerFor(emote: BiliEmote): Char? = tokenToMarker[emote.text]

  fun encode(source: String): String {
    val tokens = tokenToMarker.keys.filter(source::contains).sortedByDescending(String::length)
    if (tokens.isEmpty()) return source
    return buildString {
      var index = 0
      while (index < source.length) {
        val token = tokens.firstOrNull { source.startsWith(it, index) }
        if (token == null) {
          append(source[index])
          index += 1
        } else {
          append(tokenToMarker.getValue(token))
          index += token.length
        }
      }
    }
  }

  fun encodedOffset(source: String, decodedOffset: Int): Int =
    encode(source.substring(0, decodedOffset.coerceIn(0, source.length))).length

  fun decodedOffset(source: String, encodedOffset: Int): Int =
    decode(source.substring(0, encodedOffset.coerceIn(0, source.length))).length

  fun decode(source: String): String = buildString {
    source.forEach { character -> append(markerToEmote[character]?.text ?: character) }
  }
}

@Composable
internal fun CommentToolPanel(
  page: CommentToolPage,
  onPageChanged: (CommentToolPage) -> Unit,
  emotes: List<BiliEmote>,
  emotePackages: List<BiliEmotePackage>,
  mentionQuery: String,
  onMentionQueryChanged: (String) -> Unit,
  mentionSuggestions: List<MentionSuggestion>,
  mentionSuggestionsLoading: Boolean,
  onEmoteSelected: (BiliEmote) -> Unit,
  onMentionSelected: (MentionSuggestion) -> Unit,
  allowMentions: Boolean = true,
  imagePickerAvailable: Boolean = false,
  imageUnavailableReason: String = "图片评论不可用",
  onImagePick: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val packages =
    remember(emotes, emotePackages) {
      if (emotePackages.isNotEmpty()) emotePackages else listOf(BiliEmotePackage(-1, "表情", emotes))
    }
  var selectedPackageId by remember(packages) { mutableStateOf(packages.firstOrNull()?.id ?: -1L) }
  val selectedEmotes =
    packages.firstOrNull { it.id == selectedPackageId }?.emotes
      ?: packages.firstOrNull()?.emotes.orEmpty()
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
    border =
      androidx.compose.foundation.BorderStroke(.75.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(Modifier.fillMaxWidth().heightIn(max = 166.dp)) {
        when (page) {
          CommentToolPage.EMOTES -> {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
              LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(packages, key = { it.id }) { pack ->
                  FilterChip(
                    selected = pack.id == selectedPackageId,
                    onClick = { selectedPackageId = pack.id },
                    label = { Text(pack.name, maxLines = 1) },
                  )
                }
              }
              if (selectedEmotes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                  Text("暂无可用表情", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              } else {
                LazyHorizontalGrid(
                  rows = GridCells.Fixed(3),
                  horizontalArrangement = Arrangement.spacedBy(5.dp),
                  verticalArrangement = Arrangement.spacedBy(5.dp),
                  modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                  gridItems(selectedEmotes, key = { "${selectedPackageId}_${it.text}" }) { emote ->
                    AsyncImage(
                      model = emote.url,
                      contentDescription = emote.text,
                      modifier =
                        Modifier.size(36.dp).clip(RoundedCornerShape(7.dp)).clickable {
                          onEmoteSelected(emote)
                        },
                      contentScale = ContentScale.Fit,
                    )
                  }
                }
              }
            }
          }
          CommentToolPage.MENTIONS -> {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
              ) {
                BasicTextField(
                  value = mentionQuery,
                  onValueChange = onMentionQueryChanged,
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                  textStyle =
                    MaterialTheme.typography.bodyMedium.copy(
                      color = MaterialTheme.colorScheme.onSurface
                    ),
                  decorationBox = { inner ->
                    if (mentionQuery.isBlank())
                      Text(
                        "选择或输入你想@的人",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    inner()
                  },
                )
              }
              Text(
                if (mentionQuery.isBlank()) "我的关注" else "搜索结果",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Box(Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(Modifier.fillMaxSize()) {
                  items(mentionSuggestions, key = { it.mid }) { user ->
                    Row(
                      modifier =
                        Modifier.fillMaxWidth()
                          .clip(RoundedCornerShape(12.dp))
                          .clickable { onMentionSelected(user) }
                          .padding(horizontal = 8.dp, vertical = 6.dp),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      AsyncImage(
                        model = user.face,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                      )
                      Spacer(Modifier.width(9.dp))
                      Column(Modifier.weight(1f)) {
                        Text(user.name, style = MaterialTheme.typography.labelLarge)
                        Text(
                          user.subtitle,
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    }
                  }
                }
                if (mentionSuggestionsLoading) {
                  CircularProgressIndicator(
                    Modifier.align(Alignment.Center).size(22.dp),
                    strokeWidth = 2.dp,
                  )
                } else if (mentionSuggestions.isEmpty()) {
                  Text(
                    if (mentionQuery.isBlank()) "暂无关注数据" else "没有找到相关用户",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
          CommentToolPage.IMAGE -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("图片", style = MaterialTheme.typography.titleMedium)
                if (imagePickerAvailable) {
                  TextButton(onClick = onImagePick) { Text("从相册选择图片") }
                } else {
                  Text(
                    imageUnavailableReason,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                  )
                }
              }
            }
          }
        }
      }
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        CommentToolTab("表情", page == CommentToolPage.EMOTES, Modifier.weight(1f)) {
          onPageChanged(CommentToolPage.EMOTES)
        }
        if (allowMentions) {
          CommentToolTab("@", page == CommentToolPage.MENTIONS, Modifier.weight(1f)) {
            onPageChanged(CommentToolPage.MENTIONS)
          }
        }
        CommentToolTab("图片", page == CommentToolPage.IMAGE, Modifier.weight(1f)) {
          onPageChanged(CommentToolPage.IMAGE)
        }
      }
    }
  }
}

@Composable
internal fun CommentToolTab(
  label: String,
  selected: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Surface(
    modifier = modifier.height(38.dp).clickable(onClick = onClick),
    shape = RoundedCornerShape(14.dp),
    color =
      if (selected) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surfaceVariant,
    contentColor =
      if (selected) MaterialTheme.colorScheme.onPrimaryContainer
      else MaterialTheme.colorScheme.onSurfaceVariant,
  ) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text(label, style = MaterialTheme.typography.labelLarge)
    }
  }
}
