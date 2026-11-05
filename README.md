# 깜빡코치

단속되기 전에, 습관부터. 차량 신호로 운전 법규 습관을 진단하고, 고칠 습관만 골라 미리 알려주는 Pleos Connect 차량용 앱입니다. 현대자동차그룹 차량용 앱 개발 공모전 출품작입니다.

## 저장소 구조

앱, 서버, 판정 엔진을 한 저장소에서 개발합니다.

| 폴더 | 내용 | 담당 | 상태 |
| --- | --- | --- | --- |
| [engine/](engine/README.md) | 판정 엔진 (순수 Kotlin 라이브러리) | 박겸 | R1 완료 (v0.1) |
| `scenarios/` | 주행 시나리오 JSON (테스트와 데모 공용) | 박겸 | S1~S10 |
| [app/](app/README.md) | Pleos Connect 차량 앱 | 정현우 | 시작 전 |
| [server/](server/README.md) | Spring 서버 | 김종범 | 시작 전 |
| `docs/` | 기획서, 엔진 명세, 작업 순서 | 송대현, 박겸 | 진행 중 |
| [apk/](apk/README.md) | 제출용 APK | 정현우 | 시작 전 |

## 시작하기

필요 환경: JDK 17, Android SDK (`local.properties`의 `sdk.dir` 또는 `ANDROID_HOME`)

```bash
git clone https://github.com/ParkGyeom/Driver-sense.git
cd Driver-sense
./gradlew test
```

## 협업 규칙

- `main`은 항상 빌드되는 상태로 둡니다. 직접 푸시하지 않습니다.
- 작업 브랜치에서 개발하고 작은 PR로 합칩니다. 브랜치 이름은 `engine/r2`, `app/coach-card`, `server/event-api`처럼 폴더 이름으로 시작합니다.
- 다른 담당자의 폴더를 고칠 때는 담당자에게 먼저 알립니다.
- 엔진 입출력 형식을 바꿀 때는 `docs/spec.md`를 먼저 고치고 앱·서버 담당자에게 공유합니다.
- 비밀 값(API 키, `local.properties`, 서명 키)은 커밋하지 않습니다.
