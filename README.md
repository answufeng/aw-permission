# aw-permission

[![](https://jitpack.io/v/answufeng/aw-permission.svg)](https://jitpack.io/#answufeng/aw-permission)

基于协程 + 隐藏 Fragment 构建的 Android 运行时权限库。无需重写 `onRequestPermissionsResult`。

## 特性

- 基于协程的权限请求（`suspend` 函数）
- 互斥锁序列化请求，保证并发安全（rationale 展示也在锁内）
- 准确区分"拒绝"和"永久拒绝（不再询问）"，国产 ROM 增强检测（AppOps + noteOp 双重检测 + 请求历史记录）
- 挂起式理由回调，支持自定义 UI，可配置触发策略
- Activity/Fragment 扩展函数 + DSL 风格 API（支持 strategy 配置）
- Flow API，用于观察权限状态变化（支持区分永久拒绝）
- 内置权限组常量（Android 14+ 部分媒体、后台传感器、WiFi 等）
- 版本自适应权限组（`storage()`、`location()` 自动选择正确权限）
- 安全的应用设置页导航，国产 ROM 多重回退（华为/小米/OPPO/vivo/魅族）
- 特殊权限引导（自启动/悬浮窗/通知栏/后台弹出/省电策略等）+ 状态检查
- 权限说明辅助工具（中文标签、说明、风险等级）
- 挂起版 `openAppSettingsAndWait`，返回后自动检查权限（含超时保护）
- 批量权限状态查询
- 可配置的日志系统
- 重复权限自动去重
- Activity 状态校验（isFinishing/isDestroyed）
- 超时保护机制（权限请求 60 秒，设置页等待 120 秒），防止协程永久挂起
- 隐藏 Fragment 配置变更安全，无泄漏

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
    implementation("com.github.answufeng:aw-permission:1.1.0")
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
        // 查询单个权限状态
        if (result.isGranted(Manifest.permission.CAMERA)) {
            openCamera()
        }
        if (result.isPermanentlyDenied(Manifest.permission.RECORD_AUDIO)) {
            AwPermission.openAppSettings(context)
        }
    }
}
```

### 使用权限组

```kotlin
lifecycleScope.launch {
    // 静态权限组
    val result = AwPermission.request(activity, *PermissionGroups.LOCATION)
    val result2 = AwPermission.request(activity, *PermissionGroups.MEDIA_VISUAL)

    // 版本自适应权限组（推荐）
    val result3 = AwPermission.request(activity, *PermissionGroups.storage())
    // API 33+: 自动使用 READ_MEDIA_IMAGES + READ_MEDIA_VIDEO + READ_MEDIA_AUDIO
    // API < 33: 使用 READ/WRITE_EXTERNAL_STORAGE

    val result4 = AwPermission.request(activity, *PermissionGroups.location())
    // API 31+: 只请求 ACCESS_FINE_LOCATION（系统自动降级到 COARSE）
    // API < 31: 请求 FINE + COARSE

    // 使用 List 入参（无需展开运算符）
    val result5 = AwPermission.request(activity, PermissionGroups.LOCATION.toList())
}
```

### 带理由的请求

```kotlin
lifecycleScope.launch {
    // 默认策略：仅当系统建议展示理由时触发
    val result = AwPermission.requestWithRationale(
        activity,
        Manifest.permission.CAMERA,
    ) { permissions ->
        showRationaleDialog(permissions)
    }

    // OnDenied 策略：任何未授权的权限都触发（包括首次请求）
    val result2 = AwPermission.requestWithRationale(
        activity,
        Manifest.permission.CAMERA,
        strategy = RationaleStrategy.OnDenied
    ) { permissions ->
        // 使用 PermissionInfo 获取中文说明
        val descriptions = permissions.mapNotNull { PermissionInfo.getInfo(it)?.description }
        showRationaleDialog("需要以下权限：\n${descriptions.joinToString("\n")}")
    }

    // 如果用户取消了理由对话框，result 为 null
    if (result != null && result.isAllGranted) {
        openCamera()
    }
}
```

### 扩展函数

```kotlin
// suspend 扩展函数
lifecycleScope.launch {
    val result = requestRuntimePermissions(Manifest.permission.CAMERA)
    if (result.isAllGranted) { openCamera() }
}

