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
package org.fractalmicro.dyld;

/**
 * What the loader has to say, said without help.
 *
 * The loader runs before the libraries it maps, so it cannot write to the system log: that
 * code is in a library it has not loaded. The error stream instead, and only when asked.
 * dyld turns its tracing on with an environment variable for the same reason.
 */
public final class Trace {
    private Trace() {}

    /** Set this to see each image as it is mapped. */
    public static final String PRINT_LIBRARIES = "DYLD_PRINT_LIBRARIES";

    private static final boolean PRINTING =
        System.getenv(PRINT_LIBRARIES) != null
        || Boolean.getBoolean("org.fractalmicro.dyld.trace");

    public static void say(String what) {
        if (PRINTING) System.err.println("dyld: " + what);
    }

    /** Something worth saying whether or not anyone asked for tracing. */
    public static void warn(String what) {
        System.err.println("dyld: " + what);
    }
}
