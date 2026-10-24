# Règles ProGuard/R8 — MISSA TV 360
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
# DTO Gson du chargeur JSON et modèles du cache agrégé.
-keep class com.missa.tv360.data.json.** { *; }
-keep class com.missa.tv360.data.Video* { *; }
-keep class com.missa.tv360.data.Game* { *; }
-keep class com.missa.tv360.data.ContentKind { *; }
-keep class com.missa.tv360.data.CardShapeHint { *; }

# ── Extensions ────────────────────────────────────────────────────────────
# Le contrat plugin <-> application est chargé par le classloader parent du
# plugin : ses noms ne doivent JAMAIS être obfusqués, sinon les extensions
# publiées ne résolvent plus leurs types au chargement.
-keep class com.missa.tv360.api.** { *; }
-keep interface com.missa.tv360.api.** { *; }
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Instanciation par réflexion de la classe déclarée dans manifest.json :
# on conserve le constructeur sans argument de tout MissaPlugin.
-keepclassmembers class * extends com.missa.tv360.api.MissaPlugin {
    public <init>();
}
-keepnames class * implements com.missa.tv360.api.Provider

# Prototype Cloudstream : les .cs3 résolvent ces classes par leurs noms binaires.
-keep class com.lagradost.cloudstream3.** { *; }
-keep interface com.lagradost.cloudstream3.** { *; }
-keepclassmembers class * extends com.lagradost.cloudstream3.plugins.BasePlugin {
    public <init>();
}
-keep class com.lagradost.nicehttp.** { *; }
-keep interface com.lagradost.nicehttp.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class org.jsoup.** { *; }
-keep class com.fasterxml.jackson.** { *; }

# --- Extensions Cloudstream : API chargee dynamiquement ---
-keep class com.lagradost.cloudstream3.** { *; }
-keep interface com.lagradost.cloudstream3.** { *; }
-keepclassmembers class com.lagradost.cloudstream3.** { *; }
-dontwarn com.lagradost.cloudstream3.**
-keep class kotlinx.serialization.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class com.lagradost.nicehttp.** { *; }
-keep class org.jsoup.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations
-dontwarn kotlinx.serialization.**
