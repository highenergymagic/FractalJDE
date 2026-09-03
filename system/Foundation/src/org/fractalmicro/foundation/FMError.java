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
 * Something that went wrong, as a value rather than as a throw.
 *
 * A file that is not there, a name that is taken, a server not answering. Foundation
 * returns these, because what a caller does with one is usually put it in front of a
 * person.
 *
 * A domain scopes the number, so there is no register everything must agree on. What a
 * person is told and what a programmer is told are separate fields: they are different
 * sentences.
 */
public final class FMError {

    /** Whose error this is. */
    public static final FMString POSIX_DOMAIN = FMString.of("FMPOSIXErrorDomain");
    public static final FMString COCOA_DOMAIN = FMString.of("FMCocoaErrorDomain");
    public static final FMString OS_STATUS_DOMAIN = FMString.of("FMOSStatusErrorDomain");

    private final FMString domain;
    private final long code;
    private final FMString description;
    private final FMString reason;
    private final FMError underlying;

    private FMError(FMString domain, long code, FMString description, FMString reason,
                    FMError underlying) {
        this.domain = domain;
        this.code = code;
        this.description = description;
        this.reason = reason;
        this.underlying = underlying;
    }

    public static FMError of(FMString domain, long code, FMString description) {
        return new FMError(domain, code, description, FMString.EMPTY, null);
    }

    /**
     * One made from something the runtime threw.
     *
     * The message a runtime failure carries was written for whoever is reading a log, so
     * it goes in as the reason rather than as what a person is shown.
     */
    public static FMError from(Throwable thrown, FMString description) {
        return new FMError(COCOA_DOMAIN, 0, description,
                           FMString.describing(thrown.getMessage()), null);
    }

    public FMString domain() { return domain; }

    public long code() { return code; }

    /** What a person is told: one sentence, about what they were trying to do. */
    public FMString description() { return description; }

    /** Why, where there is more to say than the description says. */
    public FMString reason() { return reason; }

    /** What went wrong underneath this, where one failure caused another. */
    public FMError underlying() { return underlying; }

    public FMError becauseOf(FMError cause) {
        return new FMError(domain, code, description, reason, cause);
    }

    public FMError withReason(FMString why) {
        return new FMError(domain, code, description, why, underlying);
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder(description.toString());
        if (!reason.isEmpty()) sb.append(": ").append(reason);
        if (code != 0) sb.append(" (").append(domain).append(' ').append(code).append(')');
        return sb.toString();
    }
}
