package kkam.app

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kkam.engine.Engine
import kkam.engine.model.EngineOutput
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.SignalFrame
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kotlin.math.roundToInt

/**
 * 주행 화면(코치 카드)과 시연용 데모 패널을 띄운다.
 * 데모 패널(F9)은 debug 빌드에서만 열리고, 주행 화면 레이아웃과 따로 있다.
 */
class MainActivity : Activity() {
    private lateinit var player: ScenarioPlayer
    private lateinit var speaker: Speaker
    private lateinit var collector: SignalCollector
    private var live = false
    private var engine = Engine()
    private var state = CoachState()

    private val tiles = mutableMapOf<String, TextView>()
    private val buttons = mutableMapOf<String, Button>()
    private val view by lazy { Views(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        player = ScenarioPlayer(this)
        speaker = Speaker(this).apply { start() }
        collector = SignalCollector(this)

        TILE_LABELS.forEach(::addTile)
        addLiveButton()
        player.fileNames().forEach(::addScenarioButton)
        setUpDemoToggle()
        render()
    }

    /** 데모 패널은 debug 빌드에서만 열 수 있다. 릴리스 APK에는 여는 버튼이 없다. */
    private fun setUpDemoToggle() {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!debuggable) return
        view.demoToggle.visibility = View.VISIBLE
        view.demoToggle.background = rounded(getColor(R.color.surface), radiusDp = 999, strokeColor = getColor(R.color.line))
        view.demoToggle.setOnClickListener { showDemoPanel(view.demoPanel.visibility != View.VISIBLE) }
    }

    private fun showDemoPanel(show: Boolean) {
        view.demoPanel.visibility = if (show) View.VISIBLE else View.GONE
        view.demoToggle.setText(if (show) R.string.demo_close else R.string.demo_open)
    }

    // TODO(주행 제한, 4번): CarUxRestrictions는 여기에 붙인다.
    //  - onStart에서 Car.CAR_UX_RESTRICTION_SERVICE의 CarUxRestrictionsManager에 리스너를 등록하고 onStop에서 해제.
    //  - 제한 중(isRequiresDistractionOptimization)이면 데모 패널을 닫고 demoToggle을 숨긴다.
    //  - 제한 중에는 screenSlot을 주행 화면에서 다른 화면으로 바꾸지 않는다.
    //
    // TODO(다음 화면, 2·3번): 주행 리포트(기어 P), 진단 리포트, 습관 선택은 각각 screen_*.xml로 만들고
    //  activity_main의 screenSlot 안 내용을 갈아끼운다. 기어 P 전환은 onFrame의 frame.gear로 알 수 있다.

    override fun onDestroy() {
        player.stop()
        stopLive()
        speaker.release()
        super.onDestroy()
    }

    private fun play(fileName: String) {
        stopLive()
        speaker.stop()
        engine = Engine()
        state = state.resetDrive(fileName.substringBefore('_'))
        render()
        highlight(fileName)
        player.play(fileName, onFrame = ::onFrame, onEnd = { highlight(null) })
    }

    /** 실제 차량 신호로 주행을 시작한다. 위치와 속도는 런타임 권한이 필요하다. */
    private fun toggleLive() {
        if (live) {
            stopLive()
            highlight(null)
            return
        }
        val missing = LIVE_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startLive() else requestPermissions(missing.toTypedArray(), REQUEST_LIVE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LIVE && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startLive()
        }
    }

    private fun startLive() {
        player.stop()
        speaker.stop()
        engine = Engine()
        state = state.resetDrive(LIVE_LABEL)
        render()
        highlight(LIVE_LABEL)
        live = true
        collector.start(::onFrame)
    }

    private fun stopLive() {
        if (live) collector.stop()
        live = false
    }

    private fun onFrame(frame: SignalFrame) {
        val outputs = engine.onFrame(frame)
        outputs.forEach { Log.i(TAG, "${frame.t} $it") }
        outputs.filterIsInstance<Guide>().forEach { speaker.speak(it.text) }
        state = state.next(frame, outputs)
        showSignals(frame)
        render()
    }

    private fun showSignals(frame: SignalFrame) {
        tiles.getValue("속도").text = "${frame.speedKph.roundToInt()} km/h"
        tiles.getValue("핸들 각도").text = frame.steeringDeg?.let { "${it.roundToInt()}°" } ?: "-"
        tiles.getValue("깜빡이").text = frame.turnSignal.label()
        tiles.getValue("기어").text = frame.gear.name
        tiles.getValue("내비 TBT").text = frame.tbt?.let { "${it.turn.label()} ${it.distanceM} m" } ?: "-"
        tiles.getValue("전방 신호").text = frame.light?.phase?.name ?: "-"
    }

