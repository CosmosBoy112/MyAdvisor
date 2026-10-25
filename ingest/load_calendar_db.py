"""Load data/calendar/<year>.json into terms and academic_calendar (docs/advisor-schema.sql section 6).

Usage:
  python ingest/load_calendar_db.py --data data/calendar/2026-27.json [--dry-run]

Connection settings come from backend/.env like ingest/load_catalog_db.py. Runs in one transaction and never
deletes: terms are upserted by code, and each event is updated in place by (term_code, event_key, campus) or
inserted, so re-running after a Registrar change updates dates without removing rows. The catalog year must
already exist in catalog_years.
"""
import argparse, json, pathlib, sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from load_catalog_db import settings  # noqa: E402


def load(cur, data):
    stats = {"terms_inserted": 0, "terms_updated": 0, "events_inserted": 0, "events_updated": 0}
    cur.execute("SELECT 1 FROM catalog_years WHERE code=%s", (data["catalog_year"],))
    if cur.fetchone() is None:
        raise SystemExit(f"catalog_years has no {data['catalog_year']} row; load that catalog year first")
    for t in data["terms"]:
        cur.execute("""INSERT INTO terms (code, season, year, starts_on, ends_on, catalog_year) VALUES (%s, %s, %s, %s, %s, %s)
                       ON CONFLICT (code) DO UPDATE SET starts_on=EXCLUDED.starts_on, ends_on=EXCLUDED.ends_on, catalog_year=EXCLUDED.catalog_year
                       RETURNING (xmax = 0)""",
                    (t["code"], t["season"], t["year"], t["starts_on"], t["ends_on"], t["catalog_year"]))
        stats["terms_inserted" if cur.fetchone()[0] else "terms_updated"] += 1
    for e in data["events"]:
        row = (e["label"], e["starts_on"], e["ends_on"], e["source_url"], e["retrieved_on"])
        cur.execute("""UPDATE academic_calendar SET label=%s, starts_on=%s, ends_on=%s, source_url=%s, fetched_at=%s::date
                       WHERE term_code=%s AND event_key=%s AND campus IS NOT DISTINCT FROM %s""",
                    row + (e["term_code"], e["event_key"], e["campus"]))
        if cur.rowcount:
            stats["events_updated"] += 1
            continue
        cur.execute("""INSERT INTO academic_calendar (term_code, event_key, label, starts_on, ends_on, campus, source_url, fetched_at)
                       VALUES (%s, %s, %s, %s, %s, %s, %s, %s::date)""",
                    (e["term_code"], e["event_key"], e["label"], e["starts_on"], e["ends_on"], e["campus"], e["source_url"], e["retrieved_on"]))
        stats["events_inserted"] += 1
    return stats


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True)
    ap.add_argument("--dry-run", action="store_true", help="load inside a transaction, print counts, then roll back")
    args = ap.parse_args()
    import psycopg
    data = json.loads(pathlib.Path(args.data).read_text())
    with psycopg.connect(**settings()) as conn:
        with conn.cursor() as cur:
            stats = load(cur, data)
        if args.dry_run:
            conn.rollback()
        print(("rolled back " if args.dry_run else "loaded ") + json.dumps(stats))


if __name__ == "__main__":
    main()
