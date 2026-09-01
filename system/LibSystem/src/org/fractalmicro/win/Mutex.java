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
 * Claiming a name, once, for everyone.
 *
 * Asking whether a name is taken and then taking it is two steps, and between them anything
 * can happen: two programs both look, both see nothing, and both start. No amount of
 * looking harder fixes that. What fixes it is one step that both claims and answers, and
 * the operating system has one: a named mutex is created and says, in the same call,
 * whether it already existed.
 *
 * So a service claims its name here before it listens. The one that loses the race finds
 * out from the same call that made the attempt, which is the only way to find out safely.
 */
public final class Mutex implements AutoCloseable {

    private static final SymbolLookup K32 = Native.library("kernel32.dll");

    private static final int ERROR_ALREADY_EXISTS = 183;

    private static final MethodHandle CREATE_MUTEX = Native.handle(K32,
        "CreateMutexW", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle CLOSE_HANDLE = Native.handle(K32,
        "CloseHandle", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private final MemorySegment handle;
    private final String name;
    private volatile boolean held;

    private Mutex(String name, MemorySegment handle) {
        this.name = name;
        this.handle = handle;
        this.held = true;
    }

    /**
     * Claims a name, or returns null if something else holds it. The claim lasts until this
     * is closed or the process ends, whichever comes first: a process that dies without
     * cleaning up still releases the name, so this is safe for something that must not run
     * twice.
     */
    public static Mutex claim(String name) {
        try (Arena arena = Arena.ofConfined()) {
            // Session-local, which is the scope wanted: one desktop, one of each.
            MemorySegment wide = Native.wide(arena, "Local\\" + name);
            MemorySegment handle = (MemorySegment) CREATE_MUTEX.invokeExact(
                MemorySegment.NULL, 0, wide);
            if (handle.address() == 0) return null;
            if (Streams.lastError() == ERROR_ALREADY_EXISTS) {
                int ignored = (int) CLOSE_HANDLE.invokeExact(handle);
                return null;
            }
            return new Mutex(name, handle);
        } catch (Throwable t) {
            org.fractalmicro.core.Log.error("could not claim the name " + name, t);
            return null;
        }
    }

    /** Whether a name is spoken for, which is only ever a hint: it may change at once. */
    public static boolean taken(String name) {
        Mutex attempt = claim(name);
        if (attempt == null) return true;
        attempt.close();
        return false;
    }

    public String name() { return name; }

    public boolean isHeld() { return held; }

    @Override public void close() {
        if (!held) return;
        held = false;
        try {
            int ignored = (int) CLOSE_HANDLE.invokeExact(handle);
        } catch (Throwable ignored) {
            // The name goes back when the process ends in any case.
        }
    }
}
