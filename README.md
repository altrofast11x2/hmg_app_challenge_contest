# HMG App Challenge

## 앱 이름 및 한 줄 소개

- **앱 이름**: (미정)
- **한 줄 소개**: (미정)

## 실행 방법

1. Android Studio에서 이 저장소 루트를 엽니다 (`File > Open`).
2. Gradle Sync가 끝나면 실행 대상으로 `Pleos_Connect_v2.1` 에뮬레이터를 선택하고 **Run** 합니다.

명령줄로 빌드·설치할 경우:

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug       # 실행 중인 에뮬레이터에 설치
```

## 테스트 환경 및 필요한 설정

| 항목 | 버전 / 값 |
|---|---|
| Android Studio | 2026.2.1 |
| JDK | 17 (Temurin 17.0.20.1) |
| Gradle / AGP / Kotlin | 8.10.2 / 8.8.2 / 2.0.0 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 28 |
| Emulator | Pleos Connect v2.1.0 (Android 14, API 34, x86_64) |
| Pleos Connect SDK | Vehicle 2.3.2 |

### Pleos Connect Emulator 설정

1. `Tools > SDK Manager > SDK Update Sites`에 아래 URL 추가 후, `SDK Platforms`에서 API 34의 Pleos Connect system image 설치
   - Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
   - Mac: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
2. `Device Manager > Create device > Automotive > Pleos Connect v2.1.0`
3. Advanced Settings: Boot **Cold**, RAM **4 GB**, Graphics **Hardware**
   - Quick boot으로 실행하면 VHAL(차량 데이터)을 사용할 수 없습니다.

### Virtual Console (차량 상태 시뮬레이션)

```bash
adb forward tcp:8088 tcp:8088
```

브라우저에서 http://127.0.0.1:8088/ 접속

## APK 파일 위치 또는 GitHub Release 링크

- `apk/` 폴더 (파일명: `팀명_앱이름_release.apk`)
