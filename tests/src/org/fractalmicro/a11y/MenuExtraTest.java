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

import org.fractalmicro.bundle.Bundle;
import org.fractalmicro.windowserver.Desktop;
import org.fractalmicro.windowserver.MainMenu;
import org.fractalmicro.windowserver.SystemUIServer;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Who owns which half of the menu bar.
 *
 * The bar has two halves with two owners, and for a long time this one built both. The
 * menus on the left belong to whichever program is in front, and change when it does. The
 * indicators on the right belong to nobody's program: the clock keeps the time whatever
 * is running.
 *
 * On the system this imitates those indicators are menu extras, bundles ending in .menu
 * kept in CoreServices, loaded by a separate thing whose whole job that is. Here they are
 * the same: real bundles with real executables, found on disk and loaded by the loader.
 * The point of checking is that a bar that knows what a clock is will pass every other
 * check in this suite while being the wrong shape.
 */
public final class MenuExtraTest {
    private MenuExtraTest() {}

    public static int count() { return 6; }

    public static int run(Desktop desktop, PrintStream out) {
        int failures = 0;
        out.println();
        out.println("the menu bar and its owners:");

        File folder = SystemUIServer.menuExtrasFolder();
        List<String> found = new ArrayList<>();
        File[] kids = folder.listFiles();
        if (kids != null) {
            for (File f : kids) {
                if (f.isDirectory() && f.getName().endsWith(SystemUIServer.EXTENSION)) {
                    found.add(f.getName());
                }
            }
        }
        java.util.Collections.sort(found);
        out.println("      in Menu Extras: " + String.join(", ", found));
        failures += check(out, "the indicators are bundles on disk, not code in the bar",
            found.size() >= 5 && found.contains("Clock.menu"));

        // A bundle, with an executable of its own, like anything else that loads.
        Bundle clock = Bundle.read(new File(folder, "Clock.menu"));
        failures += check(out, "a menu extra is a bundle with an executable",
            clock != null && clock.machOExecutable() != null
            && clock.machOExecutable().isFile());
        failures += check(out, "and names the class that draws it",
            clock != null && clock.principalClass().beginsWith(org.fractalmicro.foundation.FMString.of("org.fractalmicro.menuextras.")));

        MainMenu bar = desktop.mainMenu();
        List<String> right = statusNames(bar);
        out.println("      the right of the bar: " + String.join(", ", right));
        failures += check(out, "they are in the bar", right.size() >= 5);

        // The clock is last on the system this imitates, and the bar is read left to right.
        failures += check(out, "the clock is furthest right",
            !right.isEmpty() && right.get(right.size() - 1).matches(".*\\d.*"));

        // The bar itself must no longer know what any of them are.
        failures += check(out, "the bar builds none of them itself",
            !barMentions("clockStatusMenu") && !barMentions("volumeStatusMenu"));

        out.println("      " + (failures == 0
            ? "the front program owns the left, the extras own the right"
            : failures + " failed"));
        return failures;
    }

    /** What is in the bar after the glue, which is everything on the right. */
    private static List<String> statusNames(JMenuBar bar) {
        List<String> out = new ArrayList<>();
        for (int i = bar.getMenuCount() - 1; i >= 0; i--) {
            JMenu m = bar.getMenu(i);
            if (m == null || m.getText() == null || m.getText().isEmpty()) break;
            out.add(0, m.getText());
        }
        // Everything before the glue is the front program's; keep the trailing run only.
        int keep = Math.min(out.size(), 5);
        return out.subList(out.size() - keep, out.size());
    }

    /** Whether the bar's own source still builds an indicator. */
    private static boolean barMentions(String method) {
        java.nio.file.Path p = java.nio.file.Path.of(
            "system/AppKit/src/org/fractalmicro/windowserver/MainMenu.java");
        try {
            return java.nio.file.Files.isReadable(p)
                && java.nio.file.Files.readString(p).contains(method + "()");
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
