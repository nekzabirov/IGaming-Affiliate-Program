package io.github.nekzabirov.affiliate.postback

import io.github.nekzabirov.affiliate.model.PostbackType
import org.springframework.stereotype.Component

/** The adapter of each tracker type. A type without one fails the boot rather than the first partner who picks it. */
@Component
class PostbackAdapters(adapters: List<PostbackAdapter>) {

    private val byType = adapters.associateBy { it.type }

    init {
        val missing = PostbackType.entries.filterNot { it in byType }
        check(missing.isEmpty()) { "No postback adapter for $missing" }
    }

    fun of(type: PostbackType): PostbackAdapter = byType.getValue(type)
}
