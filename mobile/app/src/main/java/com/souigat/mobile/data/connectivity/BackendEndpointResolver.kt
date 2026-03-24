package com.souigat.mobile.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.RouteInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import com.souigat.mobile.util.Constants

@Singleton
class BackendEndpointResolver @Inject constructor(
    @ApplicationContext context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    @Volatile
    private var currentBaseUrl: String = prefs.getString(KEY_BASE_URL, Constants.BASE_URL) ?: Constants.BASE_URL

    @Volatile
    private var resolvedNetworkKey: String? = prefs.getString(KEY_NETWORK_KEY, null)

    fun getActiveBaseUrl(): String {
        if (!Constants.usesDynamicDebugBackend()) {
            return Constants.BASE_URL
        }

        val networkKey = currentNetworkKey() ?: return currentBaseUrl
        if (networkKey == resolvedNetworkKey && currentBaseUrl != Constants.BASE_URL) {
            return currentBaseUrl
        }

        return resolveBaseUrl(forceRefresh = false)
    }

    fun resolveBaseUrl(forceRefresh: Boolean): String {
        if (!Constants.usesDynamicDebugBackend()) {
            return Constants.BASE_URL
        }

        synchronized(lock) {
            val networkKey = currentNetworkKey()
            if (!forceRefresh && networkKey != null && networkKey == resolvedNetworkKey && currentBaseUrl != Constants.BASE_URL) {
                return currentBaseUrl
            }

            val cachedBaseUrl = prefs.getString(KEY_BASE_URL, null)
            val cachedNetworkKey = prefs.getString(KEY_NETWORK_KEY, null)
            val candidates = buildCandidates(networkKey, cachedBaseUrl, cachedNetworkKey == networkKey)
            val discoveredBaseUrl = runBlocking(Dispatchers.IO) {
                discoverReachableBaseUrl(candidates)
            }

            if (discoveredBaseUrl != null) {
                persistResolvedBaseUrl(discoveredBaseUrl, networkKey)
                Timber.i("Resolved debug backend endpoint: $discoveredBaseUrl")
                return discoveredBaseUrl
            }

            Timber.w("Unable to resolve a reachable LAN backend. Keeping $currentBaseUrl")
            return currentBaseUrl
        }
    }

    fun invalidateResolvedBaseUrl() {
        if (!Constants.usesDynamicDebugBackend()) {
            return
        }
        resolvedNetworkKey = null
    }

    private fun persistResolvedBaseUrl(baseUrl: String, networkKey: String?) {
        currentBaseUrl = baseUrl
        resolvedNetworkKey = networkKey
        prefs.edit()
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_NETWORK_KEY, networkKey)
            .apply()
    }

    private fun buildCandidates(
        networkKey: String?,
        cachedBaseUrl: String?,
        canReuseCached: Boolean
    ): List<String> {
        val candidates = linkedSetOf<String>()

        if (canReuseCached && !cachedBaseUrl.isNullOrBlank()) {
            candidates += cachedBaseUrl
        }

        val linkProperties = connectivityManager.getLinkProperties(connectivityManager.activeNetwork)
            ?: return candidates.toList()

        val selfAddress = linkProperties.linkAddresses
            .mapNotNull { it.address as? Inet4Address }
            .firstOrNull()

        val gateway = linkProperties.routes
            .mapNotNull { route -> route.gatewayIfIpv4() }
            .firstOrNull()

        if (gateway != null) {
            candidates += buildBaseUrl(gateway.hostAddress ?: return candidates.toList())
        }

        if (selfAddress != null) {
            val prefixLength = linkProperties.linkAddresses
                .firstOrNull { it.address == selfAddress }
                ?.prefixLength
                ?: DEFAULT_PREFIX_LENGTH
            candidates += subnetCandidates(selfAddress, prefixLength)
        }

        if (!canReuseCached && !cachedBaseUrl.isNullOrBlank()) {
            candidates += cachedBaseUrl
        }

        if (networkKey != null && currentBaseUrl != Constants.BASE_URL) {
            candidates += currentBaseUrl
        }

        return candidates.toList()
    }

    private suspend fun discoverReachableBaseUrl(candidates: List<String>): String? = coroutineScope {
        if (candidates.isEmpty()) {
            return@coroutineScope null
        }

        val gate = Semaphore(PARALLEL_PROBES)
        candidates.map { candidate ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    if (isBackendReachable(candidate)) {
                        candidate
                    } else {
                        null
                    }
                }
            }
        }.awaitAll().firstOrNull { it != null }
    }

    private fun isBackendReachable(baseUrl: String): Boolean {
        return runCatching {
            val connection = (URL(baseUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
            }

            connection.use { http ->
                val statusCode = http.responseCode
                if (statusCode !in 200..299) {
                    false
                } else {
                    val body = BufferedInputStream(http.inputStream).bufferedReader().use { it.readText() }
                    body.contains("\"service\":\"souigat-api\"")
                }
            }
        }.getOrDefault(false)
    }

    private fun subnetCandidates(address: Inet4Address, prefixLength: Int): List<String> {
        val normalizedPrefix = prefixLength.coerceIn(MIN_PREFIX_LENGTH, MAX_PREFIX_LENGTH)
        val hostCount = 1 shl (32 - normalizedPrefix)
        val baseAddress = ipv4ToInt(address) and prefixMask(normalizedPrefix)
        val selfAddress = ipv4ToInt(address)
        val candidates = ArrayList<String>(hostCount)

        for (offset in 1 until hostCount - 1) {
            val candidate = baseAddress + offset
            if (candidate == selfAddress) {
                continue
            }
            candidates += buildBaseUrl(intToIpv4(candidate))
        }

        return candidates
    }

    private fun currentNetworkKey(): String? {
        val linkProperties = connectivityManager.getLinkProperties(connectivityManager.activeNetwork) ?: return null
        val ipv4Link = linkProperties.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        return "${ipv4Link.address.hostAddress}/${ipv4Link.prefixLength}"
    }

    private fun buildBaseUrl(host: String): String = "http://$host:8000/api/"

    private fun prefixMask(prefixLength: Int): Int {
        if (prefixLength == 0) {
            return 0
        }
        return (-1 shl (32 - prefixLength))
    }

    private fun ipv4ToInt(address: Inet4Address): Int {
        val octets = address.address
        return ((octets[0].toInt() and 0xff) shl 24) or
            ((octets[1].toInt() and 0xff) shl 16) or
            ((octets[2].toInt() and 0xff) shl 8) or
            (octets[3].toInt() and 0xff)
    }

    private fun intToIpv4(value: Int): String {
        return listOf(
            (value ushr 24) and 0xff,
            (value ushr 16) and 0xff,
            (value ushr 8) and 0xff,
            value and 0xff
        ).joinToString(".")
    }

    private fun HttpURLConnection.use(block: (HttpURLConnection) -> Boolean): Boolean {
        return try {
            block(this)
        } finally {
            disconnect()
        }
    }

    private fun RouteInfo.gatewayIfIpv4(): Inet4Address? = gateway as? Inet4Address

    companion object {
        private const val PREFS_NAME = "backend_endpoint_resolver"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_NETWORK_KEY = "network_key"
        private const val CONNECT_TIMEOUT_MS = 350
        private const val READ_TIMEOUT_MS = 350
        private const val PARALLEL_PROBES = 24
        private const val DEFAULT_PREFIX_LENGTH = 24
        private const val MIN_PREFIX_LENGTH = 24
        private const val MAX_PREFIX_LENGTH = 28
    }
}
