# Agent Instructions

이 저장소에서 작업하는 AI 코딩 에이전트(Claude Code, Codex 등)와 팀원을 위한 규칙입니다.

## 작업 시작 전
- `PROGRESS.md`의 최근 항목을 먼저 읽고, 다른 팀원의 진행 상황·막힌 문제·필요한 설정을 파악하세요.
- `git fetch` 후 `origin/main`에 새 커밋이 있는지 확인하세요.

## 참고 문서
- DB 스키마: 실제 앱이 쓰는 테이블은 `backend/src/main/resources/schema.sql`(live 프로필에서 실행). Advisor 에이전트용 카탈로그·학생 상태 스키마 초안은 `docs/advisor-schema.sql`이며 아직 `DatabaseConfig`에 연결되지 않았습니다.
- 에이전트 도구 명세: `docs/advisor-tools.md` (도구별 입출력, 라우팅 규칙, Gemini function declaration 예시). 예시 값은 가상 데이터입니다.
- 카탈로그 시드: `data/catalog/2026-27/` (과목 `courses.json`, 전공 요건 `programs/*.json`). 생성 스크립트는 `ingest/parse_bulletin_courses.py`
- 카탈로그 작업 배경과 변경 내역: `docs/CHANGES-advisor-catalog-db.md`
- 스키마나 도구 명세를 바꾸면 위 문서를 함께 갱신하세요.

## 커밋·푸시 전
- `PROGRESS.md` 맨 위에 이번 작업 항목을 추가하고 같은 커밋에 포함하세요. 형식은 파일 상단 템플릿을 따릅니다.
- "상태"에는 실제로 실행한 검증만 적고, 미검증 범위를 숨기지 마세요.
- 새 환경변수가 필요하면 `backend/.env.example`과 `PROGRESS.md`의 "필요한 설정"에 이름만 적으세요. 키·비밀번호 값은 절대 커밋하지 마세요.
- 검증 결과를 자세히 남길 때는 `VERIFICATION.md`에 섹션을 추가하세요.
