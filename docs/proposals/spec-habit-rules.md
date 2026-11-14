# spec 추가 제안: 경로 데모 운전 습관 장면을 실제 판정으로

2026-11-13 · 제안 단계 (AI 담당 확인 전) · spec v0.3.4 후보

경로 데모(세종대 → 포티투닷 판교)에 영상용 **각본 카드**(엔진 판정 없음)로 넣은 운전 습관 장면 8개를 Pleos SDK 차량 신호로 엔진이 실제로 판정할 수 있는지 조사하고, 가능한 장면의 룰 초안을 적는다. 코드는 아직 바꾸지 않았다. 아래 "미정 질문"에 답이 정해지면 `docs/spec.md`에 옮기고 구현한다.

---

## 0. 조사한 SDK

앱 Gradle 캐시의 aar 클래스를 `javap`로 직접 열어 확인했다 (공개 문서보다 이쪽이 정확하다).

| SDK | 버전 | 운전 습관 판정에 쓸 수 있는 것 |
| --- | --- | --- |
| Vehicle | 2.3.2 (vehicle-data 1.1.11) | `Brake.registerBrakePedalStatus` (밟음 여부 Boolean), `SteeringWheel` (핸들 각도), `TurnSignal`, `Gear`, `Safety.registerTelltales` (계기판 경고등) |
| CarPropertyManager (AAOS) | - | `PERF_VEHICLE_SPEED` (m/s). 지금 1 Hz로 받는다 |
| NaviHelper | 3.0.5 (navi-data 3.0.4) | `onTBTInfo` (다음 안내 종류·남은 거리), **`onCurrentRoadSpeedLimit(Int)` (지금 달리는 도로의 제한속도)**, `onCameraAlert` (단속 카메라: 종류·제한속도·남은 거리), `onCautionPointInfo` (주의 지점) |
| FusedLocation | 1.1.3 | 위도·경도·고도·진행 방향·정확도 |

**없는 것** (클래스 목록 전체에서 찾았다)

- 가속도 센서(종·횡 가속도), 요 레이트: 없다. 감속도는 속도 두 프레임의 차이로만 구한다.
- 차로 번호, 차로 변경, 차선 이탈 이벤트: 없다. `TBTInfo`는 `type`·`distance`·`description`뿐이다.
  계기판 경고등 종류(`TelltaleType`)에 `LKA`, `LFA`, `LCA_LEFT/RIGHT`, `ELK`가 있지만 **보조 시스템이 켜져 있다는 표시**라서 운전자가 차로를 옮긴 시점이나 지금 몇 차로인지는 알 수 없다.
- 도로 종류(고속도로·일반도로): 없다.
- 커브(곡률) 정보: 없다. `CautionPointType`에 급커브 항목이 없고, `TurnType`에도 커브가 없다 (`TURN_KEEP_L/R`은 갈림길).

---

## 1. 장면별 판정 가능 여부

| # | 장면 (데모 `CueKind`) | 필요한 신호 | SDK에 있나 | 판정 | 제안 |
| --- | --- | --- | --- | --- | --- |
| 1 | 급정거 (`SUDDEN_STOP`) | 감속도 | 속도(1 Hz) + 브레이크 페달 | **가능** (속도 차이로 감속도 추정) | R9 신설 (2장) |
| 2 | 깜빡이 타이밍 (청계산로 좌회전 `Driver.LATE`) | 깜빡이, 회전까지 거리 | 있음 | **이미 판정 중** (R1 MISSED) | 그대로 |
| 3 | 고속도로 진출 감속 (`EXIT_SLOW`) | 진출로 위치, 속도, 진출로 제한속도 | TBT + 속도 + **현재 도로 제한속도** | **가능** | R11 제한속도로 판정 (4장) |
| 4 | 브레이크 타이밍 (`BRAKE_EARLY`) | 회전까지 거리, 속도 | TBT + 속도 | **가능** | R10 신설 (3장) |
| 5 | 커브 속도 (`CURVE`) | 커브 위치·곡률, 속도 | 커브 정보 **없음**. 현재 도로 제한속도는 있음 | **부분 가능**: "커브"라는 걸 모르지만, 커브 구간의 제한속도가 낮으면 R11로 판정된다 | R11 (카드 문구는 "커브길" 대신 "제한속도 60") |
| 6 | 깜빡이 켜고 3초 뒤 차로 이동 (`LANE_WAIT`) | 차로를 옮긴 시점 | **없음** | 불가 | 각본 유지 |
| 7 | 1차로 정속 주행 (`LANE_KEEP`) | 지금 몇 차로인지 | **없음** | 불가 | 각본 유지 |
| 8 | 진출 직후 여러 차로 이동 (`PRE_LANE`) | 차로 번호·차로 변경 | **없음** | 불가 (안내 시점은 TBT로 알 수 있지만 따랐는지 모름) | 각본 유지 |

