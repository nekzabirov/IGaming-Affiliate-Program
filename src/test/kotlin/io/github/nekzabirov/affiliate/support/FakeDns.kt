package io.github.nekzabirov.affiliate.support

import java.net.InetAddress
import java.net.UnknownHostException

/** The names the tests resolve, so no test depends on the internet's DNS. */
object FakeDns {

    const val TRACKER = "tracker.example.com"

    /** A public name that resolves to a private address: what DNS rebinding would look like at send time. */
    const val INTERNAL = "internal.example.com"

    /** One public address and one loopback address: a single bad address blocks the host. */
    const val MIXED = "mixed.example.com"

    const val NOWHERE = "nowhere.example.com"

    private val records = mapOf(
        TRACKER to listOf("203.0.113.10"),
        INTERNAL to listOf("10.0.0.5"),
        MIXED to listOf("203.0.113.11", "127.0.0.1"),
    )

    /** Literals resolve as the system resolves them (no DNS involved); names only from the table above. */
    fun lookup(name: String): Array<InetAddress> {
        if (':' in name || name.all { it.isDigit() || it == '.' }) {
            return InetAddress.getAllByName(name)
        }
        return records[name]?.map { InetAddress.getByName(it) }?.toTypedArray() ?: throw UnknownHostException(name)
    }
}
