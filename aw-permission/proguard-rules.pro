# aw-permission ProGuard Rules
# 此文件用于库自身的 release 构建混淆规则
# Consumer-facing rules（供使用者混淆时使用）位于 consumer-rules.pro

# ===========================================================
# 保留公共 API
# ===========================================================

# 保留所有公共类
-keep class com.answufeng.permission.** { *; }

# 保留 Activity 和 Fragment 相关类（用于权限回调）
-keep class android.app.Activity { *; }
-keep class androidx.fragment.app.Fragment { *; }

# 保留 Kotlin 反射和元数据
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes Exceptions
-keep class kotlin.Metadata { *; }

# ===========================================================
# 保留枚举和 sealed class
# ===========================================================

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * {
    @kotlin.Metadata *;
}

# ===========================================================
# 保留 View 构造函数（自定义 View 必须在 XML 中能正常实例化）
# ===========================================================

-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
