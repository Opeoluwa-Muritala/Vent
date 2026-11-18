# Vent frontend and backend audit

Scope covers the Compose screens, local storage, sign-in, and Ktor API routes. Findings are based on source inspection. Device behavior and backend integration were not exercised.

## Frontend

| Priority | Finding | Status |
| --- | --- | --- |
| High | Four built-in session identifiers previously limited the schedule. | Organizer creation now saves stable session IDs and details to local storage. Sessions on the same day with matching start labels share a group, even if their halls or end times differ. |
| High | The offline workspace persists sessions, seats, quests, and roster changes on one device. These writes are not synchronized with the server. | Open. The local database and the server need a shared data contract and synchronization before multi-device rollout. |
| High | Legacy seat and check-in fields are shared by the local workspace. Seat assignments are now mapped per session and email, but check-in still needs per-attendee storage. | Open. |
| Medium | Schedule colors changed when navigating between screens. | The app now uses the supplied fixed Material palettes. No Android wallpaper palette is applied. |
| Medium | Organizer home lacked actionable event controls. | The dashboard links to session editing, attendee management, event setup and live operations. The projector is offered on wide layouts. |
| Medium | Seat editing exposed many controls and provided no automatic assignment. | Auto-seat assigns eligible unassigned attendees to distinct available seats and preserves existing assignments. Assigned seats are protected from blocking and deletion. Row labels follow visual order while storage keys remain stable. |
| Medium | Preferences occupied space on the pass screen and the schedule search replaced the title. | Preferences open in a dialog. Search expands below the top row. |
| Medium | Session times are entered as text. | Grouping recognizes the start label in common time ranges. Structured times and overlap validation are open. |
| Low | The keynote and navigation styling need visual verification against the references. | The featured keynote image and check-in label are present. Navigation uses a neutral surface and emphasizes the selected icon. |

## Backend

| Priority | Finding | Evidence |
| --- | --- | --- |
| High | Poll and quest creation still return sample responses without persisted writes. | Event and session creation now persist through `ScheduleRepository.kt`. Event reads use PostgreSQL and filter by membership. The other creation routes still need repositories. |
| High | Assignment commit increments a version but writes no booking and does not bind the commit to a stored preview. | The assignment preview and commit handlers in `VentRoutes.kt`. |
| High | Several routes authorize against a fixed event or validate a fixed session rather than resolving the URL resource and its parent. | Assignment, check-in, poll and layout handlers in `VentRoutes.kt`. Before enabling them for real events, resolve the resource and check membership and role on its actual parent event. |
| High | Check-in responses do not record actual attendance. | Both check-in handlers in `VentRoutes.kt` return a fixed seat and points. Manual check-in validates input length without a booking lookup. |
| Medium | Poll votes are kept in process memory and keyed by user alone. Result aggregation assumes four possible choices. | `DemoState.pollAnswers` and the answer/results routes. |
| Medium | Android's backend URL targets an emulator over HTTP. | `VentApiFactory.android.kt`. Production needs a configurable HTTPS endpoint. |
| Medium | Organizer discovery returns an application-wide role even though roles are stored per event. | `GET /me` in `Main.kt` and `user_roles` in the initial migration. Event-scoped capabilities are needed for multiple organizations. |
| Medium | The fixed local attendee code verifies access to the device workspace, not email ownership. | `AuthFlow.kt`. It should not authorize production data or server actions. The local organizer code is limited to debuggable Android builds. |

## Verification

`git diff --check` passed. The regression suite now includes organizer-only creation, independent concurrent sessions, local-store reload, and stable auto-seating. Compilation, camera behavior, the Room migration from existing data, and the tests remain unverified.
