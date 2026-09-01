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
package org.fractalmicro.win;

import java.io.File;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * How much room a file actually takes up.
 *
 * A file has two sizes. The logical size is how much data is in it, which is what most
 * things report. The size on disk is that rounded up to whole allocation blocks, or smaller
 * for a compressed or sparse file. Get Info shows both, and calling both of them "size" is
 * how people end up asking why one number is ninety megabytes larger than the other.
 *
 * The operating system will say, so it is asked rather than guessed at from a block size.
 */
public final class Files32 {
    private Files32() {}

    private static final SymbolLookup K32 = Native.library("kernel32.dll");
    private static final int INVALID_FILE_SIZE = -1;

    private static final MethodHandle GET_COMPRESSED_FILE_SIZE = Native.handle(K32,
        "GetCompressedFileSizeW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    /**
     * The room a file takes on its disk, in bytes, or -1 when the system will not say.
     *
     * This is the number that belongs in the parentheses after a size: the space the file
     * is actually using, which for almost everything is its data rounded up to the next
     * whole block, and for a sparse or compressed file is less than its data.
     */
    public static long allocatedSize(File file) {
        if (file == null || !file.isFile()) return -1;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment path = Native.wide(arena, file.getPath());
            MemorySegment high = arena.allocate(ValueLayout.JAVA_INT);
            int low = (int) GET_COMPRESSED_FILE_SIZE.invokeExact(path, high);
            if (low == INVALID_FILE_SIZE && Streams.lastError() != 0) return -1;
            return ((long) high.get(ValueLayout.JAVA_INT, 0) << 32) | (low & 0xFFFFFFFFL);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * The room a file takes, falling back to its own length when the system will not say.
     * A number that is close is better here than no number at all.
     */
    public static long allocatedSizeOrLength(File file) {
        long allocated = allocatedSize(file);
        return allocated >= 0 ? allocated : (file == null ? -1 : file.length());
    }
}
