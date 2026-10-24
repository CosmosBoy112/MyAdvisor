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
- DeepSeek/루미나리: Luminal `flash.luminal.cloud/v1`의 `deepseek-ai/DeepSeek-V4.1-Flash` 연결. 직접 호출 200 확인, 앱 화면에서의 전체 흐름은 미검증 (아래 "Luminal 라우팅" 참고)

## 모델 설정 변경

기존 `gemini-2.5-flash`는 모델 정보 조회에는 응답했지만 생성 요청은 신규 사용자에게 지원하지 않는다는 HTTP 404를 반환했습니다. 제공자 응답이 안내한 `gemini-3.8-flash`로 로컬 GEMINI_MODEL을 변경했고, 구조화 분석과 질문 응답을 검증했습니다. 실제 응답 시간과 과금은 모델·계정 상태에 따라 달라집니다.

## 기존 화면 검증

첫 화면, 샘플 대시보드, 완료 필터, Department List 상세, 원문 근거 모달, 요건별 샘플 채팅을 확인했습니다. 샘플 화면의 답변은 사전에 작성된 로컬 응답이며 실제 Gemini 응답과 구분됩니다.


## 새로고침 인증 유지 수정

- Auth0 SDK 캐시를 memory에서 localstorage로 변경하고 refresh token 사용 및 SSO fallback 설정
- SDK 초기화 중에는 Checking session 표시, 인증 완료 후에만 보고서 목록 요청
- 프런트 테스트 7개 및 production build 통과
- 회귀 테스트: SDK 캐시 인스턴스를 재생성해도 가상 인증 항목 유지, 항목 제거 후 복원되지 않음, 세션 복원 중 signed-out UI 방지
- 실제 계정의 로그인 → 새로고침 → 토큰 만료 후 갱신 전체 흐름은 이번 수정에서 자동 검증하지 않음. 사용자는 기존 Auth0 API 등록 문제를 해결했다고 알림
- 수정 적용 후 재로그인 필요. 지속적인 토큰 갱신에는 Auth0 Refresh Token Rotation / Allow Offline Access 설정 필요

## PDF 업로드 무한 대기 수정

- 인증 토큰을 기다리는 시간까지 포함하는 하드 타임아웃 적용
- 멈춘 인증 후 늦게 토큰이 도착해도 취소된 파일을 업로드하지 않음
- HTTP 헤더 이후 본문이 멈추는 경우에도 시간 제한 적용
- 업로드 단계와 실제 경과 시간 표시, 대기 중단 버튼 추가
- 분석 결과 표시와 저장 보고서 목록 새로고침 분리
- 서버 AI 요청 Future 취소 / 504 오류, DB 통신·쿼리·잠금 시간 제한 추가
- 프런트 테스트 11개, 백엔드 일반 테스트 14개 및 두 빌드 통과
- 사용자의 실제 PDF를 재전송하지 않았으며 그 파일의 성공 여부는 별도 확인 필요


## PDF 분석 100초 실패 원인 확인 및 모델 교체

- 실제 업로드 2건: PDF 추출 337ms / 55ms 성공 후 AI 단계에서 100초 timeout 확인
- 기존 gemini-3.8-flash: 작은 가상 보고서도 약 96.8초 소요, 별도 요청에서 HTTP 503 high demand 확인
- gemini-3.5-flash도 긴 분석에서 HTTP 503 발생. 로컬 설정 및 예시를 gemini-3.5-flash-lite로 변경
- Gemini 3에는 low thinking 설정, 요건 중복 출력 방지 지시, 응답 상태/시간/토큰 수만 로그에 기록
- 출력 중단(MAX_TOKENS), 과부하(503), 시간 초과를 구분하며 PDF 크기가 원인이라고 단정하지 않는 오류 문구 적용
- 실제 API 검증: 8페이지/24개 요건 가상 보고서 7.6초, 전체 요건 수/학점 합계/원문 인용 일치 통과
- 짧은 보고서 분석 약 1.3초, 챗봇 약 1초, TigerData 저장/조회/소유권 격리/대화 저장 통과. 테스트 데이터 rollback
- 사용자 실제 PDF는 재업로드 후 성공 여부와 추출 정확도 확인 필요. 위 시간은 가상 입력에 대한 측정이며 보장값이 아님
- 모델 추론 설정 참고: https://ai.google.dev/gemini-api/docs/generate-content/thinking


## Luminal 라우팅 추가

- 계획·What-If·비교·순서 질문과 400자 초과 질문은 Luminal, 나머지 채팅과 PDF 분석은 Gemini
- `ModelRouterTest` 6개: 질문 분류, 제공자 선택, Luminal 실패 시 Gemini 전환, 키 미설정 시 Luminal 생략 확인
- 백엔드 일반 JUnit tests 20개, 프런트 production build 통과
- Luminal `/chat/completions` 직접 호출: HTTP 200, OpenAI 호환 응답 형식 확인
- 로그인 후 브라우저에서 Luminal 응답 확인: 미검증

## Luminal 실제 연결 검증

- 로컬 backend/.env의 설정으로 가상 보고서와 학기별 계획 비교 질문을 전송
- 설정 모델 deepseek-ai/DeepSeek-V4.1-Flash: HTTP 200, 실제 답변 수신, 약 3.5초
- ModelRouter 결과 provider=luminal 확인. Gemini fallback이 호출되지 않았는지도 검증
- 실제 학생 PDF/기존 사용자 보고서는 테스트에 사용하지 않음. DB 저장 없음
- 재실행: backend 디렉터리에서 `LIONPATHWAY_LUMINAL_TEST=1 ./mvnw -Dtest=LiveLuminalTest test`
- 이 테스트는 명시적으로 활성화해야 실행되며 API 사용량이 발생함

## 2026-10-24 CMPSC 공식 카탈로그

- `python -m unittest discover -s ingest -p 'test_cmpsc_catalog.py' -v`: 12/12 통과. 캠퍼스/학년도, ETM, 복합 대안, 교차개설, 출처, 미확인 목록과 과거 Handbook 격리 검증.
- 공식 과목 코드 75/75 매칭, 불일치 0. 선수과목 review가 있는 매칭 과목 14개는 수동 확인 필요.
- 캐시 기반 재생성 전후 모든 카탈로그 JSON SHA-256 동일. CMPSC 대상이 아닌 기존 과목은 HEAD 대비 그대로 유지됨.
- `git diff --check` 통과. 프런트/백엔드 런타임 코드 미변경으로 빌드 재실행하지 않음. DB 적재·실제 PDF 매칭·졸업 판정은 구현/검증하지 않음.

## 2026-10-24 CMPSC 앱 연결

- Maven 일반 테스트: 30개 중 26개 통과, Live* 4개 환경변수 조건으로 제외. 새 CatalogServiceTest 6개는 범위 거부, 원문 확인, 학점 보존/충돌, 미확인 목록, 27개 선택 코드, 업로드 저장 인자와 채팅 컨텍스트를 검증합니다.
- 별도 `LIONPATHWAY_LIVE_TEST=1 ... -Dtest=LiveDatabaseTest test`: 실제 TigerData JSONB에 매칭 결과 저장/조회 검증, 소유권/대화/삭제 확인, 트랜잭션 롤백 완료.
- 프런트 11개 테스트 통과 및 Vite production build 성공.
- 실제 로그인 브라우저/PDF/AI 생성 및 Docker 컨테이너는 이번 검증에 포함하지 않았습니다.

- 변경된 백엔드를 live 프로필로 재시작하고 `/api/health` 응답 확인.
