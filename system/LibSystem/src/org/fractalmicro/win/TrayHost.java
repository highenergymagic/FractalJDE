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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * The notification area.
 *
 * Programs put icons beside the clock by calling Shell_NotifyIcon, which is not a call
 * to the system at all: it finds the window of class Shell_TrayWnd and sends it a
 * WM_COPYDATA carrying a signature, an operation, and a NOTIFYICONDATA. Explorer owns
 * that window. A desktop replacing Explorer has to own it instead, or every background
 * program's icon vanishes, screen reader helpers among them.
 *
 * So: a window of that class, a decoder for both the 64 and 32 bit shapes of the
 * structure, a list of what programs have asked to show, and the callback messages sent
 * back when someone clicks one. Explorer keeps the class while it is running, so under
 * a normal desktop this listens and hears nothing; as the shell it hears everything.
 */
public final class TrayHost {

    /** Signature Windows puts at the front of the wide version of the message. */
    private static final int NI_NOTIFY_SIG = 0x34753423;

    public static final int NIM_ADD = 0;
    public static final int NIM_MODIFY = 1;
    public static final int NIM_DELETE = 2;
    public static final int NIM_SETFOCUS = 3;
    public static final int NIM_SETVERSION = 4;

    private static final int NIF_MESSAGE = 0x01;
    private static final int NIF_ICON = 0x02;
    private static final int NIF_TIP = 0x04;
    private static final int NIF_STATE = 0x08;

    private static final int NIS_HIDDEN = 0x01;

    private static final int WM_COPYDATA = 0x004A;
    private static final int WM_LBUTTONUP = 0x0202;
    private static final int WM_RBUTTONUP = 0x0205;
    private static final int WM_CONTEXTMENU = 0x007B;

    private static final int WS_POPUP = 0x80000000;
    private static final int WS_CHILD = 0x40000000;

    /** One icon a program has asked to show. */
    public static final class Icon {
        public final long ownerWindow;
        public final int id;
        public String tooltip = "";
        public long iconHandle;
        public int callbackMessage;
        public boolean hidden;
        public int version;

        Icon(long ownerWindow, int id) {
            this.ownerWindow = ownerWindow;
            this.id = id;
        }

        public String key() { return ownerWindow + ":" + id; }

        /**
         * What the icon is called: its tooltip, or failing that the program's window title.
         *
         * An icon that answers to neither has no name here, and nothing is invented for
         * it. This layer is beneath the one that holds the words, so a name for something
         * nameless belongs to whoever puts it on the screen.
         */
        public String name() {
            if (!tooltip.isBlank()) return tooltip.lines().findFirst().orElse(tooltip).trim();
            for (User32.Win w : User32.taskWindows()) {
                if (w.handle == ownerWindow) return w.title;
            }
            return "";
        }

        @Override public String toString() { return name(); }
    }

    private static final Map<String, Icon> ICONS = new LinkedHashMap<>();
    private static final List<Runnable> LISTENERS = new ArrayList<>();
    private static long trayWindow;
    private static long notifyWindow;
    private static boolean owned;
    private static boolean started;

    private TrayHost() {}

    /** True when this program owns the notification area rather than Explorer. */
    public static boolean owningTray() { return owned; }

    public static long window() { return trayWindow; }

    public static synchronized List<Icon> icons() {
        List<Icon> out = new ArrayList<>();
        for (Icon i : ICONS.values()) if (!i.hidden) out.add(i);
        return out;
    }

    public static void onChange(Runnable r) { LISTENERS.add(r); }

    /**
     * Puts up the windows programs look for. Nothing happens when Explorer already has
     * them, which is the usual case until this is the shell.
     */
    public static synchronized void start() {
        if (started) return;
        started = true;

        // Only when nothing else is the shell. A window class belongs to the process that
        // registered it, not to the machine, so a second Shell_TrayWnd can always be made
        // and Explorer's carries on existing beside it. Both then answer to FindWindow, and
        // whichever is nearer the front of the window list is the one everybody reaches:
        // the icons programs send, and the edge reservations sent by SHAppBarMessage, which
        // finds the shell that same way. A decoy standing in front of a running Explorer
        // swallows both, so the menu bar and the Dock quietly stop reserving their strips.
        long theirs = User32.findWindowByClass("Shell_TrayWnd");
        if (theirs != 0 && User32.processOf(theirs) != ProcessHandle.current().pid()) {
            Log.info("the notification area belongs to something else, probably Explorer");
            return;
        }

        MessageWindow window = MessageWindow.sharedWindow();
        window.addHandler(TrayHost::handle);

        trayWindow = window.createWindow("Shell_TrayWnd", null, WS_POPUP, 0);
        if (trayWindow == 0) {
            Log.info("the notification area could not be taken over");
            return;
        }
        notifyWindow = window.createWindow("TrayNotifyWnd", null, WS_CHILD, trayWindow);
        owned = notifyWindow != 0;
        Log.info(owned ? "the notification area is ours" : "the notification area is half ours");
    }

    /* ------------------------------------------------------------ messages */

