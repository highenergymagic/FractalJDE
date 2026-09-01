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

/** Drives, disk space, memory and processors, straight from kernel32. */
public final class Kernel32 {
    private Kernel32() {}

    public static final int DRIVE_UNKNOWN     = 0;
    public static final int DRIVE_NO_ROOT_DIR = 1;
    public static final int DRIVE_REMOVABLE   = 2;
    public static final int DRIVE_FIXED       = 3;
    public static final int DRIVE_REMOTE      = 4;
    public static final int DRIVE_CDROM       = 5;
    public static final int DRIVE_RAMDISK     = 6;

    private static final SymbolLookup K32 = Native.library("kernel32.dll");

    private static final MethodHandle GET_LOGICAL_DRIVES = Native.handle(K32,
        "GetLogicalDrives", FunctionDescriptor.of(ValueLayout.JAVA_INT));

    private static final MethodHandle GET_DRIVE_TYPE = Native.handle(K32,
        "GetDriveTypeW", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle GET_VOLUME_INFORMATION = Native.handle(K32,
        "GetVolumeInformationW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static final MethodHandle GET_DISK_FREE_SPACE = Native.handle(K32,
        "GetDiskFreeSpaceExW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle GLOBAL_MEMORY_STATUS = Native.handle(K32,
        "GlobalMemoryStatusEx", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle GET_PROCESSOR_INFORMATION = Native.handle(K32,
        "GetLogicalProcessorInformationEx", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle CREATE_FILE = Native.handle(K32,
        "CreateFileW", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle DEVICE_IO_CONTROL = Native.handle(K32,
        "DeviceIoControl", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle CLOSE_HANDLE = Native.handle(K32,
        "CloseHandle", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    /** Drive letters that exist, as "C:\\" style roots. */
    public static java.util.List<String> logicalDrives() {
        java.util.List<String> roots = new java.util.ArrayList<>();
        try {
            int mask = (int) GET_LOGICAL_DRIVES.invokeExact();
            for (int i = 0; i < 26; i++) {
                if ((mask & (1 << i)) != 0) roots.add((char) ('A' + i) + ":\\");
            }
        } catch (Throwable t) {
            for (java.io.File f : java.io.File.listRoots()) roots.add(f.getAbsolutePath());
        }
        return roots;
    }

    public static int driveType(String root) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment path = Native.wide(arena, root);
            return (int) GET_DRIVE_TYPE.invokeExact(path);
        } catch (Throwable t) {
            return DRIVE_UNKNOWN;
        }
    }

    /** Volume label, or an empty string when the drive has none or is not ready. */
    public static String volumeLabel(String root) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment path = Native.wide(arena, root);
            MemorySegment label = Native.wideBuffer(arena, 261);
            MemorySegment fsName = Native.wideBuffer(arena, 261);
            MemorySegment serial = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment maxComponent = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment flags = arena.allocate(ValueLayout.JAVA_INT);
            int ok = (int) GET_VOLUME_INFORMATION.invokeExact(
                path, label, 261, serial, maxComponent, flags, fsName, 261);
            return ok == 0 ? "" : Native.readWide(label);
        } catch (Throwable t) {
            return "";
        }
    }

    public static String fileSystem(String root) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment path = Native.wide(arena, root);
            MemorySegment label = Native.wideBuffer(arena, 261);
            MemorySegment fsName = Native.wideBuffer(arena, 261);
            MemorySegment serial = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment maxComponent = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment flags = arena.allocate(ValueLayout.JAVA_INT);
            int ok = (int) GET_VOLUME_INFORMATION.invokeExact(
                path, label, 261, serial, maxComponent, flags, fsName, 261);
            return ok == 0 ? "" : Native.readWide(fsName);
        } catch (Throwable t) {
            return "";
        }
    }

