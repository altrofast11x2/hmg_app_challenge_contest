# design/ — DriveLog 디자인 기준 폴더

이 폴더는 **디자인 담당 Claude(claude.ai)** 가 직접 갱신한다.
개발 쪽(Claude Code)은 **읽기만** 하고 수정하지 않는다. 디자인 변경이 필요하면 `design/REQUESTS.md`에 요청을 적는다.

현재 버전: **v0.1 · 콘셉트 "Quiet Navy"** (변경 내용은 `CHANGELOG.md`)

## 구조

| 경로 | 내용 | 개발에서 쓰는 법 |
|---|---|---|
| `tokens/Color.kt` `Type.kt` `Dimens.kt` `DriveLogTheme.kt` | Compose용 디자인 토큰 | `app/.../ui/theme/`로 **복사**해서 쓴다. 값을 직접 고치지 않는다 |
| `tokens/tokens.css` | 관리자 웹용 CSS 변수 (Color.kt와 같은 값) | `web/`에서 import |
| `assets/fonts/` | Asta Sans 가변 글꼴 + OFL 라이선스 | `app/src/main/res/font/`로 복사 |
| `specs/A00-common.md` | 공통: 설치 방법, 화면 영역, 버튼·카드·배지, 주행 상태 규칙 | **먼저 읽는다** |
| `specs/A01-consent.md` … | 화면별 구현 명세 (컴포넌트, dp, 상태, 주행/정차 차이, 토큰 이름) | 화면 구현 전에 반드시 읽는다 |
| `mockups/*.html` | 시각 목업 (2560×1440, Landscape/Portrait, 다크/라이트) | 브라우저로 열어 참고 |
| `CHANGELOG.md` | 디자인 변경 이력 | 작업 시작 시 확인 |
| `REQUESTS.md` | 개발 → 디자인 요청·질문 | 개발 쪽이 여기에 적는다 (이 파일만 개발 쪽이 수정 가능) |

## 토큰 이름

| 디자인 토큰 | Compose | CSS |
|---|---|---|
| `drivelog_text_primary` | `DriveLogTheme.colors.textPrimary` | `var(--drivelog-text-primary)` |
| `drivelog_title` | `DriveLogTheme.typography.title` | `font: var(--drivelog-title)` |
| `drivelog_spacing_large` | `DriveLogTheme.dimens.spacingLarge` | `var(--drivelog-spacing-large)` |

## 규칙

1. 색·글자·간격·모서리는 토큰만 쓴다. 하드코딩된 `Color(0x…)`, `16.dp` 같은 값 금지 (토큰에 없으면 `REQUESTS.md`에 요청).
2. 모든 차량 화면은 Landscape(전체 앱 창)와 Portrait(2분할 절반) 두 레이아웃을 구현한다. 판단 기준은 앱 창 너비 1200dp.
3. 주행 중 화면: 터치 요소·애니메이션·자동 스크롤 없음. 명세의 "주행 중 / 정차 중" 항목을 따른다.
4. 명세에 **(가정값)** 표시가 있는 수치는 Pleos 공식 Figma 자료 확인 전의 임시값이다. 나중에 바뀔 수 있으니 토큰으로만 참조한다.
5. 명세와 실제 SDK 동작이 맞지 않으면 구현을 멈추지 말고 가까운 쪽으로 구현한 뒤 `REQUESTS.md`에 적는다.

## 진행 순서 (디자인)

A1~3 ✅ → A4~5 → A6~9 → B(관리자 웹) → C(아이콘, 타이틀 카드)
