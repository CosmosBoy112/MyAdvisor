# LionPathway

Penn State What-If Report를 학점 대시보드와 요건별 대화로 바꾸는 HackPSU MVP입니다.

**Vue 3 + Spring Boot 3 / Java 21 + Gemini + TigerData PostgreSQL + Auth0**

## 빠른 시작 — 계정 없이 샘플 보기

Node.js 20.19+ 또는 22.11+를 권장합니다.

```sh
cd frontend
npm ci
npm run dev
```

브라우저에서 `http://localhost:5173`을 열고 **Explore a sample**을 누르세요. 샘플 화면과 샘플 답변은 브라우저에서만 실행됩니다. 실제 PDF를 분석한 것처럼 표시하지 않습니다. 초기 페이지는 검색창과 PDF 첨부 버튼으로 구성됩니다.

## 구현된 기능

- 반응형 Vue 화면, 자연어 전공·마이너 입력, PDF 선택 및 드래그 앤 드롭
- 완료 / 수강 중 / 남은 학점 막대그래프, 값이 없으면 미확인 처리
- 요건별 필터, 상세 정보, PDF 근거 페이지와 인용문 확인
- 요건별 질문 및 Gemini 대화, 최근 12개 메시지 문맥 전달
- 로그인 후 My reports에서 보고서 다시 열기, 최근 대화 복원, 확인 후 삭제
- Auth0 로그인, JWT issuer / audience 검증, 보고서 소유자 검증
- PDFBox 텍스트 추출: 10 MB / 40페이지 / 추출 텍스트 160,000자 제한
- Gemini JSON Schema 기반 출력, 수치 검증, 인용문과 실제 페이지 텍스트 대조
- TigerData에 분석 결과와 대화 저장, 보고서 삭제 API
- 분석 JSON 다운로드

## 실제 서비스 연결

### 1. Auth0

Auth0에서 Single Page Application과 API를 생성합니다.

- Allowed Callback URLs: `http://localhost:5173`
- Allowed Logout URLs: `http://localhost:5173`
- Allowed Web Origins: `http://localhost:5173`
- API Identifier 예: `https://lionpathway-api`
- API 토큰 서명 알고리즘: RS256

`frontend/.env.example`을 `frontend/.env.local`로 복사하고 값을 입력하세요. SPA에는 **client secret이 필요하지 않습니다**. 프런트의 audience와 백엔드의 audience가 같아야 합니다. `localhost`와 `127.0.0.1`을 섞지 마세요. 주소를 변경하면 Auth0 허용 URL과 FRONTEND_ORIGIN도 맞추세요.

### 2. TigerData

Tiger Cloud PostgreSQL 서비스의 연결 정보를 준비합니다.

```text
TIGER_DATABASE_URL=jdbc:postgresql://YOUR-HOST:YOUR-PORT/tsdb?sslmode=require
TIGER_DATABASE_USER=tsdbadmin
TIGER_DATABASE_PASSWORD=...
```

연결 정보는 백엔드 환경변수로만 전달합니다. TLS를 요구하며, 배포 환경에서는 신뢰 CA를 구성한 `sslmode=verify-full` 사용을 권장합니다. 시계열 확장이 필요 없는 일반 PostgreSQL 테이블입니다. `backend/src/main/resources/schema.sql`은 live 프로필 시작 시 `CREATE IF NOT EXISTS`로 실행됩니다. 계정에는 해당 테이블 생성 권한이 필요합니다. 기존 앱 DB에 붙이기보다 이 프로젝트 전용 DB/사용자를 쓰세요.

### 3. Gemini

Google AI Studio에서 API 키를 준비하고 해당 프로젝트에서 사용 가능한 모델 이름을 `GEMINI_MODEL`에 지정하세요. 예시 값은 `gemini-3.8-flash`입니다. 모델 접근 권한과 가용성은 계정에서 확인해야 합니다.

키는 백엔드에만 둡니다. PDF 텍스트는 Google Gemini로 전송되며 결과와 채팅은 TigerData에 저장됩니다. PDF 원본은 DB/디스크에 영구 저장하지 않습니다. 업로드 전 이름·학번 등 개인정보를 제거하세요. 자동 익명화를 보장하지 않습니다.

### 4. Spring Boot 실행

JDK 21+가 필요합니다. Maven Wrapper가 포함되어 Maven을 별도로 설치하지 않아도 됩니다. 첫 실행에는 Maven 다운로드를 위한 네트워크 연결이 필요합니다. Windows에서는 `mvnw.cmd`를 사용하세요. 이제 Spring Boot 시작 시 `backend/.env`를 자동으로 읽습니다. 프로젝트 루트나 backend 폴더에서 실행할 수 있습니다. 시스템 환경변수와 JVM 설정이 로컬 파일보다 우선합니다. `.env` 내용은 로그에 출력하지 않습니다.

