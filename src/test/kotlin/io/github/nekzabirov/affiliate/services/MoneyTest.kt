package io.github.nekzabirov.affiliate.services

import io.github.nekzabirov.affiliate.errors.ValidationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/** Amounts as requests write them: the cabinet shows the refusal's message under the input, so it must name the reason. */
class MoneyTest {

    @Test
    fun `every refused amount says what is wrong with it`() {
        listOf(
            Triple("05", "format", "amount must not start with a zero"),
            Triple("00.5", "format", "amount must not start with a zero"),
            Triple("1.001", "format", "amount must have at most 2 fraction digits"),
            Triple("12,5", "format", "amount must be a decimal number, like 12.5"),
            Triple("-5", "format", "amount must be a decimal number, like 12.5"),
            Triple("1e3", "format", "amount must be a decimal number, like 12.5"),
            Triple("12345678901", "max", "amount must be at most 1000000000"),
            Triple("1000000000.01", "max", "amount must be at most 1000000000"),
            Triple("0.00", "positive", "amount must be greater than zero"),
        ).forEach { (raw, rule, message) ->
            assertThatThrownBy { Money.parsePositive("amount", raw) }
                .describedAs(raw)
                .isInstanceOf(ValidationException::class.java)
                .hasMessage(message)
                .extracting("rule")
                .isEqualTo(rule)
        }
        assertThatThrownBy { Money.parsePositive("amount", "100.01", max = Money.HUNDRED) }
            .hasMessage("amount must be at most 100")
    }

    @Test
    fun `plain amounts up to the limit are read as they are written`() {
        listOf("0.5", "12", "12.5", "99.99", "1000000000").forEach { raw ->
            assertThat(Money.parsePositive("amount", raw)).isEqualByComparingTo(BigDecimal(raw))
        }
    }

    @Test
    fun `terms read zero in any spelling, and every other rule still holds`() {
        listOf("0", "0.0", "0.00").forEach { raw ->
            assertThat(Money.parseNonNegative("amount", raw)).describedAs(raw).isEqualByComparingTo(BigDecimal.ZERO)
        }
        listOf("0.5", "12.5", "100").forEach { raw ->
            assertThat(Money.parseNonNegative("amount", raw, max = Money.HUNDRED)).isEqualByComparingTo(BigDecimal(raw))
        }
        listOf(
            Triple("", "required", "amount is required"),
            Triple("-1", "format", "amount must be a decimal number, like 12.5"),
            Triple("-0", "format", "amount must be a decimal number, like 12.5"),
            Triple("00", "format", "amount must not start with a zero"),
            Triple("0.001", "format", "amount must have at most 2 fraction digits"),
            Triple("1000000000.01", "max", "amount must be at most 1000000000"),
        ).forEach { (raw, rule, message) ->
            assertThatThrownBy { Money.parseNonNegative("amount", raw) }
                .describedAs(raw)
                .isInstanceOf(ValidationException::class.java)
                .hasMessage(message)
                .extracting("rule")
                .isEqualTo(rule)
        }
        assertThatThrownBy { Money.parseNonNegative("amount", "100.01", max = Money.HUNDRED) }
            .hasMessage("amount must be at most 100")
    }
}
