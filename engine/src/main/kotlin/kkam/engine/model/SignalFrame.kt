package kkam.engine.model

import kotlinx.serialization.Serializable

/** 차량 신호 한 프레임 (spec 2.1). */
@Serializable
data class SignalFrame(
    /** 시각 (ms). 시나리오는 0부터 시작한다. */
    val t: Long,
    val speedKph: Double,
    val gear: Gear,
    /** 핸들 각도. 왼쪽이 음수. SDK에서 안 나오면 null. */
    val steeringDeg: Double?,
    val turnSignal: TurnSignal,
    val brake: Boolean,
    val lat: Double,
    val lon: Double,
    /** 진행 방향. 북쪽 0°, 시계 방향, 0~360. */
    val headingDeg: Double,
    /** 내비의 다음 안내. 경로 안내 중이 아니면 null. */
    val tbt: Tbt?,
    /** 전방 차량 신호. 정보가 없으면 null. R3에서만 쓴다. */
    val light: TrafficLight?,
)

/** 내비의 다음 안내와 남은 거리. */
@Serializable
data class Tbt(
    val turn: TurnType,
    val distanceM: Int,
)

/** 전방 차량 신호. */
@Serializable
data class TrafficLight(
    val phase: LightPhase,
    /** 남은 시간 (초). 모르면 null. */
    val remainSec: Int?,
)

@Serializable
enum class Gear { P, R, N, D }

@Serializable
enum class TurnSignal { OFF, LEFT, RIGHT, HAZARD }

@Serializable
enum class TurnType { LEFT, RIGHT, STRAIGHT, UTURN, OTHER }

@Serializable
enum class LightPhase { RED, YELLOW, GREEN, UNKNOWN }
