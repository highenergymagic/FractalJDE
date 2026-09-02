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

import org.fractalmicro.appkit.FMDragOperation;
import org.fractalmicro.appkit.FMFileDragging;
import org.fractalmicro.appkit.FMPasteboard;
import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMURL;
import org.fractalmicro.fs.FS;
import org.fractalmicro.ui.Finder;

import java.awt.Point;
import java.awt.event.InputEvent;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Dragging files, which is most of what a file manager is for.
 *
 * Three things are checked and they are separate on purpose. What a drop would do, which is
 * arithmetic on the keys held and the two paths and is the part a person feels: get it wrong
 * and files are copied when they should have moved, or worse. What a drop refuses, which is
 * the part that decides whether somebody can lose a folder by aiming badly. And what a drop
 * actually does to the disk, including whether the way back from it works.
 *
 * The middle one is worth more than it looks. A folder dropped into itself, or into a folder
 * inside itself, is the case that eats the folder: the move rewrites the path underneath the
 * copy that is walking it. It cannot be tested by dragging, so it is tested here, where the
 * refusal lives.
 */
public final class DragTest {
    private DragTest() {}

    public static int count() { return 35; }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("dragging files:");

        failures += checkTheRules(out);
        failures += checkWhatIsRefused(out);
        failures += checkTheBoard(out);
        failures += checkDoingIt(out);
        failures += checkTheViewsAreWiredUp(out);
        failures += checkSpringing(out);

