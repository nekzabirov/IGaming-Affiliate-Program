package io.github.nekzabirov.affiliate.postback

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.net.Inet6Address
import java.net.InetAddress

class UrlGuardTest {

    /** The system resolver: only literals are asked of it here, and those never touch DNS. */
    private val guard = UrlGuard()

    @Test
    fun `every blocked range is refused at both ends`() {
        listOf(
            "0.0.0.0", "0.255.255.255",
            "10.0.0.0", "10.255.255.255",
            "100.64.0.0", "100.127.255.255",
            "127.0.0.1", "127.255.255.255",
            "169.254.0.0", "169.254.169.254", "169.254.255.255",
            "172.16.0.0", "172.31.255.255",
            "192.0.0.0", "192.0.0.255",
            "192.168.0.0", "192.168.255.255",
            "198.18.0.0", "198.19.255.255",
            "224.0.0.0", "239.255.255.255",
            "240.0.0.0", "255.255.255.254",
            "255.255.255.255",
            "::", "::1",
            "64:ff9b::", "64:ff9b::7f00:1", "64:ff9b::ffff:ffff",
            "fc00::", "fd00:ec2::254", "fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "fe80::", "febf:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "ff00::", "ff02::1", "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
        ).forEach { literal ->
            assertThat(guard.isBlocked(InetAddress.getByName(literal))).describedAs(literal).isTrue()
        }
    }

    @Test
    fun `public addresses next to the blocked ranges pass`() {
        listOf(
            "1.0.0.0", "8.8.8.8", "9.255.255.255", "11.0.0.0",
            "100.63.255.255", "100.128.0.0",
            "126.255.255.255", "128.0.0.0",
            "169.253.255.255", "169.255.0.0",
            "172.15.255.255", "172.32.0.0",
            "192.0.1.0", "192.167.255.255", "192.169.0.0",
            "198.17.255.255", "198.20.0.0",
            "203.0.113.10", "223.255.255.255",
            "64:ff9b:1::1", "2001:4860:4860::8888", "2606:4700:4700::1111", "fbff:ffff::1",
        ).forEach { literal ->
            assertThat(guard.isBlocked(InetAddress.getByName(literal))).describedAs(literal).isFalse()
        }
    }

    @Test
    fun `an IPv4-mapped IPv6 address is judged by the IPv4 address it carries`() {
        fun mapped(a: Int, b: Int, c: Int, d: Int): InetAddress {
            val bytes = ByteArray(16)
            bytes[10] = -1
            bytes[11] = -1
            bytes[12] = a.toByte()
            bytes[13] = b.toByte()
            bytes[14] = c.toByte()
            bytes[15] = d.toByte()
            return Inet6Address.getByAddress(null, bytes, -1)
        }

        assertThat(mapped(127, 0, 0, 1)).isInstanceOf(Inet6Address::class.java)
        assertThat(guard.isBlocked(mapped(127, 0, 0, 1))).isTrue()
        assertThat(guard.isBlocked(mapped(10, 1, 2, 3))).isTrue()
        assertThat(guard.isBlocked(mapped(169, 254, 169, 254))).isTrue()
        assertThat(guard.isBlocked(mapped(8, 8, 8, 8))).isFalse()
        // The resolver itself turns a mapped literal into IPv4.
        assertThatThrownBy { guard.resolve("::ffff:127.0.0.1") }.isInstanceOf(BlockedHostException::class.java)
    }

    @Test
    fun `numeric host forms are judged by what they resolve to`() {
        listOf("127.1", "127.0.1", "0.0.0.0", "[::1]", "[::ffff:10.0.0.1]", "169.254.169.254").forEach { host ->
            assertThatThrownBy { guard.resolve(host) }.describedAs(host).isInstanceOf(BlockedHostException::class.java)
        }
        // A bare number is also a single label, refused before it resolves to 127.0.0.1.
        assertThatThrownBy { guard.resolve("2130706433") }.isInstanceOf(BlockedHostException::class.java)
        assertThat(guard.resolve("8.8.8.8").map { it.hostAddress }).containsExactly("8.8.8.8")
    }

    @Test
    fun `internal and single-label names are refused without being resolved`() {
        val strict = UrlGuard { name -> error("$name must not be resolved") }

        listOf(
            "localhost", "LOCALHOST", "localhost.",
            "printer.local", "billing.corp.internal", "BILLING.CORP.INTERNAL.",
            "billing.corp.svc", "billing.corp.svc.cluster.local", "app.localhost",
            "billing-service", "redis-server", "",
        ).forEach { host ->
            assertThatThrownBy { strict.resolve(host) }.describedAs(host).isInstanceOf(BlockedHostException::class.java)
        }
    }

    @Test
    fun `a name is refused when any of its addresses is blocked`() {
        val dns = UrlGuard { name ->
            when (name) {
                "good.example.com" -> arrayOf(InetAddress.getByName("203.0.113.1"), InetAddress.getByName("2001:db8::1"))
                "rebound.example.com" -> arrayOf(InetAddress.getByName("203.0.113.1"), InetAddress.getByName("10.0.0.1"))
                else -> error(name)
            }
        }

        assertThat(dns.resolve("Good.Example.Com.")).hasSize(2)
        assertThatThrownBy { dns.resolve("rebound.example.com") }
            .isInstanceOf(BlockedHostException::class.java)
            .hasMessageContaining("10.0.0.1")
    }
}
