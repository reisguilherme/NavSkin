package br.com.gamemap.navigation.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import br.com.gamemap.navigation.model.GeoPoint
import br.com.gamemap.navigation.model.LocationFix

/** A single service-owned Android source. Cached positions retain their original monotonic age. */
class AndroidLocationSource(
    private val context: Context,
    private val onFix: (LocationFix) -> Unit,
    private val onProvidersChanged: (Boolean) -> Unit,
    private val onFailure: (String) -> Unit,
) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private var listening = false
    private var receiverRegistered = false
    private var lastGps: Location? = null
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) { deliver(location) }
        override fun onProviderEnabled(provider: String) { reportProviders() }
        override fun onProviderDisabled(provider: String) { reportProviders() }
        @Deprecated("Required by Android 8 LocationListener")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }
    private val providerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { reportProviders() }
    }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (listening) return true
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            onFailure("Permita o acesso à localização no celular.")
            return false
        }
        try {
            ContextCompat.registerReceiver(context, providerReceiver,
                IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
            var registered = false
            if (fine && manager.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
                registered = true
            }
            if (manager.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2_000L, 0f, listener, Looper.getMainLooper())
                registered = true
            }
            listening = registered
            reportProviders()
            if (!registered) {
                onFailure("Este aparelho não oferece um provedor de localização disponível.")
                stop()
                return false
            }
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).mapNotNull { provider ->
                if (provider == LocationManager.GPS_PROVIDER && !fine) null else
                    runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }.sortedBy { it.elapsedRealtimeNanos }.forEach(::deliver)
            return true
        } catch (_: SecurityException) {
            onFailure("A permissão de localização foi removida. Abra o aplicativo no celular.")
        } catch (_: Exception) {
            onFailure("Não foi possível iniciar o GPS. Confira a localização do aparelho.")
        }
        stop()
        return false
    }

    fun stop() {
        runCatching { manager.removeUpdates(listener) }
        if (receiverRegistered) runCatching { context.unregisterReceiver(providerReceiver) }
        receiverRegistered = false
        listening = false
        lastGps = null
    }

    private fun deliver(location: Location) {
        val gps = lastGps
        if (location.provider == LocationManager.NETWORK_PROVIDER && gps != null &&
            location.elapsedRealtimeNanos - gps.elapsedRealtimeNanos in 0..5_000_000_000L &&
            gps.hasAccuracy() && gps.accuracy <= 35f &&
            (!location.hasAccuracy() || gps.accuracy <= location.accuracy * 1.5f)) return
        if (location.provider == LocationManager.GPS_PROVIDER &&
            (gps == null || location.elapsedRealtimeNanos > gps.elapsedRealtimeNanos)) lastGps = Location(location)
        onFix(location.toFix())
    }

    private fun reportProviders() {
        val enabled = runCatching {
            if (Build.VERSION.SDK_INT >= 28) manager.isLocationEnabled else
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
        onProvidersChanged(enabled)
    }

    private fun Location.toFix(): LocationFix {
        val reliableBearing = hasBearing() &&
            (!hasBearingAccuracy() || bearingAccuracyDegrees.isFinite() && bearingAccuracyDegrees <= 45f)
        return LocationFix(
            GeoPoint(latitude, longitude),
            if (hasAccuracy()) accuracy.toDouble() else Double.POSITIVE_INFINITY,
            bearing.toDouble().takeIf { reliableBearing },
            speed.toDouble().takeIf { hasSpeed() },
            speedAccuracyMetersPerSecond.toDouble().takeIf { hasSpeedAccuracy() },
            elapsedRealtimeNanos / 1_000_000L,
            time,
            if (Build.VERSION.SDK_INT >= 31) isMock else @Suppress("DEPRECATION") isFromMockProvider,
        )
    }
}
