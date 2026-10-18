package io.github.nekzabirov.affiliate.services

import io.github.nekzabirov.affiliate.errors.ValidationException
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Money and percent arithmetic. Inside the app every amount is a Long of nano units (1e-9) of the partner
 * currency, the platform's wire unit; at the REST boundary it is a plain decimal string, because a JSON number
 * goes through a double in the browser and a balance must never be rounded by the reader.
 */
object Money {

    /** Nano units: 9 fraction digits. */
    const val SCALE = 9

    /** Requests carry cents for money and hundredths for a percent, nothing finer. */
    const val INPUT_FRACTION_DIGITS = 2

    /** The largest amount a request may name; its nano units still fit a Long with room for sums. */
    val MAX_INPUT = BigDecimal("1000000000")

    val HUNDRED = BigDecimal("100")

    /** Digits, then optionally a dot and more digits: the shape of every amount, before its own rules are checked. */
    private val DECIMAL = Regex("^([0-9]+)(?:\\.([0-9]+))?$")

    /** The integer digits of [MAX_INPUT]: an amount with more is beyond any limit, and its text is never parsed. */
    private val MAX_INTEGER_DIGITS = MAX_INPUT.precision() - MAX_INPUT.scale()

    /** "12.5", "0.000123", "-3": plain, trailing zeros stripped, at most 9 fraction digits. */
    fun format(nano: Long): String = format(BigDecimal.valueOf(nano, SCALE))

    fun format(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

    fun toNano(value: BigDecimal): Long = value.movePointRight(SCALE).setScale(0, RoundingMode.HALF_UP).longValueExact()

    fun toDecimal(nano: Long): BigDecimal = BigDecimal.valueOf(nano, SCALE)

    /** round_half_up(amount * percent / 100), in the same nano units as [amountNano]. */
    fun percentOf(amountNano: Long, percent: BigDecimal): Long =
        BigDecimal.valueOf(amountNano).multiply(percent).divide(HUNDRED, 0, RoundingMode.HALF_UP).longValueExact()

    /**
     * A decimal string from a request: digits with at most [INPUT_FRACTION_DIGITS] fraction digits and no leading
     * zero, more than zero and at most [max]. A JSON number is read as its text too, so 12.5 and "12.5" are the
     * same request. Each refusal names its own reason, because the cabinet shows the message under the input.
     */
    fun parsePositive(field: String, raw: String?, max: BigDecimal = MAX_INPUT): BigDecimal {
        val value = parseNonNegative(field, raw, max)
        if (value.signum() <= 0) {
            throw ValidationException(field, ValidationException.POSITIVE, "$field must be greater than zero")
        }
        return value
    }

    /**
     * [parsePositive] that also takes zero ("0", "0.00"): a company's terms may be nothing at all, for a partner
     * who was paid up front, while a payout of nothing is never a request. A minus sign is no part of a decimal
     * here, so anything below zero is still refused.
     */
    fun parseNonNegative(field: String, raw: String?, max: BigDecimal = MAX_INPUT): BigDecimal {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) {
            throw ValidationException(field, ValidationException.REQUIRED, "$field is required")
        }
        val (whole, fraction) = DECIMAL.matchEntire(text)?.destructured
            ?: throw ValidationException(field, ValidationException.FORMAT, "$field must be a decimal number, like 12.5")
        if (fraction.length > INPUT_FRACTION_DIGITS) {
            throw ValidationException(field, ValidationException.FORMAT, "$field must have at most $INPUT_FRACTION_DIGITS fraction digits")
        }
        if (whole.length > 1 && whole.startsWith('0')) {
            throw ValidationException(field, ValidationException.FORMAT, "$field must not start with a zero")
        }
        if (whole.length > MAX_INTEGER_DIGITS) {
            throw ValidationException(field, ValidationException.MAX, "$field must be at most ${format(max)}")
        }
        val value = BigDecimal(text)
        if (value > max) {
            throw ValidationException(field, ValidationException.MAX, "$field must be at most ${format(max)}")
        }
        return value
    }
}