- 6번은 대리 지표로 "회전 안내가 없는 곳에서 깜빡이를 켠 뒤 끌 때까지 3초 이상"을 볼 수는 있다. 하지만 차로를 실제로 옮겼는지, 옮긴 뒤 껐는지 모르므로 판정이라 부르기 어렵다. 쓰지 않는 것을 권한다.
- 커브를 진행 방향 변화율 × 속도(횡가속도)로 사후 판정하는 방법도 봤다. 데모의 커브(자동차전용도로, 약 400 m에 걸쳐 40° 꺾임)는 80 km/h에서 횡가속도가 약 0.8 m/s²로 위험 수준이 아니다. 실제 차량도 위치가 1초에 한 번이라 거칠다. 그래서 제한속도 방식을 권한다.

---

## 2. R9 급정거 (초안)

spec 12장 "보조 지표: 급정거·급출발(설계 미정)"의 급정거 쪽을 룰로 만든다. 급출발은 이번에 하지 않는다.

**근거**: 법규 조항으로 처분되는 행위가 아니다 (단독 범칙금 없음, 12장). 안전거리 확보(도로교통법 제19조)와 이어지는 안전 습관이다. R6·R7처럼 **준수율과 아낀 범칙금에서는 뺀다** (미정 Q4).

**MVP 단순화**

- 연속한 두 프레임의 속도 차이 ÷ 시간 차이로 감속도를 구한다. 가속도 센서가 SDK에 없다.
- 급정거는 지난 일을 알려주는 것이라 **예방 안내가 없다.** 감지한 프레임에 GUIDE(사후 안내)와 EVENT(MISSED, guided = false)를 함께 낸다. KEPT는 내지 않는다.
- 한 번 센 뒤 `HARD_BRAKE_COOLDOWN_MS` 동안은 다시 세지 않는다 (한 번 멈추는 동안 여러 프레임이 기준을 넘어도 한 번).

### 2.1 파라미터

| 이름 | 값 | 뜻 |
| --- | --- | --- |
| HARD_BRAKE_MPS2 | 3.0 (초안) | 감속도가 이 이상이면 급정거 (m/s², 약 0.3 g). 근거 자료 확인 필요 (미정 Q2) |
| HARD_BRAKE_FROM_KPH | 20 | 직전 속도가 이 이상일 때만 센다. 정차 직전 저속에서 꾹 밟는 것은 빼기 위해서다 |
| HARD_BRAKE_COOLDOWN_MS | 10000 | 센 뒤 이만큼 다시 세지 않는다 |
| MAX_FRAME_GAP_MS | 3000 | 두 프레임 간격이 이보다 길면 감속도를 계산하지 않는다 (신호가 끊긴 뒤 속도가 튀는 것 방지) |

감속도: decel = (직전 speedKph − speedKph) ÷ 3.6 ÷ ((t − 직전 t) ÷ 1000)

### 2.2 상태

| 상태 | 뜻 | 기억하는 값 |
| --- | --- | --- |
| READY | 감시 중 | 없음 |
| COOLDOWN | 방금 셌음 | until |

모든 상태에서 직전 프레임(t, speedKph)을 프레임마다 기억한다 (전이표 검사 뒤 갱신).

### 2.3 전이표

