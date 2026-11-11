# 깜빡코치 판정 엔진 명세

v0.3 · 2026-11-11 새 코치 상황 추가 (제안서 `docs/proposals/spec-new-rules.md`, AI 담당 확인) · 담당 AI

| 버전 | 바뀐 것 |
| --- | --- |
| v0.1 (11-06) | R1 상태 머신, S1~S10 |
| v0.2 (11-09) | R4·R5 추가(시연용 최소 범위), 입력 `headlight`, tbt.turn에 `RAMP_LEFT`·`RAMP_RIGHT`·`TUNNEL`, GUIDE에 `action`, EVENT의 `turn`은 null 허용, S11·S12 |
| v0.3 (11-11) | R1이 유턴(`UTURN`)도 감시, EVENT의 `turn`에 `UTURN`, S22·S23. R1 연속 회전 보완(전이표 #1a·#2a, `TBT_JUMP_M`), S24~S26. R2 깜빡이 없이 회전(9장), S17~S21 |

이 문서가 기준이다. 코드와 다르면 이 문서를 먼저 고친다.

---

## 1. 엔진이 하는 일

차량 신호를 한 프레임씩 받아서 두 가지를 내보낸다.

- **안내 요청 (GUIDE)**: 지금 운전자에게 음성으로 말할 문구. 앱이 받아서 음성(TTS)으로 읽고 코치 카드에 표시한다.
- **판정 이벤트 (EVENT)**: 법규를 지켰는지(KEPT) 놓쳤는지(MISSED). 앱은 리포트 화면에 쓰고, 서버는 저장한다.

| 룰 | 내용 | 구현 시기 |
| --- | --- | --- |
| R1 | 회전·유턴 전 깜빡이 | 지금 (11/10까지). 유턴은 v0.3 |
| R2 | 깜빡이 없이 회전 (기록만) | v0.3 |
| R3 | 적색 신호 우회전 일시정지 | 11/15 이후 |
| R4 | 나들목 진출입 전 깜빡이 (안내 중심) | R3 다음 (일정은 tasks에서 정한다) |
| R5 | 터널 진입 전 전조등 (안내 중심) | R3 다음 (일정은 tasks에서 정한다) |

R4·R5는 PT 시연용 최소 범위다. 놓침(MISSED)은 내지 않고, 안내 후 운전자가 따르면 지킴(KEPT)만 기록한다. 이유는 각 룰의 "MVP 단순화"에 적는다.

원칙

- 판정은 규칙(상태 머신)으로만 한다. LLM은 엔진에 넣지 않는다. (설명과 코칭 문장은 앱에서 Gleo AI로 따로 만든다.)
- 엔진은 현재 시각, 랜덤에 의존하지 않는다. 프레임의 `t`만 시간으로 쓴다. 같은 프레임 순서를 넣으면 항상 같은 출력이 나온다.

---

## 2. 입출력 형식

### 2.1 입력: SignalFrame (차량 신호 한 프레임)

```json
{
  "t": 5000,
  "speedKph": 28.0,
  "gear": "D",
  "steeringDeg": -8.0,
  "turnSignal": "OFF",
  "brake": false,
  "lat": 37.5501,
  "lon": 127.0732,
  "headingDeg": 90.0,
  "tbt": { "turn": "LEFT", "distanceM": 48 },
  "light": null,
  "headlight": "OFF"
}
```

| 필드 | 타입 | 뜻 | 앱에서 가져오는 곳 | 없을 때 |
| --- | --- | --- | --- | --- |
| t | Long | 시각 (ms). 시나리오는 0부터 | 앱 | 항상 있음 |
| speedKph | Double | 속도 (km/h) | CarPropertyManager `PERF_VEHICLE_SPEED` (m/s) × 3.6 | 직전 값 유지 |
| gear | enum | 기어 | Vehicle SDK Gear | 직전 값 유지 |
| steeringDeg | Double? | 핸들 각도. 좌우 부호는 미정(5장) | Vehicle SDK SteeringWheel | SDK에서 안 나오면 null |
| turnSignal | enum | 방향지시등 상태 | Vehicle SDK TurnSignal. 앱은 당분간 HAZARD를 보내지 않는다(5장) | 직전 값 유지 |
| brake | Boolean | 브레이크 페달 밟음 | Vehicle SDK Brake | 직전 값 유지 |
| lat, lon | Double | 위치 | Fused Location | 직전 값 유지 |
| headingDeg | Double | 진행 방향 (북쪽 0°, 시계 방향, 0~360) | Fused Location | 직전 값 유지 |
| tbt | Tbt? | 내비의 다음 안내와 남은 거리 | NaviHelper getTBTInfo 목록의 첫 항목 | 목록이 비면 null |
| light | TrafficLight? | 전방 차량 신호 | 서버(공공 신호 API) → 앱 | 정보 없으면 null. R3에서만 사용 |
| headlight | enum? | 전조등(하향등) 상태 | Vehicle SDK Light (아래 변환표) | 직전 값 유지. 한 번도 값이 없으면 null. R5에서만 사용 |

- headlight는 Kotlin에서 기본값 `= null`을 준다. 이 필드가 없는 시나리오(S1~S10)도 그대로 읽히게 하기 위해서다.
- 앱은 headlight 값이 없다는 이유로 프레임을 막지 않는다. null이면 R5만 아무것도 하지 않는다.

enum 값

- gear: `P`, `R`, `N`, `D`
- turnSignal: `OFF`, `LEFT`, `RIGHT`, `HAZARD`
- tbt.turn: `LEFT`, `RIGHT`, `STRAIGHT`, `UTURN`, `RAMP_LEFT`, `RAMP_RIGHT`, `TUNNEL`, `OTHER`
- light.phase: `RED`, `YELLOW`, `GREEN`, `UNKNOWN`
- headlight: `OFF`, `ON`, `AUTO`

Tbt: `{ "turn": "LEFT", "distanceM": 48 }` (distanceM은 Int, 미터. SDK의 distance를 반올림)

앱이 SDK 값을 엔진 값으로 바꾸는 규칙

| 필드 | SDK 값 | 엔진 값 |
| --- | --- | --- |
| speedKph | `PERF_VEHICLE_SPEED` (m/s) | 3.6을 곱한 값 |
| gear | `GEAR_PARK`, `GEAR_REVERSE`, `GEAR_NEUTRAL`, `GEAR_DRIVE` | `P`, `R`, `N`, `D` |
| gear | `GEAR_1`~`GEAR_9` | `D` |
| gear | `GEAR_UNKNOWN` | 직전 값 유지 |
| turnSignal | `OFF`, `LEFT`, `RIGHT` | 같은 이름. 콘솔의 Blink와 Flash는 둘 다 `LEFT`/`RIGHT`로 나온다 |
| tbt.turn | `TURN_LEFT`, `TURN_SHARP_L` | `LEFT` |
| tbt.turn | `TURN_RIGHT`, `TURN_SHARP_R` | `RIGHT` |
| tbt.turn | `TURN_UTURN` | `UTURN` |
| tbt.turn | `TURN_STRAIGHT` | `STRAIGHT` |
| tbt.turn | `TURN_ENTER_IC_L`, `TURN_EXIT_IC_L` | `RAMP_LEFT` |
| tbt.turn | `TURN_ENTER_IC_R`, `TURN_EXIT_IC_R` | `RAMP_RIGHT` |
| tbt.turn | `TURN_TUNNEL` | `TUNNEL` |
| tbt.turn | 그 밖의 값 (`TURN_KEEP_*`, `TURN_ENTER_IC_C`, `TURN_EXIT_IC_C`, 고가, 지하차도, 회전교차로 등) | `OTHER` |
| headlight | 하향등 상태(`getCurrentLowBeamLightState`)가 `ON` | `ON` |
| headlight | 위가 아니고, 하향등 스위치(`getLowBeamLightSwitch`)가 `AUTOMATIC` | `AUTO` |
| headlight | 그 밖의 값 (상태 `OFF`, `DAYTIME_RUNNING`) | `OFF` |

- tbt는 NaviHelper 목록의 첫 항목만 쓴다. 터널 앞에 회전 안내가 먼저 있으면 그 회전이 끝날 때까지 터널은 보이지 않는다.
- R1은 `LEFT`, `RIGHT`만 보므로 새 값이 생겨도 R1 판정은 그대로다. 앱의 `TurnType`을 쓰는 `when`은 새 값을 처리해야 컴파일된다.
- `DAYTIME_RUNNING`(주간주행등)은 터널 안을 밝히는 등화로 보지 않고 `OFF`로 둔다. 실제 차량 동작은 확인하지 못했다(5장).
- Light API 권한은 `pleos.car.permission.CAR_LIGHTS`이며 앱 매니페스트에 이미 있다.

TrafficLight: `{ "phase": "RED", "remainSec": 12 }` (remainSec는 Int?, 모르면 null)

### 2.2 출력: GUIDE (안내 요청)

```json
{ "type": "GUIDE", "t": 5000, "rule": "R1", "text": "좌회전 곧이에요, 깜빡이 켜세요", "action": "SIGNAL_LEFT" }
```

| 필드 | 뜻 |
| --- | --- |
| action | 안내가 운전자에게 바라는 동작. 앱이 "따랐는지"를 프레임으로 확인할 때 쓴다 |

| action | 나오는 룰 | 따른 것으로 보는 프레임 |
| --- | --- | --- |
| `SIGNAL_LEFT` | R1, R4 | turnSignal == LEFT |
| `SIGNAL_RIGHT` | R1, R4 | turnSignal == RIGHT |
| `HEADLIGHT_ON` | R5 | headlight가 ON 또는 AUTO |

- action이 있으면 앱은 재탐색으로 tbt가 바뀌어도 안내한 방향을 잃지 않는다.
- 시나리오 비교 항목에는 넣지 않는다(4.1).

### 2.3 출력: EVENT (판정 이벤트)

```json
{
  "type": "EVENT",
  "id": "evt-1",
  "t": 11000,
  "rule": "R1",
  "result": "KEPT",
  "guided": false,
  "turn": "LEFT",
  "lat": 37.5503,
  "lon": 127.0712
}
```

| 필드 | 뜻 |
| --- | --- |
| id | 엔진 인스턴스 안에서 순서대로 `evt-1`, `evt-2` … (테스트 재현성 때문에 UUID를 쓰지 않는다) |
| t | 이벤트가 나온 프레임의 시각 |
| rule | `R1`, `R2`, `R3`, `R4`, `R5` |
| result | `KEPT`(지킴), `MISSED`(놓침). R4·R5는 `KEPT`만 낸다 |
| guided | 이 판정 전에 예방 안내(GUIDE)가 나갔는지. 앱의 "아낀 범칙금" 계산에 쓴다 |
| turn | `LEFT`, `RIGHT`, `UTURN`, 또는 null. R1은 `LEFT`·`RIGHT`·`UTURN`, R4는 방향, R5는 null |

- `turn`이 null일 수 있으므로 앱과 서버는 null을 처리해야 한다. R1의 유턴은 `UTURN`으로 온다(v0.3).
| lat, lon | 이벤트가 나온 프레임의 위치 |

### 2.4 앱에서 쓰는 방법

```kotlin
val engine = Engine()

// 차량 신호가 들어올 때마다
val outputs: List<EngineOutput> = engine.onFrame(frame)
for (out in outputs) {
    when (out) {
        is Guide -> speak(out.text)          // 앱 담당: TTS + 코치 카드
        is Event -> saveAndShow(out)         // 앱 담당: 리포트, 서버 담당: 서버 저장
    }
}
```

- 엔진은 한 스레드에서만 호출한다고 가정한다.
- 한 프레임에서 출력이 0개, 1개, 여러 개 나올 수 있다. 여러 룰이 한 프레임에 출력하면 룰 번호 순서(R1, R2, R3, R4, R5)로 담는다.
- R2는 안내 없이 EVENT만 낸다. guided는 항상 false다.
- R5는 안내 직후 운전자가 전조등을 켜면 그 프레임에 EVENT가 바로 나온다. 앱이 "EVENT가 오면 안내 표시를 숨김"으로 처리하면 따랐다는 표시가 보이지 않을 수 있다. 표시 유지 시간은 앱(앱 담당)이 정한다.

---

## 3. 룰 R1: 회전·유턴 전 깜빡이

**법 근거**: 도로교통법 제38조. 좌·우회전할 때 일반도로는 회전 지점 30m 전부터(고속도로는 100m 전부터), 동작이 끝날 때까지 방향지시등을 켜야 한다. 유턴도 같은 조항이며 왼쪽 방향지시등을 30m 전부터 켠다(시행령 별표 2 1호, 법규표 LAW-03). 승용차 기준 현장 적발 범칙금 3만 원, 공익신고 과태료 4만 원.

유턴을 새 룰로 두지 않고 R1에 넣은 이유: 판정 방식(30m 지점, 회전 완료)이 좌회전과 완전히 같다. 유턴만 따로 집계하려면 EVENT의 `turn == UTURN`으로 나눈다(v0.3).

**MVP 단순화**

- 30m 지점을 지나는 순간 해당 방향 깜빡이가 켜져 있으면 지킴(KEPT), 아니면 놓침(MISSED).
- 고속도로 100m 기준은 MVP에서 뺀다.

### 3.1 파라미터

| 이름 | 값 | 뜻 |
| --- | --- | --- |
| APPROACH_START_M | 100 | 다음 회전까지 이 거리 안이면 감시를 시작한다 |
| GUIDE_LEAD_MS | 4000 | 판정 지점에 도착하기 이만큼 전에 안내한다. 음성 재생과 반응 시간을 합한 초기값이며 TTS 실측 후 조정한다 |
| GUIDE_MIN_M | 50 | 안내 거리의 최솟값 |
| GUIDE_MAX_M | 100 | 안내 거리의 최댓값 |
| CHECK_AT_M | 30 | 판정 지점 |
| TURN_DONE_DEG | 60 | 진행 방향이 이만큼 바뀌면 회전이 끝난 것으로 본다 |
| TURN_TIMEOUT_MS | 30000 | 판정 후 주행 시간(speedKph > 0)이 이만큼 쌓여도 회전이 안 끝나면 기록 없이 취소한다 |
| TBT_JUMP_M | 20 | 다가가는 중 TBT 거리가 직전보다 이만큼 넘게 커지면 다음 회전으로 본다 (v0.3) |

안내 거리: guideDistance(speedKph) = CHECK_AT_M + speedKph ÷ 3.6 × GUIDE_LEAD_MS ÷ 1000 을 GUIDE_MIN_M 이상 GUIDE_MAX_M 이하로 자른 값.

| 속도 | 안내 거리 |
| --- | --- |
| 15km/h | 50m (최솟값) |
| 30km/h | 약 63m |
| 40km/h | 약 74m |
| 60km/h | 약 97m |
| 80km/h | 100m (최댓값) |

### 3.2 상태

| 상태 | 뜻 | 기억하는 값 |
| --- | --- | --- |
| IDLE | 대기 | 없음 |
| APPROACHING | 회전 지점에 다가가는 중 | targetTurn(LEFT/RIGHT/UTURN), guided, lastDistanceM |
| TURNING | 판정을 마치고 회전이 끝나길 기다리는 중 | targetTurn, guided, signalOk, startHeading, movingMs, lastT |

- signalOk: 30m 지점에서 해당 방향 깜빡이가 켜져 있었는가
- startHeading: 30m 판정을 한 프레임의 headingDeg. 회전 전 곡선 구간의 영향을 줄이기 위해 판정 시점 값을 쓴다
- movingMs: 판정 후 쌓인 주행 시간. 신호 대기 시간은 세지 않는다
- lastT: 직전 프레임의 t
- lastDistanceM: APPROACHING에서 직전 프레임의 tbt.distanceM. APPROACHING에 머무는 모든 프레임(#4와 맞는 행이 없을 때)에서 이번 값으로 바꾼다

TURNING에서는 전이표를 검사하기 전에 프레임마다 시간을 먼저 갱신한다. speedKph > 0이면 movingMs에 (t − lastT)를 더하고, 속도와 상관없이 lastT = t로 바꾼다.

### 3.3 전이표

한 프레임마다 현재 상태의 행을 **위에서부터** 검사하고, **처음 맞는 행 하나만** 적용한다. 맞는 행이 없으면 상태를 유지하고 출력도 없다.

| # | 현재 | 조건 | 다음 | 출력 |
| --- | --- | --- | --- | --- |
| 0 | 모든 상태 | gear가 P 또는 R | IDLE | 없음 (주차·후진으로 보고 취소) |
| 1a | IDLE | tbt.turn이 LEFT, RIGHT, UTURN 중 하나이고 tbt.distanceM ≤ 30 | TURNING (#3과 같은 값, guided = false) | 없음 (처음 본 회전이 이미 판정 지점 안. 연속 회전) |
| 1 | IDLE | tbt.turn이 LEFT, RIGHT, UTURN 중 하나이고 tbt.distanceM ≤ 100 | APPROACHING (targetTurn = tbt.turn, guided = false, lastDistanceM = tbt.distanceM) | 없음 |
| 2 | APPROACHING | tbt가 null이거나 tbt.turn ≠ targetTurn | IDLE | 없음 (경로 재탐색으로 보고 취소) |
| 2a | APPROACHING | tbt.distanceM > lastDistanceM + 20 | APPROACHING (targetTurn = tbt.turn, guided = false, lastDistanceM = tbt.distanceM) | 없음 (같은 방향 다음 회전. 앞 회전은 30m 프레임을 못 봐서 기록하지 않음) |
| 3 | APPROACHING | tbt.distanceM ≤ 30 | TURNING (signalOk = turnSignal가 targetTurn과 같은 방향, startHeading = headingDeg, movingMs = 0, lastT = t) | 없음 |
| 4 | APPROACHING | 30 < tbt.distanceM ≤ guideDistance(speedKph) 이고 turnSignal이 targetTurn과 다르고 guided == false | APPROACHING (guided = true) | GUIDE |
| 5 | TURNING | headingDiff(startHeading, headingDeg) ≥ 60 | IDLE | EVENT (result = signalOk면 KEPT 아니면 MISSED, guided 그대로) |
| 6 | TURNING | movingMs > 30000 | IDLE | 없음 (회전하지 않은 것으로 보고 취소) |

- "turnSignal이 targetTurn과 같은 방향": targetTurn이 LEFT 또는 UTURN이면 turnSignal == LEFT, RIGHT면 turnSignal == RIGHT. HAZARD(비상등)는 같은 방향으로 치지 않는다.
- TURNING에서는 tbt를 보지 않는다. 회전 지점을 지나면 내비가 다음 안내로 바뀌기 때문이다.
- headingDiff(a, b) = | ((b − a + 540) mod 360) − 180 |. 예: 90 → 30 이면 60, 350 → 10 이면 20.
- 유턴은 진행 방향이 180° 바뀌므로 #5의 60° 기준을 반드시 넘는다.
- #1a, #2a는 연속 회전 때문에 넣었다(v0.3). #1a가 없으면 앞 회전이 끝난 프레임에 다음 회전이 이미 30m 안일 때 다음 회전을 놓친다(S25). #2a가 없으면 같은 방향 회전이 이어질 때 앞 회전의 guided가 남아 다음 회전 안내가 나가지 않는다(S26).
- #1a는 안내 기회 없이 MISSED가 나올 수 있다. 법 기준(30m 전 점등)에는 맞는다.
- GUIDE 문구: LEFT면 `좌회전 곧이에요, 깜빡이 켜세요`, RIGHT면 `우회전 곧이에요, 깜빡이 켜세요`, UTURN이면 `유턴 곧이에요, 왼쪽 깜빡이 켜세요`.
- GUIDE action: LEFT·UTURN이면 `SIGNAL_LEFT`, RIGHT면 `SIGNAL_RIGHT`.

### 3.4 예시 흐름 (시나리오 S2)

1. t=2000: 좌회전 90m → APPROACHING (38km/h의 안내 거리는 약 72m라 아직 안내하지 않음)
2. t=4000: 60m, 32km/h의 안내 거리는 약 66m, 깜빡이 꺼짐 → GUIDE (guided = true)
3. t=6000: 운전자가 왼쪽 깜빡이를 켬
4. t=7000: 28m, 깜빡이 켜짐 → TURNING (signalOk = true, startHeading = 90)
5. t=11000: 진행 방향 90° → 30°, 60° 바뀜 → EVENT (KEPT, guided = true) → IDLE

---

## 4. 시나리오

### 4.1 파일 형식

```json
{
  "name": "S1_좌회전_처음부터_켬",
  "description": "왼쪽 깜빡이를 미리 켜고 좌회전. 안내 없이 지킴.",
  "expect": [
    { "type": "EVENT", "t": 11000, "rule": "R1", "result": "KEPT", "guided": false }
  ],
  "frames": [ { "t": 0, "...": "SignalFrame" } ]
}
```

- `expect`에는 엔진이 내보내야 하는 출력 **전체**를 순서대로 적는다. 개수가 다르면 실패다.
- 비교 항목: `type`, `t`, `rule`, 그리고 EVENT면 `result`, `guided`. `id`, `text`, 위치는 비교하지 않는다.
- 표에 없는 필드의 공통값: `gear` = D, `brake` = false, `steeringDeg` = 0.0, `light` = null, `headlight`는 적지 않음(null), `lat`/`lon` = 37.5500/127.0700 근처에서 조금씩 움직이는 값.
- `steeringDeg` = 0.0은 회전 중인 프레임에도 그대로 쓴다. R1은 핸들 각도를 쓰지 않고 좌우 부호도 미정이다. 실제 값은 R2 시나리오부터 넣는다.
- 표의 "다음 안내"는 `tbt = { "turn": "STRAIGHT", "distanceM": 500 }`, 그 다음 프레임은 `distanceM: 480`.

### 4.2 S1: 좌회전, 처음부터 깜빡이 켬

| t (ms) | tbt | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- | --- |
| 0 | LEFT 120m | 40 | OFF | 90 |
| 2000 | LEFT 90m | 38 | LEFT | 90 |
| 4000 | LEFT 60m | 32 | LEFT | 90 |
| 6000 | LEFT 40m | 26 | LEFT | 90 |
| 7000 | LEFT 28m | 22 | LEFT | 90 |
| 9000 | LEFT 10m | 15 | LEFT | 80 |
| 11000 | 다음 안내 | 15 | LEFT | 30 |
| 13000 | 다음 안내 (480m) | 25 | OFF | 0 |

기대 출력: EVENT t=11000, R1, KEPT, guided = false

### 4.3 S2: 좌회전, 안내 듣고 켬

| t (ms) | tbt | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- | --- |
| 0 | LEFT 120m | 40 | OFF | 90 |
| 2000 | LEFT 90m | 38 | OFF | 90 |
| 4000 | LEFT 60m | 32 | OFF | 90 |
| 5000 | LEFT 48m | 28 | OFF | 90 |
| 6000 | LEFT 40m | 26 | LEFT | 90 |
| 7000 | LEFT 28m | 22 | LEFT | 90 |
| 9000 | LEFT 10m | 15 | LEFT | 80 |
| 11000 | 다음 안내 | 15 | LEFT | 30 |
| 13000 | 다음 안내 (480m) | 25 | OFF | 0 |

기대 출력

1. GUIDE t=4000, R1
2. EVENT t=11000, R1, KEPT, guided = true

### 4.4 S3: 좌회전, 끝까지 안 켬

S2와 같고, turnSignal만 모든 프레임에서 OFF.

기대 출력

1. GUIDE t=4000, R1
2. EVENT t=11000, R1, MISSED, guided = true

### 4.5 추가 시나리오 (tasks 단계 6)

| 이름 | 내용 | 기대 출력 |
| --- | --- | --- |
| S4_우회전_처음부터_켬 | S1과 같되 tbt는 RIGHT, turnSignal은 RIGHT, headingDeg는 90 → 100 → 150 → 180 (우회전이라 커짐) | EVENT R1 KEPT guided=false (진행 방향이 60° 바뀌는 프레임에서) |
| S5_좌회전_반대쪽_깜빡이 | S2와 같되 깜빡이를 LEFT가 아니라 RIGHT로 켬 | GUIDE t=4000, EVENT R1 MISSED guided=true |
| S6_접근중_재탐색 | S1처럼 다가가다 t=4000(60m) 다음 프레임에서 tbt가 null이 되고, 이후 직진 | 출력 없음 |
| S7_신호대기_후_좌회전 | 4.6 표 | GUIDE t=4000, EVENT t=72000 R1 MISSED guided=true |
| S8_판정후_주차 | 4.7 표 | 출력 없음 |
| S9_재탐색후_우회전 | 4.8 표 | EVENT t=12000 R1 KEPT guided=false |
| S10_판정후_회전없이_주행 | 4.9 표 | 출력 없음 |
| S11_터널_전조등 | 4.10 표 | GUIDE t=11000 R5, EVENT t=14000 R5 KEPT guided=true |
| S12_나들목_안내후_켬 | 4.11 표 | GUIDE t=15000 R4, EVENT t=18000 R4 KEPT guided=true |
| S22_유턴_안내후_켬 | S2와 같되 tbt는 UTURN, headingDeg는 90(6프레임) → 80 → 30 → 270 | GUIDE t=4000 R1, EVENT t=11000 R1 KEPT guided=true |
| S23_유턴_반대쪽_깜빡이 | S22와 같되 t=6000~11000에 RIGHT를 켬 | GUIDE t=4000 R1, EVENT t=11000 R1 MISSED guided=true |
| S17_골목우회전_깜빡이켬 | 4.13 표 | EVENT t=8000 R2 KEPT guided=false |
| S18_골목좌회전_깜빡이없음 | 4.13 표 | EVENT t=8000 R2 MISSED guided=false |
| S19_완만한_곡선도로 | 4.13 표 | 출력 없음 |
| S20_내비안내중_좌회전_R1만 | S1과 같은 프레임 | EVENT t=11000 R1 KEPT guided=false (R2 출력 없음) |
| S21_주차장_출발후_회전 | 4.13 표 | 출력 없음 |
| S24_연속회전_좌후우_여유 | 4.12 표 | EVENT t=11000 R1 KEPT guided=false, GUIDE t=15000 R1, EVENT t=21000 R1 KEPT guided=true |
| S25_연속회전_좌후우_근접 | 4.12 표 | EVENT t=11000 R1 KEPT guided=false, EVENT t=15000 R1 KEPT guided=false |
| S26_같은방향_연속_30m놓침 | 4.12 표 | GUIDE t=4000 R1, GUIDE t=10000 R1, EVENT t=14000 R1 MISSED guided=true |

### 4.6 S7: 판정 후 신호 대기

30m에서 깜빡이가 꺼져 있었으므로, 대기 중에 켜도 MISSED다. 대기 시간은 타임아웃에 세지 않고 guided도 유지된다.

| t (ms) | tbt | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- | --- |
| 0 | LEFT 120m | 40 | OFF | 90 |
| 2000 | LEFT 90m | 38 | OFF | 90 |
| 4000 | LEFT 60m | 32 | OFF | 90 |
| 7000 | LEFT 28m | 22 | OFF | 90 |
| 8000 | LEFT 20m | 0 | OFF | 90 |
| 38000 | LEFT 20m | 0 | LEFT | 90 |
| 68000 | LEFT 20m | 0 | LEFT | 90 |
| 70000 | LEFT 10m | 15 | LEFT | 80 |
| 72000 | 다음 안내 | 15 | LEFT | 30 |

### 4.7 S8: 판정 후 주차

회전 지점 앞에서 주차하면 기록 없이 취소한다. 다시 출발해 방향이 달라져도 EVENT가 나오지 않아야 한다.

| t (ms) | tbt | speedKph | gear | turnSignal | headingDeg |
| --- | --- | --- | --- | --- | --- |
| 0 | LEFT 120m | 40 | D | OFF | 90 |
| 2000 | LEFT 90m | 38 | D | LEFT | 90 |
| 4000 | LEFT 60m | 32 | D | LEFT | 90 |
| 7000 | LEFT 28m | 22 | D | LEFT | 90 |
| 9000 | LEFT 25m | 0 | P | LEFT | 90 |
| 60000 | 다음 안내 | 10 | D | OFF | 30 |

### 4.8 S9: 재탐색 후 다른 방향 회전

전이표 #2를 검증한다. 좌회전 대상이 취소되어야 우회전을 새로 감시하고 오른쪽 깜빡이를 KEPT로 판정한다.

| t (ms) | tbt | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- | --- |
| 0 | LEFT 120m | 40 | OFF | 90 |
| 2000 | LEFT 90m | 38 | OFF | 90 |
| 4000 | RIGHT 80m | 35 | RIGHT | 90 |
| 6000 | RIGHT 60m | 32 | RIGHT | 90 |
| 8000 | RIGHT 28m | 22 | RIGHT | 90 |
| 10000 | RIGHT 10m | 15 | RIGHT | 100 |
| 12000 | 다음 안내 | 15 | RIGHT | 150 |

### 4.9 S10: 판정 후 회전 없이 계속 주행

전이표 #6을 검증한다. 주행 시간이 t=38000에 31000ms가 되어 취소된다. 그 뒤 방향이 바뀌어도 EVENT가 나오지 않아야 한다.

| t (ms) | tbt | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- | --- |
| 0 | LEFT 120m | 40 | OFF | 90 |
| 2000 | LEFT 90m | 38 | LEFT | 90 |
| 4000 | LEFT 60m | 32 | LEFT | 90 |
| 7000 | LEFT 28m | 22 | LEFT | 90 |
| 37000 | 다음 안내 | 40 | OFF | 90 |
| 38000 | 다음 안내 (480m) | 40 | OFF | 90 |
| 40000 | 다음 안내 (480m) | 30 | OFF | 20 |

### 4.10 S11: 터널 전조등 (R5)

70km/h로 직진하며 터널에 다가간다. 200m 안의 첫 프레임(187m)에서 안내하고, 운전자가 전조등을 켠 프레임에서 KEPT. turnSignal은 모두 OFF, headingDeg는 모두 90.

| t (ms) | tbt | speedKph | headlight |
| --- | --- | --- | --- |
| 0 | TUNNEL 400m | 70 | OFF |
| 5000 | TUNNEL 303m | 70 | OFF |
| 10000 | TUNNEL 206m | 70 | OFF |
| 11000 | TUNNEL 187m | 70 | OFF |
| 14000 | TUNNEL 129m | 70 | ON |
| 20000 | TUNNEL 12m | 70 | ON |
| 22000 | 다음 안내 | 70 | ON |

기대 출력

1. GUIDE t=11000, R5
2. EVENT t=14000, R5, KEPT, guided = true

### 4.11 S12: 나들목, 안내 듣고 깜빡이 (R4)

80km/h로 오른쪽 나들목에 다가간다. 안내 거리는 약 189m라 167m 프레임에서 안내하고, 100m 지점에서 오른쪽 깜빡이가 켜져 있어 KEPT. headingDeg는 모두 90.

| t (ms) | tbt | speedKph | turnSignal |
| --- | --- | --- | --- |
| 0 | RAMP_RIGHT 500m | 80 | OFF |
| 5000 | RAMP_RIGHT 389m | 80 | OFF |
| 9000 | RAMP_RIGHT 300m | 80 | OFF |
| 15000 | RAMP_RIGHT 167m | 80 | OFF |
| 17000 | RAMP_RIGHT 122m | 80 | RIGHT |
| 18000 | RAMP_RIGHT 100m | 80 | RIGHT |
| 20000 | 다음 안내 | 80 | RIGHT |

기대 출력

1. GUIDE t=15000, R4
2. EVENT t=18000, R4, KEPT, guided = true

### 4.12 S24~S26: 연속 회전 (R1)

S24·S25는 t=0~9000이 같다: LEFT 120·90·60·28·10m, 속도 40·38·32·22·15, turnSignal LEFT, headingDeg 90(4프레임) → 80.

S24 (다음 우회전까지 여유)

| t (ms) | tbt | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- | --- |
| 11000 | RIGHT 90m | 15 | LEFT | 30 |
| 13000 | RIGHT 70m | 25 | OFF | 0 |
| 15000 | RIGHT 50m | 25 | OFF | 0 |
| 16000 | RIGHT 40m | 22 | RIGHT | 0 |
| 17000 | RIGHT 28m | 20 | RIGHT | 0 |
| 19000 | RIGHT 10m | 15 | RIGHT | 20 |
| 21000 | 다음 안내 | 15 | RIGHT | 70 |

S25 (좌회전이 끝나는 프레임에 우회전이 이미 25m 앞. #1a 검증)

| t (ms) | tbt | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- | --- |
| 11000 | RIGHT 25m | 15 | RIGHT | 30 |
| 13000 | RIGHT 8m | 12 | RIGHT | 0 |
| 15000 | 다음 안내 | 15 | RIGHT | 70 |
| 17000 | 다음 안내 (480m) | 25 | OFF | 90 |

S26 (같은 방향 좌회전 두 번, 첫 회전의 30m 프레임 없음. #2a 검증)

| t (ms) | tbt | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- | --- |
| 0 | LEFT 120m | 40 | OFF | 90 |
| 2000 | LEFT 90m | 38 | OFF | 90 |
| 4000 | LEFT 60m | 32 | OFF | 90 |
| 6000 | LEFT 35m | 26 | OFF | 90 |
| 8000 | LEFT 90m | 25 | OFF | 30 |
| 10000 | LEFT 60m | 30 | OFF | 30 |
| 12000 | LEFT 28m | 22 | OFF | 30 |
| 14000 | 다음 안내 | 15 | OFF | 330 |

### 4.13 S17~S21: 깜빡이 없이 회전 (R2)

tbt는 모두 null(내비 없음).

S17 (오른쪽 깜빡이 켜고 골목 우회전)

| t (ms) | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- |
| 0 | 30 | OFF | 0 |
| 2000 | 25 | RIGHT | 0 |
| 4000 | 20 | RIGHT | 0 |
| 6000 | 15 | RIGHT | 30 |
| 8000 | 15 | RIGHT | 80 |
| 10000 | 25 | OFF | 90 |

S18 (깜빡이 없이 골목 좌회전): S17과 같은 시각·속도, turnSignal 모두 OFF, headingDeg 90 → 90 → 90 → 60 → 10 → 0.

S19 (완만한 곡선): t = 0~22000을 2000 간격, speedKph 25, turnSignal OFF, headingDeg 0에서 프레임마다 +8 (초당 4°, 총 88°).

S21 (주차에서 출발 직후 회전)

| t (ms) | gear | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- | --- |
| 0 | P | 0 | OFF | 0 |
| 2000 | D | 8 | OFF | 0 |
| 4000 | D | 10 | OFF | 0 |
| 6000 | D | 10 | OFF | 30 |
| 8000 | D | 10 | OFF | 90 |
| 10000 | D | 15 | OFF | 90 |

---

## 5. 아직 정하지 않은 것

공모전은 실차 시연을 지원하지 않고 앱 검증은 에뮬레이터로 한다 (2026-10-28 온라인 설명회). 아래 값은 실제 도로에서 잴 수 없으니 에뮬레이터에서 재현할 방법을 찾거나 값을 미정으로 둔 채 진행한다.

- [ ] 30m 단순화(30m 지점 한 번만 판정)를 팀이 받아들이는가
- [x] 핸들 각도가 SDK에서 숫자로 나오는가: 에뮬레이터에서 Float로 나온다
- [ ] 핸들 각도의 좌우 부호. 콘솔에 -90을 넣으면 SDK는 +90을 준다. 실제 왼쪽이 어느 쪽인지 확인 필요. R1·R2는 진행 방향만 쓰므로 영향 없음 (v0.3: R2는 핸들 각도를 쓰지 않기로 함)
- [ ] R2 파라미터(초당 8°, 깜빡이 5초, 10초 창, 주차 후 60초)는 초기값이다. 실제 headingDeg 품질과 앱 프레임 주기를 확인한 뒤 조정한다 (v0.3)
- [ ] R2: 엔진이 처음 켜졌을 때를 주차 직후로 볼지. 지금은 CRUISING에서 시작한다 (v0.3)
- [ ] TBT 남은 거리가 몇 초마다 갱신되는가. 에뮬레이터 내비는 위치가 움직이지 않아 측정 불가. 재현 방법을 주최측에 문의한다. 너무 드물면 50m·30m 지점을 건너뛸 수 있음
- [ ] 회전 완료를 진행 방향 변화 대신 TBT 변화로 볼지. 비스듬한 교차로(진행 방향이 60°까지 안 바뀜) 직후 다음 회전이 오면 R1이 TURNING에 묶여 두 회전 모두 기록하지 않는다(v0.3 제안서 S27, 시나리오는 이 질문이 정해진 뒤 만든다)
- [ ] 실제 headingDeg 기준(북쪽 0°, 시계 방향)이 Fused Location과 같은가. 에뮬레이터에서는 위치를 움직여도 0.0으로만 나와 측정 불가. 재현 방법을 주최측에 문의한다
- [ ] TBT 남은 거리의 기준점이 무엇인가. 에뮬레이터에서 측정 불가, 공개 문서에도 없다. 제출과 시연에 꼭 필요하지 않아 주최측 문의에서는 뺐다. 법령의 30m 기준점은 교차로 가장자리(시행령 별표 2)라서 기준점이 교차로 중심이면 판정이 늦어진다
- [ ] 비상등 입력원. Vehicle SDK에서 찾지 못했다. R1은 HAZARD와 OFF를 똑같이 처리하므로 영향 없음
- [x] 에뮬레이터에서 하향등 상태·스위치 값이 실제로 나오는가 (11/11 확인). Virtual Console의 Exterior Lights Mode를 Low beam으로 두면 앱에 `ON`, Off·Tail lamp(미등)는 `OFF`로 들어온다
- [ ] 전조등 자동(`AUTO`)이 들어오는가. 에뮬레이터에서는 확인할 수 없다. 에뮬레이터의 `HEADLIGHTS_SWITCH`는 OFF·ON만 지원하고, 콘솔의 Auto는 제조사 속성 `EXTERIOR_LIGHTS_MODE`(0x21400754)만 바꾼다. 이 속성은 Pleos SDK에 읽는 함수가 없고, 직접 읽으려면 일반 앱이 받을 수 없는 `CAR_VENDOR_EXTENSION` 권한이 필요하다. 그래서 자동으로 둔 운전자도 터널 200m 앞에서 아직 꺼져 있으면 R5 안내를 한 번 듣고, 터널에서 켜지면 KEPT로 기록된다. 실제 차량의 스위치가 `AUTOMATIC`을 지원하면 지금 변환표대로 `AUTO`가 된다
- [ ] `DAYTIME_RUNNING`을 OFF로 보는 것이 맞는가. 주간주행등이 터널에서 어떻게 동작하는지 확인 필요 (v0.2)
- [ ] R4 판정 지점을 100m 하나로 두는 것. 일반도로 나들목은 30m 기준이라 더 엄격하게 본다 (v0.2)
- [ ] R4·R5에서 MISSED를 낼 조건. 차로 번호, 터널 통과 여부를 알 수 있는 입력이 생기면 다시 본다 (v0.2)

---

## 6. 나중 룰 메모 (지금 구현하지 않음)

- R2는 v0.3에서 9장으로 옮겼다.
- **R3 적색 신호 우회전 일시정지**: tbt가 RIGHT이고 light.phase가 RED인 상태로 다가가면 GUIDE `전방 적색이에요, 정지선에서 멈췄다 가세요`. 회전이 끝나기 전에 speedKph == 0인 프레임이 없으면 MISSED. 근거는 도로교통법 제5조, 범칙금 6만 원·벌점 15점. light는 서버(서버 담당)가 채워서 앱이 넣어준다.
- **v0.2에서 뺀 것** (시연용 최소 범위 밖)
  - R6 비 올 때 와이퍼: 입력 `wiper`, `rain`이 필요하다. 와이퍼 권한 `pleos.car.permission.CAR_WINDOWS`가 앱에 없고, 비 정보 출처(서버 날씨)도 정해지지 않았다. 법규표에 근거 조항이 없다.
  - R4의 `TURN_KEEP_L`/`TURN_KEEP_R`(갈림길 유지): 차로를 바꿀 필요가 없는 경우가 많아 안내가 잔소리가 될 수 있다.
  - R5의 야간·비 조건: 엔진은 현재 시각을 쓰지 않으므로 별도 입력이 필요하다.
  - 모드(DIAGNOSE/CORRECT/TRAINING/QUIET)에서 R4·R5를 어떻게 다룰지: tasks 단계 11에서 함께 정한다.
  - `data/law_table.json`에 룰 연결 필드(`rule`)와 터널 등화 조항(LAW-16) 추가: 법규 Q&A와 리포트 연결 작업 때 한다.

---

## 7. 룰 R4: 나들목 진출입 전 깜빡이

**법 근거**: 도로교통법 제38조제1항, 시행령 별표 2 (법규표 LAW-04). 차로를 바꿀 때 일반도로는 30m, 고속도로는 100m 이상 앞선 지점부터 방향지시등을 켜야 한다.

**MVP 단순화**

- 내비 안내가 나들목 진입·진출(`RAMP_LEFT`, `RAMP_RIGHT`)일 때만 본다.
- 판정 지점은 고속도로 기준 100m 하나로 둔다. 엔진은 도로 종류를 모른다.
- 100m 지점에서 해당 방향 깜빡이가 켜져 있으면 KEPT. **꺼져 있어도 MISSED를 내지 않는다.** 차로 번호를 모르므로 이미 바깥 차로에 있어서 차로를 바꿀 필요가 없었는지 알 수 없기 때문이다.
- 회전 완료(진행 방향 변화)는 보지 않는다. 나들목은 진행 방향이 60°까지 바뀌지 않는 경우가 많다.

### 7.1 파라미터

| 이름 | 값 | 뜻 |
| --- | --- | --- |
| LANE_START_M | 300 | 나들목까지 이 거리 안이면 감시를 시작한다 |
| LANE_CHECK_M | 100 | 판정 지점 |
| LANE_GUIDE_MAX_M | 300 | 안내 거리의 최댓값 |
| GUIDE_LEAD_MS | 4000 | R1과 같은 값을 쓴다 |

안내 거리: laneGuideDistance(speedKph) = LANE_CHECK_M + speedKph ÷ 3.6 × GUIDE_LEAD_MS ÷ 1000 을 LANE_GUIDE_MAX_M 이하로 자른 값.

| 속도 | 안내 거리 |
| --- | --- |
| 60km/h | 약 167m |
| 80km/h | 약 189m |
| 100km/h | 약 211m |

### 7.2 상태

| 상태 | 뜻 | 기억하는 값 |
| --- | --- | --- |
| IDLE | 대기 | 없음 |
| APPROACHING | 나들목에 다가가는 중 | targetTurn(RAMP_LEFT/RAMP_RIGHT), guided |

### 7.3 전이표

R1과 같이 위에서부터 검사하고 처음 맞는 행 하나만 적용한다.

| # | 현재 | 조건 | 다음 | 출력 |
| --- | --- | --- | --- | --- |
| 0 | 모든 상태 | gear가 P 또는 R | IDLE | 없음 |
| 1 | IDLE | tbt.turn이 RAMP_LEFT 또는 RAMP_RIGHT이고 tbt.distanceM ≤ 300 | APPROACHING (targetTurn = tbt.turn, guided = false) | 없음 |
| 2 | APPROACHING | tbt가 null이거나 tbt.turn ≠ targetTurn | IDLE | 없음 (재탐색으로 보고 취소) |
| 3 | APPROACHING | tbt.distanceM ≤ 100 이고 turnSignal이 targetTurn과 같은 방향 | IDLE | EVENT (KEPT, guided 그대로, turn = LEFT 또는 RIGHT) |
| 4 | APPROACHING | tbt.distanceM ≤ 100 | IDLE | 없음 (MVP 단순화) |
| 5 | APPROACHING | 100 < tbt.distanceM ≤ laneGuideDistance(speedKph) 이고 turnSignal이 targetTurn과 다르고 guided == false | APPROACHING (guided = true) | GUIDE |

- "같은 방향": RAMP_LEFT면 turnSignal == LEFT, RAMP_RIGHT면 turnSignal == RIGHT. HAZARD는 같은 방향으로 치지 않는다.
- EVENT의 turn: RAMP_LEFT면 `LEFT`, RAMP_RIGHT면 `RIGHT`.
- GUIDE 문구: RAMP_LEFT면 `왼쪽 나들목이 곧이에요, 깜빡이 켜세요`, RAMP_RIGHT면 `오른쪽 나들목이 곧이에요, 깜빡이 켜세요`.
- GUIDE action: RAMP_LEFT면 `SIGNAL_LEFT`, RAMP_RIGHT면 `SIGNAL_RIGHT`.

---

## 8. 룰 R5: 터널 진입 전 전조등

**법 근거**: 도로교통법 제37조제1항제3호. 터널 안을 운행할 때는 전조등 등 등화를 켜야 한다. 범칙금은 아직 확인하지 못했다(법규표에 행이 없다).

**MVP 단순화**

- 내비 안내가 터널(`TUNNEL`)일 때만 본다. 야간·비 조건은 뺀다.
- 안내를 들은 뒤 전조등을 켜면 KEPT. **끝까지 안 켜도 MISSED를 내지 않는다.** 터널 안내가 사라지는 것이 터널 통과인지 경로 재탐색인지 구분할 수 없기 때문이다.
- 다가갈 때 이미 전조등이 ON 또는 AUTO이면 안내도 기록도 하지 않는다.
- headlight가 null이면 아무것도 하지 않는다.

### 8.1 파라미터

| 이름 | 값 | 뜻 |
| --- | --- | --- |
| TUNNEL_GUIDE_M | 200 | 터널까지 이 거리 안이면 안내한다 |

### 8.2 상태

| 상태 | 뜻 | 기억하는 값 |
| --- | --- | --- |
| IDLE | 대기 | 없음 |
| WATCHING | 안내를 했고 전조등이 켜지길 기다리는 중 | 없음 |
| DONE | 이번 터널은 끝남. 같은 터널로 다시 안내하지 않으려고 기다리는 중 | 없음 |

### 8.3 전이표

| # | 현재 | 조건 | 다음 | 출력 |
| --- | --- | --- | --- | --- |
| 0 | 모든 상태 | gear가 P 또는 R | IDLE | 없음 |
| 1 | IDLE | tbt.turn이 TUNNEL이고 tbt.distanceM ≤ 200 이고 headlight == OFF | WATCHING | GUIDE |
| 2 | IDLE | tbt.turn이 TUNNEL이고 tbt.distanceM ≤ 200 이고 headlight가 ON 또는 AUTO | DONE | 없음 (이미 켜져 있음) |
| 3 | WATCHING | headlight가 ON 또는 AUTO | DONE | EVENT (KEPT, guided = true, turn = null) |
| 4 | WATCHING | tbt가 null이거나 tbt.turn ≠ TUNNEL | IDLE | 없음 (MVP 단순화) |
| 5 | DONE | tbt가 null이거나 tbt.turn ≠ TUNNEL | IDLE | 없음 |

- IDLE에서 headlight가 null이면 #1, #2 둘 다 맞지 않아 IDLE에 머문다.
- GUIDE 문구: `터널이 곧 나와요, 전조등 켜세요`.
- GUIDE action: `HEADLIGHT_ON`.

---

## 9. 룰 R2: 깜빡이 없이 회전

**법 근거**: 도로교통법 제38조제1항 (법규표 LAW-03). 30m 기준은 LAW-01, 시행령 별표 2.

**MVP 단순화**

- tbt를 쓰지 않는다. 진행 방향 변화로 회전을 잡는다. 내비 안내가 없는 골목, 주차장 출구, 경로 밖 회전을 R1 대신 기록한다.
- 저속(5~30km/h)에서 진행 방향이 초당 8° 이상 바뀌기 시작하면 회전 시작, 시작 전 방향에서 60° 이상 바뀌면 회전 완료.
- 회전 시작 전 5초 안에 그 방향 깜빡이가 한 프레임이라도 켜져 있었으면 KEPT, 아니면 MISSED. 30m를 거리로 잴 수 없어 시간으로 대신한다(20km/h에서 5초 ≈ 28m).
- 안내(GUIDE)는 하지 않는다. 회전을 미리 알 수 없기 때문이다.
- R1이 회전을 맡고 있으면 쉰다. 같은 회전을 두 번 기록하지 않기 위해서다.
- steeringDeg는 쓰지 않는다. 좌우 부호가 미정이고 null일 수 있다(5장).

### 9.1 R1과 겹치는 회전

Engine이 R1의 상태를 R2에 넘긴다(`otherRuleBusy`). R1이 이 프레임을 처리하기 **전이나 후에** IDLE이 아니면 true다.

- "전"을 보는 이유: R1은 60°에서 EVENT를 내고 IDLE이 되는데, 그 프레임에 R2가 같은 회전을 잡으면 안 된다.
- R1이 끝난 뒤에도 주행 10초(R2_HANDOFF_MS) 동안 더 쉰다. 실제 회전은 90°(유턴은 180°)까지 이어지므로 남은 회전을 새 회전으로 잡지 않기 위해서다.
- 검토한 다른 방법: R2 EVENT를 Engine이 버리는 방법(판정 논리가 Engine에 섞임), 내비를 안 쓸 때만 R2를 켜는 방법(경로 밖 골목 회전을 놓침). v0.3 제안서 2.2.

### 9.2 파라미터

| 이름 | 값 | 뜻 |
| --- | --- | --- |
| R2_MIN_SPEED_KPH | 5 | 이보다 느리면 회전 시작으로 보지 않는다. 정지 중 GPS 진행 방향이 튀는 것을 거른다 |
| R2_MAX_SPEED_KPH | 30 | 이보다 빠르면 회전 시작으로 보지 않는다. 굽은 도로를 거른다 |
| R2_TURN_RATE_DPS | 8 | 직전 프레임 대비 진행 방향이 초당 이만큼 이상 바뀌면 회전 시작 |
| TURN_DONE_DEG | 60 | R1과 같음 |
| R2_TURN_WINDOW_MS | 10000 | 회전 시작 후 이 시간 안에 60°가 안 되면 굽은 도로로 보고 취소 |
| SIGNAL_LOOKBACK_MS | 5000 | 회전 시작 전 이 시간 안에 깜빡이가 켜졌으면 KEPT |
| R2_AFTER_PARK_MS | 60000 | 주차(P)·후진(R) 뒤 주행 시간이 이만큼 쌓이기 전에는 판정하지 않는다 |
| R2_HANDOFF_MS | 10000 | R1이 회전을 맡았다가 끝난 뒤 이만큼 주행할 때까지 쉰다 |

- turnRate = headingDiff(prevHeading, headingDeg) ÷ ((t − prevT) ÷ 1000). 프레임 간격이 달라도 같은 기준이 되도록 초당 각도로 본다. 첫 프레임은 0.
- signedHeadingDiff(a, b) = ((b − a + 540) mod 360) − 180. 양수면 RIGHT(시계 방향), 음수면 LEFT.

### 9.3 상태

| 상태 | 뜻 | 기억하는 값 |
| --- | --- | --- |
| CRUISING | 회전을 기다리며 달리는 중 (처음 상태) | 없음 |
| TURNING | 회전이 시작됐고 60°가 되길 기다리는 중 | startHeading, startT, direction(LEFT/RIGHT), signalOk |
| COOLDOWN | 판정하지 않고 쉬는 중 (주차·후진 직후, R1이 회전을 맡음) | remainMs |

### 9.4 프레임마다 먼저 갱신하는 값

상태와 상관없이 전이표를 보기 **전에** 갱신한다(R1의 movingMs와 같은 방식).

- turnRate를 직전 값으로 계산한 뒤 prevHeading = headingDeg, prevT = t.
- turnSignal이 LEFT면 lastLeftT = t, RIGHT면 lastRightT = t. OFF, HAZARD는 깜빡이로 치지 않는다.
- COOLDOWN이고 speedKph > 0이면 remainMs에서 (t − 직전 t)를 뺀다. 서 있는 시간은 세지 않는다.

### 9.5 전이표

| # | 현재 | 조건 | 다음 | 출력 |
| --- | --- | --- | --- | --- |
| 0 | 모든 상태 | gear가 P 또는 R | COOLDOWN (remainMs = 60000) | 없음 |
| 1 | 모든 상태 | otherRuleBusy | COOLDOWN (remainMs = max(지금 remainMs, 10000)) | 없음 (R1이 맡음) |
| 2 | COOLDOWN | remainMs ≤ 0 | CRUISING | 없음 |
| 3 | CRUISING | 5 ≤ speedKph ≤ 30 이고 turnRate ≥ 8 | TURNING (startHeading = prevHeading, startT = t, direction = signedHeadingDiff 부호, signalOk = 그 방향 lastT ≥ t − 5000) | 없음 |
| 4 | TURNING | headingDiff(startHeading, headingDeg) ≥ 60 | CRUISING | EVENT (signalOk면 KEPT 아니면 MISSED, guided = false, turn = direction) |
| 5 | TURNING | t − startT > 10000 | CRUISING | 없음 (굽은 도로로 보고 취소) |

- #1의 "지금 remainMs"는 COOLDOWN이 아니면 0이다.
- #3의 startHeading은 직전 프레임 값이다. 회전이 이미 시작된 프레임에서 잡히므로 그 전 방향이 기준이다.
- 반대 방향 깜빡이를 켜고 회전하면 MISSED다(R1과 같다).
- 내비 없는 유턴은 왼쪽 회전으로 기록된다. 유턴 깜빡이도 왼쪽이 맞다.

### 9.6 오판 위험

| 위험 | 결과 | 줄이는 방법 |
| --- | --- | --- |
| 저속 급커브(산길 헤어핀) | 회전으로 보고 MISSED | 초당 8°와 10초 창으로 일부 거른다. 남는 위험은 받아들인다 |
| 정지·저속에서 GPS 진행 방향 튐 | 가짜 회전 | R2_MIN_SPEED_KPH. 실제 품질은 에뮬레이터에서 측정 불가(5장) |
| 주차 없이 들어간 주차장·드라이브스루 | MISSED 남발 | 거르지 못한다 |
| R1이 다가가다 다른 골목으로 꺾음 | R1·R2 모두 기록 안 함 | 9.1 방법의 한계. 받아들인다 |
| 루프형 나들목 램프 | 회전으로 기록 | 램프는 대개 30km/h보다 빨라 거른다 |
| 프레임 간격이 매우 김 (예: 5초) | 회전 시작을 놓침 | 앱 프레임 주기 확인 필요 |

