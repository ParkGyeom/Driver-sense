package kkam.app

import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.SignalFrame
import kkam.engine.model.Tbt
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kotlin.test.Test
import kotlin.test.assertEquals

class CoachStateTest {
    private fun frame(signal: TurnSignal, tbt: Tbt?) = SignalFrame(
        t = 4000,
        speedKph = 30.0,
        gear = Gear.D,
        steeringDeg = 0.0,
        turnSignal = signal,
        brake = false,
        lat = 37.55,
        lon = 127.07,
        headingDeg = 90.0,
        tbt = tbt,
        light = null,
    )

    private fun event(result: Result, guided: Boolean) =
        Event("evt-1", 11000, RuleId.R1, result, guided, TurnType.LEFT, 37.55, 127.07)

    @Test
    fun `깜빡이가 꺼져 있으면 주황으로 켜라고 한다`() {
        val state = CoachState().next(frame(TurnSignal.OFF, Tbt(TurnType.LEFT, 48)), emptyList())

        assertEquals(Tone.WARN, state.tone)
        assertEquals("좌회전", state.turnLabel)
        assertEquals("48 m", state.distanceLabel)
        assertEquals("깜빡이 꺼짐", state.signalLabel)
        assertEquals("지금 켜세요", state.badge)
    }

    @Test
    fun `같은 방향 깜빡이는 파랑이고 반대 방향은 주황이다`() {
        val same = CoachState().next(frame(TurnSignal.LEFT, Tbt(TurnType.LEFT, 48)), emptyList())
        val opposite = CoachState().next(frame(TurnSignal.RIGHT, Tbt(TurnType.LEFT, 48)), emptyList())

        assertEquals(Tone.OK, same.tone)
        assertEquals("좋아요", same.badge)
        assertEquals(Tone.WARN, opposite.tone)
        assertEquals("반대쪽 깜빡이", opposite.signalLabel)
    }

    @Test
    fun `다음 안내가 회전이 아니면 대기로 돌아간다`() {
        val state = CoachState().next(frame(TurnSignal.OFF, Tbt(TurnType.STRAIGHT, 500)), emptyList())

        assertEquals(Tone.IDLE, state.tone)
        assertEquals("다음 회전 대기", state.turnLabel)
    }

    @Test
    fun `안내와 판정을 문구와 횟수와 기록에 반영한다`() {
        val quiet = frame(TurnSignal.LEFT, null)
        val guide = Guide(5000, RuleId.R1, "좌회전 곧이에요, 깜빡이 켜세요")

        val guided = CoachState().next(quiet, listOf(guide))
        assertEquals(guide.text, guided.message)

        val kept = guided.next(quiet, listOf(event(Result.KEPT, guided = true)))
        assertEquals("안내 듣고 잘 켰어요", kept.message)
        assertEquals(1 to 1, kept.kept to kept.total)
        assertEquals(listOf("5초 음성 안내", "11초 좌회전 지킴"), kept.logs)

        val missed = kept.next(quiet, listOf(event(Result.MISSED, guided = true)))
        assertEquals("이번 좌회전은 깜빡이를 놓쳤어요", missed.message)
        assertEquals(1 to 2, missed.kept to missed.total)
    }

    @Test
    fun `새 주행은 카드만 비우고 횟수와 기록은 남긴다`() {
        val state = CoachState(tone = Tone.WARN, message = "안내", kept = 1, total = 2, logs = listOf("기록"))

        val reset = state.resetDrive("S2")

        assertEquals(Tone.IDLE, reset.tone)
        assertEquals("", reset.message)
        assertEquals(1 to 2, reset.kept to reset.total)
        assertEquals(listOf("기록"), reset.logs)
    }

    @Test
    fun `깜빡이를 켜면 안내 문구가 바뀌고 켜둔 채 시작한 주행에는 문구가 없다`() {
        val guide = Guide(4000, RuleId.R1, "좌회전 곧이에요, 깜빡이 켜세요")
        val near = Tbt(TurnType.LEFT, 48)

        val guided = CoachState().resetDrive("S2").next(frame(TurnSignal.OFF, near), listOf(guide))
        val answered = guided.next(frame(TurnSignal.LEFT, near), emptyList())
        assertEquals("깜빡이를 켰어요", answered.message)
        assertEquals(listOf("S2 4초 음성 안내"), answered.logs)

        val quiet = CoachState().resetDrive("S1").next(frame(TurnSignal.LEFT, near), emptyList())
        assertEquals("", quiet.message)
    }
}
