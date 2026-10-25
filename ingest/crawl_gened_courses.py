"""Crawl every 2026-27 undergraduate subject page of the Bulletin and keep the General Education courses.

Only /university-course-descriptions/undergraduate/ and /undergraduate/general-education/ pages are fetched
(allowed by robots.txt); /search/, /archive/ and /pdf/ are never requested, so attribute search is not used and
every subject page is read instead. Downloads are cached outside the repo and reused, with a 1 second delay
between live requests.
Run: python ingest/crawl_gened_courses.py --cache /tmp/gened-pages --out data/catalog/2026-27 [--offline]

Courses are kept when they carry a GenEd attribute (GA/GH/GN/GQ/GS/GWS/GHW, INTER) or an Additional University
Requirement attribute (US, IL, WAC, FYS). The attributes read from course pages are then compared with the
Bulletin's own per-attribute course lists; the differences are written out, never silently merged.

Output:
  gened_courses.json            kept courses plus the course-list comparison
  references/GENED_sources.json one row per fetched page with its sha256
"""
from __future__ import annotations
import argparse, json, re, sys
from collections import Counter
from pathlib import Path
from crawl_eecs_catalog import Fetcher, dump
from parse_bulletin_courses import parse_block, build_course

YEAR = '2026-27'
INDEX = 'https://bulletins.psu.edu/university-course-descriptions/undergraduate/'
LISTS = 'https://bulletins.psu.edu/undergraduate/general-education/course-lists/'
KEPT = {'GA', 'GH', 'GN', 'GQ', 'GS', 'GWS', 'GHW', 'INTER', 'US', 'IL', 'WAC', 'FYS'}
LIST_PAGES = {'arts': 'GA', 'humanities': 'GH', 'natural-sciences': 'GN', 'quantification': 'GQ',
              'social-behavioral-sciences': 'GS', 'writing-speaking': 'GWS', 'health-wellness': 'GHW',
              'inter-domain': 'INTER', 'united-states-cultures': 'US', 'international-cultures': 'IL',
              'writing-across-curriculum': 'WAC', 'first-year-seminar': 'FYS'}


def subject_slugs(index_html):
    slugs = sorted(set(re.findall(r'href="/university-course-descriptions/undergraduate/([a-z_-]+)/"', index_html)))
    if len(slugs) < 200: raise ValueError(f'Only {len(slugs)} subject links found; the index layout may have changed')
    return slugs


def list_codes(page):
    """Course codes in a course-list table (the codecol links)."""
    codes = re.findall(r'class="codecol[^"]*"><a [^>]*title="([^"]+)"', page)
    return {' '.join(c.replace('\xa0', ' ').split()) for c in codes}


def gened_courses(pages):
    """pages: iterable of (slug, html). Returns (kept courses, total course count, attributes of every course, skipped codes).

    Placeholder registrations whose number has no digits (EDAB TEMPH) cannot be stored as courses and are skipped."""
    out, total, attrs, skipped = [], 0, {}, []
    for slug, page in pages:
        for block in page.split('<div class="courseblock">')[1:]:
            code, title, credits_text, paras = parse_block(block)
            total += 1
            if not re.fullmatch(r'\S+ \d+[A-Z]*', code):
                skipped.append(code); continue
            if code in attrs: raise ValueError(f'{code} appears on more than one subject page')
            course = build_course(code, title, credits_text, paras, slug)
            attrs[code] = set(course['attributes'])
            if KEPT & attrs[code]:
                out.append(course)
    return out, total, attrs, skipped


def compare(attrs, lists):
    """Per attribute: codes on the official list without the attribute on the course page, and the reverse."""
    result = {}
    for attr, codes in sorted(lists.items()):
        pages = {c for c, a in attrs.items() if attr in a}
        result[attr] = {'list_count': len(codes), 'course_page_count': len(pages),
                        'only_on_list': sorted(codes - pages), 'only_on_course_page': sorted(pages - codes)}
    return result


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--cache', type=Path, required=True)
    ap.add_argument('--out', type=Path, required=True)
    ap.add_argument('--offline', action='store_true')
    args = ap.parse_args()
    fetcher = Fetcher(args.cache, args.offline)
    slugs = subject_slugs(fetcher.get('index.html', INDEX, 'bulletin_ucd_index_2026_27').decode('utf-8'))
    pages = []  # download everything first so a parse error never interrupts the crawl
    for i, slug in enumerate(slugs, 1):
        pages.append((slug, fetcher.get(f'c_{slug}.html', INDEX + slug + '/', f'bulletin_ucd_{slug}_2026_27').decode('utf-8')))
        if i % 25 == 0: print(f'{i}/{len(slugs)} subject pages', file=sys.stderr)
    lists = {}
    for slug, attr in LIST_PAGES.items():
        page = fetcher.get(f'l_{slug}.html', LISTS + slug + '/', f'bulletin_gened_list_{slug}_2026_27').decode('utf-8')
        lists[attr] = list_codes(page)
        if not lists[attr]: raise ValueError(f'No courses found on the {slug} list; the layout may have changed')

    courses, total, attrs, skipped = gened_courses(pages)
    check = compare(attrs, lists)
    dump(args.out / 'gened_courses.json', {'catalog_year': YEAR, 'source': INDEX, 'subject_pages': len(slugs),
                                           'courses_scanned': total, 'skipped_codes': skipped, 'kept_attributes': sorted(KEPT),
                                           'course_list_check': check, 'courses': courses})
    dump(args.out / 'references' / 'GENED_sources.json', fetcher.sources)
    print(len(slugs), 'subject pages,', total, 'courses scanned,', len(courses), 'courses kept, skipped', skipped)
    print(dict(sorted(Counter(a for c in courses for a in c['attributes']).items())))
    print(dict(Counter(c['status'] for c in courses)))
    for attr, r in check.items():
        print(f"{attr}: list {r['list_count']}, pages {r['course_page_count']}, only on list {len(r['only_on_list'])}, only on pages {len(r['only_on_course_page'])}")


if __name__ == '__main__':
    main()
