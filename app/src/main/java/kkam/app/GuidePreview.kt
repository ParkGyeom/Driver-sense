package kkam.app

/**
 * 데모 패널의 "안내 종류 미리보기"에 쓰는 예시 값. 엔진 출력이 아니라 화면 확인용 문구다.
 * 엔진이 차선 변경·전조등·와이퍼 룰을 내보내기 전에 그림과 배치를 미리 보려고 둔다.
 */
fun CoachState.preview(kind: GuideKind): CoachState {
    val (title, distance, status, headline) = when (kind) {
        GuideKind.TURN_SIGNAL -> listOf("좌회전", "60 m", "깜빡이 꺼짐", "지금 켜세요")
        GuideKind.LANE_CHANGE -> listOf("오른쪽 차로로", "300 m", "깜빡이 꺼짐", "깜빡이 켜고 옮기세요")
        GuideKind.HEADLIGHT -> listOf("터널 진입", "200 m", "전조등 꺼짐", "전조등 켜세요")
        GuideKind.WIPER -> listOf("비가 와요", "", "와이퍼 꺼짐", "와이퍼 켜세요")
        GuideKind.RED_RIGHT_TURN -> listOf("적색 우회전", "40 m", "전방 적색", "멈췄다 가세요")
    }
    return copy(
        guideKind = kind,
        tone = Tone.WARN,
        turnLabel = title,
        distanceLabel = distance,
        signalLabel = status,
        badge = headline,
        message = previewHowTo(kind, title),
    )
}

/** 미리보기 한 줄 문구이자 음성. 레버를 쓰는 안내는 레버 동작, 아니면 해야 할 일. */
fun previewHowTo(kind: GuideKind, title: String): String =
    leverMotion(kind, title)?.howTo ?: "정지선에서 완전히 멈췄다가 우회전하세요"

/** 미리보기 상태를 팝업으로 그대로 옮긴다. */
fun CoachState.previewPopup(): Popup = Popup(PopupTone.WARN, guideKind, turnLabel, distanceLabel, badge)
