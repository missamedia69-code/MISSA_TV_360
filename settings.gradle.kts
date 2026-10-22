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
    }
}

rootProject.name = "MISSA_TV_360"
include(":app")
include(":plugin-api")
// Extension d'exemple : elle est compilée avec l'application pour garantir que
// le contrat plugin-api reste utilisable, mais son archive .msa n'est produite
// que par la tâche `packagePlugin`.
include(":extensions-src:hello-provider")
