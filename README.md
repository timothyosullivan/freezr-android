# Freezr Android

Minimal rebuilt version with Room, Hilt, Jetpack Compose list (add/archive/delete/undo, sort, archived toggle).

## Commit Policy
Only commit after emulator validation of the change.

## Validation Workflow
1. Implement change.
2. Build: `./gradlew :app:assembleDebug`.
3. Install & launch on emulator; exercise affected features.
4. (Optional) Run unit tests: `./gradlew testDebugUnitTest`.
5. Commit & push.

## Hard Guard (Optional)
Enable a local pre-commit hook that blocks commits unless:
- Last build succeeded.
- Emulator validation marker file `.validated` exists.
- No `NEEDS_EMULATOR_CHECK` marker remains.

### Setup
Run:
```
chmod +x .githooks/pre-commit
git config core.hooksPath .githooks
chmod +x tools/validate.sh
```
Then after validating on emulator: `touch .validated` before committing. Remove `.validated` (`rm .validated`) when beginning new work.

### Markers
- Create `NEEDS_EMULATOR_CHECK` when starting risky work. Delete it after validation.
- `.validated` is ignored by Git and signals last validation success.

## Validation Script
Use `tools/validate.sh` to automate build + install + launch.

Run without marking:
```
tools/validate.sh
```

Run and mark validated (writes `.validated` and removes `NEEDS_EMULATOR_CHECK`):
```
tools/validate.sh --mark
```

