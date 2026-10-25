# Advisor Agent tool spec (draft)

Based on `docs/advisor-schema.sql`. Lists the tools the agent calls, and their input/output formats, in the flow: user query → context assembly → agent ⇄ tools → output validation → response.

Example values (courses, prerequisites, credits, policy numbers, dates) are synthetic, only illustrate the format, and were not checked against the actual Bulletin.

## Contents

| Category | Tools |
|---|---|
| Automatic context | (not a tool) profile, programs, audit summary, long-term notes |
| Catalog lookup | `search_courses`, `get_course`, `search_programs`, `get_program`, `get_offerings` |
| Records and computation | `get_student_record`, `check_prerequisites`, `run_audit`, `what_if`, `find_courses_for_requirement`, `validate_plan` |
| Credits | `get_credit_summary`, `gpa_what_if`, `lookup_credit_equivalency` |
| University policy | `get_policy_rule`, `get_deadlines`, `search_policy` |
| External search | `web_search`, `search_internal_docs` (TBD) |
| Notes | `save_memory`, `forget_memory` |
| Writes (approval required) | `save_plan`, `update_student_record`, `set_program_goal`, `update_profile` |
| Run logs | (not a tool) the server records automatically to `agent_runs`, `agent_steps` |

## 0. Common rules

**Identifier formats**

| Item | Format | Example |
|---|---|---|
| Course | `SUBJECT NUMBER` | `STAT 414`, `CMPSC 497A` |
| Term | `FA` / `SP` / `SU` + 2-digit year | `FA26` |
| Catalog year | `YYYY-YY` | `2025-26` |
| Program | Uppercase code | `DATSC_BS`, `STAT_MINOR`, `DATSC_ETM` |

**User identity**: no tool takes a user ID as an argument. The server injects it from the JWT `sub`, which removes any path for the LLM to reach another user's data.

**Default catalog year**: if `catalog_year` is omitted, the user profile's catalog year is used; without a profile, the current catalog year.

**Response envelope**: every tool responds in the same shape.

```json
{
  "ok": true,
  "data": { },
  "sources": [
    { "type": "catalog", "ref": "course_versions:STAT 414@2025-26", "url": "https://bulletins.psu.edu/...", "catalog_year": "2025-26" }
  ],
  "warnings": ["SP27 offering data has not been collected yet."]
}
```

```json
{ "ok": false, "error": { "code": "NOT_FOUND", "message": "Unknown course: STAT 999" } }
```

- `sources[].type`: `catalog` | `offering` | `record` | `audit` | `equivalency` | `policy_rule` | `calendar` | `policy` | `web`
- The output validation step checks that numbers, course codes, and dates in the answer exist in this turn's tool results (or the automatic context), and that their evidence is in `sources`.

**Error codes**

| Code | Meaning | Agent response |
|---|---|---|
| `INVALID_ARGUMENT` | Malformed or out of range | Fix the arguments and retry once |
| `NOT_FOUND` | No such course, program, plan, or policy | Check candidates with `search_*` |
| `DATA_NOT_AVAILABLE` | Data for that term/catalog year not collected | Tell the user it's unconfirmed |
| `PROFILE_INCOMPLETE` | Required profile data (catalog year, major, etc.) missing | Ask the user |
| `RATE_LIMITED` | Call limit exceeded | Stop and explain |

**Call limits**: at most 8 tool calls per turn; list results at most 20 items.

**Judgment values**: when unsure, return `unknown` instead of guessing. E.g. unparsed prerequisite sentences (`TEXT` nodes), unmatched prior credit, uninterpreted enrollment restrictions (`text` restrictions).

**Policy scope precedence**: when `policy_rules` with the same `key` exist at several scopes, apply program > college > university, and show the applied scope in the result.

---

## 1. Automatic context (not a tool)

