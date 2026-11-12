package kkam.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.NaverMapSdk
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PathOverlay
import kkam.engine.model.SignalFrame
import kotlin.math.roundToInt

/**
 * 경로 데모의 앱 안 지도 (네이버 지도 SDK). 주행 화면일 때 앱 칸 전체(routeMapPanel)에 그린다.
 * 경로선, 목적지, 차 위치를 보여주고, 진행 방향이 화면 위로 오게 카메라를 돌린다 (내비처럼).
 * 주행 화면이라 터치로 지도를 움직이지 못하게 막는다 (기획서 5.4: 주행 중 터치 조작 없음).
 *
 * 키(NAVER_MAP_CLIENT_ID)는 local.properties에서 BuildConfig로 들어온다. 키가 없으면 지도 뷰를 만들지 않고
 * 속도와 안내 카드만 보인다. MapView는 화면 생명주기를 그대로 받아야 해서 MainActivity가 on* 함수를 부른다.
 */
class RouteMap(private val activity: Activity) {
    private val panel: FrameLayout = activity.findViewById(R.id.routeMapPanel)
    private val speed: TextView = activity.findViewById(R.id.routeMapSpeed)
    /** 지도 위쪽 가운데 안내 카드. 내비 위 팝업과 같은 모양. */
    private val popupCard = MapPopupCard(panel)
    /** 키가 없으면 null. 그때는 지도 없이 속도와 카드만 보인다. */
    private val mapView: MapView?
    /** 지도가 준비되면(getMapAsync) 채운다. 그 전에 온 render는 마지막 값만 기억했다가 준비되면 그린다. */
    private var naverMap: NaverMap? = null
    private val line = PathOverlay()
    private val destination = Marker()
    /** 지금 그린 경로. 바뀌면 경로선과 목적지를 다시 놓는다. */
    private var drawnPath: List<LatLon>? = null
    private var pendingPath: List<LatLon> = emptyList()
    private var pendingFrame: SignalFrame? = null

    init {
        val clientId = BuildConfig.NAVER_MAP_CLIENT_ID
        mapView = if (clientId.isEmpty()) {
            Log.w(TAG, "NAVER_MAP_CLIENT_ID 없음: 경로 데모 지도를 띄우지 않는다")
            null
        } else {
            NaverMapSdk.getInstance(activity).apply {
                // 새 NCP Maps 콘솔에서 발급한 키는 NcpKeyClient로 인증한다 (예전 NaverCloudPlatformClient 아님).
                client = NaverMapSdk.NcpKeyClient(clientId)
                setOnAuthFailedListener { e -> Log.e(TAG, "네이버 지도 인증 실패: ${e.errorCode} ${e.message}") }
            }
            MapView(activity)
        }
        mapView?.let { view ->
            view.onCreate(null)
            panel.addView(view, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            view.getMapAsync(::onMapReady)
        }
        line.apply {
            color = activity.getColor(R.color.ok)
            outlineWidth = 0
            width = activity.dp(LINE_WIDTH_DP)
        }
        destination.icon = OverlayImage.fromBitmap(bitmapOf(R.drawable.ic_kk_destination))
    }

    private fun onMapReady(map: NaverMap) {
        naverMap = map
        map.uiSettings.apply {
            // 주행 화면에서는 지도를 손으로 움직이지 않는다
            setAllGesturesEnabled(false)
            isZoomControlEnabled = false
            isScaleBarEnabled = false
            isCompassEnabled = false
            // 네이버 로고는 가리면 안 된다. 왼쪽 아래 속도 표시와 겹치지 않게 오른쪽 아래로 옮긴다.
            logoGravity = Gravity.BOTTOM or Gravity.END
        }
        map.locationOverlay.apply {
            icon = OverlayImage.fromBitmap(bitmapOf(R.drawable.ic_kk_car_arrow))
            // 기본 원(정확도 표시)은 데모 차에는 필요 없다
            circleRadius = 0
        }
        render(pendingPath, pendingFrame, null)
    }

    /** 경로 좌표가 있고 주행 화면일 때만 보인다. */
    fun show(visible: Boolean) {
        panel.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** 경로선과 차 위치, 안내 카드를 그린다. 차가 가운데, 진행 방향이 위. */
    fun render(path: List<LatLon>, frame: SignalFrame?, popup: Popup?) {
        popupCard.show(popup)
        if (frame != null) speed.text = activity.getString(R.string.route_map_speed, frame.speedKph.roundToInt())
        pendingPath = path
        pendingFrame = frame
        val map = naverMap ?: return
        if (path != drawnPath) {
            drawnPath = path
            // PathOverlay는 좌표가 2개 이상이어야 한다
            if (path.size >= 2) {
                line.coords = path.map { LatLng(it.lat, it.lon) }
                line.map = map
            } else {
                line.map = null
            }
            path.lastOrNull()?.let {
                destination.position = LatLng(it.lat, it.lon)
                destination.map = map
            }
            path.firstOrNull()?.let { map.cameraPosition = CameraPosition(LatLng(it.lat, it.lon), ZOOM) }
        }
        if (frame != null) {
            val here = LatLng(frame.lat, frame.lon)
            map.locationOverlay.apply {
                position = here
                bearing = frame.headingDeg.toFloat()
                isVisible = true
            }
            // bearing을 진행 방향으로 두면 진행 방향이 화면 위가 된다. 기울기는 0 (위에서 내려다봄).
            map.cameraPosition = CameraPosition(here, ZOOM, 0.0, frame.headingDeg)
        }
    }

    // MapView 생명주기. MainActivity의 같은 이름 함수에서 부른다.
    fun onStart() = mapView?.onStart()
    fun onResume() = mapView?.onResume()
    fun onPause() = mapView?.onPause()
    fun onStop() = mapView?.onStop()
    fun onSaveInstanceState(outState: Bundle) = mapView?.onSaveInstanceState(outState)
    fun onLowMemory() = mapView?.onLowMemory()
    fun onDestroy() = mapView?.onDestroy()

    /** 벡터 drawable을 지도 아이콘(OverlayImage)용 비트맵으로 바꾼다. */
    private fun bitmapOf(resId: Int): Bitmap {
        val drawable = requireNotNull(activity.getDrawable(resId))
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private companion object {
        const val TAG = "RouteMap"
        const val ZOOM = 17.0
        const val LINE_WIDTH_DP = 10
    }
}
