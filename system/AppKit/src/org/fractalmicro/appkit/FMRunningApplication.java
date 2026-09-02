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
package org.fractalmicro.appkit;

import org.fractalmicro.foundation.FMString;

/**
 * Something running, as a program looking at the list sees it.
 *
 * A copy rather than a handle, for the reason NSRunningApplication is one: it is in another
 * process, cannot be passed across, and may have stopped by the time anybody reads the line.
 * What a listing needs is what was true when it was asked.
 *
 * The number is this system's own; the host is the host's number for the process it runs in,
 * which for several tasks is the same process. Both are shown because both are real.
 */
public record FMRunningApplication(int processIdentifier, int parentProcessIdentifier,
                                   FMString localizedName, FMString kind,
                                   FMString host, FMString state) {

    /** Whether it has stopped, and is only still listed because nobody has reaped it. */
    public boolean isTerminated() {
        return !state.sameAs(FMString.of("running"));
    }
}
