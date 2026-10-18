package io.github.nekzabirov.affiliate.model

/** One player's all-time deposits and real-money turnover, in nano units of the partner currency. */
data class PlayerActivity(
    val depositCount: Long,

    val depositAmount: Long,

    val turnover: Long,
) {

    companion object {

        val NONE = PlayerActivity(0, 0, 0)
    }
}
