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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shortcuts that work wherever the keyboard happens to be.
 *
 * Swing key bindings only fire while one of this program's windows is in front, which
 * is fine for a program and useless for a desktop: Spotlight has to open while you are
 * in a mail client. Windows answers that with RegisterHotKey, which claims a
 * combination system wide and posts WM_HOTKEY to the thread that asked. Registration
 * and the messages both belong to the message window's thread.
 *
 * A claimed combination is taken away from every other program while this runs, so the
 * list is kept short and deliberate.
 */
public final class HotKeys {
    private HotKeys() {}

    public static final int MOD_ALT = 0x0001;
    public static final int MOD_CONTROL = 0x0002;
    public static final int MOD_SHIFT = 0x0004;
    public static final int MOD_WIN = 0x0008;
    /** Without this a held key repeats as fast as the keyboard does. */
    public static final int MOD_NOREPEAT = 0x4000;

    private static final int WM_HOTKEY = 0x0312;

    private static final SymbolLookup U32 = Native.library("user32.dll");

    private static final MethodHandle REGISTER_HOT_KEY = Native.handle(U32,
        "RegisterHotKey", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

    private static final MethodHandle UNREGISTER_HOT_KEY = Native.handle(U32,
        "UnregisterHotKey", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static final Map<Integer, Registration> BY_ID = new LinkedHashMap<>();
    private static int nextId = 0xF100;
    private static boolean listening;

    /** One claimed combination. */
    public static final class Registration {
        public final int id;
        public final int modifiers;
        public final int keyCode;
        public final String name;
        public final Runnable action;
        public volatile boolean claimed;

        Registration(int id, int modifiers, int keyCode, String name, Runnable action) {
            this.id = id;
            this.modifiers = modifiers;
            this.keyCode = keyCode;
            this.name = name;
            this.action = action;
        }
    }

    /**
     * Claims a combination. The action runs on the event thread. Returns the
     * registration, whose claimed flag says whether Windows agreed; something else may
     * already own the combination, which is a fact to report rather than an error.
     */
    public static synchronized Registration register(int modifiers, int keyCode,
                                                     String name, Runnable action) {
        MessageWindow window = MessageWindow.get();
        listen(window);

        Registration registration = new Registration(nextId++, modifiers | MOD_NOREPEAT,
                                                     keyCode, name, action);
        BY_ID.put(registration.id, registration);

        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        window.invoke(() -> {
            try {
                MemorySegment hwnd = MemorySegment.ofAddress(window.handle());
                int ok = (int) REGISTER_HOT_KEY.invokeExact(
                    hwnd, registration.id, registration.modifiers, registration.keyCode);
                registration.claimed = ok != 0;
                if (!registration.claimed) {
                    Log.info("the shortcut " + name + " is spoken for by something else");
                }
            } catch (Throwable t) {
                Log.error("could not claim the shortcut " + name, t);
            } finally {
                done.countDown();
            }
        });

        // Registering happens on the pump thread; wait for the answer, so a caller can
        // fall back to another combination when this one is taken.
        try {
            if (!done.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
                Log.info("claiming " + name + " took too long");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!registration.claimed) BY_ID.remove(registration.id);
        return registration;
    }

    /**
     * Claims a combination, or a second one if the first is taken. Windows keeps Win+D
     * and Win+M for Explorer, so those only come free once this program is the shell;
     * until then the Mac originals, Control F2 and Control F3, stand in.
     */
    public static Registration registerWithFallback(int modifiers, int keyCode,
                                                    int fallbackModifiers, int fallbackKeyCode,
                                                    String name, Runnable action) {
        Registration first = register(modifiers, keyCode, name, action);
        if (first.claimed) return first;
        return register(fallbackModifiers, fallbackKeyCode, name, action);
    }

    private static void listen(MessageWindow window) {
        if (listening) return;
        listening = true;
        window.addHandler((owner, message, wParam, lParam) -> {
            if (message != WM_HOTKEY) return false;
            Registration registration = BY_ID.get((int) wParam);
            if (registration == null) return false;
            javax.swing.SwingUtilities.invokeLater(registration.action);
            return true;
        });
    }

    /** Gives every claimed combination back to the rest of the system. */
    public static synchronized void releaseAll() {
        MessageWindow window = MessageWindow.get();
        for (Registration registration : BY_ID.values()) {
            if (!registration.claimed) continue;
            window.invoke(() -> {
                try {
                    MemorySegment hwnd = MemorySegment.ofAddress(window.handle());
                    int ignored = (int) UNREGISTER_HOT_KEY.invokeExact(hwnd, registration.id);
                } catch (Throwable t) {
                    Log.error("could not release the shortcut " + registration.name, t);
                }
            });
        }
        BY_ID.clear();
    }

    public static synchronized java.util.List<Registration> registrations() {
        return new java.util.ArrayList<>(BY_ID.values());
    }
}
