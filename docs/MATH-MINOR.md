# Math minor preview (University Park, 2026–27)

## Implemented flow

1. `CourseInventory` extracts all recognizable course-table rows in the PDF, including completed, planned and non-passing attempts. Repeated appearances of the same course/term are deduplicated. Conflicting grades/credits are flagged; unreadable rows remain visible as an incomplete-data count. This is PDF coverage, not a certified full transcript. It exports course fields only, not student header information.
2. `data/catalog/2026-27/programs/MATH_UMNR.json` stores the dated official reference rules, their sources and conflicts. `ingest/load_math_minor_db.py` adds the minor program, version and a structured `policy_rules` snapshot without DDL, updates or deletion. Run `--dry-run` first. Identical reruns are no-ops; differing existing data aborts.
3. `MathMinorService` compares the inventory with four course groups. C-or-better attempts count as earned; IP counts conditionally toward the plan. Unknown grade equivalences, conflicting records and sub-C grades are not automatically counted. Repeat appearances/attempts do not inflate totals. Known overlapping course pairs count once and documented per-course caps apply. Missing inventory yields unknown, not a 26-credit deficit.
4. The saved-report view contains a Math minor panel with group progress, matched courses, remaining minimum credits, possible options, prerequisite text, excluded records, course history and source links. The question button fills the chat input. The same server result is attached to chat; answers explain it rather than inventing a new audit.

## Sources and limits

- [2026–27 Mathematics, Minor (Science) Bulletin](https://bulletins.psu.edu/undergraduate/colleges/eberly-science/mathematics-minor/): 26–28 total, MATH 140/141, 6–8 from the named additional pool, 12 of 400-level MATH; C or better; six credits distinct from major prescribed courses; MATH 400 cap.
- [Math department guidance](https://science.psu.edu/math/undergraduate/minors): includes additional courses not present in the current Bulletin and a MATH 497 learning-assistant cap. Conflicts remain explicit, not silently merged. The preview conservatively caps MATH 400 and 497 at three pending confirmation and follows the Bulletin pool.
- Existing sourced MATH course facts supply option titles, credit ranges and prerequisite text. These are candidates, not confirmed permission to register. Known MATH 414/418, 451/455 and 436/441 overlap restrictions are not an exhaustive equivalence engine.

The 6-credit separation from major prescribed courses, minor declaration, substitutions/crosslists, all repeat/duplicate rules and course availability require confirmation. The engine always identifies itself as a reference estimate. It does not modify major progress or claim minor approval. A different/unknown campus or catalog year is prominently flagged. Current scope is the Science Mathematics minor, not Behrend/Abington variants or other minors.

## Storage and compatibility

`MathMinorService` reads the database's `minor_audit_rules_v1` snapshot when it matches the bundled reviewed version; otherwise it uses the bundled version and exposes that storage status. `GET /api/reports/{id}` and chat calculate fresh `minorAssessment`. New uploads save `courseInventory`; historical reports without inventory ask for a fresh PDF. No schema migration is required. The existing user CS report was backfilled from its provided PDF, with a private backup, conditional update and read-back verification; other reports were not changed.

## Verification · 2026-10-24

Frontend: 26 tests and build pass. Backend: 62 total; full suite with local PDF enabled passed 56 with six opt-in checks skipped, then the separate live minor test passed (combined 57 passed, five skipped). Tests cover duplicate attempts/rows, C-/unknown grades, IP coverage, caps, overlapping pairs, missing history, year mismatch, and UI warning/unsafe-link handling. Actual Luminal synthetic test returned the numerical gap, catalog mismatch, unique-credit condition and official source (HTTP 200).

The provided PDF yielded 37 deduplicated course records and two unreadable row occurrences; its reference estimate is 19 matched credits and at least seven additional 400-level credits, subject to all checks above. Its requirement year is Spring 2023, so this is not its official applicable audit. Authenticated browser end-to-end testing was not performed.
