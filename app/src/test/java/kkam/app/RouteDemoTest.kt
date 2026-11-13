package kkam.app

import kkam.engine.Engine
import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.HeadlightState
import kkam.engine.model.LightPhase
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kkam.engine.model.WiperMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RouteDemoTest {
    private val route = RouteDemo.parse(File("src/main/assets/${RouteDemo.ASSET}").readText())
    private val run = RouteDemo.build(route)
    private val outputs: List<Pair<Long, EngineOutput>> = Engine().let { engine ->
        run.frames.flatMap { f -> engine.onFrame(f).map { f.t to it } }
    }

    @Test
    fun `경로는 세종대학교에서 포티투닷 판교까지 약 25km, 코치 지점은 좌·적색 우·차선·비·터널·나들목·우 순서다`() {
        assertEquals("세종대학교", route.start)
        assertEquals("포티투닷 (42dot)", route.destination)
        assertTrue(run.plan.distanceM in 24000..27000, "${run.plan.distanceM}")
        assertEquals(
            listOf(
                RouteDemo.CueKind.LEFT,
                RouteDemo.CueKind.RIGHT,
                RouteDemo.CueKind.LANE_CHANGE,
                RouteDemo.CueKind.RAIN,
                RouteDemo.CueKind.TUNNEL,
                RouteDemo.CueKind.RAMP_RIGHT,
                RouteDemo.CueKind.RIGHT,
            ),
            route.cues.map { it.kind },
        )
        // 차선 변경과 비는 내비 안내가 아니라서 경로 안내 순서(미리보기 칩)에 없다
        assertEquals(listOf(TurnType.LEFT, TurnType.RIGHT, TurnType.TUNNEL, TurnType.RAMP_RIGHT, TurnType.RIGHT), run.plan.turns)
        assertEquals(route.points, run.plan.path)
        // 미리보기의 예상 소요 시간: 자동차전용도로 60 km/h, 시내 25 km/h로 어림한 실제 주행 시간 (재생 시간이 아니다)
        assertTrue(run.plan.durationMin!! in 30..50, "${run.plan.durationMin}분")
        assertTrue(route.cues.zipWithNext().all { (a, b) -> a.point < b.point })
    }

    @Test
    fun `엔진에 넣으면 지점마다 기대한 안내와 판정이 순서대로 나온다`() {
        val shown = outputs.map { (_, out) ->
            when (out) {
                is Guide -> "GUIDE ${out.rule}"
                is Event -> "EVENT ${out.rule} ${out.result} guided=${out.guided}"
            }
        }
        assertEquals(
            listOf(
                // 광나루로 좌회전: 안내 듣고 켬 → 지킴
                "GUIDE R1",
                "EVENT R1 KEPT guided=true",
                // 능동로 적색 우회전: 깜빡이 안내 → 켬, 이어서 적색이 되어 정지 안내 → 정지선에서 멈춤 → 둘 다 지킴
                "GUIDE R1",
                "GUIDE R3",
                "EVENT R1 KEPT guided=true",
                "EVENT R3 KEPT guided=true",
                // 자동차전용도로 비 구간: 와이퍼 안내 → 켬 → 지킴, 이어서 김서림 안내 → 성에 제거 켬 → 지킴
                "GUIDE R6",
                "EVENT R6 KEPT guided=true",
                "GUIDE R7",
                "EVENT R7 KEPT guided=true",
                // 분당수서로 터널: 전조등 안내 → 켬 → 지킴
                "GUIDE R5",
                "EVENT R5 KEPT guided=true",
                // 서현로 방면 나들목: 안내 듣고 오른쪽 깜빡이 → 지킴
                "GUIDE R4",
                "EVENT R4 KEPT guided=true",
                // 서현로 우회전: 안내했지만 안 켬 → 놓침 (리포트의 놓침 1건)
                "GUIDE R1",
                "EVENT R1 MISSED guided=true",
            ),
            shown,
        )
        assertTrue(outputs.none { it.second.rule == RuleId.R2 }, "빨리 감기·굽은 길에서 R2가 나오면 안 된다")
    }

    @Test
    fun `안내와 판정은 모두 실제 속도 구간에서 나온다`() {
        outputs.forEach { (t, out) -> assertTrue(t !in run.fastForward, "${out.rule} at $t") }
        // 판정 직전 프레임들도 실제 속도 (빨리 감기에서 바로 판정으로 뛰지 않는다)
        outputs.filter { it.second is Guide }.forEach { (t, out) ->
            assertTrue(t - RouteDemo.FRAME_MS !in run.fastForward, "${out.rule} 안내 바로 앞이 빨리 감기")
        }
    }

    @Test
    fun `3분 30초 안에 끝난다 - 미리보기, 주행, 도착 뒤 기어 P까지`() {
        val total = run.frames.last().t + RouteDemo.FRAME_MS
        // 안내 카드를 영상에서 읽을 수 있게 주황·파랑을 몇 초씩 보여주느라 3분을 조금 넘는다
        assertTrue(total <= 210_000, "총 ${total / 1000}초")
        assertTrue(run.fastForward.isNotEmpty())
        // 빨리 감는 프레임은 30 km/h보다 빨라서 R2가 회전으로 세지 않는다
        run.frames.filter { it.t in run.fastForward }.forEach { assertTrue(it.speedKph > 30.0, "${it.t}") }
    }

    @Test
    fun `기어 P로 출발 전 미리보기를 보여주고 기어 P로 도착한다`() {
        val preview = run.frames.takeWhile { it.t < RouteDemo.PREVIEW_MS }
        assertTrue(preview.isNotEmpty() && preview.all { it.gear == Gear.P && it.speedKph == 0.0 })
        assertEquals(Gear.D, run.frames.first { it.t >= RouteDemo.PREVIEW_MS }.gear)
        assertEquals(Gear.P, run.frames.last().gear)
        assertEquals(null, run.frames.last().tbt)
        assertEquals(route.points.last(), LatLon(run.frames.last().lat, run.frames.last().lon))
        val arrive = run.frames.single { it.t == run.arriveAt }
        assertEquals(Gear.D, arrive.gear)
        assertEquals(null, arrive.tbt)
        assertTrue(run.frames.filter { it.t > run.arriveAt }.all { it.gear == Gear.P })
    }

    @Test
    fun `시간은 1초씩, 속도는 순항 속도를 넘지 않고, 실제 속도 구간의 남은 거리는 늘지 않는다`() {
        run.frames.zipWithNext().forEach { (a, b) -> assertEquals(RouteDemo.FRAME_MS, b.t - a.t) }
        assertTrue(run.frames.all { it.speedKph <= RouteDemo.EXPRESS_KPH })
        run.frames.zipWithNext()
            .filter { (a, b) -> a.gear == Gear.D && a.tbt != null && b.tbt != null && a.tbt?.turn == b.tbt?.turn }
            .forEach { (a, b) -> assertTrue(b.tbt!!.distanceM <= a.tbt!!.distanceM + 1, "${a.t}: ${a.tbt} → ${b.tbt}") }
    }

    @Test
    fun `적색 우회전 앞에서는 신호가 적색이고 정지선에서 멈춘다, 전조등은 터널 앞에서 켠다`() {
        val red = run.frames.filter { it.light?.phase == LightPhase.RED }
        assertTrue(red.isNotEmpty() && red.all { it.tbt?.turn == TurnType.RIGHT || it.tbt?.turn == TurnType.TUNNEL })
        assertTrue(red.count { it.gear == Gear.D && it.speedKph == 0.0 } >= RouteDemo.RED_STOP_FRAMES)
        val firstOn = run.frames.first { it.headlight == HeadlightState.ON }
        assertEquals(TurnType.TUNNEL, firstOn.tbt?.turn)
        assertTrue(firstOn.tbt!!.distanceM <= RouteDemo.HEADLIGHT_ON_M)
    }

    @Test
    fun `적색 우회전은 깜빡이 안내와 정지 안내를 다른 프레임에서 차례로 낸다 - 사이에 깜빡이를 켠다`() {
        val red = route.cues.single { it.redLight }
        assertEquals(RouteDemo.Driver.AFTER_GUIDE, red.driver)
        val r3 = outputs.single { it.second is Guide && it.second.rule == RuleId.R3 }.first
        val r1BeforeRed = outputs.last { it.second is Guide && it.second.rule == RuleId.R1 && it.first < r3 }.first
        assertEquals(TurnType.RIGHT, run.frames.single { it.t == r1BeforeRed }.tbt?.turn)
        // 깜빡이 안내를 3초 이상 읽고, 켠 뒤 "좋아요"도 보인 다음에 정지 안내가 나온다 (음성이 서로 끊지 않는다)
        assertTrue(r3 - r1BeforeRed >= 5_000, "R1 ${r1BeforeRed / 1000}s, R3 ${r3 / 1000}s")
        val signalOn = run.frames.first { it.t > r1BeforeRed && it.turnSignal == TurnSignal.RIGHT }.t
        assertTrue(signalOn - r1BeforeRed >= 3_000 && signalOn < r3, "깜빡이 ${signalOn / 1000}s")
        assertEquals(null, run.frames.single { it.t == signalOn }.light, "깜빡이를 켤 때는 아직 적색이 아니다")
    }

    @Test
    fun `도착 장면은 짧다 - 마지막 판정에서 기어 P까지 15초 안`() {
        val lastEvent = outputs.last { it.second is Event }.first
        val parked = run.frames.first { it.gear == Gear.P && it.t > run.arriveAt }.t
        assertTrue(parked - lastEvent <= 15_000, "마지막 판정 ${lastEvent / 1000}s → 기어 P ${parked / 1000}s")
        // 멈추기 전 마지막 몇 m를 5 km/h로 기어가지 않는다
        val crawl = run.frames.filter { it.t in lastEvent..run.arriveAt && it.speedKph in 0.1..RouteDemo.MIN_KPH }
        assertTrue(crawl.size <= 1, "기어가는 프레임 ${crawl.size}개")
    }

    @Test
    fun `차선 변경은 팝업만 - 오른쪽 깜빡이를 잠깐 켜고 판정은 없다`() {
        // 팝업이 떠 있는 동안은 실제 속도 (빨리 감기에서 팝업이 스쳐 지나가지 않는다)
        run.laneCues.forEach { cue -> assertTrue((cue.showAt until cue.hideAt step RouteDemo.FRAME_MS).none { it in run.fastForward }) }
        assertEquals(1, run.laneCues.size)
        val cue = run.laneCues.single()
        assertTrue(cue.showAt < cue.okAt && cue.okAt < cue.hideAt)
        // 어느 쪽 차로로 옮기는지 팝업 제목과 음성이 말한다
        assertTrue(cue.toRight)
        assertEquals("오른쪽 차로로", laneChangePopup(run.laneCues, cue.showAt)?.turnLabel)
        assertEquals("오른쪽 차로로 옮겨요, 오른쪽 깜빡이 켜세요", laneChangeSpeech(cue.toRight))
        val during = run.frames.filter { it.t >= cue.okAt && it.t < cue.okAt + RouteDemo.LANE_SIGNAL_MS }
        assertTrue(during.isNotEmpty() && during.all { it.turnSignal == TurnSignal.RIGHT && it.speedKph > 30.0 })
        assertTrue(outputs.none { (t, _) -> t in cue.showAt..cue.hideAt })
    }

    @Test
    fun `비 구간 - 와이퍼 안내 뒤 와이퍼를 켜고, 김서림 안내 뒤 성에 제거를 켠다`() {
        val rainy = run.frames.filter { it.rain == true }
        assertTrue(rainy.isNotEmpty())
        val start = rainy.first().t
        // 비는 한 번 와서 그친다 (구간 하나)
        assertEquals(RouteDemo.RAIN_END_AFTER_MS / RouteDemo.FRAME_MS, rainy.size.toLong())
        assertTrue(rainy.zipWithNext().all { (a, b) -> b.t - a.t == RouteDemo.FRAME_MS })
        // 비 구간은 모두 실제 속도이고 자동차전용도로 순항 속도로 달린다 (빨리 감기에 묻히지 않는다)
        assertTrue(rainy.all { it.t !in run.fastForward && it.gear == Gear.D && it.speedKph == RouteDemo.EXPRESS_KPH })
        // 안내 → 조작 순서
        val wiperGuide = outputs.single { it.second is Guide && it.second.rule == RuleId.R6 }.first
        val fogGuide = outputs.single { it.second is Guide && it.second.rule == RuleId.R7 }.first
        assertTrue(wiperGuide < start + RouteDemo.WIPER_ON_AFTER_MS, "와이퍼 안내가 와이퍼를 켜기 전에 나온다")
        assertTrue(start + RouteDemo.WIPER_ON_AFTER_MS < fogGuide, "김서림 안내는 와이퍼 안내가 끝난 뒤에 나온다")
        assertTrue(fogGuide < start + RouteDemo.DEFROST_ON_AFTER_MS, "김서림 안내가 성에 제거를 켜기 전에 나온다")
        // 비 밖에서는 와이퍼·성에 제거가 꺼져 있고 내기 순환은 늘 켜져 있다
        assertTrue(run.frames.filter { it.rain != true }.all { it.wiper == WiperMode.OFF && it.defroster == false })
        assertTrue(run.frames.all { it.recirc == true })
    }

    /** 보고용 이벤트 순서표. `./gradlew :app:testDebugUnitTest --tests "*RouteDemoTest*" -i`로 본다. */
    @Test
    fun `이벤트 순서표 출력`() {
        val byT = run.frames.associateBy { it.t }
        println("총 ${(run.frames.last().t + RouteDemo.FRAME_MS) / 1000}초, 빨리 감기 ${run.fastForward.size}프레임, 거리 ${run.plan.distanceM} m")
        run.laneCues.forEach { println("t=${it.showAt / 1000}s LANE_CHANGE 팝업 (깜빡이 ${it.okAt / 1000}s)") }
        outputs.forEach { (t, out) ->
            val f = byT.getValue(t)
            val what = when (out) {
                is Guide -> "GUIDE ${out.rule} \"${out.text}\""
                is Event -> "EVENT ${out.rule} ${out.result} guided=${out.guided}"
            }
            println("t=${t / 1000}s ${f.speedKph.toInt()}km/h tbt=${f.tbt} $what")
        }
    }
}