```sh
cd backend
# 환경변수가 없을 때: demo 프로필, 공개 health API만 제공
./mvnw spring-boot:run
```

Live 실행 시 `backend/.env.example`을 `backend/.env`로 복사해서 실제 값을 입력하세요. `SPRING_PROFILES_ACTIVE=live`도 파일에 포함합니다.

```sh
SPRING_PROFILES_ACTIVE=live ./mvnw spring-boot:run
```

필수 값: `AUTH0_ISSUER`, `AUTH0_AUDIENCE`, `TIGER_DATABASE_URL`, `TIGER_DATABASE_USER`, `TIGER_DATABASE_PASSWORD`, `GEMINI_API_KEY`, `GEMINI_MODEL`.

Auth0 issuer는 `https://YOUR-TENANT.us.auth0.com/`처럼 마지막 `/`를 포함하세요.

기본 백엔드는 `127.0.0.1:8080`에서 실행됩니다. 프런트의 Vite가 `/api`를 이 주소로 프록시합니다. 실제 서비스는 로그인한 상태에서만 업로드할 수 있으며, 서비스 연결 실패 시 샘플로 몰래 대체하지 않습니다.

Docker 사용 시:

```sh
cd backend
docker build -t lionpathway-api .
# .env는 사용자가 별도로 작성한 로컬 파일이며 Git에 포함하지 않습니다.
docker run --rm --env-file .env -p 127.0.0.1:8080:8080 lionpathway-api
```

## API

| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/api/health` | 공개 상태 확인 |
| POST | `/api/reports` | 인증 필요. multipart `file`, `intent` |
| GET | `/api/reports` | 본인 보고서 최근 50개 목록 |
| GET | `/api/reports/{id}/messages` | 소유자 확인 후 최근 대화 조회 |
| GET | `/api/reports/{id}` | 본인 보고서 조회 |
| POST | `/api/reports/{id}/chat` | 본인 보고서 질문. JSON `{"question":"..."}` |
| DELETE | `/api/reports/{id}` | 본인 보고서와 대화 삭제 |

보호 API에는 Auth0 API access token을 `Authorization: Bearer ...`로 보냅니다. ID token을 사용하지 마세요. 다른 사람의 보고서는 404로 응답합니다. demo 프로필에서는 개인 보고서 API가 차단됩니다.

## 검증

```sh
cd frontend
npm test
npm run build

cd ../backend
./mvnw test
./mvnw package
```

테스트는 학점 계산의 null/초과 학점 처리, 필터, 파일 크기 검증, 실제 PDF 텍스트 추출, 근거 불일치 처리, 비인증 차단, 보고서 소유자 조건을 다룹니다. 제공된 설정으로 Gemini/TigerData 실제 통합 테스트도 통과했습니다. Auth0는 API Identifier 등록이 필요하여 로그인 이후 브라우저 전체 흐름은 미검증입니다. 자세한 검증 범위는 VERIFICATION.md에 기록했습니다.

## 파일 구조

```text
frontend/src/App.vue            입력 화면, 대시보드, 챗봇
frontend/src/style.css          반응형 디자인
frontend/src/model.js           학점·필터·파일 검증 함수
frontend/src/sample.json        명시적으로 표시된 가상 샘플
backend/src/main/java/dev/lionpathway/
  SecurityConfig.java          Auth0 JWT·CORS·접근 정책
  PdfReader.java               PDFBox 페이지별 추출
  GeminiService.java           구조화 분석·대화 API
  AnalysisValidator.java       출력 검증·근거 대조
  ReportRepository.java        소유자 기준 DB 조회
  ReportController.java        업로드·조회·질문·삭제
backend/src/main/resources/
  analysis-schema.json         Gemini 출력 스키마
  schema.sql                   TigerData DB 테이블
