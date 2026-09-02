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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A window with no pixels.
 *
 * Windows delivers a great deal through the message queue rather than through a return
 * value: hot keys pressed anywhere, the shell hook telling you a window appeared,
 * eventually the notification area. All of it needs a window with a procedure of its
 * own and a thread pumping messages at it. This is that window: registered as a class,
 * created as a message-only window, and pumped on one daemon thread for the life of
 * the program.
 *
 * The window procedure is a Java method reached through an upcall stub, so messages
 * arrive as ordinary calls. Handlers registered here run on the pump thread and should
 * hand anything substantial to the event thread.
 */
public final class MessageWindow {

    /**
     * Something that wants to see messages. The window is zero for thread messages,
     * hot keys among them. Return true when the message is handled.
     */
    public interface Handler {
        boolean handle(long window, int message, long wParam, long lParam);
    }

    private static final SymbolLookup U32 = Native.library("user32.dll");
    private static final SymbolLookup K32 = Native.library("kernel32.dll");

    public static final int WM_DESTROY = 0x0002;
    public static final int WM_CLOSE = 0x0010;
    public static final int WM_APP = 0x8000;
    /** Posted to ask the pump thread to run whatever is waiting in its queue. */
    public static final int WM_RUN_TASK = WM_APP + 1;

    private static final int HWND_MESSAGE = -3;
    private static final int WNDCLASSEX_SIZE = 80;

    private static final MethodHandle REGISTER_CLASS = Native.handle(U32,
        "RegisterClassExW", FunctionDescriptor.of(ValueLayout.JAVA_SHORT, ValueLayout.ADDRESS));

