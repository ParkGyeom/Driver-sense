package kkam.app

import android.app.Activity
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

        TILE_LABELS.forEach(::addTile)
        player.fileNames().forEach(::addScenarioButton)
        render()
    }

    override fun onDestroy() {
        player.stop()
        speaker.release()
        super.onDestroy()
    }

    private fun play(fileName: String) {
        speaker.stop()
        engine = Engine()
        state = state.resetDrive()
        render()
        highlight(fileName)
        player.play(fileName, onFrame = ::onFrame, onEnd = { highlight(null) })
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
        message.text = state.message.ifEmpty { "시나리오를 고르면 재생을 시작해요" }
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

    private fun addScenarioButton(fileName: String) {
        val button = layoutInflater.inflate(R.layout.scenario_button, view.scenarios, false) as Button
        button.text = fileName.removeSuffix(".json").replace('_', ' ')
        button.setOnClickListener { play(fileName) }
        buttons[fileName] = button
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
        val TILE_LABELS = listOf("속도", "핸들 각도", "깜빡이", "기어", "내비 TBT", "전방 신호")
    }
}
