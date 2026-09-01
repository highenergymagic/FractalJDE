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

import java.awt.image.BufferedImage;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * Turns a Windows icon handle into something Java can draw.
 *
 * Icon handles are shared across processes, so a notification icon belonging to another
 * program can be drawn here: make a 32 bit device independent bitmap, ask Windows to
 * draw the icon into it, and read the pixels back out.
 */
public final class IconImages {
    private IconImages() {}

    private static final SymbolLookup GDI = Native.library("gdi32.dll");
    private static final SymbolLookup U32 = Native.library("user32.dll");

    private static final int DIB_RGB_COLORS = 0;
    private static final int DI_NORMAL = 3;

    private static final MethodHandle CREATE_COMPATIBLE_DC = Native.handle(GDI,
        "CreateCompatibleDC", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle CREATE_DIB_SECTION = Native.handle(GDI,
        "CreateDIBSection", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static final MethodHandle SELECT_OBJECT = Native.handle(GDI,
        "SelectObject", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle DELETE_OBJECT = Native.handle(GDI,
        "DeleteObject", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle DELETE_DC = Native.handle(GDI,
        "DeleteDC", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle DRAW_ICON_EX = Native.handle(U32,
        "DrawIconEx", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    /** The icon at the size asked for, or null when it cannot be drawn. */
    public static BufferedImage of(long iconHandle, int size) {
        if (iconHandle == 0 || size <= 0) return null;
        MemorySegment dc = MemorySegment.NULL;
        MemorySegment bitmap = MemorySegment.NULL;
        try (Arena arena = Arena.ofConfined()) {
            dc = (MemorySegment) CREATE_COMPATIBLE_DC.invokeExact(MemorySegment.NULL);
            if (dc.address() == 0) return null;

            // BITMAPINFOHEADER, with a negative height so the rows come back top down.
            MemorySegment info = arena.allocate(40);
            info.fill((byte) 0);
            info.set(ValueLayout.JAVA_INT, 0, 40);
            info.set(ValueLayout.JAVA_INT, 4, size);
            info.set(ValueLayout.JAVA_INT, 8, -size);
            info.set(ValueLayout.JAVA_SHORT, 12, (short) 1);
            info.set(ValueLayout.JAVA_SHORT, 14, (short) 32);

            MemorySegment bitsPointer = arena.allocate(ValueLayout.ADDRESS);
            bitmap = (MemorySegment) CREATE_DIB_SECTION.invokeExact(
                dc, info, DIB_RGB_COLORS, bitsPointer, MemorySegment.NULL, 0);
            if (bitmap.address() == 0) return null;

            MemorySegment previous = (MemorySegment) SELECT_OBJECT.invokeExact(dc, bitmap);
            MemorySegment iconSegment = MemorySegment.ofAddress(iconHandle);
            int drawn = (int) DRAW_ICON_EX.invokeExact(
                dc, 0, 0, iconSegment, size, size, 0, MemorySegment.NULL, DI_NORMAL);
            MemorySegment ignored = (MemorySegment) SELECT_OBJECT.invokeExact(dc, previous);
            if (drawn == 0) return null;

            long bitsAddress = bitsPointer.get(ValueLayout.ADDRESS, 0).address();
            if (bitsAddress == 0) return null;
            MemorySegment pixels = MemorySegment.ofAddress(bitsAddress)
                                                .reinterpret((long) size * size * 4);

            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            boolean anyAlpha = false;
            int[] row = new int[size * size];
            for (int i = 0; i < row.length; i++) {
                int b = pixels.get(ValueLayout.JAVA_BYTE, i * 4L) & 0xFF;
                int g = pixels.get(ValueLayout.JAVA_BYTE, i * 4L + 1) & 0xFF;
                int r = pixels.get(ValueLayout.JAVA_BYTE, i * 4L + 2) & 0xFF;
                int a = pixels.get(ValueLayout.JAVA_BYTE, i * 4L + 3) & 0xFF;
                if (a != 0) anyAlpha = true;
                row[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            // Older icons carry no alpha at all; treat them as fully opaque.
            if (!anyAlpha) {
                for (int i = 0; i < row.length; i++) row[i] |= 0xFF000000;
            }
            image.setRGB(0, 0, size, size, row, 0, size);
            return image;
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                if (bitmap.address() != 0) {
                    int ignored = (int) DELETE_OBJECT.invokeExact(bitmap);
                }
                if (dc.address() != 0) {
                    int ignored = (int) DELETE_DC.invokeExact(dc);
                }
            } catch (Throwable ignored) {
                // Nothing useful to do about a leaked device context at this point.
            }
        }
    }
}
