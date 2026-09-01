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

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * Logging out, restarting, shutting down, sleeping and locking.
 *
 * A program can pretend at these. A shell cannot: with Explorer gone there is no other
 * way to turn the machine off. Shutting down and restarting need a privilege that is
 * present but switched off in every process, so it is turned on first.
 *
 * Every method here does the real thing. {@link #setDryRun} exists so the checking run
 * can prove the plumbing without ending anybody's session.
 */
public final class Session {
    private Session() {}

    private static final SymbolLookup U32 = Native.library("user32.dll");
    private static final SymbolLookup ADVAPI = Native.library("advapi32.dll");
    private static final SymbolLookup K32 = Native.library("kernel32.dll");
    private static final SymbolLookup POWER = Native.library("powrprof.dll");

    private static final int EWX_LOGOFF = 0x00000000;
    private static final int EWX_SHUTDOWN = 0x00000001;
    private static final int EWX_REBOOT = 0x00000002;
    private static final int EWX_POWEROFF = 0x00000008;
    private static final int EWX_FORCEIFHUNG = 0x00000010;

    /** SHTDN_REASON, logged by the event log as a planned shutdown by the user. */
    private static final int SHTDN_REASON = 0x40000000 | 0x00000000;

    private static final int TOKEN_ADJUST_PRIVILEGES = 0x0020;
    private static final int TOKEN_QUERY = 0x0008;
    private static final int SE_PRIVILEGE_ENABLED = 0x00000002;

    private static final MethodHandle EXIT_WINDOWS = Native.handle(U32,
        "ExitWindowsEx", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

    private static final MethodHandle LOCK_WORKSTATION = Native.handle(U32,
        "LockWorkStation", FunctionDescriptor.of(ValueLayout.JAVA_INT));

    private static final MethodHandle SET_SUSPEND_STATE = Native.handle(POWER,
        "SetSuspendState", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_BYTE, ValueLayout.JAVA_BYTE, ValueLayout.JAVA_BYTE));

    private static final MethodHandle GET_CURRENT_PROCESS = Native.handle(K32,
        "GetCurrentProcess", FunctionDescriptor.of(ValueLayout.ADDRESS));

    private static final MethodHandle CLOSE_HANDLE = Native.handle(K32,
        "CloseHandle", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle OPEN_PROCESS_TOKEN = Native.handle(ADVAPI,
        "OpenProcessToken", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle LOOKUP_PRIVILEGE = Native.handle(ADVAPI,
        "LookupPrivilegeValueW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle ADJUST_PRIVILEGES = Native.handle(ADVAPI,
        "AdjustTokenPrivileges", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static volatile boolean dryRun;

    /** When set, nothing actually happens; the call is logged and reported as done. */
    public static void setDryRun(boolean value) { dryRun = value; }

    public static boolean isDryRun() { return dryRun; }

    /* ------------------------------------------------------------ actions */

    public static boolean logOut(boolean force) {
        return exitWindows(EWX_LOGOFF | (force ? EWX_FORCEIFHUNG : 0), "log out");
    }

    public static boolean restart(boolean force) {
        enableShutdownPrivilege();
        return exitWindows(EWX_REBOOT | (force ? EWX_FORCEIFHUNG : 0), "restart");
    }

    public static boolean shutDown(boolean force) {
        enableShutdownPrivilege();
        return exitWindows(EWX_SHUTDOWN | EWX_POWEROFF | (force ? EWX_FORCEIFHUNG : 0), "shut down");
    }

    private static boolean exitWindows(int flags, String what) {
        Log.info("session: " + what + (dryRun ? " (dry run)" : ""));
        if (dryRun) return true;
        try {
            return (int) EXIT_WINDOWS.invokeExact(flags, SHTDN_REASON) != 0;
        } catch (Throwable t) {
            Log.error("could not " + what, t);
            return false;
        }
    }

    /** Sleep. The three arguments are hibernate, force and disable wake events. */
    public static boolean sleep() {
        Log.info("session: sleep" + (dryRun ? " (dry run)" : ""));
        if (dryRun) return true;
        try {
            return (int) SET_SUSPEND_STATE.invokeExact((byte) 0, (byte) 0, (byte) 0) != 0;
        } catch (Throwable t) {
            Log.error("could not sleep", t);
            return false;
        }
    }

    public static boolean lock() {
        Log.info("session: lock" + (dryRun ? " (dry run)" : ""));
        if (dryRun) return true;
        try {
            return (int) LOCK_WORKSTATION.invokeExact() != 0;
        } catch (Throwable t) {
            Log.error("could not lock the screen", t);
            return false;
        }
    }

    /* --------------------------------------------------------- privileges */

    /**
     * Turns on SeShutdownPrivilege for this process. Every account has it; it starts
     * switched off, and shutting down or restarting fails quietly without it. Enabling
     * it shuts nothing down by itself.
     */
    public static boolean enableShutdownPrivilege() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment process = (MemorySegment) GET_CURRENT_PROCESS.invokeExact();
            MemorySegment token = arena.allocate(ValueLayout.ADDRESS);
            int opened = (int) OPEN_PROCESS_TOKEN.invokeExact(
                process, TOKEN_ADJUST_PRIVILEGES | TOKEN_QUERY, token);
            if (opened == 0) return false;

            // A kernel handle, not arena memory: it has to be closed however this leaves.
            MemorySegment openedToken = token.get(ValueLayout.ADDRESS, 0);
            try {
                MemorySegment name = Native.wide(arena, "SeShutdownPrivilege");
                MemorySegment luid = arena.allocate(8);
                int found = (int) LOOKUP_PRIVILEGE.invokeExact(MemorySegment.NULL, name, luid);
                if (found == 0) return false;

                // TOKEN_PRIVILEGES: a count, then one LUID_AND_ATTRIBUTES of twelve bytes.
                MemorySegment privileges = arena.allocate(16);
                privileges.fill((byte) 0);
                privileges.set(ValueLayout.JAVA_INT, 0, 1);
                MemorySegment.copy(luid, 0, privileges, 4, 8);
                privileges.set(ValueLayout.JAVA_INT, 12, SE_PRIVILEGE_ENABLED);

                int adjusted = (int) ADJUST_PRIVILEGES.invokeExact(
                    openedToken, 0, privileges, 0, MemorySegment.NULL, MemorySegment.NULL);
                return adjusted != 0;
            } finally {
                int closed = (int) CLOSE_HANDLE.invokeExact(openedToken);
            }
        } catch (Throwable t) {
            Log.error("could not take the shutdown privilege", t);
            return false;
        }
    }

    /* -------------------------------------------------------- shell state */

    /** True when this program is registered as the shell for this account. */
    public static boolean registeredAsShell() {
        String shell = Registry.string(Registry.HKEY_CURRENT_USER,
            "Software\\Microsoft\\Windows NT\\CurrentVersion\\Winlogon", "Shell");
        return shell != null && shell.toLowerCase(java.util.Locale.ROOT).contains("fractal");
    }

    /** True when Explorer is running, so this desktop is a program rather than the shell. */
    public static boolean explorerRunning() {
        return ProcessHandle.allProcesses()
            .anyMatch(p -> p.info().command()
                .map(c -> c.toLowerCase(java.util.Locale.ROOT).endsWith("\\explorer.exe"))
                .orElse(false));
    }

    /** Whether this is acting as the shell rather than as a program on top of one. */
    public static boolean actingAsShell() {
        return registeredAsShell() || !explorerRunning();
    }
}
