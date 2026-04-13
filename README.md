# aw-permission

Android runtime permission library built on coroutines + hidden Fragment. No need to override `onRequestPermissionsResult`.

## Features

- Coroutine-based permission requests (`suspend` functions)
- Mutex-serialized requests for concurrency safety
- Distinguishes between "denied" and "permanently denied (Don't ask again)"
- Suspend rationale callback for custom UI
- Activity/Fragment extension functions
- Flow API for observing permission state changes
- Built-in permission group constants (Android 13+ media, notifications, Bluetooth, etc.)
- Safe app settings navigation with `resolveActivity` check
- Activity state validation (isFinishing/isDestroyed)

## Requirements

- **minSdk**: 24+
- **Kotlin**: 2.0+
- **Kotlin Coroutines**: 1.9+
- **AndroidX**: Activity 1.9+, Fragment 1.8+

## Installation

```kotlin
dependencies {
    implementation("com.github.answufeng:aw-permission:1.0.0")
}
```

## Quick Start

### Simple Request

```kotlin
lifecycleScope.launch {
    val result = AwPermission.request(activity, Manifest.permission.CAMERA)
    if (result.isAllGranted) {
        openCamera()
    } else if (result.hasPermanentlyDenied) {
        AwPermission.openAppSettings(context)
    }
}
```

### Multiple Permissions

```kotlin
lifecycleScope.launch {
    val result = AwPermission.request(
        activity,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )
    if (result.isAllGranted) {
        // All granted
    } else {
        // Handle partial grant
        val denied = result.denied
        val permanentlyDenied = result.permanentlyDenied
    }
}
```

### Using Permission Groups

```kotlin
lifecycleScope.launch {
    val result = AwPermission.request(activity, *PermissionGroups.LOCATION)
    val result2 = AwPermission.request(activity, *PermissionGroups.MEDIA_VISUAL)
}
```

### Request with Rationale

```kotlin
lifecycleScope.launch {
    val result = AwPermission.requestWithRationale(
        activity,
        Manifest.permission.CAMERA,
    ) { permissions ->
        // This is a suspend function — show your rationale UI and return true/false
        showRationaleDialog(permissions)
    }
    // result is null if user cancelled the rationale dialog
    if (result != null && result.isAllGranted) {
        openCamera()
    }
}

private suspend fun showRationaleDialog(permissions: List<String>): Boolean {
    return suspendCancellableCoroutine { cont ->
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This feature requires: ${permissions.joinToString()}")
            .setPositiveButton("Allow") { _, _ -> cont.resume(true) }
            .setNegativeButton("Deny") { _, _ -> cont.resume(false) }
            .setOnCancelListener { cont.resume(false) }
            .show()
    }
}
```

### Extension Functions

```kotlin
// Callback-based request
requirePermissions(
    Manifest.permission.CAMERA,
    onGranted = { openCamera() },
    onDenied = { result -> handleDenial(result) }
)

// Check permissions
val hasCamera = hasPermission(Manifest.permission.CAMERA)
val hasAll = hasPermissions(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
```

### Flow API

Observe permission state changes when the Activity resumes (e.g., after user returns from settings):

```kotlin
lifecycleScope.launch {
    observePermissions(Manifest.permission.CAMERA).collect { result ->
        updateUI(result.isAllGranted)
    }
}
```

### Check Permissions

```kotlin
if (AwPermission.isGranted(context, Manifest.permission.CAMERA)) {
    // Already granted
}

if (AwPermission.isAllGranted(context, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) {
    // All granted
}

if (AwPermission.shouldShowRationale(activity, Manifest.permission.CAMERA)) {
    // User denied once, show rationale before next request
}
```

### Open App Settings

```kotlin
val success = AwPermission.openAppSettings(context)
// Returns false if the settings page cannot be opened (rare on custom ROMs)
```

## API Reference

### AwPermission

| Method | Description |
|--------|-------------|
| `isGranted(context, permission)` | Check if a single permission is granted |
| `isAllGranted(context, vararg permissions)` | Check if all permissions are granted |
| `shouldShowRationale(activity, permission)` | Check if rationale should be shown |
| `request(activity, vararg permissions)` | Request permissions (suspend) |
| `request(fragment, vararg permissions)` | Request permissions from Fragment (suspend) |
| `requestWithRationale(activity, vararg permissions, rationale)` | Request with rationale (suspend, returns null if cancelled) |
| `openAppSettings(context)` | Open app settings page, returns false if unavailable |

