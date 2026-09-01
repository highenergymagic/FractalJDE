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
 * A number, or a truth value, which here are the same kind of thing.
 *
 * The runtime keeps whole numbers, real numbers and true-or-false apart, and for
 * arithmetic that is right. For everything else in a system it is a nuisance: a preference
 * is a value, a property list holds values, a message carries values, and every one of
 * those has to be able to hold a count, a size, a fraction or a yes without three separate
 * ways of saying so. Foundation has always answered that with one type, and this is it.
 *
 * What it was made from is remembered, so a value written as true reads back as true
 * rather than as 1. That matters for a property list, where the two are different things
 * on the page and a program that round-trips one should not quietly change it.
 */
public final class FMNumber implements Comparable<FMNumber> {

    public static final FMNumber TRUE = new FMNumber(Kind.TRUTH, 1, 0);
    public static final FMNumber FALSE = new FMNumber(Kind.TRUTH, 0, 0);
    public static final FMNumber ZERO = new FMNumber(Kind.WHOLE, 0, 0);

    /** What the value was made from, which is what it reads back as. */
    public enum Kind { TRUTH, WHOLE, REAL }

    private final Kind kind;
    private final long whole;
    private final double real;

    private FMNumber(Kind kind, long whole, double real) {
        this.kind = kind;
        this.whole = whole;
        this.real = real;
    }

    public static FMNumber of(boolean value) { return value ? TRUE : FALSE; }

    public static FMNumber of(long value) { return new FMNumber(Kind.WHOLE, value, value); }

    public static FMNumber of(double value) {
        return new FMNumber(Kind.REAL, (long) value, value);
    }

    /**
     * Reads a number out of text, or answers nothing when the text is not one.
     *
     * A value that came from a file, a preference or a person is text until something
     * decides otherwise, and deciding is this. Text that is not a number is not an error
     * to be thrown: it is a value of some other kind, and the caller knows what to do.
     */
    public static FMNumber parsing(FMString text) {
        String raw = text.toString().trim();
        if (raw.isEmpty()) return null;
        if ("true".equalsIgnoreCase(raw) || "YES".equals(raw)) return TRUE;
        if ("false".equalsIgnoreCase(raw) || "NO".equals(raw)) return FALSE;
        try {
            return raw.indexOf('.') < 0 && raw.indexOf('e') < 0 && raw.indexOf('E') < 0
                ? of(Long.parseLong(raw)) : of(Double.parseDouble(raw));
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    public Kind kind() { return kind; }

    /** Whether this is a yes. A number is a yes when it is not zero, as it always has been. */
    public boolean isTrue() { return kind == Kind.REAL ? real != 0 : whole != 0; }

    public boolean isTruth() { return kind == Kind.TRUTH; }

    public long asWhole() { return kind == Kind.REAL ? Math.round(real) : whole; }

    public int asInteger() { return (int) asWhole(); }

    public double asReal() { return kind == Kind.REAL ? real : whole; }

    /**
     * The value as text, spelled the way its kind is spelled.
     *
     * A truth value is YES or NO, which is what a property list holds and what a person
     * reading one expects to see. A whole number has no point in it.
     */
    public FMString asString() { return FMString.of(toString()); }

    @Override public String toString() {
        return switch (kind) {
            case TRUTH -> isTrue() ? "YES" : "NO";
            case WHOLE -> Long.toString(whole);
            case REAL -> Double.toString(real);
        };
    }

    @Override public int compareTo(FMNumber other) {
        return kind == Kind.REAL || other.kind == Kind.REAL
            ? Double.compare(asReal(), other.asReal())
            : Long.compare(asWhole(), other.asWhole());
    }

    /**
     * Whether two numbers are the same number.
     *
     * Two values equal in magnitude are equal whatever they were made from, so a count
     * read back from a file as a whole number matches one a program made as a real. The
     * kind decides how a value reads and writes, not what it is worth.
     */
    @Override public boolean equals(Object other) {
        return other instanceof FMNumber n && compareTo(n) == 0;
    }

    @Override public int hashCode() { return Double.hashCode(asReal()); }
}
