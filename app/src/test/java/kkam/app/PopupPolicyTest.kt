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
import kotlin.test.assertNull

class PopupPolicyTest {
    private fun frame(signal: TurnSignal, tbt: Tbt?, gear: Gear = Gear.D) =
        SignalFrame(4000, 30.0, gear, 0.0, signal, false, 37.55, 127.07, 90.0, tbt, null)

    private val guide = Guide(4000, RuleId.R1, "좌회전 곧이에요, 깜빡이 켜세요")
    private val kept = Event("evt-1", 11000, RuleId.R1, Result.KEPT, true, TurnType.LEFT, 37.55, 127.07)
    private val near = Tbt(TurnType.LEFT, 60)

    /** 프레임과 엔진 출력을 [CoachState]와 팝업에 차례로 반영한다. */
    private fun step(state: CoachState, popup: Popup?, frame: SignalFrame, outputs: List<kkam.engine.model.EngineOutput>): Pair<CoachState, Popup?> {
        val next = state.next(frame, outputs)
        return next to nextPopup(popup, next, outputs, frame.gear)
    }

    @Test
    fun `음성 안내 전에는 회전이 가까워도 띄우지 않는다`() {
        val (_, popup) = step(CoachState(), null, frame(TurnSignal.OFF, Tbt(TurnType.LEFT, 120)), emptyList())

        assertNull(popup)
    }

    @Test
    fun `음성 안내가 나오면 주황, 깜빡이를 켜면 파랑으로 바뀐다`() {
        val (guided, warn) = step(CoachState(), null, frame(TurnSignal.OFF, near), listOf(guide))
        assertEquals(Popup(PopupTone.WARN, "좌회전", "60 m", "지금 켜세요"), warn)

        val (_, ok) = step(guided, warn, frame(TurnSignal.LEFT, Tbt(TurnType.LEFT, 40)), emptyList())
        assertEquals(Popup(PopupTone.OK, "좌회전", "40 m", "좋아요"), ok)
    }

    @Test
    fun `판정이 나오면 숨긴다`() {
        val (guided, warn) = step(CoachState(), null, frame(TurnSignal.OFF, near), listOf(guide))

        val (_, popup) = step(guided, warn, frame(TurnSignal.LEFT, near), listOf(kept))

        assertNull(popup)
    }

    @Test
    fun `처음부터 깜빡이를 켜고 와서 안내가 없으면 띄우지 않는다`() {
        val (_, popup) = step(CoachState(), null, frame(TurnSignal.LEFT, near), emptyList())

        assertNull(popup)
    }

    @Test
    fun `회전 안내가 끝나거나 기어가 P면 숨긴다`() {
        val (guided, warn) = step(CoachState(), null, frame(TurnSignal.OFF, near), listOf(guide))

        assertNull(step(guided, warn, frame(TurnSignal.OFF, Tbt(TurnType.STRAIGHT, 500)), emptyList()).second)
        assertNull(step(guided, warn, frame(TurnSignal.OFF, near, Gear.P), emptyList()).second)
    }
}