| # | 현재 | 조건 | 다음 | 출력 |
| --- | --- | --- | --- | --- |
| 0 | 모든 상태 | gear가 P 또는 R | READY | 없음 |
| 1 | READY | 직전 프레임이 있고, t − 직전 t ≤ MAX_FRAME_GAP_MS 이고, 직전 speedKph ≥ HARD_BRAKE_FROM_KPH 이고, decel ≥ HARD_BRAKE_MPS2 | COOLDOWN (until = t + HARD_BRAKE_COOLDOWN_MS) | GUIDE + EVENT (MISSED, guided = false, turn = null) |
| 2 | COOLDOWN | t ≥ until | READY | 없음 |

- GUIDE 문구: `방금 급하게 멈췄어요, 앞차와 거리를 넉넉히 두세요`. action: `KEEP_DISTANCE` (따랐는지 확인하지 않는다).

### 2.4 시나리오

| 이름 | 입력 | 기대 출력 |
| --- | --- | --- |
| S33_급정거 | 40 km/h로 달리다 1초에 40 → 18 → 4 km/h (6.1 m/s², 3.9 m/s²) | GUIDE R9, EVENT R9 MISSED guided=false (한 번만) |
| S34_천천히_정지 | 40 km/h에서 1초에 5 km/h씩 줄여 정지 (1.4 m/s²) | 출력 없음 |
| S35_저속_급정지 | 15 km/h → 0 (4.2 m/s², 직전 속도 20 미만) | 출력 없음 |

---

## 3. R10 회전 앞 감속 (브레이크 타이밍, 초안)

**근거**: 도로교통법 제25조(교차로 통행방법)는 우회전·좌회전 때 "서행"을 요구한다. "서행"은 즉시 정지할 수 있는 느린 속도(제2조제28호)이고 숫자 기준이 없다. 그래서 R10의 속도 기준은 법 기준이 아니라 **안전 습관 기준**이다. 준수율과 아낀 범칙금에서는 뺀다 (미정 Q4).

**MVP 단순화**

- 내비 안내가 좌회전·우회전·유턴일 때만 본다 (R1과 같은 대상).
- 회전 `SLOW_GUIDE_M` 앞에서 `SLOW_GUIDE_KPH`보다 빠르면 "미리 줄이세요" 안내.
- 판정 지점(R1과 같은 30 m)을 지나는 첫 프레임의 속도가 `TURN_SLOW_KPH` 이하면 KEPT, 넘으면 MISSED.
- **천천히 다가간 회전은 기록하지 않는다.** 안내가 나갔거나(빠르게 다가감) 판정 지점에서 기준을 넘었을 때만 EVENT를 낸다. 모든 회전에 KEPT를 남기면 리포트가 R1과 같은 회전으로 두 번씩 채워지기 때문이다.

### 3.1 파라미터

| 이름 | 값 | 뜻 |
| --- | --- | --- |
| SLOW_GUIDE_M | 150 | 이 거리 안에서 감시·안내한다. R1 안내(50~100 m)보다 먼저 나오게 길게 둔다 |
| SLOW_GUIDE_KPH | 50 (초안) | 안내 거리 안에서 이보다 빠르면 안내한다 |
| CHECK_AT_M | 30 | R1과 같다. 판정 지점 |
| TURN_SLOW_KPH | 30 (초안) | 판정 지점 속도가 이 이하면 KEPT (미정 Q3) |
| TBT_JUMP_M | 20 | R1과 같다. 남은 거리가 이만큼 넘게 커지면 다음 회전으로 보고 취소 |

### 3.2 상태

| 상태 | 뜻 | 기억하는 값 |
| --- | --- | --- |
| IDLE | 대기 | 없음 |
| APPROACHING | 회전에 다가가는 중 | target, guided, lastDistanceM |
| DONE | 이 회전은 판정 끝 | target, lastDistanceM |

### 3.3 전이표

"회전 안내"는 tbt.turn이 LEFT, RIGHT, UTURN 중 하나인 것이다.

