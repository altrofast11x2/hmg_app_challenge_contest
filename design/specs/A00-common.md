# A00 공통 — 레이아웃, 컴포넌트, 주행 상태

> 디자인 v0.1 · 2026-10-26 · 콘셉트 **"Quiet Navy"** (깊은 남색 실내 + 샌드 포인트 + 용도별 색 블록)
> 목업: `design/mockups/A01-A03.html` · 토큰: `design/tokens/`
> **(가정값)** = Pleos Figma 자료 확인 전 임시값. 토큰으로만 참조하면 나중에 값만 바뀐다.

## 1. 토큰 쓰는 법

| 디자인 토큰 | Compose | CSS |
|---|---|---|
| `drivelog_text_primary` | `DriveLogTheme.colors.textPrimary` | `var(--drivelog-text-primary)` |
| `drivelog_title` | `DriveLogTheme.typography.title` | `font: var(--drivelog-title)` |
| `drivelog_spacing_large` | `DriveLogTheme.dimens.spacingLarge` | `var(--drivelog-spacing-large)` |

규칙: 접두사 `drivelog_` 를 떼고 snake_case → camelCase. 명세에는 디자인 토큰 이름으로 적는다.

### 설치 (1회)
1. `design/tokens/Color.kt`, `Type.kt`, `Dimens.kt`, `DriveLogTheme.kt` → `app/src/main/java/com/altrostudio/drivelog/ui/theme/` 에 복사
2. 기존 `ui/theme/Color.kt`, `Type.kt`, `Theme.kt` 는 삭제 (위 파일로 대체. `DriveLogTheme { }` 호출부는 그대로 동작)
3. `design/assets/fonts/asta_sans_variable.ttf` → `app/src/main/res/font/asta_sans_variable.ttf`
4. 오픈소스 라이선스 화면과 `docs/licenses.md` 에 Asta Sans (SIL OFL 1.1, © 2024 The Asta Sans Project Authors) 추가. 원문: `design/assets/fonts/OFL.txt`

## 2. 화면 영역 (가정값)

에뮬레이터 2560×1440, 160dpi (1dp = 1px).

| 영역 | 위치·크기 | 비고 |
|---|---|---|
| 상태 표시줄 | 위 72 | 시스템 |
| 드라이빙뷰 | 왼쪽 760 × 1224 | 시스템 |
| GNB | 아래 144 | 시스템 |
| **앱 창 Landscape** | 1800 × 1224 | 전체 앱 창 |
| **앱 창 Portrait** | 900 × 1224 | 2분할 중 절반 |

- 레이아웃 판단은 **앱 창 너비**로 한다: `< drivelog_portrait_breakpoint_width (1200)` → Portrait. 화면 방향이 아니라 창 크기다 (`BoxWithConstraints` 또는 `WindowSizeClass`).
- 시스템 UI(공조 패널, 키보드)가 앱 창을 덮을 수 있다. 주 버튼은 창 아래 끝에 딱 붙이지 말고 `drivelog_screen_padding_vertical`(48) 이상 띄운다.
- 안쪽 여백: Landscape 좌우 `drivelog_screen_padding_horizontal_landscape`(72), Portrait 좌우 `..._portrait`(40), 위아래 `drivelog_screen_padding_vertical`(48).

## 3. 공통 컴포넌트

### 3.1 TopBar
- 높이 `drivelog_top_bar_height`(112), 배경 없음(`drivelog_background` 위)
- 왼쪽: 화면 제목 `drivelog_headline`, `drivelog_text_primary`
- 오른쪽: 상태 칩(동기화 등) + 아이콘 버튼. 아이콘 버튼 = 80×80 터치 영역(`drivelog_touch_target_minimum`), 아이콘 `drivelog_icon_size_medium`(44), 배경 `drivelog_button_secondary_background`, 모서리 `drivelog_radius_full`
- 뒤로가기가 있는 화면은 제목 왼쪽에 80×80 뒤로 버튼

### 3.2 Button

| 종류 | 높이 | 배경 / 글자 | 모서리 | 글자 |
|---|---|---|---|---|
| Primary Large | `drivelog_button_height_large` 120 | `drivelog_button_primary_background` / `..._text` | `drivelog_radius_medium` | `drivelog_title` |
| Primary | `drivelog_button_height_medium` 96 | 같음 | `drivelog_radius_medium` | `drivelog_title_small` |
| Secondary | 96 또는 120 | `drivelog_button_secondary_background` + 테두리 `drivelog_border_strong` 2 / `drivelog_button_secondary_text` | 같음 | 높이 120은 `drivelog_title`, 96은 `drivelog_title_small` |
| Text | 80 | 없음 / `drivelog_text_secondary` | `drivelog_radius_full` | `drivelog_label` |

