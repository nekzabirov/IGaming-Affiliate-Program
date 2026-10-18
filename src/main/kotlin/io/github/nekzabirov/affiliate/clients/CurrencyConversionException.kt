package io.github.nekzabirov.affiliate.clients

/**
 * The currency service did not convert an amount: it refused (an unknown currency, a missing rate) or did not answer.
 * The fact being handled cannot be counted without the amount, so its delivery is dead-lettered.
 */
class CurrencyConversionException(message: String, cause: Throwable) : RuntimeException(message, cause)
