# aw-permission consumer ProGuard rules

-keep class com.answufeng.permission.AwPermission { *; }
-keep class com.answufeng.permission.PermissionResult { *; }
-keep class com.answufeng.permission.PermissionResult$Status { *; }
-keep class com.answufeng.permission.RationaleStrategy { *; }
-keep class com.answufeng.permission.PermissionGroups { *; }
-keep class com.answufeng.permission.PermissionInfo { *; }
-keep class com.answufeng.permission.PermissionInfo$Info { *; }
-keep class com.answufeng.permission.PermissionInfo$RiskLevel { *; }
-keep class com.answufeng.permission.SpecialPermission { *; }
-keep class com.answufeng.permission.SpecialPermission$PermissionType { *; }
-keep class com.answufeng.permission.PermissionRequest { *; }
-keep class com.answufeng.permission.PermissionRequest$Builder { *; }
-keepclassmembers class com.answufeng.permission.PermissionExtKt { *; }
