package io.github.nekzabirov.affiliate.db

import io.github.nekzabirov.affiliate.model.Account
import io.github.nekzabirov.affiliate.model.Credentials
import io.github.nekzabirov.affiliate.model.Page
import io.github.nekzabirov.affiliate.model.Role
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.sql.ResultSet

/** The `account` table: logins, roles, the block switch and the one balance every company of a partner shares. */
@Component
class AccountStore(private val jdbc: JdbcClient) {

    /** A new partner, or null when the login is taken (the unique index decides, so two racing sign-ups cannot both win). */
    fun insert(login: String, passwordHash: String): Account? =
        jdbc.sql(
            """
            INSERT INTO account (login, password_hash) VALUES (:login, :passwordHash)
            ON CONFLICT (login) DO NOTHING
            RETURNING $COLUMNS
            """,
        )
            .param("login", login)
            .param("passwordHash", passwordHash)
            .query { rs, _ -> read(rs) }
            .optional()
            .orElse(null)

    fun findById(id: Long): Account? =
        jdbc.sql("SELECT $COLUMNS FROM account WHERE id = :id")
            .param("id", id)
            .query { rs, _ -> read(rs) }
            .optional()
            .orElse(null)

    fun findCredentials(login: String): Credentials? =
        jdbc.sql("SELECT $COLUMNS, password_hash FROM account WHERE login = :login")
            .param("login", login)
            .query { rs, _ -> Credentials(read(rs), rs.getString("password_hash")) }
            .optional()
            .orElse(null)

    /**
     * Debits [amount] only if the balance covers it, in one statement: two payout requests racing for the same
     * balance cannot both pass a check made before either write. False when the balance is too low.
     */
    fun reserve(accountId: Long, amount: Long): Boolean =
        jdbc.sql(
            """
            UPDATE account SET balance = balance - :amount, updated_at = now()
            WHERE id = :id AND balance >= :amount
            """,
        )
            .param("id", accountId)
            .param("amount", amount)
            .update() == 1

    /**
     * Moves the balance by a signed [amount] in one statement: a commission earned adds, a reversed one takes
     * back, and the balance may go below zero when the money was already paid out.
     */
    fun addToBalance(accountId: Long, amount: Long) {
        jdbc.sql("UPDATE account SET balance = balance + :amount, updated_at = now() WHERE id = :id")
            .param("id", accountId)
            .param("amount", amount)
            .update()
    }

    fun setActive(accountId: Long, active: Boolean): Account? =
        jdbc.sql("UPDATE account SET active = :active, updated_at = now() WHERE id = :id RETURNING $COLUMNS")
            .param("id", accountId)
            .param("active", active)
            .query { rs, _ -> read(rs) }
            .optional()
            .orElse(null)

    /** Every account, newest first; [query] matches the login anywhere in it. */
    fun page(query: String?, page: Int, size: Int): Page<Account> {
        val filter = if (query == null) "" else "WHERE login LIKE :pattern ESCAPE '\\'"
        val params = buildMap { if (query != null) put("pattern", containsPattern(query.lowercase())) }
        val items = jdbc.sql(
            """
            SELECT $COLUMNS FROM account $filter
            ORDER BY created_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """,
        )
            .params(params)
            .param("limit", size)
            .param("offset", offset(page, size))
            .query { rs, _ -> read(rs) }
            .list()
        val total = jdbc.sql("SELECT count(*) FROM account $filter")
            .params(params)
            .query(Long::class.javaObjectType)
            .single()
        return Page(items, page, size, total)
    }

    companion object {

        const val COLUMNS = "id, login, role, active, balance, created_at, updated_at"

        /** Reads [COLUMNS]; the session lookup selects the same names off its join. */
        fun read(rs: ResultSet) = Account(
            id = rs.getLong("id"),
            login = rs.getString("login"),
            role = Role.valueOf(rs.getString("role")),
            active = rs.getBoolean("active"),
            balance = rs.getLong("balance"),
            createdAt = rs.instant("created_at"),
            updatedAt = rs.instant("updated_at"),
        )
    }
}
