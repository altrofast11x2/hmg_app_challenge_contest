# 디자인 변경 이력

최신이 위. 개발 쪽은 작업 시작 시 여기를 확인한다.

## 2026-10-26 — v0.1 콘셉트 "Quiet Navy", 토큰, A1~A3
- **콘셉트**: 깊은 남색 실내 + 샌드 톤 포인트 + 맑은 블루 액션. 용도는 색 블록(출퇴근 하늘색 / 일반업무 블루 / 비업무 회색)과 아이콘·글자를 함께 쓴다. 자체 팔레트이며 특정 브랜드 색상값·로고는 쓰지 않음
- **글꼴**: Asta Sans (SIL OFL 1.1). Pleos 시스템 UI와 같은 글꼴. `assets/fonts/`
- **추가**: `tokens/Color.kt` `Type.kt` `Dimens.kt` `DriveLogTheme.kt` `tokens.css`
- **추가**: `specs/A00-common.md`(공통), `A01-consent.md`, `A02-connect.md`, `A03-home.md`
- **추가**: `mockups/A01-A03.html` (브라우저로 열기, 다크/라이트 전환)
- **개발 할 일**: A00 1절 "설치" 4단계 → 기존 `ui/theme/Color.kt`, `Type.kt`, `Theme.kt`(Navy/Green 임시값) 교체
- 다음: A04 주행 중, A05 정차 후 원탭 분류

## 2026-10-26 — design/ 폴더 연결
- 폴더 구조와 규칙(`README.md`) 생성
