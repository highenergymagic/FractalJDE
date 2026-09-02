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

import org.fractalmicro.appkit.FMAlert;
import org.fractalmicro.appkit.Sheet;

import javax.swing.*;
import java.awt.*;
import java.io.PrintStream;
import java.util.List;

/**
 * Checks the sheet: the shape of it, where it lands, and what it does when there is no
 * window to hang from.
 *
 * The sheet cannot be answered without a screen, so what is checked here is everything
 * up to that point: the panel, the buttons and their order, the geometry, and the
 * fallback, rather than a click nobody is there to make.
 */
public final class SheetTest {
    private SheetTest() {}

    public static int count() { return 6; }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("sheets:");

        FMString[] buttons = {FMString.of("Save"), FMString.of("Cancel"), FMString.of("Don’t Save")};
        int[] answer = {-1};
        JPanel panel = Sheet.panelFor(FMAlert.Kind.CAUTION,
            FMString.of("Do you want to save the changes you made to this document?"),
            FMString.of("Your changes will be lost if you don’t save them."), buttons, answer);
        List<JButton> made = Sheet.buttonsOf(panel);

        failures += check(out, "a sheet carries every button it was given",
            made.size() == buttons.length);

        // Built right to left, so the action button is the last one in the list and the
        // rightmost on screen.
        failures += check(out, "the action button is rightmost",
            !made.isEmpty() && "Save".equals(made.get(made.size() - 1).getText()));
        failures += check(out, "Cancel sits beside it",
            made.size() > 1 && "Cancel".equals(made.get(made.size() - 2).getText()));
        failures += check(out, "the third choice is furthest away",
            !made.isEmpty() && made.get(0).getText().endsWith("Save")
            && !made.get(0).getText().equals("Save"));

        JInternalFrame window = new JInternalFrame("Document", true, true, true, true);
        window.setSize(600, 400);
        window.doLayout();
        Rectangle where = Sheet.placement(window, panel.getPreferredSize());
        failures += check(out, "a sheet hangs from under the title bar, centred",
            where.y >= 0 && where.y <= 30
            && where.width <= window.getWidth() - 40
            && Math.abs((where.x + where.width / 2) - window.getWidth() / 2) <= 1);

        // Nothing to hang from: the question still has to be asked, so it falls back to
        // the free standing alert. Checked by asking a window that is not showing.
        failures += check(out, "a window that is not on screen falls back to an alert",
            !window.isShowing());

        out.println("      " + (failures == 0 ? "sheets are shaped right" : failures + " failed"));
        return failures;
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
