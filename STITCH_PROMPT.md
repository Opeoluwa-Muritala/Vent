# Vent — Stitch Design Prompt

Design a polished, high-fidelity, mobile-first product experience for **Vent**, an internal event operations and attendee app. Vent helps attendees check in, locate assigned seats on a real venue map, follow their schedule, answer polls, complete quests, and view rewards. Organizers monitor attendance, manage sessions and seating, and resolve operational issues. The product is independent and must not resemble or imply affiliation with Google or GDG.

## Design direction

Create a distinctive visual system called **Venue Pulse**. Use curved theatre rows, stage lines, aisle gaps, and subtle live-state pulses as the visual grammar. The experience should feel calm, capable, spatial, and contemporary—not like a generic conference dashboard. Use warm neutral surfaces with one event-defined seed color (start with deep violet `#6750A4`) and derive accessible semantic roles. Keep error red stable. Avoid gradients, glassmorphism, excessive cards, decorative blobs, and oversized marketing typography.

Use an 8-point spacing grid with occasional 4-point optical adjustments, 12–16 px corner radii, restrained tonal elevation, and platform-native typography. Meet WCAG AA contrast. Use icons, outlines, labels, and patterns in addition to color for every state. Touch targets must be at least 48 dp on Android and 44 pt on iOS. Support light and dark themes, text scaling, screen readers, reduced motion, and safe areas.

## Generate these connected screens

1. **Attendee event home** — “Vent Demo Day,” date and venue, notification action, a prominent live-session module showing “Check-in is open,” “Opening keynote,” assigned seat B4, closing time, primary “Check in” action, secondary “Find B4,” miniature curved venue preview, upcoming sessions, and quest momentum.
2. **Schedule** — day selector, chronological sessions, live status, hall, duration, assigned-seat summary, and clear session detail affordance.
3. **Seat map** — interactive curved theatre plan with stage, aisles, row labels, zoom controls, “Find B4,” and an anchored seat-detail sheet. Distinguish available, assigned, checked-in, accessible, exit-row, and blocked seats without relying on color. Make the attendee’s seat unmistakable.
4. **Check-in flow** — camera scanner state for Android, manual token fallback for iOS/web, success state showing seat and session, and recoverable error states for invalid, expired, already-used, or wrong-session tokens. Never expose technical error details.
5. **Quests and leaderboard** — progress for “Full House,” “Opinion Leader,” and “Early Bird,” earned badge treatment, points, current rank, and an encouraging empty state.
6. **Profile** — identity summary, points and badges, appearance/accessibility preferences, event switcher, and sign-out.
7. **Organizer overview** — live operational summary with assigned, checked-in, and not-arrived metrics; active session; attention queue; and prominent actions for sessions, seating, and controlled staff check-in.
8. **Organizer seating** — venue editor/monitor mode with seat-state legend, capacity and accessibility validation, selection tools, and attendee assignment detail.

## Responsive behavior

Design first at **390 × 844** for both iOS and Android. Also create a **768 × 1024 tablet** layout and a **1440 × 1024 web** layout. Phones use bottom navigation with Event, Schedule, Quests, and Profile for attendees; organizers use Overview, Sessions, Seating, and Profile. At 840 dp and wider, switch to a navigation rail and list-detail layouts. Do not merely stretch phone cards: use the extra width for venue visualization, details, and operational context.

## Interaction and output requirements

Prototype the primary path: Event home → Check in → Success → Find seat → Seat B4 detail. Also connect Schedule → Session → Seat map and Profile → Organizer role → Organizer overview. Use shared-axis transitions between overview and detail; reduced-motion mode uses crossfades. Show realistic content and complete normal, loading, empty, offline/unavailable, validation-error, and success states.

Produce a reusable component system and tokens suitable for implementation in **Kotlin Multiplatform with Compose Multiplatform and Material 3**. Include component variants for navigation, session rows, live-status indicators, metric tiles, quest progress, seat markers, legends, buttons, chips, sheets, dialogs, and form fields. Name layers and components semantically. The final result should look implementation-ready on Android and iOS and adapt cleanly to tablet and web.
