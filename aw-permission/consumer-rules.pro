# aw-permission consumer ProGuard rules

# Main API
-keep class com.answufeng.permission.AwPermission { public *; }
-keep class com.answufeng.permission.AwPermission$* { public *; }

# PermissionResult
-keep class com.answufeng.permission.PermissionResult { public *; }
-keep class com.answufeng.permission.PermissionResult$* { public *; }

# RationaleStrategy
-keep class com.answufeng.permission.RationaleStrategy { public *; }

# PermissionGroups
-keep class com.answufeng.permission.PermissionGroups { public *; }

# PermissionInfo
-keep class com.answufeng.permission.PermissionInfo { public *; }
-keep class com.answufeng.permission.PermissionInfo$* { public *; }

# SpecialPermission
-keep class com.answufeng.permission.SpecialPermission { public *; }
-keep class com.answufeng.permission.SpecialPermission$* { public *; }

# PermissionFragment (hidden Fragment, must not be removed)
-keep class com.answufeng.permission.PermissionFragment { public *; }

# PermissionDetector (internal detection logic, keep class name only)
-keep class com.answufeng.permission.PermissionDetector

# PermissionRequest / Builder
-keep class com.answufeng.permission.PermissionRequest { public *; }
-keep class com.answufeng.permission.PermissionRequest$Builder { public *; }

# PermissionHistory (internal tracking, keep class name only)
-keep class com.answufeng.permission.PermissionHistory

# Extension functions
-keepclassmembers class com.answufeng.permission.PermissionExtKt { public *; }

# Kotlin metadata
-keepattributes Signature, *Annotation*
-keep class kotlin.Metadata { *; }
