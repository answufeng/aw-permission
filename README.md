# aw-permission

[![JitPack](https://jitpack.io/v/answufeng/aw-permission.svg)](https://jitpack.io/#answufeng/aw-permission)

> 同属 [answufeng](https://github.com/answufeng) 的 `aw-*` 基础库（架构、网络、存储等）之一：面向 **传统 View/XML**（非 Compose），基线常见为 **minSdk 24**、**JDK 17**。

**简介**：用 **Kotlin 协程** + 无界面 **Fragment** 完成运行时危险权限请求，**无需**重写 `onRequestPermissionsResult`；与 ROM 设置页、永久拒绝、理由说明等能力一并文档化在下方。

---

## 目录

| 想做什么 | 去哪里 |
|----------|--------|
| 5 分钟接进工程 | [快速开始](#快速开始) |
| 能干什么 / 系统版本 | [特性与环境要求](#特性与环境要求) |
| 多权限、Rationale、DSL、Flow 等 | [使用示例](#使用示例) |
| 推荐流程、线程约束 | [最佳实践与线程](#最佳实践与线程) |
| 并发、ROM、机制说明 | [工作原理](#工作原理) |
| 查方法表、权限组表 | [API 与附录](#api-与附录) |
| 排错、迁移 | [常见问题](#常见问题) · [从其他库迁移](#从其他库迁移) |
| 发版前检查、Demo | [工程、ROM 与演示](#工程rom-与演示) |

---

## 快速开始

### 1. 添加依赖

在 `settings.gradle.kts` 中启用 JitPack，在 `app/build.gradle.kts` 中：

```kotlin
dependencies {
    implementation("com.github.answufeng:aw-permission:1.0.1")
}
```

### 2. 请求并处理

```kotlin
lifecycleScope.launch {
    val result = AwPermission.request(this@MainActivity, Manifest.permission.CAMERA)
    when {
        result.isAllGranted -> openCamera()
        result.hasPermanentlyDenied -> AwPermission.openAppSettings(this@MainActivity)
    }
}
```

### 3. 建议与反模式

| 建议 | 避免 |
|------|------|
| 在 `lifecycleScope` / `viewLifecycleOwner.lifecycleScope` 里**单次** `launch` 请求 | 在 `onResume` 里无状态反复弹框 |
| 多段流程依赖库内 **Mutex** 或业务自行串行 | 多路并发同时出系统权限对话框 |
| 永久拒绝后说明原因再 **`openAppSettings` / `openAppSettingsAndWait`** | 以为所有 ROM 上 `shouldShowRationale` 都可靠 |
| 把**权限弹窗 60s、设置页等待 120s** 仅作异常兜底 | 把超时时序当成功路径 |

---

## 特性与环境要求

| 项 | 版本或说明 |
|----|------------|
| 协程 `suspend` 请求、Mutex 序列化、国产 ROM 增强的「永久拒绝」判断 | 见 [工作原理](#工作原理) |
| Rationale 挂起回调 + 策略、扩展函数、DSL、`Flow` 观察、权限组与 `storage()`/`location()` 自适应 | 见 [使用示例](#使用示例) |
| 应用内设置跳转、挂起式「设置回来再检查」、类 OEM 特殊权限引导 | 见库内 `SpecialPermission` |
| **minSdk** | 24+ |
| **compileSdk / demo targetSdk** | 35（以工程为准） |
| **Kotlin / JVM** | 2.0+ / 17 |
| **协程 / AndroidX** | 1.9+；Activity 1.9+、Fragment 1.8+（以 `build.gradle` 为准） |

---

## 工程、ROM 与演示

- **CI**：[`.github/workflows/ci.yml`](.github/workflows/ci.yml)（`assembleRelease`、`ktlintCheck`、`lintRelease`、`:demo:assembleRelease` 等，以仓库为准）。
- **本地**：`./gradlew :aw-permission:assembleRelease :aw-permission:ktlintCheck :aw-permission:lintRelease :demo:assembleRelease`
- **国产 ROM**：厂商设置 Intent、AppOps 会随系统变化；本库用 `PermissionDetector` + Manifest `queries` 提高命中率，**无法保证**全机型永久有效。发版前建议至少各一台：小米、华为/荣耀、OPPO/vivo，手测见 [demo/DEMO_MATRIX.md](demo/DEMO_MATRIX.md)。维护策略：本仓库 [PermissionDetector 与国产 ROM 维护策略](#permissiondetector-与国产-rom-维护策略) 一节与 Issue/PR 协作，避免业务各写一套魔改。

**演示应用** `demo`：批量选择、Rationale 策略、应用设置启动策略、特殊权限、Flow 观察等；与本文对照见 [DEMO_MATRIX.md](demo/DEMO_MATRIX.md)，工具栏有「演示清单」类入口。

### PermissionDetector 与国产 ROM 维护策略

- 业务上默认 **`AppSettingsLaunchStrategy.AUTO`**，关键路径上 `openAppSettings` 失败要有 Toast/文案；若全部 Intent 都失败，仍可能在后续 `onResume` 读到权限，需结合产品判断。
- 超时（60s / 120s）仅兜底，不当作成功路径。

---

## 使用示例

<details>
<summary><b>多权限与权限组</b></summary>

```kotlin
lifecycleScope.launch {
    val r = AwPermission.request(
        activity,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )
    if (r.isGranted(Manifest.permission.CAMERA)) { /* */ }
    AwPermission.request(activity, *PermissionGroups.LOCATION)
    AwPermission.request(activity, *PermissionGroups.storage())
    AwPermission.request(activity, *PermissionGroups.location())
    AwPermission.request(activity, PermissionGroups.LOCATION.toList())
}
```

</details>

<details>
<summary><b>带理由的请求（<code>requestWithRationale</code>）</b></summary>

```kotlin
lifecycleScope.launch {
    val a = AwPermission.requestWithRationale(
        activity, Manifest.permission.CAMERA
    ) { perms -> showRationaleDialog(perms) }
    val b = AwPermission.requestWithRationale(
        activity, Manifest.permission.CAMERA,
        strategy = RationaleStrategy.OnDenied
    ) { perms -> showRationaleDialog(...) }
    if (a != null && a.isAllGranted) { /* 用户未取消说明且已授权 */ }
}
```

</details>

<details>
<summary><b>扩展、DSL、设置页等待、<code>Flow</code>、<code>PermissionInfo</code>、<code>SpecialPermission</code>、日志</b></summary>

```kotlin
// 扩展
lifecycleScope.launch {
    val r = requestRuntimePermissions(Manifest.permission.CAMERA)
    val r2 = requestRuntimePermissionsWithRationale(Manifest.permission.CAMERA) { showRationaleDialog(it) }
    val w = openAppSettingsAndWait(Manifest.permission.CAMERA)
}
runWithPermissions(Manifest.permission.CAMERA, onGranted = { }, onDenied = { })
val has = hasPermission(Manifest.permission.CAMERA)
val map = checkPermissions(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

// DSL
lifecycleScope.launch {
    val r = buildPermissionRequest {
        permission(Manifest.permission.CAMERA)
        permissionGroup(PermissionGroups.LOCATION)
        strategy(RationaleStrategy.OnDenied)
        rationale { showRationaleDialog(it) }
    }
}

// Flow
lifecycleScope.launch {
    observePermissions(Manifest.permission.CAMERA).collect { /* 更新 UI */ }
}

// 中文说明
PermissionInfo.getInfo(Manifest.permission.CAMERA)
PermissionInfo.getLabel(Manifest.permission.CAMERA)

// 类 OEM 特殊能力（部分类型无法程序化 isGranted，见下表与 KDoc）
SpecialPermission.isGranted(ctx, SpecialPermission.PermissionType.FLOAT_WINDOW)
SpecialPermission.openSettings(ctx, SpecialPermission.PermissionType.AUTO_START)

// 库内日志（默认关闭）
AwPermission.setLogger { level, tag, msg -> Log.d(tag, msg) }
AwPermission.setLogger(null)

// 仅打开设置（不等待）
AwPermission.openAppSettings(context)
```

</details>

<details>
<summary><b>与 <code>AwPermission</code> 同级的「检查 / <code>shouldShowRationale</code>」</b></summary>

```kotlin
AwPermission.isGranted(context, Manifest.permission.CAMERA)
AwPermission.isAllGranted(context, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
AwPermission.shouldShowRationale(activity, Manifest.permission.CAMERA)
```

</details>

---

## 最佳实践与线程

**流程**（简图）：

```
request → 全授权？ → 是 → 做业务
   ↓ 否
  有永久拒绝？ → 是 → 引导 → openAppSettingsAndWait → 再判
   ↓ 否
  展示 rationale → 再 request
```

```kotlin
lifecycleScope.launch {
    val result = AwPermission.request(activity, Manifest.permission.CAMERA)
    if (result.isAllGranted) {
        openCamera()
    } else if (result.hasPermanentlyDenied) {
        val after = openAppSettingsAndWait(Manifest.permission.CAMERA)
        if (after.isGranted(Manifest.permission.CAMERA)) openCamera()
    } else {
        // 仍可再次请求或走 requestWithRationale
    }
}
```

三表全空时 `isAllGranted` 为 false，勿与「已授」混淆；说明见 [常见问题](#常见问题)。

| API 类型 | 线程 |
|----------|------|
| `isGranted` / `isAllGranted` | 任意 |
| `shouldShowRationale` / `permissionsRequiringRationale` | 主线程 |
| `request` / `requestWithRationale` / `openAppSettingsAndWait` | 主线程 + 协程；与**同一 Mutex** 串行 |
| `openAppSettings`、`SpecialPermission.*` | 任意（仍须在合适上下文启动 Activity） |

**ProGuard**：已带 `consumer-rules.pro`，一般**无需**再在宿主加规则。

---

## 工作原理

1. 每次请求创建无界面 `PermissionFragment`，`ActivityResultContracts.RequestMultiplePermissions` 发起系统对话框，协程 `suspendCancellableCoroutine` 挂起直至结果。
2. 结束后从 Activity 移除 Fragment；若 **60s** 无回调则视为本次未批。
3. `openAppSettingsAndWait` 在离开前台后再次 `onResume` 时采集，带 **120s** 等待，避免刚在前台时误触发。
4. 所有**权限请求、理由流程、打开设置并等待**共用一个 **Mutex**，FIFO，避免多对话框与续体覆盖。

**「拒绝 / 永久拒绝」**：AOSP 的 rationale 位 + 请求历史 +（国产 ROM 上）`AppOpsManager` 等组合判断；细节见下表与源码注释。

| 场景 | 请求前 rationale | 请求后 | 分类 |
|------|-------------------|--------|------|
| 首次拒绝 | false | true | 拒绝 |
| 首次 + 不再询问 | false | false | 历史 + AppOps* |
| 后续普通拒绝 | true | true | 拒绝 |
| 已勾选不再询问 | true | false | 永久拒绝 |

\* 首次与「未请求过」在部分 ROM 上需历史与 AppOps 辅助区分。

**国产能力摘要**：`openAppSettings` 多 Intent 回退、Manifest `queries`、ROM 类特殊权限、配置变更时 Fragment 安全移除等，见 [工程、ROM 与演示](#工程rom-与演示)。

---

## API 与附录

<details>
<summary><b>AwPermission（节选，完整以源码与 KDoc 为准）</b></summary>

| 方法 | 说明 |
|------|------|
| `isGranted` / `isAllGranted` | 查询是否已授 |
| `shouldShowRationale` / `permissionsRequiringRationale` | 是否建议展示说明 |
| `request` / `requestWithRationale` | 协程请求；`rationale` 可返回 `null` 表示用户取消 |
| `openAppSettings` / `openAppSettingsAndWait` | 跳转与挂起式返回后复检；`AndWait` 与 `request` 同 **Mutex** |
| `setLogger` / `isLikelyCustomRom` | 日志与 ROM 粗判 |

</details>

<details>
<summary><b>PermissionResult</b></summary>

| 属性/方法 | 说明 |
|-----------|------|
| `granted` / `denied` / `permanentlyDenied` | 三类列表 |
| `isAllGranted` | 分表有内容且未拒绝/未永久拒时为真；**全空表时为 false** |
| `isEmpty` | 三表皆空 |
| `status` | 三表全空时仍为 `Granted` 的历史语义；业务请配合 `isAllGranted` / `isEmpty` 使用 |
| `isGranted(permission)` 等 | 单权限状态 |

</details>

<details>
<summary><b>RationaleStrategy、PermissionInfo、扩展函数、PermissionGroups</b></summary>

- `RationaleStrategy`：`OnShouldShow`（默认，系统建议才说明） / `OnDenied`（未授即说明，含首启）。
- `PermissionInfo`：部分权限的中文案与风险等级，见 `getInfo` / `getLabel` / `getDescription`。
- 扩展（节选）：`requestRuntimePermissions`、`requestRuntimePermissionsWithRationale`、`runWithPermissions`、`observePermissions`、`openAppSettingsAndWait`、`buildPermissionRequest`、`hasPermission`、`checkPermissions`。
- `PermissionGroups`：静态组 + `storage()`、`location()` 版本适配；`NOTIFICATIONS`、`NEARBY_*`、`MEDIA_*` 等有最低 API 要求，见下表与 KDoc。

| 组名（节选） | 最低 API 提示 |
|--------------|----------------|
| `NOTIFICATIONS` | 33+ |
| `NEARBY_DEVICES` | 31+ |
| `MEDIA_VISUAL` / `MEDIA_AUDIO` 等 | 见各常量 KDoc |
| `storage()` | 33+ 为细分媒体，否则存储读写 |

</details>

<details>
<summary><b>SpecialPermission 类型与可检测性</b></summary>

支持含：`AUTO_START`、`NOTIFICATION_LISTENER`、`FLOAT_WINDOW`、`BACKGROUND_POPUP`、`BATTERY_SAVING`、`IGNORE_BATTERY_OPTIMIZATION`、`WRITE_SETTINGS`（M+）、`REQUEST_INSTALL_PACKAGES`（O+）等。其中厂商类能力部分**不能**在代码中可靠 `isGranted`，以 KDoc 为准。

</details>

---

## 从其他库迁移

| 来源 | 对应到本库 |
|------|------------|
| PermissionsDispatcher | `AwPermission.request` + `isAllGranted`；`@OnShowRationale` → `requestWithRationale` |
| RxPermissions | `suspend` + `request`；流式改为协程/Flow |

（注解生成代码 → 无 KSP，手写在 Activity/Fragment。）

---

## 常见问题

<details>
<summary><b>三表都空的 <code>PermissionResult</code> 怎么理解？</b></summary>

`isEmpty == true` 时 `isAllGranted == false`，`status` 可能仍为 `Granted`（历史兼容）。业务上表示「无分项」；请用 `isAllGranted` 和 `isEmpty` 判断能否继续，不要单看 `status == Granted`。

</details>

<details>
<summary><b>DialogFragment、Service 能否请求？</b></summary>

- DialogFragment：用宿主 `Activity` / `requireActivity()` 调 `AwPermission.request`。
- Service：**不能**直接弹系统权限；在 Activity 中请求后存状态。

</details>

<details>
<summary><b>Flow、DSL 与 <code>requestRuntimePermissions</code> 命名？</b></summary>

- `observePermissions`：在每次 **RESUMED** 时发射当前结果，结合 `PermissionDetector` 可区分未请求与部分永久拒绝场景。
- DSL 等价于组合 `requestWithRationale` 与多权限展开。
- `requestRuntimePermissions` 避免与旧版 `Fragment.requestPermissions` 命名冲突。

</details>

<details>
<summary><b>Android 14 部分选图、媒体读权限？</b></summary>

`READ_MEDIA_VISUAL_USER_SELECTED` 与「完全读媒体」不是同一能力；用 `PermissionGroups.storage()` 时注意行为差异。

</details>

更多条目（ROM、超时、与 README 长版等）可查阅 Git 历史中的完整版说明与 [DEMO_MATRIX.md](demo/DEMO_MATRIX.md) 手测表。

---

## 许可证

Apache License 2.0，见 [LICENSE](LICENSE)。

*文档更新：2026-04-23*
