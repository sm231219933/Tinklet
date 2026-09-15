package com.tinklet.bharatdatingapp.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class LocationResult(
    val country: String,
    val state: String,
    val latitude: Double,
    val longitude: Double
)

class LocationHelper(private val context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val settingsClient = LocationServices.getSettingsClient(context)

    fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if location settings (GPS) are enabled.
     */
    private suspend fun checkLocationSettings(): Unit = suspendCancellableCoroutine { continuation ->
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        
        settingsClient.checkLocationSettings(builder.build())
            .addOnSuccessListener {
                continuation.resume(Unit)
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }

    /**
     * Fetches current GPS location and reverse geocodes to get Country and State.
     */
    suspend fun fetchLocation(): Result<LocationResult> {
        if (!hasLocationPermission()) {
            return Result.failure(SecurityException("Location permission required"))
        }

        return try {
            checkLocationSettings()
            
            val location = getCurrentLocation()
                ?: return Result.failure(Exception("Could not get location. Ensure GPS is on."))

            val locationResult = reverseGeocode(location.latitude, location.longitude)
            Result.success(locationResult)
        } catch (e: ResolvableApiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Suppress("MissingPermission")
    private suspend fun getCurrentLocation(): Location? =
        suspendCancellableCoroutine { continuation ->
            if (!hasLocationPermission()) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(5000) // 5 second timeout
                .build()

            fusedLocationClient.getCurrentLocation(request, null)
                .addOnSuccessListener { location ->
                    continuation.resume(location)
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }

    private suspend fun reverseGeocode(latitude: Double, longitude: Double): LocationResult = suspendCancellableCoroutine { continuation ->
        val geocoder = Geocoder(context, Locale.getDefault())

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<android.location.Address>) {
                        if (addresses.isNotEmpty()) {
                            val addr = addresses[0]
                            continuation.resume(LocationResult(
                                country = addr.countryName ?: "",
                                state = addr.adminArea ?: "",
                                latitude = latitude,
                                longitude = longitude
                            ))
                        } else {
                            continuation.resumeWithException(Exception("Address not found"))
                        }
                    }
                    override fun onError(errorMessage: String?) {
                        continuation.resumeWithException(Exception(errorMessage ?: "Geocode error"))
                    }
                })
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    continuation.resume(LocationResult(
                        country = addr.countryName ?: "",
                        state = addr.adminArea ?: "",
                        latitude = latitude,
                        longitude = longitude
                    ))
                } else {
                    continuation.resumeWithException(Exception("Address not found"))
                }
            }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }
}
