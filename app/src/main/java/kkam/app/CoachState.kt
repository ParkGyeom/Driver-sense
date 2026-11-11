package kkam.app

import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Guide
import kkam.engine.model.GuideAction
import kkam.engine.model.HeadlightState
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.SignalFrame
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kkam.engine.rules.APPROACH_START_M
import kkam.engine.rules.CHECK_AT_M
import kkam.engine.rules.LANE_CHECK_M
import kkam.engine.rules.LANE_START_M
import kkam.engine.rules.TUNNEL_GUIDE_M
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 코치 카드 색. 주황은 켜세요, 파랑은 좋아요 (proposal UI-1). */
enum class Tone { IDLE, WARN, OK }

/** 화면에 그릴 값 전체. Android에 의존하지 않아서 JVM 테스트로 확인한다. */
data class CoachState(
    /** 지금 안내의 종류. 그림과 제목을 고르는 데 쓴다. */
    val guideKind: GuideKind = GuideKind.TURN_SIGNAL,
    /** 오른쪽 방향이면 true. 화살표와 레버 그림 방향을 정한다. 음성 안내가 있으면 그 action을 따른다. */
    val right: Boolean = false,
    val tone: Tone = Tone.IDLE,
    val turnLabel: String = IDLE_LABEL,
    val distanceLabel: String = "",
    val signalLabel: String = "",
    val badge: String = "",
    val message: String = "주행을 시작하면 안내해요",
    /** 지금 보이는 문구가 음성 안내이면 true. 안내를 따르면 문구를 바꾼다. */
    val guideShowing: Boolean = false,
    /** 마지막 음성 안내가 바라는 동작. 판정이 나오거나 안내 대상이 바뀌면 지운다. */
    val guideAction: GuideAction? = null,
    /** 음성 안내의 종류 (예: R3 적색 우회전). 판정이 나오거나 안내 대상이 바뀌면 지운다. */
    val guidedKind: GuideKind? = null,
    /**
     * 판정 지점(R1 30m, R4 100m)을 지난 안내 대상과 그때의 색. 엔진은 그 지점에서 지킴·놓침을 정하므로
     * 지난 뒤에 깜빡이를 켜도 "좋아요"로 바꾸지 않는다.
     */
    val checkedTurn: TurnType? = null,
    val checkedTone: Tone? = null,
    /** 기록 앞에 붙는 주행 이름. 예: S2 */
    val driveLabel: String = "",
    val kept: Int = 0,
    val total: Int = 0,
    val logs: List<String> = emptyList(),
    /** 오늘 나온 판정 전체. 기어 P 주행 리포트에 쓴다. kept, total처럼 새 주행을 시작해도 남긴다. */
    val todayEvents: List<Event> = emptyList(),
    /** 내비 경로를 따라 코치 중이거나 마지막으로 끝난 경로. 새 주행을 시작하면 비운다 (RouteCoach.kt). */
    val route: RouteTrip? = null,
)

/**
 * 카드에 보여줄 안내 대상. 엔진 룰이 보는 내비 안내만 대상이 된다.
 * R1: 좌·우회전, R4: 나들목, R5: 터널. 감시 거리 밖이면 대상이 없다 (먼 회전에 주황을 띄우지 않는다).
 */
private data class Target(
    val turn: TurnType,
    val kind: GuideKind,
    val right: Boolean,
    /** 판정 지점. 없으면(R5) 고정하지 않는다. */
    val checkM: Int?,
    val ok: Boolean,
    val okLabel: String,
    val offLabel: String,
    val warnBadge: String,
    val answered: String,
)

