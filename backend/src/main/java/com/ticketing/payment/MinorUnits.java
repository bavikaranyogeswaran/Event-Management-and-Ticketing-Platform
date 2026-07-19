package com.ticketing.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Gateways move money in whole minor units, so amounts convert exactly or not at all.
 * The scale comes from the currency itself rather than an assumed two decimals.
 */
final class MinorUnits {

    static long from(BigDecimal amount, String currencyCode) {
        return amount.movePointRight(fractionDigits(currencyCode))
                // rounding here would mean charging a different price than the order says
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();
    }

    static BigDecimal toAmount(long minorUnits, String currencyCode) {
        return BigDecimal.valueOf(minorUnits).movePointLeft(fractionDigits(currencyCode));
    }

    private static int fractionDigits(String currencyCode) {
        return Currency.getInstance(currencyCode).getDefaultFractionDigits();
    }

    private MinorUnits() {
    }
}
