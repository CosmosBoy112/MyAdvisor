# OnlyPlans

A HackPSU MVP that turns a Penn State What-If Report into a credit dashboard and a requirement-by-requirement conversation.

Live: https://www.onlyplans.courses (frontend on Vercel, backend on Render)

**Vue 3 + Spring Boot 3 / Java 21 + Gemini + Luminal + TigerData PostgreSQL + Auth0**

> Team progress is logged in [`PROGRESS.md`](PROGRESS.md). Read the latest entries before starting, and add one at the top when you push. Contribution rules are in [`AGENTS.md`](AGENTS.md).

## Quick start: try the sample without an account

Node.js 20.19+ or 22.11+ is recommended.

```sh
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173` and click **Explore a sample**. The sample screens and sample answers run entirely in the browser and are never presented as a real PDF analysis. The landing page is just a search box and a PDF attach button.

## Features

- Responsive Vue UI, natural-language major/minor input, PDF picker and drag-and-drop
- Completed / in-progress / remaining credit bar chart; missing values are shown as unknown
- Requirement filters, details, and the PDF source page and quotation for each requirement
- Per-requirement questions and Gemini chat, with the last 12 messages sent as context
- Chat answers render headings, lists, bold/italic and inline code (parsed as data, never inserted as HTML)
- Collapsible chat sidebar listing each saved report's conversation by its latest question; tucks away while the report panel is open
- Chat settings menu (⋯) for the web search toggle and Clear chat
- After sign-in, reopen reports from My reports, restore recent conversations, and delete with confirmation
- Auth0 sign-in, JWT issuer/audience validation, report ownership checks
- PDFBox text extraction, limited to 10 MB / 40 pages / 160,000 extracted characters
- Gemini output constrained by JSON Schema, numeric validation, and quotations checked against the actual page text
- Analysis results and conversations stored in TigerData; report deletion API
- Analysis JSON download

## Connecting real services

### 1. Auth0

Create a Single Page Application and an API in Auth0.

- Allowed Callback URLs: `http://localhost:5173`
- Allowed Logout URLs: `http://localhost:5173`
- Allowed Web Origins: `http://localhost:5173`
- API Identifier, e.g. `https://lionpathway-api`
- API token signing algorithm: RS256

Copy `frontend/.env.example` to `frontend/.env.local` and fill in the values. The SPA **does not need a client secret**. The frontend and backend audiences must match. Don't mix `localhost` and `127.0.0.1`. If you change the address, update the Auth0 allowed URLs and `FRONTEND_ORIGIN` as well.

### 2. TigerData

Get the connection details for a Tiger Cloud PostgreSQL service.

```text
TIGER_DATABASE_URL=jdbc:postgresql://YOUR-HOST:YOUR-PORT/tsdb?sslmode=require
TIGER_DATABASE_USER=tsdbadmin
TIGER_DATABASE_PASSWORD=...
```

Pass connection details only as backend environment variables. TLS is required; in production, `sslmode=verify-full` with a trusted CA is recommended. The tables are plain PostgreSQL tables with no time-series extension. `backend/src/main/resources/schema.sql` runs with `CREATE IF NOT EXISTS` when the live profile starts, so the account needs permission to create those tables. Prefer a database/user dedicated to this project over an existing app database.

### 3. Gemini

Create an API key in Google AI Studio and set `GEMINI_MODEL` to a model available in that project. The example value is `gemini-3.5-flash-lite`; local verification with this model succeeded for multi-page requirement extraction and chat. Gemini 3 models are called with `thinkingLevel: low`. Check model access and availability in your own account.

### 3a. Luminal (optional)

When `LUMINAL_API_KEY` is set, chat questions that need multi-step reasoning, such as semester plans, What-If scenarios, and comparisons, go to Luminal. Simple lookups and PDF requirement extraction stay on Gemini, and a failed Luminal call falls back to Gemini automatically. Routing uses the keywords and the length threshold (over 400 characters) in `ModelRouter.java`. Luminal is assumed to expose an OpenAI-compatible `/chat/completions` endpoint; set `LUMINAL_BASE_URL` and `LUMINAL_MODEL` to match your account. The default model is `deepseek-ai/DeepSeek-V4.1-Flash`.

Keep keys on the backend only. PDF text is sent to Google Gemini, and results and chats are stored in TigerData. Original PDFs are never persisted to the database or disk. Remove names, student IDs, and other personal information before uploading; automatic anonymization is not guaranteed.

### 4. Running Spring Boot

JDK 21+ is required. The Maven Wrapper is included, so you don't need to install Maven; the first run needs network access to download it. On Windows, use `mvnw.cmd`. Spring Boot reads `backend/.env` automatically on startup, from either the project root or the `backend` folder. System environment variables and JVM properties take precedence over the file, and `.env` contents are never logged.

```sh
cd backend
# With no environment variables: demo profile, only the public health API
./mvnw spring-boot:run
```

