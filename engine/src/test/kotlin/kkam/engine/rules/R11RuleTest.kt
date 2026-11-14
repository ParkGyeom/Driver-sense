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

/** 시나리오 S39~S41이 다루지 않는 R11 전이표의 행과 출력 필드를 확인한다. */
class R11RuleTest {
    @Test
    fun guideAndEventFields() {
        val outputs = r11(frame(0, 80.0, 40), frame(2000, 80.0, 40), frame(3000, 40.0, 40))

        val guide = assertIs<Guide>(outputs[0])
        assertEquals("제한속도 40이에요, 속도를 줄이세요", guide.text)
        assertEquals(GuideAction.SLOW_DOWN, guide.action)
        val event = assertIs<Event>(outputs[1])
        assertEquals(Result.KEPT, event.result)
        assertEquals(true, event.guided)
        assertNull(event.turn)
    }

    @Test
    fun shortOverIsIgnored() {
        // 전이표 #2: 2초가 되기 전에 여유 안으로 돌아오면 안내하지 않는다
        assertEquals(emptyList(), r11(frame(0, 80.0, 60), frame(1000, 75.0, 60), frame(2000, 68.0, 60), frame(3000, 80.0, 60)))
    }

    @Test
    fun unknownLimitDoesNothing() {
        assertEquals(emptyList(), r11(frame(0, 120.0, null), frame(5000, 120.0, null)))
    }

    @Test
    fun guidesAgainAfterComingBackUnderLimit() {
        // 전이표 #6: 판정 뒤 제한속도 이하로 내려오면 다음 과속을 다시 본다
        val outputs = r11(
            frame(0, 80.0, 60), frame(2000, 80.0, 60), frame(3000, 60.0, 60), frame(3500, 60.0, 60),
            frame(4000, 80.0, 60), frame(6000, 80.0, 60), frame(16000, 80.0, 60),
        )

        assertEquals(listOf("GUIDE", "KEPT", "GUIDE", "MISSED"), outputs.map { it.kind() })
    }

    @Test
    fun staysQuietAfterMissedUntilUnderLimit() {
        // 놓친 뒤 계속 빠르면 같은 과속을 다시 안내하지 않는다
        val outputs = r11(frame(0, 80.0, 60), frame(2000, 80.0, 60), frame(12000, 80.0, 60), frame(30000, 80.0, 60))

        assertEquals(listOf("GUIDE", "MISSED"), outputs.map { it.kind() })
    }

    @Test
    fun parkingCancels() {
        val outputs = r11(frame(0, 80.0, 40), frame(2000, 80.0, 40), frame(3000, 0.0, 40, Gear.P), frame(4000, 40.0, 40))

        assertEquals(listOf("GUIDE"), outputs.map { it.kind() })
    }

    private fun EngineOutput.kind(): String = when (this) {
        is Guide -> "GUIDE"
        is Event -> result.name
    }

    /** 엔진 전체를 돌리고 R11 출력만 남긴다. */
    private fun r11(vararg frames: SignalFrame): List<EngineOutput> =
        Engine().let { engine -> frames.flatMap { engine.onFrame(it) } }.filter { it.rule == RuleId.R11 }

    private fun frame(t: Long, speedKph: Double, limit: Int?, gear: Gear = Gear.D) = SignalFrame(
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
        speedLimitKph = limit,
    )
}
