package io.github.nekzabirov.affiliate.events

/** A delivery that is not the envelope its route promises; the consumer dead-letters it. */
class MalformedEventException(message: String) : RuntimeException(message)
