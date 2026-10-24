# 변경 가이드: Advisor 카탈로그 DB 초안 (`advisor-catalog-db`)

이번 푸시에서 무엇이 추가·변경됐는지, 기존 코드와 어떻게 연결되는지 정리한 문서입니다.

## 한 줄 요약

앱 코드(백엔드·프런트)는 **변경 없음**. Advisor 에이전트용 DB 스키마 초안, 도구 명세, 2026-27 Bulletin 카탈로그 시드 데이터, 파서 스크립트를 **새 파일로만** 추가했습니다.

## 브랜치 구성

| 기반 | 내용 |
|---|---|
| `origin/main` (`5f4b4a9`) | 최신 UI·Luminal 라우팅·다크모드까지 포함 |
| `origin/backend` 병합 (`f60680d`) | `AGENTS.md`, `CLAUDE.md`, `PROGRESS.md` 추가. `main`에 아직 없던 커밋 |
| 이번 커밋 | 아래 "추가된 파일" |

`VERIFICATION.md` 병합 충돌은 양쪽 섹션("Luminal 라우팅 추가", "Luminal 실제 연결 검증")을 모두 남기는 방식으로 해결했습니다.

## 추가된 파일

| 경로 | 설명 |
|---|---|
| `docs/advisor-schema.sql` | Advisor DB 스키마 초안 (PostgreSQL / TigerData) |
| `docs/advisor-tools.md` | 에이전트가 호출할 도구 명세, 라우팅 규칙, Gemini function declaration 예시 |
| `data/catalog/2026-27/courses.json` | 43개 과목군, 302개 과목 시드 |
| `data/catalog/2026-27/programs/DATSC_BS.json` | Data Sciences B.S. 요건 트리 (123학점, APPLIED / COMPUTATIONAL / STATISTICAL 옵션) |
| `data/catalog/2026-27/programs/DATSC_ETM.json` | Data Sciences Entrance to Major 요건 |
| `ingest/parse_bulletin_courses.py` | 저장한 Bulletin 과목 페이지 HTML → `courses.json` 파서 |
| `docs/CHANGES-advisor-catalog-db.md` | 이 문서 |

## 1. DB 스키마 (`docs/advisor-schema.sql`)

**아직 앱에 연결되지 않았습니다.** `DatabaseConfig`는 계속 `backend/src/main/resources/schema.sql`만 실행합니다. 기존 `reports` 관련 테이블과는 별개의 초안입니다.

| 섹션 | 주요 테이블 | 용도 |
|---|---|---|
| 1. 기준 데이터 | `catalog_years`, `terms`, `grades` | 학년도·학기·성적 순위 |
| 2. 과목 | `courses`, `course_versions`, `course_attributes`, `course_crosslists`, `prereq_nodes` | 학년도별 과목 정보, GenEd 속성, 선수과목 트리 |
| 3. 프로그램 | `programs`, `program_versions`, `program_options`, `requirement_nodes`, `requirement_rules`, `course_restrictions` | 전공 요건 트리와 규칙 |
| 4. 개설 | `course_offerings` | 학기별 실제 개설 정보 |
| 5. 학점 인정 | `credit_sources`, `credit_equivalencies` | AP/IB·편입 학점 인정표 |
| 6. 규정 | `policy_rules`, `academic_calendar` | 수치 규정, 학사 일정 |
| 7. 사용자 | `app_users`, `student_profiles`, `student_programs`, `student_courses`, `student_exceptions` | 학생 기록 |
| 8–9. 계획·감사 | `plans`, `plan_courses`, `audit_snapshots` | 수강 계획, audit 캐시 |
| 10. 대화·메모 | `conversations`, `conversation_messages`, `student_memories` | 대화 기록, 장기 메모 |
| 11. 실행 기록 | `agent_runs`, `agent_steps` | TimescaleDB hypertable, 90일 보존 |
| 12. 규정 검색 | `policy_chunks` (주석 처리) | pgvector 필요, 선택 사항 |

