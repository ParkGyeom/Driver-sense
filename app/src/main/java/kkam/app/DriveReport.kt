package kkam.app

import kkam.engine.model.Event
import kkam.engine.model.LawEntry
import kkam.engine.model.Result
import kkam.engine.model.RuleId

/** 기어 P에서 보여줄 오늘 주행 리포트 (기획서 UI-2). Android에 의존하지 않아서 JVM 테스트로 확인한다. */
data class DriveReport(
    val kept: Int,
    val missed: Int,
    /** 안내를 듣고 지켜서 아낀 범칙금 (원). */
    val savedWon: Int,
    /** 최근 판정이 먼저 온다. */
    val items: List<ReportItem>,
)

/** 판정 카드 한 장. */
data class ReportItem(
    val kept: Boolean,
    /** 예: "좌회전 깜빡이 지킴" */
    val title: String,
    /** 예: "안내 듣고 지켰어요" */
    val detail: String,
    /** 법 조항. 룰 테이블에 없으면 빈 문자열. */
    val article: String,
    /** 예: "단속됐다면: 승용차 범칙금 3만 원 ..." 룰 테이블에 없으면 빈 문자열. */
    val penalty: String,
)

/**
 * 오늘 판정과 법규 룰 테이블로 리포트를 만든다.
 * 아낀 범칙금 = 안내 후 지킨 판정 수 × 룰별 범칙금 (docs/tasks.md 단계 16과 같은 식).
 */
fun buildReport(events: List<Event>, laws: List<LawEntry>): DriveReport {
    val lawById = laws.associateBy { it.id }
    return DriveReport(
        kept = events.count { it.result == Result.KEPT },
        missed = events.count { it.result == Result.MISSED },
        savedWon = events.filter { it.guided && it.result == Result.KEPT }.sumOf { FINE_WON.getValue(it.rule) },
        items = events.reversed().map { it.toItem(lawById[LAW_ID.getValue(it.rule)]) },
    )
}

/** 30000 → "3만 원", 0 → "0원" */
fun wonLabel(won: Int): String = when {
    won == 0 -> "0원"
    won % 10_000 == 0 -> "${won / 10_000}만 원"
    else -> "%,d원".format(won)
}

private fun Event.toItem(law: LawEntry?): ReportItem {
    val kept = result == Result.KEPT
    return ReportItem(
        kept = kept,
        title = "${ruleTitle()} ${result.label()}",
        detail = when {
            !kept -> "놓쳤어요"
            guided -> "안내 듣고 지켰어요"
            else -> "안내 없이 지켰어요"
        },
        article = law?.article.orEmpty(),
        penalty = law?.penalty?.let { if (kept) "단속됐다면: $it" else "처분: $it" }.orEmpty(),
    )
}

private fun Event.ruleTitle(): String = when (rule) {
    RuleId.R1 -> "${turn.label()} 깜빡이"
    RuleId.R2 -> "깜빡이 없이 ${turn.label()}"
    RuleId.R3 -> "적색 우회전 일시정지"
}

/** 룰별 범칙금 (승용차, 기획서 5.2). */
private val FINE_WON = mapOf(RuleId.R1 to 30_000, RuleId.R2 to 30_000, RuleId.R3 to 60_000)

/**
 * 룰과 룰 테이블 행의 연결. 룰 테이블에 룰 필드가 생기면 그걸 쓴다.
 * R1: 회전 전 방향지시등, R2: 깜빡이 없이 회전, R3: 적색 신호 우회전 일시정지.
 */
private val LAW_ID = mapOf(RuleId.R1 to "LAW-01", RuleId.R2 to "LAW-03", RuleId.R3 to "LAW-12")
