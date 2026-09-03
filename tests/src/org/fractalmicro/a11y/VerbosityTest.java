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
 * What gets read out, and what should not.
 *
 * A description is read in full every time somebody lands on the control. So it may say
 * what something is or what state it is in, "selected", "Red label", "54 items", and may
 * not name a key or tell anybody to press one.
 *
 * Keys belong in Help, in the shortcuts window, and beside the commands in the menus. The
 * rule is checked rather than remembered: it was broken three times.
 */
public final class VerbosityTest {
    private VerbosityTest() {}

    public static int count() { return 4; }

    /** Words that mean a description has turned into a lesson. */
    private static final String[] KEY_WORDS = {
        "arrow key", "press ", "return keeps", "escape leaves", "up and down",
        "command ", "control f", "type ", "tab to", "keys move", "move between",
        "moves between", "to open the", "shift tab", " key "
    };

    /** How long a description may reasonably be before it is really a paragraph. */
    private static final int TOO_LONG = 80;

    public static int run(Desktop desktop, PrintStream out) {
        int failures = 0;
        out.println();
        out.println("what is read out:");

        List<String> lessons = new ArrayList<>();
        List<String> essays = new ArrayList<>();
        int described = walk(desktop, lessons, essays);

        out.println("      " + described + " things carry a description");
        for (String one : lessons) out.println("      instructions: " + one);
        for (String one : essays) out.println("      too long: " + one);

        failures += check(out, "no description tells anybody which key to press",
            lessons.isEmpty());
        failures += check(out, "no description is a paragraph", essays.isEmpty());

        // The rule itself has to be able to fail, or it proves nothing.
        failures += check(out, "the rule catches a description that is a lesson",
            isLesson("Editing the name of X. Return keeps it, Escape leaves it as it was.")
            && isLesson("Items as icons. Arrow keys move, Command O opens the selection.")
            && isLesson("Devices, places and searches. Up and down move between them."));

        failures += check(out, "and leaves a description that says what something is",
            !isLesson("selected Microsoft Word document, Red label")
            && !isLesson("This document is locked")
            && !isLesson("54 items")
            && !isLesson("selected icon"));

        out.println("      " + (failures == 0 ? "descriptions say what things are, not how to use them"
                                              : failures + " failed"));
        return failures;
    }

    /** Whether a description has stopped describing and started instructing. */
    static boolean isLesson(String description) {
        if (description == null) return false;
        String lower = description.toLowerCase(java.util.Locale.ROOT);
        for (String word : KEY_WORDS) {
            if (lower.contains(word)) return true;
        }
        return false;
    }

    private static int walk(Component c, List<String> lessons, List<String> essays) {
        int described = 0;
        if (c instanceof Accessible accessible) {
            AccessibleContext context = accessible.getAccessibleContext();
            if (context != null) {
                String description = context.getAccessibleDescription();
                if (description != null && !description.isBlank()) {
                    described++;
                    String where = c.getClass().getSimpleName() + " ["
                                 + context.getAccessibleName() + "]: " + description;
                    // An alert says its own message in its description, which is the thing
                    // the alert is for rather than chrome around a control.
                    boolean isAlert = c.getClass().getName().contains("Sheet")
                                   || c.getClass().getName().contains("FMAlert");
                    if (!isAlert && isLesson(description)) lessons.add(where);
                    if (!isAlert && description.length() > TOO_LONG) essays.add(where);
                }
            }
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                described += walk(child, lessons, essays);
            }
        }
        return described;
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
