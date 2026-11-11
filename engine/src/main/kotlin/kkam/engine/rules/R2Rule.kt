package kkam.engine.rules

import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.SignalFrame
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType

// spec 9.2 파라미터 (TURN_DONE_DEG는 R1과 같은 값을 쓴다)
const val R2_MIN_SPEED_KPH = 5.0
const val R2_MAX_SPEED_KPH = 30.0
const val R2_TURN_RATE_DPS = 8.0
const val R2_TURN_WINDOW_MS = 10000L
const val SIGNAL_LOOKBACK_MS = 5000L
const val R2_AFTER_PARK_MS = 60000L
const val R2_HANDOFF_MS = 10000L

private const val MS_PER_SECOND = 1000.0

/** 부호 있는 진행 방향 차이 (-180~180°). 양수면 시계 방향(오른쪽), 음수면 왼쪽. 예: 350 → 10 이면 +20. */
fun signedHeadingDiff(from: Double, to: Double): Double =
    (to - from + 540).mod(360.0) - 180

/**
 * R2: 깜빡이 없이 회전 (spec 9장).
 *
 * 내비 안내(tbt)를 쓰지 않고 진행 방향 변화로 회전을 잡는다. 안내는 하지 않고 기록만 한다.
 * 다른 룰(R1)이 회전을 맡고 있으면 같은 회전을 두 번 기록하지 않도록 쉰다.
 *
 * @param nextEventId 엔진 전체에서 순서대로 이벤트 id를 만들어주는 함수
 */
class R2Rule(private val nextEventId: () -> String) {
    private sealed class State {
        /** 회전을 기다리며 달리는 중. 처음 상태. */
        data object Cruising : State()

        data class Turning(
            /** 회전이 시작되기 직전 프레임의 진행 방향 */
            val startHeading: Double,
            val startT: Long,
            /** LEFT 또는 RIGHT */
            val direction: TurnType,
            /** 회전 시작 전 SIGNAL_LOOKBACK_MS 안에 그 방향 깜빡이가 켜졌었는가 */
            val signalOk: Boolean,
        ) : State()

        /** 판정하지 않고 쉬는 중 (주차·후진 직후, 다른 룰이 회전을 맡음). */
        data class Cooldown(
            /** 앞으로 더 쉬어야 하는 주행 시간 (ms) */
            val remainMs: Long,
        ) : State()
    }

    private var state: State = State.Cruising

    // spec 9.4: 상태와 상관없이 프레임마다 기억하는 값
    private var prevHeading: Double? = null
    private var prevT: Long? = null
    private var lastLeftT: Long? = null
    private var lastRightT: Long? = null

    /**
     * 프레임 하나를 처리한다. spec 9.5 전이표를 위에서부터 검사해 처음 맞는 행만 적용한다.
     *
     * @param otherRuleBusy 다른 룰(R1)이 이 프레임 처리 전이나 후에 회전을 맡고 있는가. Engine이 계산한다.
     */
    fun onFrame(frame: SignalFrame, otherRuleBusy: Boolean): List<EngineOutput> {
        // spec 9.4: 전이표를 보기 전에 기억값을 먼저 갱신한다
        val previousHeading = prevHeading
        val elapsedMs = prevT?.let { frame.t - it } ?: 0
        val turnRate = turnRate(previousHeading, frame.headingDeg, elapsedMs)
        rememberFrame(frame)
        val current = updateCooldown(state, frame.speedKph, elapsedMs)

        when {
            // 전이표 #0: 주차·후진. 주차장 안 회전을 거르려고 한동안 쉰다
            frame.gear == Gear.P || frame.gear == Gear.R -> {
                state = State.Cooldown(remainMs = R2_AFTER_PARK_MS)
                return emptyList()
            }

            // 전이표 #1: 다른 룰이 이 회전을 맡음. 끝난 뒤에도 남은 회전을 새 회전으로 잡지 않도록 더 쉰다
            otherRuleBusy -> {
                val remain = if (current is State.Cooldown) current.remainMs else 0
                state = State.Cooldown(remainMs = maxOf(remain, R2_HANDOFF_MS))
                return emptyList()
            }

            // 전이표 #2: 다 쉬었으면 다시 회전을 기다린다
            current is State.Cooldown && current.remainMs <= 0 -> {
                state = State.Cruising
                return emptyList()
            }

            // 전이표 #3: 저속에서 진행 방향이 빠르게 바뀌기 시작함 = 회전 시작
            current is State.Cruising && previousHeading != null &&
                frame.speedKph >= R2_MIN_SPEED_KPH && frame.speedKph <= R2_MAX_SPEED_KPH &&
                turnRate >= R2_TURN_RATE_DPS -> {
                val direction =
                    if (signedHeadingDiff(previousHeading, frame.headingDeg) > 0) TurnType.RIGHT else TurnType.LEFT
                state = State.Turning(
                    startHeading = previousHeading,
                    startT = frame.t,
                    direction = direction,
                    signalOk = signaledRecently(direction, frame.t),
                )
                return emptyList()
            }

            // 전이표 #4: 진행 방향이 충분히 바뀌어 회전이 끝남. 이때 기록한다
            current is State.Turning &&
                headingDiff(current.startHeading, frame.headingDeg) >= TURN_DONE_DEG -> {
                state = State.Cruising
                return listOf(
                    Event(
                        id = nextEventId(),
                        t = frame.t,
                        rule = RuleId.R2,
                        result = if (current.signalOk) Result.KEPT else Result.MISSED,
                        guided = false,
                        turn = current.direction,
                        lat = frame.lat,
                        lon = frame.lon,
                    ),
                )
            }

            // 전이표 #5: 시간 안에 60°가 안 됨. 굽은 도로로 보고 취소
            current is State.Turning && frame.t - current.startT > R2_TURN_WINDOW_MS -> {
                state = State.Cruising
                return emptyList()
            }

            // 맞는 행이 없으면 상태를 유지한다
            else -> {
                state = current
                return emptyList()
            }
        }
    }

    /** 직전 프레임 대비 초당 진행 방향 변화 (°/s). 첫 프레임이거나 시간이 안 흘렀으면 0. */
    private fun turnRate(previousHeading: Double?, heading: Double, elapsedMs: Long): Double {
        if (previousHeading == null || elapsedMs <= 0) {
            return 0.0
        }
        return headingDiff(previousHeading, heading) / (elapsedMs / MS_PER_SECOND)
    }

    private fun rememberFrame(frame: SignalFrame) {
        prevHeading = frame.headingDeg
        prevT = frame.t
        when (frame.turnSignal) {
            TurnSignal.LEFT -> lastLeftT = frame.t
            TurnSignal.RIGHT -> lastRightT = frame.t
            else -> Unit // OFF, HAZARD는 깜빡이로 치지 않는다
        }
    }

    /** spec 9.4: COOLDOWN이면 주행 시간(speedKph > 0)만큼 쉬는 시간을 줄인다. */
    private fun updateCooldown(state: State, speedKph: Double, elapsedMs: Long): State {
        if (state !is State.Cooldown || speedKph <= 0) {
            return state
        }
        return state.copy(remainMs = state.remainMs - elapsedMs)
    }

    private fun signaledRecently(direction: TurnType, t: Long): Boolean {
        val lastT = if (direction == TurnType.LEFT) lastLeftT else lastRightT
        return lastT != null && lastT >= t - SIGNAL_LOOKBACK_MS
    }
}
