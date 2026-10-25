# Progress Log

팀원이 푸시할 때마다 **맨 위에** 항목을 하나 추가합니다. 최신 항목이 위에 오도록 유지하세요.
작업을 시작하기 전에 최근 항목을 먼저 읽고, 다른 사람의 "막힌 것"과 "다음"을 확인하세요.

```markdown
## YYYY-MM-DD · 이름 · `브랜치`
- 한 일: 무엇을 바꿨는지 (사용자 관점 한두 줄)
- 상태: 무엇을 어떻게 검증했는지, 무엇이 미검증인지
- 막힌 것: 해결 못 한 문제 (없으면 "없음")
- 다음: 이어서 할 일
- 필요한 설정: 새로 필요한 .env 값, 외부 대시보드 설정 등 (값 자체는 적지 않음)
```

---

## 2026-10-24 · Claude Code · `main`
- 한 일: README 제목을 OnlyPlans로 바꾸고 배포 주소(https://www.onlyplans.courses, 프론트 Vercel·백엔드 Render) 한 줄 추가. Auth0 audience·Java 패키지·Docker 태그·`LIONPATHWAY_LIVE_TEST` 등 실제 설정값/식별자의 `lionpathway`는 그대로 둠
- 상태: 문서 변경만. 배포 주소는 curl로 OnlyPlans 페이지·Render API 연결 확인
- 막힌 것: 없음
- 다음: 없음
- 필요한 설정: 없음

## 2026-10-24 · Claude Code · `main`
- 한 일: 프론트 브랜드를 LionPathway → OnlyPlans로 변경(헤더·푸터·페이지 제목). OnlyFans 스타일을 오마주한 오리지널 로고(O 링 + 학사모, Penn State 네이비 계열·다크 모드 색 분리, `src/components/BrandMark.vue`)와 같은 모양의 `public/favicon.svg` 추가. 원본 로고 파일은 쓰지 않음
- 상태: `npm ci` 후 프런트 33개 테스트·빌드 통과. 하늘색 초안을 headless Chrome 다크 모드로 캡처 확인. 네이비 색상 변경 후 화면·라이트 모드·모바일 폭은 미확인
- 막힌 것: 없음
- 다음: README·docs의 LionPathway 표기와 localStorage 키(`lionpathway-*`)는 그대로 둠. 필요하면 별도 정리
- 필요한 설정: 없음. 팀원 로컬은 `franc-min` 추가분 때문에 `npm ci` 필요

## 2026-10-24 · Claude Code · `main`
- 한 일: CORS 설정 빈 이름을 `cors` → `corsConfigurationSource`로 변경. Spring Security 6.4는 이 이름으로만 찾기 때문에 기존 설정이 무시되어, 배포 환경(Vercel→Render)에서 모든 교차 출처 preflight가 403이던 문제 수정. 로컬은 Vite 프록시로 같은 출처라 드러나지 않았음
- 상태: 수정 전 jar를 demo 프로필로 로컬 실행 시 허용 Origin preflight도 403 재현. 수정 후 `FRONTEND_ORIGIN` Origin → 200 + `Access-Control-Allow-Origin`, 다른 Origin → 403 확인. `mvn package` 테스트 전체 통과. Render 재배포 후 실제 브라우저 로그인·채팅은 미검증
- 막힌 것: 없음
- 다음: Render 재배포 후 `https://www.onlyplans.courses`에서 로그인·채팅 확인
- 필요한 설정: Render `FRONTEND_ORIGIN`=프론트 실제 주소(현재 www 도메인, 끝 `/` 없이). Auth0 SPA 앱 Callback/Logout/Web Origins에 같은 주소 추가

## 2026-10-24 · Codex · `main`
- 한 일: 로그인 후 API 오류 처리 보완: JSON 파싱 전401/403 처리, HTML/일반텍스트 오류와 잘못 연결된 웹페이지를 구분. 정상 JSON 에러 보존 및 빈 DELETE 200/204 지원. 배포 연결 설명 docs/DEPLOYMENT.md 추가
- 상태: 프런트33개 테스트/빌드 통과. 실제 www.onlyplans.courses 번들의 API base가 빈 값이고 /api/health·/api/reports가 Vercel404 텍스트 반환하는 것 확인. 로컬 health는 정상
- 막힌 것: 프로덕션은 프런트만으로 API에 연결되지 않음. 실제 백엔드 배포 주소 확인 후 Vercel VITE_API_BASE_URL/백엔드 FRONTEND_ORIGIN 설정 및 재배포 필요. 오류 안내 수정만으로 프로덕션 연결은 해결되지 않음
- 다음: 백엔드 공개URL 확인 및 프로덕션 연결 검증
- 필요한 설정: 기존 VITE_API_BASE_URL, FRONTEND_ORIGIN 등(상세 배포 문서)

## 2026-10-24 · Codex · `main`
- 한 일: 메인 질문을 제출 시점에 보관하고 PDF 분석 성공 후 리포트 채팅창을 자동으로 열어 질문 전송·답변 표시. 빈 입력/분석 실패 시 자동 질문 없음. 샘플도 동일 흐름(예시 답변임을 표시)
- 상태: 프런트30개 테스트/빌드 통과. 브라우저에서 메인 Department List 질문→샘플 리포트→채팅창·질문·해당 예시 답변 자동 표시 확인. 인증 PDF 업로드/실 AI 전체 흐름 재검증 없음
- 막힌 것: 없음
- 다음: PDF와 질문 제출 후 자동 답변 확인
- 필요한 설정: 없음. 기존 인증·리포트 채팅 API 사용, DB/백엔드 변경 없음

## 2026-10-24 · Codex · `main`
- 한 일: 입력 중 언어 변경 watch 제거. Enter/전송 버튼으로 제출할 때만 언어 감지·전환, Shift+Enter 줄바꿈 및 영어 메인 유지
- 상태: 프런트30개 테스트·빌드 통과. 브라우저 샘플 채팅에서 한국어 입력/Shift+Enter 시 영어 유지, Enter 제출 후 한국어 UI·답변 전환 확인. 실 AI 재호출 없음(백엔드 변경 없음)
- 막힌 것: 없음
- 다음: 새로고침 후 사용
- 필요한 설정: 없음

## 2026-10-24 · Codex · `main`
- 한 일: 영어 기본값 및 메인/업로드 화면 영어 고정. 입력 언어 10개(en/ko/zh/ja/es/fr/de/pt/ru/hi) 로컬 감지, 분석 화면 UI·예시 질문·새 AI 응답 언어 연결. 8개 언어별 253개 정적 번역, 기존 한국어 유지. 서버 언어 코드 검증/언어명 확장, 번역·감지 번들 분리
- 상태: 프런트30개 테스트/빌드 통과. 백엔드 전체63개 중54통과/9조건부 제외 및 별도 실호출 언어 테스트1개 통과(프랑스어·일본어 HTTP200). 브라우저 한국어/스페인어 입력 후 영어 홈 유지·결과 화면 전환 확인. 서버 재시작/health live 확인. 인증 PDF 업로드와 10개 언어 전체 실호출은 미검증
- 막힌 것: 짧거나 혼합된 입력은 언어 오인식 가능. 번역은 기계 보조이며 원문·저장된 설명/대화는 재번역하지 않음. 모호한 일본어 remaining 질문은 IP 이후 수치를 반환해 언어 실호출 테스트는 명확한 이수 학점 질문으로 분리(docs/LANGUAGES.md)
- 다음: 실제 질문/보고서로 번역 표현과 학사 해석 확인
- 필요한 설정: 없음. DB 변경 없음. frontend에서 npm ci로 franc-min 설치

## 2026-10-24 · Codex · `main`
- 한 일: Math minor 순서 구현: 전체 PDF 과목표 추출/중복 제거 → 공식2026–27 요건 파일+DB정책 저장 → C이상/IP/중복/상한 자동 비교 → 화면 플래너와 챗봇 연결. 후보 과목/선수과목/출처/학년도·전공중복 확인 표시. 기존 CS 보고서에37개 수강내역 보완
- 상태: 프런트26개·빌드 통과. 백엔드62개 중 실제PDF 포함56통과/6제외 후 별도 Luminal Math minor 테스트 HTTP200 통과(합산57통과/5제외). DB dry-run→적재→no-op, 기존 보고서 백업·조건부 갱신·새 연결 검증. 서버 재시작. 인증 브라우저 전체 흐름 미검증
- 막힌 것: 사용자PDF는 Spring2023 요건, Math 규칙은2026–27이므로 참고 비교. 전공필수와 다른6학점·대체/중복 인정·신청 상태는 확인 필요. PDF 전체성은 보장하지 않으며 미해석행2건 표시
- 다음: 보고서 재열기 후 Math minor 플래너 확인/질문. 적용 학년도와 중복 인정은 어드바이저 확인
- 필요한 설정: 없음. 신규 DB 스키마 없음. docs/MATH-MINOR.md 및 DB현황 문서 참조

## 2026-10-24 · Codex · `main`
- 한 일: 수강 중/예정 학점으로 전부 채워지는 요건을 충족/예정 목록과 집계에 포함, 미완료 및 추가 과목 선택 안내에서 제외. 실제 이수 완료/IP는 그대로 구분. 저장 보고서 조회와 채팅에 coveredByPlan 표시를 계산해 추가 수강을 권하지 않도록 적용
- 상태: 프런트25개 및 빌드 통과. 백엔드56개 중49통과/7조건부 제외. 완전/부분 IP, 검토/누락값, 차트 필터 테스트 통과. 백엔드 재시작. 실제 유료 챗봇 답변/로그인 브라우저 미검증
- 막힌 것: 예정 과목 통과를 전제로 충족 처리. 일부 학점만 채워지거나 검토 필요인 항목은 미완료 유지
- 다음: 기존 보고서에서 완료19+예정6=충족25 확인
- 필요한 설정: 없음. 저장된 상태/학점 및 DB 스키마 변경 없음

## 2026-10-24 · Codex · `main`
- 한 일: 요건별 실제 인정 과목 표 추가(코드·과목명·수강 학기·학점·성적/IP·PDF 페이지). 선택 과목과 구분하고 과목표 합계 검증 성공 시에만 표시. 기존 CS 보고서에도 24요건의 30개 과목 배정 내역 저장, 완료19/전체 학점 유지
- 상태: 실제 PDF 검증 포함 백엔드55개 중50통과/5제외, 프런트24개 테스트 및 빌드 통과. DB 원본 비공개 백업·조건부 갱신 후 새 연결에서 동일성 확인. 서버 재시작. 로그인 브라우저 검증 미실시
- 막힌 것: GPA 등 개별 과목표가 없거나 대조 실패한 요건은 확인된 내역 없음 표시. 30개는 요건 간 중복을 포함한 배정 건수
- 다음: My reports에서 보고서를 다시 열고 완료 요건 펼치기
- 필요한 설정: 없음, DB 스키마 변경 없음

## 2026-10-24 · Codex · `main`
- 한 일: 완료 0개 원인 확인: 저장된 CS PDF 분석에 미완료 2개만 있고 완료 항목 누락. LionPATH Satisfied 항목 직접 복원 및 과목표 used/IP 대조 추가, 이수 완료와 수강 중 구분. 새 분석 프롬프트에 완료 항목 포함 명시. 기존 해당 보고서 1건에 28항목 복원(완료19/수강중6/검토3), 기존 미완료2 및 전체 학점 유지
- 상태: 실제 사용자 PDF로 복원 결과 검증, 백엔드 55개 중 50통과/5조건부 제외, 프런트 23개·빌드 통과. DB 원본은 Git 밖에 제한 권한으로 백업하고 낙관적 잠금 갱신 후 새 연결에서 동일성 확인. 서버 재시작. 로그인 브라우저에서 저장 보고서 재열기는 미검증
- 막힌 것: 과목표 대조 불가능한 3항목은 검토로 유지. 복원은 명시적 Satisfied 섹션에 한정하며 다른 누락 미완료 항목의 전체 재분석은 아님. 요건 그룹은 서로 중복될 수 있음
- 다음: My reports에서 기존 보고서 다시 열어 완료19 항목 확인
- 필요한 설정: 없음. DB 스키마/카탈로그 변경 없음

## 2026-10-24 · Codex · `main`
- 한 일: 불확실한 질문/과목 추천에 Google Search grounding 추가. 공식 PSU 자료와 Reddit/RMP 경험담 구분, 실제 검색 메타데이터 출처 링크·조회 시각·불확실성/어드바이저 안내 표시. 검색 on/off, 기존 TEXT 컬럼에 출처 포함 대화 보존, Gemini 실패 시 Luminal 대체. 고정 거절보다 가능한 선택지와 구체적 다음 단계로 답하도록 프롬프트 수정. `docs/WEB-SEARCH.md` 참고
- 상태: 프런트 23개·빌드 통과. 백엔드 49개 중 43개 통과/6개 조건부 제외, 별도 실제 Luminal 합성 입력 테스트 HTTP 200 통과(합산 44통과/5제외). 검색 API 실제 요청은 429 사용량 초과, 성공 검색 미검증. 백엔드 재시작 및 live health 확인, 샘플 채팅 브라우저 확인. 인증된 웹 검색 전체 흐름 미검증
- 막힌 것: 현재 Gemini 검색 쿼터 초과. 실시간 검색은 쿼터 복구 필요, UI에 실패 상태 표시하고 보고서/DB 기반 답변 계속. Luminal 크레딧과 Gemini 검색 쿼터는 별도
- 다음: Gemini 쿼터 복구 후 실제 교수 후기 질문에서 웹 출처/학년도/불확실성 안내 확인
- 필요한 설정: 기존 GEMINI_API_KEY 사용. 선택 GEMINI_SEARCH_MODEL(기본 GEMINI_MODEL); DB 스키마 변경 없음

## 2026-10-24 · Codex · `main`
- 한 일: 요건 상세에 PDF 선택 과목 카드/추가 필요 학점/과목 선택 질문 버튼 추가. Bulletin 연결 항목은 과목명·선수과목과 AND/OR 선택 트리, 최소 성적·학점·검토 조건을 펼쳐 표시. 기존 main의 DB 채팅 연결 변경 fast-forward 반영
- 상태: 프런트 19개 테스트 통과(중첩 AND/OR, 미확인 승인 목록, 한국어 표시 포함), Vite 빌드·diff 공백 검사 통과. 실제 브라우저/유료 AI 답변 미검증. DB 스키마와 백엔드 로직 변경 없음
- 막힌 것: PDF/일치하는 카탈로그에 목록이 없으면 과목을 생성하지 않고 확인 필요 표시. 개인별 이수 과목 대조나 수강 자격 확정 기능은 아님
- 다음: 보고서 요건을 펼쳐 과목 목록 확인. 학년도/전공이 불일치하는 보고서는 적용 가능한 카탈로그 확보 필요
- 필요한 설정: 없음

## 2026-10-24 · CosmosBoy112 · `main`
- 한 일: 채팅이 TigerData 카탈로그(Bulletin 2026-27, Handbook 2024-25)를 근거로 답하도록 `CatalogDbService` 추가. 리포트 전공이 DB의 전공(DATSC_BS, CMPSC_BS)과 일치할 때만 요건 트리·전공 진입 요건·규정·질문 속 과목의 선수과목/열리는 과목을 `report.catalogDb`로 붙임. CMPSC 적재 메타데이터는 원문으로 넘기지 않고 검토 표시만 전달, 빈 목록은 미확인으로 표시. 학년도가 다르면 답변에 명시, 일치하지 않는 전공은 카탈로그 질문에 일반 지식으로 답하지 않음. 문서: `docs/CHANGES-advisor-catalog-db.md` "채팅 연결" 절
- 상태: 백엔드 테스트 41개 중 35개 통과·6개 설정상 제외(`CatalogDbServiceTest` 6개 추가). `LiveCatalogDbTest`로 실제 DB 확인(문맥 DS 약 17KB / CMPSC 약 26KB, 약칭 `Comp Sci - Univ Park (BS)`는 major_not_in_catalog). 임시 테스트로 실제 Luminal 답변 확인: DS 리포트는 CMPSC 465 선수과목·List A를 출처와 함께 답하고 2026-27/2024-25 차이를 명시, CMPSC 리포트는 빈 승인 자연과학 목록을 "아무 GN"으로 해석하지 않고 requires_review를 명시. 이 컴퓨터의 Gemini 키는 404라 Gemini 답변과 브라우저 전체 흐름은 미검증
- 막힌 것: 실제 Data Sciences 리포트의 요건 학년도는 Fall 2024(2024-25)인데 DB는 2026-27판만 있음. 실제 CMPSC 리포트 전공 표기가 약칭(`Comp Sci - Univ Park (BS)`)이라 매칭 안 됨
- 다음: 2024-25 Bulletin(archive) 적재 검토, 전공 약칭 별칭 테이블 검토, 실제 화면에서 질문 확인
- 필요한 설정: 없음

## 2026-10-24 · Codex · `main`
- 한 일: 영어 Inter·한국어 Noto Sans KR로 글꼴 통일. 제목 굵기/자간 완화, 학점 숫자 폭 정렬, 한국어 제목 줄바꿈 조정
- 상태: Vite 빌드 및 diff 공백 검사 통과. CSS만 변경, 동작 테스트 재실행 및 브라우저 시각 검증 미실시
- 막힌 것: 없음
- 다음: 새로고침 후 글꼴 확인
- 필요한 설정: 없음. 기존 Google Fonts 사용 방식 유지, 로딩 실패 시 시스템 글꼴 적용

## 2026-10-24 · Codex · `main`
- 한 일: 메인/대시보드 홍보 문구, 중복 그래프 설명, 채팅 인사·중복 안내 제거. 영어·한국어 간결한 제목/설명 적용. BLS 푸터, 개인정보 전송 동의, 학점 해석·검토 안내 유지
- 상태: 프런트 테스트 18개, Vite 빌드, diff 공백 검사 통과. 브라우저 시각 검증 미실시. 백엔드/DB/학점 계산 로직 변경 없음
- 막힌 것: 없음
- 다음: 실제 보고서 화면에서 간소화된 안내 확인
- 필요한 설정: 없음

## 2026-10-24 · Codex · `main`
- 한 일: UP CMPSC_BS/CMPSC_ETM 2026–27을 기존 TigerData 카탈로그 테이블에 추가. 요건 노드 85개·규칙 94개·정책 메타데이터 5건. 스키마 변경/기존 행 삭제·갱신 없이 공용 과목 75개 참조. 전용 적재기 `ingest/load_cmpsc_db.py` 추가
- 상태: dry-run 롤백 → 실제 커밋 → 새 연결에서 재실행(no-op) 확인. 요건 트리/정책 원본 왕복 대조, 스키마 지문·공용 및 DATSC 내용 해시 동일 확인. ingest 테스트 15개 통과. DB 현황 문서 갱신. 앱 런타임 코드는 변경하지 않아 프런트/백엔드 테스트 재실행하지 않음
- 막힌 것: 승인 목록/구판 Handbook/선수과목 검토 표시 유지. DB 적재는 자동 졸업 판정이나 앱의 DB 카탈로그 조회 구현을 포함하지 않음
- 다음: DB를 읽는 도구는 catalog_snapshot의 캠퍼스·학년도·requires_review·eligibility를 함께 해석
- 필요한 설정: 기존 TIGER_DATABASE_* 및 psycopg 사용, 새 환경변수 없음

## 2026-10-24 · CosmosBoy112 · `catalog-db-load`
- 한 일: TigerData에 Advisor 카탈로그 테이블(`advisor-schema.sql` 섹션 1–6) 생성, Data Sciences B.S.(Computational 옵션 포함)·ETM 요건, 과목 357개, 선수과목 트리, 규정 5건(권장 학기 계획, 자연과학·Department List 제외 등) 적재. 적재기 `ingest/load_catalog_db.py`, 규정 생성기 `ingest/build_datsc_policies.py`, `policies/DATSC_BS.json` 추가. **DB 구조·현황 문서: `docs/CHANGES-advisor-catalog-db.md` "DB 적재 현황" 절**
- 상태: dry-run(트랜잭션 롤백) 후 적재. 백엔드 테스트 34개 중 29개 통과·5개 설정상 제외, ingest 테스트 12개 통과. 행 수가 JSON과 일치(prereq 880, 요건 노드 90, 규칙 473). Computational 요건·List A·CMPSC 465 선수과목·권장 계획을 쿼리로 Bulletin과 대조. Bulletin 과목 페이지 43개 재수집 결과 기존 시드와 동일(참조 과목 14개 제목만 보충). `review` 137건 수동 검토, 섹션 7–12 실행, 앱 연동은 미실시
- 막힌 것: DTSCE Handbook 2025-26/2026-27판이 공개되지 않음 → Handbook 규칙은 2024-25판 기준으로 표시. 졸업 학점이 Handbook 126 / Bulletin 123으로 다름
- 다음: 백엔드에서 카탈로그 테이블을 읽는 도구(`check_prerequisites`, `get_program`) 구현 후 채팅 연결
- 필요한 설정: 적재 시 `pip install -r ingest/requirements.txt` (psycopg, beautifulsoup4 추가). 새 `.env` 값 없음

## 2026-10-24 · Codex · `main`
- 한 일: 공통 글자 크기(본문16/보조14/제목22px) 적용. 기본 영어, 목표/질문 입력에 따라 영어·한국어 UI 자동 전환. 분석 language와 채팅 responseLanguage 전달, 원문 제목/근거 보존
- 상태: 프런트 18개 테스트·빌드 통과, 백엔드 29개 통과·조건부 5개 제외. 브라우저에서 한국어→영어 전환, 한국어 샘플 대시보드·채팅 표시 확인. 실제 유료 AI 다국어 생성은 미검증
- 막힌 것: UI 번역은 영어·한국어 지원. 기존 저장된 분석 설명/대화는 자동 재번역하지 않음
- 다음: 새 분석/질문에서 입력 언어에 따른 응답 확인
- 필요한 설정: 새 환경변수 없음. 업로드 language 및 채팅 language는 en/ko, 기본 en

## 2026-10-24 · Codex · `main`
- 한 일: 작은 채팅 팝업을 화면 높이의 540px 사이드 패널로 확대. 본문/입력 16px, 제목 21px, 넓은 줄간격과 3줄 입력창 적용. 1400px 이상에서 보고서 공간 분리, 모바일 전체 화면, 안전 영역/낮은 화면 대응
- 상태: 프런트 테스트 15개 및 Vite 빌드·diff 공백 검사 통과. 실제 브라우저 시각 검증은 미실시
- 막힌 것: 없음
- 다음: 보고서에서 Ask your report를 열어 가독성 확인
- 필요한 설정: 없음

## 2026-10-24 · Codex · `main`
- 한 일: LionPATH 학위 총계의 used가 IP를 포함하는 문제 수정. 인식 가능한 CMPSC 총계 표의 고유 과목 합계와 used/needed를 대조한 후 완료·IP를 분리. 불완전/불일치 표는 미확인 처리. IP 학기별 분포 및 예정 학기 포함 표시
- 상태: 백엔드 28개 통과·조건부 5개 제외, 프런트 15개와 빌드 통과. 사용자 제공 PDF는 로컬에서 별도 검증하여 합계 일치 확인. 원본/개인정보는 저장소에 추가하지 않음. 실제 AI 재업로드 및 기존 DB 보고서 수정은 미실시
- 막힌 것: 검증기는 인식된 CMPSC 학위 총계 표에 한정됨. 항목별/다른 형식의 AI 추출은 별도 검토 필요
- 다음: 기존 보고서를 재업로드하여 수정된 총계 저장
- 필요한 설정: 없음

## 2026-10-24 · Codex · `main`
- 한 일: 전체/항목별 Remaining 의미를 분리. Still to earn은 required−completed, After current courses는 required−completed−inProgress로 계산. 그래프 3구간은 중복 없이 유지하고 전체 미취득 학점을 별도 표시
- 상태: 프런트 테스트 15개 및 Vite 빌드 통과. 수강 중 과목이 부족 학점을 전부 채우는 경우, 미확인 값, 소수/초과 학점과 실제 컴포넌트 표시 검증. 사용자 PDF의 추출 숫자 및 실제 브라우저 시각 검증은 미실시
- 막힌 것: 없음
- 다음: 전체 학점 그래프의 새 구분 확인
- 필요한 설정: 없음

## 2026-10-24 · Codex · `main`
- 한 일: Show completed의 추가 표시 토글을 To complete / Completed 필터로 변경. 선택 상태·각 항목 수·완료 항목 0건 안내를 표시하여 결과 변화가 명확하게 보이도록 수정
- 상태: 프런트 테스트 12개와 Vite 빌드 통과. 실제 Vue SFC를 메모리 렌더러로 마운트하여 버튼 이벤트→완료 항목만 표시→되돌리기 및 완료 0건 안내 검증. 실제 브라우저 클릭은 미검증
- 막힌 것: 없음
- 다음: 사용자의 보고서에서 필터 확인
- 필요한 설정: 없음

## 2026-10-24 · Codex · `main`
- 한 일: 요건별 막대그래프와 학점 도넛의 글자 확대. 제목 22px, 항목명 16px, 범례 14px, 설명 13px로 조정하고 좁은 화면에서는 도넛 범례 줄바꿈 허용
- 상태: Vite 빌드 및 diff 공백 검사 통과. 실제 브라우저 시각 검증 미실시
- 막힌 것: 없음
- 다음: 보고서 화면 확인
- 필요한 설정: 없음

## 2026-10-24 · Codex · `main`
- 한 일: 각 요건별 이수 완료·수강 중·남은 학점 막대그래프 추가. 부족 학점 내림차순, 완료 항목 표시 토글, 상세 요건으로 이동/포커스, 미확인 학점 별도 표시. 모바일/다크 모드 스타일 적용
- 상태: 프런트 테스트 11개 및 Vite 빌드 통과, diff 공백 검사 통과. 브라우저 랜딩 페이지 로딩 확인, 샘플 버튼 자동 조작에 반응이 없어 그래프 시각 검증은 미완료
- 막힌 것: 없음
- 다음: 실제 보고서에서 항목별 그래프 확인
- 필요한 설정: 없음

## 2026-10-24 · Codex · `main`
- 한 일: 대시보드 오른쪽 안내 카드를 이수 완료·수강 중·남은 학점 도넛 그래프로 교체. 중앙 완료율, 범례, 학점 미확인 상태, 다크 모드/모바일 스타일 적용
- 상태: 프런트 테스트 11개와 Vite 빌드 통과. 실제 브라우저 시각 검증은 탭 접근 불가로 미실시
- 막힌 것: 없음
- 다음: 사용자 보고서 화면에서 그래프 확인
- 필요한 설정: 없음

## 2026-10-24 · Codex · `main`
- 한 일: 공식 CMPSC 카탈로그를 PDF 업로드→검증→TigerData 저장→보고서 조회/내보내기→Gemini/Luminal 채팅에 연결. Vue에 공식 비교·학점 충돌·과목 출처 표시. 기존 보고서는 재업로드 전 범위 미확인 처리
- 상태: 백엔드 일반 테스트 26개 통과(외부 서비스 4개 기본 제외), 프런트 테스트 11개·빌드 통과. 별도로 LiveDatabaseTest를 실행해 카탈로그 포함 JSON의 실제 TigerData 저장/조회·소유권·삭제 검증 후 롤백. 실제 사용자 PDF/로그인 브라우저와 유료 AI 생성, Docker 실행은 미검증
- 막힌 것: 현재 Handbook 학년도 및 미확인 승인 목록 제약은 유지. 자동 졸업/수강 가능 판정 없음
- 다음: 새 PDF를 업로드하여 해당 보고서의 학년도와 항목 매칭을 확인
- 필요한 설정: 새 .env/DB 마이그레이션 없음. Docker 빌드는 저장소 루트에서 `docker build -f backend/Dockerfile -t lionpathway-api .`

## 2026-10-24 · Codex · `main`
- 한 일: University Park CMPSC_BS/ETM 2026–27 Bulletin 수집, 과목 75개 매칭, 2024–25 Handbook 교차표와 학년도 충돌 표시. 재수집 스크립트·보고서 항목 명칭 매칭 CLI 추가. 상세: `docs/CHANGES-cmpsc-catalog.md`
- 상태: 카탈로그/매칭 테스트 12개 통과, 오프라인 재생성 동일성 및 관련 없는 기존 과목 보존 확인, diff 공백 검사 통과. 앱·DB 연결과 브라우저 동작은 이번 변경 범위 밖이며 미검증
- 막힌 것: 공식 페이지에 2026–27 학부 Handbook 없음. Department List/승인 자연과학 목록과 선수과목 review 14건은 확인 필요
- 다음: 시드 로더와 감사 도구 구현 후 PDF 분석 흐름 연결. 미해결 조건을 자동 충족으로 처리하지 않기
- 필요한 설정: Python lxml/pypdf (`ingest/requirements.txt`). 새 .env 값 없음

## 2026-10-24 · CosmosBoy112 · `backend`
- 한 일: `AGENTS.md`에 "참고 문서" 섹션 추가. DB 스키마(`schema.sql`, `docs/advisor-schema.sql`), 도구 명세(`docs/advisor-tools.md`), 카탈로그 시드·파서, 카탈로그 변경 내역 위치 안내. `advisor-catalog-db` 브랜치 내용을 `backend`에 반영
- 상태: 문서만 변경. 병합 상태에서 백엔드 테스트 24개 중 20개 통과, `Live*Test` 4개는 설정상 건너뜀, 프런트 빌드 통과
- 막힌 것: 없음
- 다음: `backend` → `main` PR
- 필요한 설정: 없음

## 2026-10-24 · WoosungLim01 · `advisor-catalog-db`
- 한 일: Advisor 에이전트용 DB 스키마 초안(`docs/advisor-schema.sql`), 도구 명세(`docs/advisor-tools.md`), 2026-27 Bulletin 과목 302개·Data Sciences B.S./ETM 요건 시드(`data/catalog/2026-27/`), Bulletin 파서(`ingest/parse_bulletin_courses.py`) 추가. 앱 코드는 변경 없음. 자세한 내용은 `docs/CHANGES-advisor-catalog-db.md`
- 상태: JSON 3개 파싱과 파서 `py_compile`만 확인. 스키마 실제 DB 실행, 시드 적재는 미검증. 아직 `DatabaseConfig`에 연결하지 않음
- 막힌 것: `courses.json`의 `review` 137건은 사람이 확인해야 함 (TEXT로 남은 선수과목 등)
- 다음: TigerData 개발 DB에서 스키마 실행, 시드 로더 작성, `check_prerequisites`·`run_audit` 도구 구현
- 필요한 설정: 스키마 11절에 `timescaledb` 확장 필요 (Tiger Cloud 기본 설치). 새 `.env` 값 없음

## 2026-10-24 · CosmosBoy112 · `backend`
- 한 일: 채팅 질문 라우팅 추가. 계획·What-If·비교·순서 질문(또는 400자 초과)은 Luminal(`deepseek-ai/DeepSeek-V4.1-Flash`), 나머지 질문과 PDF 요건 추출은 Gemini. Luminal 실패·미설정 시 Gemini로 자동 전환. `/chat` 응답에 `provider` 필드 추가. 동의 문구에 Luminal 명시
- 상태: 백엔드 단위 테스트 20개 통과(`ModelRouterTest` 6개 포함), 프런트 빌드 통과, Luminal `/chat/completions` 직접 호출 200 확인. 로그인 후 브라우저에서 실제 질문 흐름과 `Live*Test`는 미검증
- 막힌 것: 일부 계정의 Gemini 키(`AQ.` 형식)는 모델 조회는 되지만 생성 요청이 모든 모델에서 HTTP 404. 계정·프로젝트 쪽 원인으로 추정. 로컬 Auth0 Callback URL에 `http://localhost:5173`, `http://127.0.0.1:5173` 둘 다 등록 필요
- 다음: `main` 병합, 실제 화면에서 계획 질문 시 백엔드 로그에 `Luminal response ... status=200` 확인
- 필요한 설정: `backend/.env`에 `LUMINAL_API_KEY`, `LUMINAL_BASE_URL=https://flash.luminal.cloud/v1`, `LUMINAL_MODEL=deepseek-ai/DeepSeek-V4.1-Flash`
