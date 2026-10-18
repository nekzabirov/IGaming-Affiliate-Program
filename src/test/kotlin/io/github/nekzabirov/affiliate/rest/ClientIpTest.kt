package io.github.nekzabirov.affiliate.rest

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

/** Which address keys the rate limits: the socket's unless a proxy's header is trusted. */
class ClientIpTest {

    @Test
    fun `without a trusted header the remote address is the client, whatever headers the request carries`() {
        val request = request(remote = "203.0.113.7", "CF-Connecting-IP" to "198.51.100.1", "X-Forwarded-For" to "198.51.100.2")

        assertThat(clientIp(request, null)).isEqualTo("203.0.113.7")
    }

    @Test
    fun `with a trusted header that header comes first, then the first forwarded hop, then the remote address`() {
        val header = "CF-Connecting-IP"

        assertThat(clientIp(request("10.0.0.5", header to " 198.51.100.1 ", "X-Forwarded-For" to "198.51.100.2"), header)).isEqualTo("198.51.100.1")
        assertThat(clientIp(request("10.0.0.5", "X-Forwarded-For" to "198.51.100.2, 10.0.0.9"), header)).isEqualTo("198.51.100.2")
        assertThat(clientIp(request("10.0.0.5", header to " "), header)).isEqualTo("10.0.0.5")
        assertThat(clientIp(request("10.0.0.5", "X-Real-IP" to "198.51.100.3"), "X-Real-IP")).isEqualTo("198.51.100.3")
    }

    private fun request(remote: String, vararg headers: Pair<String, String>) = MockHttpServletRequest().apply {
        remoteAddr = remote
        headers.forEach { (name, value) -> addHeader(name, value) }
    }
}
