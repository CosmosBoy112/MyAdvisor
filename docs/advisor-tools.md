# Advisor Agent 도구 명세 (초안)

`docs/advisor-schema.sql` 기반. 유저 쿼리 → 문맥 조립 → 에이전트 ⇄ 도구 → 출력 검증 → 응답 구조에서 에이전트가 호출하는 도구 목록과 입출력 형식입니다.

예시 값(과목, 선수과목, 학점, 규정 수치, 날짜)은 형식을 보여주기 위한 가상 값이며 실제 Bulletin과 대조하지 않았습니다.

## 목차

| 분류 | 도구 |
|---|---|
| 자동 문맥 | (도구 아님) 프로필, 전공, audit 요약, 장기 메모 |
| 카탈로그 조회 | `search_courses`, `get_course`, `search_programs`, `get_program`, `get_offerings` |
| 기록·계산 | `get_student_record`, `check_prerequisites`, `run_audit`, `what_if`, `find_courses_for_requirement`, `validate_plan` |
| 학점 | `get_credit_summary`, `gpa_what_if`, `lookup_credit_equivalency` |
| 학교 규정 | `get_policy_rule`, `get_deadlines`, `search_policy` |
| 외부 검색 | `web_search`, `search_internal_docs` (미정) |
| 메모 | `save_memory`, `forget_memory` |
| 쓰기 (승인 필요) | `save_plan`, `update_student_record`, `set_program_goal`, `update_profile` |
| 실행 기록 | (도구 아님) 서버가 `agent_runs`, `agent_steps`에 자동 기록 |

## 0. 공통 규칙

**식별자 형식**

| 항목 | 형식 | 예 |
|---|---|---|
| 과목 | `SUBJECT NUMBER` | `STAT 414`, `CMPSC 497A` |
| 학기 | `FA` / `SP` / `SU` + 연도 2자리 | `FA26` |
| 학년도 | `YYYY-YY` | `2025-26` |
| 프로그램 | 대문자 코드 | `DATSC_BS`, `STAT_MINOR`, `DATSC_ETM` |

**유저 식별**: 어떤 도구도 유저 ID를 인자로 받지 않습니다. 서버가 JWT `sub`로 주입합니다. LLM이 다른 유저 데이터에 접근할 경로를 원천 차단합니다.

**학년도 기본값**: `catalog_year`를 생략하면 유저 프로필의 학년도, 프로필이 없으면 현재 학년도를 사용합니다.

**응답 봉투**: 모든 도구가 같은 형태로 응답합니다.

```json
{
  "ok": true,
  "data": { },
  "sources": [
    { "type": "catalog", "ref": "course_versions:STAT 414@2025-26", "url": "https://bulletins.psu.edu/...", "catalog_year": "2025-26" }
  ],
  "warnings": ["SP27 개설 정보는 아직 수집되지 않았습니다."]
}
```

```json
{ "ok": false, "error": { "code": "NOT_FOUND", "message": "Unknown course: STAT 999" } }
```

- `sources[].type`: `catalog` | `offering` | `record` | `audit` | `equivalency` | `policy_rule` | `calendar` | `policy` | `web`
- 출력 검증 단계는 답변의 수치·과목 코드·날짜가 이번 턴의 도구 결과(또는 자동 문맥)에 존재하는지, 근거가 `sources`에 있는지 확인합니다.

**에러 코드**

| 코드 | 의미 | 에이전트 대응 |
|---|---|---|
| `INVALID_ARGUMENT` | 형식 오류, 범위 초과 | 인자 수정 후 1회 재시도 |
| `NOT_FOUND` | 과목·프로그램·계획·규정 없음 | `search_*`로 후보 확인 |
| `DATA_NOT_AVAILABLE` | 해당 학기/학년도 데이터 미수집 | 유저에게 미확인이라고 명시 |
| `PROFILE_INCOMPLETE` | 학년도·전공 등 필수 프로필 없음 | 유저에게 질문 |
| `RATE_LIMITED` | 호출 한도 초과 | 중단하고 안내 |

**호출 한도**: 한 턴에 도구 호출 최대 8회, 목록형 결과는 최대 20개.

**판정 값**: 확실하지 않으면 추측하지 않고 `unknown`을 반환합니다. 예) 파싱 못 한 선수과목 문장(`TEXT` 노드), 매칭 안 된 이전 학점, 해석 못 한 수강 제한(`text` 제한).

**규정 범위 우선순위**: 같은 `key`의 `policy_rules`가 여러 범위에 있으면 program > college > university 순으로 적용하고, 적용한 범위를 결과에 표시합니다.

---

## 1. 자동 문맥 (도구 아님)

매 턴 프롬프트에 서버가 넣는 내용입니다. 이것만으로 답할 수 있으면 도구를 호출하지 않습니다.

```json
{
  "profile": { "catalog_year": "2024-25", "campus": "UP", "college": "IST", "entry_term": "FA24", "expected_grad_term": "SP28", "max_credits_per_term": 18 },
  "programs": [
    { "code": "DATSC_BS", "option": "APPLIED", "status": "declared" },
    { "code": "STAT_MINOR", "option": null, "status": "exploring" }
  ],
  "current_term": "FA26",
  "record_summary": { "completed_credits": 58, "in_progress_credits": 15, "semester_standing": 4, "cumulative_gpa": 3.41, "unmatched_items": 1 },
  "audit_summary": [
    { "program": "DATSC_BS", "remaining_credits": 47, "unsatisfied": ["Supporting Courses", "GN"], "snapshot_id": 812 }
  ],
  "upcoming_deadlines": [ { "event": "late_drop", "label": "Late drop deadline", "date": "2026-11-13" } ],
  "memories": [
    { "id": 4, "category": "preference", "content": "8시 수업은 피하고 싶음" },
    { "id": 7, "category": "goal", "content": "데이터 엔지니어 진로 희망" }
  ],
  "primary_plan_id": "1f6c..."
}
```

