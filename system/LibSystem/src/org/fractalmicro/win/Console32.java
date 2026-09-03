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

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.List;

/**
 * Starting a program in a console of its own.
 *
 * The runtime can start a process and say nothing about its console, and a console is what
 * a terminal is made of. Asking Windows directly also means the window can start hidden
 * and be shown once it is where it belongs, rather than appearing somewhere else first.
 */
public final class Console32 {
    private Console32() {}

    /** A console of its own rather than the one the caller has, or has not. */
    public static final int CREATE_NEW_CONSOLE = 0x00000010;

    /** That wShowWindow means anything at all. Without it the field is ignored. */
    private static final int STARTF_USESHOWWINDOW = 0x00000001;

    private static final int SW_HIDE = 0;

    /** STARTUPINFOW, and where the two fields that matter sit inside it. */
    private static final int STARTUPINFO_SIZE = 104;
    private static final int OFFSET_CB = 0;
    private static final int OFFSET_FLAGS = 60;
    private static final int OFFSET_SHOW_WINDOW = 64;

    /** PROCESS_INFORMATION: two handles, then the two numbers. */
    private static final int PROCESS_INFORMATION_SIZE = 24;
    private static final int OFFSET_PROCESS = 0;
    private static final int OFFSET_THREAD = 8;
    private static final int OFFSET_PROCESS_ID = 16;

    private static final SymbolLookup K32 = Native.library("kernel32.dll");

    private static final MethodHandle CREATE_PROCESS = Native.handle(K32,
        "CreateProcessW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS));

    private static final MethodHandle CLOSE_HANDLE = Native.handle(K32,
        "CloseHandle", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    /**
     * Starts a program with a console window of its own, hidden or shown.
     *
     * Answers the process id, or 0. Hidden is what a terminal wants: the window is put
     * inside one and shown there, and a console that appeared first would be a window
     * flashing up in the middle of the screen on the way to somewhere else.
     */
    public static long startWithConsole(List<String> command, java.io.File directory,
                                        boolean visible) {
        if (command == null || command.isEmpty()) return 0;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment startup = arena.allocate(STARTUPINFO_SIZE);
            startup.fill((byte) 0);
            startup.set(ValueLayout.JAVA_INT, OFFSET_CB, STARTUPINFO_SIZE);
            if (!visible) {
                startup.set(ValueLayout.JAVA_INT, OFFSET_FLAGS, STARTF_USESHOWWINDOW);
                startup.set(ValueLayout.JAVA_SHORT, OFFSET_SHOW_WINDOW, (short) SW_HIDE);
            }
            MemorySegment about = arena.allocate(PROCESS_INFORMATION_SIZE);
            about.fill((byte) 0);

            // CreateProcess writes into the command line it is given, so it cannot be a
            // constant. A wide buffer of our own is what the documentation asks for.
            MemorySegment line = Native.wide(arena, commandLine(command));
            MemorySegment where = directory == null ? MemorySegment.NULL
                : Native.wide(arena, directory.getAbsolutePath());

            int ok = (int) CREATE_PROCESS.invokeExact(
                MemorySegment.NULL, line, MemorySegment.NULL, MemorySegment.NULL,
                0, CREATE_NEW_CONSOLE, MemorySegment.NULL, where, startup, about);
            if (ok == 0) return 0;

            long pid = about.get(ValueLayout.JAVA_INT, OFFSET_PROCESS_ID) & 0xFFFFFFFFL;
            // The handles are this process's copies. The program goes on running without
            // them; holding them only keeps the entry alive after it has gone.
            close(about.get(ValueLayout.ADDRESS, OFFSET_PROCESS));
            close(about.get(ValueLayout.ADDRESS, OFFSET_THREAD));
            return pid;
        } catch (Throwable wouldNotStart) {
            return 0;
        }
    }

    /**
     * The words as one command line, quoted the way Windows takes them apart again.
     *
     * There is one command line, not a list, and every program that wants a list has to
     * agree about how it was written down. A word with a space in it is quoted; a quote
     * inside one is escaped, and so are the backslashes in front of it.
     */
    static String commandLine(List<String> words) {
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (!line.isEmpty()) line.append(' ');
            if (!word.isEmpty() && word.indexOf(' ') < 0 && word.indexOf('"') < 0
                    && word.indexOf('\t') < 0) {
                line.append(word);
                continue;
            }
            line.append('"');
            int slashes = 0;
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                if (c == '\\') {
                    slashes++;
                    continue;
                }
                // Backslashes double only when a quote is what follows them. A run in the
                // middle of a path means one backslash each, as it reads.
                line.append("\\".repeat(c == '"' ? slashes * 2 + 1 : slashes));
                slashes = 0;
                line.append(c);
            }
            // At the end they always double, because the closing quote is what follows.
            line.append("\\".repeat(slashes * 2)).append('"');
        }
        return line.toString();
    }

    private static void close(MemorySegment handle) {
        try {
            if (handle != null && !handle.equals(MemorySegment.NULL)) {
                int ignored = (int) CLOSE_HANDLE.invokeExact(handle);
            }
        } catch (Throwable alreadyGone) {
            // A handle that will not close is one this process is done with anyway.
        }
    }
}