For a live run, copy `backend/.env.example` to `backend/.env`, fill in real values, and include `SPRING_PROFILES_ACTIVE=live` in the file.

```sh
SPRING_PROFILES_ACTIVE=live ./mvnw spring-boot:run
```

Required: `AUTH0_ISSUER`, `AUTH0_AUDIENCE`, `TIGER_DATABASE_URL`, `TIGER_DATABASE_USER`, `TIGER_DATABASE_PASSWORD`, `GEMINI_API_KEY`, `GEMINI_MODEL`.

Include the trailing `/` in the Auth0 issuer, e.g. `https://YOUR-TENANT.us.auth0.com/`. Without it, startup fails with an issuer mismatch.

By default the backend listens on `127.0.0.1:8080`, and Vite proxies `/api` to it. Uploads in the live service require sign-in, and a failed service connection never silently falls back to the sample.

With Docker:

```sh
# Run from the repository root so the official catalog is included in the image.
docker build -f backend/Dockerfile -t lionpathway-api .
# .env is a local file you create yourself; it is not committed to Git.
docker run --rm --env-file backend/.env -p 127.0.0.1:8080:8080 lionpathway-api
```

### Easiest local run

Open two terminals at the project root:

```sh
./start-backend.sh
```

```sh
./start-frontend.sh
```

Then open **http://localhost:5173**. If you registered `localhost` in Auth0, don't switch to `127.0.0.1`.

### Fixing Auth0 `Service not found`

`Service not found: https://lionpathway-api` means the sign-in SPA exists but no API is registered for that audience. In the Auth0 Dashboard, go to Applications → APIs → Create API and set the Identifier to `https://lionpathway-api` with RS256 signing. If you already created an API with a different Identifier, set both the backend `AUTH0_AUDIENCE` and the frontend `VITE_AUTH0_AUDIENCE` to that value and restart. The API Identifier is just an identifier; it doesn't need to be a reachable web address.

## Deployment

The live site runs the frontend on Vercel and the backend on Render.

- **Vercel**: Root Directory `frontend`, Vue.js preset. Set `VITE_AUTH0_DOMAIN`, `VITE_AUTH0_CLIENT_ID`, `VITE_AUTH0_AUDIENCE`, and `VITE_API_BASE_URL` (the Render URL, no trailing `/`). `VITE_` values are baked in at build time, so redeploy after changing them.
- **Render**: Docker runtime, Dockerfile path `backend/Dockerfile`, build context the repository root. Set the `backend/.env` values plus `FRONTEND_ORIGIN` (the exact frontend address, no trailing `/`). Render provides `PORT`. On the free 512 MB instance, `JAVA_TOOL_OPTIONS=-Xmx350m -XX:+UseSerialGC` keeps the JVM within memory. Health check path: `/api/health`.
- **CORS** allows exactly one origin, `FRONTEND_ORIGIN`. Requests from any other address, including the `*.vercel.app` URL once a custom domain is set, are rejected.
- **Auth0**: add the frontend address to Allowed Callback URLs, Allowed Logout URLs, and Allowed Web Origins.

The free Render instance sleeps when idle and takes about a minute to wake up, so open `/api/health` before a demo.

## API

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/health` | Public status check |
| POST | `/api/reports` | Auth required. multipart `file`, `intent` |
| GET | `/api/reports` | Your 50 most recent reports, each with `lastQuestion` (first 120 characters of the latest user message, or null) |
| GET | `/api/reports/{id}/messages` | Recent conversation, after an ownership check |
| GET | `/api/reports/{id}` | One of your reports |
| POST | `/api/reports/{id}/chat` | Ask about your report. JSON `{"question":"..."}` |
| DELETE | `/api/reports/{id}/messages` | Clear your report's conversation; the report stays |
| DELETE | `/api/reports/{id}` | Delete your report and its conversation |

Send the Auth0 API access token to protected APIs as `Authorization: Bearer ...`; don't use the ID token. Other users' reports return 404. Personal report APIs are blocked in the demo profile.

## Verification

```sh
cd frontend
npm test
npm run build

