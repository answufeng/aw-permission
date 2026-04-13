# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2025-04-13

### Added
- Coroutine-based permission requests via `AwPermission.request()`
- Mutex-serialized concurrent request safety
- Accurate denied vs permanently denied classification using before/after rationale state
- `AwPermission.requestWithRationale()` with suspend rationale callback
- `AwPermission.shouldShowRationale()` for checking rationale state
- `AwPermission.openAppSettings()` with `resolveActivity` safety check
- Activity state validation (isFinishing/isDestroyed)
- `PermissionResult` with `status`, `hasDenied`, `firstDenied`, `firstPermanentlyDenied`
- `PermissionResult.Status` sealed interface (Granted/Denied/PermanentlyDenied)
- `PermissionGroups` constants for common Android permission groups
- Extension functions: `hasPermission`, `hasPermissions`, `requestPermissions`, `requirePermissions`
- Flow API: `observePermissions()` for observing permission state on Activity resume
- `PermissionFragment` with `AtomicReference` for thread-safe continuation management
- `AtomicLong`-based tag generation for unique Fragment tags
- `try-catch` for `permissionLauncher.launch()` to handle IllegalStateException
- `isAdded` check before Fragment removal
- Unit tests (Robolectric) for `AwPermission`, `PermissionResult`, `PermissionGroups`
- Instrumented tests for `AwPermission` on device
- Demo app showcasing all API features
- ProGuard consumer rules
- Maven Publish configuration

### Changed
- Renamed `BrickPermission` to `AwPermission`
- Rationale callback changed from `(permissions, proceed, cancel) -> Unit` to `suspend (permissions) -> Boolean`
- `openAppSettings()` now returns `Boolean` indicating success
- Fragment extension functions use `lifecycleScope` instead of `viewLifecycleOwner.lifecycleScope`
- `PermissionFragment` returns raw `Map<String, Boolean>` instead of classified `PermissionResult`
- Permission classification logic moved from `PermissionFragment` to `AwPermission`
- Cleaned up `libs.versions.toml` to only include relevant dependencies
- All public API uses explicit `public` visibility modifier

### Removed
- Unrelated dependencies from `libs.versions.toml` (Hilt, Retrofit, Room, Coil, MMKV, etc.)
- `ksp` and `hilt` plugins from root build.gradle.kts