| # | 현재 | 조건 | 다음 | 출력 |
| --- | --- | --- | --- | --- |
| 0 | 모든 상태 | gear가 P 또는 R | IDLE | 없음 |
| 1 | IDLE | 회전 안내이고 CHECK_AT_M < tbt.distanceM ≤ SLOW_GUIDE_M | APPROACHING (target = tbt.turn, guided = false, lastDistanceM = tbt.distanceM) | 없음 |
| 2 | APPROACHING | tbt가 null이거나 tbt.turn ≠ target 이거나 tbt.distanceM > lastDistanceM + TBT_JUMP_M | IDLE | 없음 (재탐색·다음 회전) |
| 3 | APPROACHING | tbt.distanceM ≤ CHECK_AT_M 이고 (guided 이거나 speedKph > TURN_SLOW_KPH) | DONE | EVENT (speedKph ≤ TURN_SLOW_KPH면 KEPT 아니면 MISSED, guided 그대로, turn = target) |
| 4 | APPROACHING | tbt.distanceM ≤ CHECK_AT_M | DONE | 없음 (천천히 다가간 회전) |
| 5 | APPROACHING | speedKph > SLOW_GUIDE_KPH 이고 guided == false | APPROACHING (guided = true, lastDistanceM 갱신) | GUIDE |
| 6 | APPROACHING | 그 밖 | APPROACHING (lastDistanceM 갱신) | 없음 |
| 7 | DONE | tbt가 null이거나 tbt.turn ≠ target 이거나 tbt.distanceM > lastDistanceM + TBT_JUMP_M | IDLE | 없음 (다음 안내로 넘어감) |

- DONE에 머무는 프레임에서도 lastDistanceM을 갱신한다.
- GUIDE 문구: `회전이 가까워요, 지금부터 천천히 속도를 줄이세요`. action: `SLOW_DOWN` (speedKph ≤ TURN_SLOW_KPH면 따른 것으로 본다).

### 3.4 시나리오

| 이름 | 입력 | 기대 출력 |
| --- | --- | --- |
| S36_회전앞_미리감속 | 좌회전 150 m 앞을 60 km/h로, 안내 뒤 줄여 30 m 지점 25 km/h | GUIDE R10, (R1 출력), EVENT R10 KEPT guided=true |
| S37_회전앞_늦은감속 | 60 km/h로 다가가 30 m 지점 45 km/h | GUIDE R10, EVENT R10 MISSED guided=true |
| S38_회전앞_천천히 | 40 km/h로 다가가 30 m 지점 20 km/h | R10 출력 없음 |

---

## 4. R11 제한속도 (진출로·커브, 초안)

**근거**: 도로교통법 제17조제3항(최고속도 준수). 승용차 20 km/h 이하 초과는 범칙금 3만 원(시행령 별표 8)으로 알고 있으나 **원문 확인 필요**. 확인되면 법규표에 LAW-20으로 넣는다.

**새 입력**: `speedLimitKph: Int?` — NaviHelper `onCurrentRoadSpeedLimit(Int)`. 0 이하이거나 아직 안 들어왔으면 null. 기본값 null이라 기존 시나리오는 그대로 읽힌다.

**MVP 단순화**

- "진출로"나 "커브"라는 것은 모른다. 지금 도로의 제한속도보다 `OVER_MARGIN_KPH` 넘게 빠른 상태가 `OVER_HOLD_MS` 이어지면 안내한다. 진출로·커브 구간의 제한속도가 낮으면 이 장면들이 자연히 잡힌다.
- 안내 뒤 `SLOW_GRACE_MS` 안에 제한속도 이하로 줄이면 KEPT, 못 줄이면 MISSED.
- 한 번 판정한 뒤에는 제한속도 이하로 내려갈 때까지 다시 안내하지 않는다.

### 4.1 파라미터

| 이름 | 값 | 뜻 |
| --- | --- | --- |
| OVER_MARGIN_KPH | 10 (초안) | 제한속도보다 이만큼 넘게 빠르면 과속으로 본다. 속도계 오차를 감안한 여유 |
| OVER_HOLD_MS | 2000 | 과속이 이만큼 이어지면 안내한다. 제한속도가 바뀌는 경계에서 잠깐 튀는 값을 무시한다 |
| SLOW_GRACE_MS | 10000 | 안내 뒤 이 안에 제한속도 이하로 줄이면 KEPT |

### 4.2 상태

| 상태 | 뜻 | 기억하는 값 |
| --- | --- | --- |
| IDLE | 대기 | 없음 |
| OVER | 과속이 이어지는 중 | since |
| WATCHING | 안내했고 줄이길 기다리는 중 | guidedAt |
| DONE | 이번 과속은 판정 끝 | 없음 |

