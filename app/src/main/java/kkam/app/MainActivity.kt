package kkam.app

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.IBinder
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.LawEntry
import kkam.engine.model.SignalFrame
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * 주행 화면(코치 카드)과 시연용 데모 패널을 띄운다.
 * 데모 패널(F9)은 debug 빌드에서만 열리고, 주행 화면 레이아웃과 따로 있다.
 * 재생, 신호 수집, 엔진, 음성은 [CoachService]의 [CoachSession]이 맡는다. 이 화면은 세션 상태를 그리기만 한다.
 * 그래서 내비가 앞에 와서 이 화면이 숨겨져도 안내는 계속된다.
 */
class MainActivity : Activity() {
    /** 서비스에 붙기 전에는 null. 붙으면 [onSessionChanged]로 상태를 받는다. */
    private var session: CoachSession? = null
    private val state: CoachState get() = session?.state ?: CoachState()
    private val sessionListener = CoachSession.Listener(::onSessionChanged)
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val connected = (binder as CoachService.LocalBinder).session
            session = connected
            connected.addListener(sessionListener)
            // 서비스가 먼저 돌고 있었으면 (내비를 보다가 돌아온 경우) 마지막 프레임으로 화면을 맞춘다.
            connected.lastFrame?.let { frame ->
                gear = frame.gear
                screen = nextScreen(screen, frame.gear)
                showSignals(frame)
            }
            highlight(connected.running)
            render()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            session = null
        }
    }
    /** 데모: 시나리오를 틀면 내비를 앞으로 띄운다 (내비 위 팝업 시연용). */
    private var naviAfterPlay = false
    /** 지금 screenSlot에 보이는 화면. 마지막 프레임의 기어로 정한다. */
    private var screen = Screen.DRIVE
    /** 마지막 프레임의 기어. 아직 프레임이 없으면 null. 진단·습관 선택은 P일 때만 연다. */
    private var gear: Gear? = null
    private lateinit var habitScreens: HabitScreens
    /** 진단 값. TODO(박겸 습관 인사이트, 12/1): 실제 계산 구현으로 바꾼다. */
    private val diagnosisSource: DiagnosisSource = DemoDiagnosisSource
    /** 리포트 카드를 마지막으로 그린 판정 목록. 같으면 다시 그리지 않는다. */
    private var reportDrawnFor: List<Event>? = null
    private val laws: List<LawEntry> by lazy { loadLaws() }

    private val tiles = mutableMapOf<String, TextView>()
    private val buttons = mutableMapOf<String, Button>()
    private val view by lazy { Views(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        TILE_LABELS.forEach(::addTile)
        setUpHabitScreens()
        addButton(DIAGNOSE_LABEL, getString(R.string.demo_open_diagnose)) { openDiagnoseParked() }
        addNaviButton()
        addLiveButton()
        ScenarioPlayer(this).fileNames().forEach(::addScenarioButton)
        setUpDemoToggle()
        setUpReport()
        render()

        CoachService.start(this)
        bindService(Intent(this, CoachService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    private fun setUpReport() = with(view) {
        listOf(reportSavedTile, reportKeptTile, reportMissedTile).forEach {
            it.background = rounded(getColor(R.color.surface), radiusDp = 24)
        }
        reportAsk.background = rounded(getColor(R.color.bg), radiusDp = 999, strokeColor = getColor(R.color.line))
    }

    /** 법규 룰 테이블은 루트 data/law_table.json을 assets로 바로 쓴다. */
    private fun loadLaws(): List<LawEntry> {
        val text = assets.open(LAW_TABLE_FILE).bufferedReader().use { it.readText() }
        return Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(LawEntry.serializer()), text)
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
    //  - 제한 중에는 screenSlot을 주행 화면에서 다른 화면으로 바꾸지 않는다 (onFrame의 screen 결정).

    /** 진단 리포트 → 습관 선택 → 주행 화면 (기획서 5.5 PT 흐름 1~2). */
    private fun setUpHabitScreens() {
        habitScreens = HabitScreens(
            activity = this,
            diagnosis = diagnosisSource.load(),
            onOpenHabit = { showParked(Screen.HABIT) },
            onBackToDiagnose = { showParked(Screen.DIAGNOSE) },
            onFinish = ::finishHabits,
        )
    }

    /** 데모 패널 버튼. 재생을 멈추고 기어 P로 세운 뒤 진단 리포트를 연다. */
    private fun openDiagnoseParked() {
        session?.stopAll()
        highlight(null)
        gear = Gear.P
        tiles.getValue("기어").text = Gear.P.name
        showParked(Screen.DIAGNOSE)
    }

    /** 기어 P가 아니면 열지 않고 주행 화면에 머문다 (기획서 5.4). */
    private fun showParked(target: Screen) {
        screen = openScreen(target, gear)
        render()
    }

    private fun finishHabits(selection: HabitSelection) {
        // TODO(엔진 모드 입력, spec v0.2 · 박겸 11/16): 교정(CORRECT) 모드와 고른 습관을 엔진에 넘긴다.
        //  지금은 엔진에 넘기지 않고, 주행 화면 한 줄 문구에만 보여준다.
        session?.showMessage(selection.summary())
        screen = Screen.DRIVE
        render()
    }

    /** 화면만 닫는다. 주행 중이면 서비스가 계속 돌고, 멈춰 있으면 서비스도 끝난다. */
    override fun onDestroy() {
        session?.removeListener(sessionListener)
        unbindService(connection)
        session = null
        super.onDestroy()
    }

    private fun play(fileName: String) {
        val current = session ?: return
        screen = Screen.DRIVE
        current.play(fileName)
        if (naviAfterPlay) NaviLauncher.open(this)
    }

    /** 실제 차량 신호로 주행을 시작한다. 위치와 속도는 런타임 권한이 필요하다. */
    private fun toggleLive() {
        val current = session ?: return
        if (current.running == CoachSession.LIVE) {
            current.stopAll()
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
        screen = Screen.DRIVE
        session?.startLive()
    }

    /** 세션 상태가 바뀌었을 때. 새 프레임이면 기어로 화면을 정하고 신호 칸을 갱신한다. */
    private fun onSessionChanged(frame: SignalFrame?) {
        if (frame != null) {
            // 기어 P면 리포트(진단·습관 선택 중이면 그대로), 그 밖의 기어면 주행 화면 (기획서 5.4).
            gear = frame.gear
            screen = nextScreen(screen, frame.gear)
            showSignals(frame)
        }
        highlight(session?.running)
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

    /** 지금 화면을 그린다. 값은 모두 [CoachState]에서 온다. */
    private fun render() {
        view.driveScreen.visibility = if (screen == Screen.DRIVE) View.VISIBLE else View.GONE
        view.reportScreen.visibility = if (screen == Screen.REPORT) View.VISIBLE else View.GONE
        habitScreens.show(screen)
        if (screen == Screen.REPORT) renderReport()
        renderDrive()
    }

    /** 기어 P 주행 리포트. 판정 목록이 바뀌었을 때만 다시 그린다. */
    private fun renderReport() {
        if (reportDrawnFor == state.todayEvents) return
        reportDrawnFor = state.todayEvents
        drawReport(buildReport(state.todayEvents, laws))
    }

    private fun drawReport(report: DriveReport) = with(view) {
        reportSaved.text = wonLabel(report.savedWon)
        reportKept.text = report.kept.toString()
        reportMissed.text = report.missed.toString()
        reportEmpty.visibility = if (report.items.isEmpty()) View.VISIBLE else View.GONE
        reportItems.removeAllViews()
        report.items.forEach(::addReportItem)
    }

    private fun addReportItem(item: ReportItem) {
        val card = layoutInflater.inflate(R.layout.report_item, view.reportItems, false)
        card.background = rounded(getColor(R.color.surface), radiusDp = 20)
        card.clipToOutline = true
        card.findViewById<View>(R.id.itemBar).setBackgroundColor(getColor(if (item.kept) R.color.ok else R.color.warn))
        card.findViewById<TextView>(R.id.itemTitle).text = item.title
        card.findViewById<TextView>(R.id.itemDetail).text = item.detail
        card.findViewById<TextView>(R.id.itemArticle).text = item.article
        card.findViewById<TextView>(R.id.itemPenalty).text = item.penalty
        view.reportItems.addView(card)
    }

    /** 주행 화면 (코치 카드). */
    private fun renderDrive() = with(view) {
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

    private fun addLiveButton() = addButton(CoachSession.LIVE, "실제 차량 신호") { toggleLive() }

    /** 데모 패널 토글: 켜면 시나리오를 틀 때 내비를 앞으로 띄운다. */
    private fun addNaviButton() {
        addButton(NAVI_LABEL, naviButtonText()) {
            naviAfterPlay = !naviAfterPlay
            buttons.getValue(NAVI_LABEL).text = naviButtonText()
        }
    }

    private fun naviButtonText() =
        getString(if (naviAfterPlay) R.string.demo_navi_after_play_on else R.string.demo_navi_after_play_off)

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
        val driveScreen: View = activity.findViewById(R.id.driveScreen)
        val reportScreen: View = activity.findViewById(R.id.reportScreen)
        val reportSavedTile: View = activity.findViewById(R.id.reportSavedTile)
        val reportKeptTile: View = activity.findViewById(R.id.reportKeptTile)
        val reportMissedTile: View = activity.findViewById(R.id.reportMissedTile)
        val reportSaved: TextView = activity.findViewById(R.id.reportSaved)
        val reportKept: TextView = activity.findViewById(R.id.reportKept)
        val reportMissed: TextView = activity.findViewById(R.id.reportMissed)
        val reportItems: LinearLayout = activity.findViewById(R.id.reportItems)
        val reportEmpty: TextView = activity.findViewById(R.id.reportEmpty)
        val reportAsk: TextView = activity.findViewById(R.id.reportAsk)
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
        const val LAW_TABLE_FILE = "law_table.json"
        const val MAX_LOGS = 12
        const val DISTANCE_UNIT_SCALE = 0.45f
        const val NAVI_LABEL = "내비"
        const val DIAGNOSE_LABEL = "진단"
        const val REQUEST_LIVE = 1
        val LIVE_PERMISSIONS = listOf("android.car.permission.CAR_SPEED", "pleos.car.permission.FUSED_LOCATION")
        val TILE_LABELS = listOf("속도", "핸들 각도", "깜빡이", "기어", "내비 TBT", "전방 신호")
    }
}
