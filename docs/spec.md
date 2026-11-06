# 깜빡코치 판정 엔진 명세

v0.1 · 2026-11-06 상태 머신 리뷰와 에뮬레이터 확인 반영 · 담당 박겸

이 문서가 기준이다. 코드와 다르면 이 문서를 먼저 고친다.

---

## 1. 엔진이 하는 일

차량 신호를 한 프레임씩 받아서 두 가지를 내보낸다.

- **안내 요청 (GUIDE)**: 지금 운전자에게 음성으로 말할 문구. 앱이 받아서 음성(TTS)으로 읽고 코치 카드에 표시한다.
- **판정 이벤트 (EVENT)**: 법규를 지켰는지(KEPT) 놓쳤는지(MISSED). 앱은 리포트 화면에 쓰고, 서버는 저장한다.

| 룰 | 내용 | 구현 시기 |
| --- | --- | --- |
| R1 | 회전 전 깜빡이 | 지금 (11/10까지) |
| R2 | 깜빡이 없이 회전 | 11/15 이후 |
| R3 | 적색 신호 우회전 일시정지 | 11/15 이후 |

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
  "light": null
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

enum 값

- gear: `P`, `R`, `N`, `D`
- turnSignal: `OFF`, `LEFT`, `RIGHT`, `HAZARD`
- tbt.turn: `LEFT`, `RIGHT`, `STRAIGHT`, `UTURN`, `OTHER`
- light.phase: `RED`, `YELLOW`, `GREEN`, `UNKNOWN`

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
| tbt.turn | 그 밖의 값 (`TURN_KEEP_*`, 나들목, 고가, 지하차도, 회전교차로 등) | `OTHER` |

TrafficLight: `{ "phase": "RED", "remainSec": 12 }` (remainSec는 Int?, 모르면 null)

### 2.2 출력: GUIDE (안내 요청)

```json
{ "type": "GUIDE", "t": 5000, "rule": "R1", "text": "좌회전 곧이에요, 깜빡이 켜세요" }
```

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
| rule | `R1`, `R2`, `R3` |
| result | `KEPT`(지킴), `MISSED`(놓침) |
| guided | 이 판정 전에 예방 안내(GUIDE)가 나갔는지. 앱의 "아낀 범칙금" 계산에 쓴다 |
| turn | `LEFT`, `RIGHT` |
| lat, lon | 이벤트가 나온 프레임의 위치 |

### 2.4 앱에서 쓰는 방법

```kotlin
val engine = Engine()

// 차량 신호가 들어올 때마다
val outputs: List<EngineOutput> = engine.onFrame(frame)
for (out in outputs) {
    when (out) {
        is Guide -> speak(out.text)          // 정현우: TTS + 코치 카드
        is Event -> saveAndShow(out)         // 정현우: 리포트, 김종범: 서버 저장
    }
}
```

- 엔진은 한 스레드에서만 호출한다고 가정한다.
- 한 프레임에서 출력이 0개, 1개, 여러 개 나올 수 있다.

---

## 3. 룰 R1: 회전 전 깜빡이

**법 근거**: 도로교통법 제38조. 좌·우회전할 때 일반도로는 회전 지점 30m 전부터(고속도로는 100m 전부터), 동작이 끝날 때까지 방향지시등을 켜야 한다. 승용차 기준 현장 적발 범칙금 3만 원, 공익신고 과태료 4만 원.

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
| APPROACHING | 회전 지점에 다가가는 중 | targetTurn(LEFT/RIGHT), guided |
| TURNING | 판정을 마치고 회전이 끝나길 기다리는 중 | targetTurn, guided, signalOk, startHeading, movingMs, lastT |

- signalOk: 30m 지점에서 해당 방향 깜빡이가 켜져 있었는가
- startHeading: 30m 판정을 한 프레임의 headingDeg. 회전 전 곡선 구간의 영향을 줄이기 위해 판정 시점 값을 쓴다
- movingMs: 판정 후 쌓인 주행 시간. 신호 대기 시간은 세지 않는다
- lastT: 직전 프레임의 t

TURNING에서는 전이표를 검사하기 전에 프레임마다 시간을 먼저 갱신한다. speedKph > 0이면 movingMs에 (t − lastT)를 더하고, 속도와 상관없이 lastT = t로 바꾼다.

### 3.3 전이표

한 프레임마다 현재 상태의 행을 **위에서부터** 검사하고, **처음 맞는 행 하나만** 적용한다. 맞는 행이 없으면 상태를 유지하고 출력도 없다.

