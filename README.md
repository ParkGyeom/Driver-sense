# 깜빡코치

**단속되기 전에, 습관부터.** 차량 신호로 운전 법규 습관을 진단하고, 고칠 습관만 골라 단속되기 전에 미리 알려주는 Pleos Connect 차량용 앱입니다. 현대자동차그룹 차량용 앱 개발 공모전 출품작입니다.

- 방향지시등, 기어, 속도, 내비의 다음 안내(TBT) 같은 차량 신호로 법규 판정을 차 안에서 바로 합니다.
- 판정 지점 몇 초 전에 깜빡이나 전조등이 꺼져 있으면 음성 한 문장과 내비 위 작은 카드로 알려줍니다.
- 기어를 P에 두면 오늘 지킨 것과 놓친 것을 법 조항·처분과 함께 보여줍니다.

## APK

실행용 release APK는 [apk/](apk/README.md) 폴더에 있습니다. 파일 이름은 `팀명_앱이름_release.apk`입니다.

## 테스트 환경

| 항목 | 버전·설정 |
| --- | --- |
| 실행 환경 | Pleos Connect Emulator v2.1.0 (2560×1440, 160dpi) |
| Android | minSdk 28, Kotlin 2.4, Gradle Wrapper 포함 |
| 빌드 | JDK 17, Android SDK (`local.properties`의 `sdk.dir` 또는 `ANDROID_HOME`) |
| Pleos SDK | Vehicle 2.3.2, NaviHelper 3.0.5, FusedLocation 1.1.3, TextToSpeech 3.3.1. Maven 저장소 `https://nexus-playground.pleos.ai/repository/maven-releases/` |
| 네이버 지도 | 경로 데모 지도. `local.properties`에 `NAVER_MAP_CLIENT_ID`를 적습니다 ([app/README.md](app/README.md)의 "네이버 지도"). 없으면 지도만 안 뜹니다 |
| 권한 | 위치(`pleos.car.permission.FUSED_LOCATION`)와 속도(`android.car.permission.CAR_SPEED`)는 앱을 처음 켤 때 묻습니다 |

에뮬레이터의 내비는 경로를 설정해도 위치가 움직이지 않습니다. 그래서 주행 장면은 녹화된 차량 신호(`scenarios/`)를 앱에 재생해서 보여줍니다. 판정 엔진은 실제 차량 신호와 재생 신호를 똑같이 처리합니다.

## 실행 방법

### APK로 실행

1. Pleos Connect Emulator를 켭니다.
2. APK를 설치하고 실행합니다.

   ```bash
   adb install -r apk/팀명_앱이름_release.apk
   adb shell am start --user 10 -n kkam.app/.MainActivity
   ```

3. 처음 켤 때 위치·속도 권한을 허용하면 실제 차량 신호로 주행 코치를 시작합니다. 기어, 방향지시등, 속도는 에뮬레이터의 Virtual Console에서 바꿉니다. `adb forward tcp:8088 tcp:8088` 뒤 브라우저로 `http://127.0.0.1:8088/`을 엽니다. 기어를 P로 바꾸면 오늘 주행 리포트가 나옵니다.
4. 내비 위 안내 카드는 "다른 앱 위에 표시" 권한이 있어야 뜹니다. 에뮬레이터에는 이 설정 화면이 없어서 아래 명령으로 켭니다. 권한이 없으면 같은 내용을 상단 알림으로 보여줍니다.

   ```bash
   adb shell appops set --user 10 kkam.app SYSTEM_ALERT_WINDOW allow
   ```

### 소스에서 빌드하고 시연 시나리오 재생

```bash
git clone https://github.com/ParkGyeom/Driver-sense.git
cd Driver-sense
./gradlew test :app:testDebugUnitTest   # 엔진·앱 단위 테스트와 시나리오 테스트
./gradlew :app:installDebug             # 에뮬레이터에 debug 앱 설치
```

debug 앱은 오른쪽 위 "데모" 버튼으로 데모 패널을 엽니다. 시나리오를 누르면 녹화된 주행 신호를 시간대로 재생합니다.

