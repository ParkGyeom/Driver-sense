package kkam.engine.rules

import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.GuideAction
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.SignalFrame

// spec 17.1 파라미터
const val OVER_MARGIN_KPH = 10
const val OVER_HOLD_MS = 2000L
const val SLOW_GRACE_MS = 10000L

/**
 * R11: 제한속도 (spec 17장).
 *
 * 내비가 알려주는 지금 도로의 제한속도보다 [OVER_MARGIN_KPH] 넘게 빠른 상태가 [OVER_HOLD_MS] 이어지면 안내하고,
 * 안내 뒤 [SLOW_GRACE_MS] 안에 제한속도 이하로 줄였는지 본다. 엔진은 진출로·커브를 모르지만,
 * 그런 구간은 제한속도가 낮아서 이 룰로 잡힌다.
 *
 * @param nextEventId 엔진 전체에서 순서대로 이벤트 id를 만들어주는 함수
 */
class R11Rule(private val nextEventId: () -> String) {
    private sealed class State {
        data object Idle : State()

        /** 과속이 이어지는 중 */
        data class Over(val since: Long) : State()

        /** 안내했고 줄이길 기다리는 중 */
        data class Watching(val guidedAt: Long) : State()

        /** 이번 과속은 판정 끝. 제한속도 이하로 내려가길 기다린다 */
        data object Done : State()
    }

    private var state: State = State.Idle

    /** 프레임 하나를 처리한다. spec 17.3 전이표를 위에서부터 검사해 처음 맞는 행만 적용한다. */
    fun onFrame(frame: SignalFrame): List<EngineOutput> {
        val current = state
        val limit = frame.speedLimitKph
        // spec 17.3 표 위의 정의
        val over = limit != null && frame.speedKph > limit + OVER_MARGIN_KPH
        val withinLimit = limit == null || frame.speedKph <= limit

        when {
            // 전이표 #0: 주차·후진으로 보고 취소
            frame.gear == Gear.P || frame.gear == Gear.R -> {
                state = State.Idle
                return emptyList()
            }

            // 전이표 #1: 과속이 시작됐다
            current is State.Idle && over -> {
                state = State.Over(since = frame.t)
                return emptyList()
            }

            // 전이표 #2: 안내 전에 과속이 끝났다
            current is State.Over && !over -> {
                state = State.Idle
                return emptyList()
            }

            // 전이표 #3: 과속이 OVER_HOLD_MS 이어졌다. 한 번 안내한다
            current is State.Over && frame.t - current.since >= OVER_HOLD_MS -> {
                state = State.Watching(guidedAt = frame.t)
                return listOf(Guide(frame.t, RuleId.R11, "제한속도 ${limit}이에요, 속도를 줄이세요", GuideAction.SLOW_DOWN))
            }

            // 전이표 #4: 안내 듣고 제한속도 이하로 줄였다
            current is State.Watching && withinLimit -> {
                state = State.Done
                return listOf(event(frame, Result.KEPT))
            }

            // 전이표 #5: 안내 뒤 SLOW_GRACE_MS 안에 줄이지 않았다
            current is State.Watching && frame.t - current.guidedAt >= SLOW_GRACE_MS -> {
                state = State.Done
                return listOf(event(frame, Result.MISSED))
            }

            // 전이표 #6: 제한속도 이하로 내려왔다. 다음 과속을 기다린다
            current is State.Done && withinLimit -> {
                state = State.Idle
                return emptyList()
            }

            // 맞는 행이 없으면 상태를 유지한다
            else -> return emptyList()
        }
    }

    private fun event(frame: SignalFrame, result: Result) = Event(
        id = nextEventId(),
        t = frame.t,
        rule = RuleId.R11,
        result = result,
        guided = true,
        turn = null,
        lat = frame.lat,
        lon = frame.lon,
    )
}
