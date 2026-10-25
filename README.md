# 자동 운행기록부 (DriveLog)

AltroStudio · 현대자동차그룹 차량용 앱 개발 공모전 출품작 (Pleos Connect 앱)

> 개발 중입니다. 지금은 프로젝트 골격(1단계)까지만 완료되었고, 기능은 아직 구현되지 않았습니다.

## 앱 이름과 한 줄 소개

- **앱 이름**: 자동 운행기록부 (영문명 DriveLog, 임시)
- **한 줄 소개**: 외근이 잦은 법인·개인사업자를 위해 차가 알아서 운행기록부를 써 주는 앱. 주행 중에는 조작이 없고, 정차 후 한 번 눌러 용도만 고르면 월별 운행기록부가 완성됩니다.

## 실행 방법

1. Android Studio에서 `File > Open`으로 이 저장소 루트를 엽니다.
2. Gradle 동기화가 끝나면 실행 대상으로 `Pleos_Connect_v2.1` 에뮬레이터를 고르고 **Run** 합니다.

명령줄로 빌드·설치할 때 (JDK 17):

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug       # 실행 중인 에뮬레이터에 설치
```

## 테스트 환경과 필요한 설정

| 항목 | 버전 / 값 |
|---|---|
| JDK | 17 |
| Gradle / AGP / Kotlin | 8.10.2 / 8.8.2 / 2.0.0 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 28 |
| 에뮬레이터 | Pleos Connect v2.1 (Android 14, API 34, x86_64, Automotive) |

### Pleos Connect 에뮬레이터

1. `Tools > SDK Manager > SDK Update Sites`에 아래 URL을 추가하고, `SDK Platforms`에서 API 34의 Pleos Connect system image를 설치합니다.
   - Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
2. `Device Manager > Create device > Automotive > Pleos Connect v2.1.0`
3. Advanced Settings: Boot **Cold**, RAM **4 GB**. Quick boot로 실행하면 차량 데이터(VHAL)를 쓸 수 없습니다.

### Virtual Console (차량 상태 시뮬레이션)

```bash
adb forward tcp:8088 tcp:8088
```

브라우저에서 http://127.0.0.1:8088/ 에 접속합니다.

### 데모 계정

백엔드 구현(6단계) 후에 추가합니다.

## APK 위치

- `apk/AltroStudio_DriveLog_release.apk` (release 서명 빌드, 8단계에서 추가 예정)

## 라이선스

[MIT](LICENSE)
