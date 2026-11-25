package dev.resonance

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

class MacLibrary(
    private val context: Context,
    private val transport: ShelfTransport = HttpShelfTransport(),
) {
    internal data class Snapshot(
        val base: String,
        val key: String,
        val sourceId: String,
        val entries: List<MediaEntry>,
        val names: JSONObject,
        val collections: JSONObject,
        val retiredHashes: Set<String>,
    )

    private val preferences = context.getSharedPreferences("mac", 0)
    val status = MutableStateFlow("")
    val busy = MutableStateFlow(false)
    val incoming = MutableStateFlow<Uri?>(null)
    @Volatile
    var authority: String = ""
        private set

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

    /** Do not send the pairing key to a newly discovered, unverified host. */
    fun preferredBase(): String {
        val usb = "http://127.0.0.1:10002"
        if (key.isNotBlank() && verifyIdentity(usb, key, 250)) return usb
        return preferences.getString("wifi_base", "").orEmpty().ifBlank { base }
    }

    private fun verifyIdentity(address: String, accessKey: String, timeout: Int): Boolean {
        val challenge =
            ByteArray(16)
                .also { SecureRandom().nextBytes(it) }
                .joinToString("") { "%02x".format(it) }
        val probe =
            (URL("$address/api/identity?challenge=$challenge").openConnection(Proxy.NO_PROXY)
                as HttpURLConnection)
        return try {
            probe.connectTimeout = timeout
            probe.readTimeout = timeout
            probe.instanceFollowRedirects = false
            if (probe.responseCode != 200) return false
            val actual =
                JSONObject(probe.inputStream.bufferedReader().use { it.readText() })
                    .optString("proof")
            val hmac = Mac.getInstance("HmacSHA256")
            hmac.init(SecretKeySpec(accessKey.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            val expected =
                hmac.doFinal(challenge.toByteArray(Charsets.UTF_8)).joinToString("") {
                    "%02x".format(it)
                }
            MessageDigest.isEqual(
                actual.toByteArray(Charsets.US_ASCII),
                expected.toByteArray(Charsets.US_ASCII),
            )
        } catch (_: Exception) {
            false
        } finally {
            probe.disconnect()
        }
    }

    internal fun prepare(address: String, accessKey: String, fresh: Boolean = false): Snapshot {
        check(lanAllowed()) { "lan_permission" }
        val checked = validateBase(address)
        require(accessKey.matches(Regex("[a-f0-9]{48}"))) { "mac_key" }
        check(verifyIdentity(checked, accessKey, 1500)) { "mac_unavailable" }
        val raw =
            if (fresh) transport.freshCatalog(checked, accessKey)
            else transport.catalog(checked, accessKey)
        val entries = ShelfCatalogCodec.decode(raw)
        val payload = JSONObject(raw)
        val sourceId =
            payload
                .optString("sourceId", "")
                .takeIf { it.matches(Regex("[a-f0-9-]{36}")) }
                .orEmpty()
        val names = JSONObject()
        val retired = payload.optJSONArray("retiredHashes") ?: org.json.JSONArray()
        require(retired.length() <= MediaLimits.CATALOG_ITEMS) { "capacity" }
        val retiredHashes = (0 until retired.length()).map { retired.getString(it) }.toSet()
        require(retiredHashes.all { it.matches(Regex("[a-f0-9]{64}")) }) { "catalog_scope" }
        for (entry in entries) entry.syncedDisplayName?.let {
            names.put(
                entry.contentHash,
                JSONObject().put("title", it.title).put("subtitle", it.subtitle),
            )
        }
        return Snapshot(
            checked,
            accessKey,
            sourceId,
            entries,
            names,
            payload.optJSONObject("collectionNames") ?: JSONObject(),
            retiredHashes,
        )
    }

    internal fun commit(snapshot: Snapshot) {
        check(
            preferences
                .edit()
                .putString("synced_names", snapshot.names.toString())
                .putString("synced_collections", snapshot.collections.toString())
                .putString("base", snapshot.base)
                .putString("key", snapshot.key)
                .commit()
        ) {
            "storage_full"
        }
        authority = snapshot.sourceId
        if (!snapshot.base.contains("127.0.0.1") && !snapshot.base.contains("localhost")) {
            preferences.edit().putString("wifi_base", snapshot.base).apply()
        } else {
            // USB supplies the paired Mac's LAN address for the next cable-free sync.
            try {
                val request = transport.connection(snapshot.base, snapshot.key, "/api/connection")
                try {
                    if (request.responseCode == 200) {
                        val host =
                            JSONObject(request.inputStream.bufferedReader().use { it.readText() })
                                .optString("base")
                        if (host.isNotBlank() && !host.contains("127.0.0.1"))
                            preferences.edit().putString("wifi_base", validateBase(host)).apply()
                    }
                } finally {
                    request.disconnect()
                }
            } catch (_: Exception) {}
        }
        restoreNames()
        status.value = "mac_connected"
    }

    fun connect(address: String, accessKey: String, acceptCatalog: (List<MediaEntry>) -> Unit) {
        val snapshot = prepare(address, accessKey)
        acceptCatalog(snapshot.entries)
        commit(snapshot)
    }

    fun refresh(acceptCatalog: (List<MediaEntry>) -> Unit) = connect(base, key, acceptCatalog)

    internal fun verifiedCatalog(): Snapshot = prepare(base, key, fresh = true)

    fun restoreNames() {
        val json = JSONObject(preferences.getString("synced_names", "{}").orEmpty())
        val collections = JSONObject(preferences.getString("synced_collections", "{}").orEmpty())
        LibraryPresentation.updateNames(
            json.keys().asSequence().associateWith { hash ->
                val row = json.getJSONObject(hash)
                DisplayName(row.getString("title"), row.optString("subtitle"))
            },
            collections.keys().asSequence().associateWith { collections.getString(it) },
        )
    }

    fun streamUri(hash: String): Uri {
        check(lanAllowed()) { "lan_permission" }
        require(hash.matches(Regex("[a-f0-9]{64}")))
        return Uri.parse(validateBase(base) + "/media/" + hash)
    }
}
