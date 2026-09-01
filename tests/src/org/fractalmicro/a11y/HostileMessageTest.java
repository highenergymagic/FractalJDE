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

import org.fractalmicro.nib.Nib;
import org.fractalmicro.nib.Nib.ControlClass;
import org.fractalmicro.plist.Plist;
import org.fractalmicro.win.Pipes;
import org.fractalmicro.windowserver.WindowServer;
import org.fractalmicro.xpc.Connection;
import org.fractalmicro.xpc.Message;
import org.fractalmicro.xpc.Service;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * What a service does when the message was meant to hurt it.
 *
 * Every other check sends a message this system wrote. A program on the same machine is
 * under no such obligation, and the ports take anyone: a message can be built to run the
 * reader out of memory with a length it never has to back up, or out of stack with a
 * value that points back at itself, or simply be nonsense. None of that may take a service
 * down. This is a check rather than a hope because for a long time it would
 * have failed, silently, because nothing ever sent a service anything it had not made.
 */
public final class HostileMessageTest {
    private HostileMessageTest() {}

    public static int count() { return 11; }

    private static final String SERVICE = "org.fractalmicro.checking.hostile";

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("hostile messages:");

        /* ----------------------------------------- the parser, on its own */

        failures += check(out, "a binary plist claiming billions of objects is refused",
            refused(hugeCount()));
        failures += check(out, "a binary plist that points back at itself is refused",
            refused(cycle()));
        failures += check(out, "a property list nested past all reason is refused",
            refused(deeplyNested()));
        failures += check(out, "plain nonsense is refused rather than parsed",
            refused("not a property list at all".getBytes(StandardCharsets.UTF_8)));

        /* ------------------------------------- a real service, over the wire */

        Service service = new Service(SERVICE, request ->
            Message.of("echo").put("said", request.string("say", "")));
        if (!service.start()) {
            out.println("FAIL  the checking service would not start");
            return failures + 4;
        }
        try {
            boolean survivedAll = true;
            for (byte[] attack : new byte[][]{hugeCount(), cycle(), deeplyNested(),
                                              new byte[]{0, 1, 2, 3}}) {
                sendRaw(SERVICE, attack);
                if (!Connection.available(SERVICE)) { survivedAll = false; break; }
            }
            failures += check(out, "a service handed each of these is still there after",
                survivedAll);

            boolean stillAnswers = false;
            try {
                Message reply = Connection.ask(SERVICE, Message.of("echo").put("say", "well"));
                stillAnswers = "well".equals(reply.string("said", ""));
            } catch (Exception e) {
                out.println("      " + e);
            }
            failures += check(out, "and answers a well-formed message as if nothing happened",
                stillAnswers);

            // A hostile message is answered with an error, not silence: the sender is told
            // no, and the connection is not left hanging.
            boolean toldNo = false;
            try {
                Pipes.Port port = Pipes.connect(SERVICE, 500);
                if (port != null) {
                    try (port) {
                        port.write(hugeCount());
                        byte[] reply = port.read();
                        toldNo = reply != null;
                    }
                }
            } catch (Exception e) {
                out.println("      " + e);
            }
            failures += check(out, "a hostile message is answered, not left hanging", toldNo);

            failures += check(out, "the service is fenced to this account",
                org.fractalmicro.win.Security.userOnly() != java.lang.foreign.MemorySegment.NULL);
        } finally {
            service.close();
        }

        failures += windowServerChecks(out);

