# Règles ProGuard/R8 — MISSA TV 360
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# DTO Gson du chargeur JSON et modèles du cache agrégé.
-keep class com.missa.tv360.data.json.** { *; }
-keep class com.missa.tv360.data.Video* { *; }
-keep class com.missa.tv360.data.Game* { *; }
-keep class com.missa.tv360.data.ContentKind { *; }
-keep class com.missa.tv360.data.CardShapeHint { *; }

# ── Extensions & Contrat ──────────────────────────────────────────────────
-keep class com.missa.tv360.api.** { *; }
-keep interface com.missa.tv360.api.** { *; }

-keepclassmembers class * extends com.missa.tv360.api.MissaPlugin {
    public <init>();
}
-keepnames class * implements com.missa.tv360.api.Provider
-keepnames class * implements com.missa.tv360.api.ResolvingProvider

# ── Cloudstream Compatibility Layer & Plugins ──────────────────────────────
-keep class com.lagradost.cloudstream3.** { *; }
-keep interface com.lagradost.cloudstream3.** { *; }
-keepclassmembers class * extends com.lagradost.cloudstream3.plugins.BasePlugin {
    public <init>();
}
-keep class com.lagradost.nicehttp.** { *; }
-keep class okhttp3.** { *; }
-keep class org.jsoup.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class kotlinx.serialization.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }

-dontwarn com.lagradost.cloudstream3.**
-dontwarn kotlinx.serialization.**
-dontwarn com.oracle.svm.**
-dontwarn org.graalvm.**
-dontwarn java.beans.**
-dontwarn okhttp3.**
-dontwarn com.fasterxml.jackson.**
-dontwarn org.jsoup.**