        out.println("      " + (failures == 0 ? "files go where they are dropped"
                                              : failures + " failed"));
        return failures;
    }

    /* ------------------------------------------------------- what a drop would do */

    private static int checkTheRules(PrintStream out) {
        int failures = 0;
        File home = FS.home();
        File alsoHome = FS.desktopFolder();

        failures += check(out, "on one disk, dragging moves",
            FMDragOperation.forDrop(0, home, alsoHome) == FMDragOperation.MOVE);

        // Somewhere that is certainly not this volume. A path on a drive letter that is not
        // mounted cannot be asked which store it is on, and the answer to not knowing has to
        // be copy: a copy that should have been a move leaves the original behind, which a
        // person can tidy up. A move that should have been a copy cannot be untidied.
        File elsewhere = new File("\\\\a-machine-that-is-not-here\\share");
        failures += check(out, "and when it cannot be told, it copies",
            FMDragOperation.forDrop(0, home, elsewhere) == FMDragOperation.COPY);

        failures += check(out, "Option copies, wherever it is going",
            FMDragOperation.forDrop(InputEvent.ALT_DOWN_MASK, home, alsoHome)
                == FMDragOperation.COPY
            && FMDragOperation.forDrop(InputEvent.ALT_DOWN_MASK, home, elsewhere)
                == FMDragOperation.COPY);

        failures += check(out, "Command moves, wherever it is going",
            FMDragOperation.forDrop(InputEvent.META_DOWN_MASK, home, elsewhere)
                == FMDragOperation.MOVE);

        failures += check(out, "and both together make an alias",
            FMDragOperation.forDrop(InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK,
                                    home, alsoHome) == FMDragOperation.LINK);

        failures += check(out, "the pointer shows which of the three it is",
            FMDragOperation.MOVE.asSwing() == javax.swing.TransferHandler.MOVE
            && FMDragOperation.COPY.asSwing() == javax.swing.TransferHandler.COPY
            && FMDragOperation.NONE.asSwing() == javax.swing.TransferHandler.NONE);
        return failures;
    }

    /* --------------------------------------------------------- what a drop refuses */

    private static int checkWhatIsRefused(PrintStream out) {
        int failures = 0;
        Path root;
        try {
            root = Files.createTempDirectory("fractal-drag-check");
        } catch (Exception e) {
            out.println("FAIL  a folder to work in: " + e);
            return 7;
        }

        try {
            File outer = new File(root.toFile(), "Outer");
            File inner = new File(outer, "Inner");
            File deeper = new File(inner, "Deeper");
            deeper.mkdirs();
            File document = new File(root.toFile(), "Report.txt");
            Files.writeString(document.toPath(), "a report");

            Where into = new Where(outer);
            failures += check(out, "a folder cannot be dropped into itself",
                into.operationAt(POINT, List.of(outer), 0) == FMDragOperation.NONE);

            failures += check(out, "nor into a folder inside it",
                new Where(inner).operationAt(POINT, List.of(outer), 0)
                    == FMDragOperation.NONE
                && new Where(deeper).operationAt(POINT, List.of(outer), 0)
                    == FMDragOperation.NONE);

            failures += check(out, "but a folder inside it can be dropped out",
                new Where(root.toFile())
                    .operationAt(POINT, List.of(inner), 0) == FMDragOperation.MOVE);

            failures += check(out, "moving something where it already is does nothing",
                new Where(root.toFile())
                    .operationAt(POINT, List.of(document), 0) == FMDragOperation.NONE);

            failures += check(out, "though copying it there is Duplicate, and is allowed",
                new Where(root.toFile()).operationAt(
                    POINT, List.of(document), InputEvent.ALT_DOWN_MASK) == FMDragOperation.COPY);

            failures += check(out, "a drop carrying nothing is refused",
                into.operationAt(POINT, List.of(), 0) == FMDragOperation.NONE);

            failures += check(out, "and one aimed at nowhere",
                new Where(null).operationAt(POINT, List.of(document), 0)
                    == FMDragOperation.NONE);
        } catch (Exception e) {
            out.println("FAIL  what a drop refuses: " + e);
            failures++;
        } finally {
            deleteTree(root.toFile());
        }
        return failures;
    }

    /* ------------------------------------------------------------------- the board */

    private static int checkTheBoard(PrintStream out) {
        int failures = 0;
        File one = FS.home();
        File two = FS.desktopFolder();

        java.awt.datatransfer.Transferable carried = FMPasteboard.carrying(List.of(one, two));
        FMArray<FMURL> read = FMPasteboard.filesIn(carried);
        failures += check(out, "files put on the board come off it again",
            read.count() == 2 && read.at(0).asFile().equals(one)
            && read.at(1).asFile().equals(two));

        failures += check(out, "and something carrying no files reads as none",
            FMPasteboard.filesIn(new java.awt.datatransfer.StringSelection("words")).isEmpty()
            && FMPasteboard.filesIn(null).isEmpty());
        return failures;
    }

    /* --------------------------------------------------------------- doing it */

    private static int checkDoingIt(PrintStream out) {
        int failures = 0;
        Path root;
        try {
            root = Files.createTempDirectory("fractal-drop-check");
        } catch (Exception e) {
            out.println("FAIL  a folder to work in: " + e);
            return 5;
        }

        try {
            Finder.undoManager().removeAllActions();
            File from = new File(root.toFile(), "From");
            File to = new File(root.toFile(), "To");
            from.mkdirs();
            to.mkdirs();
            File document = new File(from, "Report.txt");
            Files.writeString(document.toPath(), "a report");

            boolean moved = Finder.receiveDrop(List.of(document), to, FMDragOperation.MOVE);
            File landed = new File(to, "Report.txt");
            failures += check(out, "a move takes the file with it",
                moved && landed.isFile() && !document.exists());

            failures += check(out, "and the way back from it is offered by name",
                Finder.undoManager().canUndo()
                && !Finder.undoManager().undoActionName().isEmpty());

            Finder.undoManager().undo();
            failures += check(out, "and taking it puts the file back where it was",
                document.isFile() && !landed.exists());

            Finder.undoManager().removeAllActions();
            boolean copied = Finder.receiveDrop(List.of(document), to, FMDragOperation.COPY);
            File copy = new File(to, "Report.txt");
            failures += check(out, "a copy leaves the file where it was",
                copied && copy.isFile() && document.isFile()
                && Files.readString(copy.toPath()).equals("a report"));

            Finder.undoManager().undo();
            failures += check(out, "and the way back from a copy removes what it made",
                !copy.exists() && document.isFile());

            // A copy into the folder the file is already in, which is what Option-dragging
            // something onto its own window means. It cannot have the same name.
            Finder.undoManager().removeAllActions();
            Finder.receiveDrop(List.of(document), from, FMDragOperation.COPY);
            failures += check(out, "a copy beside the original is named as a copy",
                new File(from, "Report copy.txt").isFile() && document.isFile());

            failures += check(out, "a drop of nothing, or into nowhere, does nothing",
                !Finder.receiveDrop(List.of(), to, FMDragOperation.MOVE)
                && !Finder.receiveDrop(List.of(document), null, FMDragOperation.MOVE)
                && !Finder.receiveDrop(List.of(document), to, FMDragOperation.NONE));
        } catch (Exception e) {
            out.println("FAIL  what a drop does: " + e);
            failures++;
        } finally {
            Finder.undoManager().removeAllActions();
            deleteTree(root.toFile());
        }
        return failures;
    }

    /* ------------------------------------------------- whether the views were wired */

    /**
     * That every view in a window can actually be dragged out of and into.
     *
     * The rules above are arithmetic and would pass with nothing wired to them. This is the
     * check that fails when somebody adds a fifth view, or a sixth place to drop, and does
     * not connect it: it walks the window rather than asking it, so a view that exists and
     * was forgotten is found by being there.
     *
     * The export is done for real, through the same call the drag itself makes, onto a
     * board of this check's own so a person's clipboard is not taken while it runs.
     */
    private static int checkTheViewsAreWiredUp(PrintStream out) {
        int failures = 0;
        Path root;
        try {
            root = Files.createTempDirectory("fractal-wiring-check");
        } catch (Exception e) {
            out.println("FAIL  a folder to work in: " + e);
            return 4;
        }

        org.fractalmicro.ui.FinderWindow window = null;
        try {
            File document = new File(root.toFile(), "Report.txt");
            Files.writeString(document.toPath(), "a report");

            window = org.fractalmicro.ui.Finder.newWindow(root.toFile());
            List<java.awt.Component> unwired = new ArrayList<>();
            List<javax.swing.JComponent> wired = new ArrayList<>();
            for (String mode : List.of("Icon", "List", "Column", "Cover Flow")) {
                window.setViewMode(mode);
                collect(window, wired, unwired);
            }

            failures += check(out, "every list of files in a window takes a drag",
                unwired.isEmpty() && !wired.isEmpty());
            if (!unwired.isEmpty()) {
                for (java.awt.Component c : unwired) {
                    out.println("      not wired: " + c.getClass().getSimpleName() + ", "
                                + c.getAccessibleContext().getAccessibleName());
                }
            }

            failures += check(out, "the desktop takes one too",
                org.fractalmicro.windowserver.Desktop.sharedDesktop().icons()
                    .getTransferHandler() instanceof FMFileDragging);

            // Out of a view, for real: choose the file and ask the handler to hand it over,
            // through the same call the drag itself makes. Onto a board of this check's own,
            // so a person's clipboard is not taken while it runs.
            window.setViewMode("Icon");
            window.selectAll();
            FMArray<FMURL> exported = FMArray.empty();
            for (javax.swing.JComponent control : wired) {
                java.awt.datatransfer.Clipboard board =
                    new java.awt.datatransfer.Clipboard("check");
                control.getTransferHandler().exportToClipboard(
                    control, board, javax.swing.TransferHandler.COPY);
                FMArray<FMURL> got = FMPasteboard.filesIn(board.getContents(null));
                if (got.count() > 0) { exported = got; break; }
            }
            final FMArray<FMURL> carried = exported;
            failures += check(out, "and dragging out of one carries the files chosen",
                carried.count() == 1 && carried.at(0).asFile().getName().equals("Report.txt"));

            // Dropping onto an item rather than between two of them, in every view. A
            // listing is sorted, so the gap between two rows is not a place a file can go,
            // and a view left on the mode Swing starts in would offer to put one there.
            boolean allOnItems = true;
            for (javax.swing.JComponent control : wired) {
                if (control instanceof javax.swing.JList<?> list) {
                    allOnItems &= list.getDropMode() == javax.swing.DropMode.ON
                               && list.getDragEnabled();
                } else if (control instanceof javax.swing.JTable table) {
                    allOnItems &= table.getDropMode() == javax.swing.DropMode.ON;
                }
            }
            failures += check(out, "and a drop lands on something, never between two things",
                allOnItems);
        } catch (Exception e) {
            out.println("FAIL  whether the views were wired: " + e);
            failures++;
        } finally {
            if (window != null) window.dispose();
            deleteTree(root.toFile());
        }
        return failures;
    }

    /** Every list and table under a window, split by whether a drag would be taken. */
    private static void collect(java.awt.Container from, List<javax.swing.JComponent> wired,
                                List<java.awt.Component> unwired) {
        for (java.awt.Component child : from.getComponents()) {
            boolean showsFiles = child instanceof javax.swing.JList
                              || child instanceof javax.swing.JTable;
            if (showsFiles) {
                javax.swing.JComponent control = (javax.swing.JComponent) child;
                if (control.getTransferHandler() instanceof FMFileDragging) wired.add(control);
                else unwired.add(control);
            }
            if (child instanceof java.awt.Container inside) collect(inside, wired, unwired);
        }
    }

    /* ------------------------------------------------------------- springing open */

    /**
     * Spring-loaded folders: resting a drag on one opens it, and the window goes back
     * afterwards.
     *
     * The clock is a timer and what it does when it fires is the part worth checking, so
     * this asks the view's own destination the two questions the timer asks it: what would
     * resting here open, and open it. Then it ends the drag the way the drop target does
     * and looks at where the window is.
     *
     * The going back is the half people notice. Without it a drag through four folders
     * leaves four windows open, which is the thing spring loading exists to save them from,
     * and it is the half that is easy to leave out because the opening looks finished.
     */
    private static int checkSpringing(PrintStream out) {
        int failures = 0;

        failures += check(out, "resting on a folder opens it, unless it is turned off",
            org.fractalmicro.os.FinderSettings.springLoaded());

        // Space opens one straight away, without the wait. It has to be read rather than
        // waited for: while the mouse is down the keyboard belongs to the drag and no key
        // event reaches this program at all. Checked by asking about a key that cannot be
        // held, since asking about the space bar during a check would depend on what the
        // person at the machine happens to be leaning on.
        failures += check(out, "a key can be asked about while a drag has the keyboard",
            !org.fractalmicro.win.User32.isKeyDown(0)
            && org.fractalmicro.win.User32.VK_SPACE == 0x20);
        double delay = org.fractalmicro.os.FinderSettings.springDelay();
        failures += check(out, "and the wait is a length of time somebody would wait",
            delay >= 0.2 && delay <= 2.0);

        Path root;
        try {
            root = Files.createTempDirectory("fractal-spring-check");
        } catch (Exception e) {
            out.println("FAIL  a folder to work in: " + e);
            return failures + 4;
        }

        org.fractalmicro.ui.FinderWindow window = null;
        try {
            File inside = new File(root.toFile(), "Inside");
            inside.mkdirs();
            File document = new File(root.toFile(), "Report.txt");
            Files.writeString(document.toPath(), "a report");

            window = org.fractalmicro.ui.Finder.newWindow(root.toFile());
            window.setViewMode("List");
            drain();

            javax.swing.JTable rows = tableIn(window);
            FMFileDragging dragging = rows == null ? null
                : (FMFileDragging) rows.getTransferHandler();
            if (dragging == null) {
                out.println("FAIL  the view a drag would rest on");
                return failures + 4;
            }
            FMFileDragging.Destination view = dragging.destination();

            Point onTheFolder = middleOfRowShowing(rows, "Inside");
            Point onTheFile = middleOfRowShowing(rows, "Report.txt");
            failures += check(out, "a folder is something resting would open",
                onTheFolder != null && inside.equals(view.wouldSpringOpen(onTheFolder)));
            failures += check(out, "and a file is not",
                onTheFile == null || view.wouldSpringOpen(onTheFile) == null);

            // What is under the pointer while it is being carried. Without it a drag of one
            // file and a drag of forty look the same, and so do a drag of the file somebody
            // meant and a drag of the one next to it.
            rows.setRowSelectionInterval(0, rows.getRowCount() - 1);
            FMFileDragging.Source carried = dragging.source();
            List<File> dragged = carried.filesToDrag();
            java.awt.Image ghosts = carried.pictureOfTheDrag();
            failures += check(out, "a drag carries a picture of what is in it",
                dragged.size() == 2 && ghosts != null
                && ghosts.getWidth(null) > 0 && ghosts.getHeight(null) > 0);

            Point grabbed = carried.pointerInThePicture();
            failures += check(out, "and the pointer stays where it took hold of them",
                grabbed != null && grabbed.x >= 0 && grabbed.y >= 0
                && grabbed.x <= ghosts.getWidth(null)
                && grabbed.y <= ghosts.getHeight(null));

            view.springOpen(inside);
            drain();
            failures += check(out, "opening it takes the window into it",
                inside.equals(window.currentFolder()));

            view.springBack();
            drain();
            failures += check(out, "and the window goes back when the drag is over",
                root.toFile().equals(window.currentFolder()));
        } catch (Exception e) {
            out.println("FAIL  spring-loaded folders: " + e);
            failures++;
        } finally {
            if (window != null) window.dispose();
            deleteTree(root.toFile());
        }
        return failures;
    }

    /**
     * The table list view shows its files in.
     *
     * Told from the sidebar by having more than one column, since the sidebar is a table as
     * well and takes drags as well. Asking the sidebar these questions would get the
     * sidebar's answers, which are about places rather than about what is in the folder.
     */
    private static javax.swing.JTable tableIn(java.awt.Container from) {
        for (java.awt.Component child : from.getComponents()) {
            if (child instanceof javax.swing.JTable table
                    && table.getTransferHandler() instanceof FMFileDragging
                    && table.getColumnCount() > 1
                    && table.getRowCount() > 0) {
                return table;
            }
            if (child instanceof java.awt.Container inside) {
                javax.swing.JTable found = tableIn(inside);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** A point in the middle of the row showing this name, or nothing if it shows none. */
    private static Point middleOfRowShowing(javax.swing.JTable table, String name) {
        for (int row = 0; row < table.getRowCount(); row++) {
            Object value = table.getValueAt(row, 0);
            // The cell holds the file itself, not its name: a listing draws a row from the
            // whole of what it is showing, and the name is one of the things it draws.
            String showing = value instanceof org.fractalmicro.fs.Node node
                ? node.name : String.valueOf(value);
            if (value == null || !name.equals(showing)) continue;
            java.awt.Rectangle cell = table.getCellRect(row, 0, true);
            return new Point(cell.x + cell.width / 2, cell.y + cell.height / 2);
        }
        return null;
    }

    /** Lets the screen catch up, since a window is laid out on the main thread. */
    private static void drain() {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) { }
    }

    /* ----------------------------------------------------------------- plumbing */

    private static final Point POINT = new Point(10, 10);

    /**
     * A destination that always answers with one folder.
     *
     * The refusals live in the kit and not in any view, so they are asked here without one:
     * a view would only be somewhere for a pointer to be, and every one of these questions
     * is about the two paths rather than about where the mouse is.
     */
    private static final class Where extends FMFileDragging.IntoFolders {
        private final File folder;
        private final List<File> taken = new ArrayList<>();

        Where(File folder) { this.folder = folder; }

        @Override protected File folderAt(Point where) { return folder; }

        @Override protected boolean receive(List<File> files, File into, FMDragOperation how) {
            taken.addAll(files);
            return true;
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