    /** Total and free bytes, or {0, 0} when the drive is not ready. */
    public static long[] diskSpace(String root) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment path = Native.wide(arena, root);
            MemorySegment freeToCaller = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment total = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment totalFree = arena.allocate(ValueLayout.JAVA_LONG);
            int ok = (int) GET_DISK_FREE_SPACE.invokeExact(path, freeToCaller, total, totalFree);
            if (ok == 0) return new long[]{0, 0};
            return new long[]{total.get(ValueLayout.JAVA_LONG, 0),
                              freeToCaller.get(ValueLayout.JAVA_LONG, 0)};
        } catch (Throwable t) {
            return new long[]{0, 0};
        }
    }

    /** Physical memory in bytes. */
    public static long totalMemory() {
        try (Arena arena = Arena.ofConfined()) {
            // MEMORYSTATUSEX: DWORD dwLength, DWORD dwMemoryLoad, then seven 64-bit fields.
            MemorySegment status = arena.allocate(64);
            status.set(ValueLayout.JAVA_INT, 0, 64);
            int ok = (int) GLOBAL_MEMORY_STATUS.invokeExact(status);
            return ok == 0 ? 0 : status.get(ValueLayout.JAVA_LONG, 8);
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Physical processor cores, counted from GetLogicalProcessorInformationEx with
     * RelationProcessorCore. Falls back to the logical count.
     */
    public static int physicalCores() {
        final int RELATION_PROCESSOR_CORE = 0;
        final int ERROR_INSUFFICIENT_BUFFER = 122;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment length = arena.allocate(ValueLayout.JAVA_INT);
            length.set(ValueLayout.JAVA_INT, 0, 0);
            int ok = (int) GET_PROCESSOR_INFORMATION.invokeExact(
                RELATION_PROCESSOR_CORE, MemorySegment.NULL, length);
            int needed = length.get(ValueLayout.JAVA_INT, 0);
            if (ok != 0 || needed <= 0) return Runtime.getRuntime().availableProcessors();

            MemorySegment buffer = arena.allocate(needed);
            ok = (int) GET_PROCESSOR_INFORMATION.invokeExact(
                RELATION_PROCESSOR_CORE, buffer, length);
            if (ok == 0) return Runtime.getRuntime().availableProcessors();

            int cores = 0;
            long offset = 0;
            while (offset + 8 <= needed) {
                int size = buffer.get(ValueLayout.JAVA_INT, offset + 4);
                if (size <= 0) break;
                if (buffer.get(ValueLayout.JAVA_INT, offset) == RELATION_PROCESSOR_CORE) cores++;
                offset += size;
            }
            return cores > 0 ? cores : Runtime.getRuntime().availableProcessors();
        } catch (Throwable t) {
            return Runtime.getRuntime().availableProcessors();
        }
    }

    /** Asks a drive to eject its media. */
    public static boolean ejectMedia(String driveLetter) {
        final int GENERIC_READ = 0x80000000;
        final int FILE_SHARE_READ = 1, FILE_SHARE_WRITE = 2;
        final int OPEN_EXISTING = 3;
        final int IOCTL_STORAGE_EJECT_MEDIA = 0x2D4808;
        String letter = driveLetter.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment path = Native.wide(arena, "\\\\.\\" + letter + ":");
            MemorySegment handle = (MemorySegment) CREATE_FILE.invokeExact(
                path, GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE,
                MemorySegment.NULL, OPEN_EXISTING, 0, MemorySegment.NULL);
            if (handle.address() == -1L || handle.address() == 0L) return false;
            MemorySegment returned = arena.allocate(ValueLayout.JAVA_INT);
            int ok = (int) DEVICE_IO_CONTROL.invokeExact(handle, IOCTL_STORAGE_EJECT_MEDIA,
                MemorySegment.NULL, 0, MemorySegment.NULL, 0, returned, MemorySegment.NULL);
            int ignored = (int) CLOSE_HANDLE.invokeExact(handle);
            return ok != 0;
        } catch (Throwable t) {
            return false;
        }
    }
}
