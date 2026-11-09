package kkam.app

import kkam.engine.model.Gear

/** screenSlot에 들어가는 화면. */
enum class Screen {
    /** 주행 화면 (UI-1). 코치 카드. */
    DRIVE,

    /** 오늘 주행 리포트 (UI-2). */
    REPORT,

    /** 진단 리포트 (UI-3 앞부분). */
    DIAGNOSE,

    /** 습관 선택 (UI-3 뒷부분). */
    HABIT,

    /** 교정 진행·주간 코칭 (UI-4). */
    PROGRESS,

    /** 법규 Q&A (UI-5). */
    QNA,

    /** 모드 설정 (F3: 진단·교정·연수·조용). */
    MODE,
}

/** 기어 P에서만 여는 화면 (기획서 5.4: 리포트, 습관 선택, Q&A는 기어 P에서만). */
val Screen.parkedOnly: Boolean get() = this != Screen.DRIVE

/**
 * 새 프레임의 기어로 다음 화면을 정한다.
 * - P가 아니면 무조건 주행 화면.
 * - P인데 이미 정차 화면(리포트, 진단, 습관, 교정 진행, Q&A, 모드)을 보고 있으면 그대로 둔다. 리포트로 덮지 않는다.
 * - P인데 주행 화면이면 오늘 주행 리포트.
 */
fun nextScreen(current: Screen, gear: Gear): Screen = when {
    gear != Gear.P -> Screen.DRIVE
    current.parkedOnly -> current
    else -> Screen.REPORT
}

/** 사용자가 화면을 열려고 할 때. 기어 P가 아니면 P 전용 화면은 열지 않고 주행 화면에 머문다. */
fun openScreen(target: Screen, gear: Gear?): Screen =
    if (target.parkedOnly && gear != Gear.P) Screen.DRIVE else target
