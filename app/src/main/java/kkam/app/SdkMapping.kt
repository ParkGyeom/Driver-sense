package kkam.app

import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import kkam.engine.model.Gear
import kkam.engine.model.Tbt
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kotlin.math.roundToInt
import ai.pleos.playground.navi.constants.TurnType as NaviTurnType

// SDK 값을 엔진 값으로 바꾼다. 규칙의 기준은 docs/spec.md 2.1의 변환표다.

private const val MPS_TO_KPH = 3.6

/** PERF_VEHICLE_SPEED는 m/s로 온다. */
fun mpsToKph(mps: Float): Double = mps * MPS_TO_KPH

/** GEAR_UNKNOWN이면 null. 수집기는 직전 값을 유지한다. */
fun VehicleGear.toEngineGear(): Gear? = when (this) {
    VehicleGear.GEAR_PARK -> Gear.P
    VehicleGear.GEAR_REVERSE -> Gear.R
    VehicleGear.GEAR_NEUTRAL -> Gear.N
    VehicleGear.GEAR_UNKNOWN -> null
    else -> Gear.D // GEAR_DRIVE, GEAR_1 ~ GEAR_9
}

fun TurnSignalValue.toEngineTurnSignal(): TurnSignal = when (this) {
    TurnSignalValue.LEFT -> TurnSignal.LEFT
    TurnSignalValue.RIGHT -> TurnSignal.RIGHT
    else -> TurnSignal.OFF
}

/** 목록의 첫 항목이 다음 안내다. 경로가 없으면 목록이 비어 있다. */
fun List<TBTInfo>.toEngineTbt(): Tbt? = firstOrNull()?.let {
    Tbt(it.type.toEngineTurnType(), it.distance.roundToInt())
}

fun NaviTurnType.toEngineTurnType(): TurnType = when (this) {
    NaviTurnType.TURN_LEFT, NaviTurnType.TURN_SHARP_L -> TurnType.LEFT
    NaviTurnType.TURN_RIGHT, NaviTurnType.TURN_SHARP_R -> TurnType.RIGHT
    NaviTurnType.TURN_UTURN -> TurnType.UTURN
    NaviTurnType.TURN_STRAIGHT -> TurnType.STRAIGHT
    else -> TurnType.OTHER
}