private fun SignalFrame.target(): Target? {
    val tbt = tbt ?: return null
    return when (tbt.turn) {
        TurnType.LEFT, TurnType.RIGHT -> if (tbt.distanceM > APPROACH_START_M) {
            null
        } else {
            signalTarget(tbt.turn, GuideKind.TURN_SIGNAL, CHECK_AT_M, "지금 켜세요")
        }
        TurnType.RAMP_LEFT, TurnType.RAMP_RIGHT -> if (tbt.distanceM > LANE_START_M) {
            null
        } else {
            signalTarget(tbt.turn, GuideKind.LANE_CHANGE, LANE_CHECK_M, "깜빡이 켜세요")
        }
        TurnType.TUNNEL -> {
            val light = headlight
            if (tbt.distanceM > TUNNEL_GUIDE_M || light == null) {
                null
            } else {
                val on = light != HeadlightState.OFF
                Target(
                    turn = tbt.turn,
                    kind = GuideKind.HEADLIGHT,
                    right = false,
                    checkM = null,
                    ok = on,
                    okLabel = if (light == HeadlightState.AUTO) "전조등 자동" else "전조등 켜짐",
                    offLabel = "전조등 꺼짐",
                    warnBadge = "전조등 켜세요",
                    answered = "전조등을 켰어요",
                )
            }
        }
        else -> null
    }
}

private fun SignalFrame.signalTarget(turn: TurnType, kind: GuideKind, checkM: Int, warnBadge: String): Target {
    val right = turn == TurnType.RIGHT || turn == TurnType.RAMP_RIGHT
    val ok = turnSignal == if (right) TurnSignal.RIGHT else TurnSignal.LEFT
    return Target(
        turn = turn,
        kind = kind,
        right = right,
        checkM = checkM,
        ok = ok,
        okLabel = "깜빡이 켜짐",
        offLabel = turnSignal.offLabel(),
        warnBadge = warnBadge,
        answered = "깜빡이를 켰어요",
    )
}

/** 프레임 하나와 엔진 출력을 반영한 다음 상태를 돌려준다. */
fun CoachState.next(frame: SignalFrame, outputs: List<EngineOutput>): CoachState {
    val target = frame.target()
    var state = if (target == null) {
        copy(
            tone = Tone.IDLE, turnLabel = IDLE_LABEL, distanceLabel = "", signalLabel = "", badge = "",
            guideKind = GuideKind.TURN_SIGNAL, guideAction = null, guidedKind = null,
            checkedTurn = null, checkedTone = null,
        )
    } else {
        withTarget(frame, target)
    }
    for (output in outputs) {
        state = when (output) {
            is Guide -> state.copy(
                guideKind = output.rule.guideKind() ?: state.guideKind,
                guidedKind = output.rule.guideKind(),
                guideAction = output.action,
                right = output.action == GuideAction.SIGNAL_RIGHT,
                message = output.text,
                guideShowing = true,
                logs = state.logs + state.logLine(output.t, "음성 안내"),
            )
            is Event -> state.copy(
                message = output.message(),
                guideShowing = false,
                guideAction = null,
                guidedKind = null,
                kept = state.kept + if (output.result == Result.KEPT) 1 else 0,
                total = state.total + 1,
                logs = state.logs + state.logLine(output.t, "${output.title()} ${output.result.label()}"),
                todayEvents = state.todayEvents + output,
            )
        }
    }
    return state
}

private fun CoachState.withTarget(frame: SignalFrame, target: Target): CoachState {
    val distanceM = frame.tbt?.distanceM ?: 0
    // 안내 대상이 바뀌면(재탐색, 다음 안내) 이전 안내의 종류와 고정 색을 지운다
    val sameTarget = checkedTurn == target.turn || guidedFor(target)
    val base = if (sameTarget) this else copy(guideAction = null, guidedKind = null, checkedTurn = null, checkedTone = null)

    val pastCheck = target.checkM != null && distanceM <= target.checkM
    val liveTone = if (target.ok) Tone.OK else Tone.WARN
    val locked = if (pastCheck) {
        if (base.checkedTurn == target.turn) base.checkedTone ?: liveTone else liveTone
    } else {
        null
    }
    val tone = locked ?: liveTone
    val answered = tone == Tone.OK && base.guideShowing
    return base.copy(
        guideKind = base.guidedKind ?: target.kind,
        right = base.guideAction?.let { it == GuideAction.SIGNAL_RIGHT } ?: target.right,
        tone = tone,
        turnLabel = target.turn.label(),
        distanceLabel = "$distanceM m",
        signalLabel = if (target.ok) target.okLabel else target.offLabel,
        badge = when {
            tone == Tone.OK -> "좋아요"
            locked != null -> "다음엔 미리 켜요"
            else -> target.warnBadge
        },
        message = if (answered) target.answered else base.message,
        guideShowing = base.guideShowing && !answered,
        checkedTurn = if (pastCheck) target.turn else null,
        checkedTone = locked,
    )
}

