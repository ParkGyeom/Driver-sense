package kkam.app

import android.animation.ValueAnimator
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * 주행 화면 (기획서 UI-1, screen_drive.xml). 코치 카드, 한 줄 문구, 오늘 성적.
 * 터치할 것은 두지 않는다 (주행 중 터치 조작 없음, 기획서 5.4). 값은 모두 [CoachState]에서 온다.
 *
 * 카드 바탕은 늘 어두운 surface다. 상태 색([CardLook])은 포인트로만 쓴다:
 * 왼쪽에서 흐려지는 기운, 테두리, 방향 그림 원, 행동 알약, 켜진 깜빡이 칩.
 *
 * | 모습 | 방향 그림 | 큰 글자 | 행동 알약 | 깜빡이 칩 |
 * | --- | --- | --- | --- | --- |
 * | IDLE (대기) | 회색 원 + 경로 화살표 | "다음 회전 대기" + 안내 기다림 | 숨김 | 숨김 |
 * | WARN (켜세요) | 주황 원 + 방향 | 회전(흐림) + 거리(흰색) | 주황 "! 지금 켜세요" | 꺼짐: 테두리만 + 빈 화살표 |
 * | OK (좋아요) | 파랑 원 + 방향 | 회전(흐림) + 거리(흰색) | 파랑 "✓ 좋아요" | 켜짐: 파랑으로 채움 + 꽉 찬 화살표 |
 * | MISS (놓침) | 빨강 원 + 방향 | 회전(흐림) + 거리(흰색) | 빨강 "X 다음엔 미리 켜요" | 꺼짐: 테두리만 + 빈 화살표 |
 *
 * 모습이 바뀔 때만 색을 [FADE_MS] 동안 한 번 바꾼다. 반복 애니메이션은 없다 (레버 그림 제외: 켜는 방법 안내).
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

    private val surface = color(R.color.surface)
    private val ink = color(R.color.on_accent)
    private val text = color(R.color.text)
    private val muted = color(R.color.muted)
    private val clear = color(android.R.color.transparent)

    // 배경은 한 번만 만들고 색만 바꾼다 (애니메이션 중 매 프레임 새로 만들지 않게).
    // 카드: 왼쪽(방향 그림 쪽) 상태 색 기운 → 오른쪽 surface 그라데이션 + 상태 색 테두리.
    private val cardFill = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(surface, surface)).apply {
        cornerRadius = activity.dp(CARD_RADIUS_DP).toFloat()
    }
    private val iconCircle = activity.pillBackground(surface)
    private val badgeFill = activity.pillBackground(surface)
    private val signalFill = activity.pillBackground(clear)

    /** 지금 칠해진 색. 모습이 바뀌면 여기서 새 색까지 페이드한다. */
    private var painted: CardPalette? = null
    private var paintedLook: CardLook? = null
    private var fade: ValueAnimator? = null

    init {
        card.background = cardFill
        turnIcon.background = iconCircle
        badgeBox.background = badgeFill
        signalBox.background = signalFill
        activity.findViewById<View>(R.id.scoreCard).background = activity.cardBackground()
        // 고정 색: 거리는 흰색, 알약 글자·그림은 상태 색 위 대비색.
        distance.setTextColor(text)
        badge.setTextColor(ink)
        badgeIcon.imageTintList = ColorStateList.valueOf(ink)
    }

    fun show(screen: Screen) {
        root.visibility = if (screen == Screen.DRIVE) View.VISIBLE else View.GONE
    }

    fun render(state: CoachState) {
        val look = state.look()
        renderCard(state, look)
        message.text = state.message
        renderScore(state)
        paint(state, look)
    }

    /** 카드 안 글자와 그림. 색은 [paint]가 칠한다. */
    private fun renderCard(state: CoachState, look: CardLook) {
        val active = look != CardLook.IDLE
        // 그림: 안내 중이면 안내 종류에 맞는 그림(회전이면 방향 화살표), 대기 중이면 경로 화살표.
        turnIcon.setImageResource(if (active) guideIconRes(state.guideKind, state.right) else R.drawable.ic_route_idle)
        // 대기 중에는 그림을 작게 해서 차분하게, 안내 중에는 원 안을 크게 채운다.
        val iconPadding = activity.dp(if (active) ACTIVE_ICON_PADDING_DP else IDLE_ICON_PADDING_DP)
        turnIcon.setPadding(iconPadding, iconPadding, iconPadding, iconPadding)

        turn.text = state.turnLabel
        distance.text = distanceText(state.distanceLabel, muted)
        // 거리가 없는 안내(예: 와이퍼)는 거리 줄을 접어서 제목이 그림 옆 가운데에 오게 한다.
        distance.visibility = if (active && state.distanceLabel.isNotEmpty()) View.VISIBLE else View.GONE
        idleHint.visibility = if (active) View.GONE else View.VISIBLE
        // 대기 중에는 제목이 주인공이라 흰색, 안내 중에는 거리가 주인공이라 제목을 흐리게.
        turn.setTextColor(if (active) muted else text)

        actionRow.visibility = if (active) View.VISIBLE else View.GONE
        badge.text = state.badge
        badgeIcon.setImageResource(look.badgeIconRes())
        signal.text = state.signalLabel
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

    /** 모습에 맞는 색을 칠한다. 모습이 바뀐 때만 [FADE_MS] 동안 페이드, 그 밖에는 바로 칠한다. */
    private fun paint(state: CoachState, look: CardLook) {
        val target = activity.palette(look)
        val from = painted
        val lookChanged = paintedLook != null && paintedLook != look
        paintedLook = look
        // 깜빡이 칩: 켜짐(파랑)은 상태 색으로 채우고, 꺼짐은 테두리만. 화살표 모양(꽉 참/빔)도 같이 바뀐다.
        val signalOn = look == CardLook.OK
        signalIcon.setImageResource(if (signalOn) R.drawable.ic_blinker_on else R.drawable.ic_blinker_off)
        // 레버 그림은 프레임마다 안내 종류가 바뀔 수 있어서 늘 다시 넘긴다.
        val leverMotion = leverFor(state.guideKind, state.right, look == CardLook.WARN)

        fade?.cancel()
        if (from == null || !lookChanged || root.visibility != View.VISIBLE) {
            apply(target, signalOn, leverMotion)
            return
        }
        fade = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FADE_MS
            addUpdateListener { apply(from.mix(target, it.animatedFraction), signalOn, leverMotion) }
            start()
        }
    }

    private fun apply(palette: CardPalette, signalOn: Boolean, leverMotion: LeverMotion?) {
        painted = palette
        cardFill.colors = intArrayOf(palette.tint, surface)
        cardFill.setStroke(activity.dp(CARD_EDGE_DP), palette.edge)
        iconCircle.setColor(palette.iconBg)
        turnIcon.imageTintList = ColorStateList.valueOf(palette.iconInk)
        badgeFill.setColor(palette.accent)
        signalFill.setColor(if (signalOn) palette.accent else clear)
        signalFill.setStroke(activity.dp(SIGNAL_EDGE_DP), palette.accent)
        signal.setTextColor(if (signalOn) ink else text)
        signalIcon.imageTintList = ColorStateList.valueOf(if (signalOn) ink else palette.accent)
        lever.show(leverMotion, palette.accent)
    }

    private fun color(id: Int) = activity.getColor(id)

    private companion object {
        const val FADE_MS = 200L
        const val CARD_RADIUS_DP = 32
        const val CARD_EDGE_DP = 3
        const val SIGNAL_EDGE_DP = 3
        const val ACTIVE_ICON_PADDING_DP = 32
        const val IDLE_ICON_PADDING_DP = 52
    }
}
