# 차량 앱

담당: 정현우 · 상태: 코치 카드, 기어 P 주행 리포트, 진단 리포트·습관 선택

Pleos Connect 차량 앱입니다. 공모전 개발 환경은 Android API 28 이상, Gradle 8.0 이상입니다.

## 지금 되는 것

- 시나리오 버튼을 누르면 녹화된 신호를 시간대로 재생해 엔진에 넣습니다.
- 코치 카드, 차량 신호, 오늘 지킨 횟수, 판정 기록을 화면에 보여줍니다.
- GUIDE가 나오면 Pleos TTS로 읽습니다.

- "실제 차량 신호" 버튼을 누르면 SDK 신호를 1초마다 `SignalFrame`으로 묶어 엔진에 넣습니다.

- 기어가 P가 되면 오늘 주행 리포트(지킴·놓침, 조항·처분, 아낀 범칙금)를 보여주고, 다른 기어가 되면 코치 카드로 돌아갑니다. 데모에서는 S2 다음에 S8(9초에 P)을 틀면 됩니다.

- 기어 P에서 진단 리포트(상황별 준수율, 전국 평균선 75.7%)와 습관 선택(최대 2개)을 엽니다. 데모 패널의 "진단 리포트 열기 (기어 P)"는 재생을 멈추고 기어를 P로 세운 뒤 진단 리포트를 엽니다. 진단 값은 데모 고정값(`DemoDiagnosisSource`)이고, 고른 습관은 아직 엔진에 넘기지 않습니다 (엔진 모드 입력 spec v0.2 대기).

서버 저장은 아직 없습니다. 오늘 지킨 횟수와 리포트는 앱을 끄면 사라집니다. 리포트의 지도 핀은 지도 SDK를 확인한 뒤 붙입니다.

에뮬레이터는 내비 위치와 진행 방향이 움직이지 않습니다. 그래서 실제 신호 모드에서 30m 판정과 회전 완료는 확인할 수 없습니다. 속도를 약 45km/h 이상으로 올리면 80m 안내에서 GUIDE는 확인할 수 있습니다.

## 실행

Android SDK가 필요합니다. `ANDROID_HOME`을 지정하거나 루트에 `local.properties`를 만들어 `sdk.dir`을 적습니다. 이 설정이 없으면 `./gradlew test`도 실패합니다.

```bash
./gradlew :app:installDebug
```

## 내비가 앞에 있을 때

주행 중에는 내비가 앱 칸 전체에 뜨고, 우리 앱은 뒤에서 돈다. 재생, 신호 수집, 엔진, 음성은 `CoachService`(포그라운드 서비스, 알림 채널 "주행 코치")가 갖는다. 화면을 닫아도 주행 중이면 서비스는 계속 돌고, 주행이 끝나면 스스로 끝난다. 데모 패널의 "재생하면 내비 띄우기"를 켜면 시나리오를 틀 때 내비가 앞으로 온다.

내비가 앞에 있으면 안내가 필요할 때만 내비 위쪽 가운데에 작은 팝업 카드가 뜬다 (`CoachPopup`, 규칙은 `PopupPolicy.kt`).

- 음성 안내(GUIDE)가 나오면 주황 "지금 켜세요", 깜빡이를 켜면 파랑 "좋아요", 3초 뒤 사라진다.
- 판정이 나오거나 회전이 끝나거나 기어가 P면 숨긴다. 안내 전에는 먼저 띄우지 않는다.
- 우리 앱 화면이 보이는 동안은 코치 카드가 같은 내용을 보여주므로 팝업을 띄우지 않는다.
- 내비를 보다가 기어를 P로 바꾸면 우리 앱이 앞으로 와서 리포트를 보여준다.
- 팝업은 "다른 앱 위에 표시" 권한이 있어야 뜬다. 에뮬레이터에서는 `adb shell appops set --user 10 kkam.app SYSTEM_ALERT_WINDOW allow`로 켠다. 권한이 없을 때의 대비책(상단 알림)은 다음 단계.

## 화면 구조

| 레이아웃 | 내용 |
| --- | --- |
| `activity_main.xml` | 뼈대. 왼쪽 데모 패널 자리와 오른쪽 `screenSlot` |
| `screen_drive.xml` | 주행 화면 (UI-1). 코치 카드, 한 줄 문구, 오늘 지킨 횟수. 터치할 것이 없다 |
| `screen_report.xml` | 오늘 주행 리포트 (UI-2). 기어 P일 때만 보인다 |
| `screen_diagnose.xml` | 진단 리포트 (UI-3). 핵심 문구, 상황별 준수율 막대(`diagnose_row.xml`). 기어 P일 때만 |
| `screen_habit.xml` | 습관 선택 (UI-3). 습관 카드(`habit_card.xml`) 최대 2개. 기어 P일 때만 |
| `panel_demo.xml` | 데모 패널 (F9). 시나리오, 실제 차량 신호, 신호 값, 판정 기록 |

