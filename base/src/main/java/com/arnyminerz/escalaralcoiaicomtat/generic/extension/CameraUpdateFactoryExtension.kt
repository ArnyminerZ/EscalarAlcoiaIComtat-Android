package com.arnyminerz.escalaralcoiaicomtat.generic.extension

import com.arnyminerz.escalaralcoiaicomtat.generic.isNotNull
import com.google.android.libraries.maps.CameraUpdate
import com.google.android.libraries.maps.CameraUpdateFactory
import com.google.android.libraries.maps.model.LatLng
import com.google.android.libraries.maps.model.LatLngBounds

fun newLatLngBounds(points: ArrayList<LatLng>, padding: Int): CameraUpdate? {
    if (points.size < 2)
        return if (points.size == 1)
            CameraUpdateFactory.newLatLng(points.first())
        else null
    val boundsBuilder = LatLngBounds.Builder()
    for (point in points)
        if (point.latitude.isNotNull() && point.longitude.isNotNull())
            boundsBuilder.include(point)
    val bounds = boundsBuilder.build()
    return CameraUpdateFactory.newLatLngBounds(bounds, padding)
}