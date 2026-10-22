// Contrat binaire partagé entre l'application et les extensions.
//
// Ce module ne contient AUCUN code Android et AUCUNE dépendance externe :
//  - l'application l'embarque (implementation(project(":plugin-api"))) ;
//  - une extension le compile en `compileOnly` et n'embarque donc pas ces
//    classes dans son .msa : elles sont résolues à l'exécution par le
//    classloader de l'application (parent du PathClassLoader du plugin).
//
// Conséquence : toute modification incompatible de ce module exige
// d'incrémenter PLUGIN_API_VERSION et de reconstruire les extensions.
plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}
