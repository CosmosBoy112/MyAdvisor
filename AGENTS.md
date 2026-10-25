# Agent Instructions

이 저장소에서 작업하는 AI 코딩 에이전트(Claude Code, Codex 등)와 팀원을 위한 규칙입니다.

## 작업 시작 전
- `PROGRESS.md`의 최근 항목을 먼저 읽고, 다른 팀원의 진행 상황·막힌 문제·필요한 설정을 파악하세요.
- `git fetch` 후 `origin/main`에 새 커밋이 있는지 확인하세요.

## 참고 문서
- DB 스키마: 실제 앱이 쓰는 테이블은 `backend/src/main/resources/schema.sql`(live 프로필에서 실행). Advisor 에이전트용 카탈로그·학생 상태 스키마 초안은 `docs/advisor-schema.sql`이며 아직 `DatabaseConfig`에 연결되지 않았습니다.
- **카탈로그 DB 현재 상태**: `docs/advisor-schema.sql` 섹션 1–6이 TigerData(앱과 같은 DB)에 만들어져 있고 Data Sciences(DTSCE) 및 University Park CMPSC_BS/CMPSC_ETM 2026–27 카탈로그가 적재되어 있습니다. CMPSC 추가 적재기는 `ingest/load_cmpsc_db.py`이며 DDL/UPDATE/DELETE 없이 동일 재실행을 검증합니다. 만든 테이블·행 수·출처(Bulletin/Handbook 판)·재적재 방법·확인 쿼리는 `docs/CHANGES-advisor-catalog-db.md`의 "DB 적재 현황" 절에 있습니다. 적재 스크립트는 `ingest/load_catalog_db.py`. 채팅은 `CatalogDbService`가 리포트 전공이 DB에 있는 전공과 일치할 때만 이 테이블을 읽어 `report.catalogDb`로 붙입니다(`docs/CHANGES-advisor-catalog-db.md` "채팅 연결" 절).
- 에이전트 도구 명세: `docs/advisor-tools.md` (도구별 입출력, 라우팅 규칙, Gemini function declaration 예시). 예시 값은 가상 데이터입니다.
- 카탈로그 시드: `data/catalog/2026-27/` (과목 `courses.json`, 전공 요건 `programs/*.json`). 생성 스크립트는 `ingest/parse_bulletin_courses.py`
- 카탈로그 작업 배경과 변경 내역: `docs/CHANGES-advisor-catalog-db.md`
- 스키마나 도구 명세를 바꾸면 위 문서를 함께 갱신하세요.
- DB에 테이블을 만들거나 데이터를 다시 적재·수정했다면 `docs/CHANGES-advisor-catalog-db.md`의 "DB 적재 현황"(범위, 행 수, 출처)을 같은 커밋에서 갱신하세요. 앱 DB와 같은 DB이므로 `DROP`/`DELETE`는 하지 말고, 적재 전 `--dry-run`으로 확인하세요.

- **Math minor**: `docs/MATH-MINOR.md` 참조. `MATH_UMNR` 2026–27 규칙은 policy_rules의 minor_audit_rules_v1 스냅샷에 저장. `MathMinorService`가 courseInventory와 대조해 참고 결과를 반환하며 전공 중복6학점/학년도/최종 승인은 별도 검토. 전용 적재기는 `ingest/load_math_minor_db.py`(먼저 --dry-run).

## 커밋·푸시 전
- `PROGRESS.md` 맨 위에 이번 작업 항목을 추가하고 같은 커밋에 포함하세요. 형식은 파일 상단 템플릿을 따릅니다.
- "상태"에는 실제로 실행한 검증만 적고, 미검증 범위를 숨기지 마세요.
- 새 환경변수가 필요하면 `backend/.env.example`과 `PROGRESS.md`의 "필요한 설정"에 이름만 적으세요. 키·비밀번호 값은 절대 커밋하지 마세요.
- 검증 결과를 자세히 남길 때는 `VERIFICATION.md`에 섹션을 추가하세요.
