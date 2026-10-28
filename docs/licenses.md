# 오픈소스 라이선스

차량 앱(release 빌드)에 들어가는 소프트웨어 목록입니다. 앱 안의 **설정 > 오픈소스 라이선스** 화면과 같은 내용이며, Apache License 2.0 전문은 앱 안에서 볼 수 있습니다 (`app/src/main/res/raw/apache_license_2_0.txt`, https://www.apache.org/licenses/LICENSE-2.0).

확인 방법: `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` (2026-10-28 기준)

| 소프트웨어 | 저작권자 | 라이선스 |
|---|---|---|
| Pleos Connect SDK (Vehicle 2.3.2, NaviHelper 3.0.5, TextToSpeech 3.3.1, TextToSpeech가 포함하는 universal-access 0.6.0) | Pleos | Pleos Playground 이용약관 |
| AndroidX (Core, Activity, Lifecycle, Room, SQLite, WorkManager 외) | The Android Open Source Project | Apache License 2.0 |
| OkHttp, Okio | Square | Apache License 2.0 |
| ZXing core 3.5.3 (휴대폰으로 받기 QR 코드) | ZXing authors | Apache License 2.0 |
| Jetpack Compose (UI, Foundation, Material 3) | The Android Open Source Project | Apache License 2.0 |
| Asta Sans (글꼴, `res/font/asta_sans_variable.ttf`) | The Asta Sans Project Authors | SIL Open Font License 1.1 (`res/raw/sil_ofl_1_1.txt`) |
| Kotlin Standard Library, kotlin-reflect | JetBrains | Apache License 2.0 |
| kotlinx.coroutines, kotlinx.serialization | JetBrains | Apache License 2.0 |
| Dagger, Hilt (Pleos SDK가 포함) | Google | Apache License 2.0 |
| SQLCipher for Android 4.17.0 (운행 기록 DB 암호화) | Zetetic, LLC | BSD 계열 SQLCipher Community Edition License (`res/raw/sqlcipher_license.txt`, 앱 안에서 전문 표시) |
| Gson (Pleos SDK가 포함) | Google | Apache License 2.0 |
| Material Components for Android, Tink, Error Prone Annotations (Pleos TextToSpeech가 포함) | Google | Apache License 2.0 |
| JNA, Java Native Access (Pleos TextToSpeech가 포함) | JNA authors | Apache License 2.0 (LGPL 2.1과의 이중 라이선스 중 Apache 2.0) |
| Guava ListenableFuture | Google | Apache License 2.0 |
| JSR 305 Annotations | FindBugs | Apache License 2.0 |
| javax.inject | JSR-330 | Apache License 2.0 |
| JetBrains Java Annotations, JSpecify | JetBrains, JSpecify | Apache License 2.0 |

테스트에만 쓰고 앱에 들어가지 않는 것: JUnit 4 (Eclipse Public License 1.0), kotlinx-coroutines-test (Apache License 2.0), AndroidX Test (Apache License 2.0)

폰트, 아이콘, 이미지: 글꼴은 위 표의 Asta Sans(SIL OFL 1.1, 상업 이용 가능) 하나입니다. 앱 아이콘·알림 아이콘과 화면의 선 아이콘(`DriveLogIcons.kt`, `design/mockups`에서 옮김)은 팀이 직접 그린 벡터이고, 외부 아이콘·이미지·음원은 쓰지 않습니다. 디자인 자산이 추가되면 `design/assets/`의 라이선스를 여기에 옮깁니다.

## 관리자 웹 (`web/`)

JavaScript 라이브러리는 쓰지 않습니다. 글꼴 Asta Sans(SIL Open Font License 1.1)만 Google Fonts에서 불러옵니다.

새 라이브러리를 추가하면 이 표와 `app/src/main/java/com/altrostudio/drivelog/ui/settings/LicensesScreen.kt`를 함께 고칩니다.
