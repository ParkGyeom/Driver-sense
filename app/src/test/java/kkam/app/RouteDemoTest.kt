package kkam.app

import kkam.engine.Engine
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Result
import kkam.engine.model.Scenario
import kkam.engine.model.TurnType
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RouteDemoTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val legs = RouteDemo.LEGS.map { name ->
        json.decodeFromString(Scenario.serializer(), File("../scenarios/$name").readText()).frames
    }
    private val run = RouteDemo.build(legs)

    @Test
    fun `경로 정보는 구간마다 첫 회전으로 만든다`() {
        assertEquals(listOf(TurnType.LEFT, TurnType.RIGHT, TurnType.LEFT), run.plan.turns)
        assertEquals(RouteDemo.DESTINATION, run.plan.destination)
    }

    @Test
    fun `기어 P로 출발 전 미리보기를 보여주고 기어 P로 도착한다`() {
        val preview = run.frames.takeWhile { it.t < RouteDemo.PREVIEW_MS }
        assertTrue(preview.isNotEmpty() && preview.all { it.gear == Gear.P })
        assertEquals(Gear.D, run.frames.first { it.t >= RouteDemo.PREVIEW_MS }.gear)
        assertEquals(Gear.P, run.frames.last().gear)
        assertEquals(null, run.frames.last().tbt)
    }

    @Test
    fun `시간은 계속 늘어난다`() {
        run.frames.zipWithNext().forEach { (a, b) -> assertTrue(b.t > a.t, "${a.t} 다음 ${b.t}") }
    }

    @Test
    fun `엔진에 넣으면 구간마다 판정이 하나씩 나온다 - 지킴, 지킴, 놓침`() {
        val engine = Engine()
        val events = run.frames.flatMap { engine.onFrame(it) }.filterIsInstance<Event>()

        assertEquals(listOf(Result.KEPT, Result.KEPT, Result.MISSED), events.map { it.result })
        assertEquals(listOf(TurnType.LEFT, TurnType.RIGHT, TurnType.LEFT), events.map { it.turn })
    }
}
