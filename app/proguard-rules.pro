# Add project specific ProGuard rules here.
-keep class com.diev.mabohao.hook.** { *; }
-keep class com.diev.mabohao.data.** { *; }

# Java 8 reflection classes
-dontwarn java.lang.reflect.AnnotatedType

# YukiHookAPI
-keep class com.highcapable.yukihookapi.** { *; }

# KavaRef
-keep class com.highcapable.kavaref.** { *; }

# Xposed
-keep class de.robv.android.xposed.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer