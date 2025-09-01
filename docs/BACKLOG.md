# Freezr MVP Engineering Backlog

Derived from `PRODUCT_REQUIREMENTS.md` (2025-09-01). Each item should become an issue with clear Definition of Done (DoD) & tests. Order roughly reflects dependency & foundation first.

Legend: P = Priority (1 highest). Size (S) = T-shirt (XS/S/M/L/XL). Status placeholder.

| ID | P | S | Title | Summary / DoD | Dependencies |
|----|---|----|-------|---------------|--------------|
| BL-01 | 1 | S | Core UUID Model Migration | Replace Long IDs with UUID (Container.id) & DAO changes + migration test. | Current data model |
| BL-02 | 1 | S | Time Provider Abstraction | Inject clock for deterministic tests (Instant provider). | BL-01 (optional) |
| BL-03 | 1 | M | Container Repository Expansion | Add fields: frozenDate, reminderDays, quantity, notes (encrypted stub). Update Room schema & tests. | BL-01 |
| BL-04 | 1 | M | Status USED Handling | Add USED status logic, hide by default, filter toggle + tests. | BL-03 |
| BL-05 | 1 | M | Reminder Scheduling Engine (WorkManager) | Schedule jobs per container; reschedule on boot; test scheduling logic (unit). | BL-02, BL-03 |
| BL-06 | 1 | M | Notification Actions | PendingIntents: Mark Used, Snooze, Open; integration test (Robolectric). | BL-05 |
| BL-07 | 1 | S | Settings: Default Reminder Days | Persist & integrate into add flow; unit tests. | BL-03 |
| BL-08 | 1 | M | Label Payload & Generator | Generate UUID batch, QR bitmaps, short code; unit tests uniqueness & payload format. | BL-01 |
| BL-09 | 1 | M | Label Sheet Layout Engine | Rows/cols/margins model; PDF composition (stub) + tests. | BL-08 |
| BL-10 | 1 | M | Print / PDF Export Integration | Android Print Framework adapter + instrumentation plan; Robolectric stub test. | BL-09 |
| BL-11 | 1 | L | CameraX + ML Kit Scan Screen | Live preview, QR detection, FPS metric logging; detection unit/integration tests (where feasible). | BL-08 |
| BL-12 | 1 | S | Manual Short-Code Entry Flow | UI + validation logic tests. | BL-11 |
| BL-13 | 2 | S | Inventory Filters & Sort Extensions | Expiring ≤7d, expired, oldest/newest, A→Z. Unit tests. | BL-03, BL-04 |
| BL-14 | 2 | S | Snooze Logic | Add snooze days & scheduling update test. | BL-05 |
| BL-15 | 2 | S | Backup Export (Encrypted JSON) | Serialize settings + containers (fields) + test restore roundtrip. | BL-03 |
| BL-16 | 2 | S | Backup Import / Merge | Merge by UUID, no duplicates. Tests (conflict, new, partial). | BL-15 |
| BL-17 | 2 | S | Biometric Lock Skeleton | Gate entry; Keystore key gen stub + test fallback. | BL-02 |
| BL-18 | 2 | S | Theme Settings (System/Light/Dark) | Persist + apply dynamic Material 3 theme; UI test. |  |
| BL-19 | 2 | S | Accessibility Pass | Content descriptions, TalkBack labels, contrast audit docs. | Features baseline |
| BL-20 | 2 | S | Coverage Threshold Raise | Adjust jacoco thresholds (≥85/75) after core features. | Core features done |
| BL-21 | 3 | M | Performance Test Harness | Large dataset (1000 items) scroll perf benchmark note. | BL-03 |
| BL-22 | 3 | S | Data Privacy Policy Stub | Markdown file + link placeholder. |  |
| BL-23 | 3 | M | In-App Review Trigger Logic | Conditions + test gating. | Core flows |
| BL-24 | 3 | L | Encryption Implementation | Encrypt notes using Jetpack Security + test decrypt/migration. | BL-03 |
| BL-25 | 3 | M | Migration Test Matrix Script | Automate DB migration tests across versions. | BL-01..BL-04 |

## Notes
- Create issues per backlog item; reference this ID.
- Keep PRs atomic (one backlog item unless trivial coupling).
- Update coverage thresholds after BL-20.
- Add instrumentation tests (androidTest) for camera & print flows when infrastructure ready.

Change Log:
- 2025-09-01: Initial backlog extraction.
