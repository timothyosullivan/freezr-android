# Freezr Collaboration & AI Pairing Prompt

Authoritative shared prompt for working with AI assistants on this repository. Keep concise, actionable, and updated. Treat as a living contract. Deviations require explicit justification in the PR description.

## Core Principles
1. Safety first: No mass destructive changes. Preserve history & intent.
2. Validate in a real runtime (emulator/device) before pushing.
3. Tests are product features: failing / missing test = broken feature.
4. Small, reviewable increments; isolate unrelated changes.
5. Deterministic, reproducible builds (no "works on my machine").
6. Explicitness > cleverness; readability > micro-optimizations (unless perf requirement demands otherwise).

## Scope of AI Changes
Allowed:
- Adding incremental features aligned with `docs/PRODUCT_REQUIREMENTS.md`.
- Refactors with 100% behavior parity + tests proving parity.
- Test coverage improvements.
- Tooling (scripts, hooks) that enforce existing policy.

Disallowed (without explicit human opt‑in in the issue / PR):
- Mass deletion or rewriting large swaths of code (>200 LOC or >20% of a module).
- License changes.
- Introducing network calls, analytics, ads, tracking.
- Changing security, encryption, or permission surfaces.
- Large dependency additions (>1MB AAR) unless mapped to a requirement.

## Pre-Commit / Pre-Push Checklist (HARD GUARD)
All must be TRUE before committing to the remote main branch (enforced manually + optionally via hook):
- [ ] `./gradlew :app:assembleDebug` succeeds.
- [ ] UI / feature exercised on emulator for changed surfaces.
- [ ] `./gradlew testDebugUnitTest` passes (0 failures, 0 ignored unless justified).
- [ ] Coverage report generated & reviewed (JaCoCo). Target: Line ≥80%, Branch ≥70% for touched files; deficits justified.
- [ ] No TODO/FIXME newly introduced without linked issue.
- [ ] No `println` / ad-hoc logging left (use structured logging if added later).
- [ ] No new warnings of high severity (treat new build warnings as errors for changed code).
- [ ] No uncommitted schema migrations (Room) missing tests.
- [ ] No secret / credential / PII added.
- [ ] If `.validated` workflow is in use: file present & `NEEDS_EMULATOR_CHECK` absent.

## Testing Standards
- For each new ViewModel / Repository method: unit tests (happy path + error/edge).
- For each bug fix: regression test that fails prior to fix.
- Data layer: migration tests when schema changes.
- Time-based logic (reminders / expiration): clock abstraction (injectable) + tests with fixed instant.
- Coverage thresholds (initial):
  - New Kotlin files: ≥90% line, ≥80% branch (except trivial data classes).
  - Legacy untouched files: may remain; raise gradually.
- Flaky test protocol: quarantine (tag + exclude) only with issue reference & remediation plan.

## Coverage Report Access
Generate via (example, adapt if plugin differs):
```
./gradlew jacocoTestReport
open app/build/reports/jacoco/jacocoTestReport/html/index.html
```
Assistant MUST NOT mark checklist complete without confirming path exists.

## Code Quality & Style
- Kotlin: idiomatic, but prefer explicit types for public APIs.
- Compose: stateless composables where practical; remember hoisted state.
- DI: Hilt modules minimal & single-responsibility.
- Room queries: tested with realistic data & explain query complexity if > simple select.
- Avoid premature optimization; add benchmark or perf note for any non-obvious optimization.

## Safety Constraints for AI
Assistant MUST:
- Refuse mass destructive edits (respond citing this file).
- Add tests alongside feature code in same change set.
- Highlight any assumption made (explicit ASSUMPTIONS section in PR).
- Provide rollback instructions for risky changes (summary of files touched).
- Never auto-bump dependencies broadly; only targeted upgrades with changelog reference.

Assistant MUST NOT:
- Force push or rewrite main history.
- Delete tests without adding equal or better replacements.
- Silence failing tests (e.g., @Ignore) to achieve green without justification.
- Introduce hidden side-channel communication.

## Command Execution Protocol (User-Run Steps)
When a step requires a local command (build, test, emulator, script):
1. Assistant MUST list the exact command(s) in chat in a fenced code block (shell) labeled "Run:" preceding them in plain language.
2. Assistant then pauses and requests the user to execute ("Please run the above and let me know / click Run").
3. Assistant waits for terminal output before proceeding; no speculative success.
4. After output is provided, assistant summarizes results, notes pass/fail, and decides next step.
5. If multiple commands are independent, group them (max 3) and label each; user drives execution order.
6. Never assume success of un-run commands; never fabricate output.
7. If risk of long runtime (>60s) exists, assistant warns and offers a faster partial verification alternative.
8. For destructive commands (clean, migrations), assistant must restate impact and require explicit confirmation.
9. Assistant must not reprint unchanged commands unless user requests repetition or more than 5 minutes elapsed.
10. If command fails, assistant extracts actionable error lines (≤10) and proposes targeted fixes before suggesting rerun.

This protocol ensures transparency, reproducibility, and preserves user control over execution.

### Automated Terminal Mode (Assistant-Driven)
When the AI assistant has direct, auditable terminal access (as in this session), the above protocol adapts:
1. Assistant runs commands directly (still ≤3 per batch) and immediately shares raw output.
2. User is not required to copy/paste output; review happens in-line.
3. Assistant must still: (a) announce intent before execution, (b) not assume success before output, (c) halt on failures and surface concise diagnostics.
4. Destructive / state-altering commands (git push, database migrations, clean, branch deletion) require explicit user confirmation unless previously scoped in this session.
5. Assistant must show git diff summary (names/status) before staging & committing, and list the exact commit message beforehand.
6. Coverage / test artifacts must be regenerated in-session (no reuse of stale results) prior to commit.
7. If any command produces excessive output (>200 lines), assistant summarizes and may truncate with an explicit note and offer a targeted follow-up command for detail.
8. User can at any time revert to manual mode by stating: "manual mode"; assistant then reverts to fenced Run blocks.

Mode Switching Rules:
- Default is User-Run unless repository owner explicitly opts into Automated Mode in the session (done here).
- Assistant must record the mode switch rationale in the next commit (docs update) to keep an auditable trail.

Security Guardrails (Automated Mode):
- Never run scripts or binaries outside the repo without user confirmation.
- Never modify git history (no rebase/force push) automatically.
- Never export or transmit secrets from local files.

Failure Handling Addendum:
- On failure, assistant gathers top 30 relevant lines around the error, proposes a single focused fix, and re-runs only the necessary subset of commands.


## Incremental Change Template (for PR Descriptions)
```
Summary:
Motivation / Requirement Link:
Approach:
Key Decisions:
Tests Added:
Coverage Delta (files touched):
Assumptions:
Risks & Mitigations:
Manual Validation Steps:
Follow-up Tasks:
```

## Risk Categories
| Risk | Examples | Required Extra Action |
|------|----------|-----------------------|
| Low | Pure test additions, docs | Standard checklist |
| Medium | New ViewModel, new DAO query | Add unit tests & emulator validation notes |
| High | Migration, notification scheduling logic | Add rollback plan + extended test matrix |

## Tooling Wishlist (Future)
- Git pre-commit: run assemble + unit tests + coverage threshold gate.
- Lint task gating (ktlint / detekt) if adopted.
- Snapshot UI tests (baseline golden images) once UI stabilizes.

## Change Log (this prompt)
- 2025-09-01: Initial creation.
- 2025-09-01: Added Command Execution Protocol section.

---
To update: modify sections precisely; append change log entry with date + summary. Keep under ~250 lines.
