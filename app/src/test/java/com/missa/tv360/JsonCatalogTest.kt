package com.missa.tv360

import com.missa.tv360.data.json.JsonCatalog
import com.missa.tv360.data.json.JsonSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonCatalogTest {
    private val sourceA = JsonSource("source_a", "Source A", "a.json")

    @Test
    fun emptyIndex_producesAnEmptyCatalog() {
        assertEquals(emptyList<JsonSource>(), JsonCatalog.parseIndex("""{"schema_version":1,"sources":[]}"""))
        assertTrue(JsonCatalog.merge(emptyList()).rows.isEmpty())
        assertTrue(JsonCatalog.merge(emptyList()).games.isEmpty())
    }

    @Test
    fun index_rejectsExternalAndParentPaths() {
        val external = """{"schema_version":1,"sources":[{"id":"a","path":"https://example.test/a.json"}]}"""
        val parent = """{"schema_version":1,"sources":[{"id":"a","path":"../a.json"}]}"""
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { JsonCatalog.parseIndex(external) }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { JsonCatalog.parseIndex(parent) }
    }

    @Test
    fun merge_combinesRowsAndPrefixesItemIds() {
        val first = JsonSource("alpha", "Alpha", "alpha.json")
        val second = JsonSource("beta", "Beta", "beta.json")
        val documentA = JsonCatalog.parseDocument(mediaDocument("same", "https://media.test/a.mp4"))
        val documentB = JsonCatalog.parseDocument(mediaDocument("same", "https://media.test/b.mp4"))
        val merged = JsonCatalog.merge(listOf(first to documentA, second to documentB))
        assertEquals(1, merged.rows.size)
        assertEquals(listOf("alpha:same", "beta:same"), merged.rows.single().videos.map { it.id })
        assertEquals(listOf("Alpha", "Beta"), merged.rows.single().videos.map { it.source })
    }

    @Test
    fun invalidMediaAndUnsafeGames_areNotIntegrated() {
        val document = JsonCatalog.parseDocument(
            """
            {
              "schema_version": 1,
              "rows": [
                {"id":"films","title":"Films","items":[
                  {"id":"no-stream","title":"Sans flux"},
                  {"id":"http","title":"HTTP","stream":{"url":"http://media.test/a.mp4"}}
                ]},
                {"id":"jeux","title":"Jeux","items":[
                  {"id":"bad-hash","title":"Sans empreinte","kind":"game","download_url":"https://media.test/a.apk"}
                ]}
              ]
            }
            """.trimIndent(),
        )
        val merged = JsonCatalog.merge(listOf(sourceA to document))
        assertTrue(merged.rows.isEmpty())
        assertTrue(merged.games.isEmpty())
    }

    @Test
    fun validGame_isMergedWithItsLauncher() {
        val document = JsonCatalog.parseDocument(
            """
            {"schema_version":1,"rows":[{"id":"jeux","title":"Jeux","items":[{
              "id":"game","title":"Jeu","kind":"game",
              "download_url":"https://media.test/game.apk",
              "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            }]}]}
            """.trimIndent(),
        )
        val merged = JsonCatalog.merge(listOf(sourceA to document))
        assertEquals("source_a:game", merged.games.single().games.single().id)
        assertTrue(merged.rows.single().videos.single().isGamesLauncher)
    }

    private fun mediaDocument(id: String, url: String) = """
        {"schema_version":1,"rows":[{"id":"films","title":"Films","items":[{
          "id":"$id","title":"Film","kind":"movie","stream":{"url":"$url","format":"mp4"}
        }]}]}
    """.trimIndent()
}
