package io.github.nekzabirov.affiliate.rest

import jakarta.servlet.http.HttpServletRequest

/**
 * The browser's address, the key of the sign-in and sign-up rate limits.
 *
 * Without [trustedHeader] it is the address the request came from, and nothing the client sends can change it:
 * an app reached directly must not let a forged header pick a fresh rate-limit key for every guess. Behind a
 * proxy that sets [trustedHeader] (Cloudflare's CF-Connecting-IP, nginx's X-Real-IP), that header comes first,
 * then the first X-Forwarded-For hop, should the forwarded-header filter not have consumed it already (it moves
 * that hop into the remote address), then the remote address.
 */
fun clientIp(request: HttpServletRequest, trustedHeader: String?): String {
    if (trustedHeader == null) return request.remoteAddr
    request.getHeader(trustedHeader)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    request.getHeader(X_FORWARDED_FOR)?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return request.remoteAddr
}

private const val X_FORWARDED_FOR = "X-Forwarded-For"
