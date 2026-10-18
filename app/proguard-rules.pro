# Règles ProGuard/R8 — MISSA TV 360
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class com.missa.tv360.data.remote.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn org.conscrypt.**
