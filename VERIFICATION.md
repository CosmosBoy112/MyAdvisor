# Verification results

## Latest implementation check

- Vue production build: passed
- Frontend Node tests: 5 passed
- Spring Boot Maven package: passed
- Backend regular JUnit tests: 12 passed
- Integration test with real Gemini structured analysis + TigerData save/read + Gemini answer: passed
- Real TigerData CRUD / owner isolation / conversation history / cascade delete tests: passed
- Real-connection tests used only synthetic data, and DB test records were rolled back
- `/api/health` after automatic `.env` loading: `live: true`
- Signed-out `/api/reports`: HTTP 401
- Auth0 OIDC discovery and frontend/backend domain and audience match: confirmed
- Auth0 sign-in in the browser: blocked by `Service not found: https://lionpathway-api`. The API Identifier must be registered in the Auth0 Dashboard
- Real user PDF upload after sign-in: full browser flow not verified until the Auth0 issue above is resolved
- Accuracy on real student LionPATH PDFs: not verified, no report provided
- DeepSeek/Luminal: connected to `deepseek-ai/DeepSeek-V4.1-Flash` at Luminal `flash.luminal.cloud/v1`. A direct call returned 200; the full flow in the app UI is not verified (see "Luminal routing" below)

## Model configuration change

The previous `gemini-2.5-flash` answered model info lookups but returned HTTP 404 for generation, saying it is not available to new users. Local `GEMINI_MODEL` was changed to `gemini-3.8-flash` as the provider response suggested, and structured analysis and question answering were verified. Actual latency and billing depend on the model and account.

## Existing screen checks

Checked the landing page, sample dashboard, completed filter, Department List details, source evidence modal, and per-requirement sample chat. Answers on the sample screens are prewritten local responses and are distinguished from real Gemini answers.

## Staying signed in after refresh

- Auth0 SDK cache changed from memory to localstorage, with refresh tokens and SSO fallback
- `Checking session` is shown while the SDK initializes; the report list is requested only after authentication completes
- 7 frontend tests and the production build passed
- Regression tests: synthetic auth entries survive recreating the SDK cache instance, removed entries are not restored, and the signed-out UI is not shown during session restore
- The full real-account flow (sign in → refresh → renew after token expiry) was not automatically verified in this change. The user reported fixing the earlier Auth0 API registration issue
- Sign in again after applying the change. Continuous token renewal needs Auth0 Refresh Token Rotation / Allow Offline Access

## Fix for PDF upload hanging forever

- Hard timeout that also covers waiting for the auth token
- A canceled file is not uploaded even if a stalled token arrives late
- Time limit also applies when the body stalls after HTTP headers
- Upload stage and real elapsed time are shown, plus a stop-waiting button
- Showing the analysis result is decoupled from refreshing the saved report list
- Server AI request Future cancellation / 504 error, and time limits on DB communication, queries, and locks
- 11 frontend tests, 14 regular backend tests, and both builds passed
- The user's real PDF was not re-sent; whether that file succeeds needs separate confirmation

## Cause of 100-second PDF analysis failures and model switch

- Two real uploads: PDF extraction succeeded in 337 ms / 55 ms, then the AI stage hit the 100-second timeout
- Previous gemini-3.8-flash: even a small synthetic report took about 96.8 seconds, and a separate request returned HTTP 503 high demand
- gemini-3.5-flash also returned HTTP 503 on long analyses. Local config and examples switched to gemini-3.5-flash-lite
- Gemini 3 uses low thinking, an instruction to avoid duplicate requirements, and logs only response status, time, and token counts
- Error messages distinguish truncated output (MAX_TOKENS), overload (503), and timeouts, without assuming PDF size is the cause
- Real API check: 8-page / 24-requirement synthetic report in 7.6 seconds; total requirement count, credit totals, and source quotations all matched
- Short report analysis about 1.3 seconds, chatbot about 1 second; TigerData save/read, ownership isolation, and conversation storage passed. Test data rolled back
- The user's real PDF still needs a re-upload to confirm success and extraction accuracy. The times above were measured on synthetic input and are not guarantees
- Model reasoning settings reference: https://ai.google.dev/gemini-api/docs/generate-content/thinking

## Luminal routing added

