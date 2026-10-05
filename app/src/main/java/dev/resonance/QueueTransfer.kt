package dev.resonance

import android.os.Bundle
import androidx.media3.common.BundleListRetriever
import androidx.media3.common.MediaItem

/** Media3 splits large lists across Binder transactions instead of one oversized Bundle. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun queueArguments(items: List<MediaItem>): Bundle {
    require(items.size in 1..MediaLimits.QUEUE_ITEMS)
    return Bundle().apply {
        putInt("item_count", items.size)
        putBinder(
            "item_list",
            BundleListRetriever(items.map { it.toBundleIncludeLocalConfiguration() }),
        )
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun queueItems(args: Bundle): List<MediaItem> {
    val binder = args.getBinder("item_list")
    val bundles =
        if (binder != null) {
            val count = args.getInt("item_count")
            require(count in 1..MediaLimits.QUEUE_ITEMS)
            BundleListRetriever.getList(binder).also { require(it.size == count) }
        } else {
            // Preserve the existing small play-next request contract.
            @Suppress("DEPRECATION") args.getParcelableArrayList<Bundle>("items").orEmpty()
        }
    require(bundles.size in 1..MediaLimits.QUEUE_ITEMS)
    return bundles.map { MediaItem.fromBundle(it) }
}
