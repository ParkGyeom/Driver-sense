package kkam.app

import kkam.engine.model.Gear
import kkam.engine.model.SignalFrame
import kkam.engine.model.Tbt
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * PT용 "경로 데모". 실제 도로 경로(assets/route_demo_pangyo.json)를 따라 차가 달리는 프레임을 만든다.
 *
 * 에뮬레이터 내비는 위치가 움직이지 않는다 (GPS 문장의 체크섬이 고정값이라 내비가 모든 위치를 버린다).
 * 그래서 위치, 진행 방향, 다음 회전과 남은 거리(TBT)를 이 파일이 경로 좌표로 계산한다.
 * 같은 경로를 넣으면 항상 같은 프레임이 나온다 (현재 시각, 랜덤을 쓰지 않는다). Android에 의존하지 않아서 JVM 테스트로 확인한다.
 *
 * 흐름: 출발 전 기어 P(경로 미리보기) → 회전마다 감속·코치 → 도착해 기어 P(경로 리포트)
 */
object RouteDemo {
    const val ASSET = "route_demo_pangyo.json"

    /** 회전마다 운전자가 깜빡이를 어떻게 켜는지. 시연에서 지킴·놓침 장면이 고루 나오게 정한다. */
    enum class Driver {
        /** 안내를 듣고 켠다 → 안내 후 지킴 */
        AFTER_GUIDE,

        /** 안내 전에 먼저 켠다 → 안내 없이 지킴 */
        EARLY,

        /** 끝까지 안 켠다 → 안내 후 놓침 */
        NEVER,
    }

    /** 경로의 회전 순서대로. 회전이 더 많으면 마지막 값을 반복한다. */
    val DRIVERS = listOf(Driver.AFTER_GUIDE, Driver.EARLY, Driver.NEVER, Driver.AFTER_GUIDE)

    /** 출발 전 미리보기를 보여주는 시간. */
    const val PREVIEW_MS = 8000L
    const val FRAME_MS = 1000L
    const val CRUISE_KPH = 40.0
    const val TURN_KPH = 15.0
    /** 회전 지점 이 거리 앞부터 [TURN_KPH]까지 줄인다. */
    const val SLOW_FROM_M = 60.0
    /** 1초에 올릴 수 있는 속도. */
    const val ACCEL_KPH_PER_FRAME = 8.0
    /** 도착 지점 이 거리 앞부터 멈출 때까지 줄인다. */
    const val STOP_FROM_M = 50.0
    const val MIN_KPH = 5.0
    /** [Driver.EARLY]가 깜빡이를 켜는 거리 (안내 거리 50~100m보다 앞). */
    const val EARLY_ON_M = 95
    /** [Driver.AFTER_GUIDE]가 깜빡이를 켜는 거리 (안내 뒤, 판정 30m 전). */
    const val AFTER_GUIDE_ON_M = 42
    /** 회전 지점을 이만큼 지나면 깜빡이를 끈다. */
    const val SIGNAL_OFF_PAST_M = 15.0
    /** 도착 후 기어 P로 서 있는 프레임 수. */
    const val PARKED_FRAMES = 3

    /** 경로 데모 파일의 회전 하나. [point]는 [DemoRoute.points]의 번호. */
    data class Maneuver(val turn: TurnType, val point: Int)

    data class DemoRoute(val destination: String, val points: List<LatLon>, val maneuvers: List<Maneuver>)

    /** 데모 한 번: 경로 정보, 재생할 프레임, 도착 프레임의 시각 (이때 경로를 도착으로 끝낸다). */
    data class Run(val plan: RoutePlan, val frames: List<SignalFrame>, val arriveAt: Long)