// 带理由的 suspend 扩展
lifecycleScope.launch {
    val result = requestRuntimePermissionsWithRationale(
        Manifest.permission.CAMERA
    ) { permissions -> showRationaleDialog(permissions) }
}

// 基于回调的请求（自动检查 Activity 生命周期安全）
runWithPermissions(
    Manifest.permission.CAMERA,
    onGranted = { openCamera() },
    onDenied = { result -> handleDenial(result) }
)

// 检查权限
val hasCamera = hasPermission(Manifest.permission.CAMERA)
val hasAll = hasPermissions(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

// 批量查询权限状态
val statusMap = checkPermissions(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO
)
// statusMap = {CAMERA: true, RECORD_AUDIO: false}
```

### DSL 风格 API

```kotlin
lifecycleScope.launch {
    val result = buildPermissionRequest {
        permission(Manifest.permission.CAMERA)
        permissionGroup(PermissionGroups.LOCATION)
        strategy(RationaleStrategy.OnDenied)
        rationale { permissions ->
            showRationaleDialog(permissions)
        }
    }
    if (result != null && result.isAllGranted) {
        // 全部授权
    }
}
```

### 打开设置页并等待返回

```kotlin
lifecycleScope.launch {
    // 打开设置页，用户返回后自动检查权限
    val result = openAppSettingsAndWait(Manifest.permission.CAMERA)
    if (result.isGranted(Manifest.permission.CAMERA)) {
        // 用户已在设置中授权
    }
}
```

### Flow API

在 Activity 恢复时观察权限状态变化（例如用户从设置页返回后），支持区分永久拒绝：

```kotlin
lifecycleScope.launch {
    observePermissions(Manifest.permission.CAMERA).collect { result ->
        if (result.isAllGranted) {
            updateUI()
        } else if (result.hasPermanentlyDenied) {
            showGoToSettingsButton()
        }
    }
}
```

### 权限说明

```kotlin
// 获取权限的中文标签和说明
val info = PermissionInfo.getInfo(Manifest.permission.CAMERA)
// info.label = "相机"
// info.description = "用于拍照和录像"
// info.riskLevel = RiskLevel.DANGEROUS

// 批量获取
val infos = PermissionInfo.getInfos(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO
)

// 便捷方法
val label = PermissionInfo.getLabel(Manifest.permission.CAMERA) // "相机"
val desc = PermissionInfo.getDescription(Manifest.permission.CAMERA) // "用于拍照和录像"
```

### 特殊权限引导

国产 ROM 有很多非标准权限需要引导用户手动开启：

```kotlin
// 检查特殊权限状态
val hasFloatWindow = SpecialPermission.isGranted(context, SpecialPermission.PermissionType.FLOAT_WINDOW)
val hasBatteryOpt = SpecialPermission.isGranted(context, SpecialPermission.PermissionType.IGNORE_BATTERY_OPTIMIZATION)
val hasNotifyListener = SpecialPermission.isGranted(context, SpecialPermission.PermissionType.NOTIFICATION_LISTENER)

// 打开设置页
SpecialPermission.openSettings(context, SpecialPermission.PermissionType.AUTO_START)
SpecialPermission.openSettings(context, SpecialPermission.PermissionType.FLOAT_WINDOW)
SpecialPermission.openSettings(context, SpecialPermission.PermissionType.NOTIFICATION_LISTENER)
SpecialPermission.openSettings(context, SpecialPermission.PermissionType.BACKGROUND_POPUP)
SpecialPermission.openSettings(context, SpecialPermission.PermissionType.IGNORE_BATTERY_OPTIMIZATION)
```

> 注意：`AUTO_START`、`BACKGROUND_POPUP`、`BATTERY_SAVING` 无法程序化检查状态，`isGranted` 始终返回 `false`。

### 日志系统

```kotlin
// 启用日志（默认不输出）
AwPermission.setLogger { level, tag, msg ->
    when (level) {
        AwPermission.LogLevel.WARN -> Log.w(tag, msg)
        AwPermission.LogLevel.ERROR -> Log.e(tag, msg)
        else -> Log.d(tag, msg)
    }
}

// 禁用日志
AwPermission.setLogger(null)
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
// 自动适配国产 ROM（华为/小米/OPPO/vivo/魅族安全中心等）
// 如果所有方式均无法打开则返回 false
```

## 最佳实践

### 推荐的权限请求流程

```
请求权限 → 全部授权？→ 执行功能
              ↓ 否
         有永久拒绝？→ 展示引导 → openAppSettingsAndWait → 返回后检查
              ↓ 否
         展示 rationale → 用户确认 → 重新请求
```

```kotlin
lifecycleScope.launch {
    val result = AwPermission.request(activity, Manifest.permission.CAMERA)
    when (result.status) {
        PermissionResult.Status.Granted -> openCamera()
        PermissionResult.Status.Denied -> {
            val retry = AwPermission.requestWithRationale(
                activity, Manifest.permission.CAMERA,
                strategy = RationaleStrategy.OnDenied
            ) { permissions ->
                showRationaleDialog("需要相机权限来拍照")
            }
            if (retry?.isAllGranted == true) openCamera()
        }
        PermissionResult.Status.PermanentlyDenied -> {
            val settingsResult = openAppSettingsAndWait(Manifest.permission.CAMERA)
            if (settingsResult.isGranted(Manifest.permission.CAMERA)) {
                openCamera()
            }
        }
    }
}
```

### 线程安全说明

| 方法 | 线程要求 |
|------|----------|
| `isGranted` / `isAllGranted` | 任意线程 |
| `shouldShowRationale` / `permissionsRequiringRationale` | 主线程 |
| `request` / `requestWithRationale` | 主线程（协程） |
| `openAppSettings` | 任意线程 |
| `openAppSettingsAndWait` | 主线程（协程） |
| `SpecialPermission.isGranted` | 任意线程 |
| `SpecialPermission.openSettings` | 任意线程 |

### ProGuard

库自带 `consumer-rules.pro`，使用者无需额外配置。仅公共 API 被保留，内部实现类可被混淆优化。

## 从其他权限库迁移

### 从 PermissionsDispatcher 迁移

| PermissionsDispatcher | aw-permission |
|---|---|
| `@NeedsPermission` | `AwPermission.request()` + `isAllGranted` 检查 |
| `@OnShowRationale` | `requestWithRationale()` 的 rationale 回调 |
| `@OnPermissionDenied` | `result.hasDenied` 检查 |
| `@OnNeverAskAgain` | `result.hasPermanentlyDenied` 检查 |
| 注解处理器 + 生成代码 | 纯 Kotlin 协程，无代码生成 |

### 从 RxPermissions 迁移

| RxPermissions | aw-permission |
|---|---|
| `rxPermissions.requestEach()` | `AwPermission.request()` |
| `Observable` / `Single` | `suspend` 函数 |
| 需要 RxJava 依赖 | 仅需协程依赖 |
| `shouldShowRequestPermissionRationale` 手动处理 | 内置 `requestWithRationale()` |

## API 参考

### AwPermission

| 方法 | 说明 |
|------|------|
| `isGranted(context, permission)` | 检查单个权限是否已授权 |
| `isAllGranted(context, vararg permissions)` | 检查所有权限是否已授权 |
| `shouldShowRationale(activity, permission)` | 检查是否应展示权限理由 |
| `permissionsRequiringRationale(activity, vararg permissions)` | 返回需要理由说明的权限列表 |
| `request(activity, vararg permissions)` | 请求权限（挂起函数，`@CheckResult`） |
| `request(activity, permissions: List<String>)` | 请求权限（List 入参） |
| `request(fragment, vararg permissions)` | 从 Fragment 请求权限（挂起函数） |
| `request(fragment, permissions: List<String>)` | 从 Fragment 请求权限（List 入参） |
| `requestWithRationale(activity, vararg, rationale)` | 带理由的请求（挂起函数，取消时返回 null） |
| `requestWithRationale(activity, vararg, strategy, rationale)` | 带理由的请求（可指定触发策略） |
| `requestWithRationale(fragment, vararg, rationale)` | 从 Fragment 带理由的请求 |
| `requestWithRationale(fragment, vararg, strategy, rationale)` | 从 Fragment 带理由的请求（可指定触发策略） |
| `openAppSettings(context)` | 打开应用设置页（国产 ROM 多重回退），不可用时返回 false |
| `openAppSettingsAndWait(activity, vararg permissions)` | 打开设置页并等待返回后检查权限（挂起函数，120秒超时） |
| `openAppSettingsAndWait(fragment, vararg permissions)` | 从 Fragment 打开设置页并等待返回后检查权限 |
| `setLogger(logger)` | 设置自定义日志输出，传 null 禁用 |

### PermissionResult

| 属性/方法 | 说明 |
|-----------|------|
| `granted` | 已授权的权限列表 |
| `denied` | 被拒绝的权限列表 |
| `permanentlyDenied` | 被永久拒绝的权限列表 |
| `isEmpty` | 三个列表全为空时返回 true |
| `isAllGranted` | 是否所有权限均已授权（空结果返回 false） |
| `hasDenied` | 是否存在被拒绝的权限 |
| `hasPermanentlyDenied` | 是否存在被永久拒绝的权限 |
| `grantedCount` | 已授权的权限数量 |
| `deniedCount` | 被拒绝的权限数量 |
| `permanentlyDeniedCount` | 被永久拒绝的权限数量 |
| `status` | 整体状态：`Granted`、`Denied` 或 `PermanentlyDenied` |
| `firstDenied` | 第一个被拒绝的权限，若没有则为 null |
| `firstPermanentlyDenied` | 第一个被永久拒绝的权限，若没有则为 null |
| `allDenied` | 所有被拒绝的权限（denied + permanentlyDenied） |
| `isGranted(permission)` | 检查指定权限是否已授予（O(1)，懒加载） |
| `isDenied(permission)` | 检查指定权限是否被拒绝（O(1)，懒加载） |
| `isPermanentlyDenied(permission)` | 检查指定权限是否被永久拒绝（O(1)，懒加载） |

### RationaleStrategy

| 策略 | 说明 |
|------|------|
| `OnShouldShow` | 仅当系统建议展示理由时触发（默认） |
| `OnDenied` | 任何未授权的权限都触发（包括首次请求） |

### PermissionInfo

| 方法 | 说明 |
|------|------|
| `getInfo(permission)` | 获取权限信息（标签、说明、风险等级） |
| `getLabel(permission)` | 获取权限中文标签 |
| `getDescription(permission)` | 获取权限中文说明 |
| `getInfos(vararg permissions)` | 批量获取权限信息 |

### SpecialPermission

| 方法 | 说明 |
|------|------|
| `isGranted(context, permissionType)` | 检查特殊权限是否已授权 |
| `openSettings(context, permissionType)` | 打开特殊权限设置页 |

支持的权限类型：`AUTO_START`、`NOTIFICATION_LISTENER`、`FLOAT_WINDOW`、`BACKGROUND_POPUP`、`BATTERY_SAVING`、`IGNORE_BATTERY_OPTIMIZATION`

可检查状态的权限：`FLOAT_WINDOW`、`NOTIFICATION_LISTENER`、`IGNORE_BATTERY_OPTIMIZATION`

### PermissionGroups

预定义的权限组常量：

| 权限组 | 包含权限 | 最低 API |
|--------|----------|----------|
| `CAMERA` | `CAMERA` | 1 |
| `MICROPHONE` | `RECORD_AUDIO` | 1 |
| `LOCATION` | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | 1 |
| `CONTACTS` | `READ_CONTACTS`, `WRITE_CONTACTS`, `GET_ACCOUNTS` | 1 |
| `STORAGE` | `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`（API 33 废弃） | 1 |
| `PHONE` | `READ_PHONE_STATE`, `CALL_PHONE` 等 | 1 |
| `SENSORS` | `BODY_SENSORS` | 20 |
| `SMS` | `SEND_SMS`, `RECEIVE_SMS` 等 | 1 |
| `CALENDAR` | `READ_CALENDAR`, `WRITE_CALENDAR` | 1 |
| `ACTIVITY_RECOGNITION` | `ACTIVITY_RECOGNITION` | 29 |
| `BACKGROUND_LOCATION` | `ACCESS_BACKGROUND_LOCATION` | 29 |
| `MEDIA_VISUAL` | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` | 33 |
| `MEDIA_AUDIO` | `READ_MEDIA_AUDIO` | 33 |
| `NOTIFICATIONS` | `POST_NOTIFICATIONS` | 33 |
| `NEARBY_DEVICES` | `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE` | 31 |
| `NEARBY_WIFI` | `NEARBY_WIFI_DEVICES` | 33 |
| `MEDIA_PARTIAL` | `READ_MEDIA_VISUAL_USER_SELECTED` | 34 |
| `SENSORS_BACKGROUND` | `BODY_SENSORS_BACKGROUND` | 33 |

版本自适应函数（推荐使用）：

| 函数 | 说明 |
|------|------|
| `storage()` | API 33+ 返回 `MEDIA_VISUAL + MEDIA_AUDIO`，之前返回 `STORAGE` |
| `location()` | API 31+ 只请求 `FINE_LOCATION`，之前返回 `LOCATION` |

### 扩展函数

| 函数 | 说明 |
|------|------|
| `Context.hasPermission(permission)` | 检查单个权限 |
| `Context.hasPermissions(vararg permissions)` | 检查所有权限 |
| `Context.checkPermissions(vararg permissions)` | 批量查询权限状态，返回 Map |
| `FragmentActivity.requestRuntimePermissions(vararg)` | suspend 请求权限 |
| `Fragment.requestRuntimePermissions(vararg)` | 从 Fragment suspend 请求权限 |
| `FragmentActivity.requestRuntimePermissionsWithRationale(vararg, strategy, rationale)` | suspend 带理由请求 |
| `Fragment.requestRuntimePermissionsWithRationale(vararg, strategy, rationale)` | 从 Fragment suspend 带理由请求 |
| `FragmentActivity.runWithPermissions(vararg, onGranted, onDenied)` | 带授权/拒绝处理器的请求（生命周期安全） |
| `Fragment.runWithPermissions(vararg, onGranted, onDenied)` | 从 Fragment 带处理器的请求 |
| `FragmentActivity.observePermissions(vararg)` | 每次 Activity 恢复时发射的 Flow（区分永久拒绝） |
| `FragmentActivity.openAppSettingsAndWait(vararg)` | 打开设置页并等待返回后检查权限 |
| `Fragment.openAppSettingsAndWait(vararg)` | 从 Fragment 打开设置页并等待返回 |
| `FragmentActivity.buildPermissionRequest(block)` | DSL 风格请求 |
| `Fragment.buildPermissionRequest(block)` | 从 Fragment DSL 风格请求 |

## 工作原理

1. 每次权限请求会创建一个无界面的 `PermissionFragment` 实例
2. 该 Fragment 使用 `ActivityResultContracts.RequestMultiplePermissions()` 请求权限
3. 调用方的协程通过 `suspendCancellableCoroutine` 挂起
4. 当用户响应后，协程以结果恢复执行
5. Fragment 自动从 Activity 中移除自身
6. 如果 60 秒内无响应，超时保护将所有未决权限标记为 denied

### 并发安全

所有权限请求（包括 rationale 展示）通过单个 `Mutex` 进行序列化。这确保了：
- 同一时刻只有一个权限请求流程处于活跃状态
- 不会因并发请求导致续体被覆盖
- 不会同时出现多个 rationale 对话框
- 请求按 FIFO（先进先出）顺序处理

### 拒绝 vs 永久拒绝

本库使用三层检测策略来准确分类拒绝类型：

| 场景 | 请求前 | 请求后 | 分类 |
|------|--------|--------|------|
| 首次拒绝 | `false` | `true` | 拒绝 |
| 首次 + 勾选"不再询问" | `false` | `false` | 请求历史 + AppOps 检测* |
| 后续拒绝 | `true` | `true` | 拒绝 |
| 已勾选"不再询问" | `true` | `false` | 永久拒绝 |

*\* 在首次请求时，如果用户选择了"不再询问"，标准 AOSP 无法将其与普通首次拒绝区分。本库通过请求历史记录（SharedPreferences）+ `AppOpsManager.checkOpNoThrow` + `noteOpNoThrow` 三重检测，在国产 ROM 上也能准确识别永久拒绝，且不会将从未请求过的权限误判为永久拒绝。*

### 国产 ROM 兼容

本库包含以下国产 ROM 适配：
- **永久拒绝检测**：仅在国产 ROM 上启用 AppOps 回退检测（checkOp + noteOp 双重检测），兼容 MIUI/EMUI/ColorOS/Flyme 等
- **请求历史记录**：记录已请求过的权限，防止首次请求被误判为永久拒绝
- **openAppSettings**：多重 Intent 回退链（标准设置页 → 华为安全中心 → 小米安全中心 → OPPO → vivo → 魅族 → 最终回退）
- **特殊权限引导**：自启动/悬浮窗/通知栏/后台弹出/省电策略等国产 ROM 特有权限，支持状态检查
- **超时保护**：权限请求 60 秒超时，设置页等待 120 秒超时，防止协程永久挂起
- **配置变更安全**：隐藏 Fragment 在配置变更后通过 `DefaultLifecycleObserver` 延迟移除，无泄漏

## 常见问题

### 可以在 DialogFragment 中使用吗？

可以。传入 DialogFragment 的父 Activity：

```kotlin
val result = AwPermission.request(requireActivity(), Manifest.permission.CAMERA)
```

### 可以从 Service 中请求权限吗？

不可以。Android 要求通过 Activity 或 Fragment 来展示权限对话框。你应该在 Activity 中请求权限并存储结果。

### 自定义 ROM 兼容性如何？

本库包含以下安全检查：
- `openAppSettings()` 使用多重 Intent 回退链，适配华为/小米/OPPO/vivo/魅族等国产 ROM
- Activity 状态检查防止在正在结束/已销毁的 Activity 上崩溃
- 权限启动器抛出的 `IllegalStateException` 会被捕获并作为取消传播
- `AppOpsManager` 增强检测国产 ROM 上的永久拒绝（checkOp + noteOp 双重检测）
- 请求历史记录防止首次请求被误判为永久拒绝
- 60 秒超时保护防止协程永久挂起

### Flow API 是如何工作的？

`observePermissions()` 创建一个 Flow，在每次 Activity 恢复时发射当前权限状态。这对于检测用户从系统设置页返回后授权/拒绝权限的情况非常有用。Flow 使用 `PermissionDetector` 的 AppOps 增强检测来区分"从未请求"和"永久拒绝"，比简单的 `shouldShowRequestPermissionRationale` 检测更准确。

### DSL API 和直接调用有什么区别？

DSL API 是语法糖，将权限列表、策略和 rationale 回调统一在一个 block 中：

```kotlin
// DSL 风格
val result = buildPermissionRequest {
    permission(Manifest.permission.CAMERA)
    permissionGroup(PermissionGroups.LOCATION)
    strategy(RationaleStrategy.OnDenied)
    rationale { showRationaleDialog(it) }
}

// 等价于
val result = AwPermission.requestWithRationale(
    activity,
    Manifest.permission.CAMERA,
    *PermissionGroups.LOCATION,
    strategy = RationaleStrategy.OnDenied
) { showRationaleDialog(it) }
```

### 如何处理 Android 14 部分媒体权限？

Android 14 引入了 `READ_MEDIA_VISUAL_USER_SELECTED` 权限，允许用户选择特定的照片和视频。此权限的行为与其他权限不同——即使获得此权限，也不代表获得了完整的媒体访问权限。建议在使用 `PermissionGroups.storage()` 时注意此权限的特殊行为。

### 扩展函数为什么命名为 requestRuntimePermissions？

`requestRuntimePermissions` 避免了与 Android 原生 `Fragment.requestPermissions(String[], int)` 的命名冲突。虽然原生 API 已废弃，但仍然存在，使用不同名称可以避免编译歧义。

## 许可证

Apache License 2.0，详见 [LICENSE](LICENSE)。
