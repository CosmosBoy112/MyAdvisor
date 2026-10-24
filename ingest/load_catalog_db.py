"""Load catalog seed JSON into the advisor catalog tables (docs/advisor-schema.sql sections 1-6).

Usage:
  python ingest/load_catalog_db.py --data data/catalog/2026-27 --programs DATSC_BS DATSC_ETM [--dry-run]

Connection settings come from backend/.env (TIGER_DATABASE_URL / _USER / _PASSWORD) or the environment.
Runs in one transaction. Re-running replaces the loaded catalog year's versions, prerequisite trees,
requirement trees and policy rules; it never touches the app tables (app_users, reports, chat_messages).
"""
import argparse, json, os, pathlib, re

ROOT = pathlib.Path(__file__).resolve().parents[1]
CATALOG_YEARS = {"2026-27": ("2026-05-01", "2027-04-30")}  # approximate: the edition applies from Summer 2026 entry
# Senate Policy 47-60 grade points; SA/UN are satisfactory/unsatisfactory, W is withdrawal.
GRADES = [("A", 12, 4.0, True), ("A-", 11, 3.67, True), ("B+", 10, 3.33, True), ("B", 9, 3.0, True),
          ("B-", 8, 2.67, True), ("C+", 7, 2.33, True), ("C", 6, 2.0, True), ("D", 4, 1.0, True),
          ("F", 1, 0.0, False), ("SA", 5, None, True), ("UN", 0, None, False), ("W", 0, None, False)]


def settings():
    vals = dict(os.environ)
    env = ROOT / "backend" / ".env"
    if env.exists():
        for line in env.read_text().splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                vals.setdefault(k, v)
    m = re.match(r"jdbc:postgresql://([^:/]+):(\d+)/([^?]+)\?(.*)", vals["TIGER_DATABASE_URL"])
    host, port, db, query = m.groups()
    sslmode = re.search(r"sslmode=([\w-]+)", query).group(1)
    return dict(host=host, port=port, dbname=db, user=vals["TIGER_DATABASE_USER"],
                password=vals["TIGER_DATABASE_PASSWORD"], sslmode=sslmode, connect_timeout=15)


def catalog_ddl():
    sql = (ROOT / "docs" / "advisor-schema.sql").read_text()
    start, end = sql.index("-- 1. Reference"), sql.index("-- 7. User state")
    return sql[sql.rindex("\n", 0, start):sql.rindex("-- ====", 0, end)]


def code_parts(code):
    subject, number = code.rsplit(" ", 1)
    return subject, number, int(re.match(r"\d+", number).group()) // 100 * 100


def walk(node, acc, key="course"):
    if node:
        if node.get(key):
            acc.add(node[key])
        for child in node.get("children", []):
            walk(child, acc, key)
    return acc


