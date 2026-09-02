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
import java.util.ArrayList;
import java.util.List;

/**
 * The windows belonging to other programs.
 *
 * A desktop that means to replace the Windows shell has to know what is running and be
 * able to bring it forward, because with the taskbar gone there is nothing else to do
 * it. This is the read and command layer over user32: which top level windows exist,
 * what they are called, which process owns them, and how to show, hide or close one.
 */
public final class User32 {
    private User32() {}

    private static final SymbolLookup U32 = Native.library("user32.dll");
    private static final SymbolLookup DWM = Native.library("dwmapi.dll");

    private static final int GWL_STYLE = -16;
    private static final int GWL_EXSTYLE = -20;
    private static final int WS_VISIBLE = 0x10000000;
    private static final int WS_EX_TOOLWINDOW = 0x00000080;
    private static final int WS_EX_NOACTIVATE = 0x08000000;
    private static final int DWMWA_CLOAKED = 14;

    public static final int SW_HIDE = 0;
    public static final int SW_SHOWNORMAL = 1;
    public static final int SW_SHOWMINIMIZED = 2;
    public static final int SW_SHOW = 5;
    public static final int SW_MINIMIZE = 6;
    public static final int SW_RESTORE = 9;

    private static final int WM_CLOSE = 0x0010;

