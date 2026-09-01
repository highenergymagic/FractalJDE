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

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

/**
 * Plumbing for calling Windows itself. Everything this program needs from the system
 * goes through the foreign function interface: no command lines, no scripting hosts.
 *
 * Java 21 has this as a preview feature, so the build passes --enable-preview.
 */
public final class Native {
    private Native() {}

    public static final Linker LINKER = Linker.nativeLinker();
    public static final Arena GLOBAL = Arena.global();

    public static SymbolLookup library(String name) {
        return SymbolLookup.libraryLookup(name, GLOBAL);
    }

    public static MethodHandle handle(SymbolLookup lookup, String symbol, FunctionDescriptor descriptor) {
        MemorySegment address = lookup.find(symbol)
            .orElseThrow(() -> new UnsatisfiedLinkError("no symbol " + symbol));
        return LINKER.downcallHandle(address, descriptor);
    }

    /* --------------------------------------------------------- wide strings */

    /** Allocates a null-terminated UTF-16LE string for the W entry points. */
    public static MemorySegment wide(SegmentAllocator allocator, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_16LE);
        MemorySegment segment = allocator.allocate(bytes.length + 2L);
        MemorySegment.copy(bytes, 0, segment, ValueLayout.JAVA_BYTE, 0, bytes.length);
        segment.set(ValueLayout.JAVA_BYTE, bytes.length, (byte) 0);
        segment.set(ValueLayout.JAVA_BYTE, bytes.length + 1, (byte) 0);
        return segment;
    }

    /** Allocates room for a wide string of the given character count. */
    public static MemorySegment wideBuffer(SegmentAllocator allocator, int characters) {
        return allocator.allocate(characters * 2L + 2);
    }

    /** Reads a null-terminated UTF-16LE string out of a buffer. */
    public static String readWide(MemorySegment segment) {
        long size = segment.byteSize();
        StringBuilder sb = new StringBuilder();
        for (long i = 0; i + 1 < size; i += 2) {
            char c = (char) ((segment.get(ValueLayout.JAVA_BYTE, i) & 0xFF)
                           | ((segment.get(ValueLayout.JAVA_BYTE, i + 1) & 0xFF) << 8));
            if (c == 0) break;
            sb.append(c);
        }
        return sb.toString();
    }

    /** True when the call is available; used to fall back quietly on odd systems. */
    public static boolean available(SymbolLookup lookup, String symbol) {
        return lookup.find(symbol).isPresent();
    }
}
