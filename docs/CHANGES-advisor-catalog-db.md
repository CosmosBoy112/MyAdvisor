# Change guide: Advisor catalog DB draft (`advisor-catalog-db`)

> For the **current state of the catalog DB actually loaded into TigerData** (tables created, row counts, sources, how to reload, verification queries), see the [DB load status](#db-load-status-2026-10-24) section at the bottom.

This document summarizes what this push added or changed and how it connects to the existing code.

## Summary

**No changes** to app code (backend or frontend). The Advisor agent DB schema draft, tool spec, 2026-27 Bulletin catalog seed data, and parser script were added **as new files only**.

## Branch makeup

| Base | Contents |
|---|---|
| `origin/main` (`5f4b4a9`) | Includes the latest UI, Luminal routing, and dark mode |
| `origin/backend` merge (`f60680d`) | Adds `AGENTS.md`, `CLAUDE.md`, `PROGRESS.md`; commits not yet on `main` |
| This commit | See "Added files" below |

The `VERIFICATION.md` merge conflict was resolved by keeping both sections ("Luminal routing added" and "Luminal live connection check").

## Added files

| Path | Description |
|---|---|
| `docs/advisor-schema.sql` | Advisor DB schema draft (PostgreSQL / TigerData) |
| `docs/advisor-tools.md` | Tool spec for the agent, routing rules, Gemini function declaration examples |
| `data/catalog/2026-27/courses.json` | Seed of 43 subjects, 302 courses |
| `data/catalog/2026-27/programs/DATSC_BS.json` | Data Sciences B.S. requirement tree (123 credits, APPLIED / COMPUTATIONAL / STATISTICAL options) |
| `data/catalog/2026-27/programs/DATSC_ETM.json` | Data Sciences Entrance to Major requirements |
| `ingest/parse_bulletin_courses.py` | Parser from saved Bulletin course page HTML → `courses.json` |
| `docs/CHANGES-advisor-catalog-db.md` | This document |

## 1. DB schema (`docs/advisor-schema.sql`)

**Not yet wired into the app.** `DatabaseConfig` still runs only `backend/src/main/resources/schema.sql`. This draft is separate from the existing `reports` tables.

| Section | Main tables | Purpose |
|---|---|---|
| 1. Reference data | `catalog_years`, `terms`, `grades` | Catalog years, terms, grade order |
| 2. Courses | `courses`, `course_versions`, `course_attributes`, `course_crosslists`, `prereq_nodes` | Per-year course info, GenEd attributes, prerequisite trees |
| 3. Programs | `programs`, `program_versions`, `program_options`, `requirement_nodes`, `requirement_rules`, `course_restrictions` | Major requirement trees and rules |
| 4. Offerings | `course_offerings` | Actual per-term offerings |
| 5. Credit transfer | `credit_sources`, `credit_equivalencies` | AP/IB and transfer credit tables |
| 6. Policies | `policy_rules`, `academic_calendar` | Numeric policies, academic calendar |
| 7. Users | `app_users`, `student_profiles`, `student_programs`, `student_courses`, `student_exceptions` | Student records |
| 8–9. Plans and audits | `plans`, `plan_courses`, `audit_snapshots` | Course plans, audit cache |
| 10. Conversations and notes | `conversations`, `conversation_messages`, `student_memories` | Conversation history, long-term notes |
| 11. Run logs | `agent_runs`, `agent_steps` | TimescaleDB hypertable, 90-day retention |
| 12. Policy search | `policy_chunks` (commented out) | Requires pgvector; optional |

Note: section 11 needs the `timescaledb` extension (installed by default on Tiger Cloud). It has not yet been run against local PostgreSQL.

## 2. Tool spec (`docs/advisor-tools.md`)

Defines agent tools on top of `advisor-schema.sql`.

- Catalog lookup: `search_courses`, `get_course`, `search_programs`, `get_program`, `get_offerings`
- Rule engine: `get_student_record`, `check_prerequisites`, `run_audit`, `what_if`, `find_courses_for_requirement`, `validate_plan`
- Credits: `get_credit_summary`, `gpa_what_if`, `lookup_credit_equivalency`
- Policies: `get_policy_rule`, `get_deadlines`, `search_policy`
- Other: `web_search`, `save_memory`, `forget_memory`, and 4 write tools that require approval

The core principle: "the LLM never counts credits, GPA, requirements, or eligibility itself; it answers from tool results" (routing rules in section 11). Example values in the document are synthetic and only illustrate the format.

## 3. Catalog data (`data/catalog/2026-27/`)

Each course in `courses.json` has `status` and `review`. **A person must check `review` entries before loading into the DB.**

| `status` | Count | Meaning |
|---|---|---|
| `auto_parsed` | 166 | Prerequisites parsed automatically into a tree |
| `reference_only` | 73 | Included for reference only |
| `text_only` | 27 | Didn't fit the grammar; kept as a raw TEXT node |
| `no_prereq` | 23 | No prerequisites |
| `not_in_course_list` | 13 | Not on a Bulletin page; uses fallback title and credits only |

Courses with `review`: 137 (e.g. "Could not parse prerequisite ...; kept as TEXT.").

## 4. Parser (`ingest/parse_bulletin_courses.py`)

```bash
python ingest/parse_bulletin_courses.py \
  --pages DIR --codes CODES.json --catalog-year 2026-27 --out courses.json
```

- `DIR`: a folder with `https://bulletins.psu.edu/university-course-descriptions/undergraduate/<subject>/` saved as `c_<subject>.html`
- `CODES.json`: `{"codes": [...], "fallback_titles": {"CODE": [title, credits]}}`
- Standard library only (no extra packages)
- The input HTML folder and `CODES.json` are not included in this commit

## Impact on existing code

- Backend/frontend source, `pom.xml`, `package.json`, `.env.example`: unchanged
- New environment variables: none
- Existing tests and builds: unaffected (new files are outside the build path)

## Verification status

- Checked: the 3 JSON files parse, the parser passes `py_compile`, no secrets included
- Not verified: running `advisor-schema.sql` on a real DB, loading the seed data, manual review of `review` entries
- Updated 2026-10-24: sections 1–6 executed and DATSC seed loaded ([DB load status](#db-load-status-2026-10-24)). Manual `review` and running sections 7–12 are still not verified

## Next steps

1. ~~Run `advisor-schema.sql` on the TigerData dev DB~~ → only sections 1–6 executed (2026-10-24)
2. Review the 137 `review` entries in `courses.json`, then write the seed loader (`courses` / `course_versions` / `prereq_nodes`) → loader written and data loaded. **`review` not yet done** (TEXT nodes loaded as-is)
3. ~~Load `DATSC_BS.json` → `requirement_nodes` / `requirement_rules`~~ → done (2026-10-24)
4. (Partly done: catalog context wired into chat, see "Chat integration" below) Implement backend tools starting with `check_prerequisites` and `run_audit`, then wire up Gemini function calling

## Additional CMPSC collection

For the follow-up University Park 2026–27 collection, the Handbook catalog-year conflict, and the scope of name matching, see the [CMPSC change log](CHANGES-cmpsc-catalog.md). The CMPSC comparison is now wired into the app and stored in the existing `reports.analysis` JSONB. Implementing the Advisor-specific DB schema and audit engine is separate work.

## DB load status (2026-10-24)

The catalog was loaded into TigerData so the app can answer Computational Data Sciences (DTSCE) questions. Chat reads these tables; see [Chat integration](#chat-integration-catalogdbservice) below. CMPSC_BS/CMPSC_ETM requirements were also added to the existing tables (see the CMPSC section below). The app's CMPSC comparison still uses the JSON-based `CatalogService` and was not switched to DB queries. Shared course/prerequisite data and existing DATSC rows were not changed.

### What was created

- **Only sections 1–6** of `docs/advisor-schema.sql` were executed (reference data, courses, programs, offerings, credit transfer, policies). All statements are `CREATE ... IF NOT EXISTS`.
- Sections 7–12 (student records, plans, audit cache, conversations, run-log hypertable, pgvector) were **not created**. The app's `app_users` / `reports` / `chat_messages` are untouched.
- Target DB: `TIGER_DATABASE_URL` from `backend/.env` (the same DB as the app)

### Load results

| Table | Rows | Contents |
|---|---|---|
| `catalog_years` | 1 | `2026-27` (start/end dates approximate, based on Summer 2026 admission) |
| `grades` | 12 | A–F grade points (Senate Policy 47-60), SA / UN / W |
| `courses` | 357 | Course codes, including codes that appear only in prerequisites, cross-lists, or requirement rules |
| `course_versions` | 337 | 2026-27 titles, credits, descriptions, raw prerequisite text. 20 referenced courses without a confirmed title/credits exist only in `courses` |
| `course_attributes` | 122 | GenEd and other attributes (GN, GQ, GS, WAC, GWS, ...) |
| `course_crosslists` | 25 | Cross-listed pairs |
| `prereq_nodes` | 880 | Prerequisite trees. 33 unparsed entries are `TEXT` nodes (raw text in `raw_text`) |
| `programs` / `program_versions` | 4 / 4 | `DATSC_BS`, `DATSC_ETM`, `CMPSC_BS`, `CMPSC_ETM` |
| `program_options` | 3 | `APPLIED`, `COMPUTATIONAL`, `STATISTICAL` |
| `requirement_nodes` / `requirement_rules` | 175 / 567 | DATSC 90/473 + CMPSC 85/94. CMPSC node metadata preserved as JSON in `source_text` |
| `policy_rules` | 10 | 5 DATSC + 5 CMPSC (keys described below) |
| `terms`, `course_offerings`, `credit_*`, `academic_calendar`, `course_restrictions` | 0 | Tables created only |

### Sources

| Source | Edition | Used for |
|---|---|---|
| [Bulletin: Data Sciences, B.S. (Engineering)](https://bulletins.psu.edu/undergraduate/colleges/engineering/data-sciences-bs/) | 2026-27 | Major requirements, List A/B (suggested plan footnotes), suggested semester plan, natural science exclusion list |
| [Bulletin course descriptions](https://bulletins.psu.edu/university-course-descriptions/undergraduate/) | 2026-27 | Courses and prerequisites. On 2026-10-24, 43 course pages were re-collected and compared with the existing seed: content identical; only 14 referenced courses with empty titles were filled in |
| [DTSCE Handbook](https://www.eecs.psu.edu/assets/docs/handbooks/DTSCE-handbook-2024-2025.pdf) | **2024-25** | Department List exclusions and caps, FYS substitution, accepted substitutions |

As of 2026-10-24, the 2024-25 DTSCE Handbook is the latest published on the EECS site (no 2025-26 or 2026-27 edition). The Bulletin's "LIST OF COMPUTATIONAL DATA SCIENCES COURSES" link also points there. Rules taken from the Handbook state "confirm 2026-27 applicability with an adviser" in their `description`.

Where the Handbook and Bulletin differ:
- Graduation credits: Handbook "at least 126" / Bulletin 2026-27 **123** → requirements use the Bulletin value; the Handbook wording is kept in `department_list_rules.value.note`
- List A/B: CMPSC/MATH 467, DS 300, MATH 436, MATH 465, and IST 441, which appear only in the Handbook, are not included. The Bulletin 2026-27 list (including DS 441 and MATH 486) is used
- Natural science exclusions: ASTRO 6 and GAME 180N appear only in the Handbook → the Bulletin list is used, and the difference is recorded in `description`

### `policy_rules` keys (`scope='program'`, `scope_code='DATSC_BS'`, `catalog_year='2026-27'`)

| key | Source | value |
|---|---|---|
| `suggested_academic_plan.COMPUTATIONAL` | Bulletin 2026-27 | 8-semester courses and credits (total 123, per-semester totals checked), `c_required`, `list_a`, `list_b` |
| `natural_science_exclusions` | Bulletin 2026-27 | 9 GN credits, excluded courses |
| `department_list_rules` | Handbook 2024-25 | 6 credits, caps (ROTC 3, etc.), excluded courses, ranges, and categories |
| `first_year_seminar_makeup` | Handbook 2024-25 | 1-credit substitution when there is no separate FYS, and the petition URL |
| `communications_substitutions` | Handbook 2024-25 | Substitutes for ENGL 15, CAS 100, MATH 230 |

### Reloading

```sh
python -m pip install -r ingest/requirements.txt
# Reads TIGER_DATABASE_* from backend/.env (environment variables take precedence)
python ingest/load_catalog_db.py --data data/catalog/2026-27 --programs DATSC_BS DATSC_ETM --dry-run   # insert, then roll back
python ingest/load_catalog_db.py --data data/catalog/2026-27 --programs DATSC_BS DATSC_ETM
```

- Runs in a single transaction. Rerunning replaces that catalog year's course versions, prerequisite trees, requirement trees, and policies (`courses` codes are kept).
- Regenerating the policy file: save the Bulletin page as HTML, then run `python ingest/build_datsc_policies.py PAGE.html data/catalog/2026-27/policies/DATSC_BS.json`. Handbook rules are transcribed into the script, so compare and update them by hand when a new Handbook is published.

### Checking in the TigerData console

[console.cloud.timescale.com](https://console.cloud.timescale.com) → your service (the first part of the `.env` host is the service ID) → SQL editor. Use only `SELECT` when just looking (it's the same DB as the app).

```sql
-- Computational option requirement tree
WITH RECURSIVE t AS (
  SELECT n.id, n.label, n.node_type, n.min_credits, 0 AS d
  FROM requirement_nodes n JOIN program_options o ON o.id = n.option_id
  WHERE o.code = 'COMPUTATIONAL' AND n.parent_id IS NULL
  UNION ALL
  SELECT c.id, c.label, c.node_type, c.min_credits, d + 1 FROM requirement_nodes c JOIN t ON c.parent_id = t.id)
SELECT repeat('  ', d) || label, node_type, min_credits FROM t ORDER BY id;

-- Prerequisites: courses unlocked by taking CMPSC 360
SELECT DISTINCT v.course_code, v.title
FROM prereq_nodes p JOIN course_versions v ON v.id = p.course_version_id
WHERE p.required_course = 'CMPSC 360';

-- Suggested semester plan / policy sources
SELECT jsonb_pretty(value -> 'terms') FROM policy_rules WHERE key = 'suggested_academic_plan.COMPUTATIONAL';
SELECT key, description, source_url FROM policy_rules ORDER BY key;
```

### CMPSC load, no schema changes

University Park 2026–27 `CMPSC_BS` and `CMPSC_ETM` were added with `ingest/load_cmpsc_db.py`. The existing `load_catalog_db.py` runs DDL and deletes before reloading, so it was not used for the CMPSC work.

- Uses only INSERT and SELECT, with no DDL, UPDATE, DELETE, or DROP. Rerunning with identical data compares against the existing tree and policies and creates no new rows. If existing CMPSC data differs, it rolls back instead of replacing it automatically.
- CMPSC_BS: minimum 127 credits, 78 requirement nodes, 87 rules. CMPSC_ETM: 7 nodes, 7 rules, GPA 3.20 and C-or-better conditions.
- The 75 linked course versions already existed in the DB and are reused. The 357 shared courses, 337 course versions, 880 prerequisite nodes, and DATSC requirements/policies are unchanged.
- A node's `source_text` is marked as **JSON metadata**, not a verbatim quote, and preserves `source`, `requires_review`, `review`, `credits_range`, and so on. AND/OR child structures and cross-listed alternatives are stored as-is in the requirement tree.
- An empty POOL, such as the Department List or the approved natural science list, does not mean any course counts. Tools reading the DB must also read the review conditions and scope in the snapshot below. This load does not implement an automatic graduation audit engine.
- Schema column/constraint/index fingerprints, plus row counts and content hashes of shared and other-major tables, are compared before and after loading. App report, user, and conversation tables are neither read nor written.

Added `policy_rules` (`scope='program'`, `catalog_year='2026-27'`):

| scope_code | key | Contents |
|---|---|---|
| CMPSC_BS | catalog_snapshot | Full source major JSON, source hashes, `automatic_audit_allowed=false`. Preserves campus, catalog year, GenEd overlap, and unconfirmed conditions |
| CMPSC_ETM | catalog_snapshot | Full source ETM JSON. Preserves enrollment, 29–55 graded PSU credits, AP/transfer exclusion, and admission term |
| CMPSC_BS | handbook_crosswalk | Differences between the 2024–25 Handbook and the 2026–27 Bulletin. `automatic_application_allowed=false`; historical limits are reference only |
| CMPSC_BS | course_matches | Code matches, sources, and prerequisite review status for 75 courses |
| CMPSC_BS | sources | Official URLs, collection times, source SHA-256 |

```sh
# Requires the existing schema and shared courses; does not create tables.
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

Sources are the 2026–27 [CMPSC Bulletin](https://bulletins.psu.edu/undergraduate/colleges/engineering/computer-science-bs/) and the 2024–25 undergraduate Handbook from the [EECS Handbook list](https://www.eecs.psu.edu/Students/undergraduate/Computer-Science.aspx). The historical Handbook is never applied automatically to current requirements. The app's current JSON-based matching flow was not changed.

### Chat integration (`CatalogDbService`)

For every chat request, `ReportController` attaches the result of `CatalogDbService.context(report, question)` as `report.catalogDb` and sends it to Gemini/Luminal. Uploaded and saved reports are not modified.

- **Major match rule**: matches only when the report `major` contains every word of a `programs` name (program_type `major`) and the remaining words are only degree, campus, college, or option words. Parentheses in program names (e.g. `(Engineering)`) are ignored. Examples: `Data Sciences (BS)` → DATSC_BS, `Computer Science (BS)` → CMPSC_BS; `Comp Sci - Univ Park (BS)` (abbreviated), `Computer Engineering (BS)`, and `Data Sciences Minor` do not match. Newly loaded majors become eligible without code changes. Campus is not checked (CMPSC_BS data is University Park).
- **status**: `matched` (catalog year matches), `matched_year_differs` (e.g. report Fall 2024 → 2024-25 vs DB 2026-27; the answer must state the difference), `matched_year_unknown`, `major_not_in_catalog` (catalog questions are not answered from general knowledge), `catalog_unavailable` (on DB error, answers come from the report only)
- **Options**: if the major name contains an option word, or exactly one option appears in the report's requirement titles (e.g. "Computational Option List A"), only that option's requirements are included. Otherwise all options are included and no option is assumed
- **Included**: common and option requirement trees (course titles and credits), entrance-to-major requirements, `policy_rules` (Handbook-sourced rules state their edition and recommend confirming with an adviser), and for up to 8 courses mentioned in the question: credits, attributes, cross-lists, prerequisite tree, and courses they unlock
- **CMPSC load format**: when `source_text` is "catalog metadata (not a verbatim quote)", it is not passed as Bulletin text (`bulletin_text`); only `requires_review`, `review`, `credits_range`, and (for review nodes) `source_url` are passed. A POOL with no rules is `course_list: not_listed_in_database` (not "any course counts"). Policy values over 8,000 characters (`catalog_snapshot`, etc.) are marked `value_omitted` and excluded
- **Context size** (measured): Data Sciences about 17 KB, CMPSC about 26 KB
- **Rules** (`GeminiService.CHAT_SYSTEM`): cite source URLs, quote `unparsed_text` prerequisites without interpreting them, never change the report's completion status or credits. Existing rules for `report.catalog` (the JSON-based CMPSC comparison) are kept
- Tests: `CatalogDbServiceTest` (major, catalog year, course codes, DB errors), `LiveCatalogDbTest` (`LIONPATHWAY_LIVE_TEST=1`, read-only queries on the real DB)

### 2026-10-24 · Restoring completed items in an app report

No catalog table or schema changes. Added 28 missing Satisfied sections, verified against the PDF source text, to one existing CS report in `reports.analysis` (19 complete, 6 dependent on IP, 3 needing course-table verification). The existing 2 incomplete items, overall credits 127/74 earned/30 IP, and conversation history were kept. The original was backed up to a private working folder outside Git, updated only if it matched the existing JSON, and verified from a separate connection. `RequirementReconciler` applies the same restoration to new PDF uploads. The completed count is the number of report requirement sections, not a sum of credits or courses that may overlap.

### 2026-10-24 · Adding applied course details

Filled in the per-requirement `appliedCourses`/`appliedCoursesVerified` fields for the same app report. For the 24 requirements whose source course-table totals match `used`, stored 30 course assignments (including duplicates) with code, title, term, credits, grade/IP, and page. Uncertain tables were left empty and existing status/credits kept. Separate private backup, conditional update, and comparison from a new connection completed. No catalog or DB schema changes.

### 2026-10-24 · Math minor reference requirements and course history

Added via INSERT one `MATH_UMNR` minor program, one 2026–27 version (minimum 26 credits), and one `minor_audit_rules_v1` policy snapshot. The 4 requirement groups, grades, overlap and individual credit limits, and source conflicts are kept in the policy JSON and not duplicated into `requirement_nodes`. No existing tables, schema, or catalog rows were modified. Using the dedicated `ingest/load_math_minor_db.py`: `--dry-run` rollback → real load → rerun no-op and identical schema fingerprint confirmed. `MathMinorService` reads the DB snapshot to compute the Math minor comparison; see `docs/MATH-MINOR.md` for scope.

Added a `courseInventory` extracted from the provided PDF (37 courses / per-term enrollment history, with repeated requirement entries deduplicated) to one existing user CS report. Overall credits, 30 requirements, and conversation history were kept. Original backed up privately outside Git, conditionally UPDATEd, then compared from a new connection. This PDF uses 2023 requirements, so the 2026–27 minor comparison is for reference only.


### 2026-10-24: Last-report deletion protection verification

The app now requires each account to retain at least one report. No schema or catalog changes. Account-row locking, ownership checks, and report counting run in one transaction. LiveDatabaseTest created a synthetic account and two reports, verified last-report HTTP 409 protection, rejected another user's deletion, and checked deletion of one of two reports with cascading conversation removal. All test rows were rolled back. No real user reports were modified or deleted.
