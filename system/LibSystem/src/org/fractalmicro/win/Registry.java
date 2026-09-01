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

/** Registry reads through RegGetValueW. Read only; nothing here writes. */
public final class Registry {
    private Registry() {}

    public static final long HKEY_CLASSES_ROOT  = 0x80000000L;
    public static final long HKEY_CURRENT_USER  = 0x80000001L;
    public static final long HKEY_LOCAL_MACHINE = 0x80000002L;

    private static final int RRF_RT_REG_SZ = 0x00000002;
    private static final int RRF_RT_REG_EXPAND_SZ = 0x00000004;
    private static final int RRF_RT_REG_DWORD = 0x00000018;
    private static final int RRF_NOEXPAND = 0x10000000;

    private static final SymbolLookup ADVAPI = Native.library("advapi32.dll");

    private static final int KEY_READ = 0x20019;
    private static final int ERROR_SUCCESS = 0;

    private static final MethodHandle REG_OPEN_KEY = Native.handle(ADVAPI,
        "RegOpenKeyExW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle REG_ENUM_VALUE = Native.handle(ADVAPI,
        "RegEnumValueW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle REG_CLOSE_KEY = Native.handle(ADVAPI,
        "RegCloseKey", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle REG_GET_VALUE = Native.handle(ADVAPI,
        "RegGetValueW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    /** A string value, or null when the key or value is missing. */
    public static String string(long hive, String subKey, String value) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment key = Native.wide(arena, subKey);
            MemorySegment name = value == null || value.isEmpty()
                ? MemorySegment.NULL : Native.wide(arena, value);
            MemorySegment data = arena.allocate(4096);
            MemorySegment size = arena.allocate(ValueLayout.JAVA_INT);
            size.set(ValueLayout.JAVA_INT, 0, 4096);
            int status = (int) REG_GET_VALUE.invokeExact(
                MemorySegment.ofAddress(hive), key, name,
                RRF_RT_REG_SZ | RRF_RT_REG_EXPAND_SZ | RRF_NOEXPAND,
                MemorySegment.NULL, data, size);
            if (status != 0) return null;
            String result = Native.readWide(data);
            return result.isEmpty() ? null : result;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Every string value under a key, in order. Used for the Run keys, which are a list
     * of programs to start rather than one setting.
     */
    public static java.util.Map<String, String> values(long hive, String subKey) {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment key = Native.wide(arena, subKey);
            MemorySegment handle = arena.allocate(ValueLayout.ADDRESS);
            int status = (int) REG_OPEN_KEY.invokeExact(
                MemorySegment.ofAddress(hive), key, 0, KEY_READ, handle);
            if (status != ERROR_SUCCESS) return out;

            MemorySegment opened = handle.get(ValueLayout.ADDRESS, 0);
            try {
                for (int index = 0; index < 512; index++) {
                    MemorySegment name = Native.wideBuffer(arena, 16384);
                    MemorySegment nameLength = arena.allocate(ValueLayout.JAVA_INT);
                    nameLength.set(ValueLayout.JAVA_INT, 0, 16383);
                    MemorySegment type = arena.allocate(ValueLayout.JAVA_INT);
                    MemorySegment data = arena.allocate(8192);
                    MemorySegment dataLength = arena.allocate(ValueLayout.JAVA_INT);
                    dataLength.set(ValueLayout.JAVA_INT, 0, 8192);

                    int result = (int) REG_ENUM_VALUE.invokeExact(
                        opened, index, name, nameLength, MemorySegment.NULL,
                        type, data, dataLength);
                    if (result != ERROR_SUCCESS) break;

                    int kind = type.get(ValueLayout.JAVA_INT, 0);
                    if (kind != 1 && kind != 2) continue;      // strings only
                    out.put(Native.readWide(name), Native.readWide(data));
                }
            } finally {
                int ignored = (int) REG_CLOSE_KEY.invokeExact(opened);
            }
        } catch (Throwable t) {
            org.fractalmicro.core.Log.error("could not read the values under " + subKey, t);
        }
        return out;
    }

    /** A DWORD value, or the fallback when it is missing. */
    public static int dword(long hive, String subKey, String value, int fallback) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment key = Native.wide(arena, subKey);
            MemorySegment name = Native.wide(arena, value);
            MemorySegment data = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment size = arena.allocate(ValueLayout.JAVA_INT);
            size.set(ValueLayout.JAVA_INT, 0, 4);
            int status = (int) REG_GET_VALUE.invokeExact(
                MemorySegment.ofAddress(hive), key, name,
                RRF_RT_REG_DWORD, MemorySegment.NULL, data, size);
            return status != 0 ? fallback : data.get(ValueLayout.JAVA_INT, 0);
        } catch (Throwable t) {
            return fallback;
        }
    }
}