`upcoming_deadlines`는 현재 학기 `academic_calendar`에서 14일 이내 항목만 넣습니다.

---

## 2. 카탈로그 조회

### `search_courses`
자연어/조건으로 과목 찾기.

| 입력 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `query` | string | | 제목·설명 키워드 |
| `subject` | string | | `STAT` |
| `level_min`, `level_max` | int | | `400`, `499` |
| `attributes` | string[] | | `["GQ"]`, 모두 만족(AND) |
| `offered_term` | string | | 해당 학기 개설 과목만 |
| `catalog_year` | string | | |
| `limit` | int | | 기본 10, 최대 20 |

입력 중 최소 하나는 있어야 합니다.

```json
{ "courses": [
  { "code": "STAT 414", "title": "Introduction to Probability Theory", "credits": [3, 3], "level": 400, "attributes": [],
    "typical_terms": ["FA", "SP"], "offered_in_term": "yes" }
], "total": 1 }
```

`offered_in_term`: `yes`(수집된 개설 확인) | `no`(수집됐지만 없음) | `predicted`(미수집, `typical_terms` 기준 예상) | `unknown`

테이블: `courses`, `course_versions`, `course_attributes`, `course_offerings`

### `get_course`
과목 상세, 선수과목 트리, 수강 제한, 이 과목이 열어주는 과목.

| 입력 | 타입 | 필수 |
|---|---|---|
| `code` | string | ✓ |
| `catalog_year` | string | |

```json
{
  "code": "STAT 414", "title": "...", "credits": [3, 3], "description": "...",
  "attributes": [], "crosslists": [], "repeatable": false, "typical_terms": ["FA", "SP"],
  "prereq": {
    "text": "MATH 230 or MATH 231; concurrent: STAT 200",
    "tree": { "type": "ALL", "children": [
      { "type": "ANY", "children": [
        { "type": "COURSE", "course": "MATH 230", "min_grade": null, "concurrent_ok": false },
        { "type": "COURSE", "course": "MATH 231", "min_grade": null, "concurrent_ok": false }
      ] },
      { "type": "COREQ", "course": "STAT 200" }
    ] }
  },
  "restrictions": [
    { "type": "min_semester", "include": true, "min_semester": 5, "text": "Fifth-semester standing or higher" }
  ],
  "unlocks": ["STAT 415", "STAT 416"]
}
```

테이블: `course_versions`, `course_attributes`, `course_crosslists`, `prereq_nodes`, `course_restrictions`

### `search_programs`
"통계 마이너", "데이터 사이언스 전공 진입 요건" 같은 표현을 프로그램 코드로 변환.

| 입력 | 타입 | 필수 |
|---|---|---|
| `query` | string | ✓ |
| `program_type` | `major` \| `minor` \| `certificate` \| `gened` \| `entrance` | |

```json
{ "programs": [
  { "code": "STAT_MINOR", "name": "Statistics Minor", "type": "minor", "college": "Science", "entrance_for": null },
  { "code": "DATSC_ETM", "name": "Data Sciences Entrance to Major", "type": "entrance", "college": "IST", "entrance_for": "DATSC_BS" }
] }
```

### `get_program`
요건 트리 구조.

| 입력 | 타입 | 필수 |
|---|---|---|
| `program_code` | string | ✓ |
| `option_code` | string | |
| `catalog_year` | string | |
| `depth` | int | 기본 전체 |

```json
{
  "program": { "code": "DATSC_BS", "name": "Data Sciences, B.S.", "type": "major", "catalog_year": "2024-25", "min_total_credits": 120,
               "entrance_program": "DATSC_ETM" },
  "options": [ { "code": "APPLIED", "name": "Applied Data Sciences" } ],
  "requirements": [
    { "node_id": 301, "label": "Supporting Courses", "type": "POOL", "min_credits": 9, "min_grade": "C", "exclusive": true,
      "rules": "400-level STAT or CMPSC, excluding STAT 497", "source_text": "...", "children": [] },
    { "node_id": 330, "label": "Major GPA", "type": "GPA_MIN", "min_gpa": 2.0,
      "rules": "All courses in DS, STAT, CMPSC", "source_text": "...", "children": [] }
  ]
}
```

- `rules`는 `requirement_rules`를 사람이 읽을 수 있는 문장으로 요약한 값입니다.
- `entrance_program`은 `programs.entrance_for`로 이 전공을 가리키는 ETM 프로그램입니다.

### `get_offerings`
학기별 개설 섹션.

| 입력 | 타입 | 필수 |
|---|---|---|
| `course_codes` | string[] | ✓ (최대 10) |
| `term_code` | string | ✓ |
| `campus` | string | 기본 프로필 캠퍼스 |

```json
{ "term": "SP27", "fetched_at": "2026-10-20T04:00:00Z", "sections": [
  { "course": "STAT 414", "section": "001", "instructor": "...", "meetings": [ { "days": "MWF", "start": "10:10", "end": "11:00" } ], "capacity": 120, "enrolled": 97 }
], "not_offered": ["STAT 416"] }
```

수집하지 않은 학기는 `DATA_NOT_AVAILABLE`이고, `data.predicted`에 `typical_terms` 기준 예상(`{ "STAT 414": true }`)을 담습니다. "개설 안 됨"(`not_offered`), "예상"(`predicted`), "모름"을 구분합니다.

