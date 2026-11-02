package com.peaspilaf.copyparty.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.peaspilaf.copyparty.core.AppState
import com.peaspilaf.copyparty.core.ThemeMode
import com.peaspilaf.copyparty.ui.AppRoot
import com.peaspilaf.copyparty.ui.FakeStorageAccess
import com.peaspilaf.copyparty.ui.UiTags
import com.peaspilaf.copyparty.ui.testViewModel
import com.peaspilaf.copyparty.ui.tap
import com.peaspilaf.copyparty.ui.theme.CopypartyTheme
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

/**
 * The "Add files" promise: documents chosen in the system picker are copied into the app's own
 * share folder - the one folder every build can serve - the result says how many arrived, and an
 * existing file is never silently replaced.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ImportFilesTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val documents = DocumentsProvider()

    private lateinit var shareFolder: File

    @Before
    fun setUp() {
        shareFolder = Files.createTempDirectory("copyparty-import").toFile()
        ShadowContentResolver.registerProviderInternal(AUTHORITY, documents)
    }

    @Test
    fun `choosing documents copies them into the app share folder`() {
        val uri = document("report.txt", "hello report")

        val result = FileImporter(context.contentResolver).import(listOf(uri), shareFolder)

        assertThat(result.failed).isEqualTo(0)
        val copied = result.imported.single()
        assertThat(copied.parentFile?.canonicalPath).isEqualTo(shareFolder.canonicalPath)
        assertThat(copied.name).isEqualTo("report.txt")
        assertThat(copied.readText()).isEqualTo("hello report")
    }

    @Test
    fun `the import reports how many files were added`() {
        val result = FileImporter(context.contentResolver)
            .import(listOf(document("a.txt", "a"), document("b.txt", "b")), shareFolder)

        assertThat(result.imported).hasSize(2)
        assertThat(result.summary).contains("2 files")
    }

    @Test
    fun `importing never overwrites an existing file`() {
        File(shareFolder, "report.txt").writeText("original")

        val result = FileImporter(context.contentResolver)
            .import(listOf(document("report.txt", "new content")), shareFolder)

        val copied = result.imported.single()
        assertThat(copied.name).isNotEqualTo("report.txt")
        assertThat(File(shareFolder, "report.txt").readText()).isEqualTo("original")
        assertThat(copied.readText()).isEqualTo("new content")
    }

    @Test
    fun `a document that cannot be read fails alone and is reported`() {
        val unreadable = Uri.parse("content://nothing-registered-here/missing.txt")

        val result = FileImporter(context.contentResolver)
            .import(listOf(document("kept.txt", "kept"), unreadable), shareFolder)

        assertThat(result.imported.map { it.readText() }).containsExactly("kept")
        assertThat(result.failed).isEqualTo(1)
        assertThat(result.summary).contains("could not be read")
    }

    @Test
    fun `the Add files action opens the system picker for several documents`() {
        compose.setContent {
            CopypartyTheme(themeMode = ThemeMode.LIGHT) {
                AppRoot(testViewModel(storage = FakeStorageAccess(appRoot = shareFolder)))
            }
        }

        compose.tap(UiTags.ImportFiles)
        compose.waitForIdle()

        val started = shadowOf(compose.activity).nextStartedActivityForResult
        assertThat(started).isNotNull()
        assertThat(started.intent.action).isEqualTo(Intent.ACTION_OPEN_DOCUMENT)
        assertThat(started.intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)).isTrue()
    }

    private fun document(name: String, body: String): Uri {
        val uri = Uri.parse("content://$AUTHORITY/$name")
        documents.add(uri, name, body.toByteArray(Charsets.UTF_8))
        shadowOf(context.contentResolver).registerInputStream(uri, body.byteInputStream())
        return uri
    }

    private companion object {
        const val AUTHORITY = "com.peaspilaf.copyparty.test.documents"
    }

    /** The system document provider, as much of it as the importer needs. */
    private class DocumentsProvider : ContentProvider() {
        private val names = mutableMapOf<Uri, String>()
        private val bodies = mutableMapOf<Uri, ByteArray>()

        fun add(uri: Uri, name: String, body: ByteArray) {
            names[uri] = name
            bodies[uri] = body
        }

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME)
            val cursor = MatrixCursor(columns)
            val row = cursor.newRow()
            for (column in columns) {
                row.add(
                    when (column) {
                        OpenableColumns.DISPLAY_NAME -> names[uri] ?: uri.lastPathSegment.orEmpty()
                        OpenableColumns.SIZE -> bodies[uri]?.size ?: 0
                        else -> null
                    },
                )
            }
            return cursor
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val body = bodies[uri] ?: throw FileNotFoundException("no document at $uri")
            val copy = Files.createTempFile("document", ".bin").toFile()
            copy.writeBytes(body)
            return ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun getType(uri: Uri): String = "application/octet-stream"

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }
}