    private static boolean handle(long owner, int message, long wParam, long lParam) {
        if (message != WM_COPYDATA || owner == 0 || owner != trayWindow) return false;
        try {
            // COPYDATASTRUCT: a tag, a length, and a pointer, already mapped into this
            // process by the system.
            MemorySegment copyData = MemorySegment.ofAddress(lParam).reinterpret(24);
            long tag = copyData.get(ValueLayout.JAVA_LONG, 0);
            int length = copyData.get(ValueLayout.JAVA_INT, 8);
            long dataAddress = copyData.get(ValueLayout.JAVA_LONG, 16);
            if (tag != 1 || length <= 0 || dataAddress == 0) return false;

            byte[] payload = MemorySegment.ofAddress(dataAddress)
                                          .reinterpret(length)
                                          .toArray(ValueLayout.JAVA_BYTE);
            return accept(payload);
        } catch (Throwable t) {
            Log.error("a notification area message could not be read", t);
            return false;
        }
    }

    /**
     * Decodes one tray message. Public so it can be checked without Explorer having to
     * stand aside: the shape is the thing worth testing.
     */
    public static synchronized boolean accept(byte[] payload) {
        if (payload.length < 16) return false;
        int signature = readInt(payload, 0);
        if (signature != NI_NOTIFY_SIG) return false;
        int operation = readInt(payload, 4);

        int base = 8;
        int cbSize = readInt(payload, base);
        boolean wide = sixtyFourBit(cbSize, payload.length - base);
        long ownerWindow = wide ? readLong(payload, base + 8) : readInt(payload, base + 4);
        int id = wide ? readInt(payload, base + 16) : readInt(payload, base + 8);
        int flags = wide ? readInt(payload, base + 20) : readInt(payload, base + 12);
        int callback = wide ? readInt(payload, base + 24) : readInt(payload, base + 16);
        long iconHandle = wide ? readLong(payload, base + 32) : readInt(payload, base + 20);
        int tipOffset = wide ? base + 40 : base + 24;
        int stateOffset = wide ? base + 296 : base + 280;

        String key = ownerWindow + ":" + id;
        switch (operation) {
            case NIM_DELETE: {
                boolean removed = ICONS.remove(key) != null;
                if (removed) changed();
                return true;
            }
            case NIM_SETVERSION: {
                Icon icon = ICONS.get(key);
                if (icon != null) icon.version = callback;
                return true;
            }
            case NIM_ADD:
            case NIM_MODIFY: {
                Icon icon = ICONS.computeIfAbsent(key, k -> new Icon(ownerWindow, id));
                if ((flags & NIF_MESSAGE) != 0) icon.callbackMessage = callback;
                if ((flags & NIF_ICON) != 0) icon.iconHandle = iconHandle;
                if ((flags & NIF_TIP) != 0) {
                    icon.tooltip = readWide(payload, tipOffset, 128);
                }
                if ((flags & NIF_STATE) != 0 && payload.length > stateOffset + 8) {
                    int state = readInt(payload, stateOffset);
                    int stateMask = readInt(payload, stateOffset + 4);
                    if ((stateMask & NIS_HIDDEN) != 0) icon.hidden = (state & NIS_HIDDEN) != 0;
                }
                changed();
                return true;
            }
            default:
                return false;
        }
    }

    /**
     * Which shape the sender used. A 32 bit program's handles are four bytes wide and
     * everything after them shifts, so guessing from the total length is not enough:
     * the two structures differ by twenty bytes out of nine hundred. The size the
     * sender declares says which it is.
     */
    private static boolean sixtyFourBit(int cbSize, int available) {
        switch (cbSize) {
            case 976: case 968: case 952: case 512:
                return true;
            case 956: case 948: case 936: case 504: case 488:
                return false;
            default:
                return available >= 976;
        }
    }

    private static void changed() {
        javax.swing.SwingUtilities.invokeLater(() -> {
            for (Runnable r : new ArrayList<>(LISTENERS)) r.run();
        });
    }

    /* ------------------------------------------------------------ clicking */

    /** Tells the program that owns an icon that it was clicked. */
    public static void click(Icon icon, boolean rightButton) {
        if (icon == null || icon.callbackMessage == 0) return;
        int buttonMessage = rightButton ? WM_RBUTTONUP : WM_LBUTTONUP;
        // Before version 4 the icon id is in wParam and the mouse message in lParam.
        long wParam = icon.version >= 4 ? 0 : icon.id;
        long lParam = buttonMessage;
        if (icon.version >= 4) {
            wParam = 0;                                    // no screen position to give
            lParam = ((long) icon.id << 16) | buttonMessage;
        }
        User32.post(icon.ownerWindow, icon.callbackMessage, wParam, lParam);
        if (rightButton) {
            // Programs that use the newer protocol expect this as well.
            User32.post(icon.ownerWindow, icon.callbackMessage,
                        wParam, ((long) icon.id << 16) | WM_CONTEXTMENU);
        }
    }

    /* ------------------------------------------------------------- reading */

    private static int readInt(byte[] b, int at) {
        if (at + 4 > b.length) return 0;
        return (b[at] & 0xFF) | ((b[at + 1] & 0xFF) << 8)
             | ((b[at + 2] & 0xFF) << 16) | ((b[at + 3] & 0xFF) << 24);
    }

    private static long readLong(byte[] b, int at) {
        if (at + 8 > b.length) return 0;
        long v = 0;
        for (int i = 7; i >= 0; i--) v = (v << 8) | (b[at + i] & 0xFFL);
        return v;
    }

    private static String readWide(byte[] b, int at, int characters) {
        if (at >= b.length) return "";
        int end = at;
        int limit = Math.min(b.length - 1, at + characters * 2);
        while (end < limit && !(b[end] == 0 && b[end + 1] == 0)) end += 2;
        return new String(b, at, end - at, StandardCharsets.UTF_16LE);
    }
}