- 데모 패널은 debug 빌드에서만 오른쪽 위 "데모" 버튼으로 연다. 릴리스 빌드에는 버튼이 없다.
- 코치 카드는 상태 색으로 전체를 칠한다. 주황은 켜세요, 파랑은 좋아요, 어두운 카드는 대기다.
- 화면은 `screenSlot` 안에서 마지막 프레임의 기어로 바꾼다. 기어 P에서 진단·습관 선택을 보고 있으면 리포트로 덮지 않는다 (`Screen.kt`). 주행 제한 처리 자리는 `MainActivity`의 TODO에 적어 두었다.
- 리포트의 조항·처분은 루트 `data/law_table.json`을 assets로 바로 쓴다. 룰 테이블에 룰 필드가 없어서 `DriveReport.kt`에서 R1→LAW-01, R2→LAW-03, R3→LAW-12로 연결한다.

코드 구조

| 파일 | 역할 |
| --- | --- |
| `MainActivity.kt` | 화면. 서비스에 붙어 세션 상태를 그리기만 한다 |
| `CoachService.kt` | 포그라운드 서비스. 내비가 앞에 있어도 세션이 계속 돈다 |
| `CoachSession.kt` | 재생·실제 신호 → 엔진 → 음성 → `CoachState` 흐름 |
| `PopupPolicy.kt` | 내비 위 팝업을 언제 띄우고 숨길지. JVM 테스트 대상 |
| `CoachPopup.kt` | 내비 위 팝업 카드 (겹침 창) |
| `NaviLauncher.kt` | 차량 기본 내비를 앞으로 띄움 (데모 "재생하면 내비 띄우기") |
| `CoachState.kt` | 프레임과 엔진 출력을 화면 값으로 변환. JVM 테스트 대상 |
| `DriveReport.kt` | 오늘 판정과 룰 테이블로 리포트 값 계산. JVM 테스트 대상 |
| `Screen.kt` | 화면 목록과 기어에 따른 화면 전환 규칙. JVM 테스트 대상 |
| `Diagnosis.kt` | 진단 값(습관별 준수율, 핵심 문구)과 `DiagnosisSource`. 지금은 데모 고정값. JVM 테스트 대상 |
| `HabitSelection.kt` | 습관 선택 상태 (최대 2개). JVM 테스트 대상 |
| `HabitScreens.kt` | 진단 리포트·습관 선택 화면 그리기 |
| `ScenarioPlayer.kt` | `scenarios/`의 JSON을 시간대로 재생 |
| `Speaker.kt` | Pleos TTS 호출 |
| `SignalCollector.kt` | 차량 신호 수집과 프레임 생성 |
| `SdkMapping.kt` | SDK 값을 엔진 값으로 변환. JVM 테스트 대상 |

시나리오 파일은 복사하지 않습니다. 루트 `scenarios/`를 앱 assets로 바로 씁니다.

## Pleos SDK 연동

Maven 저장소 `https://nexus-playground.pleos.ai/repository/maven-releases/`를 추가합니다. 확인용 앱에서 쓴 버전은 아래와 같습니다.

| 입력 | 가져오는 방법 | 권한 |
| --- | --- | --- |
| 방향지시등, 핸들, 기어, 브레이크 | `ai.pleos.playground:Vehicle:2.3.2` | `pleos.car.permission.CAR_LIGHTS`, `CAR_DRIVE`, `CAR_INFO` |
| 속도 | `android.car`의 `CarPropertyManager`, `PERF_VEHICLE_SPEED` | `android.car.permission.CAR_SPEED` |
| 위치, 진행 방향 | `ai.pleos.playground:FusedLocation:1.1.3` | `pleos.car.permission.FUSED_LOCATION` |
| TBT | `ai.pleos.playground:NaviHelper:3.0.5` | `pleos.car.permission.NAVI_ROUTE` |
| 음성 안내 | `ai.pleos.playground:TextToSpeech:3.3.1` | `pleos.car.permission.TTS_SERVICE` |

속도는 Vehicle SDK에 없습니다. 앱에서 `useLibrary("android.car")`를 선언합니다.

TTS는 `Mode.ON_DEVICE`로 만들면 앱 등록 없이 동작합니다. 언어는 `Locale.KOREA`로 지정합니다. `Locale.KOREAN`은 `ON_DEVICE_ENGINE_ERROR`로 실패합니다.

SDK 값을 엔진 값으로 바꾸는 규칙은 [spec 2.1](../docs/spec.md)을 따릅니다.

Virtual Console은 `adb forward tcp:8088 tcp:8088` 뒤 `http://127.0.0.1:8088/`을 브라우저로 열어 둔 동안만 값을 바꿀 수 있습니다.

## 엔진 사용법

[engine/README.md](../engine/README.md)를 참고합니다.
