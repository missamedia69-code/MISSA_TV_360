// Fichier racine — build des plugins (appliqués dans :app)
plugins {
    id("com.android.application") version "8.7.3" apply false
    // AllMovieLand référence SpillingKt, introduit par le stdlib Kotlin 2.1.
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.jvm") version "2.1.0" apply false
}