주의: 11번 섹션은 `timescaledb` 확장이 필요합니다(Tiger Cloud에는 기본 설치). 로컬 PostgreSQL에서 실행 검증은 아직 하지 않았습니다.

## 2. 도구 명세 (`docs/advisor-tools.md`)

`advisor-schema.sql` 기반으로 에이전트 도구를 정의합니다.

- 카탈로그 조회: `search_courses`, `get_course`, `search_programs`, `get_program`, `get_offerings`
- 룰 엔진 계산: `get_student_record`, `check_prerequisites`, `run_audit`, `what_if`, `find_courses_for_requirement`, `validate_plan`
- 학점: `get_credit_summary`, `gpa_what_if`, `lookup_credit_equivalency`
- 규정: `get_policy_rule`, `get_deadlines`, `search_policy`
- 기타: `web_search`, `save_memory`, `forget_memory`, 승인이 필요한 쓰기 도구 4개

핵심 원칙은 "학점·GPA·요건·수강 가능 여부는 LLM이 직접 세지 않고 계산 도구 결과로 답한다"입니다 (11절 라우팅 규칙). 문서 안 예시 값은 형식 설명용 가상 값입니다.

## 3. 카탈로그 데이터 (`data/catalog/2026-27/`)

`courses.json`의 각 과목에는 `status`와 `review`가 있습니다. **DB에 넣기 전 `review` 항목을 사람이 확인해야 합니다.**

| `status` | 개수 | 의미 |
|---|---|---|
| `auto_parsed` | 166 | 선수과목을 트리로 자동 파싱 |
| `reference_only` | 73 | 참고용으로만 포함 |
| `text_only` | 27 | 문법에 맞지 않아 원문 TEXT 노드로 유지 |
| `no_prereq` | 23 | 선수과목 없음 |
| `not_in_course_list` | 13 | Bulletin 페이지에 없어 fallback 제목·학점만 사용 |

`review`가 붙은 과목: 137개 (예: "Could not parse prerequisite ...; kept as TEXT.").

## 4. 파서 (`ingest/parse_bulletin_courses.py`)

```bash
python ingest/parse_bulletin_courses.py \
  --pages DIR --codes CODES.json --catalog-year 2026-27 --out courses.json
```

- `DIR`: `https://bulletins.psu.edu/university-course-descriptions/undergraduate/<subject>/`를 `c_<subject>.html`로 저장한 폴더
- `CODES.json`: `{"codes": [...], "fallback_titles": {"CODE": [title, credits]}}`
- 표준 라이브러리만 사용 (추가 패키지 없음)
- 입력 HTML 폴더와 `CODES.json`은 이번 커밋에 포함되지 않았습니다

## 기존 코드에 미치는 영향

- 백엔드·프런트 소스, `pom.xml`, `package.json`, `.env.example`: 변경 없음
- 새 환경변수: 없음
- 기존 테스트·빌드 동작: 영향 없음 (새 파일은 빌드 경로 밖)

## 검증 상태

- 확인함: JSON 3개 파싱, 파서 `py_compile` 통과, 비밀값 미포함 확인
- 미검증: `advisor-schema.sql` 실제 DB 실행, 시드 데이터 적재, `review` 항목 수동 검토

## 다음 할 일

1. TigerData 개발 DB에서 `advisor-schema.sql` 실행 확인
2. `courses.json`의 `review` 137건 검토 후 시드 로더 작성 (`courses` / `course_versions` / `prereq_nodes`)
3. `DATSC_BS.json` → `requirement_nodes` / `requirement_rules` 적재
4. 백엔드에 `check_prerequisites`, `run_audit`부터 도구 구현 후 Gemini function calling 연결

## CMPSC 추가 수집

University Park 2026–27의 후속 수집·Handbook 학년도 충돌·명칭 매칭 범위는 [CMPSC 변경 내역](CHANGES-cmpsc-catalog.md)을 참고하세요. 앱/DB 연결은 아직 별도 작업입니다.