The server inserts this into the prompt every turn. If it's enough to answer, no tool is called.

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
    { "id": 4, "category": "preference", "content": "Wants to avoid 8 a.m. classes" },
    { "id": 7, "category": "goal", "content": "Hopes to become a data engineer" }
  ],
  "primary_plan_id": "1f6c..."
}
```

`upcoming_deadlines` includes only items within 14 days from the current term's `academic_calendar`.

---

## 2. Catalog lookup

### `search_courses`
Find courses by natural language or filters.

| Input | Type | Required | Description |
|---|---|---|---|
| `query` | string | | Title/description keywords |
| `subject` | string | | `STAT` |
| `level_min`, `level_max` | int | | `400`, `499` |
| `attributes` | string[] | | `["GQ"]`, all must match (AND) |
| `offered_term` | string | | Only courses offered that term |
| `catalog_year` | string | | |
| `limit` | int | | Default 10, max 20 |

At least one input is required.

```json
{ "courses": [
  { "code": "STAT 414", "title": "Introduction to Probability Theory", "credits": [3, 3], "level": 400, "attributes": [],
    "typical_terms": ["FA", "SP"], "offered_in_term": "yes" }
], "total": 1 }
```

`offered_in_term`: `yes` (confirmed in collected offerings) | `no` (collected but not offered) | `predicted` (not collected; predicted from `typical_terms`) | `unknown`

Tables: `courses`, `course_versions`, `course_attributes`, `course_offerings`

### `get_course`
Course details, prerequisite tree, enrollment restrictions, and courses this one unlocks.

| Input | Type | Required |
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

Tables: `course_versions`, `course_attributes`, `course_crosslists`, `prereq_nodes`, `course_restrictions`

### `search_programs`
Turns phrases like "statistics minor" or "data science entrance to major requirements" into program codes.

| Input | Type | Required |
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
Requirement tree structure.

| Input | Type | Required |
|---|---|---|
| `program_code` | string | ✓ |
| `option_code` | string | |
| `catalog_year` | string | |
| `depth` | int | Default: full tree |

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

- `rules` is a human-readable summary of `requirement_rules`.
- `entrance_program` is the ETM program whose `programs.entrance_for` points to this major.

### `get_offerings`
Sections offered per term.

| Input | Type | Required |
|---|---|---|
| `course_codes` | string[] | ✓ (max 10) |
| `term_code` | string | ✓ |
| `campus` | string | Default: profile campus |

```json
{ "term": "SP27", "fetched_at": "2026-10-20T04:00:00Z", "sections": [
  { "course": "STAT 414", "section": "001", "instructor": "...", "meetings": [ { "days": "MWF", "start": "10:10", "end": "11:00" } ], "capacity": 120, "enrolled": 97 }
], "not_offered": ["STAT 416"] }
```

An uncollected term returns `DATA_NOT_AVAILABLE`, with a `typical_terms`-based prediction in `data.predicted` (`{ "STAT 414": true }`). "Not offered" (`not_offered`), "predicted" (`predicted`), and "unknown" are kept distinct.

---

## 3. Student records and computation (rule engine)

### `get_student_record`

| Input | Type | Required |
|---|---|---|
| `status` | (`completed` \| `in_progress` \| `transfer` \| `exam`)[] | Default: all |
| `term_code` | string | |

```json
{ "courses": [
  { "id": 51, "code": "STAT 200", "term": "FA24", "status": "completed", "grade": "A-", "credits": 4 },
  { "id": 63, "code": null, "raw_label": "STAT 2XX", "status": "transfer", "credits": 3, "equivalency_id": 1204 }
], "totals": { "completed": 58, "in_progress": 15, "transfer": 3 } }
```

### `check_prerequisites`
Whether a course can be taken. Evaluates prerequisites, corequisites, and enrollment restrictions together.

| Input | Type | Required | Description |
|---|---|---|---|
| `course_code` | string | ✓ | |
| `term_code` | string | | Assume it's taken this term. Default: next term |
| `assume_completed` | string[] | | Assume these are also completed before then |
| `same_term` | string[] | | Assume these are taken the same term (for corequisites) |
| `plan_id` | string | | Treat the plan's earlier-term courses as completed and same-term courses as concurrent |

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

- `eligible`: `yes` | `no` | `unknown`. `yes` only when the tree and all restrictions are `met`.
- Node `status`: `met` | `unmet` | `in_progress` | `same_term` | `unknown`
- Semester standing is computed from projected credits at the start of that term and `semester_classification` in `policy_rules`.
- In-progress courses are marked `in_progress`; if a requirement is met only once the term ends, it's noted in `warnings`.

### `run_audit`
Per-requirement status. Entrance to Major uses the same tool (pass the ETM code as `program_code`).

| Input | Type | Required | Description |
|---|---|---|---|
| `program_code` | string | | Default: declared major |
| `option_code` | string | | Default: profile option |
| `plan_id` | string | | Include planned courses |
| `count_in_progress` | bool | | Default `true`. `false` counts completed only |
| `node_id` | int | | A single requirement only |

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

- Node `status`: `satisfied` | `partial` | `unsatisfied` | `unknown`
- `in_progress` credits are not added to `completed`. `GPA_MIN` uses only courses with final grades.
- Transfer and exam credits are applied to rules via the `psu_course_code` or `psu_label` (subject prefix and level) matched in `credit_equivalencies`.
- Results are cached in `audit_snapshots` with an input hash.

### `what_if`
Before/after comparison for hypothetical changes. Nothing is saved.

| Input | Type | Required |
|---|---|---|
| `add_courses` | `{code, term, grade?}[]` | |
| `remove_courses` | string[] | |
| `add_programs` | `{code, option?}[]` | |
| `remove_programs` | string[] | |
| `switch_option` | `{program, option}` | |
| `base_plan_id` | string | |

At least one input is required. If `grade` is given, it's also applied to `GPA_MIN` nodes.

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

`credit_overload` is based on the profile's `max_credits_per_term`; exceeding `max_credits_without_approval` in `policy_rules` is flagged separately as needing approval.

### `find_courses_for_requirement`
The core recommendation tool for "what should I take to fill this requirement?"

| Input | Type | Required | Description |
|---|---|---|---|
| `program_code` | string | ✓ | |
| `node_id` | int | ✓ | From `run_audit` / `get_program` results |
| `term_code` | string | | Include offering and eligibility for this term |
| `eligible_only` | bool | | Default `false` |
| `limit` | int | | Default 10, max 20 |

```json
{ "node": { "node_id": 310, "label": "GN", "remaining": { "credits": 6 } },
  "candidates": [
    { "code": "BIOL 110", "title": "...", "credits": [4, 4],
      "eligible": "yes", "offered": "predicted",
      "also_satisfies": [ { "program": "GENED", "node_id": 901, "label": "Integrative Studies" } ] }
  ] }
