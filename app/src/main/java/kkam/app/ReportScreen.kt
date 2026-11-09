package kkam.app

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kkam.engine.model.Event
import kkam.engine.model.LawEntry

/**
 * 오늘 주행 리포트 (기획서 UI-2, screen_report.xml). 기어 P일 때만 보인다.
 * 값은 [buildReport]에서만 온다.
 */
class ReportScreen(
    private val activity: Activity,
    /** 법규 룰 테이블. 리포트를 처음 그릴 때 읽는다. */
    private val laws: () -> List<LawEntry>,
) {
    private val root: View = activity.findViewById(R.id.reportScreen)
    private val saved: TextView = activity.findViewById(R.id.reportSaved)
    private val kept: TextView = activity.findViewById(R.id.reportKept)
    private val missed: TextView = activity.findViewById(R.id.reportMissed)
    private val items: LinearLayout = activity.findViewById(R.id.reportItems)
    private val empty: TextView = activity.findViewById(R.id.reportEmpty)

    /** 리포트 카드를 마지막으로 그린 판정 목록. 같으면 다시 그리지 않는다. */
    private var drawnFor: List<Event>? = null

    init {
        listOf(R.id.reportSavedTile, R.id.reportKeptTile, R.id.reportMissedTile).forEach {
            activity.findViewById<View>(it).background = activity.rounded(color(R.color.surface), radiusDp = 24)
        }
        activity.findViewById<View>(R.id.reportAsk).background =
            activity.rounded(color(R.color.bg), radiusDp = 999, strokeColor = color(R.color.line))
    }

    fun show(screen: Screen) {
        root.visibility = if (screen == Screen.REPORT) View.VISIBLE else View.GONE
    }

    /** 판정 목록이 바뀌었을 때만 다시 그린다. */
    fun render(state: CoachState) {
        if (drawnFor == state.todayEvents) return
        drawnFor = state.todayEvents
        draw(buildReport(state.todayEvents, laws()))
    }

    private fun draw(report: DriveReport) {
        saved.text = wonLabel(report.savedWon)
        kept.text = report.kept.toString()
        missed.text = report.missed.toString()
        empty.visibility = if (report.items.isEmpty()) View.VISIBLE else View.GONE
        items.removeAllViews()
        report.items.forEach(::addItem)
    }

    private fun addItem(item: ReportItem) {
        val card = activity.layoutInflater.inflate(R.layout.report_item, items, false)
        card.background = activity.rounded(color(R.color.surface), radiusDp = 20)
        card.clipToOutline = true
        card.findViewById<View>(R.id.itemBar).setBackgroundColor(color(if (item.kept) R.color.ok else R.color.warn))
        card.findViewById<TextView>(R.id.itemTitle).text = item.title
        card.findViewById<TextView>(R.id.itemDetail).text = item.detail
        card.findViewById<TextView>(R.id.itemArticle).text = item.article
        card.findViewById<TextView>(R.id.itemPenalty).text = item.penalty
        items.addView(card)
    }

    private fun color(id: Int) = activity.getColor(id)
}
