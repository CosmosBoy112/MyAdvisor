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
