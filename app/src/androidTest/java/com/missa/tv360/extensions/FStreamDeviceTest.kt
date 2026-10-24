package com.missa.tv360.extensions

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.missa.tv360.api.Provider
import com.missa.tv360.cloudstream.compat.CloudstreamCompatRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Sonde supplémentaire avec le vrai FStream.cs3. Elle ne valide ni BuzzMonclick
 * ni P4 : le binaire et les réponses réseau ne sont pas remplacés par des fixtures
 * Kotlin ou des cartes synthétiques.
 */
@RunWith(AndroidJUnit4::class)
class FStreamDeviceTest {

    @Test
    fun fStreamChargeEtPublieUneRangeeReelle() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val artifact = File(target.filesDir, "p3-probe/FStream.cs3")
        artifact.parentFile?.mkdirs()
        instrumentation.context.assets.open("FStream.cs3").use { input ->
            artifact.outputStream().use { output -> input.copyTo(output) }
        }

        val record = InstalledExtension(
            internalName = "p3-fstream-supplemental-probe",
            repositoryUrl = "https://git.disroot.org/ayza/FStream",
            name = "FStream",
            version = 16,
            filePath = artifact.absolutePath,
            apiVersion = 1,
        )
        var provider: Provider? = null
        val scenario = ActivityScenario.launch(ExtensionsActivity::class.java)
        try {
            scenario.onActivity { activity ->
                try {
                    ExtensionLoader.load(activity, record)
                } catch (error: Throwable) {
                    val category = (error as? ExtensionException)?.type?.let { " [$it]" }.orEmpty()
                    throw AssertionError("Chargement FStream échoué$category : ${describe(error)}")
                        .apply { initCause(error) }
                }
                provider = CloudstreamCompatRegistry.providers()
                    .firstOrNull { it.name.equals("FStream", ignoreCase = true) }
            }

            val loadedProvider = provider
            assertNotNull(
                "Aucun MainAPI FStream enregistré : " +
                    CloudstreamCompatRegistry.providers().joinToString { it.name },
                loadedProvider,
            )
            val api = CloudstreamCompatRegistry.providerFor(loadedProvider!!.id)
                ?: error("MainAPI introuvable après enregistrement")
            val rows = try {
                runBlocking { withTimeout(60_000L) { loadedProvider!!.rows() } }
            } catch (error: Throwable) {
                throw AssertionError(
                    "FStream chargé, mais rows() a échoué : ${describe(error)}. " +
                        diagnosePages(api),
                ).apply { initCause(error) }
            }
            val items = rows.flatMap { it.items }
            if (rows.isEmpty() || items.isEmpty()) {
                throw AssertionError(
                    "FStream chargé, mais aucune rangée réelle. " + diagnosePages(api),
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

    private fun diagnosePages(api: MainAPI): String {
        if (api.mainPage.isEmpty()) {
            return "Aucune mainPage déclarée ; hasMainPage=${api.hasMainPage}."
        }
        return runBlocking {
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
                    "${page.name}: ${describe(error)}".take(1_200)
                }
                diagnostics += diagnostic
            }
            diagnostics.joinToString(" | ")
        }
    }

    private fun describe(error: Throwable): String {
        val root = generateSequence(error) { it.cause }.take(12).last()
        return "${root.javaClass.name}: ${root.message}"
    }
}
