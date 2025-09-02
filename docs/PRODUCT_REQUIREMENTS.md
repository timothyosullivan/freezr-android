# Freezr – Product Requirements Specification (PRS)

> Source captured on 2025-09-01. This file is the durable reference for the MVP scope and related non-functional requirements. Update with change log entries when requirements evolve.

## 1. Vision & Scope
Goal: Make batch-cooking painless by tagging frozen containers with unique QR labels, then scanning to see contents and “use-by” reminders—fast, offline, private, and delightful.

**MVP (device-only) includes:**
- Generate & print sheets of unique QR labels.
- Scan labels to create/update containers.
- Store: item name, date frozen, quantity/portion, optional notes.
- Default + per-item reminder windows (e.g., 30/60/90 days).
- Local notifications when an item hits its reminder window.
- Inventory: search, filters, sort, quick actions.
- Local backup/restore to a file (no cloud).
- Polished, accessible, high-performance UX.
- Play Store readiness (target, permissions, assets, listing).

**Explicitly out (MVP):** Family sharing, multi-device sync, cloud backup.

## 2. Users & Core Journeys
**Solo home cook / meal prepper** – Print labels → cook → scan → set item → freeze; later scan to view age, mark used, or snooze.
## 1. Vision & Scope
Goal: Make batch-cooking painless by tagging frozen containers with pre‑printed unique QR labels, then scanning to claim / view contents and “use-by” reminders—fast, offline, private, and delightful. Physical-first: users cannot create an item without scanning a label.
## 3. Functional Requirements
**MVP (device-only) includes:**
- Batch generate & print sheets of UNUSED placeholder QR labels (DB rows created on generation; no manual add without scan).
- Scan UNUSED label → claim form (description, date frozen (default today), quantity, reminder override) → becomes ACTIVE.
- Scan ACTIVE label → detail sheet (age, fields, actions: Mark Used, Archive, Edit, Extend Reminder).
- Scan USED / ARCHIVED label → reuse flow (archives prior row & creates new ACTIVE preserving the physical QR's uuid) or (future) history view.
- Store: name, date frozen, quantity/portion, notes, status (UNUSED→ACTIVE→USED/ARCHIVED), reminder days/override.
- Default + per-item reminder offset; store reminderAt; local notifications with actions.
- Inventory: filters (expiring soon, expired, used, archived, unused), sorts (age, name).
- Local backup/restore (file, no cloud).
- Polished accessible high-performance UX.
- Play Store readiness (target, permissions, assets, listing).
- Live scan (CameraX + ML Kit QR) offline.
### 3.1 Label Generation & Printing
- Batch generate placeholder rows (status UNUSED) each with UUIDv4; stored immediately so scanning finds them.
- QR payload: `freezr://v1/c/<uuid>`.
- Human-readable short code (first/last 6 chars) beneath QR for manual fallback.
- Initial layout: A4 portrait 4×10 grid (40/pg) multi‑page; future presets for custom rows/cols.
- Share / Print via generated multi-page PDF.
- Acceptance: Request 120 → +120 UNUSED rows, 3 pages, unique UUIDs, >=95% scan success at ~150–200px QR size.
- List: search (name/notes), filters (expiring ≤7d, expired, used, active), sorts (oldest, newest, A→Z).
### 3.2 Scanning & Claim / Detail / Reuse
- Live scan (CameraX + ML Kit) offline.
- Branching:
	* UNUSED → Claim form (requires description) → set frozenDate + ACTIVE.
	* ACTIVE → Detail panel (edit, Mark Used, Archive, Extend Reminder).
	* USED / ARCHIVED → Reuse dialog (archive old w/ randomized uuid & create new ACTIVE preserving original uuid) or view history (post-MVP).
	* Unknown/malformed → brief snackbar error.
- Manual short-code entry fallback (optional post-MVP).
- Acceptance: Cold start ≤2.0s; detect-to-dialog ≤400ms; claim updates existing UNUSED row (no duplicate uuid row); reuse preserves physical uuid.
- Default + per-item reminder days.
### 3.3 Inventory & Details
- Default list: ACTIVE only.
- Filters: Expiring Soon (0 < reminderAt - now ≤ 7d), Expired (now > reminderAt), USED, ARCHIVED, UNUSED.
- Sorts: Oldest/Newest (frozenDate), Name A→Z/Z→A.
- Detail: age (days), time to reminder, edit fields, actions (Mark Used, Archive, Reuse if historical, Snooze 7d optional).
- Acceptance: Mark Used removes from default list; Expiring Soon & Expired filters accurate; Reuse inserts new ACTIVE + archives prior with randomized uuid.
### 3.5 Settings
### 3.4 Reminders & Notifications
- reminderAt = frozenDate + selected days (or default) persisted (or derived lazily) and scheduled.
- Reschedule on edit; cancel on Mark Used / Archive / Reuse.
- Notification: "<name> frozen <N> days" + actions (Mark Used, Snooze 7d, Open) (Snooze optional if time).
- Android 13+ permission rationale, non-blocking decline.
- Acceptance: Creating ACTIVE schedules; editing date/reminder days updates schedule; Mark Used cancels; notification after reboot.
## 4. Non-Functional Requirements
## 5. Data Model (MVP)
**Container**: id (Long PK), uuid (UUIDv4 unique), name (blank until claimed), frozenDate (nullable until claimed), quantity (Int, default 1), reminderDays (nullable → use Settings.defaultReminderDays), reminderAt (optional cached), notes (nullable), status (UNUSED|ACTIVE|USED|ARCHIVED|DELETED internal), createdAt, updatedAt, dateUsed (nullable).
**Settings**: defaultReminderDays, future: category shelf-life defaults, label presets, biometricEnabled.
Rules:
- Batch generation inserts UNUSED placeholder rows.
- Claim transitions UNUSED→ACTIVE, sets name & frozenDate (if null).
- Mark Used sets status USED + dateUsed, cancels reminder.
- Archive sets ARCHIVED, cancels reminder.
- Reuse: archive prior (randomize old uuid) then insert new ACTIVE with original uuid.
- DELETED = soft delete (hidden).
Implemented:
-- Data model: Room `Container` uses auto-increment Long `id` + `uuid` (unique). Status values: ACTIVE, ARCHIVED, DELETED, USED (UNUSED not yet in code at snapshot time, planned in revised spec).
- No analytics/ads/network (MVP). Minimal permissions: CAMERA, POST_NOTIFICATIONS (13+).
Next Alignment Steps (updated):
1. Add UNUSED status & placeholder batch generation + PDF wiring.
2. Scan branching logic (UNUSED claim / ACTIVE detail / historical reuse).
3. Reminder enrichment & cancellation paths.
4. Settings for default reminder days & derived category defaults (future).
5. Expiring Soon & Expired filters using reminderAt.
Change Log:
- 2025-09-01 (later): Added Section 13 with current implementation snapshot (schema v3 with Long id + uuid, basic list/add/archive/delete, single QR label dialog, PDF label generator utility stub, reuse() backend clone, reminder scheduler scaffold). Original PRS retained as target scope.
- 2025-09-02: Overhauled requirements to introduce physical-first placeholder labels (UNUSED status), scan branching, refined data model & reminder logic.
**Settings**: defaultReminderDays, label layout presets, biometricEnabled.
Rules: Unique id enforced; scanning existing UUID updates (no duplicate). Soft delete via status=USED.

## 6. Architecture & Tech Stack
Kotlin, Jetpack Compose, Material 3; CameraX + ML Kit; ZXing for QR generation; Room (+ optional field encryption); WorkManager + NotificationCompat; Android Print Framework + custom PrintDocumentAdapter.

## 7. Play Store Compliance (MVP)
- Target API 35; AAB + Play App Signing.
- POST_NOTIFICATIONS permission handling; avoid exact alarms.
- Store listing assets + Data safety + Privacy Policy + IARC rating.
- Pre-launch reports; In-App Review API for ratings; ASO (Food & Drink category, keywords, localization) basics.

## 8. High-Level UX Flows
Print labels → choose preset → count → preview → Print/Save PDF.
Scan & add → camera → detect → new? add form; existing? details.
Inventory → tabs (All / Expiring Soon / Used), search/filter/sort → detail.
Reminder → notification → detail actions.

## 9. Validation & Acceptance (Condensed)
First run stability (API 26–35); permission graceful degradation; PDF opens & QR scans; known vs unknown scan paths; reminders fire on correct day & reschedule post-reboot; backup/import merge; accessibility (TalkBack, contrast, dynamic type).

## 10. Risks & Mitigations
Printer variability → rely on Print Framework/PDF.
Notification timing → WorkManager + expectation copy.
No telemetry → use Play vitals + reviews; defer analytics.

## 11. Post-MVP Roadmap
Family sharing, sync/cloud backup, nutritional tags, templates, OCR/photo thumbs, Wear OS glance, future enhancements.

## 12. Quick Launch Checklist
- Target API 35, AAB, Play App Signing.
- Content: Data safety (on-device) + Privacy Policy.
- IARC questionnaire.
- Listing: title/descriptions/screenshots/feature graphic.
- Internal testing + Pre-launch report review.
- In-app review trigger after success moments.

---
Change Log:
- 2025-09-01 (later): Added Section 13 with current implementation snapshot (schema v3 with Long id + uuid, basic list/add/archive/delete, single QR label dialog, PDF label generator utility stub, reuse() backend clone, reminder scheduler scaffold). Original PRS retained as target scope.

## 13. Current Implementation Snapshot (2025-09-01)
This section is descriptive of the codebase as of branch `feat/schema-v2-container-fields`; it is NOT altering MVP goals, only recording divergence / progress.

Implemented:
- Data model: Room `Container` uses auto-increment Long `id` (PK) plus separate `uuid` (unique) instead of UUID-as-PK. Status values currently: ACTIVE, ARCHIVED, DELETED, USED (ARCHIVED & DELETED are transitional; PRS MVP mentions only ACTIVE|USED).
- Added fields: `frozenDate`, `reminderDays` (per-item override), `quantity`, `notes`, timestamps, `status`, `uuid` (migrations 1→2→3 complete, backfilled uuid values).
- List UI: Sorting (NAME_ASC/DESC, CREATED_ASC/DESC) & archived filter toggle; quantity, reminder info, short uuid displayed.
- Add flow: Simple dialog (name only) → creates ACTIVE container with generated uuid; schedules reminder via scheduler abstraction (logic placeholder).
- Archive / Activate / Soft delete (status=DELETED) with snackbar undo for delete.
- Label: Per-item dialog shows QR (uuid payload currently raw uuid string, not URI scheme yet) and full uuid text.
- QR generation: Pure matrix utility (ZXing-free internal) producing bitmap in UI layer; JVM tests for matrix.
- PDF label generation: `LabelPdfGenerator` utility creates single A4 (595x842 @72dpi) sheet (4 cols × 10 rows) of QR codes (currently not multi-page and not wired to UI button yet).
- Reuse backend: `reuse(id, newName?)` clones an existing container (resets timestamps, status ACTIVE, updates frozenDate) via repository; no UI trigger yet.
- Camera permission added preparing for scanning feature.
- Coverage: Custom JaCoCo baseline enforcement (current line ≈55%).

Partial / Pending relative to PRS:
- Printing: Top bar "Print" button placeholder only; needs intent integration & multi-page/preset logic.
- Scanning: No CameraX/ML Kit implementation yet; no URI scheme `freezr://v1/c/<uuid>` encoded (currently raw uuid in QR).
- Reminder notifications: Scheduling abstraction exists; no real notification delivery or POST_NOTIFICATIONS handling.
- Status lifecycle: ARCHIVED & DELETED present but MVP desired USED path not wired; mark-used action absent.
- Settings: Default reminder days present in data model; broader settings (label presets, theme, backup) not implemented.
- Security/Privacy & Backup: Not started.
- Accessibility polish, performance targets, printing presets, history view, snooze actions: Not implemented.

Known Divergences from PRS (to reconcile):
- PK type (Long + uuid field) vs PRS UUID primary key: evaluate whether to migrate to UUID PK or retain composite model.
- Additional statuses (ARCHIVED, DELETED) extend lifecycle; decision needed whether to consolidate into ACTIVE/USED for MVP or document multi-state model.
- QR payload should shift to URI scheme before scan feature lands for forward compatibility.

Next Alignment Steps:
1. Implement Print flow (multi-page + Android Print Framework + share PDF) and adopt URI payload format.
2. Implement Scan flow (CameraX analyzer decoding QR → uuid) and reuse/create decision UI; optionally mark previous instance USED upon reuse (policy decision pending).
3. Flesh out reminder worker with notifications + permission handling (Android 13+).
4. Decide on PK migration or keep current model; update PRS if sticking with Long id.
5. Introduce Settings persistence for default reminder days & future label presets.

This snapshot section can be removed once implementation converges with PRS or moved to a separate CHANGELOG.
