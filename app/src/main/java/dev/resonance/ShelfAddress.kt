package dev.resonance

import java.net.InetAddress
import java.net.URI

/** Pure address policy; permission checks and network requests belong to Android adapters. */
object ShelfAddress {
    fun validate(
        value: String,
        resolve: (String) -> Array<InetAddress> = InetAddress::getAllByName,
    ): String {
        val uri = URI(value.trim())
        require(
            uri.scheme == "http" &&
                uri.userInfo == null &&
                uri.rawQuery == null &&
                uri.rawFragment == null &&
                (uri.path.isNullOrEmpty() || uri.path == "/")
        ) {
            "mac_address"
        }
        val host = uri.host ?: error("mac_address")
        require(uri.port in 1..65535) { "mac_address" }
        val addresses = resolve(host)
        require(
            addresses.isNotEmpty() &&
                addresses.all {
                    it.isSiteLocalAddress || it.isLinkLocalAddress || it.isLoopbackAddress
                }
        ) {
            "mac_address"
        }
        return "http://${if (host.contains(':')) "[$host]" else host}:${uri.port}"
    }
}
