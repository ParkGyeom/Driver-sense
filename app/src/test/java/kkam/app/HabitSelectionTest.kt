package kkam.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HabitSelectionTest {
    @Test
    fun `두 개까지 고르고 세 개째는 선택되지 않는다`() {
        val two = HabitSelection().toggle(Habit.LEFT_SIGNAL).toggle(Habit.RED_RIGHT_STOP)

        val three = two.toggle(Habit.RIGHT_SIGNAL)

        assertEquals(listOf(Habit.LEFT_SIGNAL, Habit.RED_RIGHT_STOP), three.selected)
        assertFalse(two.canToggle(Habit.RIGHT_SIGNAL))
        assertTrue(two.canToggle(Habit.LEFT_SIGNAL)) // 고른 것은 뺄 수 있다
    }

    @Test
    fun `고른 습관을 다시 누르면 빠지고 빈 자리에 다른 습관을 고를 수 있다`() {
        val selection = HabitSelection()
            .toggle(Habit.LEFT_SIGNAL)
            .toggle(Habit.RIGHT_SIGNAL)
            .toggle(Habit.LEFT_SIGNAL)
            .toggle(Habit.RED_RIGHT_STOP)

        assertEquals(listOf(Habit.RIGHT_SIGNAL, Habit.RED_RIGHT_STOP), selection.selected)
    }

    @Test
    fun `하나 이상 골라야 완료할 수 있다`() {
        assertFalse(HabitSelection().canFinish)
        assertTrue(HabitSelection().toggle(Habit.LEFT_SIGNAL).canFinish)
    }

    @Test
    fun `주행 화면 문구에 고른 순서대로 습관 이름을 쓴다`() {
        val selection = HabitSelection().toggle(Habit.LEFT_SIGNAL).toggle(Habit.RED_RIGHT_STOP)
        assertEquals("오늘 고치는 습관: 좌회전 깜빡이, 적색 우회전 일시정지", selection.summary())
    }
}
