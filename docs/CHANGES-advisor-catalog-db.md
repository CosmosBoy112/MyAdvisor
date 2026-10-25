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
4. (일부 완료: 채팅에 카탈로그 문맥 연결, 아래 "채팅 연결" 절) 백엔드에 `check_prerequisites`, `run_audit`부터 도구 구현 후 Gemini function calling 연결

## CMPSC 추가 수집

University Park 2026–27의 후속 수집·Handbook 학년도 충돌·명칭 매칭 범위는 [CMPSC 변경 내역](CHANGES-cmpsc-catalog.md)을 참고하세요. CMPSC 비교는 이제 앱에 연결되어 기존 reports.analysis JSONB에 저장됩니다. Advisor 전용 DB 스키마/감사 엔진 구현은 별도 작업입니다.

## DB 적재 현황 (2026-10-24)

Computational Data Sciences(DTSCE) 질문에 답할 수 있도록 카탈로그를 TigerData에 적재했습니다. 채팅은 이 테이블을 읽습니다: 아래 [채팅 연결](#채팅-연결-catalogdbservice) 절을 보세요. CMPSC_BS/CMPSC_ETM 요건도 기존 테이블에 추가 적재했습니다(아래 CMPSC 추가 적재 절). 앱의 CMPSC 비교는 여전히 JSON 기반 `CatalogService`를 사용하며 DB 조회로 바꾸지는 않았습니다. 공용 과목·선수과목 데이터와 기존 DATSC 행은 변경하지 않았습니다.

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
| `programs` / `program_versions` | 4 / 4 | `DATSC_BS`, `DATSC_ETM`, `CMPSC_BS`, `CMPSC_ETM` |
| `program_options` | 3 | `APPLIED`, `COMPUTATIONAL`, `STATISTICAL` |
| `requirement_nodes` / `requirement_rules` | 175 / 567 | DATSC 90/473 + CMPSC 85/94. CMPSC 노드 원본 메타데이터는 source_text에 JSON으로 보존 |
| `policy_rules` | 10 | DATSC 5건 + CMPSC 5건(아래 각 키 설명) |
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

### CMPSC 추가 적재 — 스키마 변경 없음

University Park · 2026–27의 `CMPSC_BS`와 `CMPSC_ETM`을 `ingest/load_cmpsc_db.py`로 추가했습니다. 기존 `load_catalog_db.py`는 DDL 및 삭제 후 재적재를 수행하므로 이번 CMPSC 작업에는 사용하지 않았습니다.

- DDL, UPDATE, DELETE, DROP 없이 INSERT와 조회만 사용합니다. 동일 데이터를 다시 실행하면 기존 트리·정책을 비교하여 새 행을 만들지 않습니다. 기존 CMPSC 데이터가 다르면 자동 교체하지 않고 롤백합니다.
- CMPSC_BS: 최소 127학점, 요건 노드 78개·규칙 87개. CMPSC_ETM: 노드 7개·규칙 7개, GPA 3.20 및 C 이상 조건.
- 연결된 75개 과목 버전은 이미 DB에 있어 재사용합니다. 공용 과목 357개, 과목 버전 337개, 선수과목 노드 880개 및 DATSC 요건/규정은 그대로 유지합니다.
- 노드의 `source_text`에는 원문 인용이 아닌 **JSON 메타데이터**임을 표시한 뒤 `source`, `requires_review`, `review`, `credits_range` 등을 보존합니다. AND/OR 자식 구조와 교차개설 대안은 요건 트리에 그대로 저장합니다.
- Department List와 승인 자연과학 목록 같은 빈 POOL은 아무 과목이나 인정한다는 뜻이 아닙니다. DB를 읽는 도구는 아래 snapshot의 검토 조건과 범위를 반드시 함께 읽어야 합니다. 이 적재가 자동 졸업 판정 엔진을 구현하는 것은 아닙니다.
- 스키마의 열/제약조건/인덱스 지문과 공용·다른 전공 테이블의 행 수 및 내용 해시를 적재 전후 대조합니다. 앱 보고서·사용자·대화 테이블은 읽거나 쓰지 않습니다.

추가한 `policy_rules` (`scope='program'`, `catalog_year='2026-27'`):

| scope_code | key | 내용 |
|---|---|---|
| CMPSC_BS | catalog_snapshot | 원본 전공 JSON 전체, 출처 해시, `automatic_audit_allowed=false`. 캠퍼스/학년도/GenEd 중복/미확인 조건 보존 |
| CMPSC_ETM | catalog_snapshot | 원본 ETM JSON 전체. 소속·graded PSU 29–55학점·AP/transfer 제외·입학 학기 보존 |
| CMPSC_BS | handbook_crosswalk | 2024–25 Handbook과 2026–27 Bulletin의 차이. `automatic_application_allowed=false`, 과거 제한은 참고 전용 |
| CMPSC_BS | course_matches | 75개 과목의 코드 매칭·출처·선수과목 검토 상태 |
| CMPSC_BS | sources | 공식 URL·수집 시각·원문 SHA-256 |

```sh
# 기존 스키마와 공용 과목이 있어야 하며, 테이블을 생성하지 않습니다.
python ingest/load_cmpsc_db.py --data data/catalog/2026-27 --dry-run
python ingest/load_cmpsc_db.py --data data/catalog/2026-27
python -m unittest discover -s ingest -p 'test_cmpsc_db.py' -v
```

```sql
SELECT p.program_code, count(DISTINCT n.id) AS nodes, count(r.id) AS rules
FROM program_versions p JOIN requirement_nodes n ON n.program_version_id=p.id
LEFT JOIN requirement_rules r ON r.node_id=n.id
WHERE p.program_code IN ('CMPSC_BS','CMPSC_ETM') AND p.catalog_year='2026-27'
GROUP BY p.program_code;
SELECT scope_code, key, value FROM policy_rules
WHERE scope_code IN ('CMPSC_BS','CMPSC_ETM') AND catalog_year='2026-27';
```

원본은 [CMPSC Bulletin](https://bulletins.psu.edu/undergraduate/colleges/engineering/computer-science-bs/) 2026–27과 [EECS Handbook 목록](https://www.eecs.psu.edu/Students/undergraduate/Computer-Science.aspx)의 2024–25 학부 Handbook입니다. 과거 Handbook을 현재 요건에 자동 적용하지 않습니다. 현재 앱의 JSON 기반 매칭 흐름은 변경하지 않았습니다.

### 채팅 연결 (`CatalogDbService`)

채팅 요청마다 `ReportController`가 `CatalogDbService.context(report, question)` 결과를 `report.catalogDb`로 붙여 Gemini/Luminal에 보냅니다. 업로드·저장된 리포트는 바꾸지 않습니다.

- **전공 일치 조건**: 리포트 `major`가 `programs`(program_type `major`)의 이름 단어를 모두 포함하고, 나머지는 학위·캠퍼스·단과대·옵션 단어뿐일 때만 일치. 프로그램 이름의 괄호(예: `(Engineering)`)는 무시. 예: `Data Sciences (BS)` → DATSC_BS, `Computer Science (BS)` → CMPSC_BS / `Comp Sci - Univ Park (BS)`(약칭), `Computer Engineering (BS)`, `Data Sciences Minor` 불일치. 새 전공을 적재하면 코드 수정 없이 대상이 됩니다. 캠퍼스는 확인하지 않습니다(CMPSC_BS는 University Park 기준 데이터).
- **status**: `matched`(학년도 일치), `matched_year_differs`(예: 리포트 Fall 2024 → 2024-25, DB 2026-27. 답변에 차이를 반드시 명시), `matched_year_unknown`, `major_not_in_catalog`(카탈로그 질문에 일반 지식으로 답하지 않음), `catalog_unavailable`(DB 오류 시 리포트만으로 답변)
- **옵션**: 전공명에 옵션 단어가 있거나, 리포트 요건 제목에 옵션 하나만 등장하면(예: "Computational Option List A") 그 옵션 요건만 포함. 모르면 전체 옵션 포함하고 옵션을 가정하지 않음
- **포함 내용**: 공통·옵션 요건 트리(과목 제목·학점), 전공 진입 요건, `policy_rules`(Handbook 출처 규칙은 판을 밝히고 어드바이저 확인 권고), 질문에 나온 과목 최대 8개의 학점·속성·교차개설·선수과목 트리·열리는 과목
- **CMPSC 적재 형식 처리**: `source_text`가 "catalog metadata (not a verbatim quote)"이면 Bulletin 원문(`bulletin_text`)으로 넘기지 않고 `requires_review`·`review`·`credits_range`·(검토 노드의) `source_url`만 전달. 규칙 없는 POOL은 `course_list: not_listed_in_database`(아무 과목이나 인정 아님). 8,000자 넘는 정책 값(`catalog_snapshot` 등)은 `value_omitted`로 표시하고 제외
- **문맥 크기**(실측): Data Sciences 약 17KB, CMPSC 약 26KB
- **규칙**(`GeminiService.CHAT_SYSTEM`): 출처 URL 인용, `unparsed_text` 선수과목은 해석하지 않고 인용, 리포트의 이수 상태·학점은 바꾸지 않음. `report.catalog`(JSON 기반 CMPSC 비교)의 기존 규칙은 그대로 유지
- 테스트: `CatalogDbServiceTest`(전공·학년도·과목 코드·DB 오류), `LiveCatalogDbTest`(`LIONPATHWAY_LIVE_TEST=1`, 실제 DB 조회 전용)


### 2026-10-24 · 앱 보고서 완료 항목 복구

카탈로그 테이블이나 스키마 변경 없음. `reports.analysis`의 기존 CS 보고서 1건에 PDF 원문에서 검증한 누락 Satisfied 섹션 28개를 추가했다(완료 19, IP 의존 6, 과목표 검증 필요 3). 기존 미완료 2개, 전체 학점 127/74 earned/30 IP, 대화 기록은 유지했다. 원본은 Git 밖의 비공개 작업 폴더에 백업했으며 기존 JSON과 일치할 때만 갱신 후 별도 연결에서 검증했다. `RequirementReconciler`는 새 PDF 업로드에도 동일한 복원을 적용한다. 완료 개수는 보고서 요건 섹션 수이며 서로 겹칠 수 있는 학점/과목을 합산한 값이 아니다.

### 2026-10-24 · 인정 과목 내역 추가

동일 앱 보고서 1건의 요건별 `appliedCourses`/`appliedCoursesVerified` 필드 보완. 원문 과목표 합계와 used가 일치하는 24요건에 30개 과목 배정(중복 포함)의 코드·제목·학기·학점·성적/IP·페이지 저장. 불확실한 표는 비워 두고 기존 상태/학점은 유지. 별도 비공개 백업, 조건부 갱신, 새 연결 대조 완료. 카탈로그/DB 스키마 변경 없음.


### 2026-10-24 · Math minor 참고 요건 및 과목 내역

`MATH_UMNR` minor 프로그램 1건, 2026–27 버전 1건(최소26학점), `minor_audit_rules_v1` 정책 스냅샷 1건을 INSERT로 추가했다. 4개 요건 그룹·성적·중복/개별 학점 제한·출처 충돌은 policy JSON에 보관하며 requirement_nodes에는 별도 복제하지 않았다. 기존 테이블/스키마/카탈로그 행은 수정하지 않았다. 전용 `ingest/load_math_minor_db.py --dry-run` 롤백 → 실제 적재 → 재실행 no-op 및 스키마 지문 동일 확인. `MathMinorService`가 DB 스냅샷을 읽어 Math minor 비교를 계산하며 자세한 범위는 `docs/MATH-MINOR.md` 참조.

기존 사용자 CS 보고서 1건에 제공 PDF에서 추출한 courseInventory(37개 과목/학기별 수강 내역, 반복 요건 표시는 중복 제거)를 추가했다. 전체 학점/30개 요건/대화 기록은 유지. 원본 Git 밖 비공개 백업 및 조건부 UPDATE 후 새 연결에서 대조했다. 이 PDF는 2023년 요건이므로 2026–27 마이너 비교는 참고값이다.
