# 검증 결과

## 최신 구현 검증

- Vue production build: 통과
- Frontend Node tests: 5개 통과
- Spring Boot Maven package: 통과
- Backend 일반 JUnit tests: 12개 통과
- 실제 Gemini 구조화 분석 + TigerData 저장/조회 + Gemini 답변 통합 테스트: 통과
- 실제 TigerData CRUD / 소유자 분리 / 대화 기록 / 삭제 cascade 테스트: 통과
- 실제 연결 테스트에는 가상 데이터만 사용했으며 DB 테스트 레코드는 롤백
- `.env` 자동 로드 후 `/api/health`: `live: true`
- 비로그인 `/api/reports`: HTTP 401
- Auth0 OIDC discovery 및 프런트/백엔드 domain·audience 일치: 확인
- 브라우저에서 Auth0 로그인: `Service not found: https://lionpathway-api`로 차단. Auth0 Dashboard에 해당 API Identifier 등록 필요
- 로그인 후 실제 사용자 PDF 업로드: 위 Auth0 설정이 해결되기 전까지 브라우저 전체 흐름 미검증
- 실제 학생 LionPATH PDF 정확도: 보고서 미제공으로 미검증
- DeepSeek/루미나리: API 명세 및 연결 설정 미제공으로 미연결

## 모델 설정 변경

기존 `gemini-2.5-flash`는 모델 정보 조회에는 응답했지만 생성 요청은 신규 사용자에게 지원하지 않는다는 HTTP 404를 반환했습니다. 제공자 응답이 안내한 `gemini-3.8-flash`로 로컬 GEMINI_MODEL을 변경했고, 구조화 분석과 질문 응답을 검증했습니다. 실제 응답 시간과 과금은 모델·계정 상태에 따라 달라집니다.

## 기존 화면 검증

첫 화면, 샘플 대시보드, 완료 필터, Department List 상세, 원문 근거 모달, 요건별 샘플 채팅을 확인했습니다. 샘플 화면의 답변은 사전에 작성된 로컬 응답이며 실제 Gemini 응답과 구분됩니다.
