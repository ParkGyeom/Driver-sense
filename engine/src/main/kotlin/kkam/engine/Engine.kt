package kkam.engine

import kkam.engine.model.EngineOutput
import kkam.engine.model.SignalFrame
import kkam.engine.rules.R1Rule

/** 판정 엔진. 차량 신호를 한 프레임씩 받아 룰에 넘기고 출력을 모아 돌려준다. */
class Engine {
    private var eventCount = 0
    private val r1Rule = R1Rule(::nextEventId)

    fun onFrame(frame: SignalFrame): List<EngineOutput> = r1Rule.onFrame(frame)

    /** spec 2.3: 엔진 인스턴스 안에서 순서대로 evt-1, evt-2, ... */
    private fun nextEventId(): String {
        eventCount += 1
        return "evt-$eventCount"
    }
}
