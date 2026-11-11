package kkam.app

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kkam.engine.model.Event
import kkam.engine.model.LawEntry
import kotlin.math.roundToInt

/**
 * 오늘 주행 리포트 (기획서 UI-2, screen_report.xml). 기어 P일 때만 보인다.
 * 내비 경로를 따라 달렸으면 그 경로의 판정만 묶은 경로 리포트가 된다.
 * 값은 [buildReport]에서만 온다.
 *
 * 화면 구성
 * - 히어로: 아낀 범칙금 (숫자 크게, 단위 작게)
 * - 지킴률 링 + "✓ 지킴 n번", "! 놓침 m번"
 * - 판정 기록 타임라인 (최근 것이 위). 비었으면 아이콘 + 안내 문구
 */
class ReportScreen(
    private val activity: Activity,
    /** 법규 룰 테이블. 리포트를 처음 그릴 때 읽는다. */
    private val laws: () -> List<LawEntry>,
) {
    private val root: View = activity.findViewById(R.id.reportScreen)
    private val title: TextView = activity.findViewById(R.id.reportTitle)
    private val summary: TextView = activity.findViewById(R.id.reportSummary)
    private val saved: TextView = activity.findViewById(R.id.reportSaved)
    private val ring: RateRingView = activity.findViewById(R.id.reportRing)
    private val rate: TextView = activity.findViewById(R.id.reportRate)
    private val kept: TextView = activity.findViewById(R.id.reportKept)
    private val missed: TextView = activity.findViewById(R.id.reportMissed)
    private val items: LinearLayout = activity.findViewById(R.id.reportItems)
    private val empty: View = activity.findViewById(R.id.reportEmpty)

    /** 리포트를 마지막으로 그린 경로와 판정 목록. 같으면 다시 그리지 않는다. */
    private var drawnFor: Pair<RouteTrip?, List<Event>>? = null

    init {
        activity.findViewById<View>(R.id.reportHero).background = activity.cardBackground()
        activity.findViewById<View>(R.id.reportRateCard).background = activity.cardBackground()
        activity.findViewById<View>(R.id.reportEmptyIcon).background = activity.rounded(color(R.color.surface), radiusDp = 999)
        activity.findViewById<View>(R.id.reportAsk).background =
            activity.pillBackground(color(R.color.bg), strokeColor = color(R.color.line))
    }

    fun show(screen: Screen) {
        root.visibility = if (screen == Screen.REPORT) View.VISIBLE else View.GONE
    }

    /**
     * 판정 목록이나 경로가 바뀌었을 때만 다시 그린다.
     * 내비 경로를 따라 달렸으면 그 경로의 판정만 묶어 "○○ 경로 리포트"로, 아니면 오늘 전체 리포트로 그린다.
     */
    fun render(state: CoachState) {
        val key = state.route to state.todayEvents
        if (drawnFor == key) return
        drawnFor = key
        val trip = state.route
        draw(buildReport(trip?.events(state.todayEvents) ?: state.todayEvents, laws()))
        title.text = trip?.let { activity.getString(R.string.route_report_title, it.plan.destination) }
            ?: activity.getString(R.string.report_title)
        if (trip != null) summary.text = routeSummary(trip, state.todayEvents)
    }

    private fun draw(report: DriveReport) {
        val total = report.kept + report.missed
        summary.text = if (total == 0) {
            activity.getString(R.string.parked_report_summary_empty)
        } else {
            activity.getString(R.string.parked_report_summary, total)
        }
        saved.text = numberEmphasis(wonLabel(report.savedWon))

        // 지킴률 링: 판정이 없으면 "–"와 빈 링
        ring.setCounts(kept = report.kept, missed = report.missed)
        rate.text = if (total == 0) {
            activity.getString(R.string.parked_report_rate_none)
        } else {
            numberEmphasis("${(report.kept * 100.0 / total).roundToInt()} %")
        }
        kept.text = activity.getString(R.string.parked_report_kept_count, report.kept)
        missed.text = activity.getString(R.string.parked_report_missed_count, report.missed)

        empty.visibility = if (report.items.isEmpty()) View.VISIBLE else View.GONE
        items.removeAllViews()
        report.items.forEachIndexed { index, item -> addItem(item, isLast = index == report.items.lastIndex) }
    }

    /** 타임라인 한 줄. 지킴 = 파랑 ✓, 놓침 = 빨강 !. */
    private fun addItem(item: ReportItem, isLast: Boolean) {
        val row = activity.layoutInflater.inflate(R.layout.report_item, items, false)
        val tone = color(if (item.kept) R.color.ok else R.color.miss)
        val toneSoft = color(if (item.kept) R.color.ok_soft else R.color.miss_soft)

        row.findViewById<ImageView>(R.id.itemIcon).apply {
            setImageResource(if (item.kept) R.drawable.ic_kk_check else R.drawable.ic_kk_alert)
            imageTintList = ColorStateList.valueOf(tone)
            background = activity.rounded(toneSoft, radiusDp = 999)
        }
        // 마지막 줄 아래로는 선을 잇지 않는다
        row.findViewById<View>(R.id.itemLine).visibility = if (isLast) View.INVISIBLE else View.VISIBLE

        row.findViewById<View>(R.id.itemCard).background = activity.cardBackground()
        row.findViewById<TextView>(R.id.itemTitle).apply {
            text = item.title
            setTextColor(if (item.kept) color(R.color.text) else tone)
        }
        row.findViewById<TextView>(R.id.itemDetail).text = item.detail
        row.findViewById<TextView>(R.id.itemArticle).chip(item.article, color(R.color.surface_2))
        row.findViewById<TextView>(R.id.itemPenalty).apply {
            chip(item.penalty, if (item.kept) color(R.color.surface_2) else toneSoft)
            setTextColor(if (item.kept) color(R.color.muted) else tone)
        }
        items.addView(row)
    }

    /** 칩 모양으로 칠한다. 글자가 비었으면(룰 테이블에 없음) 숨긴다. */
    private fun TextView.chip(label: String, background: Int) {
        text = label
        visibility = if (label.isEmpty()) View.GONE else View.VISIBLE
        this.background = activity.rounded(background, radiusDp = CHIP_RADIUS_DP)
    }

    private fun color(id: Int) = activity.getColor(id)

    private companion object {
        /** 칩이 두 줄로 감겨도 어색하지 않은 둥글기 */
        const val CHIP_RADIUS_DP = 20
    }
}

