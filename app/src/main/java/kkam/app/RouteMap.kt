package kkam.app

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import kkam.engine.model.SignalFrame
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/**
 * 경로 데모의 앱 안 지도 (osmdroid, OpenStreetMap 타일). 주행 화면 왼쪽 칸(routeMapPanel)에 그린다.
 * 경로선, 목적지, 차 위치를 보여주고, 진행 방향이 화면 위로 오게 지도를 돌린다 (내비처럼).
 * 주행 화면이라 터치로 지도를 움직이지 못하게 막는다 (기획서 5.4: 주행 중 터치 조작 없음).
 */
class RouteMap(private val activity: Activity) {
    private val panel: FrameLayout = activity.findViewById(R.id.routeMapPanel)
    private val map: MapView
    private val line = Polyline()
    private val car: Marker
    private val destination: Marker
    /** 지금 그린 경로. 바뀌면 경로선과 목적지를 다시 놓는다. */
    private var drawnPath: List<LatLon>? = null

    init {
        // 타일 서버 이용 규칙: 앱을 알 수 있는 User-Agent를 보낸다. 캐시는 앱 전용 폴더에 둔다.
        Configuration.getInstance().apply {
            userAgentValue = USER_AGENT
            osmdroidBasePath = File(activity.cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
        map = MapView(activity).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            controller.setZoom(ZOOM)
            // 주행 화면에서는 지도를 손으로 움직이지 않는다
            setOnTouchListener { _, _ -> true }
        }
        line.outlinePaint.apply {
            color = activity.getColor(R.color.ok)
            strokeWidth = activity.dp(LINE_WIDTH_DP).toFloat()
        }
        car = Marker(map).apply {
            icon = activity.getDrawable(R.drawable.ic_kk_car_arrow)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setInfoWindow(null)
        }
        destination = Marker(map).apply {
            icon = activity.getDrawable(R.drawable.ic_kk_destination)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setInfoWindow(null)
        }
        map.overlays.addAll(listOf(line, destination, car))
        panel.addView(map, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    /** 경로 좌표가 있고 주행 화면일 때만 보인다. */
    fun show(visible: Boolean) {
        panel.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) map.onResume() else map.onPause()
    }

    /** 경로선과 차 위치를 그린다. 차가 가운데, 진행 방향이 위. */
    fun render(path: List<LatLon>, frame: SignalFrame?) {
        if (path != drawnPath) {
            drawnPath = path
            line.setPoints(path.map { GeoPoint(it.lat, it.lon) })
            path.lastOrNull()?.let { destination.position = GeoPoint(it.lat, it.lon) }
            path.firstOrNull()?.let { map.controller.setCenter(GeoPoint(it.lat, it.lon)) }
        }
        if (frame != null) {
            val here = GeoPoint(frame.lat, frame.lon)
            car.position = here
            map.mapOrientation = -frame.headingDeg.toFloat()
            map.controller.setCenter(here)
        }
        map.invalidate()
    }

    fun release() {
        map.onDetach()
    }

    private companion object {
        const val USER_AGENT = "kkam.app/0.1 (Pleos Playground contest demo)"
        const val ZOOM = 17.5
        const val LINE_WIDTH_DP = 10
    }
}
