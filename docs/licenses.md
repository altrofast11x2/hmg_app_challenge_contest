# 오픈소스 라이선스

차량 앱(release 빌드)에 들어가는 소프트웨어 목록입니다. 앱 안의 **설정 > 오픈소스 라이선스** 화면과 같은 내용이며, Apache License 2.0 전문은 앱 안에서 볼 수 있습니다 (`app/src/main/res/raw/apache_license_2_0.txt`, https://www.apache.org/licenses/LICENSE-2.0).

확인 방법: `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` (2026-10-26 기준)

| 소프트웨어 | 저작권자 | 라이선스 |
|---|---|---|
| Pleos Connect SDK (Vehicle 2.3.2, NaviHelper 3.0.5) | Pleos | Pleos Playground 이용약관 |
| AndroidX (Core, Activity, Lifecycle, Room, SQLite, WorkManager 외) | The Android Open Source Project | Apache License 2.0 |
| OkHttp, Okio | Square | Apache License 2.0 |
| Jetpack Compose (UI, Foundation, Material 3) | The Android Open Source Project | Apache License 2.0 |
| Kotlin Standard Library, kotlin-reflect | JetBrains | Apache License 2.0 |
| kotlinx.coroutines, kotlinx.serialization | JetBrains | Apache License 2.0 |
| Dagger, Hilt (Pleos SDK가 포함) | Google | Apache License 2.0 |
| Gson (Pleos SDK가 포함) | Google | Apache License 2.0 |
| Guava ListenableFuture | Google | Apache License 2.0 |
| JSR 305 Annotations | FindBugs | Apache License 2.0 |
| javax.inject | JSR-330 | Apache License 2.0 |
| JetBrains Java Annotations, JSpecify | JetBrains, JSpecify | Apache License 2.0 |

테스트에만 쓰고 앱에 들어가지 않는 것: JUnit 4 (Eclipse Public License 1.0), kotlinx-coroutines-test (Apache License 2.0), AndroidX Test (Apache License 2.0)

폰트, 아이콘, 이미지: 앱 아이콘과 알림 아이콘은 직접 그린 벡터이고, 외부 폰트·아이콘·음원은 쓰지 않습니다. 디자인 자산이 추가되면 `design/assets/`의 라이선스를 여기에 옮깁니다.

새 라이브러리를 추가하면 이 표와 `app/src/main/java/com/altrostudio/drivelog/ui/settings/LicensesScreen.kt`를 함께 고칩니다.
