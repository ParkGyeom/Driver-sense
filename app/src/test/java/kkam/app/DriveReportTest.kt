package kkam.app

import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.LawEntry
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.SignalFrame
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kotlin.test.Test
import kotlin.test.assertEquals

class DriveReportTest {
    private fun event(id: String, rule: RuleId, result: Result, guided: Boolean, turn: TurnType = TurnType.LEFT) =
        Event(id, 11000, rule, result, guided, turn, 37.55, 127.07)

    private fun law(id: String, article: String, penalty: String) =
        LawEntry(id, "", "", article, "", penalty, "", disputed = false, note = "")

    private val laws = listOf(
        law("LAW-01", "제38조", "범칙금 3만 원"),
        law("LAW-12", "제5조", "범칙금 6만 원"),
    )

    @Test
    fun `안내 듣고 지킨 판정만 범칙금을 아낀 것으로 센다`() {
        val report = buildReport(
            listOf(
                event("evt-1", RuleId.R1, Result.KEPT, guided = true),
                event("evt-2", RuleId.R1, Result.KEPT, guided = false),
                event("evt-3", RuleId.R3, Result.KEPT, guided = true, turn = TurnType.RIGHT),
                event("evt-4", RuleId.R1, Result.MISSED, guided = true),
            ),
            laws,
        )

        assertEquals(3, report.kept)
        assertEquals(1, report.missed)
        assertEquals(90_000, report.savedWon) // R1 3만 원 + R3 6만 원
    }

    @Test
    fun `최근 판정이 먼저 오고 룰 테이블의 조항과 처분을 붙인다`() {
        val report = buildReport(
            listOf(
                event("evt-1", RuleId.R1, Result.KEPT, guided = true),
                event("evt-2", RuleId.R1, Result.MISSED, guided = false, turn = TurnType.RIGHT),
            ),
            laws,
        )

        assertEquals(
            listOf(
                ReportItem(false, "우회전 깜빡이 놓침", "놓쳤어요", "제38조", "처분: 범칙금 3만 원"),
                ReportItem(true, "좌회전 깜빡이 지킴", "안내 듣고 지켰어요", "제38조", "단속됐다면: 범칙금 3만 원"),
            ),
            report.items,
        )
    }

    @Test
    fun `룰 테이블에 없는 룰은 조항과 처분을 비운다`() {
        val report = buildReport(listOf(event("evt-1", RuleId.R2, Result.MISSED, guided = false)), laws)

        assertEquals(ReportItem(false, "깜빡이 없이 좌회전 놓침", "놓쳤어요", "", ""), report.items.single())
    }

    @Test
    fun `금액은 만 원 단위로 읽기 쉽게 쓴다`() {
        assertEquals("0원", wonLabel(0))
        assertEquals("9만 원", wonLabel(90_000))
        assertEquals("15,000원", wonLabel(15_000))
    }

    @Test
    fun `새 주행을 시작해도 오늘 판정은 남는다`() {
        val frame = SignalFrame(11000, 15.0, Gear.D, 0.0, TurnSignal.LEFT, false, 37.55, 127.07, 30.0, null, null)
        val kept = event("evt-1", RuleId.R1, Result.KEPT, guided = true)

        val state = CoachState().resetDrive("S2").next(frame, listOf(kept)).resetDrive("S8")

        assertEquals(listOf(kept), state.todayEvents)
    }
}