cd ../backend
./mvnw test
./mvnw package
```

Tests cover null and excess credit handling in credit math, filters, file size validation, real PDF text extraction, mismatched citations, blocking unauthenticated requests, and report ownership. Live Gemini/TigerData integration tests also passed with the provided configuration. See [`VERIFICATION.md`](VERIFICATION.md) for the detailed scope.

### Live service integration tests (optional)

These include two Gemini calls that may incur cost. They use only a synthetic report, and test database records are removed by transaction rollback. No personal data is sent.

```sh
cd backend
LIONPATHWAY_LIVE_TEST=1 ./mvnw -Dtest=LiveServicesTest test
# Checks the DB connection and ownership/deletion without AI cost
LIONPATHWAY_LIVE_TEST=1 ./mvnw -Dtest=LiveDatabaseTest test
```

Regular test runs skip both. Don't run the two live tests at the same time.

## Project structure

```text
frontend/src/App.vue            Input screen, dashboard, chatbot
frontend/src/style.css          Responsive design
frontend/src/model.js           Credit, filter, and file validation helpers
frontend/src/sample.json        Clearly labeled synthetic sample
backend/src/main/java/dev/lionpathway/
  SecurityConfig.java          Auth0 JWT, CORS, access policy
  PdfReader.java               Per-page PDFBox extraction
  GeminiService.java           Structured analysis and chat API
  LuminalService.java          Luminal chat API for complex questions
  ModelRouter.java             Question routing, Gemini/Luminal choice, fallback
  AnalysisValidator.java       Output validation and citation checks
  ReportRepository.java        Owner-scoped DB queries
  ReportController.java        Upload, read, chat, delete
backend/src/main/resources/
  analysis-schema.json         Gemini output schema
  schema.sql                   TigerData tables
```

## Staying signed in after refresh

The Auth0 SDK stores tokens in `localstorage` and renews them with a refresh token. There is no separate `isLoggedIn` flag that bypasses authentication, and the API keeps validating the JWT. `Checking session…` is shown while the session is restored.

Sign in once more after this change; the old in-memory cache doesn't survive a refresh. `http://localhost:5173` and `http://127.0.0.1:5173` use separate storage, so stick to one address.

To keep renewing after token expiry, check these Auth0 settings:

- Applications → your SPA → Settings → turn on Refresh Token Rotation
- Applications → your SPA → Advanced Settings → Grant Types → allow Refresh Token
- APIs → your API → Settings → turn on Allow Offline Access
- Register `http://localhost:5173` in your SPA's Allowed Web Origins

Sign-out clears the stored auth cache through the Auth0 SDK. Tokens in local storage are readable by page JavaScript, so external scripts and HTML injection must be restricted. If browser storage is blocked or cleared, you'll need to sign in again.

Reference: https://support.auth0.com/center/s/article/Why-is-authentication-lost-after-refreshing-my-SPA

## When PDF analysis takes a while

During upload, the UI shows the auth check / server analysis stage and elapsed time. The auth check is capped at 20 seconds, receiving the full AI response at 100 seconds, and the whole browser request at 140 seconds. Database connections, responses, queries, and lock waits are also time-limited. Refreshing the report list after analysis never blocks the result from showing.

`Stop waiting` ends the wait in the browser. Work that already reached the server may still finish and be saved, so check My reports before uploading again. Server logs record only a random request ID, the stage (pdf/ai/database/complete), elapsed time, and error type, never PDF content, file names, or keys.

## Official CMPSC catalog comparison

New PDF analyses are compared against the University Park · Computer Science B.S. · 2026–27 catalog bundled with the server. The report metadata and PDF text must confirm that major, campus, and catalog year. The comparison is stored in the existing TigerData `reports.analysis` JSONB and is used when a report is reopened or when Gemini/Luminal answers questions. No extra environment variables or database migrations are needed.

The Official catalog comparison section and each item's Bulletin comparison show the source, the current required credits, and any conflicts or items to confirm. Previously saved reports need the PDF re-uploaded to check scope. PDF credits and completion status are never overwritten, and no automatic graduation decision is made. See the [CMPSC catalog change log](docs/CHANGES-cmpsc-catalog.md) for details.

## Languages and text sizes

The UI defaults to English, and the landing page stays English. Once you submit a goal or a chat message, report labels and chatbot answers follow the language you wrote in. Supported: English, Korean, Chinese (Simplified), Japanese, Spanish, French, German, Portuguese, Russian, and Hindi. Course names, report quotations, and previously saved conversations stay in their original language. See [`docs/LANGUAGES.md`](docs/LANGUAGES.md) for how detection works and its limits.

Shared CSS text sizes are 16px body, 14px secondary, and 22px section headings. The large landing heading and credit figures keep their own sizes.

## MVP scope and next steps

This is currently a tool that **interprets and explains** a report. It doesn't include official graduation audits, a prerequisite graph, automatic semester planning, live course availability, or a rules engine for every major. Prompts are restricted so the model doesn't invent minors or elective lists that aren't in the report. LLM interpretation can be wrong, and a matching quotation doesn't guarantee the interpretation is correct.

Extraction quality still needs to be validated with real LionPATH PDFs. OCR for scanned PDFs isn't supported. Requirements are never double-counted, in-progress credits are never treated as completed, and the report's overall credit fields are used rather than per-category sums.

The site is deployed for the hackathon demo. Before a wider public launch, add per-user AI rate limits and a data retention policy.

## References

- [Auth0 Vue SDK](https://auth0.github.io/auth0-vue/)
- [Gemini structured output](https://ai.google.dev/gemini-api/docs/structured-output)
- [TigerData documentation](https://docs.tigerdata.com/)