        out.println("      " + (failures == 0 ? "a bad message is a bad message, and no more"
                                              : failures + " failed"));
        return failures;
    }

    /**
     * The window server, asked for things it should refuse to allocate.
     *
     * A message can be small, well formed and still hostile: every field in it is a number
     * or a name the sender chose, and any of them the server allocates against is a way to
     * make it grow. The size cap and the parser bounds do nothing about that, which is why
     * these are here as well.
     */
    private static int windowServerChecks(PrintStream out) {
        int failures = 0;
        WindowServer server = WindowServer.get();
        server.start();

        // Asking for the next event names the program asking. If that name is what decides
        // where a queue comes from, a sender with a fresh name each time allocates for ever.
        int before = server.programCount();
        try {
            for (int i = 0; i < 200; i++) {
                Connection.ask(WindowServer.SERVICE, Message.of(WindowServer.NEXT_EVENT)
                    .put("application", "made-up-" + i)
                    .put("timeout", 0L));
            }
        } catch (Exception e) {
            out.println("      " + e);
        }
        int after = server.programCount();
        out.println("      programs held before " + before + ", after 200 names " + after);
        failures += check(out, "naming a program that has no window allocates nothing for it",
            after == before);

        // A description is data too. Nothing in it may ask for a window bigger than a screen.
        try (org.fractalmicro.appkit.FMApplication app = org.fractalmicro.appkit.FMApplication.named(org.fractalmicro.foundation.FMString.of("Huge"))) {
            Nib huge = new Nib.Builder()
                .title(org.fractalmicro.foundation.FMString.of("Huge")).size(2000000000, 2000000000).resizable(false)
                .add(ControlClass.FMLabel, org.fractalmicro.foundation.FMString.of("label"), org.fractalmicro.foundation.FMString.of("Label"), org.fractalmicro.foundation.FMString.of("text"),
                     0, 0, 2000000000, 2000000000)
                .build();
            app.showWindow(huge);
            drain();
            javax.swing.JInternalFrame frame = frameTitled("Huge");
            int width = frame == null ? -1 : frame.getWidth();
            out.println("      the window asked for 2000000000 and got " + width);
            failures += check(out, "a window bigger than any screen is cut down to one",
                frame != null && width > 0 && width <= 16384);
            app.hideWindow();
            drain();
        } catch (Exception e) {
            out.println("FAIL  a window bigger than any screen is cut down to one: " + e);
            failures++;
        }

        failures += check(out, "and the programs it does hold stay within the ceiling",
            server.programCount() <= 64);
        return failures;
    }

    private static javax.swing.JInternalFrame frameTitled(String title) {
        for (javax.swing.JInternalFrame f : org.fractalmicro.windowserver.Desktop.get().windows()) {
            if (title.equals(f.getTitle())) return f;
        }
        return null;
    }

    private static void drain() {
        try {
            if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
                javax.swing.SwingUtilities.invokeAndWait(() -> { });
            }
            Thread.sleep(150);
        } catch (Exception ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /* ------------------------------------------------------------- helpers */

    private static boolean refused(byte[] bytes) {
        try {
            Plist.parse(bytes);
            return false;                      // parsed something it should have refused
        } catch (java.io.IOException expected) {
            return true;                       // said no, which is the point
        } catch (Throwable notThis) {
            // An Error escaping (out of memory, out of stack) is exactly the failure this
            // is here to catch. It is not a pass.
            return false;
        }
    }

    private static void sendRaw(String service, byte[] bytes) {
        Pipes.Port port = Pipes.connect(service, 500);
        if (port == null) return;
        try (port) {
            port.write(bytes);
            port.read();
        }
    }

    /** A binary plist whose object count is enormous, to force a huge allocation. */
    private static byte[] hugeCount() {
        byte[] data = new byte[48];
        System.arraycopy("bplist00".getBytes(StandardCharsets.US_ASCII), 0, data, 0, 8);
        int trailer = data.length - 32;
        data[trailer + 6] = 1;                 // offsetSize
        data[trailer + 7] = 1;                 // refSize
        writeLong(data, trailer + 8, 0x7fffffffL);   // count
        writeLong(data, trailer + 16, 0);            // top
        writeLong(data, trailer + 24, 8);            // table offset
        return data;
    }

    /** A binary plist holding one array whose only element is the array itself. */
    private static byte[] cycle() {
        byte[] data = new byte[43];
        System.arraycopy("bplist00".getBytes(StandardCharsets.US_ASCII), 0, data, 0, 8);
        data[8] = (byte) 0xA1;                 // array of one
        data[9] = 0x00;                        // element is object 0, this array
        data[10] = 0x08;                       // offset table: object 0 sits at offset 8
        int trailer = 11;
        data[trailer + 6] = 1;                 // offsetSize
        data[trailer + 7] = 1;                 // refSize
        writeLong(data, trailer + 8, 1);       // count
        writeLong(data, trailer + 16, 0);      // top
        writeLong(data, trailer + 24, 10);     // table offset
        return data;
    }

    /** An XML plist that opens far more arrays than anything real would. */
    private static byte[] deeplyNested() {
        StringBuilder sb = new StringBuilder(
            "<?xml version=\"1.0\"?><plist version=\"1.0\">");
        int depth = 500;
        for (int i = 0; i < depth; i++) sb.append("<array>");
        sb.append("<string>bottom</string>");
        for (int i = 0; i < depth; i++) sb.append("</array>");
        sb.append("</plist>");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void writeLong(byte[] d, int at, long value) {
        for (int i = 7; i >= 0; i--) {
            d[at + i] = (byte) (value & 0xFF);
            value >>= 8;
        }
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
