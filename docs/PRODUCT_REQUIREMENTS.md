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
**Power prepper** – Bulk create labels, batch add scans with shared metadata, inventory to find expiring/oldest.

## 3. Functional Requirements
### 3.1 Label Generation & Printing
- Unique codes (UUIDv4). Payload: `freezr://v1/c/<uuid>`.
- Human-readable short code (last 6 of UUID) beneath QR.
- Layout presets + custom rows/cols, margins, QR size.
- Android Print Framework + Save as PDF via system dialog.
- Export sheets as PDF (SAF picker).
**Acceptance:** 120 labels -> 120 unique UUIDs; print dialog opens & saves valid PDF; ≥95% scan success at 128–192px.

### 3.2 Scanning & Item Creation
- Live scan (CameraX + ML Kit QR) offline.
- Unknown UUID → Create flow (prefill today + default reminder).
- Known UUID → Detail screen.
- Manual short-code entry fallback.
**Acceptance:** Cold start to scan ≤2.0s (Pixel 4a class); continuous ≥24 FPS; QR detect median <400ms; offline.

### 3.3 Inventory & Details
- List: search (name/notes), filters (expiring ≤7d, expired, used, active), sorts (oldest, newest, A→Z).
- Detail: editable fields (name, date frozen, quantity, reminder days, notes), history (created, edited, used).
- Quick actions: Mark used, Snooze 7d, Edit.
**Acceptance:** Expiring filter logic (today - frozenDate) >= (reminderDays - 7); Mark used hides unless filter includes used.

### 3.4 Reminders & Notifications
- Default + per-item reminder days.
- Schedule via WorkManager; notifications with actions (Mark used, Snooze 7d, Open).
- Android 13+ POST_NOTIFICATIONS permission with rationale.
**Acceptance:** Denied → banner (no re-prompt spam); survive reboot; due notifications arrive.

### 3.5 Settings
- Default reminder days, label presets, theme (system/light/dark), biometric lock (optional).
- Backup/restore encrypted JSON (export/import via SAF). Merge by UUID.
**Acceptance:** Backup restores identical records; biometric uses Keystore; cancel returns to safe screen.

## 4. Non-Functional Requirements
### 4.1 Performance & Footprint
- AAB w/ Play App Signing.
- Download ≤15 MB; cold start ≤2.0s (Pixel 4a). Smooth list ≥55 FPS @ 1k items.
### 4.2 Security & Privacy
- Room persistence; optional encryption for sensitive notes (Jetpack Security).
- No analytics/ads/network (MVP). Minimal permissions: CAMERA, POST_NOTIFICATIONS (13+).
- Privacy Policy: local-only processing; Data safety reflects on-device access only.
### 4.3 Accessibility & UX
- Material 3 + Compose; large touch targets; TalkBack labels; dynamic type; high contrast.
- Core App Quality checklist compliance.

## 5. Data Model (MVP)
**Container**: id (UUID PK), name, frozenDate (UTC), reminderDays (nullable → default), quantity, notes (encrypted optional), status (ACTIVE|USED), createdAt, updatedAt.
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
- 2025-09-01: Initial capture from user-provided PRS.
