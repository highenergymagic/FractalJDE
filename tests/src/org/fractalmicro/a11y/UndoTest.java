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
import org.fractalmicro.foundation.FMUndoManager;
import org.fractalmicro.fs.FS;
import org.fractalmicro.fs.Node;
import org.fractalmicro.ui.Finder;
import org.fractalmicro.ui.FinderMenus;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The way back, and the menu that says so.
 *
 * Two ends of one thing: that an operation registers the way back from itself, and that
 * the menu knows whether the item can be used and what it is about to undo.
 *
 * The name goes in with the action rather than being worked out by whoever draws the
 * menu, or "Undo Rename" is impossible for every program at once.
 */
public final class UndoTest {
    private UndoTest() {}

    public static int count() { return 12; }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("the way back:");

        failures += checkManager(out);
        failures += checkFinder(out);

        out.println("      " + (failures == 0
            ? "what was done can be undone, and the menu says which"
            : failures + " failed"));
        return failures;
    }

    /* --------------------------------------------------------------- the manager */

    private static int checkManager(PrintStream out) {
        int failures = 0;
        FMUndoManager undo = new FMUndoManager();
        List<String> happened = new ArrayList<>();

        failures += check(out, "a manager with nothing in it offers nothing",
            !undo.canUndo() && !undo.canRedo()
            && undo.undoActionName().isEmpty() && !undo.undo());

        undo.registerUndo(FMString.of("Rename"), () -> happened.add("undid rename"));
        failures += check(out, "registering a way back offers it, by name",
            undo.canUndo() && undo.undoActionName().sameAs(FMString.of("Rename")));

        // What an undo registers while it runs is the way forward, which is how one
        // method serves both directions.
        FMUndoManager both = new FMUndoManager();
        int[] value = {0};
        Runnable[] set = new Runnable[2];
        set[0] = () -> { value[0] = 1; both.registerUndo(FMString.of("Set"), set[1]); };
        set[1] = () -> { value[0] = 2; both.registerUndo(FMString.of("Set"), set[0]); };
        both.registerUndo(FMString.of("Set"), set[0]);
        boolean wentBack = both.undo();
        failures += check(out, "undoing runs the way back and takes it off the stack",
            wentBack && value[0] == 1 && !both.canUndo());
        failures += check(out, "and what it registered on the way is the way forward",
            both.canRedo() && both.redoActionName().sameAs(FMString.of("Set")));
        boolean wentOn = both.redo();
        failures += check(out, "which can be taken, and puts the undo back",
            wentOn && value[0] == 2 && both.canUndo() && !both.canRedo());

        // A new thing done makes the forward history untrue.
        both.registerUndo(FMString.of("Something else"), () -> { });
        failures += check(out, "something new done forgets what could have been redone",
            !both.canRedo() && both.canUndo());

        FMUndoManager deep = new FMUndoManager();
        for (int i = 0; i < FMUndoManager.LEVELS_OF_UNDO + 8; i++) {
            deep.registerUndo(FMString.of("Step"), () -> { });
        }
        int kept = 0;
        while (deep.undo()) kept++;
        failures += check(out, "and it keeps a bounded number of steps",
            kept == FMUndoManager.LEVELS_OF_UNDO);

        undo.removeAllActions();
        failures += check(out, "closing what it belonged to forgets everything",
            !undo.canUndo() && !undo.canRedo() && happened.isEmpty());
        return failures;
    }

    /* ------------------------------------------------------- the Finder, and its menu */

    private static int checkFinder(PrintStream out) {
        int failures = 0;
        Path folder;
        try {
            folder = Files.createTempDirectory("fractal-undo-check");
        } catch (Exception e) {
            out.println("FAIL  a folder to work in: " + e);
            return 3;
        }

        try {
            Finder.undoManager().removeAllActions();

            // Something a person would do, done the way a menu item does it. Duplicate
            // rather than New Folder, because New Folder asks for a name straight
            // afterwards and a check has nobody to answer.
            File original = folder.resolve("Report.txt").toFile();
            Files.writeString(original.toPath(), "a report");
            String[] before = folder.toFile().list();

            Finder.duplicate(List.of(FS.node(original)));
            String[] after = folder.toFile().list();
            failures += check(out, "duplicating registers the way back from it",
                after.length == before.length + 1
                && Finder.undoManager().canUndo()
                && !Finder.undoManager().undoActionName().isEmpty());

            Finder.undoManager().undo();
            failures += check(out, "and taking it puts the folder as it was",
                folder.toFile().list().length == before.length && original.exists());

            // What the menu asks, of the same commands, with nothing selected.
            FinderMenus menus = FinderMenus.forChecking();
            failures += check(out, "a menu asks what can be done, and is told no",
                !menus.canPerform(FMString.of("getInfo"))
                && !menus.canPerform(FMString.of("moveToTrash"))
                && !menus.canPerform(FMString.of("duplicate"))
                && !menus.canPerform(FMString.of("cut"))
                && menus.canPerform(FMString.of("newWindow")));

            // The Label submenu is built at run time and so is not in the interface file
            // with the rest, which is exactly why it was the one still offering to colour
            // nothing. It answers the same question now.
            failures += check(out, "and the same about labelling, which has its own menu",
                !menus.canPerform(FMString.of("label")));
        } catch (Exception e) {
            out.println("FAIL  the Finder registers a way back: " + e);
            failures++;
        } finally {
            deleteTree(folder.toFile());
        }
        return failures;
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
