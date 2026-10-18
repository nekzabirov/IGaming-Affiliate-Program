package io.github.nekzabirov.affiliate.postback

import org.apache.hc.client5.http.DnsResolver
import org.apache.hc.client5.http.SystemDefaultDnsResolver
import java.net.InetAddress

/**
 * The postback client's only resolver: every connection goes to addresses [UrlGuard] approved at connect time.
 * Checking the URL when it was saved is not enough - the name can resolve elsewhere by the time a deposit
 * fires the postback.
 */
class GuardedDnsResolver(private val guard: UrlGuard) : DnsResolver {

    override fun resolve(host: String): Array<InetAddress> = guard.resolve(host).toTypedArray()

    // Asked only by SPNEGO and Kerberos authentication, which the postback client never performs.
    override fun resolveCanonicalHostname(host: String): String =
        SystemDefaultDnsResolver.INSTANCE.resolveCanonicalHostname(host)
}
