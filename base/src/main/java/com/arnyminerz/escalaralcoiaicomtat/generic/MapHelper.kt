package com.arnyminerz.escalaralcoiaicomtat.generic

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.fragment.app.FragmentActivity
import com.arnyminerz.escalaralcoiaicomtat.R
import com.arnyminerz.escalaralcoiaicomtat.activity.AREAS
import com.arnyminerz.escalaralcoiaicomtat.activity.MapsActivity
import com.arnyminerz.escalaralcoiaicomtat.activity.MapsActivity.Companion.KML_ADDRESS_BUNDLE_EXTRA
import com.arnyminerz.escalaralcoiaicomtat.data.climb.data.DataClass
import com.arnyminerz.escalaralcoiaicomtat.data.climb.data.find
import com.arnyminerz.escalaralcoiaicomtat.data.map.*
import com.arnyminerz.escalaralcoiaicomtat.databinding.DialogMapMarkerBinding
import com.arnyminerz.escalaralcoiaicomtat.exception.MissingPermissionException
import com.arnyminerz.escalaralcoiaicomtat.exception.NoInternetAccessException
import com.arnyminerz.escalaralcoiaicomtat.generic.extension.toUri
import com.arnyminerz.escalaralcoiaicomtat.network.base.ConnectivityProvider
import com.arnyminerz.escalaralcoiaicomtat.view.visibility
import com.bumptech.glide.Glide
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdate
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.FillManager
import com.mapbox.mapboxsdk.plugins.annotation.LineManager
import com.mapbox.mapboxsdk.plugins.annotation.Symbol
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.Serializable

class MapHelper(private val mapView: MapView) {
    companion object {
        @ExperimentalUnsignedTypes
        fun getTarget(marker: Symbol): DataClass<*, *>? {
            Timber.d("Getting marker's title...")
            val title = marker.getWindow().title
            Timber.v("Searching in ${AREAS.size} cached areas...")
            for (area in AREAS)
                if (area.displayName.equals(title, true))
                    return area
                else if (area.isNotEmpty())
                    for (zone in area)
                        if (zone.displayName.equals(title, true))
                            return zone
                        else if (zone.isNotEmpty())
                            for (sector in zone)
                                if (sector.displayName.equals(title, true))
                                    return sector

            Timber.w("Could not find targeted data class")
            return null
        }

        private fun getImageUrl(description: String?): String? {
            if (description == null || description.isEmpty()) return null

            if (description.startsWith("<img")) {
                val linkPos = description.indexOf("https://")
                val urlFirstPart = description.substring(linkPos) // This takes from the first "
                return urlFirstPart.substring(
                    0,
                    urlFirstPart.indexOf('"')
                ) // This from the previous to the next
            }

            return null
        }
    }

    private var map: MapboxMap? = null
    var style: Style? = null
        private set
    private var symbolManager: SymbolManager? = null
    private var fillManager: FillManager? = null
    private var lineManager: LineManager? = null

    private var loadedKMLAddress: String? = null

    private var startingPosition: LatLng = LatLng(-52.6885, -70.1395)
    private var startingZoom: Double = 2.0

    private val markers = arrayListOf<GeoMarker>()
    private val geometries = arrayListOf<GeoGeometry>()

    private val symbolClickListeners = arrayListOf<Symbol.() -> Boolean>()

    fun onCreate(savedInstanceState: Bundle?) = mapView.onCreate(savedInstanceState)

    fun onStart() = mapView.onStart()
    fun onResume() = mapView.onResume()
    fun onPause() = mapView.onPause()
    fun onStop() = mapView.onStop()
    fun onSaveInstanceState(outState: Bundle) = mapView.onSaveInstanceState(outState)
    fun onLowMemory() = mapView.onLowMemory()
    fun onDestroy() = mapView.onDestroy()

