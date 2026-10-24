"""Build program policy rules for Data Sciences B.S. (Computational option) from the 2026-27 Bulletin
page and the latest published DTSCE handbook (2024-25).

Usage:
  python ingest/build_datsc_policies.py BULLETIN_HTML data/catalog/2026-27/policies/DATSC_BS.json

BULLETIN_HTML is https://bulletins.psu.edu/undergraduate/colleges/engineering/data-sciences-bs/ saved as HTML.
The suggested academic plan is parsed from it; handbook rules are transcribed below and must be rechecked
when a newer handbook is published.
"""
import json, re, sys
from bs4 import BeautifulSoup

BULLETIN = "https://bulletins.psu.edu/undergraduate/colleges/engineering/data-sciences-bs/"
HANDBOOK = "https://www.eecs.psu.edu/assets/docs/handbooks/DTSCE-handbook-2024-2025.pdf"
HANDBOOK_NOTE = ("Source: DTSCE Handbook 2024-25, the latest handbook published on the EECS site as of 2026-10-24. "
                 "No 2025-26 or 2026-27 edition was found; confirm with an adviser before relying on it for 2026-27.")

soup = BeautifulSoup(open(sys.argv[1], encoding="utf-8"), "html.parser")
section = soup.find(id="suggestedacademicplantextcontainer")
heading = next(h.get_text(" ", strip=True) for h in section.find_all(["h2", "h3"]) if "Computational" in h.get_text())
assert "Computational Data Sciences Option" in heading, heading
table = section.find("table")

def credits(text):
    text = text.strip()
    return float(text) if re.fullmatch(r"\d+(\.\d+)?", text) else None

terms, year = [], 0
for tr in table.find_all("tr"):
    cells = [td.get_text(" ", strip=True) for td in tr.find_all(["th", "td"])]
    cells += [""] * (4 - len(cells))
    joined = " ".join(cells)
    if re.fullmatch(r"(First|Second|Third|Fourth) Year", joined.strip()):
        year += 1; fall = {"year": year, "season": "FA", "courses": []}; spring = {"year": year, "season": "SP", "courses": []}
        terms += [fall, spring]; continue
    if cells[:1] == ["Fall"] or cells[0].startswith("Total Credits") or year == 0:
        continue
    if not cells[0] and not cells[2]:
        fall["total_credits"], spring["total_credits"] = credits(cells[1]), credits(cells[3]); continue
    for label, cr, term in ((cells[0], cells[1], fall), (cells[2], cells[3], spring)):
        if label:
            clean = re.sub(r"\s+", " ", label.replace("\xa0", " ")).strip()
            term["courses"].append({"label": re.sub(r"\s*[*#†‡]+\s*$", "", clean).strip(), "credits": credits(cr),
                                    "c_required": "*" in clean})
total = re.search(r"Total Credits\s+([\d.]+)", section.get_text(" ", strip=True)).group(1)
assert len(terms) == 8 and float(total) == 123, (len(terms), total)
for t in terms:
    assert sum(c["credits"] for c in t["courses"]) == t["total_credits"], t