---

## 3. 학생 기록과 계산 (룰 엔진)

### `get_student_record`

| 입력 | 타입 | 필수 |
|---|---|---|
| `status` | (`completed` \| `in_progress` \| `transfer` \| `exam`)[] | 기본 전체 |
| `term_code` | string | |

```json
{ "courses": [
  { "id": 51, "code": "STAT 200", "term": "FA24", "status": "completed", "grade": "A-", "credits": 4 },
  { "id": 63, "code": null, "raw_label": "STAT 2XX", "status": "transfer", "credits": 3, "equivalency_id": 1204 }
], "totals": { "completed": 58, "in_progress": 15, "transfer": 3 } }
```

### `check_prerequisites`
수강 가능 여부. 선수과목, 동시수강, 수강 제한을 함께 평가합니다.

| 입력 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `course_code` | string | ✓ | |
| `term_code` | string | | 이 학기에 듣는다고 가정. 기본 다음 학기 |
| `assume_completed` | string[] | | 그 전까지 추가로 들었다고 가정 |
| `same_term` | string[] | | 같은 학기에 함께 듣는다고 가정 (동시수강 판정용) |
| `plan_id` | string | | 계획의 이전 학기 과목을 이수로, 같은 학기 과목을 동시수강으로 가정 |

```json
{
  "course": "STAT 414", "term": "SP27",
  "eligible": "no",
  "tree": { "type": "ALL", "status": "unmet", "children": [
    { "type": "ANY", "status": "met", "children": [
      { "type": "COURSE", "course": "MATH 230", "status": "met", "by": { "record_id": 72, "grade": "B" } },
      { "type": "COURSE", "course": "MATH 231", "status": "unmet" }
    ] },
    { "type": "COREQ", "course": "STAT 200", "status": "met", "by": { "record_id": 51 } }
  ] },
  "restrictions": [
    { "type": "min_semester", "status": "unmet", "detail": "Requires semester 5, projected semester 4 in SP27" }
  ],
  "missing": [ "Fifth-semester standing" ]
}
```

- `eligible`: `yes` | `no` | `unknown`. 트리와 제한이 모두 `met`이어야 `yes`.
- 노드 `status`: `met` | `unmet` | `in_progress` | `same_term` | `unknown`
- 학기 standing은 해당 학기 시작 시점의 예상 학점과 `policy_rules`의 `semester_classification`으로 계산합니다.
- 수강 중 과목은 `in_progress`로 표시하고, 학기가 끝나야 충족되는 경우 `warnings`에 남깁니다.

### `run_audit`
요건별 충족 현황. Entrance to Major도 같은 도구로 확인합니다(`program_code`에 ETM 코드).

| 입력 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `program_code` | string | | 기본 declared 전공 |
| `option_code` | string | | 기본 프로필 옵션 |
| `plan_id` | string | | 계획 과목까지 반영 |
| `count_in_progress` | bool | | 기본 `true`. `false`면 이수 완료만 |
| `node_id` | int | | 특정 요건만 |

```json
{
  "program": "DATSC_BS", "option": "APPLIED", "catalog_year": "2024-25", "snapshot_id": 812,
  "overall": "in_progress",
  "credits": { "completed": 58, "in_progress": 15, "planned": 0, "required": 120, "remaining": 47 },
  "nodes": [
    { "node_id": 301, "label": "Supporting Courses", "status": "partial",
      "applied": [ { "code": "STAT 414", "credits": 3, "status": "in_progress" } ],
      "remaining": { "count": null, "credits": 6 } },
    { "node_id": 310, "label": "GN", "status": "unsatisfied", "applied": [], "remaining": { "count": null, "credits": 6 } },
    { "node_id": 330, "label": "Major GPA", "status": "satisfied", "gpa": { "current": 3.18, "min": 2.0, "graded_credits": 31 } }
  ],
  "unknown_items": [ { "record_id": 63, "raw_label": "STAT 2XX", "reason": "generic transfer credit; matched by subject and level only" } ]
}
```

- 노드 `status`: `satisfied` | `partial` | `unsatisfied` | `unknown`
- `in_progress` 학점은 `completed`에 합산하지 않습니다. `GPA_MIN`은 성적이 확정된 과목만 계산합니다.
- 이전·시험 학점은 `credit_equivalencies`로 매칭된 `psu_course_code` 또는 `psu_label`(과목 접두사·레벨)로 규칙에 대입합니다.
- 결과는 `audit_snapshots`에 입력 해시와 함께 캐시합니다.

### `what_if`
가정 변경 전후 비교. 아무것도 저장하지 않습니다.

| 입력 | 타입 | 필수 |
|---|---|---|
| `add_courses` | `{code, term, grade?}[]` | |
| `remove_courses` | string[] | |
| `add_programs` | `{code, option?}[]` | |
| `remove_programs` | string[] | |
| `switch_option` | `{program, option}` | |
| `base_plan_id` | string | |

입력 중 최소 하나는 있어야 합니다. `grade`를 주면 `GPA_MIN` 노드에도 반영합니다.

```json
{ "programs": [
  { "program": "STAT_MINOR",
    "before": { "remaining_credits": 12, "unsatisfied_nodes": 3 },
    "after":  { "remaining_credits": 9,  "unsatisfied_nodes": 2 },
    "newly_satisfied": [ { "node_id": 402, "label": "STAT 414 or STAT 418" } ],
    "newly_unsatisfied": [] }
], "issues": [
  { "type": "prereq_unmet", "course": "STAT 415", "term": "SP27", "detail": "STAT 414 must be completed first" },
  { "type": "credit_overload", "term": "SP27", "detail": "19 credits > max 18" }
] }
```

