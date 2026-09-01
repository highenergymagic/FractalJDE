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

import org.fractalmicro.foundation.FMDate;
import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMError;
import org.fractalmicro.foundation.FMMutableDictionary;
import org.fractalmicro.foundation.FMNumber;
import org.fractalmicro.foundation.FMString;

import java.io.PrintStream;

/**
 * The values a program is written in.
 *
 * A platform that hands a program its own text and then makes it reach for the runtime's
 * numbers, its maps and its dates has not finished the job: the program still has two
 * vocabularies and has to know which one it is holding. These are the rest of them.
 *
 * The one worth checking carefully is the number, because it carries what it was made
 * from. A property list holds true and 1 as different things, and a value that read back
 * as one when it was written as the other would quietly change a file every time a program
 * touched it.
 */
public final class ValueTest {
    private ValueTest() {}

    public static int count() { return 12; }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("values:");

        /* ------------------------------------------------ numbers and truth */

        failures += check(out, "a truth value reads back as a truth value",
            FMNumber.of(true).isTruth() && FMNumber.of(true).isTrue()
            && "YES".equals(FMNumber.of(true).toString()));

        failures += check(out, "and a count reads back as a count",
            !FMNumber.of(1L).isTruth() && "1".equals(FMNumber.of(1L).toString()));

        failures += check(out, "but one is worth what the other is worth",
            FMNumber.of(true).equals(FMNumber.of(1L))
            && FMNumber.of(2.0).equals(FMNumber.of(2L)));

        failures += check(out, "text that is a number becomes one",
            FMNumber.parsing(FMString.of("42")).asWhole() == 42
            && FMNumber.parsing(FMString.of("YES")).isTrue()
            && FMNumber.parsing(FMString.of("half")) == null);

        /* -------------------------------------------------------- dictionaries */

        FMMutableDictionary building = FMMutableDictionary.empty();
        building.set(FMString.of("name"), FMString.of("TextEdit"));
        building.set(FMString.of("windows"), 3L);
        building.set(FMString.of("hidden"), false);
        FMDictionary finished = building.asDictionary();

        failures += check(out, "a dictionary answers in the types it was given",
            finished.string(FMString.of("name")).sameAs(FMString.of("TextEdit"))
            && finished.whole(FMString.of("windows"), 0) == 3
            && !finished.truth(FMString.of("hidden"), true));

        failures += check(out, "and keeps the order it was built in",
            FMString.join(FMString.of(","), finished.keys())
                    .sameAs(FMString.of("name,windows,hidden")));

        building.set(FMString.of("windows"), 4L);
        failures += check(out, "and what was handed over does not change afterwards",
            finished.whole(FMString.of("windows"), 0) == 3);

        failures += check(out, "a name with nothing under it answers with the fallback",
            finished.string(FMString.of("nothing"), FMString.of("none"))
                    .sameAs(FMString.of("none")));

        /* --------------------------------------------------- dates and failures */

        FMDate then = FMDate.sinceReference(0);
        failures += check(out, "a date counts from the first instant of 2001",
            then.epochMilliseconds() == 978_307_200_000L
            && FMDate.now().isAfter(then));

        FMError failed = FMError.of(FMError.POSIX_DOMAIN, 2,
                                    FMString.of("The file could not be opened."))
                                .withReason(FMString.of("There is no such file."));
        failures += check(out, "a failure is a value that says what to tell a person",
            failed.description().contains(FMString.of("could not be opened"))
            && failed.code() == 2
            && !failed.reason().isEmpty());

        /* ------------------------------------------------- what equality means */

        // Worth pinning, because getting it wrong is silent. A piece of this system's text
        // is not one of the runtime's, and asking a String whether it equals an FMString
        // compiles and answers no, every time. Three places in this system had that bug.
        failures += check(out, "this system's text is not the runtime's, and says so",
            !FMString.of("Cancel").equals("Cancel")
            && FMString.of("Cancel").equals(FMString.of("Cancel")));

        // Names on a file system do not care about case, and neither does sameAs. Equality
        // does, because a value put in a dictionary has to come back out under the key it
        // went in under.
        failures += check(out, "comparing names ignores case, comparing values does not",
            FMString.of("Cancel").sameAs(FMString.of("cancel"))
            && !FMString.of("Cancel").equals(FMString.of("cancel"))
            && FMString.of("a").hashCode() == FMString.of("a").hashCode());

        out.println("      " + (failures == 0
            ? "a program has one vocabulary" : failures + " failed"));
        return failures;
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
