package io.github.nekzabirov.affiliate.model

/** Admins are made by hand in the database (README); registration only ever creates partners. */
enum class Role {
    PARTNER,
    ADMIN,
}
