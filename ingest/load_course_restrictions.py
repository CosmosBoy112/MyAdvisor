"""Parse enrollment restrictions from Bulletin prerequisite wording into course_restrictions.

Usage:
  python ingest/load_course_restrictions.py --data data/catalog/2026-27 [--dry-run]

Reads courses.json (prereq_text), parses only the enforced part of each prerequisite (text before
"Recommended Preparation"), and inserts one row per restriction for the catalog year's course versions:
  min_semester  "third-semester standing", "5th semester standing", "4th Semester", "Junior standing"
                (Sophomore = 3, Junior = 5, Senior = 7, per the Bulletin's Classification of Students by Semester)
  text          wording that restricts enrollment but cannot be checked automatically
                (instructor approval/permission/consent, "restricted to ...", majors/campus wording), kept verbatim
"or Graduate Status" is an alternative way to meet a prerequisite, not a restriction, and is left to the
prerequisite tree. Placement exams and credit counts are prerequisites, not restrictions.

Idempotent without DELETE: a row is inserted only when an identical row is not already present. Rows already in
the table that the current parse no longer produces are counted as "stale" and left in place for review.
"""
import argparse, json, pathlib, re, sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from load_catalog_db import settings  # noqa: E402

ORDINALS = {"first": 1, "second": 2, "third": 3, "fourth": 4, "fifth": 5, "sixth": 6, "seventh": 7, "eighth": 8}
CLASSES = {"sophomore": 3, "junior": 5, "senior": 7}
SEMESTER = re.compile(r"(?i)\b(first|second|third|fourth|fifth|sixth|seventh|eighth|[1-8](?:st|nd|rd|th))[- ]semester(?: standing)?")
CLASS_STANDING = re.compile(r"(?i)\b(sophomore|junior|senior) standing\b")
# Wording that limits who may enroll but names nothing the report can prove.
UNCHECKABLE = re.compile(r"(?i)\b(?:prior )?(?:approval|permission|consent)\b[^.;]*|\brestricted to\b[^.;]*|\bopen only to\b[^.;]*|\b[\w ]{0,40}\bmajors only\b|\b[\w ]{0,40}\bcampus(?:es)? only\b")
ENFORCED = re.compile(r"(?i)^\s*enforced (?:pre|co)requisites? at enrollment:\s*")


def enforced(text):
    """The enforced part of the prerequisite text: before "Recommended Preparation", without the lead-in."""
    text = re.split(r"(?i)recommended preparations?", text or "")[0]
    return ENFORCED.sub("", text).strip()


def parse(text):
    """[(restriction_type, min_semester, phrase)] in the order the phrases appear; [] when there is none."""
    body = enforced(text)
    if not body:
        return []
    found = []
    for m in SEMESTER.finditer(body):
        word = m.group(1).lower()
        found.append((m.start(), ("min_semester", ORDINALS.get(word) or int(word[0]), m.group(0))))
    for m in CLASS_STANDING.finditer(body):
        found.append((m.start(), ("min_semester", CLASSES[m.group(1).lower()], m.group(0))))
    for m in UNCHECKABLE.finditer(body):
        found.append((m.start(), ("text", None, m.group(0).strip())))
    return [row for _, row in sorted(found)]


def load(cur, data_dir):
    data = json.loads((pathlib.Path(data_dir) / "courses.json").read_text())
    year = data["catalog_year"]
    stats = {"courses_with_restrictions": 0, "inserted": 0, "already_present": 0, "missing_course_version": [],
             "by_type": {}, "stale": 0, "catalog_year": year}
    wanted = set()
    for course in data["courses"]:
        rows = parse(course.get("prereq_text"))
        if not rows:
            continue
        stats["courses_with_restrictions"] += 1
        cur.execute("SELECT id FROM course_versions WHERE course_code=%s AND catalog_year=%s", (course["code"], year))
        hit = cur.fetchone()
        if hit is None:
            stats["missing_course_version"].append(course["code"])
            continue
        for kind, semester, phrase in rows:
            raw = phrase if kind == "text" else None
            key = (hit[0], kind, semester, raw)
            wanted.add(key)
            cur.execute("""SELECT 1 FROM course_restrictions WHERE course_version_id=%s AND restriction_type=%s AND include
                           AND min_semester IS NOT DISTINCT FROM %s AND raw_text IS NOT DISTINCT FROM %s""", key)
            if cur.fetchone():
                stats["already_present"] += 1
                continue
            cur.execute("""INSERT INTO course_restrictions (course_version_id, restriction_type, include, min_semester, raw_text)
                           VALUES (%s, %s, true, %s, %s)""", key)
            stats["inserted"] += 1
            stats["by_type"][kind] = stats["by_type"].get(kind, 0) + 1
    cur.execute("""SELECT r.course_version_id, r.restriction_type, r.min_semester, r.raw_text FROM course_restrictions r
                   JOIN course_versions v ON v.id=r.course_version_id WHERE v.catalog_year=%s""", (year,))
    stats["stale"] = sum(1 for row in cur.fetchall() if tuple(row) not in wanted)
    return stats


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", type=pathlib.Path, required=True)
    ap.add_argument("--dry-run", action="store_true", help="insert inside a transaction, print counts, then roll back")
    args = ap.parse_args()
    import psycopg
    with psycopg.connect(**settings()) as conn:
        with conn.cursor() as cur:
            stats = load(cur, args.data)
        if args.dry_run:
            conn.rollback()
    print(("rolled back " if args.dry_run else "loaded ") + json.dumps(stats, indent=2))


if __name__ == "__main__":
    main()