좌우 안쪽 여백 `drivelog_spacing_extra_large`(48). 최소 너비 240.

**상태 (모든 터치 요소 공통)**

| 상태 | 표현 |
|---|---|
| 기본 | 위 표 |
| 누름 | 크기 `drivelog_press_scale` 0.95 (100ms), 배경 `..._pressed` 토큰으로 교체. 손가락이 영역 밖으로 나가면 원래대로 + 동작 취소 |
| 비활성 | 배경 `drivelog_button_disabled_background`, 글자 `drivelog_text_disabled`, 누름 반응 없음 |
| 로딩 | 글자 자리에 원형 진행 표시(28, `..._text` 색) + 같은 너비 유지, 터치 막음. 2초(`drivelog_loading_threshold`) 넘으면 버튼 아래에 안내 문구 `drivelog_label_small` |
| 포커스(로터리·키보드) | 바깥 `drivelog_selection_ring` 4 링 |

- 누름 반응은 0.25초(`drivelog_touch_response_limit`) 안에 보여야 한다. 네트워크 응답을 기다리지 말고 즉시 누름 → 로딩으로 바꾼다.
- 스크롤 끝 바운스: Compose 기본 overscroll(스트레치)을 쓰지 않고 바운스로. 구현이 어렵다면 기본값 두고 `REQUESTS.md` 에 적기.

### 3.3 Card
- 배경 `drivelog_surface`, 모서리 `drivelog_radius_large`(32), 안쪽 여백 `drivelog_spacing_extra_large`(48), 테두리 없음
- 라이트 테마에서만 그림자 `drivelog_elevation_card`
- 카드 안의 항목 사이 `drivelog_spacing_large`(32)

### 3.4 Badge / Chip
- 높이 `drivelog_badge_height`(48), 좌우 20, 모서리 `drivelog_radius_full`, 글자 `drivelog_label_small`
- **차량 기록 배지**: 배경 `drivelog_status_integrity_container`, 글자·아이콘 `drivelog_status_integrity`, 방패+체크 아이콘 28. 문구 "차량 기록"
- **미분류 배지**: 배경 `drivelog_purpose_unclassified_background`, 글자 `drivelog_purpose_unclassified_text`, 문구 "미분류 N건"
- **용도 칩**: 배경 `drivelog_purpose_{commute|business|personal}_background`, 글자 `..._text`. 아이콘 24 + 글자 (색만으로 구분하지 않는다)
- **동기화 칩**: 아이콘 + 글자, `drivelog_text_secondary`. 상태: 동기화됨(체크) / 동기화 중(`drivelog_status_info`, 회전 아이콘 — 정차 중에만 회전, 주행 중엔 정지 아이콘) / 오프라인(`drivelog_status_warning`, "오프라인 · 연결되면 올라가요")

### 3.5 용도 표기 (전 화면 공통)

| 용도 | 문구 | 아이콘 (Material Symbols로 교체 예정) | 업무용 거리 |
|---|---|---|---|
| 출퇴근 | 출퇴근 | home_work | 포함 |
| 일반업무 | 일반업무 | work | 포함 |
| 비업무 | 비업무 | person | 제외, 주소 저장 안 함 |
| 미분류 | 미분류 | help | - |

## 4. 주행 상태별 규칙 (모든 화면)

| 상태 판단 | 앱 동작 |
|---|---|
| **주행 중**: 기어 P 아님 (또는 주행 제한 활성) | 지금 화면이 무엇이든 **A04 주행 중 화면으로 즉시 교체** (전환 애니메이션 없음). 모달·키패드·스크롤 목록 모두 닫는다. 입력하던 연결 코드는 지운다 |
| **정차 중**: 기어 P | 주행 직후면 A05 분류 화면, 아니면 직전 화면 복귀 |
| 신호 대기·Auto Hold (P 아님) | 주행 중과 같음 |

- 주행 중에는 터치 요소, 애니메이션, 움직이는 이미지, 자동 스크롤, 회전하는 진행 표시를 쓰지 않는다.
- A01~A03은 모두 **정차 중 전용** 화면이다.

## 5. 아이콘
- 목업은 직접 그린 선 아이콘(2px 획, 둥근 끝). 구현은 Material Symbols Rounded (Apache 2.0)로 같은 의미의 아이콘을 쓴다. 획 굵기 weight 400, grade 0, optical size 48.
- 현대·Pleos 로고와 상표, 그와 닮은 도형은 쓰지 않는다.
