"""Parse saved Bulletin course-description pages into seed course JSON.

Usage:
  python parse_bulletin_courses.py --pages DIR --codes CODES.json --catalog-year 2026-27 --out courses.json

DIR holds pages saved from https://bulletins.psu.edu/university-course-descriptions/undergraduate/<subject>/
named c_<subject>.html. CODES.json is {"codes": [...], "fallback_titles": {"CODE": [title, credits]}};
only listed codes are emitted, and codes missing from the pages are kept with status "not_in_course_list".

Prerequisites are parsed into the prereq_nodes tree shape. Anything the grammar does not cover becomes a
TEXT node, and every assumption is written to "review" so a person can check it before loading.
"""
import argparse, html, json, pathlib, re

PAGE_URL = "https://bulletins.psu.edu/university-course-descriptions/undergraduate/{}/"

ATTRIBUTES = [
    (r"^General Education: .*\((G[A-Z]+)\)$", None),
    (r"^General Education - Integrative: Interdomain$", "INTER"),
    (r"^International Cultures \(IL\)$", "IL"),
    (r"^United States Cultures \(US\)$", "US"),
    (r"^Writing Across the Curriculum$", "WAC"),
    (r"^First-Year Seminar$", "FYS"),
]

ORDINALS = {"first": 1, "second": 2, "third": 3, "fourth": 4, "fifth": 5, "sixth": 6, "seventh": 7, "eighth": 8}

PREREQ_MODE = r"Enforced (?:Prerequisites?|Prereq) at Enrollment(?: or concurrent)?:|Prerequisites?:"
COREQ_MODE = r"Enforced Concurrent at Enrollment:|Concurrent Courses:|Corequisites?:|Concurrent:"
TOKEN = re.compile(
    rf"(?P<prereq>{PREREQ_MODE})|(?P<coreq>{COREQ_MODE})|(?P<grade>[A-D] or better in)"
    r"|(?P<standing>(?:(?P<ord>first|second|third|fourth|fifth|sixth|seventh|eighth)|(?P<num>[1-8])(?:st|nd|rd|th))[- ]semester standing)"
    r"|(?P<course>[A-Z]{2,6}(?:/[A-Z]{2,6})? \d{1,3}[A-Z]?)\b|(?P<and>\band\b)|(?P<or>\bor\b)|(?P<lp>\()|(?P<rp>\))|(?P<sep>[.;,])",
    re.I)


def clean(s):
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", "", s))).replace("\xa0", " ").strip()


def code_parts(code):
    subject, number = code.rsplit(" ", 1)
    digits = re.match(r"\d+", number).group()
    return subject, number, int(digits) // 100 * 100


def parse_credits(text):
    m = re.match(r"([\d.]+)(?:-([\d.]+))? Credits?(?:/Maximum of (\d+))?", text)
    if not m:
        return None, None, False
    lo = float(m.group(1)); hi = float(m.group(2) or lo)
    return lo, hi, bool(m.group(3)) and float(m.group(3)) > hi


class Unparsed(Exception):
    pass


def tokenize(text):
    tokens, pos = [], 0
    for m in TOKEN.finditer(text):
        if text[pos:m.start()].strip():
            raise Unparsed(text[pos:m.start()].strip())
        for name in ("prereq", "coreq", "grade", "standing", "course", "and", "or", "lp", "rp", "sep"):
            if m.group(name):
                kind = name; break
        value = m.group()
        if kind == "course":
            subject, number = value.upper().split(" ")
            value = subject.split("/")[0] + " " + number  # 'STAT/MATH 414' -> 'STAT 414' (cross-listed)
        elif kind == "standing":
            value = ORDINALS[m.group("ord").lower()] if m.group("ord") else int(m.group("num"))
        tokens.append((kind, value))
        pos = m.end()
    if text[pos:].strip():
        raise Unparsed(text[pos:].strip())
    return tokens


