package kkam.app

import android.app.Activity
import android.content.res.ColorStateList
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 진단 리포트(screen_diagnose)와 습관 선택(screen_habit) 화면을 그린다 (기획서 UI-3).
 * 값은 [Diagnosis]와 [HabitSelection]에서만 온다. 어느 화면을 보일지는 MainActivity가 [Screen]으로 정한다.
 *
 * 색 = 의미: 주황 = 전국 평균보다 낮음(고칠 일), 파랑 = 평균 이상·선택됨.
 * 색만으로 구분하지 않게 ! 아이콘, "평균보다 낮음" 칩, ✓ 체크와 "선택됨" 글자를 같이 쓴다.
 */
class HabitScreens(
    private val activity: Activity,
    private val diagnosis: Diagnosis,
    /** "고칠 습관 고르기" */
    private val onOpenHabit: () -> Unit,
    /** "진단 다시 보기" */
    private val onBackToDiagnose: () -> Unit,
    /** "선택 완료" */
    private val onFinish: (HabitSelection) -> Unit,
) {
    private var selection = HabitSelection()

    /** 마지막으로 누른 카드가 개수 제한에 걸렸으면 true. 안내 문구를 바꾼다. */
    private var blocked = false

    private val diagnoseScreen: View = activity.findViewById(R.id.diagnoseScreen)
    private val habitScreen: View = activity.findViewById(R.id.habitScreen)
    private val habitCards: LinearLayout = activity.findViewById(R.id.habitCards)
    private val habitCount: TextView = activity.findViewById(R.id.habitCount)
    private val habitNotice: TextView = activity.findViewById(R.id.habitNotice)
    private val habitDone: TextView = activity.findViewById(R.id.habitDone)
    private val cards = mutableMapOf<Habit, View>()

    private val averageLabel = formatPercent(diagnosis.nationalAveragePercent)

    init {
        drawDiagnosis()
        Habit.entries.forEach(::addCard)
        activity.findViewById<TextView>(R.id.diagnoseNext).apply {
            background = activity.pillBackground(color(R.color.ok))
            setOnClickListener { onOpenHabit() }
        }
        activity.findViewById<TextView>(R.id.habitBack).apply {
            background = activity.pillBackground(color(R.color.bg), strokeColor = color(R.color.line))
            setOnClickListener { onBackToDiagnose() }
        }
        activity.findViewById<View>(R.id.habitProgress).background = activity.pillBackground(color(R.color.surface))
        habitDone.setOnClickListener { if (selection.canFinish) onFinish(selection) }
        drawSelection()
    }

    /** [screen]에 맞춰 두 화면의 보이기를 바꾼다. */
    fun show(screen: Screen) {
        diagnoseScreen.visibility = if (screen == Screen.DIAGNOSE) View.VISIBLE else View.GONE
        habitScreen.visibility = if (screen == Screen.HABIT) View.VISIBLE else View.GONE
    }

    // ===== 진단 리포트 =====

    private fun drawDiagnosis() {
        activity.findViewById<TextView>(R.id.diagnosePeriod).text = diagnosis.periodLabel
        activity.findViewById<TextView>(R.id.diagnoseAverage).text =
            activity.getString(R.string.parked_diagnose_average_legend, averageLabel)

        // 히어로: 가장 낮은 상황이 평균보다 낮으면 주황 !, 아니면 파랑 ✓
        val lowestBelow = diagnosis.belowAverage.isNotEmpty()
        val tone = color(if (lowestBelow) R.color.warn else R.color.ok)
        activity.findViewById<View>(R.id.diagnoseHero).background = activity.cardBackground()
        activity.findViewById<ImageView>(R.id.diagnoseHeroIcon).apply {
            setImageResource(if (lowestBelow) R.drawable.ic_kk_alert else R.drawable.ic_kk_check)
            imageTintList = ColorStateList.valueOf(tone)
            background = activity.pillBackground(color(if (lowestBelow) R.color.warn_soft else R.color.ok_soft))
        }
        activity.findViewById<TextView>(R.id.diagnoseHeadline).text = headlineText(diagnosis.headline, tone)

        activity.findViewById<View>(R.id.diagnoseRatesCard).background = activity.cardBackground()
        val rows: LinearLayout = activity.findViewById(R.id.diagnoseRows)
        diagnosis.rates.forEach { rows.addView(rateRow(rows, it)) }
    }

    /** "좌회전 깜빡이 62%, ..."에서 퍼센트 숫자만 크게, [tone] 색으로 칠한다. */
    private fun headlineText(headline: String, tone: Int): CharSequence {
        val match = Regex("""\d+%""").find(headline) ?: return headline
        val start = match.range.first
        val end = match.range.last + 1
        return SpannableString(headline).apply {
            setSpan(ForegroundColorSpan(tone), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(HEADLINE_NUMBER_SCALE), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** 준수율 막대 한 줄. 평균보다 낮으면 주황 막대·숫자 + "! 평균보다 낮음" 칩. */
    private fun rateRow(parent: LinearLayout, rate: SituationRate): View {
        val row = activity.layoutInflater.inflate(R.layout.diagnose_row, parent, false)
        val below = rate.percent < diagnosis.nationalAveragePercent
        val tone = color(if (below) R.color.warn else R.color.ok)

        row.findViewById<TextView>(R.id.rowLabel).text = rate.habit.label
        row.findViewById<TextView>(R.id.rowCount).text = activity.getString(R.string.diagnose_count, rate.kept, rate.total)
        row.findViewById<TextView>(R.id.rowChip).apply {
            text = if (below) "! " + activity.getString(R.string.parked_diagnose_below) else activity.getString(R.string.parked_diagnose_above)
            setTextColor(if (below) tone else color(R.color.muted))
            background = activity.pillBackground(color(if (below) R.color.warn_soft else R.color.surface_2))
        }
        row.findViewById<TextView>(R.id.rowPercent).apply {
            text = numberEmphasis("${rate.percent} %")
            setTextColor(tone)
        }
        row.findViewById<View>(R.id.rowTrack).apply {
            background = activity.rounded(color(R.color.surface_2), radiusDp = BAR_RADIUS_DP)
            clipToOutline = true
        }
        row.findViewById<View>(R.id.rowFill).apply {
            setBackgroundColor(tone)
            setWeight(rate.percent.toFloat())
        }
        row.findViewById<View>(R.id.rowRest).setWeight((100 - rate.percent).toFloat())
        row.findViewById<View>(R.id.rowAverageBefore).setWeight(diagnosis.nationalAveragePercent.toFloat())
        row.findViewById<View>(R.id.rowAverageAfter).setWeight((100 - diagnosis.nationalAveragePercent).toFloat())
        return row
    }

    // ===== 습관 선택 =====

    private fun addCard(habit: Habit) {
        val card = activity.layoutInflater.inflate(R.layout.habit_card, habitCards, false)
        if (habitCards.childCount > 0) (card.layoutParams as LinearLayout.LayoutParams).marginStart = activity.dp(20)
        card.findViewById<TextView>(R.id.habitName).text = habit.label

        val rate = diagnosis.rateOf(habit)
        val below = rate != null && rate.percent < diagnosis.nationalAveragePercent
        card.findViewById<TextView>(R.id.habitRate).text =
            rate?.let { rateText(it.percent, color(if (below) R.color.warn else R.color.text)) } ?: ""
        card.findViewById<TextView>(R.id.habitAverage).text =
            activity.getString(R.string.parked_habit_average, averageLabel)

        card.findViewById<ImageView>(R.id.habitIcon).apply {
            setImageResource(habit.iconRes())
            imageTintList = ColorStateList.valueOf(color(R.color.text))
            background = activity.pillBackground(color(R.color.surface_2))
        }
        card.findViewById<View>(R.id.habitBadge).apply {
            background = activity.pillBackground(color(R.color.warn))
            visibility = if (diagnosis.belowAverage.any { it.habit == habit }) View.VISIBLE else View.INVISIBLE
        }
        card.setOnClickListener { toggle(habit) }
        cards[habit] = card
        habitCards.addView(card)
    }

    /** "내 준수율 62%"에서 "62%"만 크게, [tone] 색으로. */
    private fun rateText(percent: Int, tone: Int): CharSequence {
        val label = activity.getString(R.string.habit_rate, percent)
        val start = label.indexOf(percent.toString())
        if (start < 0) return label
        return SpannableString(label).apply {
            setSpan(ForegroundColorSpan(tone), start, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(HABIT_RATE_SCALE), start, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun toggle(habit: Habit) {
        blocked = !selection.canToggle(habit)
        selection = selection.toggle(habit)
        drawSelection()
    }

    /** 카드 테두리·배경·체크·흐림, 진행 표시, 안내 문구, 완료 버튼을 지금 선택 상태로 다시 그린다. */
    private fun drawSelection() {
        cards.forEach { (habit, card) ->
            val selected = habit in selection.selected
            // 고름: 옅은 파랑 배경 + 굵은 파랑 테두리. 안 고름: 카드 배경 + 얇은 회색 테두리.
            card.background = activity.rounded(
                color(if (selected) R.color.ok_soft else R.color.surface),
                radiusDp = CARD_RADIUS_DP,
                strokeColor = color(if (selected) R.color.ok else R.color.line),
                strokeDp = if (selected) SELECTED_STROKE_DP else 2,
            )
            card.alpha = if (selection.canToggle(habit)) 1f else DISABLED_ALPHA
            card.findViewById<ImageView>(R.id.habitCheck).apply {
                // 고름: 파란 동그라미 안 ✓. 안 고름: 빈 동그라미.
                imageAlpha = if (selected) 255 else 0
                imageTintList = ColorStateList.valueOf(color(R.color.on_accent))
                background = if (selected) {
                    activity.pillBackground(color(R.color.ok))
                } else {
                    activity.rounded(color(R.color.bg), radiusDp = 999, strokeColor = color(R.color.line), strokeDp = 3)
                }
            }
            card.findViewById<View>(R.id.habitSelectedLabel).visibility = if (selected) View.VISIBLE else View.INVISIBLE
        }

        habitCount.text = numberEmphasis(activity.getString(R.string.parked_habit_progress, selection.selected.size, MAX_HABITS))
        habitCount.setTextColor(color(if (selection.selected.isEmpty()) R.color.muted else R.color.ok))

        habitNotice.text = if (blocked) {
            "! " + activity.getString(R.string.habit_full, MAX_HABITS)
        } else {
            activity.getString(R.string.parked_habit_tap)
        }
        habitNotice.setTextColor(color(if (blocked) R.color.warn else R.color.muted))
        habitDone.background = activity.pillBackground(color(if (selection.canFinish) R.color.ok else R.color.line))
        habitDone.alpha = if (selection.canFinish) 1f else DISABLED_ALPHA
    }

    /** 습관 카드 가운데 아이콘. 우회전은 기존 우회전 화살표를 쓴다. */
    private fun Habit.iconRes(): Int = when (this) {
        Habit.LEFT_SIGNAL -> R.drawable.ic_turn_left
        Habit.RIGHT_SIGNAL -> R.drawable.ic_turn_right
        Habit.RED_RIGHT_STOP -> R.drawable.ic_red_light
    }

    private fun View.setWeight(weight: Float) {
        layoutParams = (layoutParams as LinearLayout.LayoutParams).apply { this.weight = weight }
    }

    private fun color(id: Int) = activity.getColor(id)

    /** 75.7 → "75.7", 76.0 → "76" */
    private fun formatPercent(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    private companion object {
        const val DISABLED_ALPHA = 0.4f
        const val CARD_RADIUS_DP = 28
        const val SELECTED_STROKE_DP = 6
        const val BAR_RADIUS_DP = 16
        /** 히어로 문구에서 퍼센트 숫자를 몇 배로 키울지 */
        const val HEADLINE_NUMBER_SCALE = 1.6f
        /** 카드의 "62%"를 몇 배로 키울지 */
        const val HABIT_RATE_SCALE = 1.5f
    }
}
