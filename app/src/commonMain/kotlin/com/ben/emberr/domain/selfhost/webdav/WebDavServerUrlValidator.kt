package com.ben.emberr.domain.selfhost.webdav

import io.ktor.http.Url

object WebDavServerUrlValidator {

    fun isLocalNetworkHost(host: String): Boolean {
        if (host.equals("localhost", ignoreCase = true)) return true
        if (host == "::1") return true

        val octets = host.split(".").map { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it == null || it !in 0..255 }) return false
        val (first, second) = octets[0]!! to octets[1]!!

        return when {
            first == 127 -> true
            first == 10 -> true
            first == 192 && second == 168 -> true
            first == 172 && second in 16..31 -> true
            else -> false
        }
    }

    fun validate(serverUrl: String) {
        val url = try {
            Url(serverUrl)
        } catch (_: Exception) {
            throw WebDavConfigurationException("Server URL '$serverUrl' is not a valid URL")
        }

        val isHttps = url.protocol.name.equals("https", ignoreCase = true)
        val isPlainHttpOnLocalNetwork = url.protocol.name.equals("http", ignoreCase = true) &&
            isLocalNetworkHost(url.host)

        if (!isHttps && !isPlainHttpOnLocalNetwork) {
            throw WebDavConfigurationException(
                "Server URL '$serverUrl' must use https:// — plain http:// is only permitted for " +
                        "local network addresses such as localhost, 127.0.0.1, or 192.168.x.x"
            )
        }
    }
}