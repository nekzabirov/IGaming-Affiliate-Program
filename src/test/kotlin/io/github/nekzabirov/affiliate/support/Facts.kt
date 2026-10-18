package io.github.nekzabirov.affiliate.support

import tools.jackson.databind.json.JsonMapper

/**
 * Facts in the exact shapes the publishers send (docs/wiki/Events.md): whole snapshots, with every field the
 * publishers send and this app ignores, so a test that passes here passes on the wire. Amounts are nano units.
 */
object Facts {

    /** A sample time; its UTC day is [DAY]. */
    const val AT = "2026-09-17T10:37:41.507218Z"

    const val DAY = "2026-09-17"

    private val json = JsonMapper.builder().build()

    fun register(
        playerId: Long,
        tag: String?,
        attribution: Map<String, String> = emptyMap(),
        country: String? = "UA",
        createdAt: String = AT,
    ): String = envelope(playerId, player(playerId, tag, attribution, country, createdAt))

    /** An invoice.events delivery; the `player*` values are the embedded owner's, as the player service has them at that moment. */
    fun invoice(
        id: Long,
        playerId: Long,
        amount: Long,
        currency: String = "UAH",
        type: String = "CREDIT",
        status: String = "SUCCESS",
        updatedAt: String = AT,
        playerTag: String? = "referal:1",
        playerAttribution: Map<String, String> = mapOf("fbc" to "fb.1.1759300000000.IwAR2xYz"),
        playerCreatedAt: String = AT,
    ): String = envelope(
        playerId,
        """
        {
          "id": $id,
          "player": ${player(playerId, playerTag, playerAttribution, "UA", playerCreatedAt)},
          "method": {
            "identity": "fake.60337e69717e",
            "provider": "fake",
            "type": "DEPOSIT",
            "name": "Test deposit",
            "min": 0,
            "max": 0,
            "currencies": [${quote(currency)}],
            "enabled": true,
            "image": {},
            "order": 0,
            "createdAt": "2026-09-17T10:37:41.537779Z",
            "updatedAt": "2026-09-17T10:37:41.542573Z"
          },
          "currency": ${quote(currency)},
          "bin": null,
          "type": ${quote(type)},
          "status": ${quote(status)},
          "amount": $amount,
          "taxAmount": 0,
          "feeAmount": 0,
          "transactionAmount": null,
          "transactionCurrency": null,
          "parameters": {},
          "externalId": null,
          "createdAt": "2026-09-17T10:37:41.556365Z",
          "updatedAt": ${quote(updatedAt)}
        }
        """,
    )

    /** A spin.events delivery; [reference] is the whole reversed leg, as [spinLeg] writes it. */
    fun spin(
        id: Long,
        playerId: Long,
        type: String,
        realAmount: Long,
        lockAmount: Long = 0,
        bonusAmount: Long = 0,
        reference: String? = null,
        currency: String = "UAH",
        createdAt: String = AT,
    ): String = envelope(playerId, spinLeg(id, playerId, type, realAmount, lockAmount, bonusAmount, reference, currency, createdAt))

    fun spinLeg(
        id: Long,
        playerId: Long,
        type: String,
        realAmount: Long,
        lockAmount: Long = 0,
        bonusAmount: Long = 0,
        reference: String? = null,
        currency: String = "UAH",
        createdAt: String = AT,
    ): String =
        """
        {
          "id": $id,
          "externalId": "spin-$id",
          "round": {
            "id": 9001,
            "externalId": "round-9001",
            "freespinId": null,
            "playerId": $playerId,
            "game": {
              "identity": "gates_of_olympus",
              "name": "Gates of Olympus",
              "provider": {
                "identity": "pragmatic",
                "name": "Test Provider",
                "images": {},
                "order": 100,
                "active": true,
                "blockedCountry": [],
                "tags": ["live"]
              },
              "collections": [
                { "identity": "popular", "name": {}, "tags": [], "images": {}, "active": true, "order": 100 }
              ],
              "bonusBetEnable": true,
              "bonusWageringEnable": true,
              "tags": ["slots"],
              "rtp": 96.5,
              "freeSpinEnable": true,
              "freeChipEnable": false,
              "jackpotEnable": false,
              "demoEnable": true,
              "bonusBuyEnable": false,
              "locales": ["en"],
              "platforms": ["DESKTOP", "MOBILE"],
              "playLines": 20,
              "active": true,
              "images": { "1x1": "casino/game/gates_of_olympus/1x1-1a2b3c4d5e6f.webp" },
              "order": 0
            },
            "currency": ${quote(currency)},
            "createdAt": ${quote(createdAt)},
            "finishedAt": null
          },
          "reference": ${reference ?: "null"},
          "type": ${quote(type)},
          "amount": ${realAmount + lockAmount + bonusAmount},
          "realAmount": $realAmount,
          "lockAmount": $lockAmount,
          "bonusAmount": $bonusAmount
        }
        """

    /** A bet.events delivery; [reference] is the whole reversed leg, as [betLeg] writes it. */
    fun bet(
        id: Long,
        playerId: Long,
        type: String,
        realAmount: Long,
        lockAmount: Long = 0,
        reference: String? = null,
        currency: String = "UAH",
        createdAt: String = AT,
    ): String = envelope(playerId, betLeg(id, playerId, type, realAmount, lockAmount, reference, currency, createdAt))

    fun betLeg(
        id: Long,
        playerId: Long,
        type: String,
        realAmount: Long,
        lockAmount: Long = 0,
        reference: String? = null,
        currency: String = "UAH",
        createdAt: String = AT,
    ): String =
        """
        {
          "id": $id,
          "externalId": "bet-leg-$id",
          "type": ${quote(type)},
          "amount": ${realAmount + lockAmount},
          "realAmount": $realAmount,
          "lockAmount": $lockAmount,
          "bonusAmount": 0,
          "reference": ${reference ?: "null"},
          "bet": {
            "id": 1204,
            "externalId": "1204",
            "playerId": $playerId,
            "currency": ${quote(currency)},
            "createdAt": ${quote(createdAt)},
            "finishedAt": null
          }
        }
        """

    /** The player service's whole Player snapshot, which register.events carries and every invoice embeds. */
    fun player(playerId: Long, tag: String?, attribution: Map<String, String>, country: String?, createdAt: String): String =
        """
        {
          "id": $playerId,
          "username": "u68832be4a31e$playerId",
          "status": "ACTIVE",
          "blockReason": null,
          "blockedAt": null,
          "frozenAt": null,
          "frozenUntil": null,
          "affiliateTag": ${quote(tag)},
          "avatar": null,
          "segments": [],
          "email": "u68832be4a31e@example.com",
          "emailStatus": "UNVERIFIED",
          "phone": null,
          "phoneStatus": "UNVERIFIED",
          "firstName": null,
          "lastName": null,
          "middleName": null,
          "localFirstName": null,
          "localLastName": null,
          "localMiddleName": null,
          "birthDate": null,
          "country": ${quote(country)},
          "locale": null,
          "personalNumber": null,
          "gender": "UNSPECIFIED",
          "address": null,
          "kycStatus": "UNVERIFIED",
          "affiliateAttribution": ${json.writeValueAsString(attribution)},
          "createdAt": ${quote(createdAt)},
          "updatedAt": ${quote(createdAt)}
        }
        """

    private fun envelope(playerId: Long, data: String): String = """{ "playerId": $playerId, "data": $data }"""

    private fun quote(value: String?): String = if (value == null) "null" else json.writeValueAsString(value)
}
