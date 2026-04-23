# aw-permission Demo 功能矩阵

| 区域 | 能力 |
|------|------|
| Chip 多选 + 请求所选 | 批量权限 |
| 相机 / 定位 / 存储 / 多权限 | 常用组 |
| Rationale / DSL | `requestWithRationale`（**Chip** 选 OnDenied / OnShouldShow）、`buildPermissionRequest` |
| 检查 / 设置 | 状态与跳转；`openAppSettingsAndWait` 的 **AUTO / OEM_FIRST / STANDARD_FIRST**（Chip） |
| 特殊权限 | 悬浮窗、电池、通知（依系统版本） |
| Flow | `observePermissions` 日志 |

README **推荐写法与反模式**、**国产 ROM** 与本 demo 联读。工具栏菜单 **「演示清单」** 可查看本摘要。

## 推荐手测（边界与极端场景）

| 场景 | 建议操作 |
|------|----------|
| 永久拒绝 | 拒绝两次后进设置，修改权限再返回应用 |
| 国产 ROM | 小米/华为/OPPO/vivo 各一台走「设置跳转 + 权限页」 |
| 旋转 | 请求对话框展示中旋转屏幕 |
| 连续请求 | 快速连点多个权限按钮，确认互斥与无重复弹窗 |
