package io.github.nekzabirov.affiliate.errors

import org.springframework.http.HttpStatus

/** Identities are unique across all partners: the identity is the player's affiliate tag. */
class IdentityTakenException : DomainException("IDENTITY_TAKEN", HttpStatus.CONFLICT, "This identity is taken")
