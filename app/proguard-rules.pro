# 保留 OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# 保留 Okio
-dontwarn okio.**
-keep class okio.** { *; }

# 保留 Kotlin 反射
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# 保留数据类（用于 JSON 序列化）
-keep class com.example.iot.model.** { *; }

# 保留 WebSocket 回调
-keep class com.example.iot.WebSocketManager { *; }
-keepclassmembers class com.example.iot.WebSocketManager {
    *;
}

# 保留所有 Activity 和 Fragment
-keep public class com.example.iot.** extends android.app.Activity
-keep public class com.example.iot.** extends androidx.fragment.app.Fragment
-keep public class com.example.iot.** extends android.app.Service

# 保留自定义 View
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 保留 native 方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留自定义控件的无参构造
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# 保留枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留 Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# 保留 Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 移除日志（发布版本）
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# 保留 LazySodium（Curve25519 密钥交换）
-keep class com.goterl.lazysodium.** { *; }
-keep class com.goterl.lazysodium.interfaces.** { *; }
-keep class com.goterl.lazysodium.utils.** { *; }

# 保留 JNA（LazySodium 内部依赖）
-keep class com.sun.jna.** { *; }
-keep class com.sun.jna.ptr.** { *; }
-dontwarn com.sun.jna.**

# 保留 Protobuf 消息类（ProtoDecoder 使用反射）
-keep class com.example.iot.ble.proto.** { *; }