```

- Sort order: eligible → offering confirmed (`yes` > `predicted` > `unknown`) → courses that also fill other requirements.
- `eligible` uses the same judgment as `check_prerequisites` (prerequisites, corequisites, enrollment restrictions).
- `also_satisfies` respects the node's `exclusive` setting.
- Long-term notes (e.g. "wants to avoid 8 a.m. classes") are applied by the agent when explaining results, not by the tool.

### `validate_plan`
Checks a semester-by-semester plan and projects the graduation term.

| Input | Type | Required |
|---|---|---|
| `plan_id` | string | One of the two |
| `terms` | `{term, courses[]}[]` | One of the two (unsaved draft) |

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

## 4. Credits

### `get_credit_summary`
Credit and GPA status. Called when a finer breakdown than the automatic context's `record_summary` is needed.

| Input | Type | Required | Description |
|---|---|---|---|
| `group_by` | (`status` \| `level` \| `subject` \| `attribute` \| `term`)[] | | Default `["status"]` |
| `gpa_scopes` | `{label, subjects?[], program_code?, node_id?}[]` | | Additional GPA scopes to compute (e.g. major GPA) |

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

- GPA uses only completed PSU courses with `grades.points`. Transfer and exam credits count toward credits only.
- GPA handling for repeated courses follows `repeat_gpa_rule` in `policy_rules`. Without that rule, a warning is added and all attempts are included.

### `gpa_what_if`
GPA from expected grades, or the average grade needed to reach a target GPA.

| Input | Type | Required | Description |
|---|---|---|---|
| `assumed_grades` | `{code, grade}[]` | One of the two | Assumed grades for in-progress or planned courses |
| `target` | `{gpa, credits}` | One of the two | Back-solve "reach target GPA over the next N credits" |
| `scope` | `{label, subjects?[], program_code?, node_id?}` | | Default: cumulative GPA |

```json
{ "scope": "Cumulative", "current": { "gpa": 3.41, "graded_credits": 54 },
  "projected": { "gpa": 3.46, "graded_credits": 69 },
  "required_average": null }
