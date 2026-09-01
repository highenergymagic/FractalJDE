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
 * Saying what happened.
 *
 * A program writes here rather than to the runtime's own output, so that what it says ends
 * up wherever this system keeps its log rather than wherever it happened to be started
 * from. It is the same log the system writes to, which is the point: one file, in order.
 */
public final class FMLog {
    private FMLog() {}

    public static void say(FMString what) {
        org.fractalmicro.core.Log.info(what == null ? "" : what.toString());
    }

    public static void say(String what) { org.fractalmicro.core.Log.info(what); }

    public static void wrong(FMString what, Throwable why) {
        org.fractalmicro.core.Log.error(what == null ? "" : what.toString(), why);
    }

    public static void wrong(String what, Throwable why) {
        org.fractalmicro.core.Log.error(what, why);
    }
}
