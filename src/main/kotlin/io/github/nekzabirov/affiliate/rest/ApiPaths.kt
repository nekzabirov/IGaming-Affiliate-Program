package io.github.nekzabirov.affiliate.rest

import jakarta.servlet.http.HttpServletRequest

/**
 * The request path as the container decoded and normalized it (`%61dmin` is `admin`, `//` is `/`). Access rules
 * are judged on this form: on the raw URI a prefix check could be walked around by an encoding that the
 * router still decodes into the guarded path.
 */
fun apiPath(request: HttpServletRequest): String = request.servletPath + (request.pathInfo ?: "")

fun isApi(path: String): Boolean = path == API_ROOT || path.startsWith("$API_ROOT/")

private const val API_ROOT = "/api"
