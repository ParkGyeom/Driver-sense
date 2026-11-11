package kkam.app

import android.app.Activity
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 모드 설정 화면 (기획서 F3, 4.3 모드). 기어 P일 때만 보인다.
 * 어느 화면을 보일지는 MainActivity가 [Screen]으로 정하고, 이 클래스는 자기 화면만 그린다.
 *
 * 고른 모드는 [ModeStore]에 바로 저장한다. 주행 세션이 시작할 때 거기서 읽는다.
 * 오늘 기록 수는 화면을 열 때마다 [RecordStore]에서 읽는다 (세션과 같은 저장소라 따로 넘겨받지 않는다).
 */
class ModeScreen(
    private val activity: Activity,
    private val modes: ModeStore = ModeStore(activity),
    private val records: RecordStore = RecordStore(activity),
) {
    private val root: View = activity.findViewById(R.id.modeScreen)
    private val recordsLine: TextView = activity.findViewById(R.id.modeRecords)
    private val cards: Map<DriveMode, View> = buildCards()
    private var selected: DriveMode = modes.load()

    init {
        drawSelection()
    }

    fun show(screen: Screen) {
        val visible = screen == Screen.MODE
        root.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) drawRecords(records.load().size)
    }

    /** 4장을 DriveMode 순서대로 위 줄 2장, 아래 줄 2장에 넣는다. */
    private fun buildCards(): Map<DriveMode, View> {
        val rows = listOf<LinearLayout>(activity.findViewById(R.id.modeRowTop), activity.findViewById(R.id.modeRowBottom))
        val inflater = LayoutInflater.from(activity)
        return DriveMode.entries.withIndex().associate { (index, mode) ->
            val row = rows[index / CARDS_PER_ROW]
            val card = inflater.inflate(R.layout.mode_card, row, false)
            // 같은 줄 두 카드 사이 간격
            if (index % CARDS_PER_ROW > 0) {
                (card.layoutParams as LinearLayout.LayoutParams).marginStart = activity.resources.getDimensionPixelSize(R.dimen.kk_gap)
            }
            card.findViewById<ImageView>(R.id.modeIcon).setImageResource(mode.iconRes())
            card.findViewById<TextView>(R.id.modeName).text = mode.label
            card.findViewById<TextView>(R.id.modeGuide).text = activity.getString(R.string.mode_guide, mode.guideLabel)
            card.findViewById<TextView>(R.id.modeTarget).text = activity.getString(R.string.mode_target, mode.target)
            card.setOnClickListener { select(mode) }
            row.addView(card)
            mode to card
        }
    }

    private fun select(mode: DriveMode) {
        if (mode == selected) return
        selected = mode
        modes.save(mode)
        drawSelection()
    }

    /** 고른 카드: 파란 테두리(굵게) + 옅은 파란 배경 + 파란 체크. 나머지: 어두운 카드 + 빈 동그라미. */
    private fun drawSelection() {
        val ok = activity.getColor(R.color.ok)
        cards.forEach { (mode, card) ->
            val on = mode == selected
            card.isSelected = on
            card.background = if (on) {
                activity.rounded(activity.getColor(R.color.ok_soft), CARD_RADIUS_DP, strokeColor = ok, strokeDp = SELECTED_STROKE_DP)
            } else {
                activity.rounded(activity.getColor(R.color.surface), CARD_RADIUS_DP, strokeColor = activity.getColor(R.color.line))
            }
            card.findViewById<ImageView>(R.id.modeIcon).apply {
                background = activity.rounded(activity.getColor(if (on) R.color.ok else R.color.surface_2), CARD_RADIUS_DP / 2)
                imageTintList = ColorStateList.valueOf(activity.getColor(if (on) R.color.on_accent else R.color.text))
            }
            card.findViewById<TextView>(R.id.modeCheck).apply {
                text = if (on) activity.getString(R.string.mode_check) else ""
                background = if (on) {
                    activity.pillBackground(ok)
                } else {
                    activity.pillBackground(activity.getColor(R.color.surface), strokeColor = activity.getColor(R.color.line))
                }
            }
            card.findViewById<TextView>(R.id.modeGuide).setTextColor(activity.getColor(if (on) R.color.ok else R.color.text))
        }
    }

    private fun drawRecords(count: Int) {
        recordsLine.text = if (count > 0) {
            activity.getString(R.string.mode_records_saved, count)
        } else {
            activity.getString(R.string.mode_records_empty)
        }
        recordsLine.setTextColor(activity.getColor(if (count > 0) R.color.text else R.color.muted))
    }

    private fun DriveMode.iconRes(): Int = when (this) {
        DriveMode.DIAGNOSE -> R.drawable.ic_mode_diagnose
        DriveMode.CORRECT -> R.drawable.ic_mode_correct
        DriveMode.TRAINING -> R.drawable.ic_mode_training
        DriveMode.QUIET -> R.drawable.ic_mode_quiet
    }

    private companion object {
        const val CARDS_PER_ROW = 2
        const val CARD_RADIUS_DP = 28
        const val SELECTED_STROKE_DP = 6
    }
}