/**
 * 지킴률 링. 파랑 호 = 지킴, 빨강 호 = 놓침, 판정이 없으면 회색 빈 링.
 * 12시 방향에서 시계 방향으로 지킴부터 그린다. 가운데 숫자는 레이아웃의 TextView가 맡는다.
 */
class RateRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var kept = 0
    private var missed = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(RING_WIDTH_DP).toFloat()
        strokeCap = Paint.Cap.BUTT
    }
    private val oval = RectF()

    fun setCounts(kept: Int, missed: Int) {
        this.kept = kept
        this.missed = missed
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = paint.strokeWidth / 2
        oval.set(inset, inset, width - inset, height - inset)

        // 바탕 링
        paint.color = context.getColor(R.color.surface_2)
        canvas.drawArc(oval, 0f, 360f, false, paint)

        val total = kept + missed
        if (total == 0) return
        val keptSweep = 360f * kept / total
        paint.color = context.getColor(R.color.ok)
        canvas.drawArc(oval, START_ANGLE, keptSweep, false, paint)
        paint.color = context.getColor(R.color.miss)
        canvas.drawArc(oval, START_ANGLE + keptSweep, 360f - keptSweep, false, paint)
    }

    private companion object {
        const val RING_WIDTH_DP = 20
        /** 12시 방향 */
        const val START_ANGLE = -90f
    }
}