def parse_prereq(text, review):
    """Return a prereq tree for the enforced text, or raise Unparsed."""
    tokens = tokenize(text)
    i = 0
    mode, grade = "COURSE", None

    def peek():
        return tokens[i][0] if i < len(tokens) else None

    def take():
        nonlocal i
        if i >= len(tokens):
            raise Unparsed("unexpected end of text")
        i += 1
        return tokens[i - 1]

    def switch_modes():
        nonlocal mode, grade
        while peek() in ("prereq", "coreq", "grade", "sep"):
            kind, _ = take()
            if kind == "prereq":
                mode, grade = "COURSE", None
            elif kind == "coreq":
                mode, grade = "COREQ", None
            elif kind == "grade":
                grade = tokens[i - 1][1][0].upper()
                review.append(f"Assumed '{grade} or better in' applies to every course in its clause.")

    def leaf():
        switch_modes()
        kind = peek()
        if kind == "lp":
            take(); node = expr(depth=1)
            if take()[0] != "rp":
                raise Unparsed("unbalanced parentheses")
            return node
        if kind == "course":
            node = {"type": mode, "course": take()[1]}
            if grade and mode == "COURSE":
                node["min_grade"] = grade
            return node
        if kind == "standing":
            return {"type": "STANDING", "min_semester": take()[1]}
        raise Unparsed(f"unexpected token {tokens[i] if i < len(tokens) else 'end'}")

    def expr(depth=0):
        parts, ops = [leaf()], set()
        while True:
            kind = peek()
            if kind in ("and", "or"):
                ops.add(take()[0]); parts.append(leaf())
            elif kind in ("prereq", "coreq", "grade", "sep") and depth == 0:
                break  # clause boundary; clauses are joined below
            elif kind in ("prereq", "coreq", "grade", "sep", "course", "standing", "lp"):
                ops.add("and"); parts.append(leaf())
            else:
                break
        if len(ops) > 1:
            raise Unparsed("mixed 'and'/'or' without parentheses")
        return combine(parts, "ALL" if ops == {"and"} else "ANY")

    # "X or Enforced Concurrent at Enrollment: Y" stays one clause through the operator;
    # a clause boundary with no operator before it ("X Enforced Concurrent ...: Y") joins with AND.
    clauses = [expr()]
    while i < len(tokens):
        switch_modes()
        if i < len(tokens):
            clauses.append(expr())
    return combine(clauses, "ALL")


def combine(parts, kind):
    if len(parts) == 1:
        return parts[0]
    return {"type": kind, "children": flatten(parts, kind)}


def flatten(children, kind):
    out = []
    for c in children:
        out.extend(c["children"] if c.get("type") == kind else [c])
    return out


def parse_block(block):
    title_line = clean(re.search(r'course_codetitle">(.*?)</div>', block, re.S).group(1))
    code, _, title = title_line.partition(":")
    credits_text = clean(re.search(r'course_credits">(.*?)</div>', block, re.S).group(1))
    meta = block.split("courseblockmeta", 1)[1]
    paras = [p for p in (clean(p) for p in re.findall(r"<p[^>]*>(.*?)</p>", meta, re.S)) if p]
    return re.sub(r"\s+", " ", code).strip(), title.strip(), credits_text, paras


