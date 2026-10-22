// Extension d'exemple — sert de référence pour toute extension MISSA TV 360.
//
// Produit `build/plugin/hello-provider.msa` :
//   - `manifest.json` : métadonnées lues par l'application au chargement ;
//   - `classes.dex`   : le code Kotlin compilé, chargé par PathClassLoader.
//
// Commande :
//   ./gradlew :extensions-src:hello-provider:packagePlugin
//
// Prérequis : JDK 17 et Android SDK (la commande `d8` des build-tools sert à
// convertir le JAR en DEX). `ANDROID_HOME` ou `ANDROID_SDK_ROOT` doit pointer
// sur le SDK.
import groovy.json.JsonOutput
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// ── Identité de l'extension ────────────────────────────────────────────────
// Ces valeurs sont recopiées dans le manifeste de l'archive ET dans les
// métadonnées publiées par extension-tools/build_extension_repo.py.
val pluginId = "hello-provider"
val pluginName = "Démo — films libres"
val pluginVersion = 1
val pluginClassName = "com.missa.extensions.hello.HelloPlugin"
val pluginDescription = "Extension d'exemple : trois films libres (Blender Foundation)."
val pluginAuthors = listOf("MISSA TV 360")
val pluginLanguage = "fr"
val pluginTvTypes = listOf("Movies")

dependencies {
    // Fourni par l'application à l'exécution : ne doit JAMAIS être embarqué,
    // sinon le plugin aurait sa propre copie du contrat et l'enregistrement
    // des providers échouerait silencieusement.
    compileOnly(project(":plugin-api"))
}

val pluginOutputDir = layout.buildDirectory.dir("plugin")

val packagePlugin by tasks.registering {
    group = "distribution"
    description = "Construit l'archive .msa publiée dans le dépôt d'extensions"
    dependsOn(tasks.named("jar"))

    doLast {
        val outDir = pluginOutputDir.get().asFile.apply { mkdirs() }
        val jarFile = tasks.named<Jar>("jar").get().archiveFile.get().asFile

        val d8 = locateD8()
        val dexDir = outDir.resolve("dex").apply { mkdirs() }
        dexDir.listFiles()?.forEach { it.delete() }

        val process = ProcessBuilder(
            d8.absolutePath,
            "--min-api", "21",
            "--output", dexDir.absolutePath,
            jarFile.absolutePath,
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        if (exit != 0) {
            throw GradleException("d8 a échoué (code $exit) :\n$output")
        }

        val dexFile = File(dexDir, "classes.dex")
        if (!dexFile.isFile) {
            throw GradleException("d8 n'a pas produit classes.dex dans ${dexDir.absolutePath}")
        }

        val manifest = JsonOutput.prettyPrint(
            JsonOutput.toJson(
                mapOf(
                    "name" to pluginName,
                    "version" to pluginVersion,
                    "apiVersion" to 1,
                    "pluginClassName" to pluginClassName,
                    "requiresResources" to false,
                )
            )
        )

        val archive = File(outDir, "$pluginId.msa")
        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("classes.dex"))
            dexFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }

        val metadata = JsonOutput.prettyPrint(
            JsonOutput.toJson(
                mapOf(
                    "internalName" to pluginId,
                    "name" to pluginName,
                    "version" to pluginVersion,
                    "apiVersion" to 1,
                    "status" to 1,
                    "authors" to pluginAuthors,
                    "description" to pluginDescription,
                    "language" to pluginLanguage,
                    "tvTypes" to pluginTvTypes,
                )
            )
        )
        File(outDir, "$pluginId.json").writeText(metadata, Charsets.UTF_8)

        logger.lifecycle("Plugin empaqueté : ${archive.absolutePath} (${archive.length()} o)")
        logger.lifecycle("Métadonnées    : ${File(outDir, "$pluginId.json").absolutePath}")
    }
}

/** Trouve `d8` dans les build-tools du SDK Android. */
fun locateD8(): File {
    val sdk = sequenceOf(
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
        project.findProperty("sdk.dir") as String?,
        System.getProperty("user.home")?.let { "$it/Android/Sdk" },
    ).firstOrNull { !it.isNullOrBlank() && File(it).isDirectory }
        ?: throw GradleException(
            "SDK Android introuvable : définissez ANDROID_HOME (d8 est requis pour produire le DEX)."
        )

    val buildTools = File(sdk, "build-tools")
    val d8 = buildTools.listFiles()
        ?.filter { it.isDirectory }
        ?.sortedByDescending { it.name }
        ?.map { File(it, "d8") }
        ?.firstOrNull { it.isFile && it.canExecute() }

    return d8 ?: throw GradleException(
        "d8 introuvable dans ${buildTools.absolutePath} (installez des build-tools)."
    )
}
