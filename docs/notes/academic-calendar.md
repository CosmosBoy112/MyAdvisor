# Academic calendar and `get_deadlines`

## Source
- Penn State Office of the University Registrar, 2026-27 Academic Calendar: https://www.registrar.psu.edu/academic-calendars/2026-27.cfm (retrieved 2026-10-25, one request; the index page is https://www.registrar.psu.edu/academic-calendars/).
- The page covers Fall 2026, Spring 2027 and Summer 2027. It states that the calendar is subject to change without notice. Commencement is footnoted "These dates may vary among campuses", so it is loaded with `campus='UP'`; every other date has `campus` null (all campuses). Drop/add and late registration rows are footnoted "These dates apply to full semester courses". The page does not list World Campus-specific dates.

## What is loaded
Only the **Regular Session** of each term. `academic_calendar` has no session column, so the First/Second 7-Week Sessions (fall, spring), Maymester and First/Second 6-Week Sessions (summer) would collide with the regular deadlines under the same `event_key`. They are not loaded; the tool warns that these sections have their own deadlines.

| Term | Events | Range (classes begin – last final) |
|---|---|---|
| FA26 | 20 | 2026-08-24 – 2026-12-18 |
| SP27 | 20 | 2027-01-11 – 2027-05-07 |
| SU27 | 19 | 2027-05-17 – 2027-08-13 |

Event keys: `intent_to_graduate`, `leave_of_absence`, `registration`, `classes_begin`, `regular_drop`, `regular_add`, `late_drop_begins`, `late_registration_begins`, `final_exam_conflict_filing`, `late_registration`, `late_add`, `late_drop`, `declare_concurrent_majors`, `withdrawal`, `classes_end`, `study_days`, `final_exams`, `commencement`, and one key per holiday (for example `labor_day_holiday_no_classes`). The table has no time or note column, so the deadline time and the full-semester note are part of `label` (for example "Late Drop - Deadline, 11:59 p.m. ET (full-semester courses)"). `terms.catalog_year` is `2026-27` for all three terms, including Summer 2027, as on the Registrar page.

## Files
- `ingest/parse_registrar_calendar.py`: saved Registrar page → `data/calendar/<year>.json`. It never fetches. Every date is checked against the weekday printed on the page.
- `data/calendar/2026-27.json`: parsed data with `source_url` and `retrieved_on` per row.
- `ingest/load_calendar_db.py`: loads `terms` and `academic_calendar` in one transaction with `--dry-run`. It never deletes: terms are upserted by code and events are updated in place by `(term_code, event_key, campus)` or inserted.
- Backend: `CalendarRepository` (read-only queries), the `get_deadlines` tool in `AdvisorTools`, and `upcomingDeadlines` (deadlines in the next 14 days, at most 8) in the agent context.

## Dry run (2026-10-25, TigerData, rolled back)
`terms_inserted 3, events_inserted 59`. A second load inside the same transaction updated 3 terms and 59 events and inserted nothing. The term/event queries returned FA26 as the current term and the late drop (2026-11-13) and withdrawal (2026-12-11) rows. The real load has not been run.

## Refresh each term
1. Save the Registrar page for the academic year (one request) and run `parse_registrar_calendar.py` with `--url`, `--retrieved` and `--catalog-year`.
2. Review the diff of the JSON file, then run `load_calendar_db.py --dry-run` and the real load.
3. The tool warns when `fetched_at` is older than 30 days, so re-retrieve at the start of each term and whenever the Registrar announces a change. A new academic year needs its `catalog_years` row first.

## Limits
- Regular Session only, as above. Supporting other sessions needs a `session` column (or table) in the schema.
- Campus-specific dates other than University Park commencement are not loaded.
- `days_left` is computed in America/New_York time from the server date.
