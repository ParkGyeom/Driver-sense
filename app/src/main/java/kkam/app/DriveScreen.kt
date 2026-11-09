package kkam.app

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * 주행 화면 (기획서 UI-1, screen_drive.xml). 코치 카드, 한 줄 문구, 오늘 성적.
 * 터치할 것은 두지 않는다 (주행 중 터치 조작 없음, 기획서 5.4). 값은 모두 [CoachState]에서 온다.
 *
 * | 상태 | 카드 색 | 그림 | 큰 글자 | 배지 | 깜빡이 칩 |
 * | --- | --- | --- | --- | --- | --- |
 * | IDLE (대기) | surface (회색) | 경로 화살표 | "다음 회전 대기" + "다음 안내를 기다리는 중이에요" | 숨김 | 숨김 |
 * | WARN (켜세요) | warn (주황) | 회전 방향 | 회전 + 남은 거리 | "! 지금 켜세요" | 빈 화살표 + "깜빡이 꺼짐" |
 * | OK (좋아요) | ok (파랑) | 회전 방향 | 회전 + 남은 거리 | "✓ 좋아요" | 꽉 찬 화살표 + "깜빡이 켜짐" |
 *
 * 상태가 바뀔 때만 색을 [FADE_MS] 동안 한 번 바꾼다. 반복 애니메이션은 없다 (레버 그림 제외: 켜는 방법 안내).
 */
class DriveScreen(private val activity: Activity) {
    private val root: View = activity.findViewById(R.id.driveScreen)
    private val card: LinearLayout = activity.findViewById(R.id.card)
    private val turnIcon: ImageView = activity.findViewById(R.id.turnIcon)
    private val lever: LeverGuideView = activity.findViewById(R.id.lever)
    private val turn: TextView = activity.findViewById(R.id.turn)
    private val distance: TextView = activity.findViewById(R.id.distance)
    private val idleHint: TextView = activity.findViewById(R.id.idleHint)
    private val actionRow: View = activity.findViewById(R.id.actionRow)
    private val badgeBox: View = activity.findViewById(R.id.badgeBox)
    private val badgeIcon: ImageView = activity.findViewById(R.id.badgeIcon)
    private val badge: TextView = activity.findViewById(R.id.badge)
    private val signalBox: View = activity.findViewById(R.id.signalBox)
    private val signalIcon: ImageView = activity.findViewById(R.id.signalIcon)
    private val signal: TextView = activity.findViewById(R.id.signal)
    private val message: TextView = activity.findViewById(R.id.message)
    private val scoreRing: ProgressBar = activity.findViewById(R.id.scoreRing)
    private val scoreRate: TextView = activity.findViewById(R.id.scoreRate)
    private val todayKept: TextView = activity.findViewById(R.id.todayKept)
    private val todayTotal: TextView = activity.findViewById(R.id.todayTotal)

    // 배경은 한 번만 만들고 색만 바꾼다 (애니메이션 중 매 프레임 새로 만들지 않게).
    private val cardFill = activity.rounded(color(R.color.surface), radiusDp = CARD_RADIUS_DP)
    private val badgeFill = activity.pillBackground(color(R.color.on_accent))
    private val signalStroke = activity.pillBackground(Color.TRANSPARENT, strokeColor = color(R.color.on_accent))
    private val idleIconCircle = activity.pillBackground(color(R.color.surface_2))

    /** 지금 칠해진 색. 상태가 바뀌면 여기서 새 색까지 페이드한다. */
    private var painted: Palette? = null
    private var paintedTone: Tone? = null
    private var fade: ValueAnimator? = null

    init {
        card.background = cardFill
        badgeBox.background = badgeFill
        signalBox.background = signalStroke
        activity.findViewById<View>(R.id.scoreCard).background = activity.cardBackground()
    }

    fun show(screen: Screen) {
        root.visibility = if (screen == Screen.DRIVE) View.VISIBLE else View.GONE
    }

    fun render(state: CoachState) {
        val active = state.tone != Tone.IDLE
        renderCard(state, active)
        message.text = state.message
        renderScore(state)
        paint(state)
    }

