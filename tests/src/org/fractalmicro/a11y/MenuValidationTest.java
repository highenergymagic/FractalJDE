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

import org.fractalmicro.appkit.AppWindow;
import org.fractalmicro.appkit.FMApplication;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.nib.Nib;
import org.fractalmicro.windowserver.Desktop;
import org.fractalmicro.windowserver.WindowServer;

import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.io.PrintStream;
import java.util.List;

/**
 * Menus that stop lying about a program in another process.
 *
 * A menu in Cocoa asks, every time it opens, which of its items can be used, and asking is
 * a method call. Here the desktop draws the menu and the program is elsewhere, so the
 * question is carried.
 *
 * The default costs a program nothing: an item is live when the program has said what it
 * does. Beyond that a program answers for itself.
 *
 * The last check is what happens when it does not answer at all, a stopped program being
 * the case where the bar could freeze.
 */
public final class MenuValidationTest {
    private MenuValidationTest() {}

    public static int count() { return 10; }

    public static int run(Desktop desktop, PrintStream out) {
        int failures = 0;
        out.println();
        out.println("menus that ask the program:");

        WindowServer server = WindowServer.sharedServer();
        if (!server.start() && !server.isRunning()) {
            out.println("FAIL  the window server is not running");
            return count();
        }

        Thread loop = null;
        FMApplication app = FMApplication.named(FMString.of("Validating"));
        try {
            // A program with two of the three commands. Print is in its menu and it has
            // never said what Print does, which is the lie this is about.
            app.on(FMString.of("save"), e -> { });
            app.on(FMString.of("close"), e -> { });
            app.showWindow(new Nib.Builder()
                .title(FMString.of("Validating")).size(320, 200)
                .menu(FMString.of("File"),
                      Nib.MenuItem.of(FMString.of("Save"), FMString.of("save"), FMString.EMPTY),
                      Nib.MenuItem.of(FMString.of("Print"), FMString.of("print"), FMString.EMPTY),
                      Nib.MenuItem.of(FMString.of("Close"), FMString.of("close"), FMString.EMPTY))
                .build());
            drain();

            // Its run loop, which is what answers. A program that is not reading events is
            // a program that has stopped, and the last check turns this off to be one.
            loop = new Thread(app::run, "validating");
            loop.setDaemon(true);
            loop.start();

            JMenu file = menuOf(desktop, "Validating", "File");
            if (file == null) {
                out.println("FAIL  the program's menu reached the bar");
                return count();
            }
            failures += check(out, "the program's menu reached the bar", true);

            open(file);
            failures += check(out, "a command the program has is live",
                enabled(file, "Save") && enabled(file, "Close"));
            failures += check(out, "and one it has never heard of is not",
                !enabled(file, "Print"));

            // What the program says for itself, which is the half that changes while it
            // runs. Nothing has been typed, so there is nothing to save.
            boolean[] anythingToSave = {false};
            app.onValidate(action ->
                !action.sameAs(FMString.of("save")) || anythingToSave[0]);

            open(file);
            failures += check(out, "with nothing to save, Save is grey",
                !enabled(file, "Save") && enabled(file, "Close"));

            anythingToSave[0] = true;
            open(file);
            failures += check(out, "and once there is, it is not",
                enabled(file, "Save"));

            // A program that has stopped answering. Its menus stay as they were rather than
            // emptying out, and the bar waits a quarter of a second at most for it.
            app.stop();
            loop.join(2000);
            long started = System.nanoTime();
            open(file);
            long waited = (System.nanoTime() - started) / 1_000_000;
            failures += check(out, "a program that has stopped does not hold up its own menu",
                waited < WindowServer.VALIDATION_WAIT_MILLIS * 4);
            failures += check(out, "and its menu keeps what it last said",
                enabled(file, "Save") && enabled(file, "Close"));
        } catch (Exception e) {
            out.println("FAIL  menus that ask the program: " + e);
            failures++;
        } finally {
            app.stop();
            if (loop != null) {
                try { loop.join(2000); } catch (InterruptedException ignored) { }
            }
            // The window as well as the connection. A window left open owns the menu bar
            // while it is in front, and the bar would then be this check's menus for the
            // rest of the run rather than the file manager's.
            app.close(app.mainWindow());
            app.close();
            drain();
        }

        failures += checkAShippedProgram(desktop, out);
        failures += checkAShortcutStillWorks(desktop, out);

        out.println("      " + (failures == 0 ? "a menu says what the program says"
                                              : failures + " failed"));
        return failures;
    }

