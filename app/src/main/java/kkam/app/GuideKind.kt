package kkam.app

import kkam.engine.model.RuleId

/**
 * 주행 중 안내의 종류. 카드·팝업·상단 알림이 종류에 맞는 그림과 제목을 고른다.
 * 엔진은 아직 회전 깜빡이(R1)만 안내한다. 나머지는 박겸 spec(새 룰, 전조등·와이퍼 입력)이 나오면 [guideKind]에 연결한다.
 */
enum class GuideKind(
    /** 미리보기와 기록에 쓰는 이름. */
    val label: String,
) {
    TURN_SIGNAL("회전 깜빡이"),
    LANE_CHANGE("차선 변경"),
    HEADLIGHT("전조등"),
    WIPER("와이퍼"),
    RED_RIGHT_TURN("적색 우회전"),
}

/**
 * 엔진 룰 → 안내 종류. 음성 안내가 없는 룰(R2: 깜빡이 없이 회전, 기록만)은 null.
 * TODO(박겸 spec): 차선 변경·전조등·와이퍼 룰이 생기면 여기에 더한다.
 */
fun RuleId.guideKind(): GuideKind? = when (this) {
    RuleId.R1 -> GuideKind.TURN_SIGNAL
    RuleId.R2 -> null
    RuleId.R3 -> GuideKind.RED_RIGHT_TURN
}
