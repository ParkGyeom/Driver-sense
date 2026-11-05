package kkam.app

import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Guide
import kkam.engine.model.Result
import kkam.engine.model.SignalFrame
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 코치 카드 색. 주황은 켜세요, 파랑은 좋아요 (proposal UI-1). */
enum class Tone { IDLE, WARN, OK }

/** 화면에 그릴 값 전체. Android에 의존하지 않아서 JVM 테스트로 확인한다. */
data class CoachState(
    val tone: Tone = Tone.IDLE,
    val turnLabel: String = "다음 회전 대기",
    val distanceLabel: String = "",
    val signalLabel: String = "",
    val badge: String = "",
    val message: String = "시나리오를 고르면 재생을 시작해요",
    /** 지금 보이는 문구가 음성 안내이면 true. 깜빡이를 켜면 문구를 바꾼다. */
    val guideShowing: Boolean = false,
    /** 기록 앞에 붙는 주행 이름. 예: S2 */
    val driveLabel: String = "",
    val kept: Int = 0,
    val total: Int = 0,
    val logs: List<String> = emptyList(),
)

/** 프레임 하나와 엔진 출력을 반영한 다음 상태를 돌려준다. */
fun CoachState.next(frame: SignalFrame, outputs: List<EngineOutput>): CoachState {
    val turn = frame.tbt?.turn?.takeIf { it == TurnType.LEFT || it == TurnType.RIGHT }
    var state = if (turn == null) {
        copy(tone = Tone.IDLE, turnLabel = "다음 회전 대기", distanceLabel = "", signalLabel = "", badge = "")
    } else {
        val signalOk = frame.turnSignal.matches(turn)
        val guideAnswered = signalOk && guideShowing
        copy(
            message = if (guideAnswered) "깜빡이를 켰어요" else message,
            guideShowing = guideShowing && !guideAnswered,
            tone = if (signalOk) Tone.OK else Tone.WARN,
            turnLabel = turn.label(),
            distanceLabel = "${frame.tbt?.distanceM} m",
            signalLabel = if (signalOk) "깜빡이 켜짐" else frame.turnSignal.offLabel(),
            badge = if (signalOk) "좋아요" else "지금 켜세요",
        )
    }
    for (output in outputs) {
        state = when (output) {
            is Guide -> state.copy(
                message = output.text,
                guideShowing = true,
                logs = state.logs + state.logLine(output.t, "음성 안내"),
            )
            is Event -> state.copy(
                message = output.message(),
                guideShowing = false,
                kept = state.kept + if (output.result == Result.KEPT) 1 else 0,
                total = state.total + 1,
                logs = state.logs + state.logLine(output.t, "${output.turn.label()} ${output.result.label()}"),
            )
        }
    }
    return state
}

/** 새 시나리오를 시작할 때 쓴다. 오늘 횟수와 기록은 남긴다. */
fun CoachState.resetDrive(label: String): CoachState =
    CoachState(message = "", driveLabel = label, kept = kept, total = total, logs = logs)

private fun CoachState.logLine(t: Long, text: String): String =
    listOf(driveLabel, t.timeLabel(), text).filter { it.isNotEmpty() }.joinToString(" ")

private fun TurnSignal.matches(turn: TurnType): Boolean =
    (turn == TurnType.LEFT && this == TurnSignal.LEFT) || (turn == TurnType.RIGHT && this == TurnSignal.RIGHT)

private fun TurnSignal.offLabel(): String = when (this) {
    TurnSignal.OFF -> "깜빡이 꺼짐"
    TurnSignal.HAZARD -> "비상등 켜짐"
    TurnSignal.LEFT, TurnSignal.RIGHT -> "반대쪽 깜빡이"
}

fun TurnType.label(): String = when (this) {
    TurnType.LEFT -> "좌회전"
    TurnType.RIGHT -> "우회전"
    TurnType.STRAIGHT -> "직진"
    TurnType.UTURN -> "유턴"
    TurnType.OTHER -> "기타"
}

private fun Result.label(): String = when (this) {
    Result.KEPT -> "지킴"
    Result.MISSED -> "놓침"
}

private fun Event.message(): String = when {
    result == Result.MISSED -> "이번 ${turn.label()}은 깜빡이를 놓쳤어요"
    guided -> "안내 듣고 잘 켰어요"
    else -> "안내 없이 잘 켰어요"
}

/** 시나리오의 t는 0부터 세고, 실제 주행의 t는 현재 시각이다. */
private fun Long.timeLabel(): String =
    if (this < EPOCH_START_MS) {
        "${this / 1000}초"
    } else {
        CLOCK.format(Instant.ofEpochMilli(this))
    }

private const val EPOCH_START_MS = 1_000_000_000_000L
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Asia/Seoul"))
