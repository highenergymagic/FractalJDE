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

/** The Recycle Bin calls: how much is in it, and emptying it. */
public final class Shell32 {
    private Shell32() {}

    public static final int SHERB_NOCONFIRMATION = 0x00000001;
    public static final int SHERB_NOPROGRESSUI   = 0x00000002;
    public static final int SHERB_NOSOUND        = 0x00000004;

    private static final SymbolLookup SHELL = Native.library("shell32.dll");

    private static final MethodHandle SH_QUERY_RECYCLE_BIN = Native.handle(SHELL,
        "SHQueryRecycleBinW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle SH_GET_FILE_INFO = Native.handle(SHELL,
        "SHGetFileInfoW", FunctionDescriptor.of(ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

    private static final MethodHandle SH_EMPTY_RECYCLE_BIN = Native.handle(SHELL,
        "SHEmptyRecycleBinW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static final int SHGFI_DISPLAYNAME = 0x000000200;
    private static final int SHGFI_TYPENAME = 0x000000400;
    private static final int SHGFI_USEFILEATTRIBUTES = 0x000000010;
    private static final int FILE_ATTRIBUTE_NORMAL = 0x00000080;

    // SHFILEINFOW on 64 bit: HICON, int, DWORD, WCHAR[260], WCHAR[80].
    private static final int SHFILEINFO_SIZE = 696;
    private static final int DISPLAY_NAME_OFFSET = 16;
    private static final int TYPE_NAME_OFFSET = 536;

    /**
     * The type description Windows itself shows, such as "Microsoft Word Document" or
     * "PNG File". Asked for by attributes rather than by touching the file, so it works
     * for names that do not exist.
     */
    public static String typeName(String path, boolean directory) {
        return fileInfo(path, TYPE_NAME_OFFSET, SHGFI_TYPENAME | SHGFI_USEFILEATTRIBUTES,
                        directory ? 0x10 : FILE_ATTRIBUTE_NORMAL);
    }

    /** The name Windows shows for a path, such as "Local Disk (C:)". */
    public static String displayName(String path) {
        return fileInfo(path, DISPLAY_NAME_OFFSET, SHGFI_DISPLAYNAME, 0);
    }

    private static String fileInfo(String path, int offset, int flags, int attributes) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment wide = Native.wide(arena, path);
            MemorySegment info = arena.allocate(SHFILEINFO_SIZE);
            long result = (long) SH_GET_FILE_INFO.invokeExact(
                wide, attributes, info, SHFILEINFO_SIZE, flags);
            if (result == 0) return "";
            return Native.readWide(info.asSlice(offset, SHFILEINFO_SIZE - offset));
        } catch (Throwable t) {
            return "";
        }
    }

    /** {item count, total bytes} across every drive, or {0, 0} if the call fails. */
    public static long[] recycleBinInfo() {
        try (Arena arena = Arena.ofConfined()) {
            // SHQUERYRBINFO: DWORD cbSize, padding, __int64 i64Size, __int64 i64NumItems.
            MemorySegment info = arena.allocate(24);
            info.set(ValueLayout.JAVA_INT, 0, 24);
            int hr = (int) SH_QUERY_RECYCLE_BIN.invokeExact(MemorySegment.NULL, info);
            if (hr != 0) return new long[]{0, 0};
            long size = info.get(ValueLayout.JAVA_LONG, 8);
            long items = info.get(ValueLayout.JAVA_LONG, 16);
            return new long[]{items, size};
        } catch (Throwable t) {
            return new long[]{0, 0};
        }
    }

    /** Empties the Recycle Bin. The confirmation has already been asked for by then. */
    public static boolean emptyRecycleBin(boolean silent) {
        int flags = SHERB_NOCONFIRMATION | (silent ? SHERB_NOPROGRESSUI | SHERB_NOSOUND : 0);
        try {
            int hr = (int) SH_EMPTY_RECYCLE_BIN.invokeExact(
                MemorySegment.NULL, MemorySegment.NULL, flags);
            return hr == 0;
        } catch (Throwable t) {
            return false;
        }
    }
}
