package io.github.nekzabirov.affiliate.events

import org.springframework.stereotype.Component
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.cfg.EnumFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * Reads the platform's envelope `{"playerId": <number>, "data": {...}}` strictly.
 *
 * `playerId` must be a positive JSON integer: a quoted, fractional, null or missing one is a malformed fact, not
 * a player to guess, and so is one that differs from the player inside `data`. Inside `data` a number is never
 * read from a string or truncated from a fraction, and an enum never from a number, so another engine's fact in
 * the wrong shape fails its delivery instead of being counted as money it never said. Fields this app does not
 * model are ignored, and so is an enum value it does not act on: each enum of the wire records keeps only the
 * values a handler branches on, and any other - including one a publisher adds later - reads as that enum's
 * `OTHER` (`@JsonEnumDefaultValue`), which the handlers acknowledge without effect. A publisher adding a field or
 * a value must not stop the stream.
 *
 * The mapper is this class's own and deliberately not a bean: Boot's REST mapper backs off as soon as any
 * JsonMapper bean exists, and the REST side is strict about the opposite thing (unknown fields).
 */
@Component
class EventReader {

    private val mapper: JsonMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
        .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
        .build()

    /** The envelope of [body] with `data` read as [type]; [playerOf] says where [type] carries its player. */
    fun <T : Any> read(body: ByteArray, type: Class<T>, playerOf: (T) -> Long): Envelope<T> {
        val envelope = mapper.readTree(body)
        if (!envelope.isObject) {
            throw MalformedEventException("The body is not an envelope object")
        }
        val playerId = envelope.get(PLAYER_ID)
        if (playerId == null || !playerId.isIntegralNumber || !playerId.canConvertToLong() || playerId.longValue() <= 0) {
            throw MalformedEventException("'$PLAYER_ID' must be a positive JSON integer, got $playerId")
        }
        val data = envelope.get(DATA)
        if (data == null || !data.isObject) {
            throw MalformedEventException("'$DATA' must be an object, got ${data?.nodeType}")
        }
        val fact = mapper.treeToValue(data, type)
        val player = playerOf(fact)
        if (player != playerId.longValue()) {
            throw MalformedEventException("'$PLAYER_ID' ${playerId.longValue()} is not the player in '$DATA' ($player)")
        }
        return Envelope(player, fact)
    }

    private companion object {

        const val PLAYER_ID = "playerId"

        const val DATA = "data"
    }
}
