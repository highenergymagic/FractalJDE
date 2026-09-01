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

import org.fractalmicro.core.Log;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Who is allowed to talk to a port.
 *
 * A named port with no security descriptor is open to whatever else runs on the machine:
 * anyone can connect, and anyone can stand up a port of the same name first and be
 * connected to in the real one's place. Neither is wanted. Every port this system serves
 * is fenced to the account running it, which is expressed as a security descriptor built
 * once and given to every port as it is made.
 *
 * The descriptor is written in SDDL, the string form Windows documents for exactly this,
 * granting the current user and the system account, protected so nothing is inherited, and
 * naming no one else. Building it by hand out of ACLs would be the same fence with more
 * ways to get a byte wrong.
 */
public final class Security {
    private Security() {}

    private static final SymbolLookup ADVAPI = Native.library("advapi32.dll");
    private static final SymbolLookup K32 = Native.library("kernel32.dll");

    private static final int TOKEN_QUERY = 0x0008;
    private static final int TOKEN_USER = 1;              // TOKEN_INFORMATION_CLASS
    private static final int SDDL_REVISION_1 = 1;

    private static final MethodHandle GET_CURRENT_PROCESS = Native.handle(K32,
        "GetCurrentProcess", FunctionDescriptor.of(ValueLayout.ADDRESS));

    private static final MethodHandle OPEN_PROCESS_TOKEN = Native.handle(ADVAPI,
        "OpenProcessToken", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle GET_TOKEN_INFORMATION = Native.handle(ADVAPI,
        "GetTokenInformation", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle CONVERT_SID_TO_STRING = Native.handle(ADVAPI,
        "ConvertSidToStringSidW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle CONVERT_SDDL = Native.handle(ADVAPI,
        "ConvertStringSecurityDescriptorToSecurityDescriptorW",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle LOCAL_FREE = Native.handle(K32,
        "LocalFree", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle CLOSE_HANDLE = Native.handle(K32,
        "CloseHandle", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    /**
     * A SECURITY_ATTRIBUTES fencing a port to this account, or NULL if one could not be
     * built. Computed once and shared: the descriptor it points at must outlive every port
     * that uses it, so both live in the global arena for the life of the process.
     */
    private static volatile MemorySegment attributes;
    private static volatile boolean tried;

    public static synchronized MemorySegment userOnly() {
        if (tried) return attributes == null ? MemorySegment.NULL : attributes;
        tried = true;
        try {
            attributes = build();
        } catch (Throwable t) {
            Log.error("a port could not be fenced to this account; using the default", t);
            attributes = null;
        }
        return attributes == null ? MemorySegment.NULL : attributes;
    }

    private static MemorySegment build() throws Throwable {
        String sid = currentUserSid();
        if (sid == null) return null;

        // Grant generic-all to this user and to the system account, protected (P) so no
        // inherited permissions widen it, and no one else named at all.
        String sddl = "D:P(A;;GA;;;" + sid + ")(A;;GA;;;SY)";

        MemorySegment descriptor;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment text = Native.wide(arena, sddl);
            MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
            int ok = (int) CONVERT_SDDL.invokeExact(text, SDDL_REVISION_1, out,
                                                    MemorySegment.NULL);
            if (ok == 0) {
                Log.info("the port security descriptor could not be built: error "
                         + Streams.lastError());
                return null;
            }
            MemorySegment local = out.get(ValueLayout.ADDRESS, 0);
            // The system allocated it with LocalAlloc; copy it somewhere that lives as long
            // as the process, then let the system's copy go.
            long size = descriptorSize(local);
            descriptor = Native.GLOBAL.allocate(size);
            MemorySegment.copy(local.reinterpret(size), 0, descriptor, 0, size);
            MemorySegment freed = (MemorySegment) LOCAL_FREE.invokeExact(local);
        }

        // SECURITY_ATTRIBUTES { DWORD nLength; LPVOID lpSecurityDescriptor; BOOL bInheritHandle; }
        MemorySegment sa = Native.GLOBAL.allocate(24);
        sa.set(ValueLayout.JAVA_INT, 0, 24);
        sa.set(ValueLayout.ADDRESS, 8, descriptor);
        sa.set(ValueLayout.JAVA_INT, 16, 0);              // do not let child processes inherit
        Log.info("ports are fenced to " + sid);
        return sa;
    }

    /** The current process's user SID, in string form, or null. */
    private static String currentUserSid() throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment process = (MemorySegment) GET_CURRENT_PROCESS.invokeExact();
            MemorySegment tokenOut = arena.allocate(ValueLayout.ADDRESS);
            if ((int) OPEN_PROCESS_TOKEN.invokeExact(process, TOKEN_QUERY, tokenOut) == 0) {
                return null;
            }
            // The arena frees the slot the handle value sits in; the handle itself is a
            // kernel object this process now owns and has to give back by hand.
            MemorySegment token = tokenOut.get(ValueLayout.ADDRESS, 0);
            try {
                // Ask how much the user information needs, then ask for it.
                MemorySegment needed = arena.allocate(ValueLayout.JAVA_INT);
                int probe = (int) GET_TOKEN_INFORMATION.invokeExact(token, TOKEN_USER,
                    MemorySegment.NULL, 0, needed);
                int size = needed.get(ValueLayout.JAVA_INT, 0);
                if (size <= 0 || size > 4096) return null;
                MemorySegment info = arena.allocate(size);
                if ((int) GET_TOKEN_INFORMATION.invokeExact(token, TOKEN_USER, info, size,
                                                            needed) == 0) {
                    return null;
                }

                // TOKEN_USER begins with SID_AND_ATTRIBUTES, whose first field is the SID.
                MemorySegment psid = info.get(ValueLayout.ADDRESS, 0);
                MemorySegment stringOut = arena.allocate(ValueLayout.ADDRESS);
                if ((int) CONVERT_SID_TO_STRING.invokeExact(psid, stringOut) == 0) return null;
                MemorySegment str = stringOut.get(ValueLayout.ADDRESS, 0);
                String sid = Native.readWide(str.reinterpret(600));
                MemorySegment freed = (MemorySegment) LOCAL_FREE.invokeExact(str);
                return sid.isBlank() ? null : sid;
            } finally {
                int closed = (int) CLOSE_HANDLE.invokeExact(token);
            }
        }
    }

    /**
     * How long a self-relative security descriptor is.
     *
     * GetSecurityDescriptorLength answers exactly; asking it saves guessing, and the
     * descriptor has to be copied whole for it to keep meaning anything.
     */
    private static long descriptorSize(MemorySegment descriptor) throws Throwable {
        return (int) GET_SD_LENGTH.invokeExact(descriptor) & 0xFFFFFFFFL;
    }

    private static final MethodHandle GET_SD_LENGTH = Native.handle(ADVAPI,
        "GetSecurityDescriptorLength", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS));
}
