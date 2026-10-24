# 변경 가이드: Advisor 카탈로그 DB 초안 (`advisor-catalog-db`)

> **TigerData에 실제로 적재된 카탈로그 DB의 현재 상태**(만든 테이블, 행 수, 출처, 재적재 방법, 확인 쿼리)는 맨 아래 [DB 적재 현황](#db-적재-현황-2026-10-24) 절을 보세요.

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
- 2026-10-24 갱신: 섹션 1–6 실행과 DATSC 시드 적재 완료 ([DB 적재 현황](#db-적재-현황-2026-10-24)). `review` 수동 검토와 섹션 7–12 실행은 여전히 미검증

## 다음 할 일

1. ~~TigerData 개발 DB에서 `advisor-schema.sql` 실행 확인~~ → 섹션 1–6만 실행 완료 (2026-10-24)
2. `courses.json`의 `review` 137건 검토 후 시드 로더 작성 (`courses` / `course_versions` / `prereq_nodes`) → 로더 작성·적재 완료. **`review` 검토는 아직 안 함** (TEXT 노드 그대로 적재)
3. ~~`DATSC_BS.json` → `requirement_nodes` / `requirement_rules` 적재~~ → 완료 (2026-10-24)
4. 백엔드에 `check_prerequisites`, `run_audit`부터 도구 구현 후 Gemini function calling 연결

## CMPSC 추가 수집

University Park 2026–27의 후속 수집·Handbook 학년도 충돌·명칭 매칭 범위는 [CMPSC 변경 내역](CHANGES-cmpsc-catalog.md)을 참고하세요. CMPSC 비교는 이제 앱에 연결되어 기존 reports.analysis JSONB에 저장됩니다. Advisor 전용 DB 스키마/감사 엔진 구현은 별도 작업입니다.

## DB 적재 현황 (2026-10-24)

Computational Data Sciences(DTSCE) 질문에 답할 수 있도록 카탈로그를 TigerData에 적재했습니다. **앱은 아직 이 테이블을 읽지 않습니다**(위 "다음 할 일" 4번). CMPSC는 별도 방식([CMPSC 변경 내역](CHANGES-cmpsc-catalog.md), JSON 기반 `CatalogService`)이며 DB에는 CMPSC 요건 트리를 넣지 않았습니다. 과목 정보(`courses.json`)에는 CMPSC 과목도 포함되어 함께 적재됩니다.

### 만든 범위

- `docs/advisor-schema.sql` **섹션 1–6만** 실행했습니다 (기준 데이터, 과목, 프로그램, 개설, 학점 인정, 규정). 모두 `CREATE ... IF NOT EXISTS`입니다.
- 섹션 7–12(학생 기록, 계획, 감사 캐시, 대화, 실행 로그 hypertable, pgvector)는 **만들지 않았습니다**. 앱용 `app_users` / `reports` / `chat_messages`는 건드리지 않습니다.
- 대상 DB: `backend/.env`의 `TIGER_DATABASE_URL` (앱과 같은 DB)

### 적재 결과

| 테이블 | 행 수 | 내용 |
|---|---|---|
| `catalog_years` | 1 | `2026-27` (시작·종료일은 Summer 2026 입학 기준의 근사값) |
| `grades` | 12 | A ~ F 평점(Senate Policy 47-60), SA / UN / W |
| `courses` | 357 | 과목 코드. 선수과목·교차개설·요건 규칙에만 등장하는 코드 포함 |
| `course_versions` | 337 | 2026-27 제목·학점·설명·선수과목 원문. 제목/학점이 확인되지 않은 참조 과목 20개는 `courses`에만 있음 |
| `course_attributes` | 122 | GenEd 등 속성 (GN, GQ, GS, WAC, GWS, ...) |
| `course_crosslists` | 25 | 교차개설 쌍 |
| `prereq_nodes` | 880 | 선수과목 트리. 파싱 못 한 33건은 `TEXT` 노드(`raw_text`에 원문) |
| `programs` / `program_versions` | 2 / 2 | `DATSC_BS`, `DATSC_ETM` |
| `program_options` | 3 | `APPLIED`, `COMPUTATIONAL`, `STATISTICAL` |
| `requirement_nodes` / `requirement_rules` | 90 / 473 | 요건 트리와 규칙. JSON의 `notes`는 `source_text` 뒤에 `Note:`로 붙임 |
| `policy_rules` | 5 | 아래 표 |
| `terms`, `course_offerings`, `credit_*`, `academic_calendar`, `course_restrictions` | 0 | 테이블만 생성 |

### 출처

| 자료 | 판 | 쓰인 곳 |
|---|---|---|
| [Bulletin: Data Sciences, B.S. (Engineering)](https://bulletins.psu.edu/undergraduate/colleges/engineering/data-sciences-bs/) | 2026-27 | 전공 요건, List A/B(권장 계획 각주), 권장 학기 계획, 자연과학 제외 목록 |
| [Bulletin 과목 설명](https://bulletins.psu.edu/university-course-descriptions/undergraduate/) | 2026-27 | 과목·선수과목. 2026-10-24에 43개 과목 페이지를 재수집해 기존 시드와 대조: 내용 동일, 제목이 비어 있던 참조 과목 14개만 채움 |
| [DTSCE Handbook](https://www.eecs.psu.edu/assets/docs/handbooks/DTSCE-handbook-2024-2025.pdf) | **2024-25** | Department List 제외·상한, FYS 대체, 대체 인정 과목 |

2026-10-24 기준 EECS 사이트에 공개된 DTSCE Handbook은 2024-25판이 최신입니다(2025-26 / 2026-27판 없음). Bulletin의 "LIST OF COMPUTATIONAL DATA SCIENCES COURSES" 링크도 이 페이지로 연결됩니다. Handbook에서 온 규칙은 `description`에 "2026-27 적용은 어드바이저 확인 필요"를 명시했습니다.

Handbook과 Bulletin이 다른 부분:
- 졸업 학점: Handbook "at least 126" / Bulletin 2026-27 **123** → 요건은 Bulletin 값 사용, Handbook 문구는 `department_list_rules.value.note`에 보존
- List A/B: Handbook에만 있는 CMPSC/MATH 467, DS 300, MATH 436, MATH 465, IST 441은 넣지 않음. Bulletin 2026-27 목록(DS 441, MATH 486 포함) 사용
- 자연과학 제외: Handbook에만 ASTRO 6, GAME 180N 있음 → Bulletin 목록 사용, 차이는 `description`에 기록

### `policy_rules` 키 (`scope='program'`, `scope_code='DATSC_BS'`, `catalog_year='2026-27'`)

| key | 출처 | value |
|---|---|---|
| `suggested_academic_plan.COMPUTATIONAL` | Bulletin 2026-27 | 8학기 과목·학점(합계 123, 학기별 합계 검산), `c_required`, `list_a`, `list_b` |
| `natural_science_exclusions` | Bulletin 2026-27 | GN 9학점, 제외 과목 |
| `department_list_rules` | Handbook 2024-25 | 6학점, 상한(ROTC 3 등), 제외 과목·범위·범주 |
| `first_year_seminar_makeup` | Handbook 2024-25 | 별도 FYS가 없을 때 1학점 대체와 청원 URL |
| `communications_substitutions` | Handbook 2024-25 | ENGL 15, CAS 100, MATH 230 대체 과목 |

### 다시 적재하기

```sh
python -m pip install -r ingest/requirements.txt
# backend/.env 의 TIGER_DATABASE_* 를 읽습니다 (환경변수가 있으면 우선)
python ingest/load_catalog_db.py --data data/catalog/2026-27 --programs DATSC_BS DATSC_ETM --dry-run   # 넣어 보고 롤백
python ingest/load_catalog_db.py --data data/catalog/2026-27 --programs DATSC_BS DATSC_ETM
```

- 한 트랜잭션으로 실행합니다. 다시 실행하면 해당 학년도의 과목 버전·선수과목 트리·요건 트리·규정을 교체합니다(`courses` 코드는 남김).
- 규정 파일 재생성: Bulletin 페이지를 HTML로 저장한 뒤 `python ingest/build_datsc_policies.py PAGE.html data/catalog/2026-27/policies/DATSC_BS.json`. Handbook 규칙은 스크립트 안에 옮겨 적은 값이므로 새 Handbook이 나오면 직접 대조해 고쳐야 합니다.

### TigerData 콘솔에서 확인

[console.cloud.timescale.com](https://console.cloud.timescale.com) → 서비스(`.env` 호스트의 첫 부분이 서비스 ID) → SQL editor. 조회만 할 때는 `SELECT`만 사용하세요(앱 DB와 같음).

```sql
-- Computational 옵션 요건 트리
WITH RECURSIVE t AS (
  SELECT n.id, n.label, n.node_type, n.min_credits, 0 AS d
  FROM requirement_nodes n JOIN program_options o ON o.id = n.option_id
  WHERE o.code = 'COMPUTATIONAL' AND n.parent_id IS NULL
  UNION ALL
  SELECT c.id, c.label, c.node_type, c.min_credits, d + 1 FROM requirement_nodes c JOIN t ON c.parent_id = t.id)
SELECT repeat('  ', d) || label, node_type, min_credits FROM t ORDER BY id;

-- 선수과목: CMPSC 360 을 들으면 열리는 과목
SELECT DISTINCT v.course_code, v.title
FROM prereq_nodes p JOIN course_versions v ON v.id = p.course_version_id
WHERE p.required_course = 'CMPSC 360';

-- 권장 학기 계획 / 규정 출처
SELECT jsonb_pretty(value -> 'terms') FROM policy_rules WHERE key = 'suggested_academic_plan.COMPUTATIONAL';
SELECT key, description, source_url FROM policy_rules ORDER BY key;
```