`issues[].type`: `prereq_unmet` | `coreq_unmet` | `restricted` | `credit_overload` | `not_offered` | `offering_predicted` | `offering_unknown` | `duplicate` | `double_count_conflict`

`credit_overload` 기준은 프로필 `max_credits_per_term`이고, `policy_rules`의 `max_credits_without_approval`을 넘으면 승인 필요라고 별도로 표시합니다.

### `find_courses_for_requirement`
"이 요건 채우려면 뭘 들어야 해?" 추천의 핵심 도구.

| 입력 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `program_code` | string | ✓ | |
| `node_id` | int | ✓ | `run_audit` / `get_program` 결과에서 |
| `term_code` | string | | 이 학기 개설·수강 가능 여부 포함 |
| `eligible_only` | bool | | 기본 `false` |
| `limit` | int | | 기본 10, 최대 20 |

```json
{ "node": { "node_id": 310, "label": "GN", "remaining": { "credits": 6 } },
  "candidates": [
    { "code": "BIOL 110", "title": "...", "credits": [4, 4],
      "eligible": "yes", "offered": "predicted",
      "also_satisfies": [ { "program": "GENED", "node_id": 901, "label": "Integrative Studies" } ] }
  ] }
```

- 정렬: 수강 가능 → 개설 확인(`yes` > `predicted` > `unknown`) → 다른 요건도 함께 채우는 과목 순.
- `eligible`은 `check_prerequisites`와 같은 판정(선수과목, 동시수강, 수강 제한)입니다.
- `also_satisfies`는 노드의 `exclusive` 설정을 존중합니다.
- 장기 메모(예: "8시 수업 피하고 싶음")는 도구가 아니라 에이전트가 결과를 설명할 때 반영합니다.

### `validate_plan`
학기별 계획 검사와 졸업 예상 학기.

| 입력 | 타입 | 필수 |
|---|---|---|
| `plan_id` | string | 둘 중 하나 |
| `terms` | `{term, courses[]}[]` | 둘 중 하나 (저장 안 된 초안) |

```json
{ "terms": [
  { "term": "SP27", "credits": 16, "issues": [] },
  { "term": "FA27", "credits": 19, "issues": [
    { "type": "credit_overload", "detail": "19 > 18" },
    { "type": "offering_predicted", "course": "STAT 416", "detail": "FA27 not collected; usually offered SP only" }
  ] }
], "projected_completion": { "DATSC_BS": "SP28", "STAT_MINOR": "FA27" },
  "unmet_after_plan": [ { "program": "DATSC_BS", "node_id": 310, "label": "GN", "remaining_credits": 3 } ] }
```

---

## 4. 학점

### `get_credit_summary`
학점·GPA 현황. 자동 문맥의 `record_summary`보다 자세한 분해가 필요할 때 호출합니다.

| 입력 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `group_by` | (`status` \| `level` \| `subject` \| `attribute` \| `term`)[] | | 기본 `["status"]` |
| `gpa_scopes` | `{label, subjects?[], program_code?, node_id?}[]` | | 추가로 계산할 GPA 범위 (예: 전공 GPA) |

```json
{
  "credits": { "completed": 58, "in_progress": 15, "transfer": 3, "exam": 4, "total_earned": 65 },
  "semester_standing": { "current": 4, "after_in_progress": 5, "rule": "semester_classification" },
  "by_level": { "100": 22, "200": 24, "300": 9, "400": 10 },
  "gpa": [
    { "label": "Cumulative", "gpa": 3.41, "graded_credits": 54 },
    { "label": "DS/STAT/CMPSC", "gpa": 3.18, "graded_credits": 31 }
  ]
}
```

- GPA는 `grades.points`가 있는 PSU 이수 과목만 계산합니다. 이전·시험 학점은 학점에만 포함합니다.
- 재수강 과목의 GPA 처리 방식은 `policy_rules`의 `repeat_gpa_rule`을 따릅니다. 규칙이 없으면 `warnings`에 남기고 모든 시도를 포함합니다.

### `gpa_what_if`
예상 성적에 따른 GPA 계산, 또는 목표 GPA에 필요한 평균 성적 역산.

| 입력 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `assumed_grades` | `{code, grade}[]` | 둘 중 하나 | 수강 중·계획 과목의 가정 성적 |
| `target` | `{gpa, credits}` | 둘 중 하나 | "앞으로 N학점에서 목표 GPA 달성" 역산 |
| `scope` | `{label, subjects?[], program_code?, node_id?}` | | 기본 누적 GPA |

```json
{ "scope": "Cumulative", "current": { "gpa": 3.41, "graded_credits": 54 },
  "projected": { "gpa": 3.46, "graded_credits": 69 },
  "required_average": null }
```

`target`을 주면 `required_average`에 필요한 평균 평점(예: `3.62`)을 넣고, 4.0을 넘으면 `achievable: false`를 함께 반환합니다.

### `lookup_credit_equivalency`
AP·IB 등 시험 점수나 타 대학 과목이 PSU에서 어떻게 인정되는지 조회.

| 입력 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `source` | string | ✓ | `AP`, `IB`, 기관 이름 또는 코드 |
| `item` | string | | `Calculus BC`, `MATH 201`. 생략하면 해당 출처의 목록 |
| `score` | number | | 시험 점수 |
| `as_of` | date | | 기본 오늘. 적용 기간 판정용 |

