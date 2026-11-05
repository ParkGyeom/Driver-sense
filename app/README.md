# 차량 앱

담당: 정현우 · 상태: 시나리오 재생 프로토타입 (박겸이 먼저 시작)

Pleos Connect 차량 앱입니다. 공모전 개발 환경은 Android API 28 이상, Gradle 8.0 이상입니다.

## 지금 되는 것

- 시나리오 버튼을 누르면 녹화된 신호를 시간대로 재생해 엔진에 넣습니다.
- 코치 카드, 차량 신호, 오늘 지킨 횟수, 판정 기록을 화면에 보여줍니다.
- GUIDE가 나오면 Pleos TTS로 읽습니다.

실제 차량 신호 수집, 서버 저장, 리포트 화면은 아직 없습니다. 오늘 지킨 횟수는 앱을 끄면 사라집니다.

## 실행

Android SDK가 필요합니다. `ANDROID_HOME`을 지정하거나 루트에 `local.properties`를 만들어 `sdk.dir`을 적습니다. 이 설정이 없으면 `./gradlew test`도 실패합니다.

```bash
./gradlew :app:installDebug
```

코드 구조

| 파일 | 역할 |
| --- | --- |
| `MainActivity.kt` | 화면과 재생 연결 |
| `CoachState.kt` | 프레임과 엔진 출력을 화면 값으로 변환. JVM 테스트 대상 |
| `ScenarioPlayer.kt` | `scenarios/`의 JSON을 시간대로 재생 |
| `Speaker.kt` | Pleos TTS 호출 |

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
