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
