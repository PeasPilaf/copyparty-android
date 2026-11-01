package com.peaspilaf.copyparty.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The renderer is the last place a value can be changed on its way into copyparty.conf, so a value
 * must be copied through unchanged and the file has to stay a well-formed one-value-per-line file
 * at the very end.
 *
 * [ConfigRendererTest] pins the exact bytes of a whole file, but none of its values ends in
 * whitespace and every one of its volumes has an `accs:` line after the folder path, so it cannot
 * see either property.
 */
class ConfigRendererValuesTest {
    @Test
    fun `a folder path at the end of the file keeps its trailing space`() {
        // A share nobody was granted has no accs: line, so its folder path is the last line and is the
        // value a blanket trimEnd() would eat. The renderer must copy a value through untouched. Note
        // that copyparty trims config values itself when it parses them (checked against 1.20.26), so
        // a folder whose name really ends in a space cannot be served from this or any config; that is
        // a Validator question, not something the renderer may paper over by rewriting the value.
        val conf = ConfigRenderer.render(
            AppState(
                users = listOf(User("alice", "hunter2")),
                shares = listOf(
                    Share(
                        id = "s1",
                        name = "Spaced",
                        urlPath = "spaced",
                        localPath = "/storage/emulated/0/My Photos ",
                        grants = listOf(Grant(Grant.EVERYONE, emptySet())),
                    ),
                ),
            ),
        )

        assertThat(conf).contains("  /storage/emulated/0/My Photos \n")
        assertThat(conf.lines()).contains("  /storage/emulated/0/My Photos ")
    }

    @Test
    fun `every file ends with exactly one newline`() {
        val states = listOf(
            AppState(),
            AppState(users = listOf(User("alice", "hunter2"))),
            AppState(
                shares = listOf(Share("s1", "Photos", "photos", "/storage/emulated/0/DCIM")),
            ),
            AppState(
                users = listOf(User("alice", "hunter2")),
                shares = listOf(
                    Share(
                        id = "s1",
                        name = "Shared",
                        urlPath = "shared",
                        localPath = "/storage/emulated/0/Pictures",
                        grants = listOf(Grant("alice", setOf(Permission.VIEW, Permission.UPLOAD))),
                    ),
                ),
            ),
        )

        for (state in states) {
            val conf = ConfigRenderer.render(state)
            assertThat(conf.endsWith("\n")).isTrue()
            assertThat(conf.endsWith("\n\n")).isFalse()
        }
    }
}
