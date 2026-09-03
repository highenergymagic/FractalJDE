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

import org.fractalmicro.windowserver.Desktop;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import java.awt.Component;
import java.awt.Container;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks the terms this system uses about itself.
 *
 * There is no Mac here. The machine is a computer, the maker is Fractal Microsystems,
 * the creator code is FMI, and the settings live under org.fractalmicro. This walks
 * everything a person can actually read: every window's accessible name and
 * description, every menu item, every button. It fails if another company's name has
 * crept into it.
 *
 * Notes and comments that cite the guidelines are left alone: a source has to be named
 * to be checked.
 */
public final class WordingTest {
    private WordingTest() {}

    /** Words that must not appear in anything a person reads here. */
    private static final String[] FORBIDDEN = {"Mac", "Apple", "AAPL", "com.apple", "Macintosh"};

    public static int run(Desktop desktop, PrintStream out) {
        int failures = 0;
        out.println();
        out.println("terms:");

        List<String> found = new ArrayList<>();
        walk(desktop, found);
        for (org.fractalmicro.bundle.Bundle bundle : org.fractalmicro.bundle.Bundles.all()) {
            check(bundle.displayName().toString(), "a bundle name", found);
            check(bundle.identifier().toString(), "a bundle identifier", found);
        }
        check(org.fractalmicro.os.SystemProfile.OS_NAME, "the system name", found);
        check(org.fractalmicro.os.SystemProfile.longName(), "the long system name", found);
        check(org.fractalmicro.os.SystemProfile.vendor(), "the company", found);

        if (found.isEmpty()) {
            out.println("ok    nothing on screen names another company");
        } else {
            failures++;
            out.println("FAIL  another company is named on screen:");
            for (String where : found) out.println("      " + where);
        }

        // A checker that cannot fail proves nothing, so prove it can.
        failures += check(out, "the checker spots the word it is looking for",
            containsWord("Search This Mac", "Mac"));
        failures += check(out, "the checker leaves longer words alone",
            !containsWord("Machine", "Mac") && !containsWord("Applications", "Apple"));

        failures += check(out, "settings live under org.fractalmicro",
            org.fractalmicro.os.FMUserDefaults.FINDER.beginsWith(org.fractalmicro.foundation.FMString.of("org.fractalmicro"))
            && org.fractalmicro.os.FMUserDefaults.DOCK.beginsWith(org.fractalmicro.foundation.FMString.of("org.fractalmicro"))
            && org.fractalmicro.os.FMUserDefaults.UNIVERSAL_ACCESS.beginsWith(org.fractalmicro.foundation.FMString.of("org.fractalmicro")));

        failures += check(out, "bundle identifiers start with org.fractalmicro",
            org.fractalmicro.bundle.Bundles.all().stream()
                .allMatch(b -> b.identifier().beginsWith(org.fractalmicro.foundation.FMString.of("org.fractalmicro"))));

        failures += check(out, "the creator code is FMI",
            "FMI ".equals(org.fractalmicro.bundle.Bundle.CREATOR));

        failures += check(out, "the settings files are named for this system",
            org.fractalmicro.os.FMUserDefaults.of(org.fractalmicro.os.FMUserDefaults.FINDER).file()
                .getFileName().toString().startsWith("org.fractalmicro"));

        out.println("      " + (failures == 0 ? "the terms hold" : failures + " failed"));
        return failures;
    }

    private static void walk(Component c, List<String> found) {
        if (c instanceof Accessible) {
            AccessibleContext ctx = ((Accessible) c).getAccessibleContext();
            if (ctx != null) {
                check(ctx.getAccessibleName(), "a name on " + c.getClass().getSimpleName(), found);
                check(ctx.getAccessibleDescription(),
                      "a description on " + c.getClass().getSimpleName(), found);
            }
        }
        if (c instanceof javax.swing.JMenuItem item) {
            check(item.getText(), "the menu item", found);
        }
        if (c instanceof javax.swing.AbstractButton button) {
            check(button.getText(), "the button", found);
        }
        if (c instanceof javax.swing.JLabel label) {
            check(label.getText(), "a label", found);
        }
        if (c instanceof javax.swing.JMenu menu) {
            for (Component child : menu.getMenuComponents()) walk(child, found);
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) walk(child, found);
        }
    }

    private static void check(String text, String where, List<String> found) {
        if (text == null || text.isBlank()) return;
        for (String word : FORBIDDEN) {
            if (containsWord(text, word)) {
                found.add(where + ": " + text.trim());
                return;
            }
        }
    }

    /** Whole words only, so "Machine" and "Applications" are left alone. */
    private static boolean containsWord(String text, String word) {
        int at = text.indexOf(word);
        while (at >= 0) {
            boolean startOk = at == 0 || !Character.isLetterOrDigit(text.charAt(at - 1));
            int end = at + word.length();
            boolean endOk = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (startOk && endOk) return true;
            at = text.indexOf(word, at + 1);
        }
        return false;
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }

    public static int count() {
        return 7;
    }
}
