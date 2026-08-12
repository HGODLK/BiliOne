package dev.openbili.webdemo.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.PlayerState
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.FeedItem

@Composable
internal fun PlayerErrorActions(
  error: PlayerState.Error,
  onRetry: () -> Unit,
  onRetryNextQuality: () -> Unit,
  modifier: Modifier = Modifier,
  fullscreen: Boolean = false,
) {
  val primaryColor = if (fullscreen) Color.White else MaterialTheme.colorScheme.onSurface
  val secondaryColor =
    if (fullscreen) Color.White.copy(alpha = .72f) else MaterialTheme.colorScheme.onSurfaceVariant
  Column(
    modifier = modifier.padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("(>_<)", style = MaterialTheme.typography.displayLarge, color = secondaryColor)
    Text("遇到了一些问题", style = MaterialTheme.typography.headlineMedium, color = primaryColor)
    Text(error.message, style = MaterialTheme.typography.bodyLarge, color = secondaryColor)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      TextButton(onClick = onRetry) { Text("重试播放", color = primaryColor) }
      if ((error.playData?.streams?.size ?: 0) > 1) {
        TextButton(onClick = onRetryNextQuality) { Text("切换画质重试", color = primaryColor) }
      }
    }
  }
}

@Composable
internal fun AutoNextOverlay(
  coverUrl: String,
  nextCoverUrl: String,
  nextTitle: String,
  seconds: Int,
  triggered: Boolean,
  autoPlayEnabled: Boolean,
  handoffProgress: () -> Float,
  revealAlpha: () -> Float,
  isFullscreen: Boolean,
  onFullscreen: () -> Unit,
  onNext: () -> Unit,
  onReplay: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(modifier.graphicsLayer { alpha = revealAlpha() }.background(Color.Black)) {
    CoverImage(
      coverUrl = coverUrl,
      modifier =
        Modifier.fillMaxSize()
          .graphicsLayer {
            val handoff = handoffProgress().coerceIn(0f, 1f)
            scaleX = 1.06f
            scaleY = 1.06f
            alpha = 1f - handoff
          }
          .blur(20.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
      shape = RoundedCornerShape(0.dp),
      enforceAspectRatio = false,
    )
    CoverImage(
      coverUrl = nextCoverUrl,
      modifier =
        Modifier.fillMaxSize().graphicsLayer {
          alpha = handoffProgress().coerceIn(0f, 1f)
        },
      shape = RoundedCornerShape(0.dp),
      enforceAspectRatio = false,
    )
    Box(
      Modifier.fillMaxSize()
        .graphicsLayer { alpha = .68f * (1f - handoffProgress().coerceIn(0f, 1f)) }
        .background(Color.Black)
    )
    Column(
      modifier =
        Modifier.align(Alignment.Center).padding(24.dp).graphicsLayer {
          val handoff = handoffProgress().coerceIn(0f, 1f)
          alpha = 1f - handoff
          scaleX = 1f - .02f * handoff
          scaleY = 1f - .02f * handoff
        },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        when {
          triggered -> "正在播放下一集…"
          autoPlayEnabled -> "$seconds 秒后播放下一集"
          else -> "下一集已准备好"
        },
        color = Color.White,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      Text(
        nextTitle,
        color = Color.White.copy(alpha = .78f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      if (!triggered) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Button(
            onClick = onReplay,
            modifier = Modifier.height(44.dp).widthIn(min = 140.dp),
            shape = RoundedCornerShape(24.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = .18f),
                contentColor = Color.White,
              ),
            elevation =
              ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
          ) {
            Icon(
              Icons.Default.PlayArrow,
              contentDescription = null,
              modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("重新播放")
          }
          Button(onClick = onNext) { Text("立即播放") }
        }
      }
    }
    PlaybackEndedFullscreenButton(
      isFullscreen = isFullscreen,
      onClick = onFullscreen,
      modifier =
        Modifier.align(Alignment.BottomEnd).padding(12.dp).graphicsLayer {
          alpha = 1f - handoffProgress().coerceIn(0f, 1f)
        },
    )
  }
}

@Composable
internal fun PlaybackEndedRecommendations(
  coverUrl: String,
  recommendations: List<FeedItem>,
  hiddenCoverItemId: String?,
  revealAlpha: () -> Float,
  showForeground: Boolean = true,
  isFullscreen: Boolean,
  onFullscreen: () -> Unit,
  onReplay: () -> Unit,
  onRecommendationClick: (FeedItem, Rect) -> Unit,
  onRecommendationLongClick: (FeedItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.graphicsLayer { alpha = revealAlpha().coerceIn(0f, 1f) },
    color = Color.Black,
    contentColor = Color.White,
  ) {
    Box(Modifier.fillMaxSize()) {
      CoverImage(
        coverUrl = coverUrl,
        modifier =
          Modifier.fillMaxSize()
            .graphicsLayer {
              scaleX = 1.06f
              scaleY = 1.06f
            }
            .blur(20.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
        shape = RoundedCornerShape(0.dp),
        enforceAspectRatio = false,
      )
      Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .66f)))
      if (showForeground) {
        PlaybackEndedForeground(
          recommendations = recommendations,
          hiddenCoverItemId = hiddenCoverItemId,
          onReplay = onReplay,
          onRecommendationClick = onRecommendationClick,
          onRecommendationLongClick = onRecommendationLongClick,
          modifier = Modifier.fillMaxSize(),
        )
      }
      PlaybackEndedFullscreenButton(
        isFullscreen = isFullscreen,
        onClick = onFullscreen,
        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
      )
    }
  }
}

@Composable
internal fun PlaybackEndedForeground(
  recommendations: List<FeedItem>,
  hiddenCoverItemId: String?,
  onReplay: () -> Unit,
  onRecommendationClick: (FeedItem, Rect) -> Unit,
  onRecommendationLongClick: (FeedItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.padding(horizontal = 24.dp, vertical = 18.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      "看完啦 (´▽`)",
      color = Color.White,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(12.dp))
    AnimatedVisibility(
      visible = recommendations.isNotEmpty(),
      enter = fadeIn(tween(220)),
      exit = fadeOut(tween(150)),
    ) {
      LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        contentPadding = PaddingValues(horizontal = 0.dp),
      ) {
        items(recommendations.take(3), key = { it.id }) { item ->
          RecommendationCard(
            item = item,
            onClick = { bounds -> onRecommendationClick(item, bounds) },
            onLongClick = { onRecommendationLongClick(item) },
            coverVisible = item.id != hiddenCoverItemId,
            overlayStyle = true,
            showDuration = true,
          )
        }
      }
    }
    Spacer(Modifier.height(24.dp))
    Button(
      onClick = onReplay,
      modifier = Modifier.height(48.dp).widthIn(min = 164.dp),
      shape = RoundedCornerShape(24.dp),
      colors =
        ButtonDefaults.buttonColors(
          containerColor = Color(0xFFFF5C8A),
          contentColor = Color.White,
        ),
      elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
      contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
    ) {
      Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
      Spacer(Modifier.width(6.dp))
      Text("重新播放", fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
internal fun PlaybackEndedFullscreenButton(
  isFullscreen: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = CircleShape,
    color = Color.Black.copy(alpha = .42f),
    contentColor = Color.White,
  ) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
      FullscreenControlIcon(
        exiting = isFullscreen,
        modifier = Modifier.size(22.dp),
        color = Color.White,
      )
    }
  }
}
