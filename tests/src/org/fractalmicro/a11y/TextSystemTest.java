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
import org.fractalmicro.appkit.FMText;
import org.fractalmicro.appkit.FMTextField;
import org.fractalmicro.appkit.Services;
import org.fractalmicro.appkit.TextDefaults;
import org.fractalmicro.windowserver.Desktop;
import org.fractalmicro.ui.NameEditor;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.Rectangle;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.List;

/**
 * The text system, checked where it matters: in an ordinary field rather than in the
 * editor it was written for.
 *
 * A plain field in a dialog gets spelling, substitutions, detected things and the
 * services menu, or the claim that this is a text system is not true. So the checks are
 * done on a field made the way any dialog makes one, and the walk over the program's own
 * windows makes sure none of them are still using a field from underneath.
 */
public final class TextSystemTest {
    private TextSystemTest() {}

    public static int count() { return 15; }

    public static int run(Desktop desktop, PrintStream out) {
        int failures = 0;
        out.println();
        out.println("the text system:");

        boolean quotesWere = TextDefaults.smartQuotes();
        boolean detectWas = TextDefaults.detectData();
        try {
            /* -------------------------------------------------- an ordinary field */
            FMTextField field = new FMTextField(24);
            FMText.Support support = field.textSupport();
            failures += check(out, "a field arrives with the text system in it",
                support != null && FMText.supportOf(field) == support);

            TextDefaults.setSmartQuotes(true);
            field.setText("");
            field.replaceSelection("she said \"hello\"");
            failures += check(out, "smart quotes apply in a plain field",
                field.getText().contains("“") && field.getText().contains("”"));

            TextDefaults.setSmartQuotes(false);
            field.setText("");
            field.replaceSelection("she said \"hello\"");
            failures += check(out, "and stop when the setting is off",
                !field.getText().contains("“"));

            TextDefaults.setDetectData(true);
            support.setDetectingOn(true);
            field.setText("write to freya@example.com about it");
            support.refresh();
            List<DataDetectors.Detection> found = support.detections();
            failures += check(out, "a field finds the things in its own text",
                found.size() == 1 && found.get(0).kind() == DataDetectors.Kind.MAIL);

            support.setDetectingOn(false);
            failures += check(out, "and stops looking when told to",
                support.detections().isEmpty());

            /* ------------------------------------------------------------ spelling */
            field.setText("a mispelled word");
            support.setSpellingOn(true);
            support.refresh();
            boolean spelling = !org.fractalmicro.win.SpellChecker.available()
                || !support.spelling().mistakes().isEmpty();
            failures += check(out, "a field checks its own spelling", spelling);

            // The same check started from the event thread, which is where typing starts
            // it. The host's checker answers on a worker and returns nothing at all on the
            // event thread, so a check that ran there came back empty every time: nothing
            // failed, and no mistake was ever underlined. Asking only from a test thread
            // is what let that stand.
            support.spelling().checkNow();
            final int[] typed = new int[1];
            javax.swing.SwingUtilities.invokeAndWait(() -> support.spelling().checkNow());
            for (int i = 0; i < 40 && typed[0] == 0; i++) {
                javax.swing.SwingUtilities.invokeAndWait(() ->
                    typed[0] = support.spelling().mistakes().size());
                if (typed[0] == 0) Thread.sleep(50);
            }
            failures += check(out, "and finds the same mistakes when typing starts the check",
                !org.fractalmicro.win.SpellChecker.available() || typed[0] > 0);

            /* ------------------------------------------------------------ services */
            field.setText("some words to work on");
            field.selectAll();
            org.fractalmicro.foundation.FMArray<Services.Service> services = Services.forSelection(FMString.describing(field.getSelectedText()));
            failures += check(out, "services are offered for what is selected",
                !services.isEmpty());

            Services.Service upper = null;
            for (Services.Service one : services) {
                if (one.name().sameAs(FMString.of("Make Upper Case"))) upper = one;
            }
            if (upper != null) Services.run(upper, field);
            failures += check(out, "a service that changes the text writes it back",
                "SOME WORDS TO WORK ON".equals(field.getText()));

            failures += check(out, "nothing is offered for nothing selected",
                Services.forSelection(FMString.describing("")).isEmpty());

            JPopupMenu menu = FMText.menuFor(field, support, 0);
            failures += check(out, "the menu on a piece of text carries the services",
                hasMenu(menu, "Services"));

            /* -------------------------------------- every field in the program */
            List<String> plain = new java.util.ArrayList<>();
            walk(desktop, plain);
            failures += check(out, "every text field in this program is one of ours",
                plain.isEmpty());
            for (String where : plain) out.println("      plain field in " + where);

            /* ------------------------------------------------- renaming in place */
            File folder = Files.createTempDirectory("fractal-rename").toFile();
            File file = new File(folder, "Before.txt");
            Files.writeString(file.toPath(), "x");
            JPanel over = new JPanel(null);
            over.setSize(300, 100);
            org.fractalmicro.fs.Node node = org.fractalmicro.fs.FS.node(file);

            NameEditor.begin(over, new Rectangle(10, 10, 120, 18), node, null);
            JTextComponent editor = fieldIn(over);
            failures += check(out, "renaming puts a field over the item, not a dialog",
                NameEditor.isEditing() && editor != null
                && "Before.txt".equals(editor.getText())
                && "Name".equals(editor.getAccessibleContext().getAccessibleName()));

            failures += check(out, "the part before the extension is selected to be typed over",
                editor != null && "Before".equals(editor.getSelectedText()));

            NameEditor.cancel();
            failures += check(out, "leaving the field takes it away again",
                !NameEditor.isEditing() && fieldIn(over) == null && file.exists());

            file.delete();
            folder.delete();
        } catch (Exception e) {
            out.println("FAIL  the checks ran to the end: " + e);
            failures++;
        } finally {
            TextDefaults.setSmartQuotes(quotesWere);
            TextDefaults.setDetectData(detectWas);
            NameEditor.cancel();
        }

        out.println("      " + (failures == 0 ? "text is text wherever it is"
                                              : failures + " failed"));
        return failures;
    }

    /** Any text control in the program that is not one of this system's own. */
    private static void walk(java.awt.Component c, List<String> plain) {
        if (c instanceof JTextComponent text && text.isEditable()
                && FMText.supportOf(text) == null
                && !(c instanceof javax.swing.text.JTextComponent
                     && c.getClass().getName().startsWith("javax.swing.plaf"))) {
            plain.add(c.getClass().getSimpleName() + " named "
                      + text.getAccessibleContext().getAccessibleName());
        }
        if (c instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) walk(child, plain);
        }
    }

    private static JTextComponent fieldIn(JComponent over) {
        for (java.awt.Component c : over.getComponents()) {
            if (c instanceof JTextComponent text) return text;
        }
        return null;
    }

    private static boolean hasMenu(JPopupMenu menu, String name) {
        for (java.awt.Component c : menu.getComponents()) {
            if (c instanceof JMenu m && name.equals(m.getText())) return true;
        }
        return false;
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
