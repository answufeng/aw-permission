# aw-permission

Android 运行时权限库，基于协程 + 隐藏 Fragment 实现，无需覆写 onRequestPermissionsResult。

## 引入

```kotlin
dependencies {
    implementation("com.github.answufeng:aw-permission:1.0.0")
}
```

## 功能特性

- 基于协程的权限请求（suspend 函数）
- Mutex 串行化保证并发安全
- 区分「拒绝」和「永久拒绝（不再询问）」
- 支持权限理由说明（Rationale）自定义 UI
- Activity/Fragment 扩展函数
- 内置跳转应用设置页

## 使用示例

```kotlin
// 简单请求
lifecycleScope.launch {
    val result = BrickPermission.request(activity, Manifest.permission.CAMERA)
    if (result.isAllGranted) { openCamera() }
    else if (result.hasPermanentlyDenied) { BrickPermission.openAppSettings(context) }
}

// 带理由说明
val result = BrickPermission.requestWithRationale(
    activity,
    arrayOf(Manifest.permission.CAMERA),
    rationale = { permissions, proceed, cancel ->
        AlertDialog.Builder(this)
            .setMessage("需要相机权限才能拍照")
            .setPositiveButton("允许") { _, _ -> proceed() }
            .setNegativeButton("拒绝") { _, _ -> cancel() }
            .show()
    }
)

// 扩展函数
requirePermissions(Manifest.permission.CAMERA,
    onGranted = { openCamera() },
    onDenied = { result -> handleDenial(result) }
)
```

## 许可证

Apache License 2.0，详见 [LICENSE](LICENSE)。
