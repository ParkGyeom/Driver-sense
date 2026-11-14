package kkam.engine.rules

import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.GuideAction
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.SignalFrame

// spec 15.1 파라미터
const val HARD_BRAKE_MPS2 = 3.0
const val HARD_BRAKE_FROM_KPH = 20.0
const val HARD_BRAKE_COOLDOWN_MS = 10000L
const val MAX_FRAME_GAP_MS = 3000L

private const val KPH_PER_METER_PER_SECOND = 3.6
private const val MS_PER_SECOND = 1000.0

/**
 * 두 프레임 사이 감속도 (m/s²). 줄었으면 양수, 늘었으면 음수.
 * decel = (prevSpeedKph − speedKph) ÷ 3.6 ÷ ((t − prevT) ÷ 1000) (spec 15.1)
 */
fun deceleration(prevSpeedKph: Double, prevT: Long, speedKph: Double, t: Long): Double =
    (prevSpeedKph - speedKph) / KPH_PER_METER_PER_SECOND / ((t - prevT) / MS_PER_SECOND)

/**
 * R9: 급정거 (spec 15장).
 *
 * SDK에 가속도 센서가 없어서 직전 프레임과의 속도 차이로 감속도를 구한다.
 * 지난 일을 알려주는 룰이라 예방 안내가 없고, 감지한 프레임에 사후 안내(GUIDE)와 놓침(EVENT)을 함께 낸다.
 *
 * @param nextEventId 엔진 전체에서 순서대로 이벤트 id를 만들어주는 함수
 */
class R9Rule(private val nextEventId: () -> String) {
    private sealed class State {
        /** 감시 중 */
        data object Ready : State()

        /** 방금 셌음. [until]까지 다시 세지 않는다 */
        data class Cooldown(val until: Long) : State()
    }

    private var state: State = State.Ready

    /** 직전 프레임의 시각과 속도 (spec 15.2). 첫 프레임 전에는 null. */
    private var prevT: Long? = null
    private var prevSpeedKph: Double = 0.0

    /** 프레임 하나를 처리한다. spec 15.3 전이표를 위에서부터 검사해 처음 맞는 행만 적용한다. */
    fun onFrame(frame: SignalFrame): List<EngineOutput> {
        val outputs = transition(frame)
        // 상태와 상관없이 직전 프레임을 이번 값으로 바꾼다 (spec 15.2)
        prevT = frame.t
        prevSpeedKph = frame.speedKph
        return outputs
    }

    private fun transition(frame: SignalFrame): List<EngineOutput> {
        val current = state

        when {
            // 전이표 #0: 주차·후진으로 보고 처음부터
            frame.gear == Gear.P || frame.gear == Gear.R -> {
                state = State.Ready
                return emptyList()
            }

            // 전이표 #1: 직전 프레임보다 기준 넘게 급하게 줄었다. 사후 안내와 놓침을 함께 낸다
            current is State.Ready && hardBrake(frame) -> {
                state = State.Cooldown(until = frame.t + HARD_BRAKE_COOLDOWN_MS)
                return listOf(
                    Guide(frame.t, RuleId.R9, GUIDE_TEXT, GuideAction.KEEP_DISTANCE),
                    Event(
                        id = nextEventId(),
                        t = frame.t,
                        rule = RuleId.R9,
                        result = Result.MISSED,
                        guided = false,
                        turn = null,
                        lat = frame.lat,
                        lon = frame.lon,
                    ),
                )
            }

            // 전이표 #2: 다시 세지 않는 시간이 끝났다
            current is State.Cooldown && frame.t >= current.until -> {
                state = State.Ready
                return emptyList()
            }

            // 맞는 행이 없으면 상태를 유지한다
            else -> return emptyList()
        }
    }

    /** 전이표 #1의 조건: 직전 프레임이 가깝고, 직전 속도가 충분히 빨랐고, 감속도가 기준 이상이다. */
    private fun hardBrake(frame: SignalFrame): Boolean {
        val lastT = prevT ?: return false
        val gap = frame.t - lastT
        if (gap <= 0 || gap > MAX_FRAME_GAP_MS) return false
        if (prevSpeedKph < HARD_BRAKE_FROM_KPH) return false
        return deceleration(prevSpeedKph, lastT, frame.speedKph, frame.t) >= HARD_BRAKE_MPS2
    }

    private companion object {
        const val GUIDE_TEXT = "방금 급하게 멈췄어요, 앞차와 거리를 넉넉히 두세요"
    }
}
