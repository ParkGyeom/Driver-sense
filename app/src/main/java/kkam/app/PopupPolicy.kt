package kkam.app

import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide

/** 내비 위 팝업 카드 색. 주황 = 켜세요, 파랑 = 좋아요. */
enum class PopupTone { WARN, OK }

/** 내비 위에 띄울 작은 안내 카드. 값은 모두 [CoachState]에서 온다. */
data class Popup(
    val tone: PopupTone,
    /** 예: "좌회전" */
    val turnLabel: String,
    /** 예: "60 m" */
    val distanceLabel: String,
    /** 예: "지금 켜세요", "좋아요" */
    val headline: String,
)

/**
 * 프레임 하나를 반영한 다음 팝업. null이면 숨긴다.
 *
 * | 조건 | 결과 |
 * | --- | --- |
 * | 기어 P | 숨김 (정차 중에는 앱 화면이 리포트를 보여준다) |
 * | 판정(지킴·놓침)이 나옴 | 숨김. 위반 직후 잔소리하지 않는다 (기획서 5.4) |
 * | 회전 안내가 끝남 (카드 대기) | 숨김 |
 * | 숨겨져 있고 음성 안내(GUIDE)가 없음 | 계속 숨김. 안내 전에는 먼저 띄우지 않는다 |
 * | 음성 안내가 나왔거나 이미 떠 있음 | 띄움. 깜빡이를 켜면 파랑, 아니면 주황 |
 *
 * 파랑으로 바뀐 뒤 몇 초 지나 숨기는 것은 [CoachSession]이 시간을 재서 한다.
 */
fun nextPopup(current: Popup?, state: CoachState, outputs: List<EngineOutput>, gear: Gear): Popup? = when {
    gear == Gear.P -> null
    outputs.any { it is Event } -> null
    state.tone == Tone.IDLE -> null
    current == null && outputs.none { it is Guide } -> null
    else -> Popup(
        tone = if (state.tone == Tone.OK) PopupTone.OK else PopupTone.WARN,
        turnLabel = state.turnLabel,
        distanceLabel = state.distanceLabel,
        headline = state.badge,
    )
}
