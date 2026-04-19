# aw-permission consumer ProGuard rules

-keep class com.answufeng.permission.AwPermission { public *; }
-keep class com.answufeng.permission.AwPermission$LogLevel { public *; }
-keep class com.answufeng.permission.PermissionResult { public *; }
-keep class com.answufeng.permission.PermissionResult$Status { public *; }
-keep class com.answufeng.permission.RationaleStrategy { public *; }
-keep class com.answufeng.permission.PermissionGroups { public *; }
-keep class com.answufeng.permission.PermissionInfo { public *; }
-keep class com.answufeng.permission.PermissionInfo$Info { public *; }
-keep class com.answufeng.permission.PermissionInfo$RiskLevel { public *; }
-keep class com.answufeng.permission.SpecialPermission { public *; }
-keep class com.answufeng.permission.SpecialPermission$PermissionType { public *; }
-keep class com.answufeng.permission.PermissionRequest { public *; }
-keep class com.answufeng.permission.PermissionRequest$Builder { public *; }
-keepclassmembers class com.answufeng.permission.PermissionExtKt { public *; }
