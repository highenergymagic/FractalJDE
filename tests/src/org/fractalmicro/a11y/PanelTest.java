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

import org.fractalmicro.appkit.FMBrowser;
import org.fractalmicro.appkit.FMOpenPanel;
import org.fractalmicro.appkit.FMSavePanel;
import org.fractalmicro.foundation.FMMutableArray;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMURL;

import javax.accessibility.AccessibleContext;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The panel every program saves through.
 *
 * Saving is the thing a person does most often that they cannot undo by pressing something,
 * so the panel it goes through is worth checking rather than assuming. What is checked here
 * is what the panel is for: that it browses, that the route through the folders is on the
 * screen rather than in somebody's head, and that a name typed without an extension gets the
 * one the format implies.
 *
 * The panel is not shown. A modal panel put up during a check is a check that never
 * finishes, so what is built is the same components going through the same layout, asked
 * questions directly.
 */
public final class PanelTest {
    private PanelTest() {}

    public static int count() { return 11; }

    public static int run(PrintStream out) {
        out.println();
        out.println("the save panel:");
        int failures = 0;

        Path work;
        try {
            work = Files.createTempDirectory("fractal-panel");
        } catch (IOException noTemp) {
            out.println("FAIL  somewhere to work: " + noTemp);
            return count();
        }

        try {
            /* --------------------------------------------------- what it is asked */

            FMMutableArray<FMString> types = FMMutableArray.empty();
            types.add(FMString.of("rtf"));
            FMSavePanel panel = FMSavePanel.savePanel()
                .nameFieldStringValue(FMString.of("Untitled"))
                .allowedFileTypes(types.asArray());

            failures += check(out, "the panel is asked in the words NSSavePanel uses",
                panel.nameFieldStringValue().sameAs(FMString.of("Untitled"))
                && panel.allowedFileTypes().count() == 1
                && panel.nameFieldLabel().sameAs(FMString.of("Save As:"))
                && panel.prompt().sameAs(FMString.of("Save")));

            // A name with no extension gets the one the format implies; a name that has
            // one keeps it, even a different one, because somebody who typed it meant it.
            FMURL plain = FMURL.of(work.toFile()).appending(FMString.of("notes"));
            FMURL already = FMURL.of(work.toFile()).appending(FMString.of("notes.txt"));
            failures += check(out, "a name typed without an extension gets the right one",
                completed(panel, plain).lastComponent().sameAs(FMString.of("notes.rtf"))
                && completed(panel, already).lastComponent().sameAs(FMString.of("notes.txt")));

            failures += check(out, "and an open panel says Open where a save panel says Save",
                FMOpenPanel.openPanel().prompt().sameAs(FMString.of("Open")));

            /* ------------------------------------------------------- what it browses */

            Path deep = work.resolve("Letters/2026/March");
            Files.createDirectories(deep);
            Files.writeString(work.resolve("Letters/note.rtf"), "x");

            FMBrowser browser = new FMBrowser();
            List<File> chosen = new ArrayList<>();
            browser.onChosen(chosen::add);
            onSwing(() -> browser.setRoot(work.toFile()));

            failures += check(out, "a browser opens on a folder as one column",
                columnsIn(browser) == 1);

            // Choosing a folder is what opens the column beside it. That is the whole of
            // what makes this a column browser rather than a list of one folder.
            onSwing(() -> select(browser, 0, "Letters"));
            failures += check(out, "choosing a folder opens the next column beside it",
                columnsIn(browser) == 2
                && browser.currentFolder() != null
                && "Letters".equals(browser.currentFolder().getName()));

            onSwing(() -> select(browser, 1, "2026"));
            failures += check(out, "and going deeper keeps the route on the screen",
                columnsIn(browser) == 3
                && "2026".equals(browser.currentFolder().getName())
                && chosen.size() == 2);

            /* --------------------------------------------- and the other two views */

            // The same folder drawn the other two ways. What must hold is that switching
            // does not move somebody somewhere else: the view changes, the place does not.
            onSwing(() -> browser.setMode(FMBrowser.Mode.LIST));
            failures += check(out, "switching to a list keeps the folder it was showing",
                browser.mode() == FMBrowser.Mode.LIST
                && "2026".equals(browser.currentFolder().getName()));

            onSwing(() -> browser.setMode(FMBrowser.Mode.ICON));
            failures += check(out, "and so does switching to icons",
                browser.mode() == FMBrowser.Mode.ICON
                && "2026".equals(browser.currentFolder().getName()));

            /* ------------------------------------------------------------ searching */

            Files.createDirectories(work.resolve("Letters/2026/April"));
            Files.createDirectories(work.resolve("Letters/2026/August"));
            onSwing(() -> browser.setMode(FMBrowser.Mode.COLUMN));
            onSwing(() -> browser.setRoot(work.resolve("Letters/2026").toFile()));
            int all = rowsIn(browser);
            onSwing(() -> browser.search(FMString.of("Aug")));
            int some = rowsIn(browser);
            failures += check(out, "searching narrows the folder to what was asked for",
                all >= 3 && some == 1);

            onSwing(() -> browser.search(FMString.EMPTY));
            failures += check(out, "and clearing it shows everything again",
                rowsIn(browser) == all);

            // Back to a route three deep, so what is checked below is a browser with
            // several columns rather than whatever the search happened to leave.
            onSwing(() -> browser.setRoot(work.toFile()));
            onSwing(() -> select(browser, 0, "Letters"));
            onSwing(() -> select(browser, 1, "2026"));

            /* ---------------------------------------------------- and it can be read */

            List<String> unnamed = new ArrayList<>();
            int named = countNamed(browser, unnamed);
            for (String one : unnamed) out.println("      unnamed: " + one);
            out.println("      the browser holds " + named + " named parts");
            failures += check(out, "every column is named for the folder it shows",
                unnamed.isEmpty() && named >= 3);

        } catch (Exception broken) {
            out.println("FAIL  the panel checks ran to the end: " + broken);
            failures++;
        } finally {
            remove(work);
        }

        out.println("      " + (failures == 0
            ? "saving goes through one panel, and the panel browses"
            : failures + " failed"));
        return failures;
    }

