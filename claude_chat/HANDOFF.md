# 작업 인수인계

> 마지막 갱신: 2026-10-26 · 세션 ID는 `transcript/SESSION_ID.txt`

## 지금 상태

| 단계 (SPEC.md 11번) | 상태 |
|---|---|
| 1. 프로젝트 생성 | 완료: assembleDebug와 단위 테스트 성공, `Pleos_Connect_v2.1`에 설치·실행 확인 |
| 2. 환경 점검 (GMS 확인, 문서 읽기, 구현 계획과 백엔드 제안) | 조사 완료, 보고함 (GMS 없음 → Supabase 제안). 결과는 `docs/sdk-usage.md`. 사용자 결정 대기 |
| 3~9 | 계획 |

## 결정 사항

- 저장소: 비공개 `altrofast11x2/hmg_app_challenge_contest`의 `altrofast11x2` 브랜치. 이전 내용은 `altrofast11x2_backup`에 백업했다.
- `altrofast11x2` push는 매번 사용자에게 확인받는다.
- 패키지 `com.altrostudio.drivelog`, 앱 이름 "자동 운행기록부", minSdk 28, compileSdk/targetSdk 35, Gradle 8.10.2 / AGP 8.8.2 / Kotlin 2.0.0, Java 17
- Gradle wrapper는 공식 8.10.2 배포판으로 생성했고, `distributionSha256Sum`으로 배포판 체크섬을 검증한다.
- 앱 아이콘은 직접 그린 벡터다 (외부 아이콘 미사용).

## 이 PC에서 빌드할 때

- Claude Code 세션의 JAVA_HOME은 JBR 21을 가리킨다. 명령줄 빌드는 JDK 17을 직접 지정한다.
  ```bash
  export JAVA_HOME="/c/Users/kazin/AppData/Local/Programs/Eclipse Adoptium/jdk-17.0.20.1+1"
  ./gradlew assembleDebug
  ```
- 그 밖의 환경 함정은 `memory/pleos-dev-environment.md`에 있다.
