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

import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.nib.Nib;
import org.fractalmicro.textedit.Settings;
import org.fractalmicro.textedit.TextEdit;

import java.io.PrintStream;

/**
 * TextEdit, now that it is somewhere else.
 *
 * The document lives in a control the window server owns, and every editing command is a
 * name sent to it: cut, bold, centre. That arrangement has one failure that the old one
 * could not have. A name that no text view knows is not a compile error and not a crash.
 * It is a menu item that does nothing, quietly, for as long as nobody tries it.
 *
 * So that is what is checked here: every command this program can send names an action a
 * styled text view actually has. The rest is the description, which has to hold a document
 * and offer the menus, and the list of what was opened lately, which is the one piece of
 * state this program keeps for itself.
 */
public final class TextEditTest {
    private TextEditTest() {}

    public static int count() { return 6; }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("TextEdit:");

        /* ---------------------------------------------- the commands it can send */

        // A styled text pane, made here only to be asked what it knows how to do.
        javax.swing.JTextPane pane = new javax.swing.JTextPane();
        pane.setEditorKit(new javax.swing.text.rtf.RTFEditorKit());

        FMArray<TextEdit.Command> commands = TextEdit.commands();
        java.util.List<String> unknown = new java.util.ArrayList<>();
        for (int i = 0; i < commands.count(); i++) {
            TextEdit.Command one = commands.at(i);
            String action = one.action().toString();
            // Undo and redo are the program's own: a text view does not keep a history,
            // and what does is put on the control when the window is built.
            if ("undo".equals(action) || "redo".equals(action)) continue;
            if (pane.getActionMap().get(action) == null) {
                unknown.add(one.title() + " sends " + action);
            }
        }
        out.println("      " + commands.count() + " editing commands");
        failures += check(out, "every command names something a text view can do",
                          unknown.isEmpty());
        for (String one : unknown) out.println("      " + one);

        failures += check(out, "and each one has a name a person would recognise",
            allNamed(commands));

        /* --------------------------------------------------------- the description */

        Nib described;
        try {
            described = interfaceNamed("TextEdit", "Document");
        } catch (java.io.IOException e) {
            out.println("      no interface files here; this is a built copy");
            return 0;
        }
        failures += check(out, "the window holds a document that can be styled",
            described.control(FMString.of("body")) != null
            && described.control(FMString.of("body")).kind()
               == Nib.ControlClass.FMRichText);

        failures += check(out, "and offers the menus a document needs",
            hasMenu(described, "File") && hasMenu(described, "Edit")
            && hasMenu(described, "Find") && hasMenu(described, "Format"));

        /* ------------------------------------------------------------- what it keeps */

        java.io.File made = new java.io.File(
            System.getProperty("java.io.tmpdir"), "fractal-checking.txt");
        try {
            java.nio.file.Files.writeString(made.toPath(), "words");
            Settings.rememberRecent(org.fractalmicro.foundation.FMURL.of(made));
            failures += check(out, "a document that was opened is remembered",
                contains(Settings.recents(), made));

            Settings.rememberRecent(org.fractalmicro.foundation.FMURL.of(made));
            failures += check(out, "and remembering it twice does not list it twice",
                countOf(Settings.recents(), made) == 1);
        } catch (Exception e) {
            out.println("FAIL  a document that was opened is remembered: " + e);
            failures += 2;
        } finally {
            made.delete();
        }

        out.println("      " + (failures == 0
            ? "the commands reach the view, and the view knows them"
            : failures + " failed"));
        return failures;
    }

    private static boolean allNamed(FMArray<TextEdit.Command> commands) {
        for (int i = 0; i < commands.count(); i++) {
            if (commands.at(i).title().isBlank()) return false;
        }
        return true;
    }

    private static boolean hasMenu(Nib described, String title) {
        for (Nib.Menu menu : described.menus()) {
            if (menu.title().sameAs(FMString.of(title))) return true;
        }
        return false;
    }

    private static boolean contains(FMArray<org.fractalmicro.foundation.FMURL> where,
                                    java.io.File what) {
        return countOf(where, what) > 0;
    }

    private static int countOf(FMArray<org.fractalmicro.foundation.FMURL> where, java.io.File what) {
        int found = 0;
        for (org.fractalmicro.foundation.FMURL one : where) {
            if (one.asFile().getAbsolutePath().equals(what.getAbsolutePath())) found++;
        }
        return found;
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
    /**
     * An interface file as it ships, read from where a program's resources are written.
     *
     * Reading the file rather than asking a program to describe itself is the point: what
     * a program shows is what is in the file, and a check that asked the program would
     * agree with itself no matter what the file said.
     */
    static Nib interfaceNamed(String app, String name) throws java.io.IOException {
        java.io.File at = new java.io.File("apps/" + app + "/resources/" + name + ".xib");
        if (!at.isFile()) {
            at = new java.io.File("../apps/" + app + "/resources/" + name + ".xib");
        }
        if (!at.isFile()) throw new java.io.IOException("no interface file for " + name);
        return org.fractalmicro.nib.Xib.read(org.fractalmicro.foundation.FMURL.of(at));
    }

}
