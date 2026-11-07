package kkam.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeverMotionTest {
    @Test
    fun `회전 깜빡이는 좌회전이면 왼쪽 레버 아래, 우회전이면 위`() {
        assertEquals(LeverMotion.LEFT_DOWN, leverMotion(GuideKind.TURN_SIGNAL, "좌회전"))
        assertEquals(LeverMotion.LEFT_UP, leverMotion(GuideKind.TURN_SIGNAL, "우회전"))
    }

    @Test
    fun `차선 변경은 방향에 따라 살짝 눌렀다 놓는다`() {
        assertEquals(LeverMotion.LEFT_TAP_UP, leverMotion(GuideKind.LANE_CHANGE, "오른쪽 차로로"))
        assertEquals(LeverMotion.LEFT_TAP_DOWN, leverMotion(GuideKind.LANE_CHANGE, "왼쪽 차로로"))
    }

    @Test
    fun `전조등은 왼쪽 레버 끝을 돌리고 와이퍼는 오른쪽 레버를 내린다`() {
        assertEquals(LeverMotion.LEFT_TWIST, leverMotion(GuideKind.HEADLIGHT, "터널 진입"))
        assertEquals(LeverMotion.RIGHT_DOWN, leverMotion(GuideKind.WIPER, "비가 와요"))
        assertTrue(LeverMotion.RIGHT_DOWN.rightLever)
    }

    @Test
    fun `그림은 초보 기능 안내를 켜야 할 때(주황)만 보여준다`() {
        assertEquals(LeverMotion.RIGHT_DOWN, leverFor(GuideKind.WIPER, "비가 와요", warn = true))
        assertNull(leverFor(GuideKind.WIPER, "비가 와요", warn = false))
        assertNull(leverFor(GuideKind.TURN_SIGNAL, "좌회전", warn = true))
    }

    @Test
    fun `레버를 쓰지 않는 안내는 그림이 없고 해야 할 일을 말한다`() {
        assertNull(leverMotion(GuideKind.RED_RIGHT_TURN, "적색 우회전"))
        assertEquals("정지선에서 완전히 멈췄다가 우회전하세요", previewHowTo(GuideKind.RED_RIGHT_TURN, "적색 우회전"))
        assertEquals(LeverMotion.LEFT_TWIST.howTo, previewHowTo(GuideKind.HEADLIGHT, "터널 진입"))
    }
}