```json
{ "source": { "code": "AP", "name": "Advanced Placement", "kind": "exam" },
  "matches": [
    { "item": "Calculus BC", "score_range": [4, 5], "awarded": "MATH 140, MATH 141", "psu_course_code": null,
      "credits": 8, "attributes": ["GQ"], "effective_from": "2024-05-01", "effective_to": null }
  ],
  "note": "공개 인정표 기준 예상이며 최종 인정은 학교 심사 결과를 따릅니다." }
```

- 기관 이름이 모호하면 후보 목록을 `data.candidates`로 반환하고 `matches`는 비웁니다.
- 테이블에 없는 기관·과목은 `NOT_FOUND`이며, 개별 심사 대상이라고 안내합니다.

---

## 5. 학교 규정

### `get_policy_rule`
수치·구조화 규정 조회. 계산 도구도 내부적으로 같은 데이터를 씁니다.

| 입력 | 타입 | 필수 |
|---|---|---|
| `key` | string | ✓ |
| `catalog_year` | string | |

```json
{ "key": "max_credits_without_approval", "applied_scope": "university",
  "value": { "credits": 19 }, "description": "...", "policy_ref": "...", "catalog_year": "2024-25",
  "broader": [] }
```

- 프로필의 단과대·전공 기준으로 가장 구체적인 범위의 값을 `applied_scope`와 함께 반환하고, 그 값에 가려진 더 넓은 범위의 값은 `broader`에 함께 담습니다.
- 등록된 `key` 목록은 `key: "*"`로 조회합니다.

### `get_deadlines`
학사 일정과 마감일.

| 입력 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `term_code` | string | | 기본 현재 학기 |
| `event_keys` | string[] | | `late_drop`, `withdrawal` 등. 생략하면 전체 |
| `campus` | string | | 기본 프로필 캠퍼스 |

```json
{ "term": "FA26", "events": [
  { "event": "late_drop", "label": "Late drop deadline", "starts_on": null, "ends_on": "2026-11-13", "days_left": 20 }
], "fetched_at": "2026-08-01T00:00:00Z" }
```

`fetched_at`이 30일보다 오래됐으면 `warnings`에 "Registrar 페이지에서 최신 날짜를 확인하세요"를 넣고 링크를 `sources`에 포함합니다.

### `search_policy`
설명형 규정 RAG. `policy_chunks` + pgvector 필요.

| 입력 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `query` | string | ✓ | |
| `scope` | (`university` \| `college` \| `program`)[] | | 기본 전체 |
| `include_profile_scopes` | bool | | 기본 `true`. 프로필의 단과대·전공 문서를 함께 검색 |
| `catalog_year` | string | | |
| `k` | int | | 기본 5, 최대 10 |

```json
{ "chunks": [
  { "scope": "university", "scope_code": null, "policy_ref": "...", "title": "Repeating Courses", "heading": "...",
    "content": "...", "effective_on": "2024-08-01", "source_url": "https://...", "score": 0.82 },
  { "scope": "college", "scope_code": "IST", "policy_ref": null, "title": "IST Course Repeat Guidance", "heading": "...",
    "content": "...", "effective_on": null, "source_url": "https://...", "score": 0.77 }
] }
```

같은 주제로 대학·단과대 규정이 함께 나오면 에이전트는 둘 다 보여줍니다.

---

## 6. 외부 검색

### `web_search`
최후 수단. DB와 규정 데이터로 답할 수 없을 때만 호출합니다.

| 입력 | 타입 | 필수 |
|---|---|---|
| `query` | string | ✓ |
| `max_results` | int | 기본 5 |

```json
{ "results": [ { "title": "...", "url": "https://stat.psu.edu/...", "snippet": "...", "retrieved_at": "2026-10-24T..." } ] }
```

- 서버에서 `psu.edu` 하위 도메인으로 제한합니다.
- 결과는 `sources[].type = "web"`로 표시하고, 답변에서 공식 요건과 구분합니다. DB 결과와 충돌하면 DB가 우선입니다.

### `search_internal_docs` (미정)
PSU 로그인 뒤 문서용 자리입니다. 대상 문서와 접근 방식이 정해지면 `search_policy`와 같은 입출력으로 구현합니다. 유저 계정으로 대리 로그인해 수집하는 방식은 사용하지 않습니다.

---

## 7. 장기 메모

메모는 위험도가 낮고 자주 쓰이므로 승인 없이 바로 저장합니다. 대신 프런트가 "메모 저장됨: …" 알림과 되돌리기를 보여주고, 설정 화면에서 전체 목록을 보고 지울 수 있게 합니다.

### `save_memory`

| 입력 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `category` | `preference` \| `goal` \| `constraint` \| `other` | ✓ | |
| `content` | string | ✓ | 500자 이하, 한 문장 |
| `replaces_id` | int | | 기존 메모를 수정할 때 |

```json
{ "memory": { "id": 9, "category": "constraint", "content": "여름학기는 수강하지 않음" }, "notice": "shown_to_user" }
```

- 유저가 직접 말한 사실·선호만 저장합니다. 에이전트의 추측, 성적·건강 등 민감한 내용은 저장하지 않습니다.
- 유저당 최대 50개. 넘으면 `INVALID_ARGUMENT`로 정리를 요청합니다.
- 서버가 현재 메시지를 `source_message_id`로 기록합니다.

### `forget_memory`

| 입력 | 타입 | 필수 |
|---|---|---|
| `id` | int | ✓ |

유저가 "그거 잊어줘"라고 하거나 메모가 새 발언과 충돌할 때 호출합니다.

---

## 8. 쓰기 도구 (유저 승인 필요)

LLM은 변경을 **제안**만 합니다. 쓰기 도구는 실제로 저장하지 않고 `pending_action`을 반환합니다. 프런트가 이를 확인 카드로 보여주고, 유저가 승인하면 서버가 실행합니다. 승인 요청은 LLM을 거치지 않습니다.

