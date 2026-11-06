package kkam.app

import android.content.Context
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

    var state = CoachState()
        private set

    /** 마지막 프레임. 화면이 새로 붙을 때 신호 칸과 기어를 맞추는 데 쓴다. */
    var lastFrame: SignalFrame? = null
        private set

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
        if (wasRunning) notifyChanged(null)
    }

    /** 주행 화면 한 줄 문구를 바꾼다. 습관 선택을 마쳤을 때 쓴다. */
    fun showMessage(message: String) {
        state = state.copy(message = message)
        notifyChanged(null)
    }

    fun release() {
        stopAll()
        speaker.release()
        listeners.clear()
    }

    private fun startDrive(label: String, key: String) {
        engine = Engine()
        state = state.resetDrive(label)
        running = key
        notifyChanged(null)
    }

    private fun onFrame(frame: SignalFrame) {
        val outputs = engine.onFrame(frame)
        outputs.forEach { Log.i(TAG, "${frame.t} $it") }
        outputs.filterIsInstance<Guide>().forEach { speaker.speak(it.text) }
        state = state.next(frame, outputs)
        lastFrame = frame
        notifyChanged(frame)
    }

    private fun notifyChanged(frame: SignalFrame?) {
        listeners.toList().forEach { it.onSessionChanged(frame) }
    }

    companion object {
        const val LIVE = "실차"
        private const val TAG = "KkamCoach"
    }
}
