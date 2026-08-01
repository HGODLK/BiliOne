package dev.openbili.webdemo.article

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.FeedViewModel
import dev.openbili.webdemo.feed.LoadedFeedImageRegistry
import dev.openbili.webdemo.ui.PressableVideoCard
import dev.openbili.webdemo.ui.VideoShapeTokens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ArticleCard(
  article: ArticleItem,
  coverVisible: Boolean,
  onClick: (Rect) -> Unit,
  modifier: Modifier = Modifier,
  onBoundsChanged: (Rect) -> Unit = {},
  loadKey: String = article.stableId,
  compact: Boolean = false,
  compactHeight: Dp? = null,
  decorationVisible: Boolean = true,
  historyLabel: String? = null,
) {
  var latestBounds by remember(article.stableId) { mutableStateOf(Rect.Zero) }
  val decorationAlpha by
    animateFloatAsState(
      targetValue = if (decorationVisible) 1f else 0f,
      animationSpec = tween(150),
      label = "articleCardDecorationAlpha",
    )
  PressableVideoCard(
    onClick = { onClick(latestBounds) },
    onLongClick = {},
    modifier = if (compact) Modifier.widthIn(max = 304.dp) else Modifier,
  ) {
    Surface(
      modifier = if (compact) modifier.widthIn(max = 304.dp) else modifier.fillMaxWidth(),
      shape = VideoShapeTokens.Card,
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 2.dp,
      shadowElevation = 3.dp,
    ) {
      if (compact) {
        if (compactHeight != null) {
          val coverWidth = (compactHeight - 12.dp) * (16f / 9f)
          Row(
            Modifier.fillMaxWidth().height(compactHeight).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ArticleVisual(
              article = article,
              modifier =
                Modifier.width(coverWidth)
                  .fillMaxHeight()
                  .onGloballyPositioned {
                    latestBounds = it.boundsInRoot()
                    onBoundsChanged(latestBounds)
                  },
              visible = coverVisible,
              enforceAspectRatio = false,
              decorationAlpha = 0f,
              loadKey = loadKey,
            )
            Column(
              Modifier.weight(1f).graphicsLayer { alpha = decorationAlpha },
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                article.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                article.authorName.ifBlank { article.categoryName.ifBlank { "专栏" } },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        } else {
          Box {
            ArticleVisual(
              article = article,
              modifier =
                Modifier.fillMaxWidth().onGloballyPositioned {
                  latestBounds = it.boundsInRoot()
                  onBoundsChanged(latestBounds)
                },
              visible = coverVisible,
              decorationAlpha = decorationAlpha,
              loadKey = loadKey,
            )
            Box(
              Modifier.matchParentSize()
                .graphicsLayer { alpha = decorationAlpha }
                .background(
                  Brush.verticalGradient(
                    listOf(
                      Color.Transparent,
                      Color.Black.copy(alpha = .12f),
                      Color.Black.copy(alpha = .72f),
                    )
                  )
                )
            )
            Column(
              Modifier.align(Alignment.BottomStart)
                .fillMaxWidth()
                .graphicsLayer { alpha = decorationAlpha }
                .padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Text(
                article.title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                article.authorName.ifBlank { article.categoryName.ifBlank { "专栏" } },
                color = Color.White.copy(alpha = .82f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      } else {
        Column {
          ArticleVisual(
            article = article,
            modifier =
              Modifier.fillMaxWidth().onGloballyPositioned {
                latestBounds = it.boundsInRoot()
                onBoundsChanged(latestBounds)
              },
            visible = coverVisible,
            loadKey = loadKey,
          )
          Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
          ) {
          Text(
            article.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(48.dp),
          )
          Text(
            article.summary.ifBlank { "这篇专栏暂时没有简介" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(36.dp),
          )
          historyLabel?.let { label ->
            Text(
              label,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              article.authorName.ifBlank { article.categoryName.ifBlank { "专栏" } },
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              articleCardMeta(article),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          }
        }
      }
    }
  }
}

@Composable
fun ArticleVisual(
  article: ArticleItem,
  modifier: Modifier = Modifier,
  visible: Boolean = true,
  enforceAspectRatio: Boolean = true,
  decorationAlpha: Float = 1f,
  loadKey: String = article.stableId,
  alwaysLoad: Boolean = false,
  fadeIn: Boolean = true,
) {
  Box(
    modifier =
      modifier
        .then(if (enforceAspectRatio) Modifier.aspectRatio(16f / 9f) else Modifier)
        .clip(VideoShapeTokens.Player)
        .graphicsLayer { alpha = if (visible) 1f else 0f }
        .background(MaterialTheme.colorScheme.primaryContainer)
  ) {
    if (article.coverUrl.isNotBlank()) {
      val transitionBitmap =
        if (fadeIn) null
        else remember(article.coverUrl) { LoadedFeedImageRegistry.bitmap(article.coverUrl) }
      if (transitionBitmap != null) {
        Image(
          bitmap = transitionBitmap.asImageBitmap(),
          contentDescription = article.title,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
        )
      } else {
        CoverImage(
          coverUrl = article.coverUrl,
          contentDescription = article.title,
          modifier = Modifier.fillMaxSize(),
          shape = RoundedCornerShape(0.dp),
          enforceAspectRatio = false,
          loadKey = loadKey,
          alwaysLoad = alwaysLoad,
          fadeIn = fadeIn,
        )
      }
      Box(
        Modifier.fillMaxSize()
          .graphicsLayer { alpha = decorationAlpha }
          .background(
            Brush.verticalGradient(
              listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .26f))
            )
          )
      )
    } else {
      Text(
        article.title.take(1).ifBlank { "文" },
        modifier = Modifier.align(Alignment.Center),
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .58f),
        fontWeight = FontWeight.Bold,
      )
    }
    Surface(
      modifier =
        Modifier.align(Alignment.TopStart)
          .padding(10.dp)
          .graphicsLayer { alpha = decorationAlpha },
      shape = RoundedCornerShape(9.dp),
      color = MaterialTheme.colorScheme.surface.copy(alpha = .86f),
      contentColor = MaterialTheme.colorScheme.primary,
    ) {
      Text(
        article.categoryName.ifBlank { "专栏" },
        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

private fun articleCardMeta(article: ArticleItem): String {
  if (article.viewCount > 0L) return "${FeedViewModel.formatCount(article.viewCount)} 点击"
  if (article.publishedAt <= 0L) return "专栏"
  return DateTimeFormatter.ofPattern("yyyy-MM-dd")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochSecond(article.publishedAt))
}
