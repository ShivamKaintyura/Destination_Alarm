package com.destinationalarm.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.destinationalarm.app.model.Fix
import com.destinationalarm.app.model.Trip
import com.destinationalarm.app.util.Geo
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/dark"

private const val SRC_ACCURACY = "da-accuracy"
private const val SRC_ROUTE = "da-route"
private const val SRC_ROUTE_GLOW = "da-route-glow"
private const val SRC_RADIUS = "da-radius"
private const val SRC_DEST = "da-dest"
private const val SRC_DEST_HALO = "da-dest-halo"
private const val SRC_USER = "da-user"

@Composable
fun MapPane(
    modifier: Modifier = Modifier,
    dest: Trip?,
    fix: Fix?,
    route: List<Pair<Double, Double>>,
    recenterTick: Int,
    pickerActive: Boolean = false,
    cameraTarget: Pair<Double, Double>? = null,
    cameraTick: Int = 0,
    onMapClick: (Double, Double) -> Unit,
    onCenterChanged: (Double, Double) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val mapView = remember {
        MapView(context).apply { onCreate(null) }
    }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var centered by remember { mutableStateOf(false) }
    val mapClick by rememberUpdatedState(onMapClick)
    val picking by rememberUpdatedState(pickerActive)
    val centerChanged by rememberUpdatedState(onCenterChanged)

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            runCatching { mapView.onDestroy() }
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                installLayers(style)
                mapRef = map
            }
            map.addOnMapClickListener { latLng ->
                mapClick(latLng.latitude, latLng.longitude)
                true
            }
            map.addOnCameraIdleListener {
                if (picking) {
                    val t = map.cameraPosition.target
                    if (t != null) centerChanged(t.latitude, t.longitude)
                }
            }
        }
    }

    LaunchedEffect(dest, fix, route, mapRef) {
        val map = mapRef ?: return@LaunchedEffect
        updateRadius(map, dest)
        updateDest(map, dest)
        updateUser(map, fix)
        updateRoute(map, route)

        if (!centered && fix != null && !picking && dest == null) {
            centered = true
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(fix.lat, fix.lon), 14.5)
            )
        }
    }

    LaunchedEffect(recenterTick) {
        val map = mapRef ?: return@LaunchedEffect
        val f = fix ?: return@LaunchedEffect
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(f.lat, f.lon), 15.5))
    }

    LaunchedEffect(cameraTick, mapRef) {
        val map = mapRef ?: return@LaunchedEffect
        val target = cameraTarget ?: return@LaunchedEffect
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(target.first, target.second), 16.5)
        )
    }

    // Fly to a newly chosen destination (one-tap saved places, restored trip).
    LaunchedEffect(dest?.lat, dest?.lon, mapRef) {
        val map = mapRef ?: return@LaunchedEffect
        val d = dest ?: return@LaunchedEffect
        if (!picking) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(d.lat, d.lon), 15.5))
        }
    }
}