공통 응답:

```json
{ "pending_action": {
  "id": "pa_7f2...", "kind": "save_plan",
  "summary": "SP27에 STAT 414, DS 340 추가",
  "diff": { "added": [ { "term": "SP27", "course": "STAT 414" } ], "removed": [] },
  "expires_at": "2026-10-24T16:00:00Z"
} }
```

| 도구 | 입력 | 대상 테이블 |
|---|---|---|
| `save_plan` | `plan_id?`, `name`, `terms[{term, courses[]}]`, `make_primary?` | `plans`, `plan_courses` |
| `update_student_record` | `add[{code?, raw_label?, term, status, grade?, credits, equivalency_id?}]`, `update[{id, ...}]`, `remove[id]` | `student_courses` |
| `set_program_goal` | `program_code`, `option_code?`, `status` (`declared` \| `intended` \| `exploring` \| `remove`) | `student_programs` |
| `update_profile` | `catalog_year?`, `expected_grad_term?`, `campus?`, `college?`, `max_credits_per_term?` | `student_profiles` |

승인 API: `POST /api/actions/{id}/confirm`, `POST /api/actions/{id}/cancel`. 승인되면 관련 `audit_snapshots`를 무효화합니다.

---

## 9. 실행 기록 (도구 아님)

서버가 매 턴 자동으로 남깁니다. TigerData hypertable이며 90일 보존 정책이 걸려 있습니다.

| 테이블 | 단위 | 기록 내용 |
|---|---|---|
| `agent_runs` | 턴 | 모델, 상태(`ok` / `error` / `rate_limited` / `validation_failed`), 총 소요 시간, LLM·도구 호출 횟수, 토큰 수 |
| `agent_steps` | 단계 | `llm` / `tool` / `validation`, 도구 이름, 인자, 결과, 소요 시간, 에러 코드 |

- `agent_steps.run_id`는 FK 없이 `agent_runs.id`와 같은 값으로 연결합니다.
- 출력 검증이 실패해 답변을 다시 생성하면 `validation` 단계로 남기고, 재시도 후에도 실패하면 `validation_failed`로 끝냅니다.
- 유저 삭제 시 `app_users` FK의 `ON DELETE CASCADE`로 함께 지워집니다.

---

## 9.5 v1 구현 현황 (2026-10-24)

채팅(`POST /api/reports/{id}/chat`)은 `AdvisorAgent`의 도구 호출 루프로 동작합니다. 실행 순서:

1. **Luminal 에이전트** (`ADVISOR_AGENT_PROVIDER=luminal`, 기본값). OpenAI 호환 `tools` / `tool_calls` / `role: tool` 메시지. assistant 메시지(`reasoning_content` 포함)는 그대로 다시 보냄. `LUMINAL_API_KEY`가 비어 있으면 건너뜀
2. **Gemini 에이전트** (Luminal 오류·타임아웃 시, 또는 `ADVISOR_AGENT_PROVIDER=gemini`). 새 turn으로 처음부터 다시 실행
3. **고정 문맥 채팅** (두 에이전트 모두 실패 시). `report.catalogDb` + 웹 검색 + Gemini만(`ModelRouter.fallbackChat`, Luminal 생략)

`ADVISOR_AGENT_ENABLED=false`면 1·2를 건너뛰고 기존 `ModelRouter.chat`(Luminal 라우팅 포함)을 씁니다.

**시간 예산**(프런트 채팅 요청 타임아웃 160초, `App.vue` `send`): 에이전트 단계 전체 75초(Luminal 최대 55초·호출당 25초, Gemini는 남은 시간·호출당 35초, 최소 20초) + 고정 문맥 폴백 최대 70초(웹 30초 + Gemini 40초) = 145초. 실행마다 남은 시간이 14초 미만이면 도구를 금지하고 바로 답하게 합니다.

| 도구 | 구현 | 비고 |
|---|---|---|
| `get_student_record` | `AdvisorTools` | 저장된 리포트만 읽음(`summary` / `requirements` / `courses`). 학생 테이블(섹션 7) 미사용 |
| `search_courses` | `CatalogDbService.searchCourses` | 영어 키워드는 단어마다 제목·설명·코드 중 하나에 있어야 함. 적재된 과목(프로그램이 참조하는 과목)만 |
| `get_course` | `CatalogDbService.courses` + 설명 | |
| `check_prerequisites` | `PrerequisiteChecker` | `met` / `in_progress` / `not_met` / `unknown`. 교차개설 반영. `TEXT`·`STANDING`은 `unknown`. 수강 제한(`course_restrictions` 0행)·학기 지정 인자는 미지원 |
| `get_program_requirements` | `CatalogDbService.requirements(..., withIds, rootId)` | 노드 id 포함. `node_id` 없으면 개요(노드별 `course_count`, 과목 목록·원문 제외), `node_id`로 하위 트리와 과목 |
| `find_courses_for_requirement` | `CatalogDbService.requirementCourses` | 과목마다 `in_report`(no / in_progress / recorded:등급). `eligible` 판정은 아직 없음 |
| `get_policies` | `CatalogDbService.policies` | 명세의 `get_policy_rule` 대신 프로그램 규정 조회 |
| `get_minor_assessment` | `MathMinorService` | MATH만 |
| `web_search` | `WebResearchService.search` | 답변당 1회. `WEB_SEARCH_PROVIDER=tavily`(키 있을 때)면 Tavily 검색 결과(상위 6개, 스니펫 800자 이하)를 LLM 요약 없이 바로 `[W#]` 출처·발췌로 전달. `sources`: `official`(psu.edu만) / `community`(reddit.com·ratemyprofessors.com만) / `any`. Tavily가 실패(할당량 429·432·433, 오류)하면 Gemini 검색으로 폴백. 결과는 기존처럼 `webResearch`로 프런트에 전달 |
| `show_view` | `AdvisorViews` | 화면 종류와 과목/노드만 고름. 데이터는 서버가 도구로 채움(아래). 답변당 1회 |
| `save_memory` / `forget_memory` | `AdvisorStore` | 7절. 유저 본인 범위, 최대 50개, 500자. 응답 `memoryChanges`로 알림·되돌리기 |

