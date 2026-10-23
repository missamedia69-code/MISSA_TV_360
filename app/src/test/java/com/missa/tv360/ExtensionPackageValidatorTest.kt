package com.missa.tv360

import com.missa.tv360.extensions.ExtensionException
import com.missa.tv360.extensions.ExtensionPackageValidator
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExtensionPackageValidatorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `paquet Cloudstream avec manifeste et DEX est accepte`() {
        val file = archive(
            manifest = """{"pluginClassName":"com.lagradost.FrenchPlugin","requiresResources":true}""",
            dex = byteArrayOf('d'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte(), '\n'.code.toByte(), 0, 0, 0, 0),
        )

        val manifest = ExtensionPackageValidator.validate(file)

        assertEquals("com.lagradost.FrenchPlugin", manifest.pluginClassName)
        assertTrue(manifest.requiresResources)
    }

    @Test(expected = ExtensionException::class)
    fun `paquet sans classes dex est refuse`() {
        val file = temporaryFolder.newFile("missing-dex.cs3")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("""{"pluginClassName":"com.example.Plugin"}""".toByteArray())
            zip.closeEntry()
        }

        ExtensionPackageValidator.validate(file)
    }

    @Test(expected = ExtensionException::class)
    fun `classe de manifeste absente est refusee`() {
        val file = archive(manifest = """{"name":"Incomplet"}""")

        ExtensionPackageValidator.validate(file)
    }

    private fun archive(
        manifest: String,
        dex: ByteArray = byteArrayOf('d'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte(), '\n'.code.toByte(), 0, 0, 0, 0),
    ): File {
        val file = temporaryFolder.newFile("plugin-${System.nanoTime()}.cs3")
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
}
