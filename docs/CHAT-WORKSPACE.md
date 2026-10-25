# Chat-first account workspace

After Auth0 sign-in, the app loads the user's saved reports through the existing authenticated `/api/reports` endpoint.

- New account: after Auth0 registration/sign-in, the Profile page requests the first LionPATH PDF. The public main page has no Add new/file uploader. Accounts with no saved report are treated as unfinished setup, so returning before completion resumes it. This is app onboarding after the Auth0 callback, not a file upload inside Auth0's hosted signup form. Select one PDF, consent and use **Analyze and save**; the first-upload view has no text input. Setup ends only after the server confirms the saved analysis. Existing accounts with a report skip this step.
- Returning account: restore the newest saved report and its conversation into the main chat workspace. No repeated upload is required. If the report-list request fails, show an error rather than treating it as a successful empty account.
- Profile: shows Auth0 name/email and the user's saved report list. Open a report to restore its chat and display its details; add another report or use confirmed deletion when more than one report remains. The last report is disabled in the UI and rejected with HTTP 409 by the API. ReportRepository.delete locks the account row in a transaction before counting owned reports, serializing same-account deletions. A different account cannot delete the report. After deleting the active report, chat switches to a remaining report. Report switching is disabled during an active reply/upload.
- Main workspace: chat is always the primary view. Initial nonempty questions are submitted after successful analysis. Empty questions do not call the model. Enter submits and selects the response language; Shift+Enter inserts a newline.
- View report: opens an optional right-hand panel with existing credit graphs, requirements, course evidence and minor planner. Hide report/Escape closes it without clearing the conversation. On small screens it becomes an overlay sheet with a persistent close control. Requirement question buttons focus the main composer; small screens close the report sheet first.

Saved reports are the existing parsed analysis and conversation records, not retained copies of the original PDF binary. Export analysis remains JSON. No DB schema, ownership/authentication rules, or model routing changed. The signed-out/upload landing view stays English; the report workspace follows submitted input language.

`account-flow.test.js` mounts the actual Vue components with synthetic Auth0 and API boundaries. It checks empty/failed account loading, existing conversation restoration, optional report panel, profile navigation, adding a report, and a single automatic initial-question request. This does not substitute for an authenticated production browser test. Vercel still needs its public API origin and the backend CORS origin configured as described in `DEPLOYMENT.md`; the latest upstream CORS bean fix is included.


## Profile-page onboarding
Profile now uses `#/profile`, with identity, saved reports, and an inline upload form.
Accounts with no saved report must complete PDF analysis and save before entering chat.
Home, hash navigation, and Escape cannot dismiss required setup. A successful save
opens the main chat; returning accounts can navigate between Profile and chat.
No profile/upload modal is used. Last-report deletion protection remains in both UI and API.

Selecting My reports keeps the user on Profile and replaces the list with the full report inline. A dedicated back button restores My reports. During report loading, list and detail content stay hidden until loading completes. Chat View report continues to use the sidebar. Requirement question actions return to the chat composer.


## Clear conversation
Clear chat lives in the chat settings menu (⋯) and is disabled for empty or active conversations.
A confirmation dialog explains that messages will be permanently removed while the report remains.
`DELETE /api/reports/{id}/messages` verifies ownership and deletes only that report's messages.
The UI clears messages after success and preserves them on error. Sample conversations reset locally.


## Chat sidebar and compact header
- Signed-in chat shows a left sidebar of saved reports, newest first. Each item is titled by that report's latest user question (`lastQuestion` from `GET /api/reports`, first 120 characters) or "New conversation", with the major and date underneath. Selecting an item opens that report and its conversation; switching is disabled while a reply, upload or reset is in progress. "New report" opens the Profile upload form.
- The toggle at the left of the chat header hides or shows the sidebar and remembers the choice in `localStorage` (`onlyplans-chat-list`). Opening View report tucks the sidebar away without changing that preference; the toggle can bring it back next to the report, and closing the report restores the saved state.
- The sidebar is not shown in sample mode, on Profile, during first-report setup, or below 1100px width, where Profile remains the way to switch reports.
- The chat header is a single row: the PDF filename, View report, and a settings menu (⋯) holding the web search toggle (signed-in reports only) and Clear chat. The menu closes on outside click, Escape, or report change.
- `lastQuestion` comes from a read-only correlated subquery on `chat_messages`; no schema or index was added. The table has no `report_id` index, so the list query may slow down if conversations grow large.

## Chat answer formatting
`chatBlocks()` in `chat-content.js` turns model answers into headings, ordered/unordered lists, paragraphs, rules, and inline bold, italic, code and links. Vue renders this data as text nodes, so HTML in an answer is shown literally and never executed. Plain asterisks and underscores in ordinary text (for example `2 * 3`, `snake_case`) are left as-is. Existing `[W1]` web-source links and HTTPS Markdown links still work.

## Advisor agent output in the chat
- While an answer is being written, the thinking row shows the agent's current step and the steps already finished ("Checking prerequisites…", "Searching the web…", "Switching to the backup model…") with the elapsed time. The frontend sends a `requestId` with the chat request and polls `GET /api/reports/{id}/chat/progress/{requestId}` once a second; a failed poll never affects the answer.
- When the agent opens a screen (`view`: course details, course options, what is left, suggested semester plan, Math minor check), it appears as a card directly under that answer (`AdvisorView.vue`). Closing it leaves an "Open screen" chip. Screens are saved with the answer, so a reopened conversation shows them as chips.
- Memories the agent saved or forgot appear under the answer with Undo. The chat settings menu (⋯) has Memories, a list of the account's memories with delete buttons (`GET`/`DELETE /api/memories`).
- The upload consent now names Luminal and Gemini for chat answers. Screen data is built by the server from the report and catalog (`docs/advisor-tools.md` section 9.5), never from model text.
