# A02 차량 연결 (6자리 코드)

> 정차 중 전용 · 목업 `mockups/A01-A03.html#A02` · 공통 규칙 `A00-common.md`

## 목적
관리자 웹에서 발급한 6자리 숫자 코드로 이 차량을 조직에 등록한다. 키보드 대신 앱 안의 큰 숫자 키패드를 쓴다.

## Landscape (1800×1224)

```
┌ TopBar: [←] 제목 "차량 연결" ─────────────────────────── [단계 2/2 칩] ┐
│ ┌ 왼쪽 860 ─────────────────────────┐   ┌ 오른쪽: 키패드 812 ──────┐ │
│ │ 안내 제목 "연결 코드를 입력하세요" │   │  1   2   3               │ │
│ │ 보조 설명 2줄                      │   │  4   5   6               │ │
│ │ [ 4 ][ 8 ][ 2 ][ _ ][   ][   ]     │   │  7   8   9               │ │
│ │ 상태 줄 (오류·확인 중)             │   │ 지움  0  ⌫               │ │
│ │ ─────                              │   └──────────────────────────┘ │
│ │ 이 차량: 차대번호 확인됨 / 기기 ID │                                │
│ └────────────────────────────────────┘                                │
└────────────────────────────────────────────────────────────────────────┘
```

| 컴포넌트 | 크기 (dp) | 토큰 |
|---|---|---|
| 안내 제목 | | `drivelog_headline`, `drivelog_text_primary` |
| 보조 설명 | "관리자 웹 › 차량 › 연결 코드 발급에서 받은 6자리 숫자예요." / "코드는 [10분] 동안 유효해요." (유효시간은 백엔드와 맞출 것) | `drivelog_body`, `drivelog_text_secondary` |
| 코드 칸 ×6 | 칸 `drivelog_code_cell_width` 112 × `drivelog_code_cell_height` 144, 칸 사이 16, 3번째와 4번째 사이 40 (3+3 묶음) | 배경 `drivelog_surface_raised`, 모서리 `drivelog_radius_medium`, 숫자 `drivelog_display`(96) + tnum |
| 현재 칸 | 테두리 4 `drivelog_accent` + 아래쪽 커서 막대 48×6 (깜빡임 없음 — 정적) | |
| 빈 칸 | 테두리 2 `drivelog_border` | |
| 상태 줄 | 높이 48 고정 (문구가 없을 때도 자리 유지) | `drivelog_label` |
| 차량 식별 줄 | 아이콘 32 + "이 차량: 차대번호 확인됨" 또는 "이 차량: 차대번호 미확인 · 기기 ID로 등록돼요" | `drivelog_label_small`, `drivelog_text_tertiary` |
| 키패드 | 3×4 그리드, 키 사이 `drivelog_spacing_medium` 24, 키 높이 `drivelog_keypad_key_height_landscape` 128, 너비 = (812−48)/3 ≈ 254 | 키 배경 `drivelog_button_secondary_background`, 숫자 `drivelog_headline`(56) |
| 숫자 키 | 0~9 | 누름 `drivelog_button_secondary_background_pressed` + 0.95 |
| 지움 키 (좌하) | 글자 "전체 지움" | `drivelog_label`, `drivelog_text_secondary` |
| 백스페이스 키 (우하) | 아이콘 52 (backspace), `aria/contentDescription` "한 글자 지움" | `drivelog_text_primary` |

입력 동작
- 숫자 키: 다음 빈 칸 채움. 6칸이 차면 **자동으로 확인 요청** (따로 "연결" 버튼 없음)
- 6칸이 찬 뒤 키를 누르면 무시 (확인 중) 또는 오류 상태에서는 전체를 지우고 그 숫자부터 다시
- 코드 칸 자체를 눌러도 아무 동작 없음 (키보드 열지 않음)
- 붙여넣기·시스템 키보드 미지원

## Portrait (900×1224)
- 위에서부터: TopBar → 안내 제목(`drivelog_title`) → 설명 1줄 → 코드 칸 → 상태 줄 → 키패드 → 차량 식별 줄
- 코드 칸: **104×128**, 숫자 `drivelog_display`(96) 유지. 전체 너비 = 104×6 + 16×4 + 32(가운데) = 720 (창 안쪽 820에 맞음)
- 키패드: 너비 꽉 채움(820), 키 높이 `drivelog_keypad_key_height_portrait` 112, 간격 16

## 상태

| 상태 | 코드 칸 | 상태 줄 | 키패드 |
|---|---|---|---|
| 비어 있음 | 첫 칸이 현재 칸 | (비어 있음) | 활성 |
| 입력 중 | 채운 칸 테두리 `drivelog_border_strong` | (비어 있음) | 활성 |
| 확인 중 | 6칸 모두 `drivelog_border_strong` | 0~2초: 비어 있음 / 2초 이후: 정적 진행 아이콘 + "확인하고 있어요" `drivelog_status_info` | **비활성** (`drivelog_text_disabled`) |
| 오류 · 코드 틀림 | 테두리 4 `drivelog_status_error`, 숫자 유지 | 오류 아이콘 + "코드가 맞지 않아요. 관리자 웹에서 다시 확인해 주세요" `drivelog_status_error` | 활성 (누르면 새로 입력) |
| 오류 · 만료 | 같음 | "만료된 코드예요. 관리자에게 새 코드를 요청하세요" | 활성 |
| 오류 · 오프라인 | 같음 | "인터넷에 연결되지 않았어요. 연결 후 다시 입력해 주세요" `drivelog_status_warning` | 활성 |
| 연결됨 | 화면 전체를 결과로 교체: 체크 아이콘 96 `drivelog_status_integrity` + "[조직 이름]에 연결됐어요" `drivelog_headline` + "이제 운행이 자동으로 기록돼요" `drivelog_body` + [시작하기] Primary Large (너비 480) | | |

애니메이션: 오류 시 흔들림 효과 없음 (색과 문구로만). 연결됨 전환도 즉시.

## 주행 중 / 정차 중
- **주행 중**: A04 "시작 전" 변형("정차 후 연결할 수 있어요")으로 교체, 입력 중이던 숫자 삭제. 확인 요청이 이미 나갔으면 결과는 받아 두고 정차 후 결과 화면 표시
- 정차 중: 위 화면

## 뒤로가기
A01로 돌아가지 않는다 (이미 동의함). 뒤로가기 = 앱 종료. TopBar의 [←] 버튼은 Portrait·Landscape 모두 숨김. ※ 목업의 [←]는 설정 › "다른 조직으로 다시 연결"에서 들어온 경우의 모습
