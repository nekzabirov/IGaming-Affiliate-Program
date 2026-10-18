package io.github.nekzabirov.affiliate.model

/** One page of a list, in the shape every paged endpoint answers with. */
data class Page<T>(
    val items: List<T>,

    val page: Int,

    val size: Int,

    val total: Long,
) {

    fun <R> map(transform: (T) -> R): Page<R> = Page(items.map(transform), page, size, total)
}
