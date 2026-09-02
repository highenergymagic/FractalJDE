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

import org.fractalmicro.appkit.FMApplication;
import org.fractalmicro.appkit.FMBrowser;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.nib.Nib;
import org.fractalmicro.nib.Nib.ControlClass;
import org.fractalmicro.nib.Xib;
import org.fractalmicro.windowserver.Desktop;
import org.fractalmicro.windowserver.WindowServer;

import javax.swing.JInternalFrame;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A folder in a window a program does not draw.
 *
 * Every control before this one was something a program could describe in full: a label
 * holds the words it was given, a list holds the rows it was sent. A folder is not like
 * that. What is in it is on the disk, and the disk is on this side of the boundary along
 * with the icons, the kinds and the dates, so the description says which folder and how to
 * show it and nothing else crosses. That is the difference that lets a window with a
 * hundred thousand files in it be described in one short message.
 *
 * What is checked here is that boundary in both directions: a folder put in from outside
 * arrives, what somebody chooses comes back out, and the three ways of showing it and the
 * search can be driven by a program with no screen. It runs against a folder made for the
 * purpose, so what it finds does not depend on whose machine it is.
 */
public final class BrowserTest {
    private BrowserTest() {}

    public static int count() { return 14; }

    /** The control this is all about, named once. */
    private static final FMString FILES = FMString.of("files");

    public static int run(Desktop desktop, PrintStream out) {
        int failures = 0;
        out.println();
        out.println("a folder in a described window:");

        Path folder;
        try {
            folder = Files.createTempDirectory("fractal-browser-check");
            Files.createDirectory(folder.resolve("Inside"));
            Files.writeString(folder.resolve("Inside").resolve("Deeper.txt"), "deep");
            Files.writeString(folder.resolve("Notes.txt"), "notes");
        } catch (Exception e) {
            out.println("FAIL  a folder to look at: " + e);
            return count();
        }

        try {
            failures += checkDescription(out, folder);
            failures += checkWindow(desktop, out, folder);
        } finally {
            deleteTree(folder.toFile());
        }

        out.println("      " + (failures == 0
            ? "a program with no screen can show a folder and be told what was chosen"
            : failures + " failed"));
        return failures;
    }

    /* --------------------------------------------------------- what it looks like */

    private static int checkDescription(PrintStream out, Path folder) {
        int failures = 0;
        Nib nib = describing(folder);

        try {
            Nib again = Nib.parse(nib.toBytes());
            failures += check(out, "a description carrying a folder survives being read back",
                again.control(FILES) != null
                && again.control(FILES).kind() == ControlClass.FMBrowser
                && folder.toString().equals(again.control(FILES).text().toString()));
        } catch (Exception e) {
            out.println("FAIL  a description carrying a folder survives being read back: " + e);
            failures++;
        }

        try {
            Nib again = Xib.parse(Xib.write(nib));
            failures += check(out, "and survives being written as an interface file",
                again.control(FILES) != null
                && again.control(FILES).kind() == ControlClass.FMBrowser);
        } catch (Exception e) {
            out.println("FAIL  and survives being written as an interface file: " + e);
            failures++;
        }
        return failures;
    }

    private static Nib describing(Path folder) {
        return new Nib.Builder()
            .title(FMString.of("Files")).size(560, 360)
            .add(ControlClass.FMBrowser, FILES, FMString.of("Files"),
                 FMString.of(folder.toString()), 8, 8, 540, 300)
            .build();
    }

    /* ------------------------------------------------------------ what it does */