    private static final MethodHandle ENUM_WINDOWS = Native.handle(U32,
        "EnumWindows", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

    private static final MethodHandle GET_WINDOW_TEXT = Native.handle(U32,
        "GetWindowTextW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static final MethodHandle GET_CLASS_NAME = Native.handle(U32,
        "GetClassNameW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static final MethodHandle IS_WINDOW_VISIBLE = Native.handle(U32,
        "IsWindowVisible", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle IS_ICONIC = Native.handle(U32,
        "IsIconic", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle GET_WINDOW_LONG = Native.handle(U32,
        "GetWindowLongPtrW", FunctionDescriptor.of(ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static final MethodHandle GET_WINDOW_THREAD_PROCESS_ID = Native.handle(U32,
        "GetWindowThreadProcessId", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle SHOW_WINDOW = Native.handle(U32,
        "ShowWindow", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static final MethodHandle SET_FOREGROUND_WINDOW = Native.handle(U32,
        "SetForegroundWindow", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle GET_FOREGROUND_WINDOW = Native.handle(U32,
        "GetForegroundWindow", FunctionDescriptor.of(ValueLayout.ADDRESS));

    private static final MethodHandle POST_MESSAGE = Native.handle(U32,
        "PostMessageW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));

    private static final MethodHandle GET_WINDOW = Native.handle(U32,
        "GetWindow", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static final MethodHandle DWM_GET_ATTRIBUTE = Native.handle(DWM,
        "DwmGetWindowAttribute", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    /** One top level window. */
    public static final class Win {
        public final long handle;
        public final String title;
        public final String className;
        public final long pid;
        public final boolean minimized;

        Win(long handle, String title, String className, long pid, boolean minimized) {
            this.handle = handle;
            this.title = title;
            this.className = className;
            this.pid = pid;
            this.minimized = minimized;
        }

        @Override public String toString() { return title + " [" + className + "]"; }
    }

    /**
     * The windows a taskbar would show: visible, titled, not a tool window, not a
     * cloaked leftover, and owned by nobody else.
     */
    public static List<Win> taskWindows() {
        List<Win> found = new ArrayList<>();
        try (Arena arena = Arena.ofConfined()) {
            EnumCallback callback = new EnumCallback(found);
            MethodHandle target = java.lang.invoke.MethodHandles.lookup()
                .findVirtual(EnumCallback.class, "accept",
                    java.lang.invoke.MethodType.methodType(int.class, MemorySegment.class, long.class))
                .bindTo(callback);
            MemorySegment stub = Native.LINKER.upcallStub(target,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG),
                arena);
            int ignored = (int) ENUM_WINDOWS.invokeExact(stub, 0L);
        } catch (Throwable t) {
            org.fractalmicro.core.Log.error("could not list windows", t);
        }
        return found;
    }

    /** Called back once per top level window. */
    private static final class EnumCallback {
        private final List<Win> out;

        EnumCallback(List<Win> out) { this.out = out; }

        @SuppressWarnings("unused")                       // called from native code
        public int accept(MemorySegment hwnd, long ignored) {
            try {
                if (interesting(hwnd)) {
                    out.add(describe(hwnd));
                }
            } catch (Throwable t) {
                // One awkward window is no reason to stop enumerating.
            }
            return 1;
        }
    }

    private static boolean interesting(MemorySegment hwnd) throws Throwable {
        if ((int) IS_WINDOW_VISIBLE.invokeExact(hwnd) == 0) return false;

        long style = (long) GET_WINDOW_LONG.invokeExact(hwnd, GWL_STYLE);
        if ((style & WS_VISIBLE) == 0) return false;

        long exStyle = (long) GET_WINDOW_LONG.invokeExact(hwnd, GWL_EXSTYLE);
        if ((exStyle & WS_EX_TOOLWINDOW) != 0) return false;
        if ((exStyle & WS_EX_NOACTIVATE) != 0) return false;

        // A window owned by another window is a dialog of it, not an entry of its own.
        final int GW_OWNER = 4;
        MemorySegment owner = (MemorySegment) GET_WINDOW.invokeExact(hwnd, GW_OWNER);
        if (owner.address() != 0) return false;

        if (title(hwnd).isBlank()) return false;
        return !cloaked(hwnd);
    }

    /** Modern apps leave invisible shells behind; the compositor calls them cloaked. */
    private static boolean cloaked(MemorySegment hwnd) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(ValueLayout.JAVA_INT);
            int hr = (int) DWM_GET_ATTRIBUTE.invokeExact(hwnd, DWMWA_CLOAKED, value, 4);
            return hr == 0 && value.get(ValueLayout.JAVA_INT, 0) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Win describe(MemorySegment hwnd) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pid = arena.allocate(ValueLayout.JAVA_INT);
            int ignored = (int) GET_WINDOW_THREAD_PROCESS_ID.invokeExact(hwnd, pid);
            boolean minimized = (int) IS_ICONIC.invokeExact(hwnd) != 0;
            return new Win(hwnd.address(), title(hwnd), className(hwnd),
                           Integer.toUnsignedLong(pid.get(ValueLayout.JAVA_INT, 0)), minimized);
        }
    }

    private static String title(MemorySegment hwnd) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = Native.wideBuffer(arena, 512);
            int length = (int) GET_WINDOW_TEXT.invokeExact(hwnd, buffer, 512);
            return length <= 0 ? "" : Native.readWide(buffer);
        }
    }

    private static String className(MemorySegment hwnd) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = Native.wideBuffer(arena, 256);
            int length = (int) GET_CLASS_NAME.invokeExact(hwnd, buffer, 256);
            return length <= 0 ? "" : Native.readWide(buffer);
        }
    }

    /* ------------------------------------------------------------ commands */

    /** Brings a window forward, unminimizing it first if it needs it. */
    public static boolean activate(long handle) {
        try {
            MemorySegment hwnd = MemorySegment.ofAddress(handle);
            if ((int) IS_ICONIC.invokeExact(hwnd) != 0) {
                int ignored = (int) SHOW_WINDOW.invokeExact(hwnd, SW_RESTORE);
            }
            return (int) SET_FOREGROUND_WINDOW.invokeExact(hwnd) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean minimize(long handle) {
        return show(handle, SW_MINIMIZE);
    }

    public static boolean hide(long handle) {
        return show(handle, SW_HIDE);
    }

    public static boolean restore(long handle) {
        return show(handle, SW_RESTORE);
    }

    private static boolean show(long handle, int command) {
        try {
            MemorySegment hwnd = MemorySegment.ofAddress(handle);
            return (int) SHOW_WINDOW.invokeExact(hwnd, command) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Posts any message to a window. The notification area answers clicks this way. */
    public static boolean post(long handle, int message, long wParam, long lParam) {
        try {
            MemorySegment hwnd = MemorySegment.ofAddress(handle);
            return (int) POST_MESSAGE.invokeExact(hwnd, message, wParam, lParam) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Asks a window to close, the way clicking its close box would. */
    public static boolean close(long handle) {
        try {
            MemorySegment hwnd = MemorySegment.ofAddress(handle);
            return (int) POST_MESSAGE.invokeExact(hwnd, WM_CLOSE, 0L, 0L) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** The windows belonging to one process, this program's own included. */
    public static List<Win> windowsOfProcess(long pid) {
        List<Win> out = new ArrayList<>();
        for (Win w : taskWindows()) {
            if (w.pid == pid) out.add(w);
        }
        return out;
    }

    private static final MethodHandle FIND_WINDOW = Native.handle(U32,
        "FindWindowW", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    /**
     * The handle of a window by its exact title. Used to find this program's own strips,
     * which have to be named to the shell before an edge can be reserved for them.
     */
    public static long findWindowByTitle(String title) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment name = Native.wide(arena, title);
            MemorySegment hwnd = (MemorySegment) FIND_WINDOW.invokeExact(
                MemorySegment.NULL, name);
            return hwnd.address();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * The handle of the first window of a class, whoever owns it.
     *
     * The shell's own windows are found this way and not by title: they have none. It is
     * how anything reaches the notification area, and how this system finds out whether
     * something else is already the shell.
     */
    public static long findWindowByClass(String className) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment name = Native.wide(arena, className);
            MemorySegment hwnd = (MemorySegment) FIND_WINDOW.invokeExact(
                name, MemorySegment.NULL);
            return hwnd.address();
        } catch (Throwable t) {
            return 0;
        }
    }

    /** The space bar, which is the one key this system has to read while a drag is going. */
    public static final int VK_SPACE = 0x20;

    private static final MethodHandle GET_ASYNC_KEY_STATE = Native.handle(U32,
        "GetAsyncKeyState", FunctionDescriptor.of(ValueLayout.JAVA_SHORT,
            ValueLayout.JAVA_INT));

    /**
     * Whether a key is down right now.
     *
     * Asked rather than waited for, because there are moments when nothing is delivering key
     * events to this program and it still needs to know. A drag is one: while the mouse is
     * down the pointer and the keyboard belong to the drag, and a program that only knew
     * what its own windows were told could not find out that the space bar was being held.
     *
     * The top bit of the answer is whether it is down. The bottom bit is whether it has been
     * pressed since this was last asked, which is not what anybody wants here and is why the
     * answer has to be masked rather than tested for being non-zero.
     */
    public static boolean isKeyDown(int virtualKey) {
        try {
            short state = (short) GET_ASYNC_KEY_STATE.invokeExact(virtualKey);
            return (state & 0x8000) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** The process a window belongs to. */
    public static long processOf(long handle) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pid = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment hwnd = MemorySegment.ofAddress(handle);
            int ignored = (int) GET_WINDOW_THREAD_PROCESS_ID.invokeExact(hwnd, pid);
            return Integer.toUnsignedLong(pid.get(ValueLayout.JAVA_INT, 0));
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * The handle of one of this program's own windows, found by the title it carries.
     *
     * The title is matched against every window on the desktop, because that is what the
     * shell offers, and the answer is then checked to be ours. Two programs can put the
     * same words in a title bar, and handing back somebody else's window because it was
     * named the same would have this one reserving screen edges against a stranger.
     */
    public static long handleOf(java.awt.Window window) {
        String title = window instanceof java.awt.Dialog dialog ? dialog.getTitle()
                     : window instanceof java.awt.Frame frame ? frame.getTitle()
                     : null;
        if (title == null || title.isEmpty()) return 0;
        long handle = findWindowByTitle(title);
        if (handle == 0) return 0;
        return processOf(handle) == ProcessHandle.current().pid() ? handle : 0;
    }

    public static long foregroundWindow() {
        try {
            MemorySegment hwnd = (MemorySegment) GET_FOREGROUND_WINDOW.invokeExact();
            return hwnd.address();
        } catch (Throwable t) {
            return 0;
        }
    }
}