- 자동 문맥: 학생 프로필(`AdvisorTools.profile`: 학교·캠퍼스·전공/옵션·과목 행 전체를 이수(성적)/수강 중/미이수/검토 필요로 분류·남은 요건·현재 수강으로 충족되는 요건·최근 언급 과목(이전 화면 속 과목 포함)), 학점 합계·경고, 카탈로그 범위, 장기 메모, 응답 언어, 최근 대화 12개. 요건 트리는 넣지 않음. 모호한 질문("다음 학기 뭐 들어?", "어려워?")은 프로필로 해석하고 가정을 한 줄로 밝힘
- 웹 검색어: 서버가 캠퍼스를 항상, 전공은 과목 코드가 없는 검색어에만 덧붙임(`searchContext` = "캠퍼스|전공", 성적·기록은 넣지 않음)
- 답변 보정: 빈 답변, 도구 호출 마크업이 섞인 답변(DeepSeek DSML 등), 화면만 가리키는 280자 미만 답변은 한 번 다시 요청. 마크업은 사용자에게 보내지 않음
- `check_prerequisites`는 `codes`로 최대 8과목을 한 번에 판정(도구 호출 1회). 동시수강 노드에는 "같이 들어야 한다는 뜻이 아님" 설명을 붙임
- 한도: 모델 호출 6회(마지막은 도구 금지: Gemini `mode: NONE`, Luminal `tool_choice: none`), 데이터 도구 호출 8회(`show_view`·메모 도구는 제외). 인자가 JSON 객체가 아니면 도구를 실행하지 않고 `INVALID_ARGUMENT`를 돌려줌
- 응답: `answer`, `provider`(실제로 답한 쪽: `luminal-agent` / `gemini-agent` / 폴백 시 `gemini`), `toolCalls`(도구 이름·인자·ok), `webResearch`(검색했을 때), `view`(화면), `memoryChanges`(저장·삭제한 메모. 실패한 Luminal 실행에서 저장한 것도 포함)

**화면(`view`)**: 에이전트가 `show_view`로 고른 화면을 `AdvisorViews`가 리포트와 카탈로그 도구로 채웁니다. 모델이 쓴 숫자는 들어가지 않습니다. 프런트 `AdvisorView.vue`가 대시보드 상단에 표시하고, 채팅 메시지의 "화면 열기" 칩으로 다시 엽니다. 화면은 `chat_messages`에 답변과 함께 저장되어 대화를 다시 열어도 남습니다.

| view | 데이터 |
|---|---|
| `course_detail` | 과목마다 `get_course` + `check_prerequisites`(트리별 판정) |
| `course_options` | 과목마다 제목·학점·속성, 선수과목 판정 결과, 리포트 포함 여부 |
| `requirement_gap` | 리포트에서 완료·`coveredByPlan`이 아닌 요건 + 지정 노드의 `find_courses_for_requirement` |
| `semester_plan` | `suggested_academic_plan.<옵션>`(Bulletin 권장 계획)에 리포트 과목 표시(`recorded:등급` / `in_progress` / `not_in_report` / `generic`). 개인 맞춤·검증된 일정이 아님. 옵션을 모르거나 계획이 없으면 `unavailable` |
| `minor_check` | `get_minor_assessment` 결과(프런트는 `MinorPlanner` 재사용) |

**업로드**: PDF 분석에는 사용자 목표(intent)를 넣지 않고 학생 상태만 추출합니다(`gemini.analyze(pages, "", language)`). intent는 리포트에 저장되고, 업로드 직후 프런트가 채팅을 열어 에이전트의 첫 질문으로 보냅니다.

**실행 기록**: 실행마다 `agent_runs`(모델, 상태, 소요 시간, LLM·도구 호출 수, 화면 종류, 오류)와 `agent_steps`(LLM/도구 단계, 도구 이름·인자·결과, 소요 시간, 오류 코드; 결과 2만 자 초과는 요약)를 남깁니다. Luminal 실패 후 Gemini 실행은 별도 run입니다. 저장 실패는 답변을 막지 않습니다. 90일 보존, 리포트 삭제 시 함께 삭제(`backend/src/main/resources/schema.sql`)

**진행 상황**: 채팅 요청에 `requestId`(UUID)를 넣으면 서버(`AgentProgress`, 메모리, 10분 보관)가 단계(`llm` plan/answer, `tool` 이름, `fallback`)를 기록합니다. 프런트는 답변을 기다리는 동안 `GET /api/reports/{id}/chat/progress/{requestId}`를 1초마다 조회해 "선수과목 확인 중…" 같은 단계와 경과 시간을 보여줍니다. 본인(JWT subject) 요청만 조회되고, 도구 인자·결과는 담지 않습니다.

