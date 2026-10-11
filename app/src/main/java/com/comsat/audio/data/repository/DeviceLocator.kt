package com.comsat.audio.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

// Approximate location only: weather needs a city, not a street. Fresh fixes
// are requested only while COMSAT is on screen, so no background permission.
@Singleton
class DeviceLocator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val manager = context.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isEnabled(): Boolean = LocationManagerCompat.isLocationEnabled(manager)

    suspend fun currentLocation(): Location? {
        if (!hasPermission()) return null
        val provider = provider() ?: return lastKnownLocation()
        val fresh = withTimeoutOrNull(FIX_TIMEOUT_MS) { requestFix(provider) }
        return fresh ?: lastKnownLocation()
    }

    // Android withholds this from apps in the background without background
    // location permission; callers treat null as "keep the previous fix".
    @SuppressLint("MissingPermission")
    fun lastKnownLocation(): Location? {
        if (!hasPermission()) return null
        return try {
            manager.getProviders(true)
                .mapNotNull { manager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
        } catch (error: SecurityException) {
            Log.w(TAG, "Location is not available", error)
            null
        }
    }

    suspend fun placeName(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val address = withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                continuation.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                continuation.resume(null)
                            }
                        })
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // The platform geocoder is a network service with no SLA.
                Log.w(TAG, "Could not name the location", error)
                null
            }
        }
        return listOfNotNull(address?.locality, address?.subAdminArea, address?.adminArea)
            .firstOrNull { it.isNotBlank() }
    }

    private fun provider(): String? {
        val enabled = manager.getProviders(true)
        return listOfNotNull(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) LocationManager.FUSED_PROVIDER else null,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER
        ).firstOrNull { it in enabled }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFix(provider: String): Location? = suspendCancellableCoroutine { continuation ->
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) {
                    continuation.resume(it)
                }
            } else {
                // Older platforms call every listener method, so none may be left to defaults.
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (continuation.isActive) continuation.resume(location)
                    }

                    override fun onProviderDisabled(provider: String) {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onProviderEnabled(provider: String) = Unit

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        } catch (error: SecurityException) {
            // Permission can be revoked between the check and the request.
            Log.w(TAG, "Location request was rejected", error)
            continuation.resume(null)
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Location provider disappeared", error)
            continuation.resume(null)
        }
    }

    private companion object {
        const val TAG = "ComsatLocation"
        const val FIX_TIMEOUT_MS = 15_000L
        const val GEOCODE_TIMEOUT_MS = 5_000L
    }
}
