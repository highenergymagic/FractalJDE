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

import java.io.File;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

/**
 * Alternate data streams, and the file identity NTFS keeps.
 *
 * A file on NTFS can hold more than one stream of bytes. The unnamed stream is the file
 * everything else sees; a named one, opened as "path:name", is a second fork on the same
 * file. That is a resource fork by another name, and it is what a Mac writing to an NTFS
 * volume uses: the streams are AFP_Resource and AFP_AfpInfo, so this writes what a Mac
 * would expect to find.
 *
 * Also here: the file reference number, NTFS's own identity for a file. It survives rename
 * and moves within the volume, which is how an alias follows its target.
 *
 * FAT, exFAT and most network shares have no streams. They report that by failing, and the
 * caller falls back to a sidecar.
 */
public final class Streams {
    private Streams() {}

    private static final SymbolLookup K32 = Native.library("kernel32.dll");

    private static final int GENERIC_READ = 0x80000000;
    private static final int GENERIC_WRITE = 0x40000000;
    private static final int FILE_SHARE_READ = 0x1;
    private static final int FILE_SHARE_WRITE = 0x2;
    private static final int FILE_SHARE_DELETE = 0x4;
    private static final int CREATE_ALWAYS = 2;
    private static final int OPEN_EXISTING = 3;
    private static final int FILE_FLAG_BACKUP_SEMANTICS = 0x02000000;
    private static final int INVALID = -1;

