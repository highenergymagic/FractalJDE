/*CDDL HEADER START
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 * You can obtain a copy of the license at usr/src/OPENSOLARIS.LICENSE
 * or http://illumos.org/license/CDDL.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at usr/src/OPENSOLARIS.LICENSE.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *
 * CDDL HEADER END
 * Copyright (C) 2026 by Fractal Microsystems, Inc.
 * Use is subject to license terms.
 */
package org.fractalmicro.foundation;

/**
 * A number with a decimal point that behaves the way a person expects one to.
 *
 * Kept in the base it was written in. A tenth is not representable in binary floating
 * point, so a tenth plus two tenths is not three tenths there.
 *
 * Division stops at {@link #DIGITS} significant figures, rounding half away from zero.
 *
 * A division by zero, or a value that was never a number, is {@link #NOT_A_NUMBER} rather
 * than an exception: arithmetic a person is typing goes wrong all the time.
 */
public final class FMDecimal implements Comparable<FMDecimal> {

    /** How many significant figures a division keeps. */
    public static final int DIGITS = 16;

    public static final FMDecimal ZERO = new FMDecimal(java.math.BigDecimal.ZERO);
    public static final FMDecimal ONE = new FMDecimal(java.math.BigDecimal.ONE);

    /** The answer to something that has no answer. It spreads: anything with it is it. */
    public static final FMDecimal NOT_A_NUMBER = new FMDecimal(null);

    private static final java.math.MathContext HOW =
        new java.math.MathContext(DIGITS, java.math.RoundingMode.HALF_UP);

    private final java.math.BigDecimal value;

    private FMDecimal(java.math.BigDecimal value) {
        this.value = value;
    }

    /** The number a person typed, or {@link #NOT_A_NUMBER} if that was not a number. */
    public static FMDecimal of(FMString text) {
        return text == null ? NOT_A_NUMBER : of(text.toString());
    }

    public static FMDecimal of(String text) {
        if (text == null) return NOT_A_NUMBER;
        String trimmed = text.trim();
        if (trimmed.isEmpty() || ".".equals(trimmed) || "-".equals(trimmed)) return ZERO;
        try {
            return new FMDecimal(new java.math.BigDecimal(trimmed));
        } catch (NumberFormatException notANumber) {
            return NOT_A_NUMBER;
        }
    }

    public static FMDecimal of(long number) {
        return new FMDecimal(java.math.BigDecimal.valueOf(number));
    }

    public boolean isNumber() { return value != null; }

    public FMDecimal plus(FMDecimal other) {
        return either(other) ? NOT_A_NUMBER : new FMDecimal(value.add(other.value));
    }

    public FMDecimal minus(FMDecimal other) {
        return either(other) ? NOT_A_NUMBER : new FMDecimal(value.subtract(other.value));
    }

    public FMDecimal times(FMDecimal other) {
        return either(other) ? NOT_A_NUMBER : new FMDecimal(value.multiply(other.value));
    }

    /** Divided, to {@link #DIGITS} figures. Dividing by nothing is not a number. */
    public FMDecimal dividedBy(FMDecimal other) {
        if (either(other) || other.value.signum() == 0) return NOT_A_NUMBER;
        return new FMDecimal(value.divide(other.value, HOW));
    }

    public FMDecimal negated() {
        return value == null ? NOT_A_NUMBER : new FMDecimal(value.negate());
    }

    /** A hundredth of itself, which is what the percent key does. */
    public FMDecimal asPercent() {
        return value == null ? NOT_A_NUMBER
            : new FMDecimal(value.divide(java.math.BigDecimal.valueOf(100), HOW));
    }

    private boolean either(FMDecimal other) {
        return value == null || other == null || other.value == null;
    }

    @Override public int compareTo(FMDecimal other) {
        if (value == null) return other.value == null ? 0 : -1;
        if (other.value == null) return 1;
        return value.compareTo(other.value);
    }

    @Override public boolean equals(Object other) {
        return other instanceof FMDecimal d && compareTo(d) == 0;
    }

    @Override public int hashCode() {
        return value == null ? 0 : value.stripTrailingZeros().hashCode();
    }

    /** How it is written: a whole number keeps no point, and no exponent is used. */
    @Override public String toString() {
        // NaN, which is what NSDecimalNumber writes and what every other language that
        // has the value writes. It is a value rather than a sentence.
        return value == null ? "NaN" : value.stripTrailingZeros().toPlainString();
    }

    public FMString asString() { return FMString.of(toString()); }
}