    /** 카드 안 글자와 그림. 색은 [paint]가 칠한다. */
    private fun renderCard(state: CoachState, active: Boolean) {
        // 그림: 회전 안내 중이면 안내 종류에 맞는 그림(회전이면 방향 화살표), 대기 중이면 경로 화살표.
        turnIcon.setImageResource(if (active) guideIconRes(state.guideKind, state.turnLabel) else R.drawable.ic_route_idle)
        // 대기 중에는 동그라미 안에 작게, 안내 중에는 동그라미 없이 칸을 꽉 채워 크게.
        turnIcon.background = if (active) null else idleIconCircle
        val iconPadding = activity.dp(if (active) 0 else IDLE_ICON_PADDING_DP)
        turnIcon.setPadding(iconPadding, iconPadding, iconPadding, iconPadding)

        turn.text = state.turnLabel
        distance.text = numberEmphasis(state.distanceLabel)
        // 거리가 없는 안내(예: 와이퍼)는 거리 줄을 접어서 제목이 그림 옆 가운데에 오게 한다.
        distance.visibility = if (active && state.distanceLabel.isNotEmpty()) View.VISIBLE else View.GONE
        idleHint.visibility = if (active) View.GONE else View.VISIBLE

        actionRow.visibility = if (active) View.VISIBLE else View.GONE
        badge.text = state.badge
        badgeIcon.setImageResource(if (state.tone == Tone.OK) R.drawable.ic_check else R.drawable.ic_alert)
        signal.text = state.signalLabel
        // 깜빡이 칩은 색이 아니라 모양으로도 구분한다: 켜짐 = 꽉 찬 화살표, 꺼짐 = 빈 화살표.
        signalIcon.setImageResource(if (state.tone == Tone.OK) R.drawable.ic_blinker_on else R.drawable.ic_blinker_off)
        signalBox.visibility = if (state.signalLabel.isEmpty()) View.GONE else View.VISIBLE
    }

    /** 오늘 성적: 링(지킨 비율) + "지킴 n / 전체 m번". 판정이 없으면 퍼센트 대신 "–". */
    private fun renderScore(state: CoachState) {
        val rate = if (state.total == 0) null else state.kept * 100 / state.total
        scoreRing.progress = rate ?: 0
        scoreRate.text = rate?.let { activity.getString(R.string.drive_today_rate, it) }
            ?: activity.getString(R.string.drive_today_none)
        todayKept.text = state.kept.toString()
        todayTotal.text = activity.getString(R.string.drive_today_total, state.total)
    }

    /** 상태에 맞는 색을 칠한다. 상태(tone)가 바뀐 때만 [FADE_MS] 동안 페이드, 그 밖에는 바로 칠한다. */
    private fun paint(state: CoachState) {
        val target = paletteFor(state.tone)
        val from = painted
        val toneChanged = paintedTone != null && paintedTone != state.tone
        paintedTone = state.tone
        // 레버 그림은 프레임마다 안내 종류가 바뀔 수 있어서 늘 다시 넘긴다.
        val leverMotion = leverFor(state.guideKind, state.turnLabel, state.tone == Tone.WARN)
        cardFill.setStroke(activity.dp(2), if (state.tone == Tone.IDLE) color(R.color.line) else Color.TRANSPARENT)

        if (from == null || !toneChanged || root.visibility != View.VISIBLE) {
            fade?.cancel()
            apply(target, leverMotion)
            return
        }
        fade?.cancel()
        fade = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FADE_MS
            addUpdateListener { apply(from.mix(target, it.animatedFraction), leverMotion) }
            start()
        }
    }

    private fun apply(palette: Palette, leverMotion: LeverMotion?) {
        painted = palette
        cardFill.setColor(palette.fill)
        turn.setTextColor(palette.ink)
        distance.setTextColor(palette.ink)
        turnIcon.imageTintList = ColorStateList.valueOf(palette.sub)
        // 배지는 카드 색을 뒤집는다: 어두운 알약 위에 카드 색 글자.
        badgeFill.setColor(palette.ink)
        badge.setTextColor(palette.fill)
        badgeIcon.imageTintList = ColorStateList.valueOf(palette.fill)
        signal.setTextColor(palette.ink)
        signalIcon.imageTintList = ColorStateList.valueOf(palette.ink)
        signalStroke.setStroke(activity.dp(3), palette.ink)
        lever.show(leverMotion, palette.ink)
    }

    /** 대기 = 어두운 카드에 밝은 글자, 안내 중 = 상태 색 카드에 어두운 글자. */
    private fun paletteFor(tone: Tone): Palette = when (tone) {
        Tone.IDLE -> Palette(fill = color(R.color.surface), ink = color(R.color.text), sub = color(R.color.muted))
        Tone.WARN -> Palette(fill = color(R.color.warn), ink = color(R.color.on_accent), sub = color(R.color.on_accent))
        Tone.OK -> Palette(fill = color(R.color.ok), ink = color(R.color.on_accent), sub = color(R.color.on_accent))
    }

    private fun color(id: Int) = activity.getColor(id)

    /** 카드 한 장의 색 세 가지. fill = 카드 바탕, ink = 큰 글자, sub = 그림·보조 글자. */
    private data class Palette(val fill: Int, val ink: Int, val sub: Int) {
        /** 두 색 사이 중간값. fraction 0이면 this, 1이면 to. */
        fun mix(to: Palette, fraction: Float) = Palette(
            fill = blend(fill, to.fill, fraction),
            ink = blend(ink, to.ink, fraction),
            sub = blend(sub, to.sub, fraction),
        )

        private fun blend(a: Int, b: Int, fraction: Float) = ARGB.evaluate(fraction, a, b) as Int
    }

    private companion object {
        const val FADE_MS = 200L
        const val CARD_RADIUS_DP = 32
        const val IDLE_ICON_PADDING_DP = 48
        val ARGB = ArgbEvaluator()
    }
}
