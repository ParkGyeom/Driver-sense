package kkam.engine

import kkam.engine.model.EngineOutput
import kkam.engine.model.SignalFrame
import kkam.engine.rules.R1Rule
import kkam.engine.rules.R4Rule
import kkam.engine.rules.R5Rule

/** 판정 엔진. 차량 신호를 한 프레임씩 받아 룰에 넘기고 출력을 모아 돌려준다. */
class Engine {
    private var eventCount = 0
    private val r1Rule = R1Rule(::nextEventId)
    private val r4Rule = R4Rule(::nextEventId)
    private val r5Rule = R5Rule(::nextEventId)

    /** spec 2.4: 여러 룰이 한 프레임에 출력하면 룰 번호 순서로 담는다. */
    fun onFrame(frame: SignalFrame): List<EngineOutput> =
        r1Rule.onFrame(frame) + r4Rule.onFrame(frame) + r5Rule.onFrame(frame)

    /** spec 2.3: 엔진 인스턴스 안에서 순서대로 evt-1, evt-2, ... */
    private fun nextEventId(): String {
        eventCount += 1
        return "evt-$eventCount"
    }
}
