package kkam.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DriveModeTest {
    @Test
    fun `진단과 조용 모드는 주행 중 안내를 하지 않는다`() {
        assertFalse(guidesOn(DriveMode.DIAGNOSE))
        assertFalse(guidesOn(DriveMode.QUIET))
    }

    @Test
    fun `교정과 연수 모드는 주행 중 안내를 한다`() {
        assertTrue(guidesOn(DriveMode.CORRECT))
        assertTrue(guidesOn(DriveMode.TRAINING))
    }

    @Test
    fun `안내 설명은 기획서 4_3 표와 같다`() {
        assertEquals(
            listOf("없음", "고른 습관만", "모든 룰", "없음"),
            DriveMode.entries.map { it.guideLabel },
        )
    }

    @Test
    fun `저장된 이름으로 모드를 되찾고 모르는 값이면 기본 모드`() {
        assertEquals(DriveMode.TRAINING, DriveMode.fromName("TRAINING"))
        assertEquals(DriveMode.DEFAULT, DriveMode.fromName(null))
        assertEquals(DriveMode.DEFAULT, DriveMode.fromName("SPORT"))
    }
}
