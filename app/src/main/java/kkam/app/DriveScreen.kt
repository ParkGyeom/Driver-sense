package kkam.app

import android.app.Activity
import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * 주행 화면 (기획서 UI-1, screen_drive.xml). 코치 카드, 한 줄 문구, 오늘 지킨 횟수.
 * 터치할 것은 두지 않는다 (주행 중 터치 조작 없음, 기획서 5.4). 값은 모두 [CoachState]에서 온다.
 */
class DriveScreen(private val activity: Activity) {
    private val root: View = activity.findViewById(R.id.driveScreen)
    private val card: LinearLayout = activity.findViewById(R.id.card)
    private val turnIcon: ImageView = activity.findViewById(R.id.turnIcon)
    private val lever: LeverGuideView = activity.findViewById(R.id.lever)
    private val turn: TextView = activity.findViewById(R.id.turn)
    private val distance: TextView = activity.findViewById(R.id.distance)
    private val signal: TextView = activity.findViewById(R.id.signal)
    private val badge: TextView = activity.findViewById(R.id.badge)
    private val message: TextView = activity.findViewById(R.id.message)
    private val today: TextView = activity.findViewById(R.id.today)
    private val progress: ProgressBar = activity.findViewById(R.id.progress)

    fun show(screen: Screen) {
        root.visibility = if (screen == Screen.DRIVE) View.VISIBLE else View.GONE
    }

    fun render(state: CoachState) {
        // 회전 안내 중이면 카드 전체를 상태 색으로 칠하고 글자는 어둡게, 대기 중이면 어두운 카드에 흐린 글자.
        val active = state.tone != Tone.IDLE
        val fill = color(
            when (state.tone) {
                Tone.IDLE -> R.color.surface
                Tone.WARN -> R.color.warn
                Tone.OK -> R.color.ok
            },
        )
        val ink = color(if (active) R.color.on_accent else R.color.muted)
        card.background = activity.rounded(fill, radiusDp = 32, strokeColor = if (active) null else color(R.color.line))

        turnIcon.visibility = if (active) View.VISIBLE else View.GONE
        // 안내 종류에 맞는 그림. 회전 깜빡이면 방향(turnLabel)에 맞는 화살표.
        turnIcon.setImageResource(guideIconRes(state.guideKind, state.turnLabel))
        turnIcon.imageTintList = ColorStateList.valueOf(ink)

        turn.text = state.turnLabel
        distance.text = numberEmphasis(state.distanceLabel)
        badge.text = state.badge
        signal.text = state.signalLabel
        listOf(turn, distance, badge, signal).forEach { it.setTextColor(ink) }
        listOf(badge, signal).forEach { it.visibility = if (active) View.VISIBLE else View.GONE }
        // 거리가 없는 안내(예: 와이퍼)는 거리 줄을 비워 두지 않고 접어서 제목이 그림 옆 가운데에 오게 한다.
        distance.visibility = if (active && state.distanceLabel.isNotEmpty()) View.VISIBLE else View.GONE
        lever.show(leverFor(state.guideKind, state.turnLabel, state.tone == Tone.WARN), ink)

        message.text = state.message
        today.text = "${state.kept} / ${state.total}"
        progress.max = state.total.coerceAtLeast(1)
        progress.progress = state.kept
    }

    private fun color(id: Int) = activity.getColor(id)
}
