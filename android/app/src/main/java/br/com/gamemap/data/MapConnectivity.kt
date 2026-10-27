package br.com.gamemap.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper

/** Map availability only. Losing internet never changes the GPS or navigation session. */
class MapConnectivity(context: Context) {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val listeners = linkedSetOf<(Boolean) -> Unit>()
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = notifyListeners()
        override fun onLost(network: Network) = notifyListeners()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = notifyListeners()
    }

    val isOnline: Boolean get() {
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun addListener(listener: (Boolean) -> Unit): () -> Unit {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (listeners.isEmpty()) manager.registerDefaultNetworkCallback(callback, main)
        listeners += listener
        listener(isOnline)
        return {
            check(Looper.myLooper() == Looper.getMainLooper())
            if (listeners.remove(listener) && listeners.isEmpty()) manager.unregisterNetworkCallback(callback)
        }
    }

    private fun notifyListeners() {
        val online = isOnline
        listeners.toList().forEach { it(online) }
    }
}
