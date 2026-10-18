package io.github.nekzabirov.affiliate.postback

import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * The SSRF guard for partner-entered tracker URLs. Postbacks leave from inside the VPC, where the engines
 * answer gRPC and HTTP without authentication; a partner who could point a postback at one of them, or at the
 * instance metadata, could make this app call it for them.
 *
 * Names are refused by shape first, then resolved, and the host is refused if ANY of its addresses is not
 * public. The RESOLVED addresses are what is judged, never the string: `2130706433` and `127.1` are both
 * 127.0.0.1 to the resolver. The ranges are compared bit by bit here, because InetAddress.isSiteLocalAddress()
 * knows neither 100.64/10 nor fc00::/7. The HTTP client resolves through the same check ([GuardedDnsResolver])
 * and connects only to the addresses it approved, so a name that rebinds to an internal address between the
 * save and the send is refused at the send.
 *
 * [lookup] is the resolver; the default asks the system for the name as a fully qualified one.
 */
class UrlGuard(private val lookup: (String) -> Array<InetAddress> = ::systemLookup) {

    /** The addresses of [host], or a [BlockedHostException] if the name or any address is not public. */
    @Throws(UnknownHostException::class)
    fun resolve(host: String): List<InetAddress> {
        val name = host.trim().lowercase().removePrefix("[").removeSuffix("]").removeSuffix(".")
        refuseByName(host, name)
        val addresses = lookup(name)
        if (addresses.isEmpty()) {
            throw UnknownHostException(host)
        }
        addresses.firstOrNull(::isBlocked)?.let { throw BlockedHostException(host, "resolves to ${it.hostAddress}") }
        return addresses.toList()
    }

    /** True for every address this app must never send a postback to. */
    fun isBlocked(address: InetAddress): Boolean {
        val bytes = address.address
        return when {
            bytes.size == IPV4_BYTES -> IPV4_BLOCKED.any { it.contains(bytes) }
            address is Inet6Address && MAPPED_IPV4.contains(bytes) ->
                IPV4_BLOCKED.any { it.contains(bytes.copyOfRange(IPV6_BYTES - IPV4_BYTES, IPV6_BYTES)) }
            bytes.size == IPV6_BYTES -> IPV6_BLOCKED.any { it.contains(bytes) }
            else -> true
        }
    }

    private fun refuseByName(host: String, name: String) {
        if (name.isEmpty()) {
            throw BlockedHostException(host, "empty name")
        }
        if (name == LOCALHOST || BLOCKED_SUFFIXES.any { name.endsWith(it) }) {
            throw BlockedHostException(host, "internal name")
        }
        // A single label is completed by the pod's resolver search path into a cluster name (billing-service, redis-server).
        if ('.' !in name && ':' !in name) {
            throw BlockedHostException(host, "single-label name")
        }
    }

    /** A network: [base] and the number of leading [bits] that must match. */
    private class Cidr(private val base: ByteArray, private val bits: Int) {

        fun contains(address: ByteArray): Boolean {
            if (address.size != base.size) return false
            val whole = bits / Byte.SIZE_BITS
            for (i in 0 until whole) {
                if (address[i] != base[i]) return false
            }
            val rest = bits % Byte.SIZE_BITS
            if (rest == 0) return true
            val mask = (BYTE_MASK shl (Byte.SIZE_BITS - rest)) and BYTE_MASK
            return (address[whole].toInt() and mask) == (base[whole].toInt() and mask)
        }

        companion object {

            /** Parses an address literal; never touches DNS. */
            fun of(notation: String): Cidr {
                val (address, bits) = notation.split('/')
                return Cidr(InetAddress.getByName(address).address, bits.toInt())
            }
        }
    }

    private companion object {

        const val IPV4_BYTES = 4

        const val IPV6_BYTES = 16

        const val BYTE_MASK = 0xFF

        const val LOCALHOST = "localhost"

        val BLOCKED_SUFFIXES = listOf(".local", ".internal", ".svc", ".cluster.local", ".localhost")

        val IPV4_BLOCKED = listOf(
            "0.0.0.0/8",
            "10.0.0.0/8",
            "100.64.0.0/10",
            "127.0.0.0/8",
            "169.254.0.0/16",
            "172.16.0.0/12",
            "192.0.0.0/24",
            "192.168.0.0/16",
            "198.18.0.0/15",
            "224.0.0.0/4",
            "240.0.0.0/4",
            "255.255.255.255/32",
        ).map(Cidr::of)

        val IPV6_BLOCKED = listOf(
            "::/128",
            "::1/128",
            "64:ff9b::/96",
            "fc00::/7",
            "fe80::/10",
            "ff00::/8",
        ).map(Cidr::of)

        /**
         * ::ffff:0:0/96, built from bytes: InetAddress turns the literal into the IPv4 address it embeds. An
         * address in it is judged by that embedded IPv4 address.
         */
        val MAPPED_IPV4 = Cidr(ByteArray(IPV6_BYTES).also { it[10] = -1; it[11] = -1 }, 96)

        /**
         * A trailing dot makes the name absolute, so the resolver search path (`<name>.<namespace>.svc.cluster.local`
         * first) can never turn a partner's host into a cluster service. Literals stay as they are.
         */
        fun systemLookup(name: String): Array<InetAddress> {
            val literal = ':' in name || name.all { it.isDigit() || it == '.' }
            return InetAddress.getAllByName(if (literal) name else "$name.")
        }
    }
}
