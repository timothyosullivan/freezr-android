# Freezr Android

Minimal rebuilt version with Room, Hilt, Jetpack Compose list (add/archive/delete/undo, sort, archived toggle).

## Documentation

## Commit Policy
Only commit after emulator validation of the change.


## Release Checklist

1. Update `versionCode` / `versionName` in `app/build.gradle.kts`.
2. Run a clean release build:
	- `./gradlew clean :app:bundleRelease`
3. Test reminder delivery with screen off (exact alarm enabled & disabled cases).
4. Verify database migrations by installing an older debug build, creating data, then upgrading to the new build.
5. Confirm no unintended exported components in `AndroidManifest.xml`.
6. Review notification channel name & importance.
7. Validate camera & notification permissions flows on API 24, 30, 34.
8. Generate Play Store assets (512px icon, feature graphic) if changed.
9. Attach a concise privacy policy (no data leaves device) to Play listing.
10. Tag release in git: `git tag v1.0.0 && git push --tags`.

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

