package com.rork.gpssimulator.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.data.model.LocationSample

/**
 * Reads the device's real location through the platform LocationManager.
 *
 * Play Services fused provider is used when [preferPlayServices] is set and the
 * platform exposes FUSED_PROVIDER (API 31+); otherwise the option degrades
 * gracefully to GPS/network rather than erroring.
 */
class RealLocationSource(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private var listener: LocationListener? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean {
        val manager = locationManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.isLocationEnabled
            } else {
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isGpsProviderEnabled(): Boolean = try {
        locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    } catch (e: Exception) {
        false
    }

    fun isNetworkAvailable(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return try {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    /** True when the platform exposes a fused provider we can prefer. */
    fun isPlayServicesProviderAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            locationManager?.allProviders?.contains(LocationManager.FUSED_PROVIDER) == true
        } catch (e: Exception) {
            false
        }
    }

    /** Name of the provider currently used for real-location reads. */
    fun activeProviderName(preferPlayServices: Boolean): String = when {
        preferPlayServices && isPlayServicesProviderAvailable() -> "Google Play Services (fused)"
        isGpsProviderEnabled() -> "GPS"
        locationManager?.allProviders?.contains(LocationManager.NETWORK_PROVIDER) == true -> "Network"
        else -> "Unavailable"
    }

    private fun providerOrder(preferPlayServices: Boolean): List<String> = buildList {
        if (preferPlayServices && isPlayServicesProviderAvailable()) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
    }.distinct()

    /** Best last-known fix across the available providers. */
    fun lastKnown(preferPlayServices: Boolean): LocationSample? {
        if (!hasPermission()) return null
        val manager = locationManager ?: return null
        return try {
            providerOrder(preferPlayServices)
                .mapNotNull { provider ->
                    runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
                }
                .maxByOrNull { it.time }
                ?.toSample()
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission revoked while reading last known fix")
            null
        }
    }

    /**
     * Starts continuous real-location updates. Safe to call repeatedly; the
     * previous listener is removed first.
     */
    fun startUpdates(preferPlayServices: Boolean, intervalMs: Long, onSample: (LocationSample) -> Unit) {
        if (!hasPermission()) return
        val manager = locationManager ?: return
        stopUpdates()

        val newListener = object : LocationListener {
            override fun onLocationChanged(location: Location) = onSample(location.toSample())
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}

            @Deprecated("Required for API < 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        }

        try {
            val provider = providerOrder(preferPlayServices).firstOrNull { p ->
                runCatching { manager.isProviderEnabled(p) }.getOrDefault(false)
            } ?: return
            manager.requestLocationUpdates(
                provider,
                intervalMs,
                0f,
                newListener,
                Looper.getMainLooper(),
            )
            listener = newListener
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot request location updates without permission")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request location updates: ${e.javaClass.simpleName}")
        }
    }

    fun stopUpdates() {
        val manager = locationManager ?: return
        listener?.let {
            runCatching { manager.removeUpdates(it) }
        }
        listener = null
    }

    private fun Location.toSample(): LocationSample = LocationSample(
        point = LatLng(latitude, longitude),
        accuracy = if (hasAccuracy()) accuracy else 0f,
        altitude = if (hasAltitude()) altitude else 0.0,
        speed = if (hasSpeed()) speed else 0f,
        bearing = if (hasBearing()) bearing else 0f,
        provider = provider ?: "unknown",
        timestamp = time,
    )

    companion object {
        private const val TAG = "RealLocationSource"
    }
}
