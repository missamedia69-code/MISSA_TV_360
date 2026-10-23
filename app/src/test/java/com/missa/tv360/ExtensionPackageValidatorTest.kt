package com.missa.tv360

import com.missa.tv360.extensions.ExtensionErrorType
import com.missa.tv360.extensions.ExtensionException
import com.missa.tv360.extensions.ExtensionPackageValidator
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExtensionPackageValidatorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `preflight accepte un paquet natif structurellement valide`() {
        val file = archive(
            suffix = "msa",
            manifest = """{"pluginClassName":"com.missa.hello.HelloPlugin","apiVersion":1}""",
        )

        val manifest = ExtensionPackageValidator.validateNative(file)

        assertEquals("com.missa.hello.HelloPlugin", manifest.pluginClassName)
        assertEquals(1, manifest.apiVersion)
    }

    @Test
    fun `faux cs3 est reconnu avant le chargement avec sa dependance exacte`() {
        val file = archive(
            manifest = """{"pluginClassName":"com.lagradost.BuzzMonclickProviderPlugin"}""",
            dex = dexMagic + "Lcom/lagradost/cloudstream3/plugins/Plugin;".toByteArray(),
        )

        val error = assertThrows(ExtensionException::class.java) {
            ExtensionPackageValidator.validateNative(file)
        }

        assertEquals(ExtensionErrorType.MISSING_DEPENDENCY, error.type)
        assertTrue(error.message.orEmpty().contains("com.lagradost.cloudstream3.plugins.Plugin"))
        assertTrue(error.technicalDetails.orEmpty().contains("BuzzMonclickProviderPlugin"))
    }

    @Test
    fun `archive corrompue produit BAD MANIFEST`() {
        val file = temporaryFolder.newFile("corrupt.msa")
        file.writeBytes("ceci n'est pas un zip".toByteArray())

        val error = assertThrows(ExtensionException::class.java) {
            ExtensionPackageValidator.validateNative(file)
        }

        assertEquals(ExtensionErrorType.BAD_MANIFEST, error.type)
    }

    @Test
    fun `API native incompatible est distincte d'une dependance absente`() {
        val file = archive(
            suffix = "msa",
            manifest = """{"pluginClassName":"com.missa.hello.HelloPlugin","apiVersion":2}""",
        )

        val error = assertThrows(ExtensionException::class.java) {
            ExtensionPackageValidator.validateNative(file)
        }

        assertEquals(ExtensionErrorType.INCOMPATIBLE, error.type)
    }

    @Test
    fun `structure cs3 reste acceptee par l'installateur`() {
        val file = archive(
            manifest = """{"pluginClassName":"com.lagradost.FrenchPlugin","requiresResources":true}""",
            dex = dexMagic + "Lcom/lagradost/cloudstream3/MainAPI;".toByteArray(),
        )

        val manifest = ExtensionPackageValidator.validate(file)

        assertEquals("com.lagradost.FrenchPlugin", manifest.pluginClassName)
        assertTrue(manifest.requiresResources)
        assertEquals("com.lagradost.cloudstream3.MainAPI", ExtensionPackageValidator.cloudstreamDependency(file))
    }

    @Test
    fun `paquet sans classes dex produit BAD MANIFEST`() {
        val file = temporaryFolder.newFile("missing-dex.cs3")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("""{"pluginClassName":"com.example.Plugin"}""".toByteArray())
            zip.closeEntry()
        }

        val error = assertThrows(ExtensionException::class.java) {
            ExtensionPackageValidator.validate(file)
        }
        assertEquals(ExtensionErrorType.BAD_MANIFEST, error.type)
    }

    @Test
    fun `classe de manifeste absente produit BAD MANIFEST`() {
        val file = archive(manifest = """{"name":"Incomplet"}""")

        val error = assertThrows(ExtensionException::class.java) {
            ExtensionPackageValidator.validate(file)
        }
        assertEquals(ExtensionErrorType.BAD_MANIFEST, error.type)
    }

    private fun archive(
        manifest: String,
        dex: ByteArray = dexMagic,
        suffix: String = "cs3",
    ): File {
        val file = temporaryFolder.newFile("plugin-${System.nanoTime()}.$suffix")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write(dex)
            zip.closeEntry()
        }
        return file
    }

    private companion object {
        val dexMagic = byteArrayOf(
            'd'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte(), '\n'.code.toByte(),
            0, 0, 0, 0,
        )
    }
}