    private static int checkWindow(Desktop desktop, PrintStream out, Path folder) {
        int failures = 0;
        WindowServer server = WindowServer.sharedServer();
        if (!server.start() && !server.isRunning()) {
            out.println("FAIL  the window server is not running, so nothing can be shown");
            return count() - 2;
        }

        try (FMApplication app = FMApplication.named(FMString.of("Browsing"))) {
            boolean opened = app.showWindow(describing(folder));
            drain();

            JInternalFrame frame = frameTitled(desktop, "Files");
            FMBrowser browser = frame == null ? null : browserIn(frame);
            failures += check(out, "a window holding a folder opens from a description",
                opened && browser != null);
            if (browser == null) return failures + count() - 3;

            failures += check(out, "the folder announces itself to a screen reader",
                "Files".equals(browser.getAccessibleContext().getAccessibleName()));

            /* ------------------------------------------------ where it is */
            failures += check(out, "it opened on the folder the description named",
                sameFile(browser.currentFolder(), folder.toFile()));

            failures += check(out, "asking what it holds answers the folder, with nothing chosen",
                sameName(app.valueOf(FILES), folder.toFile()));

            File inside = folder.resolve("Inside").toFile();
            app.setValue(FILES, FMString.of(inside.getAbsolutePath()));
            drain();
            failures += check(out, "a folder sent in from outside is the one it shows",
                sameFile(browser.currentFolder(), inside));

            /* ------------------------------------------------ the three views */
            boolean asList = app.perform(FILES, FMString.of(FMBrowser.AS_LIST));
            drain();
            boolean list = browser.mode() == FMBrowser.Mode.LIST;
            boolean asIcons = app.perform(FILES, FMString.of(FMBrowser.AS_ICONS));
            drain();
            boolean icons = browser.mode() == FMBrowser.Mode.ICON;
            boolean asColumns = app.perform(FILES, FMString.of(FMBrowser.AS_COLUMNS));
            drain();
            failures += check(out, "it can be shown three ways by a program with no screen",
                asList && list && asIcons && icons
                && asColumns && browser.mode() == FMBrowser.Mode.COLUMN);

            /* ------------------------------------------------ going up */
            app.setValue(FILES, FMString.of(inside.getAbsolutePath()));
            drain();
            boolean wentUp = app.perform(FILES, FMString.of(FMBrowser.ENCLOSING_FOLDER));
            drain();
            failures += check(out, "and sent up a level",
                wentUp && sameFile(browser.currentFolder(), folder.toFile()));

            failures += check(out, "and refused something it cannot do",
                !app.perform(FILES, FMString.of("makeCoffee")));

            /* ------------------------------------------------ looking for something */
            boolean looked = app.find(FILES, FMString.of("Deeper"));
            drain();
            boolean searching = !browser.searching().isEmpty();
            boolean stopped = app.find(FILES, FMString.EMPTY);
            drain();
            failures += check(out, "it can be asked to look for something, and to stop",
                looked && searching && stopped && browser.searching().isEmpty());

            /* ------------------------------------------------ what comes back */
            app.setValue(FILES, FMString.of(folder.toString()));
            drain();
            while (app.nextEvent(50) != null) {
                // Everything said so far. What is being checked is the next one.
            }

            // A file rather than a folder, because choosing a folder moves the browser
            // into it and then the two things being told apart here would be the same
            // path. What is being checked is that they are two things.
            JList<?> column = firstListIn(browser);
            int notes = rowOf(column, folder.resolve("Notes.txt").toFile());
            if (notes >= 0) SwingUtilities.invokeLater(() -> column.setSelectedIndex(notes));
            drain();
            FMApplication.Event chosen = notes < 0 ? null : app.nextEvent(2000);
            failures += check(out, "choosing something sends the program its path",
                chosen != null && chosen.control().sameAs(FILES)
                && sameName(chosen.text(), folder.resolve("Notes.txt").toFile()));
            failures += check(out, "and says where the browser was when it was chosen",
                chosen != null && sameName(chosen.where(), folder.toFile()));

            // And opening it, which is the same row and a different answer. A program that
            // could not tell the two apart would open a folder every time somebody looked
            // at one, which is the whole reason they are separate events.
            if (notes >= 0) SwingUtilities.invokeLater(() -> doubleClick(column, notes));
            drain();
            FMApplication.Event openedIt = notes < 0 ? null : app.nextEvent(2000);
            failures += check(out, "opening it says so, and is not the same as choosing it",
                openedIt != null && openedIt.isOpen()
                && !openedIt.kind().sameAs(chosen == null ? FMString.EMPTY : chosen.kind())
                && sameName(openedIt.text(), folder.resolve("Notes.txt").toFile()));

            app.hideWindow();
            drain();
        } catch (Exception e) {
            out.println("FAIL  the window holding a folder could be driven: " + e);
            failures++;
        }
        return failures;
    }

    /* ---------------------------------------------------------------- looking */

    private static FMBrowser browserIn(Container where) {
        for (Component child : where.getComponents()) {
            if (child instanceof FMBrowser browser) return browser;
            if (child instanceof Container inside) {
                FMBrowser found = browserIn(inside);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Two clicks on a row, which is how a person opens something.
     *
     * Built and dispatched rather than performed with the mouse. Nothing here touches the
     * pointer: the check has to run on a machine somebody else is using, and it has to run
     * on one with no pointer at all.
     */
    private static void doubleClick(JList<?> column, int row) {
        column.setSelectedIndex(row);
        java.awt.Rectangle cell = column.getCellBounds(row, row);
        int x = cell == null ? 4 : cell.x + 4;
        int y = cell == null ? 4 : cell.y + cell.height / 2;
        column.dispatchEvent(new java.awt.event.MouseEvent(column,
            java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
            x, y, 2, false, java.awt.event.MouseEvent.BUTTON1));
    }

    /**
     * Which row of a column is a given file, or -1 when none is.
     *
     * By the file rather than by the name shown, because the name shown is the name a
     * person sees: extensions are hidden when the settings say so, and a check that looked
     * for "Notes.txt" would find nothing on a machine set up that way.
     */
    private static int rowOf(JList<?> column, File wanted) {
        if (column == null) return -1;
        for (int i = 0; i < column.getModel().getSize(); i++) {
            Object row = column.getModel().getElementAt(i);
            if (row instanceof org.fractalmicro.fs.Node node && sameFile(node.file, wanted)) {
                return i;
            }
        }
        return -1;
    }

    private static JList<?> firstListIn(Container where) {
        for (Component child : where.getComponents()) {
            if (child instanceof JList<?> list) return list;
            if (child instanceof Container inside) {
                JList<?> found = firstListIn(inside);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JInternalFrame frameTitled(Desktop desktop, String title) {
        for (JInternalFrame frame : desktop.windows()) {
            if (title.equals(frame.getTitle())) return frame;
        }
        return null;
    }

    /**
     * Whether two names are the same folder.
     *
     * Asked of the file system, because a temporary directory on Windows is reached by a
     * short name and answered with a long one, and those are the same folder written two
     * ways.
     */
    private static boolean sameFile(File one, File other) {
        if (one == null || other == null) return false;
        try {
            return one.getCanonicalFile().equals(other.getCanonicalFile());
        } catch (java.io.IOException cannotResolve) {
            return one.getAbsolutePath().equals(other.getAbsolutePath());
        }
    }

    private static boolean sameName(FMString said, File wanted) {
        return said != null && !said.isEmpty() && sameFile(new File(said.toString()), wanted);
    }

    /** Lets the event thread finish what it was asked to do. */
    private static void drain() {
        try {
            SwingUtilities.invokeAndWait(() -> { });
            Thread.sleep(60);
            SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception interrupted) {
            Thread.currentThread().interrupt();
        }
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
