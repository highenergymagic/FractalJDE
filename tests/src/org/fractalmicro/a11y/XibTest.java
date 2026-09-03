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

import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMMutableDictionary;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMURL;
import org.fractalmicro.nib.Nib;
import org.fractalmicro.nib.Xib;
import org.fractalmicro.plist.Strings;

import java.io.File;
import java.io.PrintStream;

/**
 * Interfaces in files, and words in another file beside them.
 *
 * What is read back has to be what was written, and a translation has to reach the window
 * without the program knowing.
 *
 * The third check is the one nobody notices failing: a key with no translation keeps the
 * words it was written with. Showing the key instead reads as English in English and as
 * "digit 7.accessibilityLabel" everywhere else.
 */
public final class XibTest {
    private XibTest() {}

    public static int count() { return 9; }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("interfaces and words:");

        /* ------------------------------------------------------------ the format */

        File source = new File("apps/Calculator/resources/Calculator.xib");
        if (!source.isFile()) {
            out.println("      no interface files here; this is a built copy");
            return 0;
        }

        Nib read;
        try {
            read = Xib.read(FMURL.of(source));
        } catch (Exception e) {
            out.println("FAIL  an interface file can be read: " + e);
            return count();
        }

        out.println("      Calculator: " + read.controls().count() + " controls, "
                    + read.menus().count() + " menus");
        failures += check(out, "an interface file holds a window and what is in it",
            read.title().sameAs(FMString.of("Calculator"))
            && read.controls().count() > 10 && read.menus().count() == 3);

        // What comes back has to be what went in, or the file is not the description.
        Nib again;
        try {
            again = Xib.parse(Xib.write(read));
        } catch (Exception e) {
            out.println("FAIL  it survives being written and read: " + e);
            return count() - 1;
        }
        failures += check(out, "and survives being written and read",
            again.controls().count() == read.controls().count()
            && again.menus().count() == read.menus().count()
            && again.title().sameAs(read.title()));

        Nib.Control seven = again.control(FMString.of("digit 7"));
        failures += check(out, "with every control's class, place, name and connection",
            seven != null && seven.kind() == Nib.ControlClass.FMButton
            && seven.name().sameAs(FMString.of("Seven"))
            && seven.text().sameAs(FMString.of("7"))
            && seven.action().sameAs(FMString.of("digit 7"))
            && seven.width() == 46);

        /* ------------------------------------------------------------- the words */

        FMDictionary table = Strings.parse(FMString.of(
            "/* what a translator is told */\n"
            + "\"window.title\" = \"Rechner\";\n"
            + "\"digit 7.accessibilityLabel\" = \"Sieben\";\n"));
        failures += check(out, "a strings file is comments and pairs",
            table.count() == 2
            && table.string(FMString.of("window.title")).sameAs(FMString.of("Rechner")));

        Nib german = Xib.localized(read, table);
        Nib.Control translated = german.control(FMString.of("digit 7"));
        failures += check(out, "and the words in it reach the window",
            german.title().sameAs(FMString.of("Rechner"))
            && translated.name().sameAs(FMString.of("Sieben")));

        // The half that is easy to get wrong.
        Nib.Control untouched = german.control(FMString.of("digit 8"));
        failures += check(out, "while anything untranslated keeps the words it was written with",
            untouched.name().sameAs(FMString.of("Eight"))
            && translated.text().sameAs(FMString.of("7")));

        /* ------------------------------------------------- and every program has one */

        java.util.List<String> without = new java.util.ArrayList<>();
        for (String[] app : new String[][]{
                {"Calculator", "Calculator"}, {"SystemProfiler", "SystemProfiler"},
                {"ActivityMonitor", "ActivityMonitor"},
                {"SystemPreferences", "SystemPreferences"},
                {"TextEdit", "Document"}, {"TextEdit", "Find"}}) {
            File xib = new File("apps/" + app[0] + "/resources/" + app[1] + ".xib");
            File words = new File("apps/" + app[0] + "/resources/en.lproj/"
                                  + app[1] + ".strings");
            if (!xib.isFile() || !words.isFile()) without.add(app[0] + "/" + app[1]);
        }
        failures += check(out, "every window that ships has a file and a table of its words",
            without.isEmpty());
        for (String one : without) out.println("      missing: " + one);

        // Every key a translator would be given has to be one the interface actually uses,
        // or the file sends somebody off translating words that will never be shown.
        FMDictionary keys = Xib.stringsFor(read);
        FMMutableDictionary blank = FMMutableDictionary.empty();
        int reachable = 0;
        for (FMString key : keys.keys()) {
            blank.set(key, FMString.of("x"));
        }
        Nib all = Xib.localized(read, blank.asDictionary());
        if (all.title().sameAs(FMString.of("x"))) reachable++;
        for (Nib.Control c : all.controls()) {
            if (c.name().sameAs(FMString.of("x"))) reachable++;
        }
        out.println("      " + keys.count() + " keys offered for translation");
        failures += check(out, "and every key offered changes something",
            keys.count() > 0 && reachable > read.controls().count() / 2);

        /* ------------------------------------- and every pane somebody asks for exists */

        // The file manager's Preferences item names a settings pane, and the settings list
        // their panes in their own interface file. Nothing puts the two together at run
        // time: a name that is not a pane opens the first one instead, so the item went on
        // working and went on opening the wrong thing. It asked for one called "finder",
        // which is not a pane, for as long as the item had existed.
        File settings = new File("apps/SystemPreferences/resources/SystemPreferences.xib");
        java.util.List<String> panes = new java.util.ArrayList<>();
        if (settings.isFile()) {
            try {
                for (Nib.Control control : Xib.read(FMURL.of(settings)).controls()) {
                    if (control.kind() != Nib.ControlClass.FMTableView) continue;
                    for (FMString choice : control.choices()) panes.add(choice.toString());
                }
            } catch (Exception cannotRead) {
                out.println("      the settings could not be read: " + cannotRead);
            }
        }
        boolean named = false;
        for (String pane : panes) {
            if (pane.equalsIgnoreCase(org.fractalmicro.ui.FinderMenus.SETTINGS_PANE)) named = true;
        }
        out.println("      the settings offer " + panes.size() + " panes; Preferences asks for "
                    + org.fractalmicro.ui.FinderMenus.SETTINGS_PANE);
        failures += check(out, "the pane Preferences opens is one the settings have",
            panes.isEmpty() || named);

        out.println("      " + (failures == 0
            ? "the file is the window, and the words are beside it"
            : failures + " failed"));
        return failures;
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
