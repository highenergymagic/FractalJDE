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

import org.fractalmicro.foundation.FMString;

import org.fractalmicro.appkit.DataDetectors;
import org.fractalmicro.win.SpellChecker;

import java.io.PrintStream;
import java.util.List;

/**
 * Spelling and data detectors, checked against text with known answers in it.
 *
 * The spelling checks only run where there is a dictionary; a machine without one is not
 * a failure, and the run says which happened rather than passing quietly either way.
 */
public final class LanguageTest {
    private LanguageTest() {}

    public static int count() { return 12; }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("spelling and detected data:");
        out.println("      " + SpellChecker.describe());

        /* ------------------------------------------------------------ spelling */
        boolean dictionary = SpellChecker.available();
        if (dictionary) {
            List<SpellChecker.Mistake> mistakes =
                SpellChecker.check("This sentance has a mispelled word.");
            failures += check(out, "two misspellings are found in a sentence with two",
                mistakes.size() == 2);
            failures += check(out, "a misspelling is where the text says it is",
                !mistakes.isEmpty() && mistakes.get(0).start() == 5
                && mistakes.get(0).length() == 8);
            failures += check(out, "the dictionary suggests the right word",
                !mistakes.isEmpty() && mistakes.get(0).suggestions().contains("sentence"));
            failures += check(out, "a sentence with nothing wrong has nothing found",
                SpellChecker.check("This sentence is entirely correct.").isEmpty());
            failures += check(out, "suggestions come back for a word asked about on its own",
                SpellChecker.suggest("recieve").contains("receive"));
        } else {
            out.println("      no dictionary here, so the five spelling checks are skipped");
            failures += 0;
        }

        /* ----------------------------------------------------- data detectors */
        String text = "Write to freya@example.com or see https://www.example.com/page "
                    + "before 12/03/2026. Ring 0161 496 0000, or come to "
                    + "221 Baker Street.";
        org.fractalmicro.foundation.FMArray<DataDetectors.Detection> found = DataDetectors.find(FMString.of(text));
        out.println("      found " + found.count() + " things in the sample");
        for (DataDetectors.Detection d : found) out.println("      " + d.spoken());

        failures += check(out, "an electronic mail address is found",
            has(found, DataDetectors.Kind.MAIL, "freya@example.com"));
        failures += check(out, "a web address is found",
            has(found, DataDetectors.Kind.LINK, "https://www.example.com/page"));
        failures += check(out, "a date is found",
            has(found, DataDetectors.Kind.DATE, "12/03/2026"));
        failures += check(out, "a telephone number is found",
            hasKind(found, DataDetectors.Kind.PHONE));
        failures += check(out, "a street address is found",
            hasKind(found, DataDetectors.Kind.ADDRESS));

        // The address contains a number and the link contains dots; neither may be taken
        // twice, or acting on one would act on part of another.
        failures += check(out, "nothing is detected twice", !overlapping(found));

        boolean addresses = true;
        for (DataDetectors.Detection one : found) {
            if (one.kind() == DataDetectors.Kind.MAIL
                && !DataDetectors.actionTarget(one).beginsWith(FMString.of("mailto:"))) {
                addresses = false;
            }
        }
        failures += check(out, "an address to open is made from what was found", addresses);

        failures += check(out, "plain text with nothing in it detects nothing",
            DataDetectors.find(FMString.of("There is nothing here but words.")).isEmpty());

        out.println("      " + (failures == 0 ? "the words and the things in them hold"
                                              : failures + " failed"));
        return failures;
    }

    private static boolean has(org.fractalmicro.foundation.FMArray<DataDetectors.Detection> found,
                               DataDetectors.Kind kind, String text) {
        for (DataDetectors.Detection one : found) {
            if (one.kind() == kind && one.text().sameAs(FMString.of(text))) return true;
        }
        return false;
    }

    private static boolean hasKind(org.fractalmicro.foundation.FMArray<DataDetectors.Detection> found,
                                   DataDetectors.Kind kind) {
        for (DataDetectors.Detection one : found) {
            if (one.kind() == kind) return true;
        }
        return false;
    }

    private static boolean overlapping(
            org.fractalmicro.foundation.FMArray<DataDetectors.Detection> found) {
        for (int i = 0; i < found.count(); i++) {
            for (int j = i + 1; j < found.count(); j++) {
                DataDetectors.Detection a = found.at(i);
                DataDetectors.Detection b = found.at(j);
                if (a.start() < b.end() && b.start() < a.end()) return true;
            }
        }
        return false;
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