| 시나리오 | 보이는 것 |
| --- | --- |
| S2 좌회전 안내후 켬 | 좌회전 60m 앞 "좌회전 곧이에요, 깜빡이 켜세요" → 깜빡이를 켜면 파랑 "좋아요" → 회전 후 지킴 기록 |
| S3 좌회전 끝까지 안켬 | 안내 후에도 안 켜서 놓침 기록 |
| S8 판정후 주차 | 기어 P에서 오늘 주행 리포트(지킴·놓침, 조항·처분, 아낀 범칙금) |
| S11 터널 전조등 | 터널 187m 앞 "터널이 곧 나와요, 전조등 켜세요" → 켜면 지킴 기록 |
| S12 나들목 안내후 켬 | 오른쪽 나들목 167m 앞 깜빡이 안내와 레버 그림 → 100m 지점에서 지킴 기록 |

데모 패널의 "재생하면 내비 띄우기"를 켜면 내비를 앞에 둔 채 내비 위 안내 카드를 볼 수 있습니다. 시나리오 전체 목록과 기대 출력은 [docs/spec.md](docs/spec.md) 4장에 있습니다.

## 저장소 구조

앱, 서버, 판정 엔진을 한 저장소에서 개발합니다.

| 폴더 | 내용 | 담당 | 상태 |
| --- | --- | --- | --- |
| [engine/](engine/README.md) | 판정 엔진 (순수 Kotlin 라이브러리) | AI 담당 | R1 회전 전 깜빡이, R4 나들목 깜빡이, R5 터널 전조등 (spec v0.2) |
| `scenarios/` | 주행 시나리오 JSON (테스트와 데모 공용) | AI 담당 | S1~S12 |
| [app/](app/README.md) | Pleos Connect 차량 앱 | 앱 담당 | 주행 코치 카드, 내비 위 안내, 정차 리포트, 진단·습관 선택, 모드 설정, 법규 Q&A, 교정 진행 |
| [server/](server/README.md) | Spring 서버 | 서버 담당 | 개발 중 |
| `data/` | 법규 룰 테이블 (조항·처분·출처) | AI 담당, 서버 담당 | 15개 항목 |
| `docs/` | 기획서, 엔진 명세, 작업 순서 | PM, AI 담당 | 진행 중 |
| [apk/](apk/README.md) | 제출용 APK | 앱 담당 | 준비 중 |

## 협업 규칙

- `main`은 항상 빌드되는 상태로 둡니다. 직접 푸시하지 않습니다.
- 작업 브랜치에서 개발하고 작은 PR로 합칩니다. 브랜치 이름은 `engine/r2`, `app/coach-card`, `server/event-api`처럼 폴더 이름으로 시작합니다.
- 다른 담당자의 폴더를 고칠 때는 담당자에게 먼저 알립니다.
- 엔진 입출력 형식을 바꿀 때는 `docs/spec.md`를 먼저 고치고 앱·서버 담당자에게 공유합니다.
- 비밀 값(API 키, `local.properties`, 서명 키)은 커밋하지 않습니다.

### 실명 쓰지 않기 (공모전 제출 규정)

공모전 심사 자료와 제출 링크에는 개발자 실명, 계정 정보, 개인정보가 보이면 안 됩니다. 이 저장소의 파일은 그대로 제출본이 되므로 아래를 지킵니다.

- 코드, 주석, TODO, 문서, 앱 화면 문구에 팀원 실명, GitHub 계정 이름, 개인 이메일을 쓰지 않습니다. 대신 역할명(AI 담당, 앱 담당, 서버 담당, PM)을 씁니다.
- 시연 영상과 화면 캡처에도 이름, 계정, 개인 폴더 경로(`/Users/이름`), 메신저 알림이 보이지 않게 합니다.
- 제출용 저장소는 제출 직전에 이 저장소의 `main`을 복사해 따로 올립니다. 제출용 저장소에는 각자 커밋하지 않습니다. 커밋하면 개인 계정 이름이 기록에 남기 때문입니다.
- 커밋 메시지와 PR 본문은 제출본에 복사되지 않으므로 이름을 써도 됩니다.
