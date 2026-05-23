package com.pralayakaveri.medisave.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.google.android.libraries.places.api.net.SearchByTextRequest
import com.pralayakaveri.medisave.model.Pharmacy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.*

sealed class MapUiState {
    object Idle : MapUiState()
    object Loading : MapUiState()
    data class Success(val pharmacies: List<Pharmacy>, val isFallback: Boolean = false) : MapUiState()
    data class Error(val message: String) : MapUiState()
}

class MapViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Idle)
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _userLocation = MutableStateFlow<LatLng?>(null)
    val userLocation: StateFlow<LatLng?> = _userLocation.asStateFlow()

    fun updateLocation(location: LatLng, context: Context) {
        _userLocation.value = location
        fetchNearbyPharmacies(location, context)
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun fetchNearbyPharmacies(location: LatLng, context: Context, isFallback: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = MapUiState.Loading
            
            val placesClient = Places.createClient(context)

            // Define the fields to return
            val placeFields = listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.ADDRESS,
                Place.Field.LAT_LNG,
                Place.Field.RATING,
                Place.Field.OPENING_HOURS
            )

            // Create CircularBounds for SearchNearby
            val circle = CircularBounds.newInstance(location, 5000.0) // 5km radius

            // Build the SearchNearbyRequest
            val searchNearbyRequest = SearchNearbyRequest.builder(circle, placeFields)
                .setIncludedTypes(listOf("pharmacy"))
                .setMaxResultCount(15)
                .build()

            placesClient.searchNearby(searchNearbyRequest)
                .addOnSuccessListener { response ->
                    val pharmacies = response.places.map { place ->
                        Pharmacy(
                            id = place.id ?: "",
                            name = place.name ?: "Unknown Pharmacy",
                            address = place.address ?: "No address available",
                            distance = place.latLng?.let { calculateDistance(location, it) } ?: "",
                            isOpen = place.isOpen ?: true,
                            location = place.latLng ?: location,
                            rating = place.rating
                        )
                    }
                    _uiState.value = MapUiState.Success(pharmacies, isFallback = isFallback)
                }
                .addOnFailureListener { exception ->
                    Log.e("MapViewModel", "Error fetching pharmacies: ${exception.message}", exception)
                    _uiState.value = MapUiState.Error("Failed to load pharmacies. Check API key and quota.")
                }
        }
    }

    fun searchPharmaciesByText(query: String, location: LatLng, context: Context, fallbackToNearby: Boolean = true) {
        viewModelScope.launch {
            _uiState.value = MapUiState.Loading
            
            val placesClient = Places.createClient(context)
            val placeFields = listOf(
                Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, 
                Place.Field.LAT_LNG, Place.Field.RATING, Place.Field.OPENING_HOURS
            )

            val searchByTextRequest = SearchByTextRequest.builder(query, placeFields)
                .setLocationBias(CircularBounds.newInstance(location, 5000.0))
                .setMaxResultCount(15)
                .build()

            placesClient.searchByText(searchByTextRequest)
                .addOnSuccessListener { response ->
                    val pharmacies = response.places.map { place ->
                        Pharmacy(
                            id = place.id ?: "",
                            name = place.name ?: "Unknown Pharmacy",
                            address = place.address ?: "No address available",
                            distance = place.latLng?.let { calculateDistance(location, it) } ?: "",
                            isOpen = place.isOpen ?: true,
                            location = place.latLng ?: location,
                            rating = place.rating
                        )
                    }
                    if (pharmacies.isEmpty() && fallbackToNearby) {
                        _searchQuery.value = "Pharmacy near me"
                        fetchNearbyPharmacies(location, context, isFallback = true)
                    } else {
                        _uiState.value = MapUiState.Success(pharmacies, isFallback = false)
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("MapViewModel", "Error fetching pharmacies by text: ${exception.message}", exception)
                    if (fallbackToNearby) {
                        _searchQuery.value = "Pharmacy near me"
                        fetchNearbyPharmacies(location, context, isFallback = true)
                    } else {
                        _uiState.value = MapUiState.Error("Search failed. Try expanding your search area.")
                    }
                }
        }
    }

    private fun calculateDistance(start: LatLng, end: LatLng): String {
        val radius = 6371 // Earth radius in km
        val dLat = Math.toRadians(end.latitude - start.latitude)
        val dLon = Math.toRadians(end.longitude - start.longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(start.latitude)) * cos(Math.toRadians(end.latitude)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distance = radius * c
        return String.format("%.1f km", distance)
    }
}
