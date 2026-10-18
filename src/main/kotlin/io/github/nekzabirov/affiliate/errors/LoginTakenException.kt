package io.github.nekzabirov.affiliate.errors

import org.springframework.http.HttpStatus

class LoginTakenException : DomainException("LOGIN_TAKEN", HttpStatus.CONFLICT, "This login is taken")
