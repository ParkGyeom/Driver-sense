package kkam.engine

import kkam.engine.model.EngineOutput
import kkam.engine.model.SignalFrame

/** 판정 엔진. 단계 5에서 R1을 연결하기 전까지는 아무것도 출력하지 않는다. */
class Engine {
    fun onFrame(frame: SignalFrame): List<EngineOutput> = emptyList()
}