    private static final MethodHandle CREATE_FILE = Native.handle(K32,
        "CreateFileW", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS));

    private static final MethodHandle READ_FILE = Native.handle(K32,
        "ReadFile", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle WRITE_FILE = Native.handle(K32,
        "WriteFile", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle DELETE_FILE = Native.handle(K32,
        "DeleteFileW", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle CLOSE_HANDLE = Native.handle(K32,
        "CloseHandle", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle GET_FILE_INFORMATION = Native.handle(K32,
        "GetFileInformationByHandle", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle FIND_FIRST_STREAM = Native.handle(K32,
        "FindFirstStreamW", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static final MethodHandle FIND_NEXT_STREAM = Native.handle(K32,
        "FindNextStreamW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle FIND_CLOSE = Native.handle(K32,
        "FindClose", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    /* ------------------------------------------------------------- reading */

    /** The bytes of one named stream, or null when there is no such stream. */
    public static byte[] read(File file, String stream) {
        if (file == null) return null;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment path = Native.wide(arena, file.getPath() + ":" + stream);
            MemorySegment handle = (MemorySegment) CREATE_FILE.invokeExact(
                path, GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
                MemorySegment.NULL, OPEN_EXISTING, 0, MemorySegment.NULL);
            if (isInvalid(handle)) return null;
            try {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                MemorySegment buffer = arena.allocate(8192);
                MemorySegment read = arena.allocate(ValueLayout.JAVA_INT);
                while (true) {
                    int ok = (int) READ_FILE.invokeExact(handle, buffer, 8192, read,
                                                         MemorySegment.NULL);
                    int count = read.get(ValueLayout.JAVA_INT, 0);
                    if (ok == 0 || count <= 0) break;
                    byte[] chunk = new byte[count];
                    MemorySegment.copy(buffer, ValueLayout.JAVA_BYTE, 0, chunk, 0, count);
                    out.write(chunk);
                }
                return out.toByteArray();
            } finally {
                closeHandle(handle);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /* ------------------------------------------------------------- writing */

    /**
     * Whether a stream may be written to this file at all.
     *
     * A stream is opened as "path:name", so a malformed path silently lands somewhere else.
     * A path that is not a file, one ending in a separator, a space or a dot, or a stream
     * name containing a separator can each produce a plain file named for the stream
     * sitting beside the real one. That happened once, so it is checked here rather than
     * left to the file system to interpret.
     */
    static boolean canCarryStream(File file, String stream) {
        if (file == null || stream == null || stream.isBlank()) return false;
        for (char c : stream.toCharArray()) {
            // A separator turns the name into a path; a control character makes it
            // invisible. Neither may reach the file system.
            if (c == '\\' || c == '/' || c == ':' || c == '*' || c == '?' || c == '"'
                    || c == '<' || c == '>' || c == '|' || c < ' ') {
                Log.info("a stream name may not contain " + (c < ' '
                    ? "a control character" : "'" + c + "'") + ": " + stream.trim());
                return false;
            }
        }
        String path = file.getPath();
        if (path.isEmpty() || path.endsWith("\\") || path.endsWith("/")
                || path.endsWith(" ") || path.endsWith(".")) {
            Log.info("this path cannot carry a stream: '" + path + "'");
            return false;
        }
        if (!file.isFile()) {
            Log.info("only a file carries a stream, and this is not one: " + path);
            return false;
        }
        return true;
    }

    /** Writes one named stream, replacing whatever was there. */
    public static boolean write(File file, String stream, byte[] bytes) {
        if (file == null || bytes == null) return false;
        if (!canCarryStream(file, stream)) return false;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment path = Native.wide(arena, file.getPath() + ":" + stream);
            MemorySegment handle = (MemorySegment) CREATE_FILE.invokeExact(
                path, GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
                MemorySegment.NULL, CREATE_ALWAYS, 0, MemorySegment.NULL);
            if (isInvalid(handle)) return false;
            try {
                MemorySegment buffer = arena.allocate(bytes.length);
                MemorySegment.copy(bytes, 0, buffer, ValueLayout.JAVA_BYTE, 0, bytes.length);
                MemorySegment written = arena.allocate(ValueLayout.JAVA_INT);
                int ok = (int) WRITE_FILE.invokeExact(handle, buffer, bytes.length, written,
                                                      MemorySegment.NULL);
                return ok != 0 && written.get(ValueLayout.JAVA_INT, 0) == bytes.length;
            } finally {
                closeHandle(handle);
            }
        } catch (Throwable t) {
            return false;
        }
    }

    /** Removes one named stream, leaving the file itself alone. */
    public static boolean remove(File file, String stream) {
        if (file == null) return false;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment path = Native.wide(arena, file.getPath() + ":" + stream);
            return (int) DELETE_FILE.invokeExact(path) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean has(File file, String stream) {
        byte[] bytes = read(file, stream);
        return bytes != null && bytes.length > 0;
    }

    /**
     * Whether this file's volume supports named streams. There is no call that answers
     * directly, so this writes a small stream and removes it again.
     */
    public static boolean supported(File file) {
        if (file == null || !file.exists()) return false;
        String probe = "org.fractalmicro.probe";
        boolean written = write(file, probe, new byte[]{1});
        if (written) remove(file, probe);
        return written;
    }

    /* -------------------------------------------------------------- listing */

    /** The names of the streams on a file, the unnamed one included as "::$DATA". */
    public static List<String> list(File file) {
        List<String> names = new ArrayList<>();
        if (file == null) return names;
        try (Arena arena = Arena.ofConfined()) {
            // WIN32_FIND_STREAM_DATA: a long length then MAX_PATH + 36 wide characters.
            MemorySegment data = arena.allocate(8 + (296 * 2L));
            MemorySegment path = Native.wide(arena, file.getPath());
            MemorySegment find = (MemorySegment) FIND_FIRST_STREAM.invokeExact(
                path, 0, data, 0);
            if (isInvalid(find)) return names;
            try {
                do {
                    names.add(Native.readWide(data.asSlice(8)));
                } while ((int) FIND_NEXT_STREAM.invokeExact(find, data) != 0);
            } finally {
                int ignored = (int) FIND_CLOSE.invokeExact(find);
            }
        } catch (Throwable t) {
            return names;
        }
        return names;
    }

    /* ------------------------------------------------------------- identity */

    /**
     * The volume serial number and file reference number. Together they identify a file on
     * this machine, and survive rename and moves within the volume.
     */
    public record Identity(int volumeSerial, long fileReference) {
        public boolean known() { return fileReference != 0; }
    }

    public static Identity identityOf(File file) {
        if (file == null || !file.exists()) return new Identity(0, 0);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment path = Native.wide(arena, file.getPath());
            MemorySegment handle = (MemorySegment) CREATE_FILE.invokeExact(
                path, GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
                MemorySegment.NULL, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS,
                MemorySegment.NULL);
            if (isInvalid(handle)) return new Identity(0, 0);
            try {
                // BY_HANDLE_FILE_INFORMATION: attributes at 0, three file times to 28,
                // the volume serial at 28, the size at 32 and 36, the link count at 40,
                // and the two halves of the file index at 44 and 48.
                MemorySegment info = arena.allocate(64);
                int ok = (int) GET_FILE_INFORMATION.invokeExact(handle, info);
                if (ok == 0) return new Identity(0, 0);
                int serial = info.get(ValueLayout.JAVA_INT, 28);
                long high = info.get(ValueLayout.JAVA_INT, 44) & 0xFFFFFFFFL;
                long low = info.get(ValueLayout.JAVA_INT, 48) & 0xFFFFFFFFL;
                return new Identity(serial, (high << 32) | low);
            } finally {
                closeHandle(handle);
            }
        } catch (Throwable t) {
            return new Identity(0, 0);
        }
    }

    private static final MethodHandle GET_LAST_ERROR = Native.handle(K32,
        "GetLastError", FunctionDescriptor.of(ValueLayout.JAVA_INT));

    /** The last thing Windows complained about, for the log when a call fails. */
    public static int lastError() {
        try {
            return (int) GET_LAST_ERROR.invokeExact();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static final MethodHandle OPEN_FILE_BY_ID = Native.handle(K32,
        "OpenFileById", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static final MethodHandle GET_FINAL_PATH = Native.handle(K32,
        "GetFinalPathNameByHandleW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT));

    /**
     * Finds a file anywhere on its volume by reference number, which is how an alias
     * follows a renamed or moved target: the path in the record is only a hint.
     *
     * @return the file, or null if the volume cannot look files up this way or it is gone
     */
    public static File byIdentity(File volumeRoot, long fileReference) {
        if (volumeRoot == null || fileReference == 0) return null;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment root = Native.wide(arena, volumeRoot.getPath());
            MemorySegment volume = (MemorySegment) CREATE_FILE.invokeExact(
                root, GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
                MemorySegment.NULL, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS,
                MemorySegment.NULL);
            if (isInvalid(volume)) return null;
            try {
                // FILE_ID_DESCRIPTOR: its own size, the kind of id, then the id itself
                // in a union that starts eight bytes in.
                MemorySegment descriptor = arena.allocate(24);
                descriptor.set(ValueLayout.JAVA_INT, 0, 24);
                descriptor.set(ValueLayout.JAVA_INT, 4, 0);      // FileIdType
                descriptor.set(ValueLayout.JAVA_LONG, 8, fileReference);

                MemorySegment handle = (MemorySegment) OPEN_FILE_BY_ID.invokeExact(
                    volume, descriptor, GENERIC_READ,
                    FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
                    MemorySegment.NULL, FILE_FLAG_BACKUP_SEMANTICS);
                if (isInvalid(handle)) {
                    org.fractalmicro.core.Log.info("could not open file " + fileReference
                                          + " on " + volumeRoot + ": error " + lastError());
                    return null;
                }
                try {
                    MemorySegment buffer = Native.wideBuffer(arena, 32768);
                    int length = (int) GET_FINAL_PATH.invokeExact(handle, buffer, 32768, 0);
                    if (length <= 0) return null;
                    String path = Native.readWide(buffer);
                    // Comes back in the long form; the short one is wanted.
                    if (path.startsWith("\\\\?\\UNC\\")) path = "\\\\" + path.substring(8);
                    else if (path.startsWith("\\\\?\\")) path = path.substring(4);
                    File found = new File(path);
                    return found.exists() ? found : null;
                } finally {
                    closeHandle(handle);
                }
            } finally {
                closeHandle(volume);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isInvalid(MemorySegment handle) {
        return handle.address() == INVALID || handle.address() == 0L;
    }

    private static void closeHandle(MemorySegment handle) {
        try {
            int ignored = (int) CLOSE_HANDLE.invokeExact(handle);
        } catch (Throwable ignored) {
            // Nothing useful to do if a handle will not close.
        }
    }
}