def build_course(code, title, credits_text, paras, subject_slug):
    subject, number, level = code_parts(code)
    lo, hi, repeatable = parse_credits(credits_text)
    course = {"code": code, "subject": subject, "number": number, "level": level, "title": title,
              "credits_min": lo, "credits_max": hi, "repeatable": repeatable,
              "description": paras[0] if paras else None, "attributes": [], "crosslists": [],
              "prereq_text": None, "prereq": None, "status": "no_prereq", "review": [],
              "source_url": PAGE_URL.format(subject_slug)}
    for p in paras[1:]:
        for pattern, attr in ATTRIBUTES:
            m = re.match(pattern, p)
            if m:
                course["attributes"].append(attr or m.group(1))
        if p.startswith("Cross-listed with:"):
            course["crosslists"] = [c.strip() for c in p.split(":", 1)[1].split(",")]
        if re.match(r"(Enforced|Prerequisite|Concurrent|Corequisite)", p):
            course["prereq_text"] = p
    text = course["prereq_text"]
    if text:
        enforced = re.split(r"\s*Recommended Preparation:", text)[0].strip().rstrip(".")
        if enforced != text.rstrip("."):
            course["review"].append("Recommended preparation is advisory and was not modeled.")
        if re.search(r"Concurrent Courses:|Corequisites?:", enforced):
            course["review"].append("Non-enforced concurrent/corequisite wording was modeled as COREQ.")
        if re.search(r"\bor (?:Enforced )?Concurrent", enforced):
            course["review"].append("A concurrent clause follows 'or'; check which courses it covers.")
        try:
            course["prereq"] = parse_prereq(enforced, course["review"])
            course["status"] = "auto_parsed"
        except Unparsed as e:
            course["prereq"] = {"type": "TEXT", "raw": enforced}
            course["status"] = "text_only"
            course["review"].append(f"Could not parse prerequisite ({e}); kept as TEXT.")
    if lo is None:
        course["review"].append(f"Could not parse credits '{credits_text}'.")
    return course


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pages", required=True)
    ap.add_argument("--codes", required=True)
    ap.add_argument("--catalog-year", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    wanted = json.loads(pathlib.Path(args.codes).read_text())
    blocks = {}
    for page in sorted(pathlib.Path(args.pages).glob("c_*.html")):
        slug = page.stem[2:]
        for block in page.read_text(encoding="utf-8").split('<div class="courseblock">')[1:]:
            code, title, credits_text, paras = parse_block(block)
            blocks[code] = (title, credits_text, paras, slug)

    def stub(code, title, credits, note):
        subject, number, level = code_parts(code)
        lo = hi = float(credits) if credits and "-" not in credits else None
        return {"code": code, "subject": subject, "number": number, "level": level, "title": title or None,
                "credits_min": lo, "credits_max": hi, "repeatable": False, "description": None,
                "attributes": [], "crosslists": [], "prereq_text": None, "prereq": None,
                "status": "not_in_course_list", "review": [note], "source_url": None}

    courses = []
    for code in wanted["codes"]:
        if code in blocks:
            title, credits_text, paras, slug = blocks[code]
            courses.append(build_course(code, title, credits_text, paras, slug))
        else:
            title, credits = wanted.get("fallback_titles", {}).get(code, [None, None])
            courses.append(stub(code, title, credits,
                                "Listed in program requirements but missing from the current course descriptions; it may be renumbered or retired."))

    # Courses that appear only inside prerequisites or cross-lists still need rows for the foreign keys.
    # They are kept as references: their own prerequisites are not expanded.
    def referenced(node, acc):
        if node.get("course"):
            acc.add(node["course"])
        for child in node.get("children", []):
            referenced(child, acc)
        return acc

    listed = {c["code"] for c in courses}
    extra = set()
    for c in courses:
        if c["prereq"]:
            referenced(c["prereq"], extra)
        extra.update(c["crosslists"])
    for code in sorted(extra - listed):
        if code in blocks:
            title, credits_text, paras, slug = blocks[code]
            course = build_course(code, title, credits_text, paras, slug)
            course.update(prereq=None, crosslists=[], status="reference_only",
                          review=["Referenced by another course's prerequisite or cross-list; prerequisites and cross-lists not expanded."])
        else:
            course = stub(code, None, None,
                          "Referenced by another course's prerequisite or cross-list; subject page not collected.")
            course["status"] = "reference_only"
        courses.append(course)

    doc = {"catalog_year": args.catalog_year, "courses": courses}
    pathlib.Path(args.out).write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n")
    counts = {}
    for c in courses:
        counts[c["status"]] = counts.get(c["status"], 0) + 1
    print(len(courses), "courses", counts)


if __name__ == "__main__":
    main()
