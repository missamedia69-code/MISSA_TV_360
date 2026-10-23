package com.missa.tv360.extensions

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.missa.tv360.cloudstream.compat.CloudstreamCompatRegistry
import com.missa.tv360.api.Provider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Porte P3 : charge le vrai BuzzMonclick.cs3 sur Android, sans fixture Kotlin
 * réécrite et sans simuler la réponse du site. La fixture est téléchargée et
 * vérifiée par le workflow, elle n'est pas commitée dans le dépôt.
 */
@RunWith(AndroidJUnit4::class)
class CloudstreamDeviceTest {

    @Test
    fun buzzMonclickChargeEtPublieUneRangeeReelle() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val artifact = File(target.filesDir, "p3-probe/BuzzMonclick.cs3")
        artifact.parentFile?.mkdirs()
        instrumentation.context.assets.open("BuzzMonclick.cs3").use { input ->
            artifact.outputStream().use { output -> input.copyTo(output) }
        }

        val record = InstalledExtension(
            internalName = "p3-buzzmonclick-probe",
            repositoryUrl = "https://github.com/zzikozz/frenchCS",
            name = "BuzzMonclick",
            version = 1,
            filePath = artifact.absolutePath,
            apiVersion = 0,
        )
        var provider: Provider? = null
        val scenario = ActivityScenario.launch(ExtensionsActivity::class.java)
        try {
            scenario.onActivity { activity ->
                // Le test passe volontairement par le chargeur et l'activité
                // AppCompat réels : le cast de BuzzMonclick est ainsi couvert.
                ExtensionLoader.load(activity, record)
                provider = CloudstreamCompatRegistry.providers()
                    .firstOrNull { it.name == "BuzzMonclick" }
            }

            val loadedProvider = provider
            assertNotNull("Aucun MainAPI BuzzMonclick enregistré", loadedProvider)
            val rows = runBlocking {
                withTimeout(60_000L) { loadedProvider!!.rows() }
            }
            val items = rows.flatMap { it.items }
            assertTrue(
                "BuzzMonclick chargé, mais aucune rangée réelle n'a été produite",
                rows.isNotEmpty() && items.isNotEmpty(),
            )
            assertTrue(
                "Une page Cloudstream ne doit jamais devenir un flux",
                items.all { it.stream.url.isBlank() && !it.pageUrl.isNullOrBlank() },
            )
        } finally {
            ExtensionLoader.unload(record.internalName)
            scenario.close()
            artifact.delete()
        }
    }
}
