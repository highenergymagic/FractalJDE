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

import java.awt.Rectangle;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * Reserving an edge of the screen.
 *
 * Windows has one documented way for something other than the taskbar to own a strip of
 * the screen: register as an application desktop bar and tell the shell which edge and
 * how much. Everything else then works around it: a maximised window stops at the
 * reserved edge instead of covering it.
 *
 * This is how the menu bar gets to be a menu bar rather than a window that happens to sit
 * at the top and gets buried. The Dock uses the same thing at the bottom.
 *
 * All of it is one call, SHAppBarMessage, with an APPBARDATA:
 *
 *   0   cbSize            4 (and four bytes of padding, because the handle is aligned)
 *   8   hWnd              8
 *   16  uCallbackMessage  4
 *   20  uEdge             4
 *   24  rc                16   left, top, right, bottom
 *   40  lParam            8
 */
public final class AppBar {

    private static final SymbolLookup SHELL = Native.library("shell32.dll");

    public static final int ABM_NEW = 0x00000000;
    public static final int ABM_REMOVE = 0x00000001;
    public static final int ABM_QUERYPOS = 0x00000002;
    public static final int ABM_SETPOS = 0x00000003;

    public static final int ABE_LEFT = 0;
    public static final int ABE_TOP = 1;
    public static final int ABE_RIGHT = 2;
    public static final int ABE_BOTTOM = 3;

    private static final int SIZE = 48;
    private static final int CALLBACK_MESSAGE = 0x0400 + 71;   // WM_USER + 71

    private static final MethodHandle APP_BAR_MESSAGE = Native.handle(SHELL,
        "SHAppBarMessage", FunctionDescriptor.of(ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private final long window;
    private final int edge;
    private boolean registered;

    private AppBar(long window, int edge) {
        this.window = window;
        this.edge = edge;
    }

    /**
     * Claims an edge for a window. Answers the bar, or null if the shell would not have
     * it, which happens when Explorer is not running the desktop and is not fatal:
     * the window simply sits on top of whatever is there.
     */
    public static AppBar claim(long window, int edge, Rectangle wanted) {
        if (window == 0) return null;
        AppBar bar = new AppBar(window, edge);
        if (!bar.send(ABM_NEW, null)) {
            Log.info("the shell would not register an edge for this window");
            return null;
        }
        bar.registered = true;
        bar.setPosition(wanted);
        return bar;
    }

    /**
     * Asks the shell where a bar of this size may sit, then takes that place. The shell
     * moves the rectangle to avoid anything already there, so this is asked every time
     * the screen changes.
     */
    public Rectangle setPosition(Rectangle wanted) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = data(arena);
            putRect(data, wanted);
            long queried = call(ABM_QUERYPOS, data);
            Rectangle allowed = getRect(data);
            // The shell adjusts the edge it owns; the bar keeps the thickness it asked for.
            if (edge == ABE_TOP) allowed.height = wanted.height;
            if (edge == ABE_BOTTOM) {
                allowed.y = allowed.y + allowed.height - wanted.height;
                allowed.height = wanted.height;
            }
            putRect(data, allowed);
            call(ABM_SETPOS, data);
            return getRect(data);
        } catch (Throwable t) {
            Log.error("could not place the reserved edge", t);
            return wanted;
        }
    }

    /** Gives the edge back. Leaving one registered would shrink the desktop for good. */
    public void release() {
        if (!registered) return;
        registered = false;
        send(ABM_REMOVE, null);
    }

    public boolean isRegistered() { return registered; }

    /* ------------------------------------------------------------- plumbing */

    private boolean send(int message, Rectangle rectangle) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = data(arena);
            if (rectangle != null) putRect(data, rectangle);
            return call(message, data) != 0;
        } catch (Throwable t) {
            Log.error("the shell refused an application bar message", t);
            return false;
        }
    }

    private long call(int message, MemorySegment data) throws Throwable {
        return (long) APP_BAR_MESSAGE.invokeExact(message, data);
    }

    private MemorySegment data(Arena arena) {
        MemorySegment data = arena.allocate(SIZE);
        data.fill((byte) 0);
        data.set(ValueLayout.JAVA_INT, 0, SIZE);
        data.set(ValueLayout.JAVA_LONG, 8, window);
        data.set(ValueLayout.JAVA_INT, 16, CALLBACK_MESSAGE);
        data.set(ValueLayout.JAVA_INT, 20, edge);
        return data;
    }

    private static void putRect(MemorySegment data, Rectangle r) {
        data.set(ValueLayout.JAVA_INT, 24, r.x);
        data.set(ValueLayout.JAVA_INT, 28, r.y);
        data.set(ValueLayout.JAVA_INT, 32, r.x + r.width);
        data.set(ValueLayout.JAVA_INT, 36, r.y + r.height);
    }

    private static Rectangle getRect(MemorySegment data) {
        int left = data.get(ValueLayout.JAVA_INT, 24);
        int top = data.get(ValueLayout.JAVA_INT, 28);
        int right = data.get(ValueLayout.JAVA_INT, 32);
        int bottom = data.get(ValueLayout.JAVA_INT, 36);
        return new Rectangle(left, top, right - left, bottom - top);
    }
}