    /* ------------------------------------------------------------------ the tools */

    /** The panel's completion of a name, which is package private to everything else. */
    private static FMURL completed(FMSavePanel panel, FMURL where) throws Exception {
        java.lang.reflect.Method completing =
            FMSavePanel.class.getDeclaredMethod("completing", FMURL.class);
        completing.setAccessible(true);
        return (FMURL) completing.invoke(panel, where);
    }

    /** How many rows the browser is showing, whichever way it is showing them. */
    private static int rowsIn(FMBrowser browser) {
        List<JList<?>> lists = listsIn(browser, new ArrayList<>());
        if (!lists.isEmpty()) {
            return lists.get(lists.size() - 1).getModel().getSize();
        }
        for (javax.swing.JTable table : tablesIn(browser, new ArrayList<>())) {
            return table.getModel().getRowCount();
        }
        return 0;
    }

    private static List<javax.swing.JTable> tablesIn(Component c,
                                                     List<javax.swing.JTable> found) {
        if (c instanceof javax.swing.JTable table) found.add(table);
        if (c instanceof Container box) {
            for (Component kid : box.getComponents()) tablesIn(kid, found);
        }
        return found;
    }

    /** How many columns the browser is showing. */
    private static int columnsIn(FMBrowser browser) {
        return listsIn(browser, new ArrayList<>()).size();
    }

    private static List<JList<?>> listsIn(Component c, List<JList<?>> found) {
        if (c instanceof JList<?> list) found.add(list);
        if (c instanceof Container box) {
            for (Component kid : box.getComponents()) listsIn(kid, found);
        }
        return found;
    }

    /** Chooses a row by name in one column, the way a click would. */
    private static void select(FMBrowser browser, int column, String named) {
        List<JList<?>> lists = listsIn(browser, new ArrayList<>());
        if (column >= lists.size()) return;
        JList<?> list = lists.get(column);
        for (int i = 0; i < list.getModel().getSize(); i++) {
            Object row = list.getModel().getElementAt(i);
            if (row instanceof org.fractalmicro.fs.Node node && named.equals(node.name)) {
                list.setSelectedIndex(i);
                return;
            }
        }
    }

    /** Every part of the browser that a screen reader would meet, and whether it is named. */
    private static int countNamed(Component c, List<String> unnamed) {
        int named = 0;
        if (c instanceof JList<?> list) {
            AccessibleContext about = list.getAccessibleContext();
            String name = about == null ? null : about.getAccessibleName();
            if (name == null || name.isBlank()) unnamed.add("a column with no name");
            else named++;
        }
        if (c instanceof Container box) {
            for (Component kid : box.getComponents()) named += countNamed(kid, unnamed);
        }
        return named;
    }

    private static void onSwing(Runnable task) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeAndWait(task);
    }

    private static void remove(Path at) {
        try (java.util.stream.Stream<Path> walk = Files.walk(at)) {
            for (Path each : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(each);
            }
        } catch (IOException leftBehind) {
            // A temporary directory nothing will look at again.
        }
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
