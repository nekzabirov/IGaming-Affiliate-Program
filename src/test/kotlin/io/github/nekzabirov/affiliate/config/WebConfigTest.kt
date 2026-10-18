package io.github.nekzabirov.affiliate.config

import io.github.nekzabirov.affiliate.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping
import org.springframework.web.servlet.resource.CachingResourceResolver
import org.springframework.web.servlet.resource.PathResourceResolver
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler

/** The SPA handler as the running app registered it. */
class WebConfigTest : IntegrationTest() {

    @Autowired
    @Qualifier("resourceHandlerMapping")
    private lateinit var resources: HandlerMapping

    @Test
    fun `the SPA fallback resolves every path afresh, never into a cache keyed by the path a client chose`() {
        val spa = (resources as SimpleUrlHandlerMapping).handlerMap["/**"] as ResourceHttpRequestHandler

        assertThat(spa.resourceResolvers).noneMatch { it is CachingResourceResolver }
        assertThat(spa.resourceResolvers).anyMatch { it is PathResourceResolver }
    }
}
