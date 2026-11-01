package com.peaspilaf.copyparty.server

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The addresses the app shows after "start server". The phone itself always gets a loopback link,
 * the LAN links only appear when the user allows LAN access, and every one of them is a real
 * `http://ipv4:port` URL that a client on the same network can actually open.
 */
class LocalUrlsTest {
    private val ipv4HttpUrl = Regex("^http://\\d{1,3}(\\.\\d{1,3}){3}:\\d{1,5}$")

    @Test
    fun `the first url is always the phone talking to itself`() {
        assertThat(LocalUrls.serverUrls(3923, lanAccess = false).first())
            .isEqualTo("http://127.0.0.1:3923")
        assertThat(LocalUrls.serverUrls(4646, lanAccess = true).first())
            .isEqualTo("http://127.0.0.1:4646")
    }

    @Test
    fun `a server without lan access exposes exactly one url`() {
        assertThat(LocalUrls.serverUrls(3923, lanAccess = false))
            .containsExactly("http://127.0.0.1:3923")
    }

    @Test
    fun `lan access adds one well formed http ipv4 url per lan address`() {
        val lanAddresses = LocalUrls.lanAddresses()
        val urls = LocalUrls.serverUrls(3923, lanAccess = true)

        assertThat(urls).hasSize(1 + lanAddresses.size)
        assertThat(urls).containsNoDuplicates()
        for (url in urls) {
            assertThat(ipv4HttpUrl.matches(url)).isTrue()
            assertThat(url.endsWith(":3923")).isTrue()
        }
        assertThat(urls.drop(1)).containsExactlyElementsIn(lanAddresses.map { "http://$it:3923" })
    }

    @Test
    fun `lan addresses never include loopback or link local addresses`() {
        val addresses = LocalUrls.lanAddresses()

        assertThat(addresses).containsNoDuplicates()
        for (address in addresses) {
            assertWithMessage("loopback address leaked into the share urls: $address")
                .that(address.startsWith("127."))
                .isFalse()
            assertWithMessage("link local address leaked into the share urls: $address")
                .that(address.startsWith("169.254."))
                .isFalse()
            assertThat(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(address)).isTrue()
        }
    }
}