    /** assets의 경로 데모 JSON을 읽는다. 모양: {"destination", "points": [[lat, lon], ...], "maneuvers": [{"turn", "point"}]} */
    fun parse(text: String): DemoRoute {
        val root = Json.parseToJsonElement(text).jsonObject
        return DemoRoute(
            destination = root.getValue("destination").jsonPrimitive.content,
            points = root.getValue("points").jsonArray.map {
                val (lat, lon) = it.jsonArray.map { v -> v.jsonPrimitive.double }
                LatLon(lat, lon)
            },
            maneuvers = root.getValue("maneuvers").jsonArray.map {
                val m = it.jsonObject
                Maneuver(TurnType.valueOf(m.getValue("turn").jsonPrimitive.content), m.getValue("point").jsonPrimitive.int)
            },
        )
    }

    fun build(route: DemoRoute): Run {
        val path = Path(route.points)
        val turns = route.maneuvers.map { Turn(it.turn, path.distanceAt(it.point)) }
        val frames = mutableListOf<SignalFrame>()
        var t = 0L

        // 출발 전: 기어 P로 서 있다. 내비는 첫 회전을 이미 알고 있다.
        while (t < PREVIEW_MS) {
            frames += frame(t, path, 0.0, 0.0, Gear.P, TurnSignal.OFF, brake = true, tbt = tbtAt(turns, path, 0.0))
            t += FRAME_MS
        }

        // 주행: 1초마다 지금 위치의 프레임을 내고, 그 속도로 1초만큼 나아간다.
        var s = 0.0
        var kph = 0.0
        while (s < path.length) {
            val target = targetKph(turns, path.length, s)
            val next = min(target, kph + ACCEL_KPH_PER_FRAME)
            frames += frame(t, path, s, next, Gear.D, signalAt(turns, s), brake = next < kph, tbt = tbtAt(turns, path, s))
            kph = next
            s += kph / KPH_PER_MPS * FRAME_MS / MS_PER_SECOND
            t += FRAME_MS
        }

        // 도착: 멈추고 기어 P. 내비 안내도 끝난다.
        val end = path.length
        val arriveAt = t
        frames += frame(t, path, end, 0.0, Gear.D, TurnSignal.OFF, brake = true, tbt = null)
        repeat(PARKED_FRAMES) {
            t += FRAME_MS
            frames += frame(t, path, end, 0.0, Gear.P, TurnSignal.OFF, brake = true, tbt = null)
        }

        val plan = RoutePlan(
            destination = route.destination,
            distanceM = path.length.roundToInt(),
            turns = route.maneuvers.map { it.turn },
            path = route.points,
        )
        return Run(plan, frames, arriveAt)
    }

    private data class Turn(val turn: TurnType, val atM: Double)

    /** 다음 회전 속도로 줄이고, 도착 앞에서는 멈출 만큼 줄인다. */
    private fun targetKph(turns: List<Turn>, length: Double, s: Double): Double {
        val toTurn = turns.firstOrNull { it.atM > s }?.let { it.atM - s }
        val forTurn = if (toTurn != null && toTurn < SLOW_FROM_M) {
            TURN_KPH + (CRUISE_KPH - TURN_KPH) * toTurn / SLOW_FROM_M
        } else {
            CRUISE_KPH
        }
        val toEnd = length - s
        val forStop = if (toEnd < STOP_FROM_M) CRUISE_KPH * toEnd / STOP_FROM_M else CRUISE_KPH
        return maxOf(MIN_KPH, minOf(forTurn, forStop))
    }

    /** 다음 회전과 남은 거리. 마지막 회전 뒤에는 목적지까지 직진. */
    private fun tbtAt(turns: List<Turn>, path: Path, s: Double): Tbt {
        val next = turns.firstOrNull { it.atM > s }
        return if (next != null) {
            Tbt(next.turn, (next.atM - s).roundToInt())
        } else {
            Tbt(TurnType.STRAIGHT, (path.length - s).roundToInt())
        }
    }