    fun withStartingPosition(startingPosition: LatLng?, zoom: Double = 2.0): MapHelper {
        if (startingPosition.isNotNull())
            this.startingPosition = startingPosition!!
        this.startingZoom = zoom
        return this
    }

    fun loadMap(
        callback: MapHelper.(mapView: MapView, map: MapboxMap, style: Style) -> Unit
    ): MapHelper {
        mapView.getMapAsync { map ->
            map.setStyle(Style.SATELLITE) { style ->
                this.map = map
                this.style = style

                symbolManager = SymbolManager(mapView, map, style)
                fillManager = FillManager(mapView, map, style)
                lineManager = LineManager(mapView, map, style)

                symbolManager!!.iconAllowOverlap = false
                symbolManager!!.addClickListener {
                    var anyFalse = false
                    for (list in symbolClickListeners)
                        if (!list(it))
                            anyFalse = true
                    !anyFalse
                }

                map.moveCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(startingPosition)
                            .zoom(startingZoom)
                            .build()
                    )
                )

                callback(this, mapView, map, style)
            }
        }

        return this
    }

    /**
     * Loads the KML address. Should be called asyncronously.
     * @throws FileNotFoundException When the KMZ file could not be found
     * @throws NoInternetAccessException When no Internet access was detected
     * @throws MapNotInitializedException If this function is called before loadMap
     * @see loadMap
     * @see MapFeatures
     * @author Arnau Mora
     * @return A MapFeatures object with all the loaded data
     */
    @Throws(FileNotFoundException::class, NoInternetAccessException::class)
    @ExperimentalUnsignedTypes
    fun loadKML(
        activity: FragmentActivity,
        kmlAddress: String?,
        networkState: ConnectivityProvider.NetworkState,
        addToMap: Boolean = true
    ): MapFeatures {
        if (map == null || style == null)
            throw MapNotInitializedException("Map not initialized. Please run loadMap before this")

        val loader = KMLLoader(kmlAddress, null)
        val result = loader.load(activity, map!!, style!!, networkState)
        activity.runOnUiThread {
            Timber.v("Loaded kml. Loading managers...")
            val symbolManager = SymbolManager(mapView, map!!, style!!)
            val lineManager = LineManager(mapView, map!!, style!!)
            val fillManager = FillManager(mapView, map!!, style!!)

            Timber.v("Loading features...")
            if (addToMap) with(result) {
                Timber.v("  Loading ${markers.size} markers...")
                markers.addToMap(activity, symbolManager)
                Timber.v("  Loading ${polygons.size} polygons...")
                polygons.addToMap(fillManager, lineManager)
                Timber.v("  Loading ${polylines.size} polylines...")
                polylines.addToMap(fillManager, lineManager)
            }

            loadedKMLAddress = kmlAddress
        }
        return MapFeatures(result.markers, result.polylines, result.polygons)
    }

    @ExperimentalUnsignedTypes
    fun showMapsActivity(activity: FragmentActivity) {
        if (loadedKMLAddress.isNull()) throw MapAnyDataToLoadException("Map doesn't have any loaded data. You may run loadKML, for example.")

        Timber.v("Launching MapsActivity from KML \"$loadedKMLAddress\"")
        activity.startActivity(
            Intent(activity, MapsActivity::class.java)
                .putExtra(
                    KML_ADDRESS_BUNDLE_EXTRA,
                    loadedKMLAddress!!
                )
        )
    }

    /**
     * Moves the camera position
     * @param position The target position
     * @param zoom The target zoomo
     * @param animate If the movement should be animated
     * @author Arnau Mora
     */
    fun move(position: LatLng, zoom: Double, animate: Boolean = true) {
        if (map == null)
            throw MapNotInitializedException("Map not initialized. Please run loadMap before this")

        move(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(position)
                    .zoom(zoom)
                    .build()
            ),
            animate
        )
    }

    /**
     * Moves the camera position
     * @param update The movement to make
     * @param animate If the movement should be animated
     * @author Arnau Mora
     */
    private fun move(update: CameraUpdate, animate: Boolean = true) {
        if (map == null)
            throw MapNotInitializedException("Map not initialized. Please run loadMap before this")

        if (animate)
            map?.animateCamera(update)
        else
            map?.moveCamera(update)
    }

    /**
     * Enables the current location pointer. Requires the location permission to be granted
     * @param context The context to call from
     * @param cameraMode The camera mode to set
     * @param renderMode The pointer render mode to set
     * @author Arnau Mora
     * @see CameraMode
     * @see RenderMode
     * @see PermissionsManager
     * @throws MissingPermissionException If the location permission is not granted
     */
    @SuppressLint("MissingPermission")
    @Throws(MissingPermissionException::class)
    fun enableLocationComponent(
        context: Context,
        cameraMode: Int = CameraMode.TRACKING,
        renderMode: Int = RenderMode.COMPASS
    ) {
        if (map == null || style == null || !style!!.isFullyLoaded)
            throw MapNotInitializedException("Map not initialized. Please run loadMap before this")

        if (!PermissionsManager.areLocationPermissionsGranted(context))
            throw MissingPermissionException("Location permission not granted")

        map!!.locationComponent.apply {
            activateLocationComponent(
                LocationComponentActivationOptions.builder(context, style!!).build()
            )
            isLocationComponentEnabled = true
            this.cameraMode = cameraMode
            this.renderMode = renderMode
        }
    }

    /**
     * Adds a click listener for a symbol
     * @param call What to call on click
     * @throws MapNotInitializedException If the map has not been initialized
     */
    @Throws(MapNotInitializedException::class)
    fun addSymbolClickListener(call: Symbol.() -> Boolean) {
        if (map == null || style == null)
            throw MapNotInitializedException("Map not initialized. Please run loadMap before this")

        symbolClickListeners.add(call)
    }

    /**
     * Adds a marker to the map
     * @param markers The markers to add
     * @see GeoMarker
     * @throws MapNotInitializedException If the map has not been initialized
     */
    @Throws(MapNotInitializedException::class)
    fun add(vararg markers: GeoMarker) {
        for (marker in markers)
            this.markers.add(marker)
    }

    /**
     * Adds geometries to the map
     * @param geometries The geometries to add
     * @see GeoGeometry
     * @throws MapNotInitializedException If the map has not been initialized
     */
    @Throws(MapNotInitializedException::class)
    fun add(vararg geometries: GeoGeometry) {
        for (geometry in geometries)
            this.geometries.add(geometry)
    }

    /**
     * Makes effective all the additions to the map through the add methods
     * @param context The context to call from
     * @throws MapNotInitializedException If the map has not been initialized
     */
    @Throws(MapNotInitializedException::class)
    fun display(context: Context) {
        if (symbolManager == null || fillManager == null || lineManager == null)
            throw MapNotInitializedException("Map not initialized. Please run loadMap before this")

        for (marker in markers)
            marker.addToMap(context, symbolManager!!)
        for (geometry in geometries)
            geometry.addToMap(fillManager!!, lineManager!!)
    }

    /**
     * Centers all the contents into the map window
     * @param padding Padding added to the bounds
     * @param animate If the movement should be animated
     * @throws MapNotInitializedException If the map has not been initialized
     */
    @Throws(MapNotInitializedException::class)
    fun center(padding: Int = 11, animate: Boolean = true) {
        if (markers.isEmpty())
            return

        if (symbolManager == null || fillManager == null || lineManager == null)
            throw MapNotInitializedException("Map not initialized. Please run loadMap before this")

        val points = arrayListOf<LatLng>()
        for (marker in markers)
            points.add(marker.position.toLatLng())
        for (geometry in geometries)
            points.addAll(geometry.points)

        if (markers.size == 1)
            move(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder().target(markers.first().position.toLatLng()).build()
                )
            )
        else {
            val boundsBuilder = LatLngBounds.Builder()
            for (marker in markers)
                boundsBuilder.include(marker.position.toLatLng())

            move(
                CameraUpdateFactory.newLatLngBounds(
                    boundsBuilder.build(),
                    padding
                ), animate
            )
        }
    }

    @ExperimentalUnsignedTypes
    fun mapsActivityIntent(context: Context): Intent =
        Intent(context, MapsActivity::class.java).apply {
            val mapData = arrayListOf<Serializable>()
            for (zm in markers)
                zm.let { zoneMarker ->
                    Timber.d("  Adding position [${zoneMarker.position.latitude}, ${zoneMarker.position.longitude}]")
                    mapData.add(zoneMarker as Serializable)
                }
            putExtra(MapsActivity.MAP_DATA_BUNDLE_EXTRA, mapData)
        }

    @ExperimentalUnsignedTypes
    fun infoCard(
        context: Context,
        marker: Symbol,
        binding: DialogMapMarkerBinding
    ): MarkerWindow {
        val latLng = marker.latLng

        val anim =
            AnimationUtils.loadAnimation(context, R.anim.enter_bottom)
        anim.duration = 500
        anim.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationRepeat(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                binding.mapInfoCardView.visibility = View.VISIBLE
            }

            override fun onAnimationStart(animation: Animation?) {
                binding.mapInfoCardView.visibility = View.VISIBLE
            }
        })
        binding.mapInfoCardView.startAnimation(anim)

        val window = marker.getWindow()
        val title = window.title
        val description = window.message
        val iwdc = getTarget(marker) // Info Window Data Class
        val dcSearch = iwdc?.let { AREAS.find(it) }

        Timber.v("Marker title: $title")
        Timber.v("Marker description: $description")

        binding.mapInfoTextView.text = title

        val imageUrl = getImageUrl(description)
        if (imageUrl == null)
            binding.mapDescTextView.text = description
        else
            Glide.with(context)
                .load(imageUrl)
                .into(binding.mapInfoImageView)

        visibility(binding.fabEnter, iwdc != null && dcSearch?.isEmpty() == false)
        visibility(binding.mapInfoImageView, imageUrl != null)
        visibility(binding.mapDescTextView, imageUrl == null)

        val gmmIntentUri = latLng.toUri(true, title)
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            .setPackage("com.google.android.apps.maps")
        val mapsAvailable = mapIntent.resolveActivity(context.packageManager) != null
        binding.fabMaps.visibility(mapsAvailable)
        if (mapsAvailable)
            binding.fabMaps.setOnClickListener {
                context.startActivity(mapIntent)
            }

        if (iwdc != null && dcSearch?.isEmpty() == false)
            binding.fabEnter.setOnClickListener {
                Timber.v("Searching for info window ${iwdc.namespace}:${iwdc.id}")
                if (!dcSearch.launchActivity(context))
                    context.toast(R.string.toast_error_internal)
            }

        return MarkerWindow(context, marker, binding)
    }
}

class MapNotInitializedException(message: String) : Exception(message)
class MapAnyDataToLoadException(message: String) : Exception(message)
data class MarkerWindow(
    val context: Context,
    val marker: Symbol,
    val binding: DialogMapMarkerBinding
)

fun MarkerWindow.hide() {
    val anim = AnimationUtils.loadAnimation(context, R.anim.exit_bottom)
    anim.duration = 500
    anim.setAnimationListener(object : Animation.AnimationListener {
        override fun onAnimationRepeat(animation: Animation?) {}

        override fun onAnimationEnd(animation: Animation?) {
            binding.mapInfoCardView.visibility = View.GONE
        }

        override fun onAnimationStart(animation: Animation?) {
            binding.mapInfoCardView.visibility = View.VISIBLE
        }
    })
    binding.mapInfoCardView.startAnimation(anim)
}