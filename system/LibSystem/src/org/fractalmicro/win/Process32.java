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

/**
 * How big a process is, as the host system measures it.
 *
 * A task listing that cannot say what anything costs is just a list of names. The working
 * set, the memory a process actually has resident, is the number people mean when they ask
 * how big something is, so that is the one asked for here.
 */
public final class Process32 {
    private Process32() {}

    private static final SymbolLookup K32 = Native.library("kernel32.dll");
    private static final SymbolLookup PSAPI = Native.library("psapi.dll");

    private static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;
    private static final int INVALID = -1;

    private static final MethodHandle OPEN_PROCESS = Native.handle(K32,
        "OpenProcess", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

    private static final MethodHandle CLOSE_HANDLE = Native.handle(K32,
        "CloseHandle", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle TERMINATE_PROCESS = Native.handle(K32,
        "TerminateProcess", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static final MethodHandle GET_MEMORY_INFO = Native.handle(PSAPI,
        "GetProcessMemoryInfo", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static final int PROCESS_TERMINATE = 0x1;

    /**
     * Ends a process this program did not start. Used for a task that was adopted: there
     * is no handle to destroy, only a number, and this is what a number is good for.
     */
    public static boolean terminate(long pid) {
        if (pid <= 0) return false;
        try {
            MemorySegment handle = (MemorySegment) OPEN_PROCESS.invokeExact(
                PROCESS_TERMINATE, 0, (int) pid);
            if (handle.address() == 0 || handle.address() == INVALID) return false;
            try {
                return (int) TERMINATE_PROCESS.invokeExact(handle, 0) != 0;
            } finally {
                int ignored = (int) CLOSE_HANDLE.invokeExact(handle);
            }
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * The resident size of a process in bytes, or -1 when it cannot be asked, which
     * happens for a process that has ended, and for one this account may not look at.
     */
    public static long memoryOf(long pid) {
        if (pid <= 0) return -1;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handle = (MemorySegment) OPEN_PROCESS.invokeExact(
                PROCESS_QUERY_LIMITED_INFORMATION, 0, (int) pid);
            if (handle.address() == 0 || handle.address() == INVALID) return -1;
            try {
                // PROCESS_MEMORY_COUNTERS: a size, a fault count, then pairs of peak and
                // current sizes. The working set is the second of the first pair, 16 in.
                MemorySegment counters = arena.allocate(80);
                counters.set(ValueLayout.JAVA_INT, 0, 72);
                int ok = (int) GET_MEMORY_INFO.invokeExact(handle, counters, 72);
                if (ok == 0) return -1;
                return counters.get(ValueLayout.JAVA_LONG, 16);
            } finally {
                int ignored = (int) CLOSE_HANDLE.invokeExact(handle);
            }
        } catch (Throwable t) {
            return -1;
        }
    }
}
