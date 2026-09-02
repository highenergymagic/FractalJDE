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

import org.fractalmicro.appkit.FMApplication;
import org.fractalmicro.calculator.Calculator;
import org.fractalmicro.nib.Nib;
import org.fractalmicro.windowserver.Desktop;
import org.fractalmicro.windowserver.MainMenu;
import org.fractalmicro.windowserver.WindowServer;

import javax.swing.*;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A program somewhere else owning the menu bar.
 *
 * A window was never the whole of what a program puts on the screen. Until its menus can
 * come across too, a program in another process is a window with no commands, and every
 * program has to stay inside the desktop to have a File menu, which is the thing that was
 * stopping anything from actually moving out.
 *
 * So: the menus travel in the same description as the window, the bar shows them while that
 * window is in front, Finder gets the bar back when it is not, and choosing one sends the
 * command back to whoever asked for it.
 */
public final class MenuBridgeTest {
    private MenuBridgeTest() {}

    public static int count() { return 10; }

    public static int run(Desktop desktop, PrintStream out) {
        int failures = 0;
        out.println();
        out.println("menus from elsewhere:");

        /* ------------------------------------------- the description carries them */
        Nib described;
        try {
            described = interfaceNamed("Calculator", "Calculator");
        } catch (java.io.IOException e) {
            out.println("      no interface files here; this is a built copy");
            return 0;
        }
        failures += check(out, "a description can carry menus as well as controls",
            described.menus().count() == 3
            && described.menus().at(0).title().sameAs(org.fractalmicro.foundation.FMString.of("File")));

        try {
            Nib back = Nib.parse(described.toBytes());
            List<String> names = new ArrayList<>();
            for (Nib.Menu menu : back.menus()) names.add(menu.title().toString());
            out.println("      the menus survive as: " + String.join(", ", names));
            failures += check(out, "and they survive being written and read",
                back.menus().count() == described.menus().count()
                && back.menus().at(1).items().count()
                   == described.menus().at(1).items().count());
            boolean hasLine = false;
            for (Nib.MenuItem item : back.menus().at(1).items()) {
                if (item.separator()) hasLine = true;
            }
            failures += check(out, "including the line between two groups of commands",
                hasLine);
        } catch (Exception e) {
            out.println("FAIL  the menus survive being written and read: " + e);
            failures += 2;
        }

        failures += check(out, "a menu with no name is refused",
            refusedMenu("", "Close") && refusedMenu("File", ""));

        /* ------------------------------------------------------- the keys they use */
        KeyStroke stroke = WindowServer.strokeOf(
            Nib.MenuItem.of(org.fractalmicro.foundation.FMString.of("Close"), org.fractalmicro.foundation.FMString.of("close"), org.fractalmicro.foundation.FMString.of("w"), org.fractalmicro.foundation.FMString.of("command")));
        failures += check(out, "a description says its keys the way a person says them",
            stroke != null && stroke.equals(
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, MainMenu.CMD)));

        /* --------------------------------------------------------- across the wire */
        WindowServer server = WindowServer.sharedServer();
        server.start();
        String wasInFront = desktop.mainMenu().currentApplication();

        try (FMApplication app = FMApplication.named(Calculator.NAME)) {
            app.showWindow(described);
            drain();

            List<String> bar = menusOf(desktop.mainMenu());
            out.println("      the bar reads: " + String.join(", ", bar));
            failures += check(out, "the bar names the program and shows its menus",
                bar.contains(Calculator.NAME.toString()) && bar.contains("File")
                && bar.contains("Edit") && bar.contains("View"));
            failures += check(out, "and Finder's own menus have stood aside",
                !bar.contains("Go"));

            // The menus have to be laid out, not merely present: one that has never been
            // given a size sits in the bar and paints nothing.
            failures += check(out, "the menus have a size, so they are on the screen",
                sized(desktop.mainMenu(), Calculator.NAME.toString())
                && sized(desktop.mainMenu(), "Edit"));

            JMenuItem copy = itemNamed(desktop.mainMenu(), "Edit", "Copy");
            if (copy != null) SwingUtilities.invokeLater(copy::doClick);
            drain();
            FMApplication.Event event = app.nextEvent(2000);
            failures += check(out, "choosing a menu command sends it to the program",
                event != null && event.isMenu()
                && event.action().sameAs(org.fractalmicro.foundation.FMString.of("copy"))
                && event.control().sameAs(org.fractalmicro.foundation.FMString.of("Copy")));

            app.hideWindow();
            drain();
            failures += check(out, "and Finder has the bar back when the window closes",
                menusOf(desktop.mainMenu()).contains("Go"));
        } catch (Exception e) {
            out.println("FAIL  the checks across the wire ran: " + e);
            failures++;
        }

