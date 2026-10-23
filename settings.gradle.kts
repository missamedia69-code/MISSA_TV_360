pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Bibliothèque d'exécution officielle des extensions Cloudstream.
        maven("https://jitpack.io")
    }
}

rootProject.name = "MISSA_TV_360"
include(":app")
include(":plugin-api")
