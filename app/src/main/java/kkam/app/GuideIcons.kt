package kkam.app

/** 안내 종류에 맞는 그림. 코치 카드, 팝업, 상단 알림이 같이 쓴다. 회전 깜빡이는 방향에 따라 화살표가 다르다. */
fun guideIconRes(kind: GuideKind, right: Boolean): Int = when (kind) {
    GuideKind.TURN_SIGNAL -> if (right) R.drawable.ic_turn_right else R.drawable.ic_turn_left
    GuideKind.LANE_CHANGE -> R.drawable.ic_lane_change
    GuideKind.HEADLIGHT -> R.drawable.ic_headlight
    GuideKind.WIPER -> R.drawable.ic_wiper
    GuideKind.RED_RIGHT_TURN -> R.drawable.ic_red_light
}