    /** 운전자 각본([DRIVERS])대로 깜빡이. 회전 앞에서 켜고, 지나서 [SIGNAL_OFF_PAST_M] 뒤에 끈다. */
    private fun signalAt(turns: List<Turn>, s: Double): TurnSignal {
        turns.forEachIndexed { index, turn ->
            val ahead = turn.atM - s
            if (ahead < -SIGNAL_OFF_PAST_M) return@forEachIndexed
            val onAt = when (DRIVERS[min(index, DRIVERS.lastIndex)]) {
                Driver.AFTER_GUIDE -> AFTER_GUIDE_ON_M
                Driver.EARLY -> EARLY_ON_M
                Driver.NEVER -> return TurnSignal.OFF
            }
            return if (ahead <= onAt) turn.turn.signal() else TurnSignal.OFF
        }
        return TurnSignal.OFF
    }

    private fun TurnType.signal(): TurnSignal = when (this) {
        TurnType.LEFT, TurnType.RAMP_LEFT, TurnType.UTURN -> TurnSignal.LEFT
        TurnType.RIGHT, TurnType.RAMP_RIGHT -> TurnSignal.RIGHT
        else -> TurnSignal.OFF
    }

    private fun frame(t: Long, path: Path, s: Double, kph: Double, gear: Gear, signal: TurnSignal, brake: Boolean, tbt: Tbt?): SignalFrame {
        val at = path.pointAt(s)
        return SignalFrame(
            t = t,
            speedKph = kph,
            gear = gear,
            steeringDeg = 0.0,
            turnSignal = signal,
            brake = brake,
            lat = at.lat,
            lon = at.lon,
            headingDeg = path.headingAt(s),
            tbt = tbt,
            light = null,
        )
    }

    /** 경로 좌표를 따라 거리(m)로 위치와 진행 방향을 찾는다. */
    private class Path(private val points: List<LatLon>) {
        /** 출발점에서 각 꼭짓점까지의 거리 (m). */
        private val cumulative: List<Double> =
            points.zipWithNext { a, b -> meters(a, b) }.runningFold(0.0) { total, step -> total + step }
        val length: Double get() = cumulative.last()

        fun distanceAt(point: Int): Double = cumulative[point]

        /** [s]가 들어 있는 구간 번호. 꼭짓점에 딱 걸리면 다음 구간 (회전 뒤 방향). */
        private fun segmentAt(s: Double): Int =
            (1 until points.size).firstOrNull { cumulative[it] > s }?.minus(1) ?: (points.size - 2)

        fun pointAt(s: Double): LatLon {
            val i = segmentAt(s)
            val span = cumulative[i + 1] - cumulative[i]
            val f = if (span == 0.0) 0.0 else ((s - cumulative[i]) / span).coerceIn(0.0, 1.0)
            val a = points[i]
            val b = points[i + 1]
            return LatLon(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
        }

        fun headingAt(s: Double): Double {
            val i = segmentAt(s)
            return bearing(points[i], points[i + 1])
        }
    }

    private const val KPH_PER_MPS = 3.6
    private const val MS_PER_SECOND = 1000.0
}

/** 두 좌표 사이 거리 (m). 짧은 거리라 평면 근사로 충분하다. */
fun meters(a: LatLon, b: LatLon): Double {
    val dLat = Math.toRadians(b.lat - a.lat) * EARTH_RADIUS_M
    val dLon = Math.toRadians(b.lon - a.lon) * EARTH_RADIUS_M * cos(Math.toRadians((a.lat + b.lat) / 2))
    return sqrt(dLat * dLat + dLon * dLon)
}

/** a에서 b로 가는 방향. 북쪽 0°, 시계 방향 (SignalFrame.headingDeg와 같다). */
fun bearing(a: LatLon, b: LatLon): Double {
    val dLat = b.lat - a.lat
    val dLon = (b.lon - a.lon) * cos(Math.toRadians((a.lat + b.lat) / 2))
    return (Math.toDegrees(atan2(dLon, dLat)) + FULL_TURN_DEG) % FULL_TURN_DEG
}

private const val EARTH_RADIUS_M = 6_371_000.0
private const val FULL_TURN_DEG = 360.0
