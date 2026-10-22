# Règles ProGuard/R8 — MISSA TV 360
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class com.missa.tv360.data.remote.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn org.conscrypt.**

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
