package io.github.nekzabirov.affiliate.postback

import java.net.UnknownHostException

/**
 * A host the SSRF guard refuses. An UnknownHostException, so the HTTP client fails the connection exactly as
 * for a name that does not resolve, and never dials the address.
 */
class BlockedHostException(host: String, reason: String) : UnknownHostException("$host is blocked: $reason")
