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
package org.fractalmicro.scripting;

import org.fractalmicro.foundation.FMString;

/**
 * A command that could not be done, said the way a reply says it.
 *
 * Thrown by a handler and caught by the manager, which is as far as it travels: what
 * crosses to the other program is a number and a sentence, because an exception is a
 * thing in one process and a reply is a thing on a wire.
 */
public final class FMScriptError extends RuntimeException {

    private final long number;
    private final FMString said;

    public FMScriptError(FMString said) {
        this(FMAppleEventManager.EVENT_FAILED, said);
    }

    public FMScriptError(long number, FMString said) {
        super(said == null ? "" : said.toString());
        this.number = number;
        this.said = said == null ? FMString.EMPTY : said;
    }

    /** The number a reply carries, which is the one Cocoa's own errors carry. */
    public long number() { return number; }

    /** The same thing in words, for whoever is reading rather than switching. */
    public FMString said() { return said; }
}
