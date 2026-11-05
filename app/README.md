# 차량 앱

담당: 정현우 · 상태: 시작 전

Pleos Connect 차량 앱을 이 폴더에 만듭니다. 공모전 개발 환경은 Android API 28 이상, Gradle 8.0 이상입니다.

## 프로젝트 설정

앱은 저장소 루트의 Gradle 빌드에 `:app` 모듈로 추가합니다.

1. 루트 `settings.gradle.kts`의 `pluginManagement`와 `dependencyResolutionManagement` 저장소 목록에 `google()`을 추가합니다. Pleos SDK 저장소가 필요하면 함께 추가합니다.
2. 같은 파일에 `include(":app")`을 추가합니다.
3. 루트 `build.gradle.kts`의 `plugins`에 Android 플러그인을 `apply false`로 선언합니다.
4. `app/build.gradle.kts`의 `dependencies`에 `implementation(project(":engine"))`을 추가합니다.

엔진은 AGP 9.5.0, Gradle 9.7.1, compileSdk 36, minSdk 28, Java 17 조합의 임시 앱에서 빌드를 확인했습니다. 에뮬레이터 실행과 Pleos SDK 연동은 아직 확인하지 않았습니다.

버전을 바꿔야 하면 루트의 Gradle·Kotlin 버전을 앱에 맞춰 조정합니다.

## 엔진 사용법

[engine/README.md](../engine/README.md)를 참고합니다.
