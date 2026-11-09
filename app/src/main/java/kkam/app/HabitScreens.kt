package kkam.app

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * 진단 리포트(screen_diagnose)와 습관 선택(screen_habit) 화면을 그린다 (기획서 UI-3).
 * 값은 [Diagnosis]와 [HabitSelection]에서만 온다. 어느 화면을 보일지는 MainActivity가 [Screen]으로 정한다.
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
    var selection = HabitSelection()
        private set

    /** 마지막으로 누른 카드가 개수 제한에 걸렸으면 true. 안내 문구를 바꾼다. */
    private var blocked = false

    private val diagnoseScreen: View = activity.findViewById(R.id.diagnoseScreen)
    private val habitScreen: View = activity.findViewById(R.id.habitScreen)
    private val habitCards: LinearLayout = activity.findViewById(R.id.habitCards)
    private val habitNotice: TextView = activity.findViewById(R.id.habitNotice)
    private val habitDone: TextView = activity.findViewById(R.id.habitDone)
    private val cards = mutableMapOf<Habit, View>()

    init {
        drawDiagnosis()
        Habit.entries.forEach(::addCard)
        activity.findViewById<TextView>(R.id.diagnoseNext).apply {
            background = rounded(color(R.color.ok), radiusDp = 999)
            setOnClickListener { onOpenHabit() }
        }
        activity.findViewById<TextView>(R.id.habitBack).apply {
            background = rounded(color(R.color.bg), radiusDp = 999, strokeColor = color(R.color.line))
            setOnClickListener { onBackToDiagnose() }
        }
        habitDone.setOnClickListener { if (selection.canFinish) onFinish(selection) }
        drawSelection()
    }

    /** [screen]에 맞춰 두 화면의 보이기를 바꾼다. */
    fun show(screen: Screen) {
        diagnoseScreen.visibility = if (screen == Screen.DIAGNOSE) View.VISIBLE else View.GONE
        habitScreen.visibility = if (screen == Screen.HABIT) View.VISIBLE else View.GONE
    }

    private fun drawDiagnosis() {
        activity.findViewById<TextView>(R.id.diagnosePeriod).text = diagnosis.periodLabel
        activity.findViewById<TextView>(R.id.diagnoseAverage).text =
            activity.getString(R.string.diagnose_average, diagnosis.nationalAveragePercent.toString())
        activity.findViewById<TextView>(R.id.diagnoseHeadline).apply {
            text = headlineText(diagnosis.headline)
            background = rounded(color(R.color.surface), radiusDp = 32)
        }
        val rows: LinearLayout = activity.findViewById(R.id.diagnoseRows)
        diagnosis.rates.forEach { rows.addView(rateRow(rows, it)) }
    }

    /** "좌회전 깜빡이 62%, ..."에서 퍼센트 숫자만 주황으로 칠한다. */
    private fun headlineText(headline: String): CharSequence {
        val match = Regex("""\d+%""").find(headline) ?: return headline
        return SpannableString(headline).apply {
            setSpan(ForegroundColorSpan(color(R.color.warn)), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun rateRow(parent: LinearLayout, rate: SituationRate): View {
        val row = activity.layoutInflater.inflate(R.layout.diagnose_row, parent, false)
        val barColor = color(if (rate.percent < diagnosis.nationalAveragePercent) R.color.warn else R.color.ok)
        row.findViewById<TextView>(R.id.rowLabel).text = rate.habit.label
        row.findViewById<TextView>(R.id.rowCount).text = activity.getString(R.string.diagnose_count, rate.kept, rate.total)
        row.findViewById<TextView>(R.id.rowPercent).apply {
            text = "${rate.percent}%"
            setTextColor(barColor)
        }
        row.findViewById<View>(R.id.rowTrack).apply {
            background = rounded(color(R.color.surface), radiusDp = 12)
            clipToOutline = true
        }
        row.findViewById<View>(R.id.rowFill).apply {
            setBackgroundColor(barColor)
            setWeight(rate.percent.toFloat())
        }
        row.findViewById<View>(R.id.rowRest).setWeight((100 - rate.percent).toFloat())
        row.findViewById<View>(R.id.rowAverageBefore).setWeight(diagnosis.nationalAveragePercent.toFloat())
        row.findViewById<View>(R.id.rowAverageAfter).setWeight((100 - diagnosis.nationalAveragePercent).toFloat())
        return row
    }

    private fun addCard(habit: Habit) {
        val card = activity.layoutInflater.inflate(R.layout.habit_card, habitCards, false)
        if (habitCards.childCount > 0) (card.layoutParams as LinearLayout.LayoutParams).marginStart = dp(20)
        card.findViewById<TextView>(R.id.habitName).text = habit.label
        val rate = diagnosis.rateOf(habit)
        card.findViewById<TextView>(R.id.habitRate).text = rate?.let { activity.getString(R.string.habit_rate, it.percent) }.orEmpty()
        card.findViewById<View>(R.id.habitBadge).apply {
            background = rounded(color(R.color.warn), radiusDp = 999)
            visibility = if (diagnosis.belowAverage.any { it.habit == habit }) View.VISIBLE else View.INVISIBLE
        }
        card.setOnClickListener { toggle(habit) }
        cards[habit] = card
        habitCards.addView(card)
    }

    private fun toggle(habit: Habit) {
        blocked = !selection.canToggle(habit)
        selection = selection.toggle(habit)
        drawSelection()
    }

    /** 카드 테두리·체크·흐림, 안내 문구, 완료 버튼을 지금 선택 상태로 다시 그린다. */
    private fun drawSelection() {
        cards.forEach { (habit, card) ->
            val selected = habit in selection.selected
            card.background = rounded(
                color(R.color.surface),
                radiusDp = 28,
                strokeColor = color(if (selected) R.color.ok else R.color.line),
                strokeDp = if (selected) 6 else 2,
            )
            card.alpha = if (selection.canToggle(habit)) 1f else DISABLED_ALPHA
            card.findViewById<TextView>(R.id.habitCheck).apply {
                text = if (selected) "✓" else ""
                background = if (selected) {
                    rounded(color(R.color.ok), radiusDp = 999)
                } else {
                    rounded(color(R.color.bg), radiusDp = 999, strokeColor = color(R.color.line), strokeDp = 3)
                }
            }
        }
        habitNotice.text = if (blocked) {
            activity.getString(R.string.habit_full, MAX_HABITS)
        } else {
            activity.getString(R.string.habit_count, selection.selected.size, MAX_HABITS)
        }
        habitNotice.setTextColor(color(if (blocked) R.color.warn else R.color.muted))
        habitDone.background = rounded(color(if (selection.canFinish) R.color.ok else R.color.line), radiusDp = 999)
        habitDone.alpha = if (selection.canFinish) 1f else DISABLED_ALPHA
    }

    private fun View.setWeight(weight: Float) {
        layoutParams = (layoutParams as LinearLayout.LayoutParams).apply { this.weight = weight }
    }

    private fun color(id: Int) = activity.getColor(id)

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null, strokeDp: Int = 2) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        strokeColor?.let { setStroke(dp(strokeDp), it) }
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val DISABLED_ALPHA = 0.4f
    }
}
