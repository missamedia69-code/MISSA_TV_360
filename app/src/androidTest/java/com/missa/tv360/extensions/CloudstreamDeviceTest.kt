package com.missa.tv360.extensions

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
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
            if (rows.isEmpty() || items.isEmpty()) {
                val api = CloudstreamCompatRegistry.providerFor(loadedProvider!!.id)
                    ?: error("MainAPI introuvable après enregistrement")
                throw AssertionError(
                    "BuzzMonclick chargé, mais aucune rangée réelle. " + diagnosePages(api),
                )
            }
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

    private fun diagnosePages(api: MainAPI): String = runBlocking {
        val diagnostics = mutableListOf<String>()
        for (page in api.mainPage.take(2)) {
            val diagnostic = try {
                val response = withTimeout(20_000L) {
                    api.getMainPage(
                        1,
                        MainPageRequest(page.name, page.data, page.horizontalImages),
                    )
                }
                val responses = response?.items.orEmpty()
                val first = responses.flatMap { it.list }.firstOrNull()
                "${page.name}: réponse=${responses.map { it.list.size }}, " +
                    "premier=${first?.name}, url=${first?.url}"
            } catch (error: Throwable) {
                val root = generateSequence(error) { it.cause }.last()
                "${page.name}: ${root.javaClass.name}: ${root.message}".take(1_200)
            }
            diagnostics += diagnostic
        }
        diagnostics.joinToString(" | ")
    }
}
