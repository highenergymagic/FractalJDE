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
 * A moment.
 *
 * A file has one, a message has one, the clock in the menu bar shows one. Foundation
 * counts from a reference date of its own rather than from the one the rest of the world
 * uses, and this keeps that: what a program holds is a number of seconds, and turning it
 * into something a person reads is a separate matter, done where the language and the
 * time zone are known.
 *
 * The reference is the first instant of 2001, which is what NSDate uses. The runtime
 * counts from 1970, so crossing between them is a fixed number of seconds and nothing else.
 */
public final class FMDate implements Comparable<FMDate> {

    /** Seconds between the runtime's reference and this one: 1970 to 2001. */
    private static final long REFERENCE_OFFSET = 978_307_200L;

    private final double secondsSinceReference;

    private FMDate(double secondsSinceReference) {
        this.secondsSinceReference = secondsSinceReference;
    }

    /** The moment this is asked. */
    public static FMDate now() {
        return new FMDate(System.currentTimeMillis() / 1000.0 - REFERENCE_OFFSET);
    }

    public static FMDate sinceReference(double seconds) { return new FMDate(seconds); }

    /** From what the runtime counts, which is how a file's date arrives. */
    public static FMDate fromEpochMilliseconds(long milliseconds) {
        return new FMDate(milliseconds / 1000.0 - REFERENCE_OFFSET);
    }

    public double secondsSinceReference() { return secondsSinceReference; }

    public long epochMilliseconds() {
        return Math.round((secondsSinceReference + REFERENCE_OFFSET) * 1000.0);
    }

    /** How far this is from another, in seconds, which is what a duration is. */
    public double since(FMDate earlier) {
        return secondsSinceReference - earlier.secondsSinceReference;
    }

    public FMDate adding(double seconds) {
        return new FMDate(secondsSinceReference + seconds);
    }

    public boolean isBefore(FMDate other) { return compareTo(other) < 0; }

    public boolean isAfter(FMDate other) { return compareTo(other) > 0; }

    @Override public int compareTo(FMDate other) {
        return Double.compare(secondsSinceReference, other.secondsSinceReference);
    }

    @Override public String toString() {
        return java.time.Instant.ofEpochMilli(epochMilliseconds()).toString();
    }

    @Override public boolean equals(Object other) {
        return other instanceof FMDate d
            && Double.compare(secondsSinceReference, d.secondsSinceReference) == 0;
    }

    @Override public int hashCode() { return Double.hashCode(secondsSinceReference); }
}
