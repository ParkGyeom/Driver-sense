package kkam.engine.rules

import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.GuideAction
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.SignalFrame
import kkam.engine.model.TurnType

// spec 16.1 파라미터. CHECK_AT_M, TBT_JUMP_M은 R1과 같은 값을 쓴다 (R1Rule.kt)
const val SLOW_GUIDE_M = 150
const val SLOW_GUIDE_KPH = 50.0
const val TURN_SLOW_KPH = 30.0

/**
 * R10: 회전 앞 감속, 브레이크 타이밍 (spec 16장).
 *
 * 좌·우회전·유턴 [SLOW_GUIDE_M] 안을 [SLOW_GUIDE_KPH]보다 빠르게 다가가면 "미리 줄이세요" 안내하고,
 * 판정 지점(R1과 같은 30 m)에서 속도가 [TURN_SLOW_KPH] 이하인지 본다.
 * 천천히 다가간 회전은 기록하지 않는다 (R1과 같은 회전을 리포트에 두 번 채우지 않기 위해).
 *
 * @param nextEventId 엔진 전체에서 순서대로 이벤트 id를 만들어주는 함수
 */
class R10Rule(private val nextEventId: () -> String) {
    private sealed class State {
        data object Idle : State()

        /** 회전에 다가가는 중 */
        data class Approaching(val target: TurnType, val guided: Boolean, val lastDistanceM: Int) : State()

        /** 이 회전은 판정 끝. 다음 안내로 넘어가길 기다린다 */
        data class Done(val target: TurnType, val lastDistanceM: Int) : State()
    }

    private var state: State = State.Idle

    /** 프레임 하나를 처리한다. spec 16.3 전이표를 위에서부터 검사해 처음 맞는 행만 적용한다. */
    fun onFrame(frame: SignalFrame): List<EngineOutput> {
        val current = state
        val tbt = frame.tbt

        when {
            // 전이표 #0: 주차·후진으로 보고 취소
            frame.gear == Gear.P || frame.gear == Gear.R -> {
                state = State.Idle
                return emptyList()
            }

            // 전이표 #1: 감시 거리 안의 회전 안내를 봤다 (판정 지점 안이면 다가오는 모습을 못 봐서 보지 않는다)
            current is State.Idle && tbt != null && tbt.turn in WATCHED_TURNS &&
                tbt.distanceM > CHECK_AT_M && tbt.distanceM <= SLOW_GUIDE_M -> {
                state = State.Approaching(target = tbt.turn, guided = false, lastDistanceM = tbt.distanceM)
                return emptyList()
            }

            // 전이표 #2: 다른 안내 (재탐색, 다음 회전)
            current is State.Approaching && isOtherGuide(frame, current.target, current.lastDistanceM) -> {
                state = State.Idle
                return emptyList()
            }

            // 전이표 #3: 판정 지점 안. 안내했거나 기준보다 빠르면 기록한다
            current is State.Approaching && tbt!!.distanceM <= CHECK_AT_M &&
                (current.guided || frame.speedKph > TURN_SLOW_KPH) -> {
                state = State.Done(current.target, tbt.distanceM)
                val result = if (frame.speedKph <= TURN_SLOW_KPH) Result.KEPT else Result.MISSED
                return listOf(
                    Event(
                        id = nextEventId(),
                        t = frame.t,
                        rule = RuleId.R10,
                        result = result,
                        guided = current.guided,
                        turn = current.target,
                        lat = frame.lat,
                        lon = frame.lon,
                    ),
                )
            }

            // 전이표 #4: 판정 지점 안. 천천히 다가간 회전은 기록하지 않는다
            current is State.Approaching && tbt!!.distanceM <= CHECK_AT_M -> {
                state = State.Done(current.target, tbt.distanceM)
                return emptyList()
            }

            // 전이표 #5: 빠르게 다가가는 중. 한 번 안내한다
            current is State.Approaching && frame.speedKph > SLOW_GUIDE_KPH && !current.guided -> {
                state = current.copy(guided = true, lastDistanceM = tbt!!.distanceM)
                return listOf(Guide(frame.t, RuleId.R10, GUIDE_TEXT, GuideAction.SLOW_DOWN))
            }

            // 다가가는 중: 상태를 유지하고 남은 거리만 바꾼다 (spec 16.3 표 아래)
            current is State.Approaching -> {
                state = current.copy(lastDistanceM = tbt!!.distanceM)
                return emptyList()
            }

            // 전이표 #6: 판정을 마친 회전에서 다음 안내로 넘어갔다
            current is State.Done && isOtherGuide(frame, current.target, current.lastDistanceM) -> {
                state = State.Idle
                return emptyList()
            }

            // 판정을 마친 회전을 지나는 중: 남은 거리만 바꾼다
            current is State.Done -> {
                state = current.copy(lastDistanceM = tbt!!.distanceM)
                return emptyList()
            }

            // 맞는 행이 없으면 상태를 유지한다
            else -> return emptyList()
        }
    }

    /**
     * "다른 안내" (spec 16.3): tbt가 없거나, 안내 종류가 바뀌었거나, 남은 거리가 [TBT_JUMP_M] 넘게 커졌다 (같은 방향 다음 회전).
     * 이 함수가 false면 frame.tbt는 null이 아니다.
     */
    private fun isOtherGuide(frame: SignalFrame, target: TurnType, lastDistanceM: Int): Boolean {
        val tbt = frame.tbt ?: return true
        return tbt.turn != target || tbt.distanceM > lastDistanceM + TBT_JUMP_M
    }

    private companion object {
        /** R1과 같은 대상: 좌회전, 우회전, 유턴 */
        val WATCHED_TURNS = setOf(TurnType.LEFT, TurnType.RIGHT, TurnType.UTURN)
        const val GUIDE_TEXT = "회전이 가까워요, 지금부터 천천히 속도를 줄이세요"
    }
}
