package kkam.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import kkam.engine.model.Scenario
import kkam.engine.model.SignalFrame
import kotlinx.serialization.json.Json

/** assets의 시나리오 파일을 읽고 녹화된 시각에 맞춰 프레임을 내보낸다. 모두 메인 스레드에서 돈다. */
class ScenarioPlayer(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val token = Any()
    private val json = Json { ignoreUnknownKeys = true }

    /** S1, S2, ..., S10 순서의 파일 이름. */
    fun fileNames(): List<String> = context.assets.list("").orEmpty()
        .filter { it.matches(Regex("""S\d+_.*\.json""")) }
        .sortedBy { it.drop(1).substringBefore('_').toInt() }

    fun play(fileName: String, onFrame: (SignalFrame) -> Unit, onEnd: () -> Unit) {
        stop()
        val text = context.assets.open(fileName).bufferedReader().use { it.readText() }
        val frames = json.decodeFromString(Scenario.serializer(), text).frames
        for (frame in frames) {
            handler.postDelayed({ onFrame(frame) }, token, frame.t)
        }
        handler.postDelayed(onEnd, token, frames.last().t + END_HOLD_MS)
    }

    fun stop() = handler.removeCallbacksAndMessages(token)

    private companion object {
        const val END_HOLD_MS = 1000L
    }
}
