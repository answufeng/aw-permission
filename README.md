# aw-permission

[![](https://jitpack.io/v/answufeng/aw-permission.svg)](https://jitpack.io/#answufeng/aw-permission)

基于协程 + 隐藏 Fragment 构建的 Android 运行时权限库。无需重写 `onRequestPermissionsResult`。

## 特性

- 基于协程的权限请求（`suspend` 函数）
- 互斥锁序列化请求，保证并发安全
- 区分"拒绝"和"永久拒绝（不再询问）"
- 挂起式理由回调，支持自定义 UI
- Activity/Fragment 扩展函数
- Flow API，用于观察权限状态变化
- 内置权限组常量（Android 13+ 媒体、通知、蓝牙等）
- 安全的应用设置页导航，带 `resolveActivity` 检查
- Activity 状态校验（isFinishing/isDestroyed）

## 环境要求

- **minSdk**: 24+
- **Kotlin**: 2.0+
- **Kotlin 协程**: 1.9+
- **AndroidX**: Activity 1.9+, Fragment 1.8+

## 引入

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.answufeng:aw-permission:1.0.0")
}
```

## 快速开始

### 简单请求

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

### 多个权限

```kotlin
lifecycleScope.launch {
    val result = AwPermission.request(
        activity,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )
    if (result.isAllGranted) {
        // 全部已授权
    } else {
        // 处理部分授权
        val denied = result.denied
        val permanentlyDenied = result.permanentlyDenied
    }
}
```

### 使用权限组

```kotlin
lifecycleScope.launch {
    val result = AwPermission.request(activity, *PermissionGroups.LOCATION)
    val result2 = AwPermission.request(activity, *PermissionGroups.MEDIA_VISUAL)
}
```

### 带理由的请求

```kotlin
lifecycleScope.launch {
    val result = AwPermission.requestWithRationale(
        activity,
        Manifest.permission.CAMERA,
    ) { permissions ->
        // 这是一个挂起函数 — 显示你的理由 UI 并返回 true/false
        showRationaleDialog(permissions)
    }
    // 如果用户取消了理由对话框，result 为 null
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

### 扩展函数

```kotlin
// 基于回调的请求
requirePermissions(
    Manifest.permission.CAMERA,
    onGranted = { openCamera() },
    onDenied = { result -> handleDenial(result) }
)

// 检查权限
val hasCamera = hasPermission(Manifest.permission.CAMERA)
val hasAll = hasPermissions(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
```

### Flow API

在 Activity 恢复时观察权限状态变化（例如用户从设置页返回后）：

```kotlin
lifecycleScope.launch {
    observePermissions(Manifest.permission.CAMERA).collect { result ->
        updateUI(result.isAllGranted)
    }
}
```

### 检查权限

```kotlin
if (AwPermission.isGranted(context, Manifest.permission.CAMERA)) {
    // 已授权
}

if (AwPermission.isAllGranted(context, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) {
    // 全部已授权
}

if (AwPermission.shouldShowRationale(activity, Manifest.permission.CAMERA)) {
    // 用户曾拒绝过，下次请求前应展示理由
}
```

### 打开应用设置

```kotlin
val success = AwPermission.openAppSettings(context)
// 如果设置页无法打开则返回 false（在自定义 ROM 上偶发）
```

## API 参考

### AwPermission

| 方法 | 说明 |
|------|------|
| `isGranted(context, permission)` | 检查单个权限是否已授权 |
| `isAllGranted(context, vararg permissions)` | 检查所有权限是否已授权 |
| `shouldShowRationale(activity, permission)` | 检查是否应展示权限理由 |
| `request(activity, vararg permissions)` | 请求权限（挂起函数） |
| `request(fragment, vararg permissions)` | 从 Fragment 请求权限（挂起函数） |
| `requestWithRationale(activity, vararg permissions, rationale)` | 带理由的请求（挂起函数，取消时返回 null） |
| `openAppSettings(context)` | 打开应用设置页，不可用时返回 false |

### PermissionResult

| 属性 | 说明 |
|------|------|
| `granted` | 已授权的权限列表 |
| `denied` | 被拒绝的权限列表 |
| `permanentlyDenied` | 被永久拒绝的权限列表 |
| `isAllGranted` | 是否所有权限均已授权 |
| `hasDenied` | 是否存在被拒绝的权限 |
| `hasPermanentlyDenied` | 是否存在被永久拒绝的权限 |
| `firstDenied` | 第一个被拒绝的权限，若没有则为 null |
| `firstPermanentlyDenied` | 第一个被永久拒绝的权限，若没有则为 null |
| `status` | 整体状态：`Granted`、`Denied` 或 `PermanentlyDenied` |

### PermissionGroups

预定义的权限组常量：

| 权限组 | 包含权限 |
|--------|----------|
| `CAMERA` | `Manifest.permission.CAMERA` |
| `MICROPHONE` | `Manifest.permission.RECORD_AUDIO` |
| `LOCATION` | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` |
| `CONTACTS` | `READ_CONTACTS`, `WRITE_CONTACTS`, `GET_ACCOUNTS` |
| `STORAGE` | `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` |
| `PHONE` | `READ_PHONE_STATE`, `CALL_PHONE` 等 |
| `SENSORS` | `BODY_SENSORS` |
| `SMS` | `SEND_SMS`, `RECEIVE_SMS` 等 |
| `CALENDAR` | `READ_CALENDAR`, `WRITE_CALENDAR` |
| `MEDIA_VISUAL` | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`（Android 13+） |
| `MEDIA_AUDIO` | `READ_MEDIA_AUDIO`（Android 13+） |
| `NOTIFICATIONS` | `POST_NOTIFICATIONS`（Android 13+） |
| `NEARBY_DEVICES` | `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`（Android 12+） |

### 扩展函数

| 函数 | 说明 |
|------|------|
| `Context.hasPermission(permission)` | 检查单个权限 |
| `Context.hasPermissions(vararg permissions)` | 检查所有权限 |
| `FragmentActivity.requestPermissions(vararg, callback)` | 基于回调的请求 |
| `Fragment.requestPermissions(vararg, callback)` | 从 Fragment 基于回调的请求 |
| `FragmentActivity.requirePermissions(vararg, onGranted, onDenied)` | 带授权/拒绝处理器的请求 |
| `Fragment.requirePermissions(vararg, onGranted, onDenied)` | 从 Fragment 带处理器的请求 |
| `FragmentActivity.observePermissions(vararg)` | 每次 Activity 恢复时发射的 Flow |

## 工作原理

1. 每次权限请求会创建一个无界面的 `PermissionFragment` 实例
2. 该 Fragment 使用 `ActivityResultContracts.RequestMultiplePermissions()` 请求权限
3. 调用方的协程通过 `suspendCancellableCoroutine` 挂起
4. 当用户响应后，协程以结果恢复执行
5. Fragment 自动从 Activity 中移除自身

### 并发安全

所有权限请求通过单个 `Mutex` 进行序列化。这确保了：
- 同一时刻只有一个权限请求处于活跃状态
- 不会因并发请求导致续体被覆盖
- 请求按 FIFO（先进先出）顺序处理

### 拒绝 vs 永久拒绝

本库在请求前后使用 `shouldShowRequestPermissionRationale` 来准确分类拒绝类型：

| 场景 | 请求前 | 请求后 | 分类 |
|------|--------|--------|------|
| 首次拒绝 | `false` | `true` | 拒绝 |
| 首次 + 勾选"不再询问" | `false` | `false` | 拒绝* |
| 后续拒绝 | `true` | `true` | 拒绝 |
| 已勾选"不再询问" | `true` | `false` | 永久拒绝 |

*\* 在首次请求时，如果用户选择了"不再询问"，本库无法将其与普通首次拒绝区分。这是 Android 平台的限制。*

## 常见问题

### 可以在 DialogFragment 中使用吗？

可以。传入 DialogFragment 的父 Activity：

```kotlin
val result = AwPermission.require(requireActivity(), Manifest.permission.CAMERA)
```

### 可以从 Service 中请求权限吗？

不可以。Android 要求通过 Activity 或 Fragment 来展示权限对话框。你应该在 Activity 中请求权限并存储结果。

### 自定义 ROM 兼容性如何？

本库包含以下安全检查：
- `openAppSettings()` 使用 `resolveActivity()` 验证设置页 Intent 是否可被处理
- Activity 状态检查防止在正在结束/已销毁的 Activity 上崩溃
- 权限启动器抛出的 `IllegalStateException` 会被捕获并作为取消传播

### Flow API 是如何工作的？

`observePermissions()` 创建一个 Flow，在每次 Activity 恢复时发射当前权限状态。这对于检测用户从系统设置页返回后授权/拒绝权限的情况非常有用。

## 许可证

Apache License 2.0，详见 [LICENSE](LICENSE)。
