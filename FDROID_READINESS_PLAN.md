# NetZone F-Droid Readiness Plan

Goal: keep every release reproducible from source and keep policy-sensitive behavior documented for F-Droid review.

## Current readiness

- Release build works with `./gradlew :app:assembleRelease`.
- Unit tests run with `./gradlew :app:testDebugUnitTest`.
- Lint blockers fixed for API 24-25 foreground service startup path.
- Foreground service configuration aligned to `specialUse` for Android 14+ VPN lifecycle.

## PR checklist (use on every release PR)

1. Run verification commands locally:
   - `./gradlew :app:testDebugUnitTest`
   - `./gradlew :app:lintRelease`
   - `./gradlew :app:assembleRelease`
2. Confirm no proprietary SDKs were added (ads, analytics, crash reporting).
3. Confirm manifest permissions are still required and justified:
   - `PACKAGE_USAGE_STATS`
   - `QUERY_ALL_PACKAGES`
   - `SCHEDULE_EXACT_ALARM`
   - `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE`
4. Verify app id and launcher component remain consistent:
   - `com.netzone.app`
   - `.MainActivity`
5. Update release notes and screenshots when UI/UX changes.
6. Tag release only after checks pass.

## F-Droid submission checklist

1. Ensure `versionCode` and `versionName` are updated in `app/build.gradle.kts`.
2. Create and push a release tag (for example `v1.1.0`).
3. Prepare F-Droid metadata values:
   - Application id: `com.netzone.app`
   - Source repository URL
   - License: MIT
   - Build command: Gradle assembleRelease
4. Provide reviewer notes for policy-sensitive permissions and why each is required for firewall behavior.
5. Open/update the F-Droid inclusion request and attach the tag/version details.
6. Monitor F-Droid build logs and patch quickly if any reproducibility issue appears.
