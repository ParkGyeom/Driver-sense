package kkam.engine.rules

import kkam.engine.Engine
import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.GuideAction
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.SignalFrame
import kkam.engine.model.TurnSignal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** 시나리오 S33~S35가 다루지 않는 R9 전이표의 행과 출력 필드를 확인한다. */
class R9RuleTest {
    @Test
    fun guideAndEventFields() {
        val outputs = r9(frame(0, 40.0), frame(1000, 18.0))

        val guide = assertIs<Guide>(outputs[0])
        assertEquals("방금 급하게 멈췄어요, 앞차와 거리를 넉넉히 두세요", guide.text)
        assertEquals(GuideAction.KEEP_DISTANCE, guide.action)
        val event = assertIs<Event>(outputs[1])
        assertEquals(Result.MISSED, event.result)
        assertEquals(false, event.guided)
        assertNull(event.turn)
    }

    @Test
    fun decelerationFormula() {
        // 40 → 18 km/h를 1초에: 22 ÷ 3.6 ≈ 6.1 m/s²
        assertEquals(6.11, deceleration(40.0, 0, 18.0, 1000), 0.01)
        // 2초 간격이면 절반
        assertEquals(3.06, deceleration(40.0, 0, 18.0, 2000), 0.01)
    }

    @Test
    fun countsAgainAfterCooldown() {
        // 전이표 #2: 10초가 지나면 다시 센다
        val outputs = r9(
            frame(0, 40.0), frame(1000, 18.0),
            frame(5000, 40.0), frame(6000, 18.0), // 쉬는 시간 안: 세지 않음
            frame(11000, 40.0), frame(12000, 18.0), // 쉬는 시간 끝난 뒤: 다시 셈
        )

        assertEquals(listOf(1000L, 12000L), outputs.filterIsInstance<Event>().map { it.t })
    }

    @Test
    fun longFrameGapIsIgnored() {
        // 신호가 끊겨 5초 만에 온 프레임은 감속도를 계산하지 않는다 (MAX_FRAME_GAP_MS)
        assertEquals(emptyList(), r9(frame(0, 80.0), frame(5000, 10.0)))
    }

    @Test
    fun gentleBrakeBelowThresholdIsIgnored() {
        // 1초에 10 km/h ≈ 2.8 m/s²: 기준(3.0) 미만
        assertEquals(emptyList(), r9(frame(0, 50.0), frame(1000, 40.0)))
    }

    @Test
    fun parkingResets() {
        // 전이표 #0: 기어 P면 쉬는 시간을 끝내고 처음부터 본다
        val outputs = r9(frame(0, 40.0), frame(1000, 18.0), frame(2000, 0.0, Gear.P), frame(3000, 40.0), frame(4000, 18.0))

        assertEquals(2, outputs.filterIsInstance<Event>().size)
    }

    /** 엔진 전체를 돌리고 R9 출력만 남긴다. */
    private fun r9(vararg frames: SignalFrame): List<EngineOutput> =
        Engine().let { engine -> frames.flatMap { engine.onFrame(it) } }.filter { it.rule == RuleId.R9 }

    private fun frame(t: Long, speedKph: Double, gear: Gear = Gear.D) = SignalFrame(
        t = t,
        speedKph = speedKph,
        gear = gear,
        steeringDeg = 0.0,
        turnSignal = TurnSignal.OFF,
        brake = false,
        lat = 37.55,
        lon = 127.07,
        headingDeg = 90.0,
        tbt = null,
        light = null,
    )
}
