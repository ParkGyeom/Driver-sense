# 차량 앱

담당: 정현우 · 상태: 시작 전

Pleos Connect 차량 앱을 이 폴더에 만듭니다. 공모전 개발 환경은 Android API 28 이상, Gradle 8.0 이상입니다.

## 프로젝트 설정

앱은 저장소 루트의 Gradle 빌드에 `:app` 모듈로 추가합니다.

1. 루트 `settings.gradle.kts`의 `pluginManagement`와 `dependencyResolutionManagement` 저장소 목록에 `google()`을 추가합니다. Pleos SDK 저장소가 필요하면 함께 추가합니다.
2. 같은 파일에 `include(":app")`을 추가합니다.
3. 루트 `build.gradle.kts`의 `plugins`에 Android 플러그인을 `apply false`로 선언합니다.
4. `app/build.gradle.kts`의 `dependencies`에 `implementation(project(":engine"))`을 추가합니다.

엔진은 AGP 9.5.0, Gradle 9.7.1, compileSdk 36, minSdk 28, Java 17 조합의 임시 앱에서 빌드를 확인했습니다. 같은 조합의 확인용 앱에 Pleos SDK를 넣어 에뮬레이터에서 실행했습니다. 엔진과 Pleos SDK를 함께 연결하는 것은 아직 확인하지 않았습니다.

버전을 바꿔야 하면 루트의 Gradle·Kotlin 버전을 앱에 맞춰 조정합니다.

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
