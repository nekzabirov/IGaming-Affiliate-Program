package io.github.nekzabirov.affiliate.config

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.cfg.CoercionAction
import tools.jackson.databind.cfg.CoercionInputShape
import tools.jackson.databind.type.LogicalType

/**
 * The REST mapper is Boot's own, made strict about what a partner's browser sends.
 *
 * Jackson 3 ignores unknown properties by default; a misspelt field would then be dropped silently and the
 * request would do something other than what was meant, so here it is a 400 naming the field. A flag sent as
 * the string "false" or the number 0 is refused for the same reason.
 *
 * The event bus reads with a mapper of its own, and that one must not be a JsonMapper bean: Boot's
 * auto-configured mapper backs off as soon as any JsonMapper bean exists, and REST would lose this one.
 */
@Configuration
class JsonConfig {

    @Bean
    fun strictRequestBodies(): JsonMapperBuilderCustomizer = JsonMapperBuilderCustomizer { builder ->
        builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        builder.withCoercionConfig(LogicalType.Boolean) { booleans ->
            booleans.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
            booleans.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
        }
    }
}
