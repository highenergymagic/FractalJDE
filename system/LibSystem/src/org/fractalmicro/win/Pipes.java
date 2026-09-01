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
import java.util.ArrayList;
import java.util.List;

/**
 * Named pipes: how one program here talks to another.
 *
 * A port is a name a service listens on and a client sends to. Windows has exactly that in
 * the named pipe: the server creates \\.\pipe\name and waits, the client opens the same
 * name and writes, and the kernel carries the bytes and can say who sent them.
 *
 * Everything above this layer sees only a named port, so the rest of the system can be
 * written as though it had Mach ports.
 */
public final class Pipes {
    private Pipes() {}

    private static final SymbolLookup K32 = Native.library("kernel32.dll");

    public static final String PREFIX = "\\\\.\\pipe\\";

    private static final int PIPE_ACCESS_DUPLEX = 0x3;
    private static final int PIPE_TYPE_MESSAGE = 0x4;
    private static final int PIPE_READMODE_MESSAGE = 0x2;
    private static final int PIPE_WAIT = 0x0;
    // These ports are for local programs only; nothing off the machine belongs on them.
    private static final int PIPE_REJECT_REMOTE_CLIENTS = 0x8;
    // Fail rather than add an instance to a name that already exists. Only the first
    // instance may ask for this, so a failure means somebody else got there first.
    private static final int FILE_FLAG_FIRST_PIPE_INSTANCE = 0x00080000;
    private static final int PIPE_UNLIMITED_INSTANCES = 255;
    private static final int GENERIC_READ = 0x80000000;
    private static final int GENERIC_WRITE = 0x40000000;
    private static final int OPEN_EXISTING = 3;
    private static final int BUFFER = 64 * 1024;
    // Ceiling on one message. Requests and replies are small; anything past this is a
    // client growing the server's heap, and is dropped rather than answered.
    private static final int MAX_MESSAGE = 8 * 1024 * 1024;
    private static final int INVALID = -1;

