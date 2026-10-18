package io.github.nekzabirov.affiliate.errors

import org.springframework.http.HttpStatus

/**
 * Every refusal this app makes: the HTTP [status] it answers with and the [code] the cabinet switches on.
 *
 * [code] is the contract, not the class name: a class is renamed by a refactor, and a frontend matching on it
 * would break silently. Once a code has shipped it does not change. Messages are English and technical.
 */
abstract class DomainException(
    val code: String,

    val status: HttpStatus,

    message: String,
) : RuntimeException(message)
