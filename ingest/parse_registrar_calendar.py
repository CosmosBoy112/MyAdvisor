"""Parse a saved Penn State Registrar academic calendar page into data/calendar/<year>.json.

Usage:
  python ingest/parse_registrar_calendar.py PAGE.html data/calendar/2026-27.json \
      --url https://www.registrar.psu.edu/academic-calendars/2026-27.cfm --retrieved 2026-10-24 --catalog-year 2026-27

Save the page in a browser (or with one polite request) first; this script never fetches. Only the
"Regular Session" of each term is kept: academic_calendar has no session column, so 7-week, 6-week and
Maymester sessions would collide with the regular deadlines (see docs/notes/academic-calendar.md).
Every date is checked against the weekday printed by the Registrar.
"""
import argparse, datetime, json, pathlib, re

from bs4 import BeautifulSoup

SEASONS = {"Fall": "FA", "Spring": "SP", "Summer": "SU"}
MONTHS = {m: i for i, m in enumerate(["January", "February", "March", "April", "May", "June", "July", "August",
                                      "September", "October", "November", "December"], 1)}
WEEKDAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
SESSION = re.compile(r"^(Fall|Spring|Summer) (20\d\d) - (.+)$")
DAY = r"(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday), (" + "|".join(MONTHS) + r") (\d{1,2})"
DATES = re.compile(r"^" + DAY + r"(?: - " + DAY + r")?(?: at (\d{1,2}:\d\d [ap]\.m\.) \(ET\))?$")
FOOTNOTE = re.compile(r"^(\d)(\S.*)$")
# Labels whose slug would be unclear; every other label becomes its slug without "- Deadline".
KEYS = {
    "Intent to Graduate - Activation Period": "intent_to_graduate",
    "Final Exam Conflict - Filing Period": "final_exam_conflict_filing",
    "Declare Concurrent Majors, Minors, or Certificates - Deadline (Graduating Students)": "declare_concurrent_majors",
}


def slug(label):
    label = re.sub(r"(?i)\s*-\s*deadline$", "", label)
    return re.sub(r"[^a-z0-9]+", "_", label.lower()).strip("_")


def day(weekday, month, number, year):
    d = datetime.date(year, MONTHS[month], int(number))
    if WEEKDAYS[d.weekday()] != weekday:
        raise ValueError(f"{d} is a {WEEKDAYS[d.weekday()]}, but the page says {weekday}")
    return d


def parse_dates(text, year):
    m = DATES.match(text)
    if not m:
        return None
    first = day(*m.group(1, 2, 3), year)
    if m.group(4):
        return first.isoformat(), day(*m.group(4, 5, 6), year).isoformat(), m.group(7)
    return None, first.isoformat(), m.group(7)


def lines_of(html):
    # Line breaks only at block elements, so a label and its footnote number (<sup>) stay on one line.
    soup = BeautifulSoup(html, "html.parser")
    for tag in soup(["script", "style"]):
        tag.decompose()
    for tag in soup.find_all(["p", "li", "tr", "div", "h1", "h2", "h3", "h4", "h5", "h6", "caption", "dt", "dd", "br"]):
        tag.insert_after("\n")
    for tag in soup.find_all(["td", "th"]):
        tag.insert_after(" ")
    return [l for l in (re.sub(r"\s+", " ", l).strip() for l in soup.get_text().split("\n")) if l]


def parse(html, url, retrieved):
    lines = lines_of(html)
    terms, events = [], []
    i = 0
    while i < len(lines):
        m = SESSION.match(lines[i])
        if not m or m.group(3) != "Regular Session":
            i += 1
            continue
        season, year = m.group(1), int(m.group(2))
        code = SEASONS[season] + str(year)[2:]
        rows, footnotes = [], {}
        i += 1
        while i < len(lines) and not SESSION.match(lines[i]):
            line = lines[i]
            if line == "Calendar Footnotes":
                i += 1
                while i < len(lines) and FOOTNOTE.match(lines[i]):
                    n, text = FOOTNOTE.match(lines[i]).groups()
                    footnotes[n] = text
                    i += 1
                break
            if i + 1 < len(lines) and DATES.match(lines[i + 1]):
                rows.append((line, lines[i + 1]))
                i += 2
            else:
                i += 1
        for raw, text in rows:
            fm = re.match(r"^(.*?)\s+(\d)$", raw)
            label, note = (fm.group(1), fm.group(2)) if fm else (raw, None)
            label = re.sub(r"\s+", " ", label)
            start, end, time = parse_dates(text, year)
            note_text = footnotes.get(note, "")
            events.append({
                # academic_calendar has no time or note column, so both are kept in the display label.
                "term_code": code, "event_key": KEYS.get(label, slug(label)),
                "label": label + (f", {time} ET" if time else "") + (" (full-semester courses)" if "full semester" in note_text else ""),
                "starts_on": start, "ends_on": end,
                "campus": "UP" if "vary among campuses" in note_text else None,
                "source_url": url, "retrieved_on": retrieved,
            })
        mine = {e["event_key"]: e for e in events if e["term_code"] == code}
        terms.append({"code": code, "season": SEASONS[season], "year": year,
                      "starts_on": mine["classes_begin"]["ends_on"],
                      "ends_on": mine["final_exams"]["ends_on"]})
    if not terms:
        raise ValueError("No Regular Session found; is this a Registrar academic calendar page?")
    return {"source_url": url, "retrieved_on": retrieved, "terms": terms, "events": events}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("html")
    ap.add_argument("out")
    ap.add_argument("--url", required=True)
    ap.add_argument("--retrieved", required=True, help="YYYY-MM-DD")
    ap.add_argument("--catalog-year", required=True, help="e.g. 2026-27; Summer belongs to the same academic year")
    args = ap.parse_args()
    data = parse(pathlib.Path(args.html).read_text(encoding="utf-8", errors="replace"), args.url, args.retrieved)
    for t in data["terms"]:
        t["catalog_year"] = args.catalog_year
    data["catalog_year"] = args.catalog_year
    pathlib.Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(args.out).write_text(json.dumps(data, indent=1, ensure_ascii=False) + "\n")
    print(json.dumps({t["code"]: sum(e["term_code"] == t["code"] for e in data["events"]) for t in data["terms"]}))


if __name__ == "__main__":
    main()
