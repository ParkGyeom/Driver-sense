package kkam.app

import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import kkam.engine.Engine
import kkam.engine.model.EngineOutput
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.SignalFrame
import kkam.engine.model.TurnSignal
import kotlin.math.roundToInt

/** PT 데모 화면 (F9). 왼쪽은 재생과 차량 신호, 오른쪽은 코치 카드다. */
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
        render()
    }

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

    private fun render() = with(view) {
        val accent = when (state.tone) {
            Tone.IDLE -> getColor(R.color.line)
            Tone.WARN -> getColor(R.color.warn)
            Tone.OK -> getColor(R.color.ok)
        }
        card.background = rounded(getColor(R.color.surface), radiusDp = 20, strokeColor = accent)
        turn.text = state.turnLabel
        distance.text = state.distanceLabel
        signal.text = state.signalLabel
        badge.text = state.badge
        badge.visibility = if (state.badge.isEmpty()) android.view.View.INVISIBLE else android.view.View.VISIBLE
        badge.background = rounded(accent, radiusDp = 999)
        message.text = state.message
        today.text = "${state.kept} / ${state.total}"
        progress.max = state.total.coerceAtLeast(1)
        progress.progress = state.kept
        logs.text = state.logs.takeLast(MAX_LOGS).reversed().joinToString("\n").ifEmpty { "아직 기록이 없어요" }
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
            val color = getColor(if (name == playing) R.color.ok else R.color.surface)
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
        val scenarios: LinearLayout = activity.findViewById(R.id.scenarios)
        val tiles: GridLayout = activity.findViewById(R.id.tiles)
        val card: LinearLayout = activity.findViewById(R.id.card)
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
        const val MAX_LOGS = 6
        const val LIVE_LABEL = "실차"
        const val REQUEST_LIVE = 1
        val LIVE_PERMISSIONS = listOf("android.car.permission.CAR_SPEED", "pleos.car.permission.FUSED_LOCATION")
        val TILE_LABELS = listOf("속도", "핸들 각도", "깜빡이", "기어", "내비 TBT", "전방 신호")
    }
}
