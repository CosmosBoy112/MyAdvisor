# Chat web research

The chat now gives useful conditional guidance instead of treating missing catalog data as a reason to refuse the whole question. Official requirements and student experiences remain distinct.

## Flow

- `WebResearchService` requests Gemini Google Search grounding for course/professor recommendations, workload, explicit search requests, planning, and uncertain catalog scope. Narrow credit-total questions stay report-based. The chat checkbox disables web research for that request.
- Official PSU sources are prioritized for requirements; Reddit and Rate My Professors are additionally requested for student experience questions. A site may have no accessible relevant result. Never treat enabling search as proof that a specific website was accessed.
- Only metadata-backed source segments are passed to the answer model. IDs such as `[W1]` link to the provider-returned source URLs. Redirect URLs are labeled generically rather than assumed official from their titles. Links do not certify academic applicability.
- Gemini handles simple answers; Luminal handles complex planning. Simple Gemini failures also fall back to configured Luminal. Both use the same instruction to distinguish evidence, tentative advice, campus/year differences, and adviser confirmation.
- Search is limited to 30 seconds, Gemini chat to 40, Luminal to 60. The frontend chat deadline is 160 seconds including auth and fallback. PDF analysis retains its existing 100-second provider timeout.

## Privacy and rendering

Search receives the current question (with obvious email/long numeric identifiers/URLs removed) and at most eight public course codes from recent messages. It does not receive the PDF, report JSON or raw conversation history. This is not comprehensive anonymization: the UI tells students to omit personal information and lets them disable search.

Search content is untrusted evidence. Responses render as Vue text with safe HTTPS links; model HTML is never executed. Google Search Suggestions HTML is shown in a sandboxed iframe with no scripts, same-origin access or forms and a restrictive CSP. Suggestion links can open in a new tab. Source links, source type, retrieval timestamp and an adviser caution accompany web-assisted answers. Retrieval time is not publication time.

## Persistence (no schema migration)

New researched assistant messages store a `LIONPATHWAY_CHAT_V1` JSON envelope in the existing `chat_messages.content` TEXT column. `ChatMessageCodec` restores `content` and `webResearch` for history. Legacy plain text remains supported; user messages are never decoded as envelopes. The model receives only plain history role/content, not prior search HTML. Older app versions will display the envelope literally, so keep frontend/backend revisions together.

## Configuration and failures

Uses existing `GEMINI_API_KEY`. Optional `GEMINI_SEARCH_MODEL` defaults to `GEMINI_MODEL`; choose a model available to the project with Google Search support. Search charges/quota are independent of Luminal credits. No new key is required by the implementation.

A 429 returns `quota_exceeded`; other provider failures/timeouts return `unavailable`; no supported evidence returns `no_results`. These states appear in the UI and do not claim online verification. Report/catalog answers can still proceed. Do not fabricate search results to hide a provider failure.

Reference: [Google Search grounding API and source metadata](https://ai.google.dev/gemini-api/docs/generate-content/google-search).

## Verification · 2026-10-24

- Frontend: 23 tests and production build pass. Covers source IDs, unsafe URLs/HTML escaping, sandboxed suggestions and Korean quota warnings.
- Backend suite: 49 total, 43 passed and 6 opt-in checks skipped. Separately enabled `LiveLuminalTest` passed with synthetic data (HTTP 200); combined reports: 44 passed, 5 skipped.
- Verified search model is listed by the current API key. Actual grounded request returned HTTP 429 RESOURCE_EXHAUSTED; successful live grounded findings and citations could not be verified with this quota. Metadata handling is tested with fixtures, not represented as a successful live search.
- Local backend restarted; `/api/health` reports live/ok. Browser sample chat opens. Authenticated browser search flow was not tested.
