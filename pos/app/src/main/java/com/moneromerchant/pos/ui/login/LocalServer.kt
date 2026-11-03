package com.moneromerchant.pos.ui.payment.login

import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException

internal fun isLocalServer(
    instanceUrl: String,
    resolve: (String) -> Array<InetAddress> = InetAddress::getAllByName,
): Boolean {
    val host = runCatching { URI(instanceUrl).host }.getOrNull()
        ?.removePrefix("[")?.removeSuffix("]")?.trimEnd('.') ?: return false
    // Local name resolution itself may be blocked until permission is granted.
    if (host.endsWith(".local", ignoreCase = true)) return true

    val addresses = try {
        resolve(host)
    } catch (_: UnknownHostException) {
        // Let login report the DNS failure instead of asking for unrelated permission.
        return false
    }
    return addresses.any { address ->
        val bytes = address.address
        // InetAddress.isSiteLocalAddress does not include IPv6 unique-local addresses.
        val uniqueLocal = bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
        !address.isLoopbackAddress && (address.isSiteLocalAddress || address.isLinkLocalAddress || uniqueLocal)
    }
}
