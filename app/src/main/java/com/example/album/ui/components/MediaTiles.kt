package com.example.album.ui.components

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.album.data.MediaAlbum
import com.example.album.data.MediaItem
import com.example.album.data.ThumbnailRepository
import java.io.File
import com.example.album.ui.theme.VaultDimens
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private val gifDecodeSlots = Semaphore(1)

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AlbumTile(
    album: MediaAlbum,
    onLongClick: (() -> Unit)? = null,
    sharedElementEnabled: Boolean = true,
    onClick: () -> Unit
) {
    val english = LocalAppEnglish.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .975f else 1f, tween(90), label = "album-press")
    Column(
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            MediaThumbnail(
                item = album.cover,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(VaultDimens.AlbumRadius)).let {
                    if (sharedElementEnabled) it.mediaSharedElement(album.cover) else it
                },
                showVideoMark = album.cover.isVideo
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 2.dp, top = 7.dp, end = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(album.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = VaultDimens.AlbumName)
            Text(album.items.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = VaultDimens.AlbumCount)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun PressableMediaThumbnail(
    item: MediaItem,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
    showFavoriteBadge: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    sharedElementEnabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .965f else 1f, tween(85), label = "media-press")
    Box(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .let { if (sharedElementEnabled) it.mediaSharedElement(item) else it }
    ) {
        MediaThumbnail(item, Modifier.fillMaxSize())
        if (favorite && showFavoriteBadge) FavoriteBadge()
    }
}

@Composable
private fun BoxScope.FavoriteBadge() {
    Icon(
        imageVector = Icons.Filled.Star,
        contentDescription = null,
        tint = Color(0xFFFFC107),
        modifier = Modifier.align(Alignment.TopEnd).padding(end = 3.dp, top = 3.dp).size(22.dp)
    )
}

@Composable
fun MediaThumbnail(
    item: MediaItem,
    modifier: Modifier = Modifier,
    requestedSize: Int = 360,
    showVideoMark: Boolean = item.isVideo,
    contentScale: ContentScale = ContentScale.Crop,
    backgroundColor: Color? = null,
    animateGif: Boolean? = null,
    onLoaded: (() -> Unit)? = null
) {
    val english = LocalAppEnglish.current
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("album_settings", android.content.Context.MODE_PRIVATE) }
    val cacheGeneration = preferences.getLong("thumbnail_cache_generation", 0L)
    val shouldAnimateGif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        item.mimeType.equals("image/gif", ignoreCase = true) &&
        (animateGif ?: preferences.getBoolean("gif_thumbnails", true))
    val animatedDrawable by produceState<Drawable?>(initialValue = null, item.uri, requestedSize, shouldAnimateGif) {
        value = if (shouldAnimateGif) withContext(Dispatchers.IO) {
            gifDecodeSlots.withPermit {
                runCatching {
                        val source = if (item.uri.scheme == "file") {
                            ImageDecoder.createSource(File(item.uri.path ?: return@withPermit null))
                        } else {
                            ImageDecoder.createSource(context.contentResolver, item.uri)
                        }
                        ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                        val sample = maxOf(info.size.width, info.size.height).div(requestedSize.coerceAtLeast(1)).coerceAtLeast(1)
                        decoder.setTargetSampleSize(sample)
                    }
                }.getOrNull()
            }
        } else null
    }
    val quantizedSize = ThumbnailRepository.quantizeSize(requestedSize)
    val bitmap by produceState<Bitmap?>(
        initialValue = if (shouldAnimateGif) null else ThumbnailRepository.peek(item, quantizedSize, preferences),
        item.uri,
        item.size,
        item.dateModified,
        quantizedSize,
        shouldAnimateGif,
        cacheGeneration
    ) {
        if (shouldAnimateGif) {
            value = null
            return@produceState
        }
        value = ThumbnailRepository.load(context, item, quantizedSize, preferences)
    }

    val backgroundModifier = if (backgroundColor != null) modifier.background(backgroundColor)
    else modifier.background(placeholderBrush(item.id))
    Box(modifier = backgroundModifier) {
        val loaded = animatedDrawable != null || bitmap != null
        LaunchedEffect(loaded) {
            if (loaded) onLoaded?.invoke()
        }
        val mediaAlpha by animateFloatAsState(if (loaded) 1f else 0f, tween(180), label = "thumbnail-load")
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = mediaAlpha }) {
            animatedDrawable?.let { drawable ->
                AndroidView(
                    factory = { viewContext -> ImageView(viewContext) },
                    update = { view ->
                        view.scaleType = when (contentScale) {
                            ContentScale.Fit -> ImageView.ScaleType.FIT_CENTER
                            ContentScale.FillBounds -> ImageView.ScaleType.FIT_XY
                            else -> ImageView.ScaleType.CENTER_CROP
                        }
                        view.setImageDrawable(drawable)
                        (drawable as? AnimatedImageDrawable)?.start()
                    },
                    modifier = Modifier.fillMaxSize()
                )
                DisposableEffect(drawable) {
                    (drawable as? AnimatedImageDrawable)?.start()
                    onDispose { (drawable as? AnimatedImageDrawable)?.stop() }
                }
            } ?: bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale
                )
            }
        }
        if (showVideoMark) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                color = Color.Black.copy(alpha = .55f),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = appText("视频", english), tint = Color.White, modifier = Modifier.padding(7.dp))
            }
        }
    }
}

private fun placeholderBrush(seed: Long): Brush {
    val palettes = listOf(
        listOf(Color(0xFF9ED7D1), Color(0xFF315C69)),
        listOf(Color(0xFFF4C27A), Color(0xFF9C5940)),
        listOf(Color(0xFFAEC5EF), Color(0xFF596A9A)),
        listOf(Color(0xFFC6D99A), Color(0xFF617348))
    )
    return Brush.linearGradient(palettes[(seed % palettes.size).toInt()])
}
