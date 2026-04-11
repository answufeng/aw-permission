# aw-permission

Android runtime permission library. Coroutine-based with hidden Fragment approach, no need to override onRequestPermissionsResult.

## Installation

Add the dependency in your module-level `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.answufeng:aw-permission:1.0.0")
}
```

Make sure you have the JitPack repository in your root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

## Features

- Coroutine-based permission request (suspend function)
- Mutex-serialized concurrent safety
- Distinguishes denied vs permanently denied (Don't ask again)
- Rationale support with custom UI
- Extension functions for Activity/Fragment
- Built-in app settings launcher

## Usage

```kotlin
// Simple request
lifecycleScope.launch {
    val result = BrickPermission.request(activity, Manifest.permission.CAMERA)
    if (result.isAllGranted) { openCamera() }
    else if (result.hasPermanentlyDenied) { BrickPermission.openAppSettings(context) }
}

// With rationale
val result = BrickPermission.requestWithRationale(
    activity,
    arrayOf(Manifest.permission.CAMERA),
    rationale = { permissions, proceed, cancel ->
        AlertDialog.Builder(this)
            .setMessage("Camera permission is needed")
            .setPositiveButton("Allow") { _, _ -> proceed() }
            .setNegativeButton("Deny") { _, _ -> cancel() }
            .show()
    }
)

// Extension functions
requirePermissions(Manifest.permission.CAMERA,
    onGranted = { openCamera() },
    onDenied = { result -> handleDenial(result) }
)
```

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.