private fun installLayers(style: Style) {
    style.addSource(GeoJsonSource(SRC_ACCURACY, Geo.emptyCollection()))
    style.addLayer(
        FillLayer("da-accuracy-fill", SRC_ACCURACY).withProperties(
            PropertyFactory.fillColor("#4A9EFF"),
            PropertyFactory.fillOpacity(0.10f)
        )
    )
    style.addLayer(
        LineLayer("da-accuracy-line", SRC_ACCURACY).withProperties(
            PropertyFactory.lineColor("#4A9EFF"),
            PropertyFactory.lineOpacity(0.35f),
            PropertyFactory.lineWidth(1f)
        )
    )

    style.addSource(GeoJsonSource(SRC_ROUTE_GLOW, Geo.emptyCollection()))
    style.addLayer(
        LineLayer("da-route-glow-line", SRC_ROUTE_GLOW).withProperties(
            PropertyFactory.lineColor("#4A9EFF"),
            PropertyFactory.lineWidth(14f),
            PropertyFactory.lineOpacity(0.18f),
            PropertyFactory.lineBlur(6f),
            PropertyFactory.lineCap("round"),
            PropertyFactory.lineJoin("round")
        )
    )

    style.addSource(GeoJsonSource(SRC_ROUTE, Geo.emptyCollection()))
    style.addLayer(
        LineLayer("da-route-line", SRC_ROUTE).withProperties(
            PropertyFactory.lineColor("#6BB6FF"),
            PropertyFactory.lineWidth(5f),
            PropertyFactory.lineOpacity(0.95f),
            PropertyFactory.lineCap("round"),
            PropertyFactory.lineJoin("round")
        )
    )

    style.addSource(GeoJsonSource(SRC_RADIUS, Geo.emptyCollection()))
    style.addLayer(
        FillLayer("da-radius-fill", SRC_RADIUS).withProperties(
            PropertyFactory.fillColor("#3FB950"),
            PropertyFactory.fillOpacity(0.12f)
        )
    )
    style.addLayer(
        LineLayer("da-radius-line", SRC_RADIUS).withProperties(
            PropertyFactory.lineColor("#3FB950"),
            PropertyFactory.lineWidth(2f),
            PropertyFactory.lineOpacity(0.9f)
        )
    )

    style.addSource(GeoJsonSource(SRC_DEST_HALO, Geo.emptyCollection()))
    style.addLayer(
        CircleLayer("da-dest-halo", SRC_DEST_HALO).withProperties(
            PropertyFactory.circleColor("#F85149"),
            PropertyFactory.circleRadius(26f),
            PropertyFactory.circleOpacity(0.16f),
            PropertyFactory.circleBlur(0.4f)
        )
    )

    style.addSource(GeoJsonSource(SRC_DEST, Geo.emptyCollection()))
    style.addLayer(
        CircleLayer("da-dest-dot", SRC_DEST).withProperties(
            PropertyFactory.circleColor("#F85149"),
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f)
        )
    )

    style.addSource(GeoJsonSource(SRC_USER, Geo.emptyCollection()))
    style.addLayer(
        CircleLayer("da-user-dot", SRC_USER).withProperties(
            PropertyFactory.circleColor("#4A9EFF"),
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f)
        )
    )
}

private fun source(map: MapLibreMap, id: String): GeoJsonSource? =
    map.style?.getSourceAs<GeoJsonSource>(id)

private fun updateRadius(map: MapLibreMap, dest: Trip?) {
    val src = source(map, SRC_RADIUS) ?: return
    src.setGeoJson(
        if (dest == null) Geo.emptyCollection()
        else Geo.circleGeoJson(dest.lat, dest.lon, dest.radiusM.toDouble())
    )
}

private fun updateDest(map: MapLibreMap, dest: Trip?) {
    val src = source(map, SRC_DEST) ?: return
    src.setGeoJson(
        if (dest == null) Geo.emptyCollection()
        else Geo.pointGeoJson(dest.lat, dest.lon)
    )
    source(map, SRC_DEST_HALO)?.setGeoJson(
        if (dest == null) Geo.emptyCollection()
        else Geo.pointGeoJson(dest.lat, dest.lon)
    )
}

private fun updateUser(map: MapLibreMap, fix: Fix?) {
    val user = source(map, SRC_USER) ?: return
    val acc = source(map, SRC_ACCURACY) ?: return

    if (fix == null) {
        user.setGeoJson(Geo.emptyCollection())
        acc.setGeoJson(Geo.emptyCollection())
    } else {
        user.setGeoJson(Geo.pointGeoJson(fix.lat, fix.lon))
        acc.setGeoJson(Geo.circleGeoJson(fix.lat, fix.lon, fix.acc.toDouble().coerceAtLeast(15.0)))
    }
}

private fun updateRoute(map: MapLibreMap, route: List<Pair<Double, Double>>) {
    val geo = Geo.lineGeoJson(route)
    source(map, SRC_ROUTE)?.setGeoJson(geo)
    source(map, SRC_ROUTE_GLOW)?.setGeoJson(geo)
}