### 4.3 전이표

"과속"은 speedLimitKph ≠ null 이고 speedKph > speedLimitKph + OVER_MARGIN_KPH 인 프레임이다.

| # | 현재 | 조건 | 다음 | 출력 |
| --- | --- | --- | --- | --- |
| 0 | 모든 상태 | gear가 P 또는 R | IDLE | 없음 |
| 1 | IDLE | 과속 | OVER (since = t) | 없음 |
| 2 | OVER | 과속이 아님 | IDLE | 없음 |
| 3 | OVER | t − since ≥ OVER_HOLD_MS | WATCHING (guidedAt = t) | GUIDE |
| 4 | WATCHING | speedLimitKph == null 이거나 speedKph ≤ speedLimitKph | DONE | EVENT (KEPT, guided = true, turn = null) |
| 5 | WATCHING | t − guidedAt ≥ SLOW_GRACE_MS | DONE | EVENT (MISSED, guided = true, turn = null) |
| 6 | DONE | speedLimitKph == null 이거나 speedKph ≤ speedLimitKph | IDLE | 없음 |

- GUIDE 문구: `제한속도 {speedLimitKph}이에요, 속도를 줄이세요`. action: `SLOW_DOWN`.
- `SLOW_DOWN`을 따른 것으로 보는 프레임: R11은 speedKph ≤ 안내 때 제한속도, R10은 speedKph ≤ TURN_SLOW_KPH. 앱에는 안내 때 기준 속도를 알 수 있게 Guide에 `targetKph: Int?`를 더한다 (R10·R11만, 나머지 null).

### 4.4 시나리오

| 이름 | 입력 | 기대 출력 |
| --- | --- | --- |
| S39_진출로_안내후_감속 | 제한속도 80 → 40으로 바뀐 뒤 80 km/h 3초 유지, 안내 뒤 6초에 걸쳐 40으로 | GUIDE R11, EVENT R11 KEPT guided=true |
| S40_커브_감속안함 | 제한속도 60 구간을 80 km/h로 15초 | GUIDE R11, EVENT R11 MISSED guided=true |
| S41_제한속도_여유안 | 제한속도 50 구간을 58 km/h | 출력 없음 |

---

## 5. 앱과 경로 데모

- `SignalCollector`: `speedLimitKph`를 `naviListener.onCurrentRoadSpeedLimit`에서 채운다. 0 이하면 null.
- 경로 데모(`RouteDemo`): 구간별 제한속도를 프레임에 넣는다 (데모 값: 시내 50, 자동차전용도로 80, 커브 구간 60, 나들목 뒤 진출로 40). 급정거·브레이크 타이밍·진출로·커브 장면의 각본 카드(`ScriptCue`)를 지우고 엔진 GUIDE/EVENT로 카드와 음성을 낸다. 운전자는 안내를 듣고 줄이도록 속도 각본을 바꾼다 (브레이크 타이밍은 대왕판교로를 60으로 다가가 안내 뒤 줄임).
- 차로 장면 3개(`LANE_KEEP`, `LANE_WAIT`, `PRE_LANE`)는 각본 카드로 남긴다.
- 리포트: R9·R10·R11 EVENT가 경로 리포트와 정차 리포트에 나온다. 놓친 곳 수에 넣을지는 미정 Q4.

## 6. 미정 질문

- Q1 범위: 1장 표대로 4장면(급정거·브레이크 타이밍·진출로·커브)을 판정으로 바꾸고 차로 3장면은 각본으로 둘까?
- Q2 급정거 기준 `HARD_BRAKE_MPS2`: 3.0 m/s²로 둘까?
- Q3 회전 앞 감속 기준 `TURN_SLOW_KPH`: 30 m 지점 30 km/h 이하로 둘까?
- Q4 R9·R10을 리포트 준수율·놓친 곳 수에 넣을까, R6·R7처럼 기록만 할까? (R11은 법규 근거가 있어 준수율에 넣는다)
- Q5 (5장에 추가) 실제 차량에서 `onCurrentRoadSpeedLimit`가 언제 오는지, 에뮬레이터에서 오는지. 에뮬레이터 내비는 위치가 움직이지 않아 확인할 수 없다.
