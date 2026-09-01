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

import org.fractalmicro.appkit.Alert;

import java.io.PrintStream;

/**
 * Which button Return presses.
 *
 * This exists because of a real one. A dialog offering to log out, to cancel, or to quit
 * this desktop had logging out as its default button, so Return meant logging out wherever
 * the keyboard happened to be, and a session ended because of it.
 *
 * The rule is that an action which cannot be taken back is never the default. What is
 * checked here is that rule, in the one place that decides it, rather than by opening a
 * dialog and pressing buttons in it: a checking run should not put windows on somebody's
 * screen. The dialog itself was driven by hand once, to be sure the rule reaches it.
 */
public final class AlertTest {
    private AlertTest() {}

    public static int count() { return 6; }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("alerts:");

        // Driving a real alert means opening a window and pressing a button in it, which a
        // checking run has no business doing on somebody's screen. So the rule is checked
        // where the rule lives: one place decides which button Return presses.

        FMString[] ordinary = {FMString.of("Keep"), FMString.of("Cancel")};
        failures += check(out, "an ordinary question presses its action button on Return",
            Alert.defaultButtonName(ordinary, 0).sameAs(FMString.of("Keep")));

        FMString[] session = Alert.irreversibleButtons(FMString.of("Log Out"), FMString.of("Quit FractalJDE"));
        failures += check(out, "an irreversible question puts its buttons in the usual order",
            session.length == 3 && session[0].sameAs(FMString.of("Log Out"))
            && session[1].sameAs(FMString.of("Cancel")) && session[2].sameAs(FMString.of("Quit FractalJDE")));

        failures += check(out, "and Cancel is the one Return presses",
            Alert.defaultButtonName(session, Alert.CANCEL).sameAs(FMString.of("Cancel")));

        FMString[] two = Alert.irreversibleButtons(FMString.of("Empty Trash"), null);
        failures += check(out, "a question with two buttons still defaults to Cancel",
            two.length == 2 && Alert.defaultButtonName(two, Alert.CANCEL).sameAs(FMString.of("Cancel")));

        failures += check(out, "Cancel is where the answers say it is",
            Alert.CANCEL == 1
            && session[Alert.CANCEL].sameAs(FMString.of("Cancel"))
            && two[Alert.CANCEL].sameAs(FMString.of("Cancel")));

        // The thing that went wrong: the action was the default and Return did it.
        failures += check(out, "no irreversible question makes its action the default",
            !Alert.defaultButtonName(session, Alert.CANCEL).equals(session[0])
            && !Alert.defaultButtonName(two, Alert.CANCEL).equals(two[0]));

        out.println("      " + (failures == 0 ? "Return presses the safe button"
                                              : failures + " failed"));
        return failures;
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
