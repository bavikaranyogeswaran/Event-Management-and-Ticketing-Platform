package com.ticketing.payment;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MinorUnitsTest {

    @Test
    void rupeesBecomeCents() {
        assertThat(MinorUnits.from(new BigDecimal("1500.00"), "LKR")).isEqualTo(150_000L);
        assertThat(MinorUnits.from(new BigDecimal("0.01"), "LKR")).isEqualTo(1L);
        assertThat(MinorUnits.from(BigDecimal.ZERO, "LKR")).isZero();
    }

    @Test
    void trailingZerosDoNotChangeTheAmount() {
        assertThat(MinorUnits.from(new BigDecimal("1500"), "LKR"))
                .isEqualTo(MinorUnits.from(new BigDecimal("1500.0000"), "LKR"));
    }

    @Test
    void currenciesWithoutDecimalsAreNotMultiplied() {
        // getting this wrong would overcharge a buyer a hundredfold
        assertThat(MinorUnits.from(new BigDecimal("1500"), "JPY")).isEqualTo(1_500L);
    }

    @Test
    void anAmountFinerThanTheCurrencyIsRefusedRatherThanRounded() {
        assertThatThrownBy(() -> MinorUnits.from(new BigDecimal("10.005"), "LKR"))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void centsConvertBackToRupees() {
        assertThat(MinorUnits.toAmount(150_000L, "LKR")).isEqualByComparingTo("1500.00");
        assertThat(MinorUnits.toAmount(1_500L, "JPY")).isEqualByComparingTo("1500");
    }

    @Test
    void theRoundTripKeepsTheOriginalAmount() {
        BigDecimal original = new BigDecimal("2499.99");
        assertThat(MinorUnits.toAmount(MinorUnits.from(original, "LKR"), "LKR"))
                .isEqualByComparingTo(original);
    }
}
