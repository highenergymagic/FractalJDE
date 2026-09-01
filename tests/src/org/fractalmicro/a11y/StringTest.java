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

import org.fractalmicro.theme.Aqua;

import java.awt.Font;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Checks the wording and the typography against the guidelines rather than against
 * taste. Two rules do most of the work:
 *
 * A button is named for what it does. "OK" is only allowed where an alert is purely
 * telling you something, never where it is asking you to agree to an action, because
 * "OK" says nothing about what you are agreeing to.
 *
 * The fonts are the ones Apple lists: system 13, small 11, views 12, label 10, mini 9.
 */
public final class StringTest {
    private StringTest() {}

    /** The wording this program uses where Finder has wording of its own. */
    private static final Map<String, String[]> ALERTS = new LinkedHashMap<>();

    static {
        // situation -> {message, informative, action button}
        ALERTS.put("empty trash", new String[]{
            "Are you sure you want to permanently erase the items in the Trash?",
            "You can" + '’' + "t undo this action.",
            "Empty Trash"});
        ALERTS.put("shut down", new String[]{
            "Are you sure you want to shut down your computer now?", "", "Shut Down"});
        ALERTS.put("restart", new String[]{
            "Are you sure you want to restart your computer now?", "", "Restart"});
        ALERTS.put("log out", new String[]{
            "Are you sure you want to quit all applications and log out now?", "", "Log Out"});
        ALERTS.put("force quit", new String[]{
            "Do you want to force " + '“' + "name" + '”' + " to quit?",
            "You will lose any unsaved changes.", "Force Quit"});
    }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("wording and type:");

        for (Map.Entry<String, String[]> entry : ALERTS.entrySet()) {
            String[] parts = entry.getValue();
            failures += check(out, entry.getKey() + " asks a question",
                              parts[0].endsWith("?"));
            failures += check(out, entry.getKey() + " has a button named for the action",
                              !parts[2].equalsIgnoreCase("OK")
                              && !parts[2].equalsIgnoreCase("Yes")
                              && !parts[2].equalsIgnoreCase("No"));
            failures += check(out, entry.getKey() + " is written in sentence case",
                              sentenceCase(parts[0]));
        }

        failures += check(out, "the system font is 13 point", Aqua.systemFont().getSize() == 13);
        failures += check(out, "the small system font is 11 point", Aqua.smallFont().getSize() == 11);
        failures += check(out, "lists and tables use 12 point", Aqua.viewFont().getSize() == 12);
        failures += check(out, "toolbar labels use 10 point", Aqua.labelFont().getSize() == 10);
        failures += check(out, "the emphasized system font is bold 13",
                          Aqua.emphasizedSystemFont().getSize() == 13
                          && Aqua.emphasizedSystemFont().getStyle() == Font.BOLD);

        failures += check(out, "alert margins are Apple" + '’' + "s 24 and 20",
                          Aqua.ALERT_SIDE_MARGIN == 24 && Aqua.ALERT_BOTTOM_MARGIN == 20);
        failures += check(out, "controls are spaced 8 apart, groups 12",
                          Aqua.CONTROL_SPACING == 8 && Aqua.GROUP_SPACING == 12);

        out.println("      " + (failures == 0 ? "wording and type match the guidelines"
                                              : failures + " failed"));
        return failures;
    }

    /** First word capitalised, no Title Case Running Through The Sentence. */
    private static boolean sentenceCase(String sentence) {
        String[] words = sentence.split("\\s+");
        if (words.length < 3) return true;
        int capitals = 0;
        for (int i = 1; i < words.length; i++) {
            String word = words[i].replaceAll("[^A-Za-z]", "");
            if (!word.isEmpty() && Character.isUpperCase(word.charAt(0))) capitals++;
        }
        return capitals <= words.length / 3;
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }

    public static int count() {
        return ALERTS.size() * 3 + 7;
    }
}
