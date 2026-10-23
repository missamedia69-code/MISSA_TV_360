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
        // NiceHttp est publié sur JitPack ; aucune bibliothèque Cloudstream complète
        // ni snapshot mouvante n'est utilisée par le module de compatibilité.
        maven("https://jitpack.io")
    }
}

rootProject.name = "MISSA_TV_360"
include(":app")
include(":plugin-api")
include(":cloudstream-compat")