### PermissionResult

| Property | Description |
|----------|-------------|
| `granted` | List of granted permissions |
| `denied` | List of denied permissions |
| `permanentlyDenied` | List of permanently denied permissions |
| `isAllGranted` | Whether all permissions are granted |
| `hasDenied` | Whether any permission was denied |
| `hasPermanentlyDenied` | Whether any permission was permanently denied |
| `firstDenied` | First denied permission or null |
| `firstPermanentlyDenied` | First permanently denied permission or null |
| `status` | Overall status: `Granted`, `Denied`, or `PermanentlyDenied` |

### PermissionGroups

Pre-defined permission group constants:

| Group | Permissions |
|-------|------------|
| `CAMERA` | `Manifest.permission.CAMERA` |
| `MICROPHONE` | `Manifest.permission.RECORD_AUDIO` |
| `LOCATION` | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` |
| `CONTACTS` | `READ_CONTACTS`, `WRITE_CONTACTS`, `GET_ACCOUNTS` |
| `STORAGE` | `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` |
| `PHONE` | `READ_PHONE_STATE`, `CALL_PHONE`, etc. |
| `SENSORS` | `BODY_SENSORS` |
| `SMS` | `SEND_SMS`, `RECEIVE_SMS`, etc. |
| `CALENDAR` | `READ_CALENDAR`, `WRITE_CALENDAR` |
| `MEDIA_VISUAL` | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` (Android 13+) |
| `MEDIA_AUDIO` | `READ_MEDIA_AUDIO` (Android 13+) |
| `NOTIFICATIONS` | `POST_NOTIFICATIONS` (Android 13+) |
| `NEARBY_DEVICES` | `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE` (Android 12+) |

### Extension Functions

| Function | Description |
|----------|-------------|
| `Context.hasPermission(permission)` | Check single permission |
| `Context.hasPermissions(vararg permissions)` | Check all permissions |
| `FragmentActivity.requestPermissions(vararg, callback)` | Callback-based request |
| `Fragment.requestPermissions(vararg, callback)` | Callback-based request from Fragment |
| `FragmentActivity.requirePermissions(vararg, onGranted, onDenied)` | Request with grant/deny handlers |
| `Fragment.requirePermissions(vararg, onGranted, onDenied)` | Request from Fragment with handlers |
| `FragmentActivity.observePermissions(vararg)` | Flow that emits on each Activity resume |

## How It Works

1. Each permission request creates a headless `PermissionFragment` instance
2. The fragment uses `ActivityResultContracts.RequestMultiplePermissions()` to request permissions
3. The calling coroutine is suspended via `suspendCancellableCoroutine`
4. When the user responds, the coroutine resumes with the result
5. The fragment automatically removes itself from the Activity

### Concurrency Safety

All permission requests are serialized through a single `Mutex`. This ensures that:
- Only one permission request is active at a time
- No continuation is overwritten by concurrent requests
- Requests are processed in FIFO order

### Denied vs Permanently Denied

The library uses `shouldShowRequestPermissionRationale` before and after the request to accurately classify denials:

| Scenario | Before | After | Classification |
|----------|--------|-------|---------------|
| First time denied | `false` | `true` | Denied |
| First time + "Don't ask again" | `false` | `false` | Denied* |
| Subsequent denial | `true` | `true` | Denied |
| "Don't ask again" selected | `true` | `false` | Permanently Denied |

*\* On the very first request, if the user selects "Don't ask again", the library cannot distinguish this from a normal first-time denial. This is an Android platform limitation.*

## FAQ

### Can I use this in a DialogFragment?

Yes. Pass the DialogFragment's parent Activity:

```kotlin
val result = AwPermission.require(requireActivity(), Manifest.permission.CAMERA)
```

### Can I request permissions from a Service?

No. Android requires an Activity or Fragment to display the permission dialog. You should request permissions from an Activity and store the result.

### What about custom ROM compatibility?

The library includes safety checks:
- `openAppSettings()` uses `resolveActivity()` to verify the settings Intent can be handled
- Activity state checks prevent crashes on finishing/destroyed Activities
- `IllegalStateException` from the permission launcher is caught and propagated as cancellation

### How does the Flow API work?

`observePermissions()` creates a Flow that emits the current permission state every time the Activity resumes. This is useful for detecting when a user returns from the system settings page after granting/denying permissions.

## License

Apache License 2.0, see [LICENSE](LICENSE).