```

With `target`, `required_average` holds the needed average grade points (e.g. `3.62`); if that exceeds 4.0, `achievable: false` is also returned.

### `lookup_credit_equivalency`
How AP/IB exam scores or courses from other institutions are credited at PSU.

| Input | Type | Required | Description |
|---|---|---|---|
| `source` | string | ✓ | `AP`, `IB`, institution name or code |
| `item` | string | | `Calculus BC`, `MATH 201`. Omit to list everything for the source |
| `score` | number | | Exam score |
| `as_of` | date | | Default today. Used to check the effective period |

```json
{ "source": { "code": "AP", "name": "Advanced Placement", "kind": "exam" },
  "matches": [
    { "item": "Calculus BC", "score_range": [4, 5], "awarded": "MATH 140, MATH 141", "psu_course_code": null,
      "credits": 8, "attributes": ["GQ"], "effective_from": "2024-05-01", "effective_to": null }
  ],
  "note": "Estimate based on the public equivalency table; final credit depends on the university's review." }
```

- If the institution name is ambiguous, candidates are returned in `data.candidates` and `matches` is empty.
- Institutions or courses not in the table return `NOT_FOUND`, with a note that they require individual review.

---

## 5. University policy

### `get_policy_rule`
Numeric and structured policy lookup. Computation tools use the same data internally.

| Input | Type | Required |
|---|---|---|
| `key` | string | ✓ |
| `catalog_year` | string | |

```json
{ "key": "max_credits_without_approval", "applied_scope": "university",
  "value": { "credits": 19 }, "description": "...", "policy_ref": "...", "catalog_year": "2024-25",
  "broader": [] }
