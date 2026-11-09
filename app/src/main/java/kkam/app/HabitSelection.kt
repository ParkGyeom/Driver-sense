package kkam.app

/**
 * 습관 선택 화면의 선택 상태 (기획서 4.1 교정: 고칠 습관 1~2개).
 * Android에 의존하지 않아서 JVM 테스트로 확인한다.
 */
data class HabitSelection(
    /** 고른 순서대로. */
    val selected: List<Habit> = emptyList(),
) {
    /** 이미 골랐거나, 아직 [MAX_HABITS]개 미만이면 누를 수 있다. */
    fun canToggle(habit: Habit): Boolean = habit in selected || selected.size < MAX_HABITS

    /** 고른 습관은 빼고, 안 고른 습관은 더한다. 이미 [MAX_HABITS]개면 그대로 둔다. */
    fun toggle(habit: Habit): HabitSelection = when {
        habit in selected -> copy(selected = selected - habit)
        selected.size < MAX_HABITS -> copy(selected = selected + habit)
        else -> this
    }

    /** 하나 이상 골라야 완료할 수 있다. */
    val canFinish: Boolean get() = selected.isNotEmpty()

    /** 주행 화면 한 줄 문구. 예: "오늘 고치는 습관: 좌회전 깜빡이" */
    fun summary(): String =
        if (selected.isEmpty()) "고른 습관이 없어요" else "오늘 고치는 습관: ${selected.joinToString(", ") { it.label }}"
}

/** 한 번에 고칠 수 있는 습관 수 (기획서 UI-3: 습관 카드 최대 2개). */
const val MAX_HABITS = 2
