package kkam.app

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import kkam.engine.Engine
import kkam.engine.model.Guide
import kkam.engine.model.SignalFrame

/**
 * 주행 한 번의 흐름: 프레임(시나리오 재생 또는 실제 차량 신호) → 엔진 → 음성 → [CoachState].
 * 화면이 보이지 않아도 돌아야 해서 [CoachService]가 갖고, 화면은 [Listener]로 받아 그리기만 한다.
 * 모두 메인 스레드에서 돈다 (재생기와 수집기가 메인 스레드로 프레임을 넘긴다).
 */
class CoachSession(context: Context) {
    /** 상태가 바뀌면 불린다. 새 프레임 때문이면 frame이 있다. */
    fun interface Listener {
        fun onSessionChanged(frame: SignalFrame?)
    }

    private val player = ScenarioPlayer(context)
    private val collector = SignalCollector(context)
    private val speaker = Speaker(context).apply { start() }
    private var engine = Engine()
    private val listeners = mutableSetOf<Listener>()
    private val handler = Handler(Looper.getMainLooper())
    private val hideOkPopup = Runnable {
        if (popup?.tone == PopupTone.OK) {
            popup = null
            notifyChanged(null)
        }
    }

    private val hidePreviewPopup = Runnable {
        if (running == null && popup != null) {
            popup = null
            notifyChanged(null)
        }
    }

    var state = CoachState()
        private set

    /** 마지막 프레임. 화면이 새로 붙을 때 신호 칸과 기어를 맞추는 데 쓴다. */
    var lastFrame: SignalFrame? = null
        private set

    /** 내비 위에 띄울 팝업. null이면 숨김. 규칙은 [nextPopup]. */
    var popup: Popup? = null
        private set

    /** 우리 앱 화면이 지금 보이는지. 보이면 팝업 대신 앱 화면의 코치 카드를 쓴다. */
    var appVisible = false
        set(value) {
            field = value
            notifyChanged(null)
        }

    /** 앱 칸의 크기. 앱 화면이 보일 때 잰다. 팝업을 이 칸(내비 자리) 가운데에 띄운다. */
    var appArea: Rect? = null

    /** 지금 도는 것: 시나리오 파일 이름, 실제 주행이면 [LIVE], 멈춰 있으면 null. */
    var running: String? = null
        private set

    fun scenarioFiles(): List<String> = player.fileNames()

    fun addListener(listener: Listener) {
        listeners += listener
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    fun play(fileName: String) {
        stopAll()
        startDrive(fileName.substringBefore('_'), fileName)
        player.play(fileName, onFrame = ::onFrame, onEnd = {
            running = null
            notifyChanged(null)
        })
    }

    /** 실제 차량 신호로 주행을 시작한다. 권한 확인은 화면 쪽에서 먼저 한다. */
    fun startLive() {
        stopAll()
        startDrive(LIVE, LIVE)
        collector.start(::onFrame)
    }

    /** 재생, 실제 주행, 음성을 모두 멈춘다. 오늘 기록은 남긴다. */
    fun stopAll() {
        player.stop()
        if (running == LIVE) collector.stop()
        speaker.stop()
        val wasRunning = running != null
        running = null
        popup = null
        handler.removeCallbacks(hideOkPopup)
        handler.removeCallbacks(hidePreviewPopup)
        if (wasRunning) notifyChanged(null)
    }

    /** 데모: 안내 종류를 예시 값으로 보여주고 어떻게 하는지 읽는다. 재생은 멈춘다. 내비가 앞에 있으면 팝업으로 뜬다. */
    fun preview(kind: GuideKind) {
        stopAll()
        state = state.preview(kind)
        popup = state.previewPopup()
        // 미리보기에는 프레임이 오지 않아 팝업이 저절로 숨지 않는다. 시간이 지나면 숨긴다.
        handler.removeCallbacks(hidePreviewPopup)
        handler.postDelayed(hidePreviewPopup, PREVIEW_POPUP_MS)
        // 어떻게 하는지(레버 동작)를 음성으로도 읽는다. 실제 주행에서는 엔진 GUIDE 문구를 읽는다.
        speaker.speak(state.message)
        notifyChanged(null)
    }

    /** 주행 화면 한 줄 문구를 바꾼다. 습관 선택을 마쳤을 때 쓴다. */
    fun showMessage(message: String) {
        state = state.copy(message = message)
        notifyChanged(null)
    }

    fun release() {
        stopAll()
        handler.removeCallbacksAndMessages(null)
        speaker.release()
        listeners.clear()
    }

    private fun startDrive(label: String, key: String) {
        engine = Engine()
        state = state.resetDrive(label)
        popup = null
        running = key
        notifyChanged(null)
    }

    private fun onFrame(frame: SignalFrame) {
        val outputs = engine.onFrame(frame)
        outputs.forEach { Log.i(TAG, "${frame.t} $it") }
        outputs.filterIsInstance<Guide>().forEach { speaker.speak(it.text) }
        state = state.next(frame, outputs)
        updatePopup(outputs, frame)
        lastFrame = frame
        notifyChanged(frame)
    }

    /** 파랑("좋아요")으로 바뀌면 [OK_POPUP_MS] 뒤에 숨긴다. 그 사이 주황으로 돌아가면 숨기지 않는다. */
    private fun updatePopup(outputs: List<kkam.engine.model.EngineOutput>, frame: SignalFrame) {
        val before = popup
        popup = nextPopup(before, state, outputs, frame)
        if (popup?.tone == PopupTone.OK && before?.tone != PopupTone.OK) {
            handler.removeCallbacks(hideOkPopup)
            handler.postDelayed(hideOkPopup, OK_POPUP_MS)
        }
    }

    private fun notifyChanged(frame: SignalFrame?) {
        listeners.toList().forEach { it.onSessionChanged(frame) }
    }

    companion object {
        const val LIVE = "실차"
        private const val OK_POPUP_MS = 3000L
        private const val PREVIEW_POPUP_MS = 6000L
        private const val TAG = "KkamCoach"
    }
}
