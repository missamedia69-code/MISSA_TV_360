# Les classes sont résolues par PathClassLoader depuis les .cs3 : leurs noms et
# leurs signatures doivent rester identiques même si R8 est activé dans l'app.
-keep class com.lagradost.cloudstream3.** { *; }
-keep class com.missa.tv360.cloudstream.compat.** { *; }

# Les descripteurs du .cs3 traversent aussi ces bibliothèques.
-keep class com.lagradost.nicehttp.** { *; }
-keep interface com.lagradost.nicehttp.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class org.jsoup.** { *; }
-keep class com.fasterxml.jackson.** { *; }
