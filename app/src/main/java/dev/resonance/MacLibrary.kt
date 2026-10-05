package dev.resonance

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.net.HttpURLConnection
import kotlinx.coroutines.flow.MutableStateFlow

class MacLibrary(
    private val context: Context,
    private val transport: ShelfTransport = HttpShelfTransport(),
) {
    private val preferences = context.getSharedPreferences("mac", 0)
    val status = MutableStateFlow("")
    val busy = MutableStateFlow(false)
    val incoming = MutableStateFlow<Uri?>(null)
    val base
        get() = preferences.getString("base", "").orEmpty()

    val key
        get() = preferences.getString("key", "").orEmpty()

    fun lanAllowed() =
        Build.VERSION.SDK_INT < 37 ||
            context.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") ==
                PackageManager.PERMISSION_GRANTED

    fun validateBase(value: String): String = ShelfAddress.validate(value)

    fun connection(path: String, offset: Long = 0): HttpURLConnection {
        check(lanAllowed()) { "lan_permission" }
        return transport.connection(validateBase(base), key, path, offset)
    }

    fun connect(address: String, accessKey: String, acceptCatalog: (List<MediaEntry>) -> Unit) {
        check(lanAllowed()) { "lan_permission" }
        val checked = validateBase(address)
        require(accessKey.matches(Regex("[a-f0-9]{48}"))) { "mac_key" }
        val entries = ShelfCatalogCodec.decode(transport.catalog(checked, accessKey))
        // Pairing/status follow the destination's durable acceptance; no SQLite dependency here.
        acceptCatalog(entries)
        preferences.edit().putString("base", checked).putString("key", accessKey).apply()
        status.value = "mac_connected"
    }

    fun refresh(acceptCatalog: (List<MediaEntry>) -> Unit) = connect(base, key, acceptCatalog)

    fun streamUri(hash: String): Uri {
        check(lanAllowed()) { "lan_permission" }
        require(hash.matches(Regex("[a-f0-9]{64}")))
        return Uri.parse(validateBase(base) + "/media/" + hash)
    }
}