- Planning, What-If, comparison, and sequencing questions, plus questions over 400 characters, go to Luminal; other chat and PDF analysis go to Gemini
- 6 `ModelRouterTest` tests: question classification, provider selection, Gemini fallback on Luminal failure, Luminal skipped when no key is set
- 20 regular backend JUnit tests and the frontend production build passed
- Direct Luminal `/chat/completions` call: HTTP 200, OpenAI-compatible response format confirmed
- Luminal answer in the browser after sign-in: not verified

## Luminal live connection check

- Sent a synthetic report and a semester-plan comparison question using the local `backend/.env` settings
- Configured model deepseek-ai/DeepSeek-V4.1-Flash: HTTP 200, real answer received, about 3.5 seconds
- Confirmed ModelRouter chose provider=luminal, and that the Gemini fallback was not called
- No real student PDFs or existing user reports were used. Nothing saved to the DB
- Rerun from the backend directory: `LIONPATHWAY_LUMINAL_TEST=1 ./mvnw -Dtest=LiveLuminalTest test`
- This test runs only when explicitly enabled and uses API quota

## 2026-10-24 Official CMPSC catalog

- `python -m unittest discover -s ingest -p 'test_cmpsc_catalog.py' -v`: 12/12 passed. Covers campus/year, ETM, compound alternatives, cross-listing, sources, unconfirmed lists, and isolation of the historical Handbook.
- 75/75 official course codes matched, 0 mismatches. 14 matched courses with prerequisite review need manual confirmation.
- All catalog JSON SHA-256 hashes identical before and after cache-based regeneration. Existing non-CMPSC courses unchanged from HEAD.
- `git diff --check` passed. No frontend/backend runtime code changed, so builds were not rerun. DB loading, real PDF matching, and graduation decisions were not implemented or verified.

## 2026-10-24 CMPSC app integration

- Maven regular tests: 26 of 30 passed, 4 Live* tests skipped by environment variable. The 6 new CatalogServiceTest tests check scope rejection, source confirmation, credit preservation/conflicts, unconfirmed lists, the 27 elective codes, upload save arguments, and chat context.
- Separate `LIONPATHWAY_LIVE_TEST=1 ... -Dtest=LiveDatabaseTest test`: matching results saved to and read from real TigerData JSONB; ownership, conversations, and deletion confirmed; transaction rolled back.
- 11 frontend tests passed and the Vite production build succeeded.
- Real signed-in browser / PDF / AI generation and the Docker container were not part of this check.
- Restarted the changed backend with the live profile and confirmed the `/api/health` response.

## 2026-10-24 LionPATH used / IP correction

- 28 regular backend tests passed; 4 external-service tests and 1 local PDF selection test skipped. A separate DegreeCreditReconcilerTest run, including PDFBox extraction of a user-provided PDF, passed 3 tests. The original file is not committed.
- With synthetic data, confirmed future-term IP included in used, separation of needed and IP, and marking totals as unknown on mismatches, duplicates, or a missing section end.
- 15 frontend tests and the build passed. Displayed IP includes courses planned as of the report date. Existing saved analyses are not corrected automatically; the change applies to new uploads.

## 2026-10-24 Language / typography

- 18 frontend tests and the production build passed. Verified English default, mixed Korean/English, preserving language on empty/numeric input, and language switching in the real requirement chart component.
- 29 backend tests passed, 5 conditional tests skipped. Mock-based check that the selected language reaches the analysis service and the saved result.
- In the browser, confirmed Korean UI on the landing page, sample dashboard, and chat after Korean input, and a return to English after English input. Visually checked the dark-mode chat screen. Real Gemini/Luminal generated answers and full mobile screens were not part of this check.

## 2026-10-24 CMPSC catalog DB load

- After a successful `load_cmpsc_db.py --dry-run`, the real load was committed. Rerunning the same dry-run from another connection returned `inserted_programs=[]`, confirming no duplicates.
- CMPSC_BS: 78 requirement nodes / 87 rules; CMPSC_ETM: 7/7; 5 policy_rules added. All 75 course references exist.
- Whole DB: programs/program_versions 4/4, requirement_nodes 175, requirement_rules 567, policy_rules 10. Shared courses 357, course_versions 337, prereq_nodes 880 unchanged.
- Source requirement tree matches the DB round trip, and policy JSON matches. Column/constraint/index schema fingerprints and shared/DATSC row counts and content hashes are identical before and after. No DDL/UPDATE/DELETE/DROP used; no access to app user, report, or conversation data.
- `python -m unittest discover -s ingest -p 'test_*.py' -v`: 15 passed. The app's JSON `CatalogService` was not switched to DB queries; that integration was out of scope for this work.
