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

import org.fractalmicro.fs.FS;

/**
 * How many bytes, said the way a person reads it.
 *
 * NSByteCountFormatter, and it is a class in Cocoa rather than a function for a reason that
 * shows up immediately: there are two right answers. "1.2 GB" is what a file's size is for,
 * and "1,288,490,188 bytes" is what Get Info shows underneath it, and a program wanting one
 * usually wants the other beside it.
 *
 * The counting is the file layer's, which has done it since before this existed. What is
 * new is that it has the name Cocoa gives it, so a program formatting a size does not have
 * to know that the answer comes from the file layer at all.
 */
public final class FMByteCountFormatter {

    private static final FMByteCountFormatter SHARED = new FMByteCountFormatter();

    private FMByteCountFormatter() {}

    /** One to use, since it holds nothing and there is no reason for a second. */
    public static FMByteCountFormatter formatter() { return SHARED; }

    /** The short form: what a file's size is written as in a listing. */
    public FMString stringFromByteCount(long bytes) {
        return FMString.of(FS.formatBytes(bytes));
    }

    /** The long form, to the byte, which is what Get Info shows under the short one. */
    public FMString exactStringFromByteCount(long bytes) {
        return FMString.of(FS.formatExactBytes(bytes));
    }

    /**
     * Both, the way Get Info says them: what it is, and what it takes up.
     *
     * A file's size and the room it occupies are different numbers, because a disk hands
     * out space in blocks. Saying only the first is the answer to a question nobody asked
     * when the disk is full.
     */
    public FMString stringFromByteCount(long bytes, long onDisk) {
        return FMString.of(FS.formatSize(bytes, onDisk));
    }
}
