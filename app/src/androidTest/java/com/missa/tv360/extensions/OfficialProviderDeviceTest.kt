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

/** Sondes séparées des providers officiels, sans réponse ni contenu simulé. */
@RunWith(AndroidJUnit4::class)
class OfficialProviderDeviceTest {

    @Test
    fun invidiousChargeEtPublieUneRangeeReelle() = probe(
        ProviderProbe(
            key = "invidious",
            displayName = "Invidious",
            assetName = "InvidiousProvider.cs3",
            version = 9,
            probeDefaultPage = true,
        ),
    )

    @Test
    fun twitchChargeEtPublieUneRangeeReelle() = probe(
        ProviderProbe(
            key = "twitch",
            displayName = "Twitch",
            assetName = "TwitchProvider.cs3",
            version = 2,
        ),
    )

    private fun probe(config: ProviderProbe) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val artifact = File(target.filesDir, "provider-probe/${config.assetName}")
        artifact.parentFile?.mkdirs()
        instrumentation.context.assets.open(config.assetName).use { input ->
            artifact.outputStream().use { output -> input.copyTo(output) }
        }

        val record = InstalledExtension(
            internalName = "diagnostic-${config.key}-probe",
            repositoryUrl = "https://github.com/recloudstream/extensions",
            name = "${config.displayName}Provider",
            version = config.version,
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
                    throw AssertionError(
                        "Chargement ${config.displayName} échoué$category : ${describe(error)}",
                    ).apply { initCause(error) }
                }
                provider = CloudstreamCompatRegistry.providers()
                    .firstOrNull { it.name.contains(config.displayName, ignoreCase = true) }
            }

            val loadedProvider = provider
            assertNotNull(
                "Aucun MainAPI ${config.displayName} enregistré : " +
                    CloudstreamCompatRegistry.providers().joinToString { it.name },
                loadedProvider,
            )
            val api = CloudstreamCompatRegistry.providerFor(loadedProvider!!.id)
                ?: error("MainAPI introuvable après enregistrement")
            val rows = try {
                runBlocking { withTimeout(60_000L) { loadedProvider!!.rows() } }
            } catch (error: Throwable) {
                throw AssertionError(
                    "${config.displayName} chargé, mais rows() a échoué : ${describe(error)}. " +
                        diagnosePages(api, config.probeDefaultPage),
                ).apply { initCause(error) }
            }
            val items = rows.flatMap { it.items }
            if (rows.isEmpty() || items.isEmpty()) {
                throw AssertionError(
                    "${config.displayName} chargé, mais aucune rangée réelle. " +
                        diagnosePages(api, config.probeDefaultPage),
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

    private fun diagnosePages(api: MainAPI, probeDefaultPage: Boolean): String {
        if (api.mainPage.isEmpty() && (!probeDefaultPage || !api.hasMainPage)) {
            return "Aucune mainPage déclarée ; hasMainPage=${api.hasMainPage}."
        }
        return runBlocking {
            if (api.mainPage.isEmpty()) {
                val diagnostic = diagnoseRequest(
                    api,
                    "page directe sans mainPage",
                    MainPageRequest(api.name, "", false),
                )
                return@runBlocking "Aucune mainPage déclarée ; hasMainPage=true. $diagnostic"
            }
            val diagnostics = mutableListOf<String>()
            for (page in api.mainPage.take(2)) {
                diagnostics += diagnoseRequest(
                    api,
                    page.name,
                    MainPageRequest(page.name, page.data, page.horizontalImages),
                )
            }
            diagnostics.joinToString(" | ")
        }
    }

    private suspend fun diagnoseRequest(
        api: MainAPI,
        label: String,
        request: MainPageRequest,
    ): String = try {
        val response = withTimeout(20_000L) { api.getMainPage(1, request) }
        val responses = response?.items.orEmpty()
        val first = responses.flatMap { it.list }.firstOrNull()
        "$label: réponse=${responses.map { it.list.size }}, " +
            "premier=${first?.name}, url=${first?.url}"
    } catch (error: Throwable) {
        "$label: ${describe(error)}".take(1_200)
    }

    private fun describe(error: Throwable): String {
        val root = generateSequence(error) { it.cause }.take(12).last()
        return "${root.javaClass.name}: ${root.message}"
    }

    private data class ProviderProbe(
        val key: String,
        val displayName: String,
        val assetName: String,
        val version: Int,
        val probeDefaultPage: Boolean = false,
    )
}
