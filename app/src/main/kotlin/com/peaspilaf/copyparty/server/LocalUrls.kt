package com.peaspilaf.copyparty.server

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/** The URLs a phone or laptop on the same network can use to reach this device. */
object LocalUrls {
    fun lanAddresses(): List<String> {
        val nics = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        return Collections.list(nics)
            .filter { nic -> nic.isUp && !nic.isLoopback }
            .flatMap { nic -> Collections.list(nic.inetAddresses) }
            .filterIsInstance<Inet4Address>()
            // The interface check above is not enough on every platform: filter the addresses
            // themselves so a loopback or link-local address can never be offered to a client.
            .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress }
            .mapNotNull { it.hostAddress }
            .distinct()
    }

    fun serverUrls(port: Int, lanAccess: Boolean): List<String> {
        val urls = mutableListOf("http://127.0.0.1:$port")
        if (lanAccess) {
            for (address in lanAddresses()) urls += "http://$address:$port"
        }
        return urls
    }

    /**
     * The one URL to hand to *another* device - the notification's address and the QR code's link.
     *
     * [serverUrls] deliberately puts the phone talking to itself first (the phone's own browser and
     * the in-app file screen use it), but a loopback link is useless to a laptop or a friend's phone.
     * So the address a client is given is the first LAN URL when there is one, and loopback only when
     * there is not (LAN access off, or no network interface yet).
     */
    fun clientUrl(urls: List<String>, port: Int): String =
        urls.firstOrNull { !it.contains("127.0.0.1") }
            ?: urls.firstOrNull()
            ?: "http://127.0.0.1:$port"
}