/** 지금 안내(GUIDE)가 이 대상에 대한 것인가. 회전·나들목은 방향까지 같아야 한다. */
private fun CoachState.guidedFor(target: Target): Boolean = when (guideAction) {
    null -> false
    GuideAction.HEADLIGHT_ON -> target.kind == GuideKind.HEADLIGHT
    GuideAction.SIGNAL_LEFT -> target.kind != GuideKind.HEADLIGHT && !target.right
    GuideAction.SIGNAL_RIGHT -> target.kind != GuideKind.HEADLIGHT && target.right
}

/** 새 시나리오를 시작할 때 쓴다. 오늘 횟수와 기록은 남긴다. */
fun CoachState.resetDrive(label: String): CoachState =
    CoachState(message = "", driveLabel = label, kept = kept, total = total, logs = logs, todayEvents = todayEvents)

/**
 * 앱을 다시 켰을 때 저장해 둔 오늘 판정으로 상태를 만든다 ([RecordStore]).
 * 지킨 횟수와 기록 줄은 판정에서 다시 센다. 음성 안내 줄과 주행 이름(S2 등)은 저장하지 않아서 빠진다.
 */
fun restoredToday(events: List<Event>): CoachState {
    val base = CoachState(todayEvents = events)
    return base.copy(
        kept = events.count { it.result == Result.KEPT },
        total = events.size,
        logs = events.map { base.logLine(it.t, "${it.title()} ${it.result.label()}") },
    )
}

private fun CoachState.logLine(t: Long, text: String): String =
    listOf(driveLabel, t.timeLabel(), text).filter { it.isNotEmpty() }.joinToString(" ")

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
    TurnType.RAMP_LEFT -> "왼쪽 나들목"
    TurnType.RAMP_RIGHT -> "오른쪽 나들목"
    TurnType.TUNNEL -> "터널"
    TurnType.OTHER -> "기타"
}

fun Result.label(): String = when (this) {
    Result.KEPT -> "지킴"
    Result.MISSED -> "놓침"
}

/** 판정 이름. 예: "좌회전 깜빡이", "터널 전조등". 기록 한 줄과 리포트 카드가 같이 쓴다. */
fun Event.title(): String {
    val direction = turn?.label().orEmpty()
    return when (rule) {
        RuleId.R1 -> "$direction 깜빡이"
        RuleId.R2 -> "깜빡이 없이 $direction"
        RuleId.R3 -> "적색 우회전 일시정지"
        RuleId.R4 -> "${if (turn == TurnType.RIGHT) "오른쪽" else "왼쪽"} 나들목 깜빡이"
        RuleId.R5 -> "터널 전조등"
    }
}

private fun Event.message(): String = when {
    result == Result.MISSED && rule == RuleId.R3 -> "이번 우회전은 멈추지 않았어요"
    result == Result.MISSED -> "이번 ${turn?.label().orEmpty()}은 깜빡이를 놓쳤어요"
    rule == RuleId.R5 -> "안내 듣고 전조등을 켰어요"
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

private const val IDLE_LABEL = "다음 회전 대기"
private const val EPOCH_START_MS = 1_000_000_000_000L
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Asia/Seoul"))
