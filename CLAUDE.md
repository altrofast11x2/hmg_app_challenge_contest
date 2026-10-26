# DriveLog 작업 규칙

## 디자인

- UI 디자인 기준은 `design/` 폴더다. 디자인 담당 Claude(claude.ai)가 이 폴더를 직접 갱신한다.
- `design/`은 읽기 전용이다. 단, `design/REQUESTS.md`에는 디자인 질문과 요청을 적어도 된다.
- 색, 글자, 간격, 모서리는 `design/tokens`의 `drivelog_` 토큰만 쓴다. 하드코딩 금지.
- 화면을 구현하기 전에 `design/specs/`의 해당 명세를 먼저 읽는다.
- 작업을 시작할 때마다 `design/CHANGELOG.md`를 확인한다.
