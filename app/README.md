# 차량 앱

담당: 정현우 · 상태: 코치 카드, 기어 P 주행 리포트

Pleos Connect 차량 앱입니다. 공모전 개발 환경은 Android API 28 이상, Gradle 8.0 이상입니다.

## 지금 되는 것

- 시나리오 버튼을 누르면 녹화된 신호를 시간대로 재생해 엔진에 넣습니다.
- 코치 카드, 차량 신호, 오늘 지킨 횟수, 판정 기록을 화면에 보여줍니다.
- GUIDE가 나오면 Pleos TTS로 읽습니다.

- "실제 차량 신호" 버튼을 누르면 SDK 신호를 1초마다 `SignalFrame`으로 묶어 엔진에 넣습니다.

- 기어가 P가 되면 오늘 주행 리포트(지킴·놓침, 조항·처분, 아낀 범칙금)를 보여주고, 다른 기어가 되면 코치 카드로 돌아갑니다. 데모에서는 S2 다음에 S8(9초에 P)을 틀면 됩니다.

서버 저장은 아직 없습니다. 오늘 지킨 횟수와 리포트는 앱을 끄면 사라집니다. 리포트의 지도 핀은 지도 SDK를 확인한 뒤 붙입니다.

에뮬레이터는 내비 위치와 진행 방향이 움직이지 않습니다. 그래서 실제 신호 모드에서 30m 판정과 회전 완료는 확인할 수 없습니다. 속도를 약 45km/h 이상으로 올리면 80m 안내에서 GUIDE는 확인할 수 있습니다.

## 실행

Android SDK가 필요합니다. `ANDROID_HOME`을 지정하거나 루트에 `local.properties`를 만들어 `sdk.dir`을 적습니다. 이 설정이 없으면 `./gradlew test`도 실패합니다.

```bash
./gradlew :app:installDebug
```

## 화면 구조

| 레이아웃 | 내용 |
| --- | --- |
| `activity_main.xml` | 뼈대. 왼쪽 데모 패널 자리와 오른쪽 `screenSlot` |
| `screen_drive.xml` | 주행 화면 (UI-1). 코치 카드, 한 줄 문구, 오늘 지킨 횟수. 터치할 것이 없다 |
| `screen_report.xml` | 오늘 주행 리포트 (UI-2). 기어 P일 때만 보인다 |
| `panel_demo.xml` | 데모 패널 (F9). 시나리오, 실제 차량 신호, 신호 값, 판정 기록 |

- 데모 패널은 debug 빌드에서만 오른쪽 위 "데모" 버튼으로 연다. 릴리스 빌드에는 버튼이 없다.
- 코치 카드는 상태 색으로 전체를 칠한다. 주황은 켜세요, 파랑은 좋아요, 어두운 카드는 대기다.
- 화면은 `screenSlot` 안에서 마지막 프레임의 기어로 바꾼다. 진단 리포트·습관 선택도 여기에 더한다. 주행 제한 처리 자리는 `MainActivity`의 TODO에 적어 두었다.
- 리포트의 조항·처분은 루트 `data/law_table.json`을 assets로 바로 쓴다. 룰 테이블에 룰 필드가 없어서 `DriveReport.kt`에서 R1→LAW-01, R2→LAW-03, R3→LAW-12로 연결한다.

코드 구조

| 파일 | 역할 |
| --- | --- |
| `MainActivity.kt` | 화면과 재생 연결 |
| `CoachState.kt` | 프레임과 엔진 출력을 화면 값으로 변환. JVM 테스트 대상 |
| `DriveReport.kt` | 오늘 판정과 룰 테이블로 리포트 값 계산. JVM 테스트 대상 |
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
