package com.sannyas.player.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether *this device* has working internet.
 *
 * Exists to tell two failures apart that look identical to the player but need
 * opposite messages: the listener is on a train with no signal, or the listener
 * is fine and oshoworld.com is not answering. Telling someone with full bars to
 * "check your network" is the kind of wrong that makes an app feel broken.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    private val context: Context,
) {

    fun isOnline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            // No way to ask: we already know a request failed, so blame the
            // thing we cannot rule out rather than the listener's connection.
            ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