    /** 주행 화면을 그린다. 값은 모두 [CoachState]에서 온다. */
    private fun render() = with(view) {
        // 회전 안내 중이면 카드 전체를 상태 색으로 칠하고 글자는 어둡게, 대기 중이면 어두운 카드에 흐린 글자.
        val active = state.tone != Tone.IDLE
        val fill = getColor(
            when (state.tone) {
                Tone.IDLE -> R.color.surface
                Tone.WARN -> R.color.warn
                Tone.OK -> R.color.ok
            },
        )
        val ink = getColor(if (active) R.color.on_accent else R.color.muted)
        card.background = rounded(fill, radiusDp = 32, strokeColor = if (active) null else getColor(R.color.line))

        turnIcon.visibility = if (active) View.VISIBLE else View.GONE
        // 화살표는 좌회전 모양 하나를 뒤집어 쓴다. 방향은 turnLabel로 판단한다.
        turnIcon.scaleX = if (state.turnLabel == TurnType.RIGHT.label()) -1f else 1f
        turnIcon.imageTintList = ColorStateList.valueOf(ink)

        turn.text = state.turnLabel
        distance.text = distanceText(state.distanceLabel)
        badge.text = state.badge
        signal.text = state.signalLabel
        listOf(turn, distance, badge, signal).forEach { it.setTextColor(ink) }
        listOf(distance, badge, signal).forEach { it.visibility = if (active) View.VISIBLE else View.GONE }

        message.text = state.message
        today.text = "${state.kept} / ${state.total}"
        progress.max = state.total.coerceAtLeast(1)
        progress.progress = state.kept
        logs.text = state.logs.takeLast(MAX_LOGS).reversed().joinToString("\n").ifEmpty { getString(R.string.demo_no_logs) }
    }

    /** "60 m"에서 단위만 작게 줄여 숫자가 먼저 읽히게 한다. */
    private fun distanceText(label: String): CharSequence {
        val unitStart = label.lastIndexOf(' ')
        if (unitStart < 0) return label
        return SpannableString(label).apply {
            setSpan(RelativeSizeSpan(DISTANCE_UNIT_SCALE), unitStart, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun addTile(label: String) {
        val tile = layoutInflater.inflate(R.layout.signal_tile, view.tiles, false) as LinearLayout
        tile.layoutParams = GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED, 1f),
            GridLayout.spec(GridLayout.UNDEFINED, 1f),
        ).apply { width = 0; setMargins(dp(4), dp(4), dp(4), dp(4)) }
        tile.background = rounded(getColor(R.color.surface), radiusDp = 14)
        tile.findViewById<TextView>(R.id.tileLabel).text = label
        tiles[label] = tile.findViewById<TextView>(R.id.tileValue).also { it.text = "-" }
        view.tiles.addView(tile)
    }

    private fun addLiveButton() = addButton(LIVE_LABEL, "실제 차량 신호") { toggleLive() }

    private fun addScenarioButton(fileName: String) =
        addButton(fileName, fileName.removeSuffix(".json").replace('_', ' ')) { play(fileName) }

    private fun addButton(key: String, label: String, onClick: () -> Unit) {
        val button = layoutInflater.inflate(R.layout.scenario_button, view.scenarios, false) as Button
        button.text = label
        button.setOnClickListener { onClick() }
        buttons[key] = button
        view.scenarios.addView(button)
        highlight(null)
    }

    private fun highlight(playing: String?) {
        buttons.forEach { (name, button) ->
            val color = getColor(if (name == playing) R.color.demo_active else R.color.surface)
            button.backgroundTintList = ColorStateList.valueOf(color)
        }
    }

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        strokeColor?.let { setStroke(dp(2), it) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private fun TurnSignal.label(): String = when (this) {
        TurnSignal.OFF -> "꺼짐"
        TurnSignal.LEFT -> "좌 켜짐"
        TurnSignal.RIGHT -> "우 켜짐"
        TurnSignal.HAZARD -> "비상등"
    }

    /** 레이아웃 뷰를 한곳에 모은다. */
    private class Views(activity: Activity) {
        val demoPanel: View = activity.findViewById(R.id.demoPanel)
        val demoToggle: TextView = activity.findViewById(R.id.demoToggle)
        val scenarios: LinearLayout = activity.findViewById(R.id.scenarios)
        val tiles: GridLayout = activity.findViewById(R.id.tiles)
        val card: LinearLayout = activity.findViewById(R.id.card)
        val turnIcon: ImageView = activity.findViewById(R.id.turnIcon)
        val turn: TextView = activity.findViewById(R.id.turn)
        val distance: TextView = activity.findViewById(R.id.distance)
        val signal: TextView = activity.findViewById(R.id.signal)
        val badge: TextView = activity.findViewById(R.id.badge)
        val message: TextView = activity.findViewById(R.id.message)
        val today: TextView = activity.findViewById(R.id.today)
        val progress: android.widget.ProgressBar = activity.findViewById(R.id.progress)
        val logs: TextView = activity.findViewById(R.id.logs)
    }

    private companion object {
        const val TAG = "KkamCoach"
        const val MAX_LOGS = 12
        const val DISTANCE_UNIT_SCALE = 0.45f
        const val LIVE_LABEL = "실차"
        const val REQUEST_LIVE = 1
        val LIVE_PERMISSIONS = listOf("android.car.permission.CAR_SPEED", "pleos.car.permission.FUSED_LOCATION")
        val TILE_LABELS = listOf("속도", "핸들 각도", "깜빡이", "기어", "내비 TBT", "전방 신호")
    }
}