    private static final MethodHandle CREATE_NAMED_PIPE = Native.handle(K32,
        "CreateNamedPipeW", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle CONNECT_NAMED_PIPE = Native.handle(K32,
        "ConnectNamedPipe", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle DISCONNECT_NAMED_PIPE = Native.handle(K32,
        "DisconnectNamedPipe", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle CREATE_FILE = Native.handle(K32,
        "CreateFileW", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle WAIT_NAMED_PIPE = Native.handle(K32,
        "WaitNamedPipeW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static final MethodHandle READ_FILE = Native.handle(K32,
        "ReadFile", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle WRITE_FILE = Native.handle(K32,
        "WriteFile", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle FLUSH = Native.handle(K32,
        "FlushFileBuffers", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle CLOSE_HANDLE = Native.handle(K32,
        "CloseHandle", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle FIND_FIRST_FILE = Native.handle(K32,
        "FindFirstFileW", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle FIND_NEXT_FILE = Native.handle(K32,
        "FindNextFileW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle FIND_CLOSE = Native.handle(K32,
        "FindClose", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    /** One end of a connection: something to read from and write to. */
    public static final class Port implements AutoCloseable {
        final MemorySegment handle;
        private final boolean server;
        private volatile boolean closed;

        Port(MemorySegment handle, boolean server) {
            this.handle = handle;
            this.server = server;
        }

        public boolean isOpen() { return !closed; }

        /** Sends one message whole. The other end reads it whole. */
        public boolean write(byte[] bytes) {
            if (closed) return false;
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment buffer = arena.allocate(bytes.length);
                MemorySegment.copy(bytes, 0, buffer, ValueLayout.JAVA_BYTE, 0, bytes.length);
                MemorySegment written = arena.allocate(ValueLayout.JAVA_INT);
                int ok = (int) WRITE_FILE.invokeExact(handle, buffer, bytes.length, written,
                                                      MemorySegment.NULL);
                int flushed = (int) FLUSH.invokeExact(handle);
                return ok != 0 && written.get(ValueLayout.JAVA_INT, 0) == bytes.length;
            } catch (Throwable t) {
                return false;
            }
        }

        /** Waits for one message and answers it, or null when the other end has gone. */
        public byte[] read() {
            if (closed) return null;
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment buffer = arena.allocate(BUFFER);
                MemorySegment got = arena.allocate(ValueLayout.JAVA_INT);
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                while (true) {
                    int ok = (int) READ_FILE.invokeExact(handle, buffer, BUFFER, got,
                                                         MemorySegment.NULL);
                    int count = got.get(ValueLayout.JAVA_INT, 0);
                    if (count > 0) {
                        byte[] chunk = new byte[count];
                        MemorySegment.copy(buffer, ValueLayout.JAVA_BYTE, 0, chunk, 0, count);
                        out.write(chunk);
                    }
                    if (out.size() > MAX_MESSAGE) {
                        Log.info("a message ran past " + MAX_MESSAGE + " bytes; dropping it");
                        return null;
                    }
                    if (ok != 0) break;
                    // More of the same message is waiting; anything else is the end.
                    if (Streams.lastError() != 234) return out.size() > 0 ? out.toByteArray() : null;
                }
                return out.toByteArray();
            } catch (Throwable t) {
                return null;
            }
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            try {
                if (server) {
                    int ignored = (int) DISCONNECT_NAMED_PIPE.invokeExact(handle);
                }
                int ignored = (int) CLOSE_HANDLE.invokeExact(handle);
            } catch (Throwable ignored) {
                // Nothing useful to do if a port will not close.
            }
        }
    }

    /* -------------------------------------------------------------- serving */

    public static Port listen(String name) {
        return listen(name, false);
    }

    /**
     * Creates one instance of a named port and blocks until a client connects. Each
     * connection needs its own instance, so a service handling more than one client calls
     * this again while the first is still open.
     *
     * The port is ACLed to this account and rejects remote clients.
     *
     * @param firstInstance ask for FILE_FLAG_FIRST_PIPE_INSTANCE. A failure then means the
     *                      name was already standing before this service started, which is
     *                      squatting rather than a race.
     * @return the port, or null if it could not be created
     */
    public static Port listen(String name, boolean firstInstance) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment path = Native.wide(arena, PREFIX + name);
            int openMode = PIPE_ACCESS_DUPLEX
                | (firstInstance ? FILE_FLAG_FIRST_PIPE_INSTANCE : 0);
            MemorySegment handle = (MemorySegment) CREATE_NAMED_PIPE.invokeExact(
                path, openMode,
                PIPE_TYPE_MESSAGE | PIPE_READMODE_MESSAGE | PIPE_WAIT | PIPE_REJECT_REMOTE_CLIENTS,
                PIPE_UNLIMITED_INSTANCES, BUFFER, BUFFER, 0, Security.userOnly());
            if (handle.address() == INVALID || handle.address() == 0) {
                int error = Streams.lastError();
                // ERROR_PIPE_BUSY (231) here means the name exists and we asked to be
                // first: something that is not us is already serving it.
                if (firstInstance && (error == 231 || error == 5)) {
                    Log.info("the port " + name + " was already standing when this started"
                             + " (error " + error + "); refusing to serve it");
                } else {
                    Log.info("could not make the port " + name + ": error " + error);
                }
                return null;
            }
            int connected = (int) CONNECT_NAMED_PIPE.invokeExact(handle, MemorySegment.NULL);
            // ERROR_PIPE_CONNECTED (535): a client arrived between creating the port and
            // waiting on it. Still a connection.
            if (connected == 0 && Streams.lastError() != 535) {
                int ignored = (int) CLOSE_HANDLE.invokeExact(handle);
                return null;
            }
            return new Port(handle, true);
        } catch (Throwable t) {
            Log.error("the port " + name + " could not be opened", t);
            return null;
        }
    }

    /* ------------------------------------------------------------ connecting */

    /** Opens a connection to a named port, waiting up to a moment for it to exist. */
    public static Port connect(String name, int waitMillis) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment path = Native.wide(arena, PREFIX + name);
            long deadline = System.nanoTime() + waitMillis * 1_000_000L;
            while (true) {
                MemorySegment handle = (MemorySegment) CREATE_FILE.invokeExact(
                    path, GENERIC_READ | GENERIC_WRITE, 0, MemorySegment.NULL,
                    OPEN_EXISTING, 0, MemorySegment.NULL);
                if (handle.address() != INVALID && handle.address() != 0) {
                    return new Port(handle, false);
                }
                if (System.nanoTime() >= deadline) return null;
                int waited = (int) WAIT_NAMED_PIPE.invokeExact(path, 50);
                if (waited == 0) {
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static final MethodHandle GET_SERVER_PID = Native.handle(K32,
        "GetNamedPipeServerProcessId", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    /**
     * The host PID serving a name, or -1.
     *
     * Connect, ask the kernel whose end this is, disconnect. This is how a job this process
     * never started still gets a real PID in the task listing.
     */
    public static long serverPidOf(String name) {
        Port port = connect(name, 200);
        if (port == null) return -1;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ValueLayout.JAVA_INT);
            int ok = (int) GET_SERVER_PID.invokeExact(port.handle, out);
            return ok == 0 ? -1 : out.get(ValueLayout.JAVA_INT, 0) & 0xFFFFFFFFL;
        } catch (Throwable t) {
            return -1;
        } finally {
            port.close();
        }
    }

    /** Whether anything is listening on a name right now. */
    public static boolean exists(String name) {
        for (String open : list()) {
            if (open.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** Every named port on this machine, which is how services are found. */
    public static List<String> list() {
        List<String> names = new ArrayList<>();
        try (Arena arena = Arena.ofConfined()) {
            // WIN32_FIND_DATAW: the name is 44 bytes in, and runs 260 characters.
            MemorySegment data = arena.allocate(592);
            MemorySegment pattern = Native.wide(arena, PREFIX + "*");
            MemorySegment find = (MemorySegment) FIND_FIRST_FILE.invokeExact(pattern, data);
            if (find.address() == INVALID || find.address() == 0) return names;
            try {
                do {
                    names.add(Native.readWide(data.asSlice(44)));
                } while ((int) FIND_NEXT_FILE.invokeExact(find, data) != 0);
            } finally {
                int ignored = (int) FIND_CLOSE.invokeExact(find);
            }
        } catch (Throwable t) {
            Log.error("the ports could not be listed", t);
        }
        return names;
    }
}