```

## MVP 범위와 다음 단계

현재는 보고서를 **해석하고 설명하는 도구**입니다. 공식 졸업 판정, 선수과목 그래프, 학기별 자동 계획, 실시간 개설 정보, 모든 전공의 규칙 엔진은 포함되지 않습니다. 보고서에 없는 마이너나 선택 과목 목록을 새로 만들어내지 않도록 프롬프트를 제한합니다. LLM 해석은 오류가 있을 수 있으며 인용문 일치는 해석의 정확성을 보증하지 않습니다.

실제 LionPATH PDF로 추출 품질을 검증해야 합니다. 스캔 PDF OCR은 미지원입니다. 요건을 중복 합산하지 않으며 수강 중 학점을 이수 완료로 취급하지 않습니다. 범주가 아닌 보고서의 전체 학점 필드를 사용합니다.

공개 배포 전에는 사용자별 AI 요청량 제한과 데이터 보존 정책를 추가하세요. 현재 버전은 개발·해커톤 시연용이며 자동 배포하지 않았습니다.

## 참고 문서

- [Auth0 Vue SDK](https://auth0.github.io/auth0-vue/)
- [Gemini structured output](https://ai.google.dev/gemini-api/docs/structured-output)
- [TigerData documentation](https://docs.tigerdata.com/)


## 가장 쉬운 로컬 실행

프로젝트 루트에서 터미널 두 개를 열고 각각 실행하세요.

```sh
./start-backend.sh
```

```sh
./start-frontend.sh
```

접속 주소는 **http://localhost:5173**입니다. Auth0에 localhost를 등록했다면 127.0.0.1 주소를 섞어 사용하지 마세요.

### Auth0 `Service not found` 해결

`Service not found: https://lionpathway-api`는 로그인용 SPA는 존재하지만 audience에 해당하는 API가 등록되지 않았다는 뜻입니다. Auth0 Dashboard → Applications → APIs → Create API에서 Identifier를 `https://lionpathway-api`, 서명 알고리즘을 RS256으로 설정하세요. 이미 다른 Identifier의 API를 만든 경우 backend의 AUTH0_AUDIENCE와 frontend의 VITE_AUTH0_AUDIENCE를 둘 다 그 값으로 바꾸고 재시작하세요. API Identifier는 실제 웹 서버 주소로 접속될 필요가 없는 식별자입니다.

### 실제 서비스 통합 테스트 (선택)

비용이 발생할 수 있는 Gemini 호출 2회를 포함합니다. 가상 보고서만 사용하며 테스트 DB 레코드는 트랜잭션 롤백으로 제거됩니다. 개인정보를 전송하지 않습니다.

```sh
cd backend
LIONPATHWAY_LIVE_TEST=1 ./mvnw -Dtest=LiveServicesTest test
# AI 비용 없이 DB 연결과 소유권/삭제를 검증
LIONPATHWAY_LIVE_TEST=1 ./mvnw -Dtest=LiveDatabaseTest test
```

일반 테스트 실행에서는 이 두 테스트를 건너뜁니다. 두 실서비스 테스트를 동시에 실행하지 마세요.

### DeepSeek / 루미나리

현재 설정 파일에 제공된 Gemini를 사용합니다. DeepSeek/루미나리의 API 주소·모델·키가 아직 제공되지 않아 연결하지 않았습니다. 스캔 페이지의 이미지 인식도 아직 지원하지 않습니다. 해당 제공자의 API 명세를 확인한 뒤 분석 서비스만 분리하고, 대화는 Gemini로 유지할 수 있습니다.


## 새로고침 후 로그인 유지

Auth0 SDK가 토큰을 `localstorage`에 저장하고 refresh token으로 갱신하도록 설정했습니다. 별도 `isLoggedIn` 플래그로 인증을 우회하지 않으며 API는 계속 JWT를 검증합니다. 초기 복원 중에는 `Checking session…`을 표시합니다.

변경 적용 후 한 번 다시 로그인하세요. 이전 메모리 캐시는 새로고침을 견딜 수 없습니다. `http://localhost:5173`과 `http://127.0.0.1:5173`은 저장소가 서로 다르므로 같은 주소를 유지하세요.

토큰 만료 이후에도 지속적으로 갱신하려면 Auth0에서 다음 설정을 확인하세요.

- Applications → 해당 SPA → Settings → Refresh Token Rotation 켜기
- Applications → 해당 SPA → Advanced Settings → Grant Types → Refresh Token 허용
- APIs → 해당 API → Settings → Allow Offline Access 켜기
- 해당 SPA의 Allowed Web Origins에 `http://localhost:5173` 등록

로그아웃은 Auth0 SDK가 저장된 인증 캐시를 지우고 수행합니다. 로컬 저장소 토큰은 페이지 JavaScript에서 접근할 수 있으므로 외부 스크립트와 HTML 주입을 제한해야 합니다. 브라우저 저장소를 차단하거나 삭제한 경우에는 재로그인이 필요합니다.

참고: https://support.auth0.com/center/s/article/Why-is-authentication-lost-after-refreshing-my-SPA

## PDF 분석 대기가 길어질 때

업로드 중에는 인증 확인 / 서버 분석 단계와 경과 시간을 표시합니다. 인증 확인은 최대 20초, AI의 전체 응답 수신은 최대 100초, 브라우저 요청 전체는 최대 140초로 제한됩니다. DB 연결·응답·쿼리·잠금 대기에도 제한을 적용했습니다. 분석 완료 후 보고서 목록 갱신은 결과 표시를 막지 않습니다.

`Stop waiting`은 브라우저 대기를 종료합니다. 이미 서버에 도착한 작업은 완료되어 저장될 수 있으므로 다시 업로드하기 전에 My reports를 확인하세요. 서버 로그는 임의 요청 ID, 단계(pdf/ai/database/complete), 소요 시간, 오류 유형만 기록하고 PDF 내용·파일명·키를 기록하지 않습니다.
