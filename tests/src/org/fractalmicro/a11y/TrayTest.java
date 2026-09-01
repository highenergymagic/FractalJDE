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
package org.fractalmicro.a11y;

import org.fractalmicro.win.TrayHost;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Checks the notification area by handing it the messages a program would send.
 *
 * The real thing cannot be exercised while Explorer is running, because Explorer owns
 * the window that programs send to. The part worth checking is the decoding: the
 * structure has two shapes depending on whether the sender is 64 or 32 bit, and getting
 * an offset wrong turns a tooltip into rubbish. So the messages are built here, byte
 * for byte, and fed to the same code path the system would reach.
 */
public final class TrayTest {
    private TrayTest() {}

    private static final int SIGNATURE = 0x34753423;
    private static final int NIF_MESSAGE = 0x01;
    private static final int NIF_ICON = 0x02;
    private static final int NIF_TIP = 0x04;

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("notification area:");
        out.println("      " + (TrayHost.owningTray()
            ? "this program owns it"
            : "Explorer owns it; the decoding is checked instead"));

        failures += check(out, "a 64 bit program's icon appears", () -> {
            TrayHost.accept(message(TrayHost.NIM_ADD, true, 0x1234, 7, "Backup running"));
            return named("Backup running");
        });

        failures += check(out, "its tooltip can be changed", () -> {
            TrayHost.accept(message(TrayHost.NIM_MODIFY, true, 0x1234, 7, "Backup finished"));
            return named("Backup finished") && !named("Backup running");
        });

        failures += check(out, "it goes away when asked", () -> {
            TrayHost.accept(message(TrayHost.NIM_DELETE, true, 0x1234, 7, ""));
            return !named("Backup finished");
        });

        failures += check(out, "a 32 bit program's icon appears", () -> {
            TrayHost.accept(message(TrayHost.NIM_ADD, false, 0x5678, 3, "Old Helper"));
            return named("Old Helper");
        });

        failures += check(out, "two programs can both be there", () -> {
            TrayHost.accept(message(TrayHost.NIM_ADD, true, 0x9ABC, 1, "Screen Reader"));
            return named("Old Helper") && named("Screen Reader");
        });

        failures += check(out, "a message with the wrong signature is refused", () -> {
            byte[] bad = message(TrayHost.NIM_ADD, true, 0xDEAD, 9, "Impostor");
            writeInt(bad, 0, 0);
            TrayHost.accept(bad);
            return !named("Impostor");
        });

        // Leave nothing behind.
        TrayHost.accept(message(TrayHost.NIM_DELETE, false, 0x5678, 3, ""));
        TrayHost.accept(message(TrayHost.NIM_DELETE, true, 0x9ABC, 1, ""));

        failures += check(out, "the area is empty again", () -> TrayHost.icons().isEmpty());

        out.println("      " + (failures == 0 ? "decoding is sound" : failures + " failed"));
        return failures;
    }

    private static boolean named(String name) {
        List<TrayHost.Icon> icons = TrayHost.icons();
        for (TrayHost.Icon icon : icons) {
            if (name.equals(icon.name())) return true;
        }
        return false;
    }

    private static int check(PrintStream out, String what, java.util.function.BooleanSupplier test) {
        boolean ok;
        try {
            ok = test.getAsBoolean();
        } catch (Throwable t) {
            ok = false;
        }
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }

    /**
     * Builds what Shell_NotifyIcon puts on the wire: a signature, an operation, then
     * NOTIFYICONDATA. The 64 bit layout has eight byte handles and pads accordingly;
     * the 32 bit one does not.
     */
    private static byte[] message(int operation, boolean wide, long ownerWindow,
                                  int id, String tooltip) {
        int base = 8;
        int size = wide ? base + 976 : base + 956;
        byte[] payload = new byte[size];

        writeInt(payload, 0, SIGNATURE);
        writeInt(payload, 4, operation);

        int flags = NIF_MESSAGE | NIF_ICON | NIF_TIP;
        if (wide) {
            writeInt(payload, base, 976);                  // cbSize
            writeLong(payload, base + 8, ownerWindow);     // hWnd
            writeInt(payload, base + 16, id);              // uID
            writeInt(payload, base + 20, flags);           // uFlags
            writeInt(payload, base + 24, 0x8001);          // uCallbackMessage
            writeLong(payload, base + 32, 0);              // hIcon
            writeWide(payload, base + 40, tooltip);        // szTip
        } else {
            writeInt(payload, base, 956);
            writeInt(payload, base + 4, (int) ownerWindow);
            writeInt(payload, base + 8, id);
            writeInt(payload, base + 12, flags);
            writeInt(payload, base + 16, 0x8001);
            writeInt(payload, base + 20, 0);
            writeWide(payload, base + 24, tooltip);
        }
        return payload;
    }

    private static void writeInt(byte[] b, int at, int value) {
        b[at] = (byte) value;
        b[at + 1] = (byte) (value >> 8);
        b[at + 2] = (byte) (value >> 16);
        b[at + 3] = (byte) (value >> 24);
    }

    private static void writeLong(byte[] b, int at, long value) {
        for (int i = 0; i < 8; i++) b[at + i] = (byte) (value >> (8 * i));
    }

    private static void writeWide(byte[] b, int at, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_16LE);
        System.arraycopy(bytes, 0, b, at, Math.min(bytes.length, b.length - at - 2));
    }
}