    private static final MethodHandle CREATE_WINDOW = Native.handle(U32,
        "CreateWindowExW", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle DEF_WINDOW_PROC = Native.handle(U32,
        "DefWindowProcW", FunctionDescriptor.of(ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));

    private static final MethodHandle GET_MESSAGE = Native.handle(U32,
        "GetMessageW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

    private static final MethodHandle TRANSLATE_MESSAGE = Native.handle(U32,
        "TranslateMessage", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle DISPATCH_MESSAGE = Native.handle(U32,
        "DispatchMessageW", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

    private static final MethodHandle POST_MESSAGE = Native.handle(U32,
        "PostMessageW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));

    private static final MethodHandle DESTROY_WINDOW = Native.handle(U32,
        "DestroyWindow", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle POST_QUIT = Native.handle(U32,
        "PostQuitMessage", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));

    private static final MethodHandle GET_MODULE_HANDLE = Native.handle(K32,
        "GetModuleHandleW", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static MessageWindow instance;

    private final List<Handler> handlers = new ArrayList<>();
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final CountDownLatch ready = new CountDownLatch(1);
    private volatile long hwnd;
    private volatile MemorySegment procedureStub;
    private volatile boolean running;
    private Thread pump;

    private MessageWindow() {}

    /** The one message window, started on first use. */
    /** The one this process listens on, made the first time anything asks for it. */
    public static synchronized MessageWindow sharedWindow() {
        if (instance == null) {
            instance = new MessageWindow();
            instance.start();
        }
        return instance;
    }

    public long handle() { return hwnd; }

    public boolean isRunning() { return running && hwnd != 0; }

    public void addHandler(Handler handler) { handlers.add(handler); }

    /**
     * Runs something on the pump thread. Calls that must happen there, such as
     * registering a hot key, go through this.
     */
    public void invoke(Runnable task) {
        tasks.add(task);
        if (hwnd != 0) {
            try {
                int ignored = (int) POST_MESSAGE.invokeExact(
                    MemorySegment.ofAddress(hwnd), WM_RUN_TASK, 0L, 0L);
            } catch (Throwable t) {
                Log.error("could not wake the message window", t);
            }
        }
    }

    private void start() {
        pump = new Thread(this::run, "fractal-messages");
        pump.setDaemon(true);
        pump.start();
        try {
            if (!ready.await(5, TimeUnit.SECONDS)) {
                Log.info("the message window did not start in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void run() {
        Arena arena = Arena.ofShared();
        try {
            MemorySegment className = Native.wide(arena, "FractalJDEMessageWindow");
            MemorySegment moduleHandle = (MemorySegment) GET_MODULE_HANDLE.invokeExact(MemorySegment.NULL);

            MethodHandle target = MethodHandles.lookup()
                .findVirtual(MessageWindow.class, "windowProc",
                    MethodType.methodType(long.class, MemorySegment.class,
                                          int.class, long.class, long.class))
                .bindTo(this);
            MemorySegment procedure = Native.LINKER.upcallStub(target,
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG),
                arena);

            procedureStub = procedure;

            MemorySegment windowClass = arena.allocate(WNDCLASSEX_SIZE);
            windowClass.fill((byte) 0);
            windowClass.set(ValueLayout.JAVA_INT, 0, WNDCLASSEX_SIZE);       // cbSize
            windowClass.set(ValueLayout.ADDRESS, 8, procedure);              // lpfnWndProc
            windowClass.set(ValueLayout.ADDRESS, 24, moduleHandle);          // hInstance
            windowClass.set(ValueLayout.ADDRESS, 64, className);             // lpszClassName

            short atom = (short) REGISTER_CLASS.invokeExact(windowClass);
            if (atom == 0) {
                Log.info("the message window class could not be registered");
                ready.countDown();
                return;
            }

            MemorySegment window = (MemorySegment) CREATE_WINDOW.invokeExact(
                0, className, className, 0, 0, 0, 0, 0,
                MemorySegment.ofAddress(HWND_MESSAGE), MemorySegment.NULL,
                moduleHandle, MemorySegment.NULL);
            hwnd = window.address();
            if (hwnd == 0) {
                Log.info("the message window could not be created");
                ready.countDown();
                return;
            }

            running = true;
            Log.info("message window ready");
            ready.countDown();
            pumpMessages(arena);
        } catch (Throwable t) {
            Log.error("the message window failed", t);
            ready.countDown();
        } finally {
            running = false;
        }
    }

    /** MSG is a handle, a message, two parameters, a time and a point: 48 bytes. */
    private void pumpMessages(Arena arena) throws Throwable {
        MemorySegment message = arena.allocate(48);
        while (running) {
            int result = (int) GET_MESSAGE.invokeExact(message, MemorySegment.NULL, 0, 0);
            if (result <= 0) break;                       // 0 is WM_QUIT, -1 is an error
            int ignoredTranslate = (int) TRANSLATE_MESSAGE.invokeExact(message);
            long ignoredDispatch = (long) DISPATCH_MESSAGE.invokeExact(message);

            // Thread messages, hot keys among them, arrive with no window attached and
            // never reach the window procedure, so they are handled here.
            long owner = message.get(ValueLayout.JAVA_LONG, 0);
            if (owner == 0) {
                int what = message.get(ValueLayout.JAVA_INT, 8);
                long wParam = message.get(ValueLayout.JAVA_LONG, 16);
                long lParam = message.get(ValueLayout.JAVA_LONG, 24);
                deliver(0, what, wParam, lParam);
            }
        }
    }

    /** The window procedure. Runs on the pump thread. */
    @SuppressWarnings("unused")                            // called from native code
    private long windowProc(MemorySegment window, int message, long wParam, long lParam) {
        try {
            if (message == WM_RUN_TASK) {
                Runnable task;
                while ((task = tasks.poll()) != null) task.run();
                return 0;
            }
            if (deliver(window.address(), message, wParam, lParam)) return 0;
            if (message == WM_DESTROY) {
                POST_QUIT.invokeExact(0);
                return 0;
            }
            return (long) DEF_WINDOW_PROC.invokeExact(window, message, wParam, lParam);
        } catch (Throwable t) {
            Log.error("the window procedure threw on message " + message, t);
            return 0;
        }
    }

    private boolean deliver(long window, int message, long wParam, long lParam) {
        for (Handler handler : new ArrayList<>(handlers)) {
            try {
                if (handler.handle(window, message, wParam, lParam)) return true;
            } catch (Throwable t) {
                Log.error("a message handler threw", t);
            }
        }
        return false;
    }

    /**
     * Creates another window on the pump thread, sharing this window procedure. The
     * notification area needs windows of particular classes.
     * Returns the handle, or zero.
     */
    public long createWindow(String className, String title, int style, long parent) {
        java.util.concurrent.CompletableFuture<Long> result = new java.util.concurrent.CompletableFuture<>();
        invoke(() -> {
            try {
                MemorySegment name = Native.wide(Arena.global(), className);
                MemorySegment moduleHandle =
                    (MemorySegment) GET_MODULE_HANDLE.invokeExact(MemorySegment.NULL);

                MemorySegment windowClass = Arena.global().allocate(WNDCLASSEX_SIZE);
                windowClass.fill((byte) 0);
                windowClass.set(ValueLayout.JAVA_INT, 0, WNDCLASSEX_SIZE);
                windowClass.set(ValueLayout.ADDRESS, 8, procedureStub);
                windowClass.set(ValueLayout.ADDRESS, 24, moduleHandle);
                windowClass.set(ValueLayout.ADDRESS, 64, name);
                short atom = (short) REGISTER_CLASS.invokeExact(windowClass);
                if (atom == 0) {
                    Log.info("the class " + className + " is already taken; "
                           + "Explorer is probably still running");
                }

                MemorySegment titleText = title == null
                    ? MemorySegment.NULL : Native.wide(Arena.global(), title);
                // invokeExact matches on the static type of every argument, so the
                // parent handle is named here rather than chosen inside the call.
                MemorySegment parentWindow = parent == 0
                    ? MemorySegment.NULL : MemorySegment.ofAddress(parent);
                MemorySegment window = (MemorySegment) CREATE_WINDOW.invokeExact(
                    0, name, titleText, style, 0, 0, 0, 0,
                    parentWindow, MemorySegment.NULL, moduleHandle, MemorySegment.NULL);
                result.complete(window.address());
            } catch (Throwable t) {
                Log.error("could not create the window " + className, t);
                result.complete(0L);
            }
        });
        try {
            return result.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Closes the window and stops the pump. */
    public synchronized void stop() {
        if (hwnd == 0) return;
        try {
            running = false;
            int ignored = (int) DESTROY_WINDOW.invokeExact(MemorySegment.ofAddress(hwnd));
        } catch (Throwable t) {
            Log.error("could not close the message window", t);
        }
        hwnd = 0;
    }
}
