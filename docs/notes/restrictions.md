# Enrollment restrictions (`course_restrictions`)

Status: parser, loader and backend evaluation are in `main`. The table is still empty; the load has only been run with `--dry-run`.

## What was added
- `ingest/load_course_restrictions.py` parses the enforced part of each 2026-27 prerequisite text (`courses.json` `prereq_text`, text before "Recommended Preparation") into `course_restrictions` rows for that year's course versions. `--dry-run` inserts inside a transaction and rolls back. It never deletes: a row is inserted only when an identical row is not already there, and rows the current parse does not produce are reported as `stale` and left in place.
- `CatalogDbService.restrictions(versionId)` reads the rows.
- `check_prerequisites` now returns `restrictions` (each with `result` met / in_progress / not_met / unknown and `reason`), keeps the tree result as `prerequisiteStatus`, and sets `status` to the ALL-combination of both, so a restriction that is not met, or cannot be verified, never leaves the course reading as `met`.
- `STANDING` nodes in prerequisite trees are no longer always `unknown`; they use the same standing estimate.

## Parser rules
| Wording | Row |
|---|---|
| "third-semester standing", "5th semester standing", "4th Semester" | `min_semester` = the ordinal |
| "Sophomore / Junior / Senior standing" | `min_semester` = 3 / 5 / 7 (first semester of that class in the table below) |
| instructor approval, permission, consent, "restricted to", "majors only", "open only to", campus wording | `text`, phrase kept verbatim; always evaluated as `unknown` |
| "or Graduate Status" | not a restriction: an alternative way to meet the prerequisite, left to the prerequisite tree |
| placement exams, "3 credits in ECON" | not restrictions: prerequisites, left to the prerequisite tree |

## Semester standing
Estimated from the report's `totals.completed` (and `totals.inProgress`) with the Bulletin table [Classification of Students by Semester](https://bulletins.psu.edu/undergraduate/general-information/academic-information/registration-academic-records/classification-students-semester/): 14.0 or fewer credits = 1, 14.1-29 = 2, 29.1-44 = 3, 44.1-59 = 4, 59.1-74 = 5, 74.1-89 = 6, 89.1-104 = 7, 104.1-119 = 8, 119.1-134 = 9, 134.1-149 = 10, 149.1 or more = 11. `met` when completed credits reach the semester, `in_progress` when only completed plus in-progress credits do, otherwise `not_met`; `unknown` when the report has no completed total. Results carry `estimate: true` and the tool adds a warning, because LionPATH uses total credits earned (including transfer and AP credit), which the degree audit total may not equal. (The Bursar's tuition page lists different class ranges; the Bulletin table is the one used for semester classification.)

Program rows compare with the report's matched catalog program, campus rows with the report's campus, and college rows are `unknown` (the report does not give the college). `include = false` rows flip met and not_met.

## Dry run (TigerData, 2026-27, rolled back)
7 courses, 7 rows: `min_semester` 6 (BLAW 243, FIN 100 = 3; IB 303, NUTR 410, FDSC 404 = 5; ENGL 202C = 4), `text` 1 (IST 495 instructor approval). Missing course versions 0, already present 0, stale 0.

## Limits
- The Bulletin prints no major, college or campus restrictions for the loaded courses. Those are set in LionPATH class search ("Class Details") and stay unchecked; the tool warning says so.
- Only 2026-27. Rerun the loader after loading another catalog year, and after `load_catalog_db.py` replaces course versions (course version ids are kept on reload, so rows survive).
- To load for real: `python ingest/load_course_restrictions.py --data data/catalog/2026-27 --dry-run`, then without `--dry-run`.
