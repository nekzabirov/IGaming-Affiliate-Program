package io.github.nekzabirov.affiliate.db

import io.github.nekzabirov.affiliate.model.PlayerActivity
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.time.LocalDate

/** The `activity_daily` table: per player and UTC day, deposits and real-money turnover. */
@Component
class ActivityStore(private val jdbc: JdbcClient) {

    /** One more deposit of [amount] on the player's [day]. */
    fun addDeposit(playerId: Long, day: LocalDate, companyId: Long, amount: Long) {
        jdbc.sql(
            """
            INSERT INTO activity_daily (player_id, day, company_id, deposit_count, deposit_amount)
            VALUES (:playerId, :day, :companyId, 1, :amount)
            ON CONFLICT (player_id, day) DO UPDATE SET
                deposit_count = activity_daily.deposit_count + 1,
                deposit_amount = activity_daily.deposit_amount + EXCLUDED.deposit_amount
            """,
        )
            .param("playerId", playerId)
            .param("day", day)
            .param("companyId", companyId)
            .param("amount", amount)
            .update()
    }

    /** Moves the player's turnover of [day] by a signed [amount]: a stake adds, its rollback takes the same back. */
    fun addTurnover(playerId: Long, day: LocalDate, companyId: Long, amount: Long) {
        jdbc.sql(
            """
            INSERT INTO activity_daily (player_id, day, company_id, turnover_amount)
            VALUES (:playerId, :day, :companyId, :amount)
            ON CONFLICT (player_id, day) DO UPDATE SET
                turnover_amount = activity_daily.turnover_amount + EXCLUDED.turnover_amount
            """,
        )
            .param("playerId", playerId)
            .param("day", day)
            .param("companyId", companyId)
            .param("amount", amount)
            .update()
    }

    /** All-time totals of each player in [playerIds]; players with no activity are absent from the map. */
    fun totalsByPlayers(playerIds: Collection<Long>): Map<Long, PlayerActivity> {
        if (playerIds.isEmpty()) return emptyMap()
        return jdbc.sql(
            """
            SELECT player_id,
                   sum(deposit_count) AS deposit_count,
                   sum(deposit_amount) AS deposit_amount,
                   sum(turnover_amount) AS turnover
            FROM activity_daily WHERE player_id IN (:ids)
            GROUP BY player_id
            """,
        )
            .param("ids", playerIds)
            .query { rs, _ ->
                rs.getLong("player_id") to PlayerActivity(
                    depositCount = rs.getLong("deposit_count"),
                    depositAmount = rs.getLong("deposit_amount"),
                    turnover = rs.getLong("turnover"),
                )
            }
            .list()
            .toMap()
    }
}
