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


import org.fractalmicro.os.InterfaceStyle;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import java.awt.Component;
import java.awt.Container;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The window style, checked in the style being checked.
 *
 * Separate windows shipped broken: the menu bar and the Dock had moved into windows of
 * their own, the keyboard was still being sent to the desktop, and every check passed
 * because every check ran in the other style. So this one builds the desktop the way that
 * setting builds it and asks the questions that would have caught it.
 *
 * It builds without showing anything, so what it can prove is where things are and what
 * they are called. What it cannot prove is what a real screen does with them, and the
 * setting stays off until someone has looked.
 */
public final class SeparateWindowsTest {
    private SeparateWindowsTest() {}

    public static int count() { return 7; }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("separate windows:");

        failures += check(out, "windows are inside one until that is deliberately changed",
            !InterfaceStyle.separateWindows());

        // The desktop is built the way the other setting builds it, without being shown.
        org.fractalmicro.windowserver.Desktop desktop = null;
        try {
            desktop = buildSeparate();
        } catch (Exception e) {
            out.println("FAIL  a desktop with separate windows can be built: " + e);
            return count();
        }

        // What a screen reader needs here is the menu bar and the Dock being real, named
        // windows, which the checks below cover. What it does not need is the desktop
        // reciting which keys to press, so it must not.
        String description = desktop.getAccessibleContext().getAccessibleDescription();
        failures += check(out, "the desktop is named without being a set of instructions",
            "Finder".equals(desktop.getAccessibleContext().getAccessibleName())
            && (description == null || description.isBlank()));

        failures += check(out, "the desktop still has something to put the keyboard into",
            hasNamedFocusable(desktop));

        // The two strips are built when the screen opens, which needs windows on a screen.
        // What can be checked here is that asking for them without one does not throw and
        // does not leave the keyboard nowhere.
        boolean survivedMenu = true;
        boolean survivedDock = true;
        try {
            desktop.focusMenuBarWindow();
        } catch (Exception e) {
            survivedMenu = false;
            out.println("      " + e);
        }
        try {
            desktop.focusDockWindow();
        } catch (Exception e) {
            survivedDock = false;
            out.println("      " + e);
        }
        failures += check(out, "reaching for the menu bar works whether or not it is a window",
            survivedMenu);
        failures += check(out, "reaching for the Dock works whether or not it is a window",
            survivedDock);

        failures += check(out, "the Dock is not left inside the desktop as well",
            !isInside(desktop, desktop.dock()));

        failures += check(out, "the menu bar is not left in the desktop's own bar",
            desktop.getJMenuBar() == null);

        desktop.dispose();
        out.println("      " + (failures == 0 ? "the other style holds together as far as this can see"
                                              : failures + " failed"));
        return failures;
    }

    /** A desktop built with separate windows, without putting anything on the screen. */
    private static org.fractalmicro.windowserver.Desktop buildSeparate() throws Exception {
        org.fractalmicro.foundation.FMString was = org.fractalmicro.os.Defaults
            .of(org.fractalmicro.os.Defaults.GLOBAL)
            .string(InterfaceStyle.WINDOW_KEY, InterfaceStyle.CONTAINED);
        org.fractalmicro.os.Defaults.of(org.fractalmicro.os.Defaults.GLOBAL)
            .set(InterfaceStyle.WINDOW_KEY, InterfaceStyle.SEPARATE);
        try {
            // The forced setting is what the checking modes use; it has to be lifted for
            // as long as this one desktop is being built, and put back straight after.
            java.lang.reflect.Field forced =
                InterfaceStyle.class.getDeclaredField("forcedContained");
            forced.setAccessible(true);
            Object before = forced.get(null);
            forced.set(null, Boolean.FALSE);
            try {
                org.fractalmicro.windowserver.Desktop desktop = new org.fractalmicro.windowserver.Desktop();
                desktop.addNotify();
                desktop.validate();
                return desktop;
            } finally {
                forced.set(null, before);
            }
        } finally {
            org.fractalmicro.os.Defaults.of(org.fractalmicro.os.Defaults.GLOBAL)
                .set(InterfaceStyle.WINDOW_KEY, was);
        }
    }

    private static boolean hasNamedFocusable(Component c) {
        List<String> found = new ArrayList<>();
        collectFocusable(c, found);
        return !found.isEmpty();
    }

    private static void collectFocusable(Component c, List<String> found) {
        if (c.isFocusable() && c instanceof Accessible accessible) {
            AccessibleContext context = accessible.getAccessibleContext();
            if (context != null && context.getAccessibleName() != null
                    && !context.getAccessibleName().isBlank()) {
                found.add(context.getAccessibleName());
            }
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) collectFocusable(child, found);
        }
    }

    private static boolean isInside(Container root, Component wanted) {
        for (Component child : root.getComponents()) {
            if (child == wanted) return true;
            if (child instanceof Container container && isInside(container, wanted)) return true;
        }
        return false;
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
