package io.github.nekzabirov.affiliate.errors

import org.springframework.http.HttpStatus

/**
 * A write that is not JSON. An HTML form on another site can post only form or text bodies without a CORS
 * preflight, so refusing those closes CSRF together with the SameSite=Lax session cookie.
 */
class UnsupportedMediaTypeException :
    DomainException("UNSUPPORTED_MEDIA_TYPE", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Send the request body as application/json")
