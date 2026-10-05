package dev.resonance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Bundled overrides and local art only; no network artwork requests. */
private object LocalLibraryArt {
    private val readers = Semaphore(2)
    private val images =
        object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
        }
    private val missing = LruCache<String, Boolean>(96)

    suspend fun loadAsset(context: Context, path: String): Bitmap? {
        val key = "asset:$path"
        images.get(key)?.let {
            return it
        }
        return withContext(Dispatchers.IO) {
            readers.withPermit {
                images.get(key)?.let {
                    return@withPermit it
                }
                val bitmap = runCatching {
                    context.assets.open(path).use { BitmapFactory.decodeStream(it) }
                }
                    .getOrNull()
                if (bitmap != null) images.put(key, bitmap)
                bitmap
            }
        }
    }

    suspend fun load(context: Context, entry: MediaEntry): Bitmap? {
        val uri = Uri.parse(entry.playUri)
        if (entry.remote || uri.scheme !in listOf("content", "file", null)) return null
        val key = "${entry.id}:${entry.contentHash}:${entry.bytes}:${entry.playUri}"
        images.get(key)?.let {
            return it
        }
        if (missing.get(key) == true) return null
        return withContext(Dispatchers.IO) {
            readers.withPermit {
                images.get(key)?.let {
                    return@withPermit it
                }
                if (missing.get(key) == true) return@withPermit null
                val retriever = MediaMetadataRetriever()
                val bitmap =
                    try {
                        if (uri.scheme == null) retriever.setDataSource(entry.playUri)
                        else retriever.setDataSource(context, uri)
                        val embedded = retriever.embeddedPicture
                        val art =
                            embedded
                                ?.takeIf { it.size <= 8 * 1024 * 1024 }
                                ?.let { bytes ->
                                    val bounds =
                                        BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                                    val options =
                                        BitmapFactory.Options().apply {
                                            inSampleSize = 1
                                            while (
                                                bounds.outWidth / inSampleSize > 256 ||
                                                    bounds.outHeight / inSampleSize > 256
                                            ) inSampleSize *= 2
                                        }
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                                }
                        art
                            ?: if (
                                retriever.extractMetadata(
                                    MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO
                                ) == "yes"
                            )
                                retriever.getScaledFrameAtTime(
                                    0,
                                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                    128,
                                    128,
                                )
                            else null
                    } catch (_: Exception) {
                        null
                    } finally {
                        runCatching { retriever.release() }
                    }
                if (bitmap != null) images.put(key, bitmap) else missing.put(key, true)
                bitmap
            }
        }
    }
}

@Composable
internal fun LibraryThumbnail(entries: List<MediaEntry>, collectionKey: String? = null) {
    val context = LocalContext.current.applicationContext
    // A collection uses the first available local cover, with a bounded search.
    val candidates = entries.filter { !it.remote && it.state == "ready" }.take(3)
    val override =
        if (collectionKey != null) LibraryArtOverrides.forCollection(collectionKey)
        else entries.singleOrNull()?.let { LibraryArtOverrides.forEntry(it) }
    val fallback =
        if (collectionKey != null) LibraryArtOverrides.playlistFallback
        else LibraryArtOverrides.albumFallback
    val art =
        produceState<Bitmap?>(null, candidates, override, fallback) {
                value = null
                val bundled = override?.let { LocalLibraryArt.loadAsset(context, it) }
                if (bundled != null) {
                    value = bundled
                    return@produceState
                }
                for (entry in candidates) {
                    val image = LocalLibraryArt.load(context, entry)
                    if (image != null) {
                        value = image
                        break
                    }
                }
                if (value == null) value = fallback?.let { LocalLibraryArt.loadAsset(context, it) }
            }
            .value
    // Reserve the same small cover column while art loads, keeping titles aligned.
    Box(Modifier.width(44.dp).height(32.dp)) {
        if (art == null) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else
            art.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(3.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
    }
}