rules = [
    {"key": "suggested_academic_plan.COMPUTATIONAL",
     "value": {"campus": "UP", "total_credits": 123, "terms": terms,
               "symbols": {"c_required": "Course requires a grade of C or better"},
               "list_a": ["CMPEN 454", "CMPSC 450", "CMPSC 455", "CMPSC 456", "MATH 484", "MATH 452"],
               "list_b": ["CMPSC 431W", "EE 456", "DS 441", "MATH 486", "MATH 448", "STAT 416", "STAT 440", "STAT 460", "STAT 461", "STAT 462"]},
     "description": "Suggested academic plan for the Computational Data Sciences option at University Park, in effect for 2026-27. "
                    "A suggested sequence, not a requirement; List A/B are from the plan's footnotes.",
     "policy_ref": heading, "source_url": BULLETIN + "#suggestedacademicplantext"},
    {"key": "natural_science_exclusions",
     "value": {"required_gn_credits": 9,
               "excluded": ["ASTRO 1", "ASTRO 7N", "ASTRO 10", "ASTRO 11", "ASTRO 120", "ASTRO 140", "BISC *",
                            "CHEM below 110 (except 3 credits of CHEM 106)", "PHYS 250", "PHYS 251", "PHYS below 211", "GEOSC 20"]},
     "description": "Nine credits of Natural Science (GN) are required; the listed courses do not count (2026-27 Bulletin college notes). "
                    "The 2024-25 handbook additionally excluded ASTRO 6 and GAME 180N.",
     "policy_ref": "Suggested Academic Plan, College Notes: Natural Sciences Elective", "source_url": BULLETIN + "#suggestedacademicplantext"},
    {"key": "department_list_rules",
     "value": {"credits": 6,
               "caps": {"ROTC": 3, "music_performance": 6, "additional_physical_education": 3, "cooperative_education": 3,
                        "engineering_internship": 2, "CHEM 106": 3},
               "excluded_courses": ["ASTRO 1", "ASTRO 6", "ASTRO 7N", "ASTRO 10", "ASTRO 11", "ASTRO 120", "ASTRO 140",
                                    "BISC 1", "BISC 2", "BISC 3", "BISC 4", "CHEM 1", "CHEM 3", "CHEM 101", "CHEM 108",
                                    "CMPSC 100", "CMPSC 101", "CMPSC 200", "CMPSC 201", "CMPSC 203", "CYBER 100", "EMSC 150",
                                    "ESL 4", "EE 465", "IST 140", "IST 220", "IST 230", "IST 240", "IST 242", "IST 261", "IST 311", "IST 361",
                                    "LLED 5", "LLED 10", "MATH 200", "MATH 201", "PHIL 12", "PHSC 7", "PHYS 250", "PHYS 251",
                                    "S T S 150", "CAS 126", "CAS 283", "STAT 401", "STAT 487",
                                    "STAT 318", "STAT 319", "STAT 414", "STAT 415", "STAT 418"],
               "excluded_ranges": ["MATH below 140", "PHYS below 211", "STAT below 318"],
               "excluded_categories": ["courses not satisfying minimum requirements for a baccalaureate degree program",
                                       "courses described as intended for non-science or non-technical majors",
                                       "courses similar or remedial to a required course or one already taken",
                                       "ENGL 4, ENGL 5 or any other remedial English"],
               "note": "US/IL courses that are not Arts, Humanities, or Social/Behavioral Sciences courses may count here. "
                       "The handbook says to bring the total to at least 126 credits; the 2026-27 Bulletin minimum is 123."},
     "description": "Department List (general elective) restrictions. " + HANDBOOK_NOTE,
     "policy_ref": "Department List (General Elective) Guidelines", "source_url": HANDBOOK},
    {"key": "first_year_seminar_makeup",
     "value": {"credits": 1, "petition_url": "https://coursesub.psu.edu/"},
     "description": "Students without a separate 1-credit first-year seminar take one extra Department List credit and petition "
                    "to apply it to the first-year seminar requirement. " + HANDBOOK_NOTE,
     "policy_ref": "First Year Seminar (1 credit)", "source_url": HANDBOOK},
    {"key": "communications_substitutions",
     "value": {"ENGL 15": ["ENGL 30", "ESL 15"], "ENGL 15 + CAS 100A/B": ["ENGL 137 + ENGL 138", "CAS 137 + CAS 138"],
               "MATH 230": ["MATH 231 + MATH 232"]},
     "description": "Accepted substitutions for communications and calculus courses. " + HANDBOOK_NOTE,
     "policy_ref": "Communications / Quantification", "source_url": HANDBOOK},
]
doc = {"program_code": "DATSC_BS", "catalog_year": "2026-27", "scope": "program", "rules": rules}
json.dump(doc, open(sys.argv[2], "w"), ensure_ascii=False, indent=2)
print("terms", [(t["year"], t["season"], t.get("total_credits"), len(t["courses"])) for t in terms])
