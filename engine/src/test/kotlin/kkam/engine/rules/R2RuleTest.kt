package kkam.engine.rules

import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Result
import kkam.engine.model.SignalFrame
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class R2RuleTest {
    @Test
    fun signedHeadingDiffShowsDirection() {
        assertEquals(20.0, signedHeadingDiff(350.0, 10.0))
        assertEquals(-20.0, signedHeadingDiff(10.0, 350.0))
        assertEquals(-60.0, signedHeadingDiff(90.0, 30.0))
    }

    @Test
    fun eventCarriesDirectionAndIsNeverGuided() {
        val right = turn(R2Rule { "evt-1" }, from = 0.0, to = 90.0, signal = TurnSignal.RIGHT)
        val left = turn(R2Rule { "evt-1" }, from = 90.0, to = 0.0, signal = TurnSignal.OFF)

        assertEquals(TurnType.RIGHT, right.turn)
        assertEquals(Result.KEPT, right.result)
        assertEquals(false, right.guided)
        assertEquals(TurnType.LEFT, left.turn)
        assertEquals(Result.MISSED, left.result)
    }

    @Test
    fun worksWithoutSteeringAngle() {
        val event = turn(R2Rule { "evt-1" }, from = 0.0, to = 90.0, signal = TurnSignal.RIGHT, steeringDeg = null)

        assertEquals(Result.KEPT, event.result)
    }

    @Test
    fun hazardDoesNotCountAsTurnSignal() {
        val event = turn(R2Rule { "evt-1" }, from = 0.0, to = 90.0, signal = TurnSignal.HAZARD)

        assertEquals(Result.MISSED, event.result)
    }

    @Test
    fun oppositeSignalIsMissed() {
        val event = turn(R2Rule { "evt-1" }, from = 0.0, to = 90.0, signal = TurnSignal.LEFT)

        assertEquals(Result.MISSED, event.result)
    }

    @Test
    fun turnThatNeverReachesSixtyDegreesIsCancelled() {
        val rule = R2Rule { "evt-1" }
        rule.onFrame(frame(t = 0, heading = 0.0), otherRuleBusy = false)
        rule.onFrame(frame(t = 2000, heading = 20.0), otherRuleBusy = false) // 회전 시작 (10°/s)
        rule.onFrame(frame(t = 13000, heading = 40.0), otherRuleBusy = false) // 창(10초) 지나 취소
        val later = rule.onFrame(frame(t = 15000, heading = 70.0), otherRuleBusy = false)

        // 취소 뒤 새 회전이 15°/s로 시작될 뿐, 처음 기준(0°)으로 70°를 판정하지 않는다
        assertEquals(emptyList(), later)
    }

    @Test
    fun staysQuietWhileAndShortlyAfterOtherRuleOwnsTheTurn() {
        val rule = R2Rule { "evt-1" }
        rule.onFrame(frame(t = 0, heading = 0.0), otherRuleBusy = true)
        rule.onFrame(frame(t = 2000, heading = 60.0), otherRuleBusy = true)
        // R1이 끝난 뒤 남은 회전(60° → 180°)은 쉬는 시간(주행 10초) 안이라 기록하지 않는다
        val tail = listOf(
            rule.onFrame(frame(t = 4000, heading = 120.0), otherRuleBusy = false),
            rule.onFrame(frame(t = 6000, heading = 180.0), otherRuleBusy = false),
        ).flatten()

        assertEquals(emptyList(), tail)
    }

    @Test
    fun parkingCooldownCountsOnlyDrivingTime() {
        val rule = R2Rule { "evt-1" }
        rule.onFrame(frame(t = 0, heading = 0.0, gear = Gear.P, speedKph = 0.0), otherRuleBusy = false)
        // 70초 동안 서 있었지만 주행 시간이 아니므로 아직 쉬는 중
        rule.onFrame(frame(t = 70000, heading = 0.0, speedKph = 0.0), otherRuleBusy = false)
        rule.onFrame(frame(t = 72000, heading = 30.0), otherRuleBusy = false)
        val outputs = rule.onFrame(frame(t = 74000, heading = 90.0), otherRuleBusy = false)

        assertEquals(emptyList(), outputs)
    }

    /** 직진 두 프레임 뒤 2초마다 진행 방향을 반씩 바꿔 회전하고 EVENT를 돌려준다. */
    private fun turn(
        rule: R2Rule,
        from: Double,
        to: Double,
        signal: TurnSignal,
        steeringDeg: Double? = 0.0,
    ): Event {
        val middle = from + signedHeadingDiff(from, to) / 2
        val outputs = mutableListOf<EngineOutput>()
        outputs += rule.onFrame(frame(t = 0, heading = from, signal = signal, steeringDeg = steeringDeg), false)
        outputs += rule.onFrame(frame(t = 2000, heading = from, signal = signal, steeringDeg = steeringDeg), false)
        outputs += rule.onFrame(frame(t = 4000, heading = middle, signal = signal, steeringDeg = steeringDeg), false)
        outputs += rule.onFrame(frame(t = 6000, heading = to, signal = signal, steeringDeg = steeringDeg), false)
        return assertIs<Event>(outputs.single())
    }

    private fun frame(
        t: Long,
        heading: Double,
        signal: TurnSignal = TurnSignal.OFF,
        gear: Gear = Gear.D,
        speedKph: Double = 15.0,
        steeringDeg: Double? = 0.0,
    ) = SignalFrame(
        t = t,
        speedKph = speedKph,
        gear = gear,
        steeringDeg = steeringDeg,
        turnSignal = signal,
        brake = false,
        lat = 37.55,
        lon = 127.07,
        headingDeg = heading.mod(360.0),
        tbt = null,
        light = null,
    )
}