    /**
     * Whether a shortcut still works after its menu was opened at a bad moment.
     *
     * A switched-off item does not answer one, so opening the File menu once with nothing
     * chosen left Open grey and it stayed grey: choosing a file afterwards did not bring
     * it back. Cocoa validates before performKeyEquivalent, and so does this.
     */
    private static int checkAShortcutStillWorks(Desktop desktop, PrintStream out) {
        int failures = 0;
        javax.swing.KeyStroke cmdO = javax.swing.KeyStroke.getKeyStroke(
            java.awt.event.KeyEvent.VK_O, org.fractalmicro.windowserver.MainMenu.CMD);
        javax.swing.JMenuItem open = itemFor(desktop, cmdO);
        if (open == null) {
            out.println("FAIL  there is an item on the Open shortcut");
            return 2;
        }

        // Grey it the way opening the menu with nothing chosen would.
        open.setEnabled(false);
        org.fractalmicro.nib.NibLoader.validateEverything();
        failures += check(out, "a command with nothing to work on is switched off",
            !open.isEnabled());

        // Now something is chosen, and the shortcut is pressed without the menu ever
        // being opened. Validation has to happen here or the key does nothing.
        org.fractalmicro.ui.Finder.newWindow(
            org.fractalmicro.os.OSPaths.systemApplications().toFile());
        drain();
        org.fractalmicro.ui.Finder.frontWindow().selectAll();
        drain();
        java.awt.event.KeyEvent pressed = new java.awt.event.KeyEvent(
            desktop.mainMenu(), java.awt.event.KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(), org.fractalmicro.windowserver.MainMenu.CMD,
            java.awt.event.KeyEvent.VK_O, 'o');
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().dispatchEvent(pressed);
        drain();
        failures += check(out, "and comes back for the shortcut once there is",
            open.isEnabled());
        return failures;
    }

    /** The item a shortcut is on, wherever it is in the bar. */
    private static javax.swing.JMenuItem itemFor(Desktop desktop,
                                                 javax.swing.KeyStroke stroke) {
        javax.swing.JMenuBar bar = desktop.mainMenu();
        for (int i = 0; i < bar.getMenuCount(); i++) {
            javax.swing.JMenuItem found = carrying(bar.getMenu(i), stroke);
            if (found != null) return found;
        }
        return null;
    }

    private static javax.swing.JMenuItem carrying(javax.swing.MenuElement where,
                                                  javax.swing.KeyStroke stroke) {
        if (where == null) return null;
        for (javax.swing.MenuElement child : where.getSubElements()) {
            if (child instanceof javax.swing.JMenuItem item
                    && stroke.equals(item.getAccelerator())) {
                return item;
            }
            javax.swing.JMenuItem deeper = carrying(child, stroke);
            if (deeper != null) return deeper;
        }
        return null;
    }

    /**
     * A program this system actually ships, started for real and asked about its own menus.
     *
     * The rest of this is a program written to be asked. This catches the change failing in
     * the direction that matters: a program whose menus name one set of commands and whose
     * code answers to another comes up entirely grey, and every check above still passes.
     * A machine where TextEdit will not start says so and checks nothing.
     */
    private static int checkAShippedProgram(Desktop desktop, PrintStream out) {
        if (!org.fractalmicro.bundle.Bundles.openIdentifier("org.fractalmicro.textedit")) {
            out.println("      TextEdit will not start here, so its menus are not asked");
            return 0;
        }
        JInternalFrame window = null;
        for (int tries = 0; tries < 60 && window == null; tries++) {
            drain();
            window = windowOf(desktop, "TextEdit");
            if (window == null) {
                try { Thread.sleep(100); } catch (InterruptedException ignored) { }
            }
        }
        if (window == null) {
            out.println("      TextEdit put no window up here, so its menus are not asked");
            return 0;
        }
        try {
            JMenu file = menuNamed((AppWindow) window, "File");
            if (file == null) {
                out.println("      TextEdit brought no File menu, so nothing was asked");
                return 0;
            }
            open(file);
            int live = 0;
            for (int i = 0; i < file.getItemCount(); i++) {
                JMenuItem item = file.getItem(i);
                if (item != null && item.isEnabled()) live++;
            }
            if (live == 0) out.println("      every item in TextEdit's File menu came back grey");
            return check(out, "a program this system ships answers about its own menus", live > 0);
        } finally {
            // Shut again, because a window left open owns the menu bar while it is in
            // front, and everything checked after this would be reading TextEdit's menus
            // where it meant to read the file manager's.
            JInternalFrame open = window;
            try {
                javax.swing.SwingUtilities.invokeAndWait(open::doDefaultCloseAction);
            } catch (Exception ignored) { }
            drain();
        }
    }

    /** Opens a menu, which is the moment the question is asked. */
    private static void open(JMenu menu) {
        menu.setSelected(true);
        menu.setSelected(false);
    }

    private static boolean enabled(JMenu menu, String title) {
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            if (item != null && title.equals(item.getText())) return item.isEnabled();
        }
        return false;
    }

    /** The named menu of the named program, as the bar would build it. */
    private static JMenu menuOf(Desktop desktop, String application, String title) {
        JInternalFrame frame = windowOf(desktop, application);
        return frame == null ? null : menuNamed((AppWindow) frame, title);
    }

    private static JMenu menuNamed(AppWindow window, String title) {
        for (JMenu menu : window.applicationMenus()) {
            if (title.equals(menu.getText())) return menu;
        }
        return null;
    }

    /** A window belonging to the named program. */
    private static JInternalFrame windowOf(Desktop desktop, String application) {
        for (JInternalFrame frame : desktop.windows()) {
            if (frame instanceof AppWindow window
                    && application.equals(window.applicationName())) {
                return frame;
            }
        }
        return null;
    }

    /** Lets the screen catch up, since the window is made on the main thread. */
    private static void drain() {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) { }
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
