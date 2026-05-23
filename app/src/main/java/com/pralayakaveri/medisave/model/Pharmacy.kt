package com.pralayakaveri.medisave.model

import com.google.android.gms.maps.model.LatLng

data class Pharmacy(
    val id: String,
    val name: String,
    val address: String,
    val distance: String = "",
    val isOpen: Boolean = true,
    val location: LatLng,
    val rating: Double? = null
)