**메모 API**: `GET /api/memories`, `DELETE /api/memories/{id}`(본인 것만). 저장은 에이전트 도구로만 합니다.
- 실측(2026-10-24, 가상 리포트, `deepseek-ai/DeepSeek-V4.1-Flash`): "Can I take CMPSC 465 next semester?" → `get_student_record`, `check_prerequisites`, `get_course`×2, 약 9.4초. "What algorithms course should I take?"(과목 코드 없음) → `get_student_record`×2, `get_program_requirements`, `get_course`, `check_prerequisites`, 약 10.7초. 이 질문에서는 `search_courses` 대신 요건 트리에서 과목을 찾음
- 실측(같은 날, 화면·메모 추가 후, Luminal): "Can I take CMPSC 465?" → `course_detail` 약 12초. "Show me a semester plan to graduate." → `get_policies`·`get_program_requirements` 후 `semester_plan` 약 18~28초. "remember … avoid 8 am classes" → `save_memory` 약 4초. 업로드 목표 문장 "Computational Data Sciences, considering a Math minor" → `get_minor_assessment` 후 `minor_check` 약 12초. 예산 조정 전에는 마지막 질문에서 Luminal이 35초를 넘겨 Gemini로 폴백했음
- 미구현: `run_audit`, `what_if`, `validate_plan`, 학점·규정·쓰기 도구, 출력 검증 단계, 개인 맞춤 학기 계획(현재는 Bulletin 권장 계획 + 표시)
- 테스트: `PrerequisiteCheckerTest`, `AdvisorToolsTest`, `AdvisorAgentTest`(루프·한도·폴백), `AdvisorViewsTest`(화면·메모·실행 기록·예산), 프런트 `advisor-view.test.js`·`advisor-view-render.test.js`, `LiveAdvisorAgentTest`·`LiveAdvisorStoreTest`(`LIONPATHWAY_LIVE_TEST=1`, 실제 DB + Gemini/Luminal. 쓰기는 모두 트랜잭션 롤백)

---

## 10. 남은 한계

| 영역 | 내용 |
|---|---|
| 중복 인정 | 노드 단위 `exclusive`만 지원. "전공·마이너 간 최대 N학점 공유" 같은 프로그램 간 규칙은 미지원 |
| 학위 전체 조건 | "마지막 N학점은 PSU에서" 같은 거주 요건은 수강 장소 필터가 없어 표현 불가 |
| 미래 개설 | `typical_terms` 기반 예상일 뿐, 실제 개설은 수집된 학기만 확정 |
| 학점 인정 | 공개 인정표에 있는 항목만. 개별 심사 결과는 유저가 기록을 직접 수정 |
| 규정 해석 | 예외 승인이 필요한 사안은 판정하지 않고 절차·담당 부서만 안내 |

---

## 11. 에이전트 라우팅 규칙 (시스템 프롬프트 요약)

1. 자동 문맥으로 답할 수 있으면 도구를 호출하지 않는다.
2. 학점·GPA·요건·수강 가능 여부는 반드시 계산 도구(`run_audit`, `what_if`, `check_prerequisites`, `validate_plan`, `get_credit_summary`, `gpa_what_if`) 결과로 답한다. 직접 세지 않는다.
3. 과목·프로그램 이름이 모호하면 `search_courses` / `search_programs`로 코드를 확정한 뒤 진행한다.
4. 마감일과 규정 수치는 `get_deadlines` / `get_policy_rule` 값으로만 답하고, 적용 학년도와 출처 링크를 함께 보여준다.
5. 설명형 규정은 `search_policy` 결과를 인용하고 규정 번호·링크를 붙인다. 대학과 단과대 규정이 함께 나오면 둘 다 보여준다.
6. 출처 우선순위: 계산 도구·카탈로그·규정 데이터 > `search_policy` > `web_search`.
7. 학점 인정 결과는 "공개 인정표 기준 예상"이라고 밝힌다.
8. `unknown`, `predicted`, `DATA_NOT_AVAILABLE`은 추측으로 메우지 않고 유저에게 그대로 알린다.
9. 예외 승인이 필요한 사안(학점 초과, 마감 후 철회, 요건 대체)은 가능 여부를 판단하지 않고 절차와 어드바이저 상담을 안내한다.
10. 유저가 밝힌 선호·목표·제약은 `save_memory`로 저장하고, 추천할 때 자동 문맥의 메모를 반영한다.
11. 기록·계획·전공 변경은 쓰기 도구로 제안하고, 승인 전에는 "저장했다"고 말하지 않는다.
12. 졸업 가능 여부를 답할 때는 공식 졸업 판정이 아니라는 점을 명시한다.

## 12. Gemini function declaration 예시

```json
{
  "name": "check_prerequisites",
  "description": "Check whether the current student can take a course in a given term. Evaluates prerequisites, corequisites and enrollment restrictions; never guess when a node is unknown.",
  "parameters": {
    "type": "object",
    "properties": {
      "course_code": { "type": "string", "description": "e.g. 'STAT 414'" },
      "term_code": { "type": "string", "description": "e.g. 'SP27'. Defaults to next term." },
      "assume_completed": { "type": "array", "items": { "type": "string" } },
      "same_term": { "type": "array", "items": { "type": "string" } },
      "plan_id": { "type": "string" }
    },
    "required": ["course_code"]
  }
}
```

```json
{
  "name": "get_deadlines",
  "description": "Academic calendar deadlines for a term. Always answer dates from this tool, with the source link.",
  "parameters": {
    "type": "object",
    "properties": {
      "term_code": { "type": "string", "description": "e.g. 'FA26'. Defaults to the current term." },
      "event_keys": { "type": "array", "items": { "type": "string" }, "description": "e.g. ['late_drop', 'withdrawal']" },
      "campus": { "type": "string" }
    }
  }
}
```
