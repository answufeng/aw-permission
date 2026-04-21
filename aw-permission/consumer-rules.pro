# aw-permission consumer ProGuard rules
# 宿主应用混淆时需要保留的公共 API

# Main API
-keep class com.answufeng.permission.AwPermission {
    public *** setLogger(...);
    public static *** isGranted(...);
    public static *** isAllGranted(...);
    public static *** shouldShowRationale(...);
    public static *** permissionsRequiringRationale(...);
    public static *** request(...);
    public static *** requestWithRationale(...);
    public static *** openAppSettings(...);
    public static *** openAppSettingsAndWait(...);
}

-keep class com.answufeng.permission.AwPermission$LogLevel {
    public static *** values();
    public static *** valueOf(java.lang.String);
}

# PermissionResult
-keep class com.answufeng.permission.PermissionResult {
    public *** getGranted();
    public *** getDenied();
    public *** getPermanentlyDenied();
    public *** getEmpty();
    public *** getAllGranted();
    public *** getHasDenied();
    public *** getHasPermanentlyDenied();
    public *** getGrantedCount();
    public *** getDeniedCount();
    public *** getPermanentlyDeniedCount();
    public *** getStatus();
    public *** getFirstDenied();
    public *** getFirstPermanentlyDenied();
    public *** getAllDenied();
    public *** isGranted(java.lang.String);
    public *** isDenied(java.lang.String);
    public *** isPermanentlyDenied(java.lang.String);
}

-keep class com.answufeng.permission.PermissionResult$Status {
    public static *** values();
    public static *** valueOf(java.lang.String);
}

-keep class com.answufeng.permission.PermissionResult$Status$Granted
-keep class com.answufeng.permission.PermissionResult$Status$Denied
-keep class com.answufeng.permission.PermissionResult$Status$PermanentlyDenied

# RationaleStrategy
-keep class com.answufeng.permission.RationaleStrategy {
    public static *** values();
    public static *** valueOf(java.lang.String);
}

# PermissionGroups
-keep class com.answufeng.permission.PermissionGroups {
    public static *** CAMERA;
    public static *** MICROPHONE;
    public static *** CONTACTS;
    public static *** LOCATION;
    public static *** STORAGE;
    public static *** PHONE;
    public static *** SENSORS;
    public static *** SMS;
    public static *** CALENDAR;
    public static *** MEDIA_VISUAL;
    public static *** MEDIA_AUDIO;
    public static *** NOTIFICATIONS;
    public static *** NEARBY_DEVICES;
    public static *** ACTIVITY_RECOGNITION;
    public static *** BACKGROUND_LOCATION;
    public static *** NEARBY_WIFI;
    public static *** MEDIA_PARTIAL;
    public static *** SENSORS_BACKGROUND;
    public static *** storage();
    public static *** location();
}

# PermissionInfo
-keep class com.answufeng.permission.PermissionInfo {
    public static *** getInfo(...);
    public static *** getLabel(...);
    public static *** getDescription(...);
    public static *** getInfos(...);
}

-keep class com.answufeng.permission.PermissionInfo$Info {
    public *** getPermission();
    public *** getLabel();
    public *** getDescription();
    public *** getRiskLevel();
}

-keep class com.answufeng.permission.PermissionInfo$RiskLevel {
    public static *** values();
    public static *** valueOf(java.lang.String);
}

# SpecialPermission
-keep class com.answufeng.permission.SpecialPermission {
    public static *** isGranted(...);
    public static *** openSettings(...);
}

-keep class com.answufeng.permission.SpecialPermission$PermissionType {
    public static *** values();
    public static *** valueOf(java.lang.String);
}

# Extension functions
-keepclassmembers class com.answufeng.permission.PermissionExtKt {
    public static *** hasPermission(...);
    public static *** hasPermissions(...);
    public static *** requestRuntimePermissions(...);
    public static *** requestRuntimePermissionsWithRationale(...);
    public static *** runWithPermissions(...);
    public static *** observePermissions(...);
    public static *** openAppSettingsAndWait(...);
    public static *** checkPermissions(...);
    public static *** buildPermissionRequest(...);
}

# DSL
-keep class com.answufeng.permission.AwPermissionDsl
-keep class com.answufeng.permission.PermissionRequest {
    public *** getPermissions();
    public *** getRationale();
    public *** getStrategy();
}

-keep class com.answufeng.permission.PermissionRequest$Builder {
    public *** permission(...);
    public *** permissions(...);
    public *** permissionGroup(...);
    public *** rationale(...);
    public *** strategy(...);
}

# Kotlin metadata
-keepattributes Signature, *Annotation*
-keep class kotlin.Metadata { *; }
