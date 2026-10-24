---
name: contest-dev-repo-location
description: 현대차·기아 차량용 앱 공모전의 실제 개발 저장소는 C:\dev\hmg_app_challenge_contest (OneDrive 폴더 아님)
metadata:
  node_type: memory
  type: project
  originSessionId: e32737fb-6845-4701-9a14-453c509dc039
  modified: 2026-10-22T13:58:33.678Z
---

현대자동차·기아 차량용 앱 개발 공모전(Pleos Connect, 접수 마감 2026-11-15) Android 프로젝트의 작업 위치는 `C:\dev\hmg_app_challenge_contest` (origin: github.com/altrofast11x2/hmg_app_challenge_contest). `OneDrive\Desktop\현대 공모전` 폴더는 같은 저장소의 원래 클론으로, 2026-10-22부터 개발에 사용하지 않는다.

**Why:** 한글·공백 경로와 OneDrive 동기화는 Gradle 빌드를 방해한다. 사용자가 제안한 D:/E:는 exFAT USB 외장 SSD여서 Git ownership 오류와 USB 분리 위험 때문에 제외했다 (사용자는 "별로면 C:/dev로" 하라고 했다).

**How to apply:** 빌드·코드 작업은 C:\dev 경로에서 한다. 환경 관련 사실은 [[pleos-dev-environment]]에 정리되어 있다.
