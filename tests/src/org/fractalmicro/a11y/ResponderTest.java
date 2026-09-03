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

import org.fractalmicro.appkit.FMResponder;
import org.fractalmicro.appkit.FMResponderChain;
import org.fractalmicro.appkit.FMTextField;
import org.fractalmicro.foundation.FMString;

import javax.swing.JPanel;
import java.io.PrintStream;
import java.util.List;

/**
 * Who a command is offered to, and in what order.
 *
 * Whatever has the keyboard, then out through what it sits inside, then the program. The
 * first that can do it does.
 *
 * The case worth checking is Copy: the file manager copies files, a text field copies
 * text, both answer to the name, and which happens depends only on where the keyboard is.
 *
 * Walked from a named starting point rather than the real keyboard, since giving something
 * the keyboard means showing a window on a machine somebody is using.
 */
public final class ResponderTest {
    private ResponderTest() {}

    public static int count() { return 12; }

    private static final FMString COPY = FMString.of("copy");
    private static final FMString PASTE = FMString.of("paste");
    private static final FMString NEW_WINDOW = FMString.of("newWindow");

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("who does a command:");

        // The program, at the end of every chain, which does two things and counts.
        int[] copiedFiles = {0};
        FMResponder program = new FMResponder() {
            @Override public boolean canPerform(FMString action) {
                return action.sameAs(COPY) || action.sameAs(NEW_WINDOW);
            }
            @Override public boolean perform(FMString action) {
                if (action.sameAs(COPY)) copiedFiles[0]++;
                return true;
            }
        };

        FMTextField field = new FMTextField(FMString.of("some words"));
        JPanel around = new JPanel();
        around.add(field);

        /* ------------------------------------------------------- the chain itself */

        List<FMResponder> chain = FMResponderChain.chain(field, program);
        failures += check(out, "the chain runs from the keyboard out to the program",
            chain.size() >= 2 && chain.get(0) == field
            && chain.get(chain.size() - 1) == program);

        failures += check(out, "and the program alone is a chain of one",
            FMResponderChain.chain(null, program).size() == 1);

        /* -------------------------------------------- who copies, and what they copy */

        field.setSelectionStart(0);
        field.setSelectionEnd(4);
        failures += check(out, "a field with something selected can copy",
            field.canPerform(COPY));

        int before = copiedFiles[0];
        boolean done = FMResponderChain.sendAction(field, COPY, program);
        failures += check(out, "and Copy is the field's, not the program's",
            done && copiedFiles[0] == before);

        // The half people notice. Nothing selected in the field means the field cannot
        // copy, so the offer carries on to the program, which is what a Mac does: with the
        // cursor in an empty search field, Copy still copies the files that are selected.
        field.setSelectionStart(0);
        field.setSelectionEnd(0);
        failures += check(out, "with nothing selected in it, the field cannot",
            !field.canPerform(COPY));

        before = copiedFiles[0];
        done = FMResponderChain.sendAction(field, COPY, program);
        failures += check(out, "so Copy carries on to the program behind",
            done && copiedFiles[0] == before + 1);

        /* ------------------------------------------ what nobody in the chain can do */

        failures += check(out, "a command only the program has still reaches it",
            FMResponderChain.canPerform(field, NEW_WINDOW, program));

        failures += check(out, "and one nobody has is refused",
            !FMResponderChain.canPerform(field, FMString.of("fly"), program)
            && !FMResponderChain.sendAction(field, FMString.of("fly"), program));

        // Paste is about the board rather than the selection, so a field that is not
        // editable cannot do it however much is on the board.
        field.setEditable(false);
        failures += check(out, "a field that cannot be typed in cannot paste",
            !field.canPerform(PASTE));

        /* --------------------------------------------- and the board it goes through */

        // A checking run has a board of its own, so this reads back what it just wrote
        // without going near the one the person is using.
        failures += check(out, "a checking run has a board of its own",
            !org.fractalmicro.appkit.FMPasteboard.isShared());

        field.setEditable(true);
        field.setText("some words");
        field.setSelectionStart(0);
        field.setSelectionEnd(4);
        FMResponderChain.sendAction(field, COPY, program);
        failures += check(out, "copying in a field puts the selection on the board",
            org.fractalmicro.appkit.FMPasteboard.general().string()
                .sameAs(FMString.of("some")));

        field.selectAll();
        FMResponderChain.sendAction(field, PASTE, program);
        failures += check(out, "and pasting takes it off again",
            "some".equals(field.getText()));

        out.println("      " + (failures == 0
            ? "a command goes to whoever has the keyboard"
            : failures + " failed"));
        return failures;
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
