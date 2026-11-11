package kkam.app

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/**
 * 경로 데모 지도 위쪽 가운데에 뜨는 안내 카드. 내비 위 팝업([CoachPopup])과 같은 popup_card.xml을 쓰고 같은 [Popup] 값을 그린다.
 * 다른 점: 겹침 창이 아니라 지도 칸(routeMapPanel) 안의 보통 뷰라서 권한이 필요 없고 우리 앱 화면에서만 보인다.
 * 주황 "지금 켜세요" → 파랑 "좋아요", 판정이 나오거나 회전을 지나면 숨는다 (규칙은 PopupPolicy.kt).
 * 누를 것은 없다 (주행 화면).
 */
class MapPopupCard(parent: FrameLayout) {
    private val context = parent.context
    private val card: View = LayoutInflater.from(context).inflate(R.layout.popup_card, parent, false)
    private val icon: ImageView = card.findViewById(R.id.popupIcon)
    private val turn: TextView = card.findViewById(R.id.popupTurn)
    private val distance: TextView = card.findViewById(R.id.popupDistance)
    private val badgeIcon: ImageView = card.findViewById(R.id.popupBadgeIcon)
    private val headline: TextView = card.findViewById(R.id.popupHeadline)
    private val lever: LeverGuideView = card.findViewById(R.id.popupLever)
    private val ink = context.getColor(R.color.on_accent)
    private val density = context.resources.displayMetrics.density
    private val cardFill = GradientDrawable().apply { cornerRadius = CARD_RADIUS_DP * density }
    private var shown: Popup? = null

    init {
        card.background = cardFill
        card.findViewById<View>(R.id.popupBadge).background = GradientDrawable().apply {
            setColor(ink)
            cornerRadius = BADGE_RADIUS_DP * density
        }
        icon.imageTintList = ColorStateList.valueOf(ink)
        card.visibility = View.GONE
        parent.addView(
            card,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = (TOP_MARGIN_DP * density).toInt()
            },
        )
    }

    /** null이면 숨긴다. 같은 값이면 다시 그리지 않는다. */
    fun show(popup: Popup?) {
        if (popup == shown) return
        shown = popup
        if (popup == null) {
            card.visibility = View.GONE
            return
        }
        val fill = context.getColor(if (popup.tone == PopupTone.OK) R.color.ok else R.color.warn)
        cardFill.setColor(fill)
        icon.setImageResource(guideIconRes(popup.kind, popup.right))
        turn.text = popup.turnLabel
        distance.text = numberEmphasis(popup.distanceLabel)
        distance.visibility = if (popup.distanceLabel.isEmpty()) View.GONE else View.VISIBLE
        headline.text = popup.headline
        headline.setTextColor(fill)
        badgeIcon.setImageResource(if (popup.tone == PopupTone.OK) R.drawable.ic_check else R.drawable.ic_alert)
        badgeIcon.imageTintList = ColorStateList.valueOf(fill)
        lever.show(leverFor(popup.kind, popup.right, popup.tone == PopupTone.WARN), ink)
        card.visibility = View.VISIBLE
    }

    private companion object {
        const val CARD_RADIUS_DP = 32
        const val BADGE_RADIUS_DP = 999
        const val TOP_MARGIN_DP = 24
    }
}
