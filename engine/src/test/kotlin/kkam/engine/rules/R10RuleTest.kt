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
import kkam.engine.model.Tbt
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 시나리오 S36~S38이 다루지 않는 R10 전이표의 행과 출력 필드를 확인한다. */
class R10RuleTest {
    @Test
    fun guideAndEventFields() {
        val outputs = r10(
            frame(0, 140, 60.0, TurnType.UTURN),
            frame(1000, 120, 60.0, TurnType.UTURN),
            frame(2000, 28, 25.0, TurnType.UTURN),
        )

        val guide = assertIs<Guide>(outputs[0])
        assertEquals("회전이 가까워요, 지금부터 천천히 속도를 줄이세요", guide.text)
        assertEquals(GuideAction.SLOW_DOWN, guide.action)
        val event = assertIs<Event>(outputs[1])
        assertEquals(Result.KEPT, event.result)
        assertEquals(true, event.guided)
        assertEquals(TurnType.UTURN, event.turn)
    }

    @Test
    fun fastWithoutGuideIsMissed() {
        // 50 km/h 이하라 안내는 없었지만 30 m 지점에서 40 km/h: 전이표 #3, guided = false
        val outputs = r10(frame(0, 140, 45.0), frame(1000, 80, 45.0), frame(2000, 28, 40.0))

        val event = assertIs<Event>(outputs.single())
        assertEquals(Result.MISSED, event.result)
        assertEquals(false, event.guided)
    }

    @Test
    fun rerouteCancels() {
        // 전이표 #2: 안내가 다른 회전으로 바뀌면 취소
        val outputs = r10(frame(0, 140, 60.0), frame(1000, 120, 60.0), frame(2000, 90, 60.0, TurnType.RIGHT), frame(3000, 25, 60.0, TurnType.RIGHT))

        assertEquals(listOf("GUIDE"), outputs.map { it.kind() })
    }

    @Test
    fun nextTurnSameDirectionIsWatchedAgain() {
        // 전이표 #6: 판정 뒤 같은 방향 다음 회전(거리가 크게 늘어남)으로 넘어가면 대기로 돌아가 다시 본다
        val outputs = r10(
            frame(0, 140, 60.0), frame(1000, 100, 55.0), frame(2000, 25, 25.0),
            frame(3000, 140, 40.0), frame(4000, 120, 40.0), frame(5000, 100, 60.0), frame(6000, 25, 50.0),
        )

        assertEquals(listOf("GUIDE", "KEPT", "GUIDE", "MISSED"), outputs.map { it.kind() })
    }

    @Test
    fun turnAlreadyInsideCheckIsIgnored() {
        // 처음 본 회전이 이미 30 m 안이면 다가오는 모습을 못 봐서 보지 않는다
        assertEquals(emptyList(), r10(frame(0, 25, 60.0), frame(1000, 10, 60.0)))
    }

    @Test
    fun parkingCancels() {
        val outputs = r10(frame(0, 140, 60.0), frame(1000, 120, 60.0), frame(2000, 110, 60.0, gear = Gear.P), frame(3000, 28, 40.0))

        assertEquals(listOf("GUIDE"), outputs.map { it.kind() })
    }

    private fun EngineOutput.kind(): String = when (this) {
        is Guide -> "GUIDE"
        is Event -> result.name
    }

    /** 엔진 전체를 돌리고 R10 출력만 남긴다. */
    private fun r10(vararg frames: SignalFrame): List<EngineOutput> =
        Engine().let { engine -> frames.flatMap { engine.onFrame(it) } }.filter { it.rule == RuleId.R10 }

    private fun frame(t: Long, distanceM: Int, speedKph: Double, turn: TurnType = TurnType.LEFT, gear: Gear = Gear.D) = SignalFrame(
        t = t,
        speedKph = speedKph,
        gear = gear,
        steeringDeg = 0.0,
        turnSignal = TurnSignal.LEFT,
        brake = false,
        lat = 37.55,
        lon = 127.07,
        headingDeg = 90.0,
        tbt = Tbt(turn, distanceM),
        light = null,
    )
}
