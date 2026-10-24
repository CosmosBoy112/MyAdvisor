# University Park Computer Science · 2026–27

공식 Bulletin의 CMPSC_BS(University Park) 요건과 과목을 수집하고, 공식 EECS 페이지의 학부 Handbook과 대조했습니다. 다른 캠퍼스의 CSENG_BS는 포함하지 않습니다.

## 수집 결과

- 학위 최소 127학점, 전공 106–108학점, General Education 45학점. 전공과 GenEd 중복 24학점이 있어 모든 항목을 단순 합산하면 안 됩니다.
- 전공 요건에 등장하는 과목 코드 75개를 8개 과목 페이지에서 모두 찾았습니다. 선수과목 해석에 review가 있는 과목 14개는 자동 충족 판정 대상이 아닙니다.
- CMPSC_ETM은 University Park의 GPA 3.20, graded Penn State 29–55학점 및 C 이상 선수과목 조건을 별도 저장합니다. AP/편입 학점과 현재 소속은 추가 검증이 필요합니다.
- CMPEN 270 또는 (271 AND 275), MATH 230/230H 또는 (231 AND 232) 구조를 보존합니다. STAT/MATH 교차개설 코드는 대안이며 이중 학점으로 계산하지 않습니다.

## Handbook 학년도 충돌

2026-10-24 수집 당시 공식 EECS 페이지에 연결된 최신 학부 Handbook은 2024–25입니다. PDF 표지와 링크는 2024–25이지만, 18쪽에는 2021–22 입학생을 지칭하는 문구도 있어 적용 학년도를 확정할 수 없습니다. 2026–27 규칙으로 자동 적용하지 않습니다.

| 항목 | 2026–27 Bulletin | 이전 Handbook | 매칭 처리 |
|---|---|---|---|
| Department List | 6학점 | 10–14학점 | 명칭 연결, 학점 충돌 표시. 승인 과목 목록은 확인 필요 |
| CS technical electives | 명시된 27개 코드 중 18학점 | CS electives 12학점 및 별도 구조 | 현재 목록 사용, 이전 구조 자동 대체 금지 |
| Supporting Electives | 동일한 독립 요건 확인 안 됨 | 주제에 맞는 고급 과목 6학점 | 현재 자연과학/Department List로 변환 금지 |
| 자연과학 선택 | PHYS 213 또는 214 또는 승인 목록 3학점 | 이전 안내 | 승인 목록 분기는 확인 필요 |
| Writing | CMPSC 483W가 필수 목록에 포함 | 이전 선택 구조 | 이전 선택 과목의 대체 인정 추정 금지 |

빈 승인 목록은 ‘아무 과목 가능’이 아닙니다. `requires_review`와 미해결 분기를 존중해야 합니다. Handbook의 제외 과목과 상한은 `historical_*`에만 저장합니다. 과목 존재 여부와 해당 요건에 인정되는지는 별개의 판단입니다.

## 파일

- `data/catalog/2026-27/programs/CMPSC_BS.json`: 현재 학위 요건 트리
- `data/catalog/2026-27/programs/CMPSC_ETM.json`: 현재 전공 진입 조건
- `data/catalog/2026-27/courses.json`: 기존 카탈로그에 공식 과목 정보 병합
- `data/catalog/2026-27/references/CMPSC_course_matches.json`: 코드별 출처·선수과목 검토 상태
- `data/catalog/2026-27/references/CMPSC_handbook_crosswalk.json`: 보고서 항목 명칭, 충돌, 과거 지침
- `data/catalog/2026-27/references/CMPSC_sources.json`: URL, 수집 시각, 원문 SHA-256
- `ingest/crawl_cmpsc_catalog.py`: 재수집 스크립트. 원문 HTML/PDF는 별도 캐시에 보관
- `ingest/match_cmpsc_requirements.py`: 명시적 별칭 기반 매칭 CLI. 학년도와 캠퍼스 필수, 불명확한 List A/B는 unmatched

## 실행

Python 3.10 이상, `lxml`, `pypdf`가 필요합니다.

```sh
python -m pip install -r ingest/requirements.txt
python ingest/crawl_cmpsc_catalog.py --cache /tmp/cmpsc-sources --out data/catalog/2026-27
# 캐시로 재현: --offline / 원문 재수집: --refresh
python ingest/match_cmpsc_requirements.py 'Department List' --catalog-year 2026-27 --campus UP
python -m unittest discover -s ingest -p 'test_cmpsc_catalog.py' -v
```

크롤러는 Bulletin 학년도가 바뀌거나 새 Handbook이 연결되면 검토를 요구하며 중단합니다. 캐시는 원본 URL을 바꾸지 않고 같은 수집용 디렉터리를 사용하세요. 재수집 후 결과를 검토하고 커밋해야 합니다.

## 앱 적용 범위

이번 변경은 카탈로그 시드와 독립 Python 매칭 도구입니다. Spring Boot PDF 분석·Gemini/Luminal 프롬프트·TigerData·Vue 화면에 아직 연결하지 않았습니다. DB 스키마 초안도 변경하거나 실행하지 않았습니다. 따라서 현재 웹서비스 응답이 이 데이터를 사용한다고 가정하면 안 됩니다.

후속 로더/감사 엔진은 미해결 목록, GenEd 중복/분배, 학점 출처, 최소 성적, 반복/동등 과목을 별도로 처리해야 합니다. 현재 스키마 초안에서 지원하지 않는 메타데이터를 버리고 졸업 가능으로 판정하면 안 됩니다. 보고서의 명시 학년도나 남은 학점을 이 명칭 매칭 결과로 덮어쓰지 마세요.

## 공식 출처

- [Computer Science B.S. Bulletin](https://bulletins.psu.edu/undergraduate/colleges/engineering/computer-science-bs/)
- [EECS Computer Science 학부 Handbook 목록](https://www.eecs.psu.edu/Students/undergraduate/Computer-Science.aspx)
- [2024–25 Handbook PDF](https://www.eecs.psu.edu/assets/docs/handbooks/CMPSC-handbook-2024-2025.pdf)