```

- Returns the most specific value for the profile's college and major together with `applied_scope`, and puts broader-scope values it overrides in `broader`.
- List registered keys with `key: "*"`.

### `get_deadlines`
Academic calendar and deadlines.

| Input | Type | Required | Description |
|---|---|---|---|
| `term_code` | string | | Default: current term |
| `event_keys` | string[] | | `late_drop`, `withdrawal`, etc. Omit for all |
| `campus` | string | | Default: profile campus |

```json
{ "term": "FA26", "events": [
  { "event": "late_drop", "label": "Late drop deadline", "starts_on": null, "ends_on": "2026-11-13", "days_left": 20 }
], "fetched_at": "2026-08-01T00:00:00Z" }
```

If `fetched_at` is older than 30 days, add "Check the Registrar page for the latest dates" to `warnings` and include the link in `sources`.

### `search_policy`
RAG over descriptive policies. Requires `policy_chunks` + pgvector.

| Input | Type | Required | Description |
|---|---|---|---|
| `query` | string | ✓ | |
| `scope` | (`university` \| `college` \| `program`)[] | | Default: all |
| `include_profile_scopes` | bool | | Default `true`. Also search the profile's college and major documents |
| `catalog_year` | string | | |
| `k` | int | | Default 5, max 10 |

```json
{ "chunks": [
  { "scope": "university", "scope_code": null, "policy_ref": "...", "title": "Repeating Courses", "heading": "...",
    "content": "...", "effective_on": "2024-08-01", "source_url": "https://...", "score": 0.82 },
  { "scope": "college", "scope_code": "IST", "policy_ref": null, "title": "IST Course Repeat Guidance", "heading": "...",
    "content": "...", "effective_on": null, "source_url": "https://...", "score": 0.77 }
] }
```

When university and college policies on the same topic both come back, the agent shows both.

---

## 6. External search

### `web_search`
Last resort. Called only when the DB and policy data can't answer.

| Input | Type | Required |
|---|---|---|
| `query` | string | ✓ |
| `max_results` | int | Default 5 |

```json
{ "results": [ { "title": "...", "url": "https://stat.psu.edu/...", "snippet": "...", "retrieved_at": "2026-10-24T..." } ] }
```

- The server restricts results to `psu.edu` subdomains.
- Results are marked `sources[].type = "web"` and distinguished from official requirements in the answer. If they conflict with DB results, the DB wins.

### `search_internal_docs` (TBD)
Placeholder for documents behind PSU sign-in. Once the target documents and access method are decided, it will use the same input/output as `search_policy`. Collecting by signing in on behalf of the user's account will not be used.

---

## 7. Long-term notes

Notes are low risk and frequently used, so they're saved immediately without approval. Instead, the frontend shows a "Note saved: …" notice with undo, and a settings screen lists all notes for review and deletion.

### `save_memory`

| Input | Type | Required | Description |
|---|---|---|---|
| `category` | `preference` \| `goal` \| `constraint` \| `other` | ✓ | |
| `content` | string | ✓ | 500 characters or fewer, one sentence |
| `replaces_id` | int | | When editing an existing note |

```json
{ "memory": { "id": 9, "category": "constraint", "content": "Won't take summer courses" }, "notice": "shown_to_user" }
```

- Save only facts and preferences the user stated directly. Never save the agent's guesses or sensitive content such as grades or health.
- At most 50 per user. Beyond that, return `INVALID_ARGUMENT` asking the user to clean up.
- The server records the current message as `source_message_id`.

### `forget_memory`

| Input | Type | Required |
|---|---|---|
| `id` | int | ✓ |

Called when the user says "forget that" or a note conflicts with something newly said.

---

## 8. Write tools (user approval required)

The LLM only **proposes** changes. Write tools don't save anything; they return a `pending_action`. The frontend shows it as a confirmation card, and the server executes it once the user approves. The approval request never goes through the LLM.

Common response:

```json
{ "pending_action": {
  "id": "pa_7f2...", "kind": "save_plan",
  "summary": "Add STAT 414 and DS 340 to SP27",
  "diff": { "added": [ { "term": "SP27", "course": "STAT 414" } ], "removed": [] },
  "expires_at": "2026-10-24T16:00:00Z"
} }
```

| Tool | Input | Target tables |
|---|---|---|
| `save_plan` | `plan_id?`, `name`, `terms[{term, courses[]}]`, `make_primary?` | `plans`, `plan_courses` |
| `update_student_record` | `add[{code?, raw_label?, term, status, grade?, credits, equivalency_id?}]`, `update[{id, ...}]`, `remove[id]` | `student_courses` |
| `set_program_goal` | `program_code`, `option_code?`, `status` (`declared` \| `intended` \| `exploring` \| `remove`) | `student_programs` |
| `update_profile` | `catalog_year?`, `expected_grad_term?`, `campus?`, `college?`, `max_credits_per_term?` | `student_profiles` |

Approval API: `POST /api/actions/{id}/confirm`, `POST /api/actions/{id}/cancel`. Approval invalidates related `audit_snapshots`.

---

## 9. Run logs (not a tool)

The server records these automatically every turn. They're TigerData hypertables with a 90-day retention policy.

| Table | Unit | Recorded |
|---|---|---|
| `agent_runs` | Turn | Model, status (`ok` / `error` / `rate_limited` / `validation_failed`), total duration, LLM and tool call counts, token counts |
| `agent_steps` | Step | `llm` / `tool` / `validation`, tool name, arguments, result, duration, error code |

- `agent_steps.run_id` links to `agent_runs.id` by value, without a FK.
- If output validation fails and the answer is regenerated, it's recorded as a `validation` step; if it still fails after retrying, the run ends as `validation_failed`.
- When a user is deleted, rows are removed through `ON DELETE CASCADE` on the `app_users` FK.

---

## 9.5 v1 implementation status (2026-10-24)

Chat (`POST /api/reports/{id}/chat`) runs as the tool-calling loop in `AdvisorAgent`. Order of attempts:

1. **Luminal agent** (`ADVISOR_AGENT_PROVIDER=luminal`, the default). OpenAI-compatible `tools` / `tool_calls` / `role: tool` messages; the assistant message (including `reasoning_content`) is sent back verbatim. Skipped when `LUMINAL_API_KEY` is empty.
2. **Gemini agent** when Luminal errors or times out (or with `ADVISOR_AGENT_PROVIDER=gemini`). Starts a fresh turn.
3. **Fixed-context chat** when both agents fail: `report.catalogDb` + web search + Gemini only (`ModelRouter.fallbackChat`).

`ADVISOR_AGENT_ENABLED=false` skips 1 and 2 and uses the previous `ModelRouter.chat`.

**Time budget** (frontend chat timeout 160 s in `App.vue` `send`): the agent stage is capped at 75 s (Luminal at most 55 s and 25 s per call; Gemini gets the rest, at least 20 s, 35 s per call), plus at most 70 s for the fixed-context fallback (web 30 s + Gemini 40 s) = 145 s. Once less than 14 s is left in a run, tools are switched off and the model must answer.

| Tool | Implementation | Notes |
|---|---|---|
| `get_student_record` | `AdvisorTools` | Reads only the stored report (`summary` / `requirements` / `courses`) |
| `search_courses` | `CatalogDbService.searchCourses` | Every English keyword must appear in the title, description or code; only courses referenced by loaded programs |
| `get_course` | `CatalogDbService.courses` + description | |
| `check_prerequisites` | `PrerequisiteChecker` | `met` / `in_progress` / `not_met` / `unknown`; cross-listings count; `TEXT` nodes are `unknown`; `STANDING` nodes and `min_semester` restrictions use the semester standing estimated from the report's credit totals (Bulletin "Classification of Students by Semester", marked `estimate`); `course_restrictions` rows come back as `restrictions` and `status` combines them with the tree (the tree alone is `prerequisiteStatus`); `codes` checks up to 8 courses in one call; concurrent nodes explain that the courses do not have to be taken together. See `docs/notes/restrictions.md` |
| `get_program_requirements` | `CatalogDbService.requirements(..., withIds, rootId)` (loaded majors: DATSC_BS, CMPSC_BS, CMPEN_BS, EE_BS, each with its ETM) | With node ids. Without `node_id` it is an overview (`course_count` per node, no course lists or Bulletin text); `node_id` returns one subtree with its courses |
| `find_courses_for_requirement` | `CatalogDbService.requirementCourses` | Each course is marked `in_report` (no / in_progress / recorded:GRADE) |
| `get_policies` | `CatalogDbService.policies` | Program policies and the suggested semester plan, in place of `get_policy_rule` |
| `get_minor_assessment` | `MathMinorService` | MATH only |
| `web_search` | `WebResearchService.search` | Once per answer. With `WEB_SEARCH_PROVIDER=tavily` and a key, Tavily results (top 6, snippets up to 800 characters) go straight to the agent as `[W#]` sources and excerpts, with no summarizing LLM call. `sources`: `official` (psu.edu only) / `community` (reddit.com, ratemyprofessors.com only) / `any`. The server adds the campus to every query and the program to queries without a course code; grades and records are never added. Tavily failures (429, 432, 433, errors) fall back to Gemini search |
| `get_deadlines` | `CalendarRepository` | Registrar Regular Session dates (`terms`, `academic_calendar`); defaults to the term in session, else the next one; `days_left`, Registrar source link, warnings for 7-week/6-week/Maymester sections, other campuses and dates older than 30 days. Offered only when the calendar repository is available. Deadlines in the next 14 days (at most 8) are also in the automatic context as `upcomingDeadlines`. See `docs/notes/academic-calendar.md` |
| `show_view` | `AdvisorViews` | The model picks a screen and its courses or nodes; the server fills the data (below). Once per answer |
| `save_memory` / `forget_memory` | `AdvisorStore` | Section 7. Scoped to the user, at most 50 memories of 500 characters. Reported to the client as `memoryChanges` for a notice and undo |

- Automatic context: a student profile (`AdvisorTools.profile`: school, campus, program and option, every course row classified as completed with grade / in progress / not earned / needs review, open requirements, requirements covered by current courses, and the most recently discussed courses, including courses shown on an earlier screen), credit totals and warnings, catalog scope, memories, response language and the last 12 messages. No requirement trees. Vague questions ("what should I take next?", "is it hard?") are read through the profile, and the model states its assumption in one clause instead of asking.
- Limits: 6 model calls (the last one without tools: Gemini `mode: NONE`, Luminal `tool_choice: none`) and 8 data-tool calls; `show_view` and memory tools do not count. Arguments that are not a JSON object become an `INVALID_ARGUMENT` result without running the tool.
- Answer repair: an empty answer, an answer with tool-call markup in its text (DeepSeek DSML and similar), or an answer under 280 characters that only points at the screen is asked for once more. Markup is never sent to the user.
- Response: `answer`, `provider` (`luminal-agent` / `gemini-agent` / `gemini` on fallback), `toolCalls` (name, arguments, ok), `webResearch` (when searched), `view` (screen), `memoryChanges` (memories saved or forgotten, including by a Luminal run that later failed).

**Screens (`view`)**: `AdvisorViews` fills the screen chosen with `show_view` from the report and the catalog tools; no numbers come from model text. The frontend shows it under the answer in the chat (`AdvisorView.vue`). Screens are stored with the answer in `chat_messages`, so they come back when a conversation is reopened.

| view | Data |
|---|---|
| `course_detail` | `get_course` + `check_prerequisites` per course |
| `course_options` | Title, credits and attributes, prerequisite result and report status per course |
| `requirement_gap` | Report requirements that are neither completed nor `coveredByPlan`, plus `find_courses_for_requirement` for the given nodes |
| `semester_plan` | `suggested_academic_plan.<option>` (the Bulletin's suggested plan) with the report's courses marked (`recorded:GRADE` / `in_progress` / `not_in_report` / `generic`). Not a personalized or validated schedule; `unavailable` when the option is unknown or no plan is loaded |
| `minor_check` | The `get_minor_assessment` result (rendered with `MinorPlanner`) |

**Upload**: PDF analysis no longer receives the user's goal and extracts the student's status only (`gemini.analyze(pages, "", language)`). The goal is stored with the report and the frontend sends it as the first chat question.

**Run log**: each run writes `agent_runs` (model, status, latency, LLM and tool call counts, screen type, error) and `agent_steps` (LLM/tool step, tool name, arguments, result, latency, error code; results over 20,000 characters are summarized). A Gemini run after a Luminal failure is a separate run. Logging failures never block the answer. 90-day retention; rows are deleted with their report (`backend/src/main/resources/schema.sql`).

**Progress**: a chat request may carry `requestId` (a UUID). `AgentProgress` keeps its steps in memory for 10 minutes (`llm` plan/answer, `tool` name, `fallback`), and the frontend polls `GET /api/reports/{id}/chat/progress/{requestId}` once a second to show steps such as "Checking prerequisites…" with the elapsed time. Only the requesting user (JWT subject) can read it, and no tool arguments or results are kept.

**Memory API**: `GET /api/memories`, `DELETE /api/memories/{id}` (own memories only). Saving happens only through the agent tool.

- Catalog edition: when the DB has the program version for the report's catalog year, that edition is used (`CatalogDbService.edition`); otherwise the newest with `matched_year_differs`. Only 2026-27 is loaded so far.
- University rules: the system prompt declines only the part of a request that breaks Penn State rules (graded work, misrepresenting facts to get around prerequisites or restrictions, forged records or documents, other students' records, scraping university systems), names the policy area and offers the legitimate route. Questions about the rules are answered normally (`LiveUniversityRulesTest`).
- Not implemented: `run_audit`, `what_if`, `validate_plan`, credit, policy and write tools, an output validation step, personalized semester plans.
- Tests: `PrerequisiteCheckerTest`, `AdvisorToolsTest`, `AdvisorAgentTest`, `AdvisorViewsTest`, `AgentProgressTest`, `WebResearchServiceTest`, `AdvisorDeadlinesTest`, `CatalogDbServiceTest`; frontend `advisor-view.test.js` and `advisor-view-render.test.js`; opt-in `LiveAdvisorAgentTest`, `LiveAdvisorStoreTest`, `LiveTavilyTest`, `LiveVagueQuestionsTest`, `LiveCalendarTest`, `LiveUniversityRulesTest` (`LIONPATHWAY_LIVE_TEST=1`, real DB and Gemini/Luminal/Tavily; every write is rolled back).

---

## 10. Remaining limitations

| Area | Details |
|---|---|
| Double counting | Only node-level `exclusive` is supported. Cross-program rules like "share at most N credits between major and minor" are not |
| Degree-wide conditions | Residency rules like "last N credits at PSU" can't be expressed without a filter on where courses were taken |
| Future offerings | Only a `typical_terms`-based prediction; actual offerings are confirmed only for collected terms |
| Credit transfer | Only items in public equivalency tables. Individual review results are entered by the user editing their record |
| Policy interpretation | Matters needing an exception are not judged; only the procedure and responsible office are given |

---

## 11. Agent routing rules (system prompt summary)

1. If the automatic context can answer, don't call a tool.
2. Answer credits, GPA, requirements, and eligibility only from computation tool results (`run_audit`, `what_if`, `check_prerequisites`, `validate_plan`, `get_credit_summary`, `gpa_what_if`). Never count yourself.
3. If a course or program name is ambiguous, confirm the code with `search_courses` / `search_programs` before continuing.
4. Answer deadlines and policy numbers only from `get_deadlines` / `get_policy_rule`, and show the applicable catalog year and source link.
5. Quote `search_policy` results for descriptive policies, with the policy number and link. When university and college policies both come back, show both.
6. Source precedence: computation tools, catalog, and policy data > `search_policy` > `web_search`.
7. State that credit-transfer results are "estimates based on the public equivalency table."
8. Never fill in `unknown`, `predicted`, or `DATA_NOT_AVAILABLE` with guesses; tell the user as is.
9. For matters that need an exception (credit overload, withdrawal after the deadline, requirement substitution), don't judge whether it's possible; explain the procedure and suggest seeing an adviser.
10. Save preferences, goals, and constraints the user states with `save_memory`, and apply the notes from the automatic context when recommending.
11. Propose record, plan, and program changes through write tools, and never say "saved" before approval.
12. When answering whether a student can graduate, state that it is not an official graduation audit.
13. Decline only the part of a request that breaks university rules (academic integrity, forged documents, misrepresentation, other students' records, scraping), name the policy area and give the legitimate route.

## 12. Gemini function declaration examples

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
