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
