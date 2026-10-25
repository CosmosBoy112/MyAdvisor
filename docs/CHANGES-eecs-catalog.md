# University Park EECS majors · 2026–27

The catalog database now covers every University Park major of the School of Electrical Engineering and Computer Science, majors only (no minors), for the 2026–27 Bulletin only:

| Program | Code | Source | Status |
|---|---|---|---|
| Computer Science, B.S. | `CMPSC_BS`, `CMPSC_ETM` | Earlier load (`docs/CHANGES-cmpsc-catalog.md`) | unchanged |
| Data Sciences, B.S. | `DATSC_BS`, `DATSC_ETM` | Earlier load | unchanged |
| Computer Engineering, B.S. | `CMPEN_BS`, `CMPEN_ETM` | [Bulletin](https://bulletins.psu.edu/undergraduate/colleges/engineering/computer-engineering-bs/) | new |
| Electrical Engineering, B.S. | `EE_BS`, `EE_ETM` | [Bulletin](https://bulletins.psu.edu/undergraduate/colleges/engineering/electrical-engineering-bs/) | new |

## What was collected
- **Requirements**: every row of each program's requirement table (Prescribed, Additional, Supporting), with C-or-better sections, footnoted exceptions (CMPSC 122 and CMPEN 275 do not require a C), OR rows as `GROUP_ANY`, and course pairs (CMPEN 271 + 275, MATH 231 + 232) as `GROUP_ALL`. General Education, US/IL/WAC and the 2.00 graduation GPA use the same nodes as CMPSC.
- **Credits**: both 127 credits. CMPEN major 106–108 (24 GenEd credits overlap), EE major 109–111 (27 overlap).
- **Entrance to Major (University Park)**: CMPEN: administrative enrollment controls, Engineering or DUS, 29–55 graded Penn State credits, C or better in CMPSC 121/131, CHEM 110, MATH 140, MATH 141, PHYS 211, GPA 3.20. EE: same credit window and courses (CMPSC 121/131 or EDSGN 100), GPA 2.60; the Bulletin warns a higher GPA is likely if the major goes under enrollment control.
- **Suggested academic plan**: stored as `suggested_academic_plan.DEFAULT` (these programs have no options), shown by the `semester_plan` screen.
- **Courses**: 36 new course codes (33 with a 2026-27 version) and prerequisite trees for 4 courses that were reference-only before (CAS 137H, CAS 138T, IE 424, STAT 401). Descriptions are not stored, as for CMPSC.

## Not resolved (kept as `requires_review`)
- EE "program-approved list" electives (6 credits at 300 level, 3 at 300/400, 6 at 400) and the 6 additional credits (ROTC, co-op and others): the Bulletin names the lists but does not enumerate them. They are empty POOLs with `requires_review`, never "any course".
- Entrance-to-major eligibility fields (credit origin, current college, enrollment control) cannot be enforced by the requirement tree.

## Files
- `ingest/crawl_eecs_catalog.py`: crawler. Only `bulletins.psu.edu` program pages and `/university-course-descriptions/` are fetched (robots.txt disallows `/archive/` and `/pdf/`, which are never requested), one request per page with a one-second pause, cached outside the repo. Any unrecognized row, changed edition or missing course stops the crawl.
- `ingest/load_eecs_db.py`: insert-only loader (no DDL, UPDATE or DELETE). Every row that existed before the load and every other program is fingerprinted and must be unchanged; `--dry-run` rolls back.
- `ingest/test_eecs_catalog.py`: structure, footnote, review and loader-bundle tests.
- `data/catalog/2026-27/programs/{CMPEN,EE}_{BS,ETM}.json`, `policies/{CMPEN,EE}_BS.json`, `references/EECS_course_matches.json`, `references/EECS_sources.json` (URLs, SHA-256, download times).

## Running
```sh
python ingest/crawl_eecs_catalog.py --cache /tmp/eecs-sources --out data/catalog/2026-27   # --offline reuses the cache
python -m unittest discover -s ingest -p 'test_*.py'
python ingest/load_eecs_db.py --data data/catalog/2026-27 --dry-run
python ingest/load_eecs_db.py --data data/catalog/2026-27
```

## Backend changes
- Major matching (`CatalogDbService.matchMajor`) now prefers the program whose name appears as a phrase in the report's major, then the longer name, so "Computer Science (Engineering)" stays CMPSC when Computer Engineering is loaded. "technology" is no longer a neutral word, so Electrical Engineering Technology does not match Electrical Engineering.
- `semester_plan` uses `suggested_academic_plan.DEFAULT` for programs without options.