        if (!"Finder".equals(wasInFront)) desktop.mainMenu().showFinderMenus();

        out.println("      " + (failures == 0 ? "a program elsewhere owns the bar like any other"
                                              : failures + " failed"));
        return failures;
    }

    /* ------------------------------------------------------------- helpers */

    private static boolean refusedMenu(String menuTitle, String itemTitle) {
        org.fractalmicro.foundation.FMMutableDictionary item =
            org.fractalmicro.foundation.FMMutableDictionary.empty();
        item.set(Nib.TITLE, org.fractalmicro.foundation.FMString.of(itemTitle));
        org.fractalmicro.foundation.FMMutableDictionary menu =
            org.fractalmicro.foundation.FMMutableDictionary.empty();
        menu.set(Nib.TITLE, org.fractalmicro.foundation.FMString.of(menuTitle));
        menu.set(Nib.ITEMS, org.fractalmicro.foundation.FMArray.of((Object) item.asDictionary()));
        org.fractalmicro.foundation.FMMutableDictionary window =
            org.fractalmicro.foundation.FMMutableDictionary.empty();
        window.set(Nib.TITLE, org.fractalmicro.foundation.FMString.of("Bad"));
        org.fractalmicro.foundation.FMMutableDictionary root =
            org.fractalmicro.foundation.FMMutableDictionary.empty();
        root.set(Nib.WINDOW, window.asDictionary());
        root.set(Nib.CONTROLS, org.fractalmicro.foundation.FMArray.empty());
        root.set(Nib.MENUS, org.fractalmicro.foundation.FMArray.of((Object) menu.asDictionary()));
        try {
            Nib.from(root.asDictionary());
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static List<String> menusOf(JMenuBar bar) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu menu = bar.getMenu(i);
            if (menu != null && menu.getText() != null && !menu.getText().isEmpty()) {
                names.add(menu.getText());
            }
        }
        return names;
    }

    private static boolean sized(JMenuBar bar, String name) {
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu menu = bar.getMenu(i);
            if (menu != null && name.equals(menu.getText())) {
                return menu.getWidth() > 0 && menu.getHeight() > 0;
            }
        }
        return false;
    }

    private static JMenuItem itemNamed(JMenuBar bar, String menuName, String itemName) {
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu menu = bar.getMenu(i);
            if (menu == null || !menuName.equals(menu.getText())) continue;
            for (java.awt.Component child : menu.getMenuComponents()) {
                if (child instanceof JMenuItem item && itemName.equals(item.getText())) {
                    return item;
                }
            }
        }
        return null;
    }

    private static void drain() {
        try {
            if (!SwingUtilities.isEventDispatchThread()) {
                SwingUtilities.invokeAndWait(() -> { });
            }
            Thread.sleep(150);
        } catch (Exception ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
    /**
     * An interface file as it ships, read from where a program's resources are written.
     *
     * Reading the file rather than asking a program to describe itself is the point: what
     * a program shows is what is in the file, and a check that asked the program would
     * agree with itself no matter what the file said.
     */
    static Nib interfaceNamed(String app, String name) throws java.io.IOException {
        java.io.File at = new java.io.File("apps/" + app + "/resources/" + name + ".xib");
        if (!at.isFile()) {
            at = new java.io.File("../apps/" + app + "/resources/" + name + ".xib");
        }
        if (!at.isFile()) throw new java.io.IOException("no interface file for " + name);
        return org.fractalmicro.nib.Xib.read(org.fractalmicro.foundation.FMURL.of(at));
    }

}