| # | 현재 | 조건 | 다음 | 출력 |
| --- | --- | --- | --- | --- |
| 0 | 모든 상태 | gear가 P 또는 R | IDLE | 없음 (주차·후진으로 보고 취소) |
| 1 | IDLE | tbt.turn이 LEFT 또는 RIGHT이고 tbt.distanceM ≤ 100 | APPROACHING (targetTurn = tbt.turn, guided = false) | 없음 |
| 2 | APPROACHING | tbt가 null이거나 tbt.turn ≠ targetTurn | IDLE | 없음 (경로 재탐색으로 보고 취소) |
| 3 | APPROACHING | tbt.distanceM ≤ 30 | TURNING (signalOk = turnSignal가 targetTurn과 같은 방향, startHeading = headingDeg, movingMs = 0, lastT = t) | 없음 |
| 4 | APPROACHING | 30 < tbt.distanceM ≤ guideDistance(speedKph) 이고 turnSignal이 targetTurn과 다르고 guided == false | APPROACHING (guided = true) | GUIDE |
| 5 | TURNING | headingDiff(startHeading, headingDeg) ≥ 60 | IDLE | EVENT (result = signalOk면 KEPT 아니면 MISSED, guided 그대로) |
| 6 | TURNING | movingMs > 30000 | IDLE | 없음 (회전하지 않은 것으로 보고 취소) |

- "turnSignal이 targetTurn과 같은 방향": targetTurn이 LEFT면 turnSignal == LEFT, RIGHT면 turnSignal == RIGHT. HAZARD(비상등)는 같은 방향으로 치지 않는다.
- TURNING에서는 tbt를 보지 않는다. 회전 지점을 지나면 내비가 다음 안내로 바뀌기 때문이다.
- headingDiff(a, b) = | ((b − a + 540) mod 360) − 180 |. 예: 90 → 30 이면 60, 350 → 10 이면 20.
- GUIDE 문구: LEFT면 `좌회전 곧이에요, 깜빡이 켜세요`, RIGHT면 `우회전 곧이에요, 깜빡이 켜세요`.

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
- 표에 없는 필드의 공통값: `gear` = D, `brake` = false, `steeringDeg` = 0.0, `light` = null, `lat`/`lon` = 37.5500/127.0700 근처에서 조금씩 움직이는 값.
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

---

## 5. 아직 정하지 않은 것

공모전은 실차 시연을 지원하지 않고 앱 검증은 에뮬레이터로 한다 (2026-10-28 온라인 설명회). 아래 값은 실제 도로에서 잴 수 없으니 에뮬레이터에서 재현할 방법을 찾거나 값을 미정으로 둔 채 진행한다.

- [ ] 30m 단순화(30m 지점 한 번만 판정)를 팀이 받아들이는가
- [x] 핸들 각도가 SDK에서 숫자로 나오는가: 에뮬레이터에서 Float로 나온다
- [ ] 핸들 각도의 좌우 부호. 콘솔에 -90을 넣으면 SDK는 +90을 준다. 실제 왼쪽이 어느 쪽인지 확인 필요. R1은 진행 방향만 쓰므로 영향 없음. R2 설계 전에 정한다
- [ ] TBT 남은 거리가 몇 초마다 갱신되는가. 에뮬레이터 내비는 위치가 움직이지 않아 측정 불가. 재현 방법을 주최측에 문의한다. 너무 드물면 50m·30m 지점을 건너뛸 수 있음
- [ ] 회전 완료를 진행 방향 변화 대신 TBT 변화로 볼지
- [ ] 실제 headingDeg 기준(북쪽 0°, 시계 방향)이 Fused Location과 같은가. 에뮬레이터에서는 위치를 움직여도 0.0으로만 나와 측정 불가. 재현 방법을 주최측에 문의한다
- [ ] TBT 남은 거리의 기준점이 무엇인가. 에뮬레이터에서 측정 불가, 공개 문서에도 없다. 제출과 시연에 꼭 필요하지 않아 주최측 문의에서는 뺐다. 법령의 30m 기준점은 교차로 가장자리(시행령 별표 2)라서 기준점이 교차로 중심이면 판정이 늦어진다
- [ ] 비상등 입력원. Vehicle SDK에서 찾지 못했다. R1은 HAZARD와 OFF를 똑같이 처리하므로 영향 없음

---

## 6. 나중 룰 메모 (지금 구현하지 않음)

- **R2 깜빡이 없이 회전**: 저속(≤ 30km/h)에서 진행 방향이 60° 이상 바뀌었고, 바뀌기 시작하기 전 5초 동안 깜빡이가 꺼져 있었으면 MISSED. 후진(gear R)과 주차장 제외. 핸들 각도가 나오면 보조로 쓴다(예: 180° 이상). 내비 없이도 동작해야 한다.
- **R3 적색 신호 우회전 일시정지**: tbt가 RIGHT이고 light.phase가 RED인 상태로 다가가면 GUIDE `전방 적색이에요, 정지선에서 멈췄다 가세요`. 회전이 끝나기 전에 speedKph == 0인 프레임이 없으면 MISSED. 근거는 도로교통법 제5조, 범칙금 6만 원·벌점 15점. light는 서버(김종범)가 채워서 앱이 넣어준다.
