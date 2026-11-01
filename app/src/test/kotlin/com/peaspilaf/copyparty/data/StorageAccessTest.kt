package com.peaspilaf.copyparty.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Which folders the app may offer for sharing without asking for any permission: a private folder
 * that always exists under the app's own storage, is always reachable from the folder picker, and
 * a picker that lists folders in a predictable order and hides Android's dot-folders.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StorageAccessTest {
    private lateinit var context: Context
    private lateinit var storage: AndroidStorageAccess

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        storage = AndroidStorageAccess(context)
    }

    @Test
    fun `the app share folder is created inside app private storage`() {
        val root = storage.appShareRoot()

        assertThat(root.isDirectory).isTrue()
        assertThat(root.canonicalPath.startsWith(context.filesDir.canonicalPath)).isTrue()
    }

    @Test
    fun `the app share folder comes back after it is deleted`() {
        val root = storage.appShareRoot()
        assertThat(root.deleteRecursively()).isTrue()

        val recreated = storage.appShareRoot()

        assertThat(recreated.isDirectory).isTrue()
        assertThat(recreated.canonicalPath).isEqualTo(root.canonicalPath)
    }

    @Test
    fun `the folder picker can always browse the app share folder`() {
        // TODO(server-runtime implementer): this needs AndroidStorageAccess.hasAllFilesAccess() to
        //  treat an unavailable platform answer as "no All files access". On a JVM test host
        //  Environment.isExternalStorageManager() cannot answer (the platform user environment is
        //  not initialised) and must not crash the folder picker - on a phone the app just falls
        //  back to its private share folder, which is exactly what this test pins down.
        val roots = storage.browseRoots().map { it.canonicalPath }

        assertThat(roots).contains(storage.appShareRoot().canonicalPath)
    }

    @Test
    fun `folders are listed alphabetically ignoring case and hidden ones are skipped`() {
        val dir = File(context.filesDir, "picker").apply { mkdirs() }
        for (name in listOf("beta", "Alpha", "gamma", "zulu", ".hidden")) {
            File(dir, name).mkdirs()
        }
        File(dir, "afile.txt").writeText("not a folder")
        File(dir, ".hiddenfile").writeText("not a folder either")

        val names = storage.listFolders(dir).map { it.name }

        assertThat(names).containsExactly("Alpha", "beta", "gamma", "zulu").inOrder()
    }
}
