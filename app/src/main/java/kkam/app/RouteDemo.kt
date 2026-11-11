package kkam.app

import kkam.engine.model.Gear
import kkam.engine.model.SignalFrame
import kkam.engine.model.TurnSignal

/**
 * PT용 "경로 데모". 에뮬레이터 내비는 경로를 찍어도 위치가 움직이지 않아서,
 * 기존 시나리오 몇 개를 시간 순으로 이어 붙여 목적지까지 가는 한 경로처럼 재생한다.
 * scenarios/ 파일은 고치지 않고 읽기만 한다. Android에 의존하지 않아서 JVM 테스트로 확인한다.
 *
 * 흐름: 출발 전 기어 P(경로 미리보기) → 구간 1, 2, 3 (회전마다 코치) → 서행 → 기어 P(도착, 경로 리포트)
 */
object RouteDemo {
    /** 이어 붙일 구간. 좌회전 안내 후 지킴 → 우회전 처음부터 지킴 → 좌회전 놓침. */
    val LEGS = listOf(
        "S2_좌회전_안내후_켬.json",
        "S4_우회전_처음부터_켬.json",
        "S3_좌회전_끝까지_안켬.json",
    )

    /** 데모 목적지. 실제 내비 값이 아니다. */
    const val DESTINATION = "판교역"
    const val DISTANCE_M = 4200

    /** 출발 전 미리보기를 보여주는 시간. */
    const val PREVIEW_MS = 8000L

    /** 구간 사이 간격. 앞 구간 마지막 프레임 뒤에 다음 구간 첫 프레임이 온다. */
    const val LEG_GAP_MS = 3000L

    /** 마지막 구간 뒤 서행하다가 기어 P로 도착하기까지의 간격. */
    const val ARRIVE_GAP_MS = 2000L

    /** 데모 한 번: 경로 정보와 재생할 프레임. */
    data class Run(val plan: RoutePlan, val frames: List<SignalFrame>)

    /** 구간별 프레임(각각 t가 0부터)을 받아 한 경로로 잇는다. */
    fun build(legs: List<List<SignalFrame>>): Run {
        require(legs.isNotEmpty() && legs.all { it.isNotEmpty() }) { "구간이 비었어요" }
        val first = legs.first().first()
        val parked = first.copy(speedKph = 0.0, gear = Gear.P, turnSignal = TurnSignal.OFF, brake = true)

        val frames = mutableListOf<SignalFrame>()
        // 출발 전: 기어 P로 서 있다. 내비는 첫 회전을 이미 알고 있다.
        var t = 0L
        while (t < PREVIEW_MS) {
            frames += parked.copy(t = t)
            t += FRAME_MS
        }
        // 구간: 각 구간의 t를 앞 구간 끝 뒤로 민다.
        var offset = PREVIEW_MS
        for (leg in legs) {
            leg.forEach { frames += it.copy(t = it.t + offset) }
            offset += leg.last().t + LEG_GAP_MS
        }
        // 도착: 안내가 없어지고 서행하다 기어 P.
        val last = frames.last()
        val arriveAt = last.t + ARRIVE_GAP_MS
        frames += last.copy(t = arriveAt, speedKph = SLOW_KPH, turnSignal = TurnSignal.OFF, tbt = null)
        frames += last.copy(t = arriveAt + FRAME_MS, speedKph = 0.0, gear = Gear.P, turnSignal = TurnSignal.OFF, brake = true, tbt = null)

        val plan = RoutePlan(
            destination = DESTINATION,
            distanceM = DISTANCE_M,
            turns = legs.mapNotNull { leg -> leg.first().tbt?.turn },
        )
        return Run(plan, frames)
    }

    private const val FRAME_MS = 1000L
    private const val SLOW_KPH = 10.0
}
