# University Park Computer Science · 2026–27

We collected the CMPSC_BS (University Park) requirements and courses from the official Bulletin and cross-checked them against the undergraduate Handbook linked from the official EECS page. CSENG_BS at other campuses is not included.

## What was collected

- Degree minimum 127 credits, major 106–108 credits, General Education 45 credits. The major and GenEd overlap by 24 credits, so the items must not simply be summed.
- All 75 course codes that appear in the major requirements were found across 8 course pages. 14 courses whose prerequisite interpretation needs review are not eligible for automatic satisfaction.
- CMPSC_ETM stores University Park's GPA 3.20, 29–55 graded Penn State credits, and C-or-better prerequisite conditions separately. AP/transfer credits and current enrollment status need additional verification.
- The structures CMPEN 270 or (271 AND 275) and MATH 230/230H or (231 AND 232) are preserved. STAT/MATH cross-listed codes are alternatives and are not counted as double credit.

## Handbook catalog-year conflict

When collected on 2026-10-24, the latest undergraduate Handbook linked from the official EECS page was 2024–25. The PDF cover and link say 2024–25, but page 18 also refers to students admitted in 2021–22, so the applicable year can't be confirmed. It is not applied automatically as 2026–27 rules.

| Item | 2026–27 Bulletin | Earlier Handbook | Matching treatment |
|---|---|---|---|
| Department List | 6 credits | 10–14 credits | Names linked, credit conflict flagged. Approved course list needs confirmation |
| CS technical electives | 18 credits from 27 listed codes | 12 credits of CS electives with a separate structure | Use the current list; never auto-substitute the old structure |
| Supporting Electives | No equivalent standalone requirement found | 6 credits of topic-relevant advanced courses | Don't convert to current natural science / Department List |
| Natural science elective | PHYS 213 or 214, or 3 credits from an approved list | Earlier guidance | Approved-list branch needs confirmation |
| Writing | CMPSC 483W is in the required list | Earlier elective structure | Don't assume earlier electives substitute |

An empty approved list does not mean "any course counts." `requires_review` and unresolved branches must be respected. Handbook exclusions and caps are stored only in `historical_*`. Whether a course exists and whether it counts toward a requirement are separate judgments.

## Files

- `data/catalog/2026-27/programs/CMPSC_BS.json`: current degree requirement tree
- `data/catalog/2026-27/programs/CMPSC_ETM.json`: current entrance-to-major conditions
- `data/catalog/2026-27/courses.json`: official course data merged into the existing catalog
- `data/catalog/2026-27/references/CMPSC_course_matches.json`: per-code source and prerequisite review status
- `data/catalog/2026-27/references/CMPSC_handbook_crosswalk.json`: report item names, conflicts, historical guidance
- `data/catalog/2026-27/references/CMPSC_sources.json`: URLs, collection times, source SHA-256
- `ingest/crawl_cmpsc_catalog.py`: re-collection script. Source HTML/PDF are kept in a separate cache
- `ingest/match_cmpsc_requirements.py`: explicit-alias matching CLI. Catalog year and campus are required; ambiguous List A/B stays unmatched

## Running

Requires Python 3.10+, `lxml`, and `pypdf`.

```sh
python -m pip install -r ingest/requirements.txt
python ingest/crawl_cmpsc_catalog.py --cache /tmp/cmpsc-sources --out data/catalog/2026-27
# Reproduce from cache: --offline / re-fetch sources: --refresh
python ingest/match_cmpsc_requirements.py 'Department List' --catalog-year 2026-27 --campus UP
python -m unittest discover -s ingest -p 'test_cmpsc_catalog.py' -v
```

The crawler stops and asks for review when the Bulletin year changes or a new Handbook is linked. Reuse the same collection directory for the cache without changing source URLs. Review and commit the results after re-collecting.

## Scope in the app

Beyond the catalog seed and the Python matching tool, the Spring Boot `CatalogService` reads the JSON bundled at Maven build time. For a new PDF, major, campus, and catalog year are compared after Gemini extraction and evidence validation. When the metadata and source text confirm them, items are matched by exact alias. This confirms the wording exists in the document; it is not an academic audit of an entire multi-program document. Other years, campuses, or unconfirmed values are never applied automatically.

The comparison (`catalog`, `requirements[].catalogMatch`) is stored alongside the existing `reports.analysis` JSONB, and saved analyses follow the existing ownership and deletion policy. The CMPSC source requirements were later also loaded into the existing Advisor catalog tables without schema changes; see the CMPSC section of `CHANGES-advisor-catalog-db.md`. The app comparison still reads the JSON. Report views, exports, and Gemini/Luminal chat all use the same sources and review information. Previously saved reports have no source text, so they show `scope_unverified` or `scope_not_matched` until re-uploaded.

Vue shows the overall comparison status, required-credit conflicts, official source links, electives with explicit lists, and prerequisite review status. The report's credits and completion status are kept. Approved natural science lists, historical Supporting Electives, and ambiguous List A/B are never accepted automatically. Build Docker from the repository root with `docker build -f backend/Dockerfile -t lionpathway-api .`.

A future loader/audit engine must handle unresolved lists, GenEd overlap/distribution, credit sources, minimum grades, and repeated/equivalent courses separately. Don't drop metadata the current schema draft can't represent and then declare a student eligible to graduate. Don't overwrite the report's stated catalog year or remaining credits with these name-matching results.

## Official sources

- [Computer Science B.S. Bulletin](https://bulletins.psu.edu/undergraduate/colleges/engineering/computer-science-bs/)
- [EECS Computer Science undergraduate Handbook list](https://www.eecs.psu.edu/Students/undergraduate/Computer-Science.aspx)
- [2024–25 Handbook PDF](https://www.eecs.psu.edu/assets/docs/handbooks/CMPSC-handbook-2024-2025.pdf)
