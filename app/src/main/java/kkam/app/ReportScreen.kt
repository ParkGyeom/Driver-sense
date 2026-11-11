package kkam.app

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kkam.engine.model.Event
import kkam.engine.model.LawEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * 오늘 주행 리포트 (기획서 UI-2, screen_report.xml). 기어 P일 때만 보인다.
 * 값은 [buildReport]에서만 온다. 앱에서 이 화면만 흰 배경이라 색은 모두 paper_* 를 쓴다.
 *
 * 화면 구성 (읽는 순서)
 * 1. 제목 + 핵심 요약 한 줄
 * 2. 요약 수치 카드 3개: 지킴, 놓침, 아낀 범칙금
 * 3. 판정 기록 리스트: 시간 | 상황 | 결과 칩, 아래 조항·처분. 비었으면 아이콘 + 안내 문구
 */
class ReportScreen(
    private val activity: Activity,
    /** 법규 룰 테이블. 리포트를 처음 그릴 때 읽는다. */
    private val laws: () -> List<LawEntry>,
) {
    private val root: View = activity.findViewById(R.id.reportScreen)
    private val summary: TextView = activity.findViewById(R.id.reportSummary)
    private val statKept: View = activity.findViewById(R.id.reportStatKept)
    private val statMissed: View = activity.findViewById(R.id.reportStatMissed)
    private val statSaved: View = activity.findViewById(R.id.reportStatSaved)
    private val items: LinearLayout = activity.findViewById(R.id.reportItems)
    private val empty: View = activity.findViewById(R.id.reportEmpty)

    /** 리포트를 마지막으로 그린 판정 목록. 같으면 다시 그리지 않는다. */
    private var drawnFor: List<Event>? = null

    init {
        listOf(statKept, statMissed, statSaved).forEach { it.background = paperCard() }
        activity.findViewById<View>(R.id.reportListCard).background = paperCard()
        activity.findViewById<View>(R.id.reportEmptyIcon).background = activity.rounded(color(R.color.paper), radiusDp = 999)
        activity.findViewById<View>(R.id.reportAsk).background =
            activity.pillBackground(color(R.color.paper_card), strokeColor = color(R.color.paper_line))

        // 수치 카드의 고정 부분: 기호, 라벨, 숫자 색
        statKept.setStatHead("✓", R.string.report_kept, R.color.paper_ok, R.color.paper_ok_soft)
        statMissed.setStatHead("!", R.string.report_missed, R.color.paper_miss, R.color.paper_miss_soft)
        statSaved.setStatHead("₩", R.string.report_saved, R.color.paper_text, R.color.paper)
    }

    fun show(screen: Screen) {
        root.visibility = if (screen == Screen.REPORT) View.VISIBLE else View.GONE
    }

    /** 판정 목록이 바뀌었을 때만 다시 그린다. */
    fun render(state: CoachState) {
        if (drawnFor == state.todayEvents) return
        drawnFor = state.todayEvents
        // report.items는 최근 판정이 먼저라 시간도 같은 순서로 맞춘다
        draw(buildReport(state.todayEvents, laws()), times = state.todayEvents.reversed().map { it.t })
    }

    private fun draw(report: DriveReport, times: List<Long>) {
        val total = report.kept + report.missed
        // "0원"도 다른 카드처럼 숫자 크게, 단위 작게 보이게 띄어 쓴다
        val savedLabel = if (report.savedWon == 0) ZERO_WON else wonLabel(report.savedWon)

        // 1. 핵심 요약 한 줄
        summary.text = when {
            total == 0 -> activity.getString(R.string.parked_report_summary_empty)
            report.savedWon > 0 -> activity.getString(R.string.parked_report_summary_saved, total, report.kept, savedLabel)
            else -> activity.getString(R.string.parked_report_summary, total, report.kept)
        }

        // 2. 수치 카드 3개
        statKept.setStatBody(
            value = countLabel(report.kept),
            caption = if (total == 0) {
                activity.getString(R.string.parked_report_none_caption)
            } else {
                activity.getString(R.string.parked_report_kept_caption, (report.kept * 100.0 / total).roundToInt())
            },
        )
        statMissed.setStatBody(
            value = countLabel(report.missed),
            caption = activity.getString(
                if (report.missed == 0) R.string.parked_report_missed_none else R.string.parked_report_missed_caption,
            ),
        )
        // 놓침 0번은 나쁜 소식이 아니므로 빨강 대신 회색 숫자로
        statMissed.findViewById<TextView>(R.id.statValue)
            .setTextColor(color(if (report.missed == 0) R.color.paper_muted else R.color.paper_miss))
        statSaved.setStatBody(value = savedLabel, caption = activity.getString(R.string.parked_report_saved_caption))

        // 3. 판정 기록
        empty.visibility = if (report.items.isEmpty()) View.VISIBLE else View.GONE
        items.removeAllViews()
        report.items.forEachIndexed { index, item ->
            if (index > 0) items.addView(divider())
            addItem(item, times.getOrNull(index))
        }
    }

    /** 수치 카드 위쪽: 기호 동그라미, 라벨, 숫자 색. */
    private fun View.setStatHead(symbol: String, labelRes: Int, toneRes: Int, softRes: Int) {
        findViewById<TextView>(R.id.statIcon).apply {
            text = symbol
            setTextColor(color(toneRes))
            background = activity.rounded(color(softRes), radiusDp = 999)
        }
        findViewById<TextView>(R.id.statLabel).setText(labelRes)
        findViewById<TextView>(R.id.statValue).setTextColor(color(toneRes))
    }

    /** 수치 카드 아래쪽: "3만 원"(숫자 크게, 단위 작게)과 보조 설명. */
    private fun View.setStatBody(value: String, caption: String) {
        findViewById<TextView>(R.id.statValue).text = numberEmphasis(value)
        findViewById<TextView>(R.id.statCaption).text = caption
    }

    /** 판정 한 줄: 시간 | 상황 + 설명 | 결과 칩, 아래 조항·처분. */
    private fun addItem(item: ReportItem, t: Long?) {
        val row = activity.layoutInflater.inflate(R.layout.report_item, items, false)
        val tone = color(if (item.kept) R.color.paper_ok else R.color.paper_miss)

        row.findViewById<TextView>(R.id.itemTime).text = t?.let(::timeLabel).orEmpty()
        // 결과는 오른쪽 칩이 말하므로 제목에서는 "지킴"/"놓침"을 뗀다. 예: "좌회전 깜빡이 지킴" → "좌회전 깜빡이"
        row.findViewById<TextView>(R.id.itemTitle).text = item.title.substringBeforeLast(" ")
        row.findViewById<TextView>(R.id.itemDetail).text = item.detail
        row.findViewById<TextView>(R.id.itemResult).apply {
            setText(if (item.kept) R.string.parked_report_kept_chip else R.string.parked_report_missed_chip)
            setTextColor(tone)
            background = activity.pillBackground(color(if (item.kept) R.color.paper_ok_soft else R.color.paper_miss_soft))
        }
        row.findViewById<TextView>(R.id.itemArticle).showOrHide(item.article)
        row.findViewById<TextView>(R.id.itemPenalty).apply {
            showOrHide(item.penalty)
            setTextColor(if (item.kept) color(R.color.paper_muted) else tone)
        }
        items.addView(row)
    }

    /** 판정 줄 사이 구분선. */
    private fun divider() = View(activity).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(1))
        setBackgroundColor(color(R.color.paper_line))
    }

    /** 흰 카드 + 얇은 테두리. */
    private fun paperCard() =
        activity.rounded(color(R.color.paper_card), radiusDp = PAPER_RADIUS_DP, strokeColor = color(R.color.paper_line))

    /** 글자가 비었으면(룰 테이블에 없음) 줄을 숨긴다. */
    private fun TextView.showOrHide(label: String) {
        text = label
        visibility = if (label.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun countLabel(count: Int) = activity.getString(R.string.parked_report_count, count)

    private fun color(id: Int) = activity.getColor(id)

    private companion object {
        const val PAPER_RADIUS_DP = 24
        const val ZERO_WON = "0 원"

        /** 이보다 작은 t는 시나리오 시각(0부터 ms), 크면 실제 시각(epoch ms). CoachState의 판정 기록과 같은 기준. */
        const val EPOCH_START_MS = 1_000_000_000_000L
        val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Seoul"))

        /** 시나리오: "42초", 실제 주행: "14:32" */
        fun timeLabel(t: Long): String =
            if (t < EPOCH_START_MS) "${t / 1000}초" else CLOCK.format(Instant.ofEpochMilli(t))
    }
}
