package kkam.app

/**
 * "어떻게 켜는지" 그림에서 어느 레버를 어떻게 움직이는지.
 * 현대·기아 공통 배치 기준: 왼쪽 레버 = 깜빡이·전조등, 오른쪽 레버 = 와이퍼.
 * 차종마다 다를 수 있어서 안내 문구에도 레버 위치를 함께 말한다.
 */
enum class LeverMotion(
    /** 오른쪽 레버(와이퍼)를 움직이면 true. 아니면 왼쪽 레버. */
    val rightLever: Boolean,
) {
    /** 왼쪽 레버를 아래로 끝까지: 좌회전 깜빡이. */
    LEFT_DOWN(false),

    /** 왼쪽 레버를 위로 끝까지: 우회전 깜빡이. */
    LEFT_UP(false),

    /** 왼쪽 레버를 아래로 살짝 눌렀다 놓기: 왼쪽 차로 변경 (깜빡이 세 번). */
    LEFT_TAP_DOWN(false),

    /** 왼쪽 레버를 위로 살짝 올렸다 놓기: 오른쪽 차로 변경 (깜빡이 세 번). */
    LEFT_TAP_UP(false),

    /** 왼쪽 레버 끝을 앞으로 돌리기: 전조등. */
    LEFT_TWIST(false),

    /** 오른쪽 레버를 아래로 한 칸: 와이퍼. */
    RIGHT_DOWN(true),
}

/**
 * 안내 종류와 방향으로 레버 동작을 정한다. 레버를 쓰지 않는 안내(적색 우회전 일시정지)는 null.
 * 방향: 회전은 turnLabel("좌회전"/"우회전"), 차선 변경은 제목의 "왼쪽"/"오른쪽".
 * TODO(박겸 spec): 차선 변경 방향이 GUIDE에 따로 오면 제목 대신 그 값을 쓴다.
 */
fun leverMotion(kind: GuideKind, turnLabel: String): LeverMotion? = when (kind) {
    GuideKind.TURN_SIGNAL -> if (turnLabel.contains("우")) LeverMotion.LEFT_UP else LeverMotion.LEFT_DOWN
    GuideKind.LANE_CHANGE -> if (turnLabel.contains("오른")) LeverMotion.LEFT_TAP_UP else LeverMotion.LEFT_TAP_DOWN
    GuideKind.HEADLIGHT -> LeverMotion.LEFT_TWIST
    GuideKind.WIPER -> LeverMotion.RIGHT_DOWN
    GuideKind.RED_RIGHT_TURN -> null
}

/**
 * 그림을 보여줄 안내인지. 초보 기능 안내(차선 변경, 전조등, 와이퍼)만 보여준다.
 * 회전 깜빡이는 누구나 아는 동작이라 그림 없이 화살표만 쓴다.
 * TODO(연수 모드, spec v0.2): 초보 운전자(연수 모드)면 회전 깜빡이에도 그림을 보여준다.
 */
fun leverFor(kind: GuideKind, turnLabel: String, warn: Boolean): LeverMotion? =
    if (warn && kind != GuideKind.TURN_SIGNAL) leverMotion(kind, turnLabel) else null

/** 레버 동작을 말로. 그림과 같이 보여주고, 미리보기에서는 음성으로 읽는다. */
val LeverMotion.howTo: String
    get() = when (this) {
        LeverMotion.LEFT_DOWN -> "왼쪽 레버를 아래로 내리면 좌회전 깜빡이가 켜져요"
        LeverMotion.LEFT_UP -> "왼쪽 레버를 위로 올리면 우회전 깜빡이가 켜져요"
        LeverMotion.LEFT_TAP_DOWN -> "왼쪽 레버를 아래로 살짝 눌렀다 놓으면 깜빡이가 세 번 깜빡여요"
        LeverMotion.LEFT_TAP_UP -> "왼쪽 레버를 위로 살짝 올렸다 놓으면 깜빡이가 세 번 깜빡여요"
        LeverMotion.LEFT_TWIST -> "왼쪽 레버 끝을 앞으로 돌리면 전조등이 켜져요"
        LeverMotion.RIGHT_DOWN -> "오른쪽 레버를 아래로 한 칸 내리면 와이퍼가 움직여요"
    }
