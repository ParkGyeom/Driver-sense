package kkam.app

import kkam.engine.Engine
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.Result
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RouteDemoTest {
    private val route = RouteDemo.parse(File("src/main/assets/${RouteDemo.ASSET}").readText())
    private val run = RouteDemo.build(route)

    @Test
    fun `경로 정보는 데모 파일의 회전 순서와 좌표다`() {
        assertEquals(listOf(TurnType.LEFT, TurnType.RIGHT, TurnType.LEFT, TurnType.RIGHT), run.plan.turns)
        assertEquals(route.points, run.plan.path)
        assertTrue(run.plan.distanceM in 1000..1200, "${run.plan.distanceM}")
    }

    @Test
    fun `기어 P로 출발 전 미리보기를 보여주고 기어 P로 도착한다`() {
        val preview = run.frames.takeWhile { it.t < RouteDemo.PREVIEW_MS }
        assertTrue(preview.isNotEmpty() && preview.all { it.gear == Gear.P && it.speedKph == 0.0 })
        assertEquals(Gear.D, run.frames.first { it.t >= RouteDemo.PREVIEW_MS }.gear)
        assertEquals(Gear.P, run.frames.last().gear)
        assertEquals(null, run.frames.last().tbt)
        assertEquals(route.points.last(), LatLon(run.frames.last().lat, run.frames.last().lon))
    }

    @Test
    fun `시간은 1초씩, 속도는 순항 속도를 넘지 않고, 남은 거리는 회전마다 줄어든다`() {
        run.frames.zipWithNext().forEach { (a, b) -> assertEquals(RouteDemo.FRAME_MS, b.t - a.t) }
        assertTrue(run.frames.all { it.speedKph <= RouteDemo.CRUISE_KPH })
        // 같은 회전을 향하는 동안 남은 거리는 늘지 않는다
        run.frames.zipWithNext().filter { (a, b) -> a.tbt != null && b.tbt != null && a.tbt?.turn == b.tbt?.turn && a.gear == Gear.D }
            .forEach { (a, b) -> assertTrue(b.tbt!!.distanceM <= a.tbt!!.distanceM + 1, "${a.t}: ${a.tbt} → ${b.tbt}") }
    }

    @Test
    fun `깜빡이 각본 - 첫 회전은 안내 뒤에, 둘째는 먼저, 셋째는 안 켠다`() {
        /** 그 회전에 다가가는 100m 안에서 나온 깜빡이 값 (앞 회전에서 켜 둔 것이 꺼진 뒤). */
        fun signalsBefore(turnIndex: Int): Set<TurnSignal> {
            var seen = -1
            var last: TurnType? = null
            val out = mutableSetOf<TurnSignal>()
            for (f in run.frames) {
                val turn = f.tbt?.turn ?: continue
                if (turn != last) seen++
                last = turn
                if (seen == turnIndex && f.tbt!!.distanceM <= 100) out += f.turnSignal
            }
            return out
        }
        assertTrue(TurnSignal.LEFT in signalsBefore(0))
        assertTrue(TurnSignal.RIGHT in signalsBefore(1))
        assertEquals(setOf(TurnSignal.OFF), signalsBefore(2))
    }

    @Test
    fun `엔진에 넣으면 회전마다 판정이 나온다 - 안내 후 지킴, 안내 없이 지킴, 놓침, 안내 후 지킴`() {
        val engine = Engine()
        val outputs = run.frames.flatMap { engine.onFrame(it) }
        val events = outputs.filterIsInstance<Event>()

        assertEquals(listOf(Result.KEPT, Result.KEPT, Result.MISSED, Result.KEPT), events.map { it.result })
        assertEquals(listOf(true, false, true, true), events.map { it.guided })
        assertEquals(listOf(TurnType.LEFT, TurnType.RIGHT, TurnType.LEFT, TurnType.RIGHT), events.map { it.turn })
        assertEquals(3, outputs.filterIsInstance<Guide>().size)
    }

    @Test
    fun `거리와 방향 계산`() {
        val a = LatLon(37.4, 127.1)
        assertEquals(0.0, bearing(a, LatLon(37.401, 127.1)), 0.5) // 북
        assertEquals(90.0, bearing(a, LatLon(37.4, 127.101)), 0.5) // 동
        assertEquals(111.2, meters(a, LatLon(37.401, 127.1)), 0.5)
    }
}