def load(cur, data, program_codes):
    year = json.loads((data / "courses.json").read_text())["catalog_year"]
    courses = json.loads((data / "courses.json").read_text())["courses"]
    programs = [json.loads((data / "programs" / f"{code}.json").read_text()) for code in program_codes]
    policies = [json.loads(p.read_text()) for p in sorted((data / "policies").glob("*.json"))
                if p.stem in program_codes] if (data / "policies").exists() else []
    stats = {}

    cur.execute(catalog_ddl())
    starts, ends = CATALOG_YEARS[year]
    cur.execute("INSERT INTO catalog_years VALUES (%s,%s,%s) ON CONFLICT (code) DO UPDATE SET starts_on=EXCLUDED.starts_on, ends_on=EXCLUDED.ends_on",
                (year, starts, ends))
    cur.executemany("INSERT INTO grades VALUES (%s,%s,%s,%s) ON CONFLICT (grade) DO UPDATE SET rank=EXCLUDED.rank, points=EXCLUDED.points, earns_credit=EXCLUDED.earns_credit",
                    GRADES)

    # Every code mentioned anywhere needs a courses row for the foreign keys.
    codes = {c["code"] for c in courses}
    for c in courses:
        walk(c.get("prereq"), codes)
        codes.update(c["crosslists"])
    for p in programs:
        for node in p["requirements"]:
            stack = [node]
            while stack:
                n = stack.pop()
                codes.update(r["course"] for r in n.get("rules", []) if r.get("course"))
                stack.extend(n.get("children", []))
    cur.executemany("INSERT INTO courses VALUES (%s,%s,%s,%s) ON CONFLICT (code) DO NOTHING",
                    [(code, *code_parts(code)) for code in sorted(codes)])
    stats["courses"] = len(codes)

    versions = 0
    for c in courses:
        if not c.get("title") or c.get("credits_min") is None:
            continue  # code-only reference: no verified title or credits to store
        cur.execute("""INSERT INTO course_versions (course_code, catalog_year, title, credits_min, credits_max, description, prereq_text, repeatable, source_url)
                       VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)
                       ON CONFLICT (course_code, catalog_year) DO UPDATE SET title=EXCLUDED.title, credits_min=EXCLUDED.credits_min,
                         credits_max=EXCLUDED.credits_max, description=EXCLUDED.description, prereq_text=EXCLUDED.prereq_text,
                         repeatable=EXCLUDED.repeatable, source_url=EXCLUDED.source_url
                       RETURNING id""",
                    (c["code"], year, c["title"], c["credits_min"], c["credits_max"], c.get("description"),
                     c.get("prereq_text"), c.get("repeatable", False), c.get("source_url")))
        vid = cur.fetchone()[0]
        versions += 1
        cur.execute("DELETE FROM course_attributes WHERE course_version_id=%s", (vid,))
        cur.execute("DELETE FROM prereq_nodes WHERE course_version_id=%s", (vid,))
        cur.executemany("INSERT INTO course_attributes VALUES (%s,%s)", [(vid, a) for a in sorted(set(c["attributes"]))])

        def insert_prereq(node, parent, order):
            cur.execute("""INSERT INTO prereq_nodes (course_version_id, parent_id, sort_order, node_type, required_course, min_grade, min_semester, raw_text)
                           VALUES (%s,%s,%s,%s,%s,%s,%s,%s) RETURNING id""",
                        (vid, parent, order, node["type"], node.get("course"), node.get("min_grade"),
                         node.get("min_semester"), node.get("raw")))
            nid = cur.fetchone()[0]
            for i, child in enumerate(node.get("children", [])):
                insert_prereq(child, nid, i)
        if c.get("prereq"):
            insert_prereq(c["prereq"], None, 0)
        for other in c["crosslists"]:
            a, b = sorted((c["code"], other))
            cur.execute("INSERT INTO course_crosslists VALUES (%s,%s) ON CONFLICT DO NOTHING", (a, b))
    stats["course_versions"] = versions

    # Majors before entrance programs so entrance_for resolves.
    for p in sorted(programs, key=lambda p: p["program"]["type"] == "entrance"):
        info = p["program"]
        cur.execute("""INSERT INTO programs VALUES (%s,%s,%s,%s,%s)
                       ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name, program_type=EXCLUDED.program_type, college=EXCLUDED.college, entrance_for=EXCLUDED.entrance_for""",
                    (info["code"], info["name"], info["type"], info.get("college"), info.get("entrance_for")))
        cur.execute("""INSERT INTO program_versions (program_code, catalog_year, min_total_credits, source_url) VALUES (%s,%s,%s,%s)
                       ON CONFLICT (program_code, catalog_year) DO UPDATE SET min_total_credits=EXCLUDED.min_total_credits, source_url=EXCLUDED.source_url
                       RETURNING id""",
                    (info["code"], p["catalog_year"], p.get("min_total_credits"), p["source_urls"][0]))
        pvid = cur.fetchone()[0]
        cur.execute("DELETE FROM requirement_nodes WHERE program_version_id=%s", (pvid,))
        cur.execute("DELETE FROM program_options WHERE program_version_id=%s", (pvid,))
        options = {}
        for o in p.get("options", []):
            cur.execute("INSERT INTO program_options (program_version_id, code, name) VALUES (%s,%s,%s) RETURNING id", (pvid, o["code"], o["name"]))
            options[o["code"]] = cur.fetchone()[0]

        def insert_requirement(node, parent, order, option_id):
            option_id = options[node["option"]] if node.get("option") else option_id
            text = " ".join(filter(None, [node.get("source_text")] + [f"Note: {n}" for n in node.get("notes", [])])) or None
            cur.execute("""INSERT INTO requirement_nodes (program_version_id, option_id, parent_id, sort_order, label, node_type,
                             min_count, min_credits, min_grade, min_gpa, exclusive, source_text)
                           VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s) RETURNING id""",
                        (pvid, option_id, parent, order, node["label"], node["type"], node.get("min_count"), node.get("min_credits"),
                         node.get("min_grade"), node.get("min_gpa"), node.get("exclusive", True), text))
            nid = cur.fetchone()[0]
            cur.executemany("""INSERT INTO requirement_rules (node_id, include, course_code, subject, attribute, level_min, level_max)
                               VALUES (%s,%s,%s,%s,%s,%s,%s)""",
                            [(nid, r.get("include", True), r.get("course"), r.get("subject"), r.get("attribute"),
                              r.get("level_min"), r.get("level_max")) for r in node.get("rules", [])])
            for i, child in enumerate(node.get("children", [])):
                insert_requirement(child, nid, i, option_id)
        for i, node in enumerate(p["requirements"]):
            insert_requirement(node, None, i, None)
    stats["programs"] = len(programs)

    rules = 0
    for doc in policies:
        cur.execute("DELETE FROM policy_rules WHERE catalog_year=%s AND scope=%s AND scope_code=%s",
                    (doc["catalog_year"], doc["scope"], doc["program_code"]))
        for r in doc["rules"]:
            cur.execute("""INSERT INTO policy_rules (key, catalog_year, scope, scope_code, value, description, policy_ref, source_url)
                           VALUES (%s,%s,%s,%s,%s,%s,%s,%s)""",
                        (r["key"], doc["catalog_year"], doc["scope"], doc["program_code"], json.dumps(r["value"]),
                         r["description"], r.get("policy_ref"), r["source_url"]))
            rules += 1
    stats["policy_rules"] = rules
    return stats


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True)
    ap.add_argument("--programs", nargs="+", required=True)
    ap.add_argument("--dry-run", action="store_true", help="load inside a transaction, print counts, then roll back")
    args = ap.parse_args()
    import psycopg
    with psycopg.connect(**settings()) as conn:
        with conn.cursor() as cur:
            stats = load(cur, pathlib.Path(args.data), args.programs)
        if args.dry_run:
            conn.rollback()
        print(("rolled back " if args.dry_run else "loaded ") + json.dumps(stats))


if __name__ == "__main__":
    main()
