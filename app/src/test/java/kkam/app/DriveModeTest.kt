package kkam.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DriveModeTest {
    @Test
    fun `켬이면 안내하고 기록한다`() {
        assertTrue(guidesOn(DriveMode.ON))
        assertTrue(recordsOn(DriveMode.ON))
    }

    @Test
    fun `끔이면 안내도 기록도 하지 않는다`() {
        assertFalse(guidesOn(DriveMode.OFF))
        assertFalse(recordsOn(DriveMode.OFF))
    }

    @Test
    fun `저장된 켬 끔을 되찾고 모르는 값이면 켬`() {
        assertEquals(DriveMode.ON, DriveMode.fromName("ON"))
        assertEquals(DriveMode.OFF, DriveMode.fromName("OFF"))
        assertEquals(DriveMode.ON, DriveMode.fromName(null))
        assertEquals(DriveMode.ON, DriveMode.fromName("SPORT"))
    }

    @Test
    fun `예전 4개 모드는 음성 안내를 했는지로 옮긴다`() {
        assertEquals(DriveMode.ON, DriveMode.fromName("CORRECT"))
        assertEquals(DriveMode.ON, DriveMode.fromName("TRAINING"))
        assertEquals(DriveMode.OFF, DriveMode.fromName("DIAGNOSE"))
        assertEquals(DriveMode.OFF, DriveMode.fromName("QUIET"))
    }
}
