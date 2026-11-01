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
            .mapNotNull { it.hostAddress }
            .filterNot { it.startsWith("169.254.") }
            .distinct()
    }

    fun serverUrls(port: Int, lanAccess: Boolean): List<String> {
        val urls = mutableListOf("http://127.0.0.1:$port")
        if (lanAccess) {
            for (address in lanAddresses()) urls += "http://$address:$port"
        }
        return urls
    }
}
