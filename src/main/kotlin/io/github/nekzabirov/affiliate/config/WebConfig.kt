package io.github.nekzabirov.affiliate.config

import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver
import java.util.concurrent.TimeUnit

/**
 * Serves the Vue SPA the CI build puts into classpath:/static, out of the same jar as the API.
 *
 * Vite names every file under /assets by its content hash, so those are cached for a year. Everything else
 * revalidates on every load: without a Cache-Control at all, a phone keeps showing yesterday's build for hours
 * after a deploy.
 */
@Configuration
class WebConfig : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/assets/**")
            .addResourceLocations("classpath:/static/assets/")
            .setCacheControl(HASHED)

        // No resolution cache: it is unbounded and keyed by the request path, and every path without a dot
        // resolves to index.html here, so anyone could grow the heap one random URL at a time until the pod dies.
        // Looking a file up in the jar on each request costs next to nothing.
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .setCacheControl(REVALIDATE)
            .resourceChain(false)
            .addResolver(SpaFallback())
    }

    /**
     * A history-mode route (/companies/summer) is not a file: it answers index.html and the router draws it.
     * /api never falls back, so an unknown endpoint is a JSON 404 rather than a page; neither does a path
     * that names a file, so a missing chunk is a 404 rather than HTML served as JavaScript.
     */
    private class SpaFallback : PathResourceResolver() {

        override fun getResource(resourcePath: String, location: Resource): Resource? {
            if (resourcePath.isNotEmpty()) {
                val requested = location.createRelative(resourcePath)
                if (requested.exists() && requested.isReadable) {
                    return requested
                }
            }
            if (resourcePath == API || resourcePath.startsWith("$API/")) {
                return null
            }
            if (resourcePath.substringAfterLast('/').contains('.')) {
                return null
            }
            return location.createRelative(INDEX).takeIf { it.exists() && it.isReadable }
        }
    }

    private companion object {

        const val API = "api"

        const val INDEX = "index.html"

        const val HASHED_DAYS = 365L

        val HASHED: CacheControl = CacheControl.maxAge(HASHED_DAYS, TimeUnit.DAYS).cachePublic().immutable()

        val REVALIDATE: CacheControl = CacheControl.noCache()
    }
}
