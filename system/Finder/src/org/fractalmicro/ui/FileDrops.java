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
package org.fractalmicro.ui;

import org.fractalmicro.appkit.FMDragOperation;
import org.fractalmicro.appkit.FMFileDragging;
import org.fractalmicro.fs.Node;
import org.fractalmicro.fs.Trash;
import org.fractalmicro.theme.Icons;

import javax.swing.JList;
import javax.swing.JTable;
import java.awt.Point;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * What dragging means in a view of files.
 *
 * The kit knows how a drag works and refuses the drops that would lose somebody's folder.
 * What it cannot know is what the thing under the pointer is, because that is a listing and
 * listings belong to the file manager. This is the join: given a view and a way to ask what
 * is at a point, it says which folder a drop lands in.
 *
 * Three answers, and they are the three kinds of thing a Mac lets you drop onto:
 *
 *   a folder or a disk    the drop goes into it
 *   the Trash             the files are thrown away, whatever the keys say
 *   anything else         the folder the view is showing, which is what the white space
 *                         between the icons means
 *
 * The last is the one people use without noticing. Dropping onto the empty part of a window
 * puts the file in that window's folder, and it has to work at the edges too: a window
 * showing four files is mostly empty space, and every part of it that is not an icon is the
 * folder.
 */
public final class FileDrops {
    private FileDrops() {}

    /** Dragging out of and into a list of files. */
    public static void install(JList<Node> list, Supplier<File> showing) {
        Ghosts ghosts = new Ghosts(list);
        FMFileDragging.install(list,
            new Carrying(ghosts) {
                @Override public List<File> filesToDrag() {
                    List<Node> chosen = list.getSelectedValuesList();
                    ghosts.of(chosen, index -> list.getCellBounds(index, index),
                              list.getSelectedIndices());
                    return filesOf(chosen);
                }
            },
            new IntoNodes(list, showing) {
                @Override protected Node nodeAt(Point where) {
                    int i = list.locationToIndex(where);
                    if (i < 0) return null;
                    java.awt.Rectangle cell = list.getCellBounds(i, i);
                    return cell != null && cell.contains(where) ? list.getModel().getElementAt(i) : null;
                }
            });
    }

    /** The same, for a view that shows its files as rows. */
    public static void install(JTable table, IntFunction<Node> rowAt, Supplier<File> showing) {
        Ghosts ghosts = new Ghosts(table);
        FMFileDragging.install(table,
            new Carrying(ghosts) {
                @Override public List<File> filesToDrag() {
                    List<Node> chosen = new ArrayList<>();
                    for (int row : table.getSelectedRows()) {
                        Node n = rowAt.apply(table.convertRowIndexToModel(row));
                        if (n != null) chosen.add(n);
                    }
                    ghosts.of(chosen, row -> table.getCellRect(row, 0, true),
                              table.getSelectedRows());
                    return filesOf(chosen);
                }
            },
            new IntoNodes(table, showing) {
                @Override protected Node nodeAt(Point where) {
                    int row = table.rowAtPoint(where);
                    return row < 0 ? null : rowAt.apply(table.convertRowIndexToModel(row));
                }
            });
    }

    /**
     * Somewhere files can be thrown away.
     *
     * Not a folder, which is why it is its own destination rather than a folder that
     * happens to be the Trash: there is no directory on this host that means "the Trash",
     * and pretending otherwise would have the refusals in the kit asking whether a place
     * that does not exist is writable.
     */
    public static FMFileDragging.Destination theTrash() {
        return new FMFileDragging.Destination() {
            @Override public FMDragOperation operationAt(Point where, List<File> files, int keys) {
                // Always a move, whatever is held. There is no copying something into the
                // Trash: a Mac has one Trash and a second copy of a file in it would be a
                // file somebody threw away twice.
                return files.isEmpty() || !Trash.canMoveToTrash()
                    ? FMDragOperation.NONE : FMDragOperation.MOVE;
            }

            @Override public boolean take(Point where, List<File> files, FMDragOperation how) {
                List<Node> nodes = new ArrayList<>();
                for (File f : files) if (f != null && f.exists()) nodes.add(org.fractalmicro.fs.FS.node(f));
                if (nodes.isEmpty()) return false;
                Finder.moveToTrash(nodes);
                return true;
            }
        };
    }

    /* --------------------------------------------------- what the drag looks like */

    /** A source that carries files and draws them while they are being carried. */
    private abstract static class Carrying implements FMFileDragging.Source {
        private final Ghosts ghosts;

        Carrying(Ghosts ghosts) { this.ghosts = ghosts; }

        @Override public java.awt.Image pictureOfTheDrag() { return ghosts.picture(); }
        @Override public Point pointerInThePicture() { return ghosts.pointer(); }
    }

    /**
     * The ghosts of the files being dragged.
     *
     * A Mac drags translucent copies of the things being carried, each where it was when it
     * was picked up. That is more than decoration: it is the only thing that says what is in
     * your hand. A drag of one file and a drag of forty look the same otherwise, and so do a
     * drag of the file you meant and a drag of the one next to it.
     *
     * Drawn from the view's own cells, so the arrangement is the arrangement on the screen:
     * a row of icons stays a row, a column of rows stays a column, and nothing has to know
     * which kind of view it came from.
     */
    private static final class Ghosts {
        /** How see-through they are. A Mac's are faint enough to read the desktop through. */
        private static final float FAINTNESS = 0.55f;

        /** Big enough for any selection somebody can see, and no bigger. */
        private static final int WIDEST = 1200;
        private static final int TALLEST = 1200;

        private java.awt.Image picture;
        private Point pointer = new Point(0, 0);
        private Point pickedUpAt = new Point(0, 0);

        Ghosts(javax.swing.JComponent view) {
            // Where the drag began, which is where the pointer has to stay in the picture.
            // Without it the icons jump to sit beside the pointer the moment the drag
            // starts, which looks like dropping them and picking up something else.
            view.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mousePressed(java.awt.event.MouseEvent e) {
                    pickedUpAt = e.getPoint();
                }
            });
        }

        java.awt.Image picture() { return picture; }

        Point pointer() { return pointer; }

        /** Draws them, from where each one is in the view. */
        void of(List<Node> chosen, IntFunction<java.awt.Rectangle> cellOf, int[] indices) {
            picture = null;
            if (chosen == null || chosen.isEmpty() || indices.length == 0) return;

            java.awt.Rectangle all = null;
            for (int index : indices) {
                java.awt.Rectangle cell = cellOf.apply(index);
                if (cell == null) continue;
                all = all == null ? new java.awt.Rectangle(cell) : all.union(cell);
            }
            if (all == null || all.width <= 0 || all.height <= 0) return;
            if (all.width > WIDEST || all.height > TALLEST) return;

            java.awt.image.BufferedImage made = new java.awt.image.BufferedImage(
                all.width, all.height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = made.createGraphics();
            org.fractalmicro.theme.Aqua.antialias(g);
            g.setComposite(java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, FAINTNESS));
            int size = Math.min(64, Math.max(16, smallestCellHeight(cellOf, indices) - 4));
            for (int i = 0; i < indices.length && i < chosen.size(); i++) {
                java.awt.Rectangle cell = cellOf.apply(indices[i]);
                if (cell == null) continue;
                java.awt.Image icon = Icons.forNode(chosen.get(i), size);
                g.drawImage(icon, cell.x - all.x + (cell.width - size) / 2,
                            cell.y - all.y + (cell.height - size) / 2, null);
            }
            g.dispose();

            picture = made;
            pointer = new Point(pickedUpAt.x - all.x, pickedUpAt.y - all.y);
        }

        /**
         * How big to draw them.
         *
         * From the shortest cell, so a list of twenty-pixel rows gets sixteen-pixel icons
         * and a grid of icons gets the size it is already showing. Taking the tallest
         * instead would have one big cell make every icon in the drag too large for its
         * place, which is the arrangement no longer being the arrangement.
         */
        private static int smallestCellHeight(IntFunction<java.awt.Rectangle> cellOf, int[] indices) {
            int shortest = Integer.MAX_VALUE;
            for (int index : indices) {
                java.awt.Rectangle cell = cellOf.apply(index);
                if (cell != null) shortest = Math.min(shortest, cell.height);
            }
            return shortest == Integer.MAX_VALUE ? 32 : shortest;
        }
    }

    /* ------------------------------------------------------------------- inside */

    /** A destination that reads a listing: whatever is under the pointer decides. */
    private abstract static class IntoNodes extends FMFileDragging.IntoFolders {
        private final Supplier<File> showing;
        private final javax.swing.JComponent view;

        IntoNodes(javax.swing.JComponent view, Supplier<File> showing) {
            this.view = view;
            this.showing = showing;
        }

        /* ---------------------------------------------------- springing open */

        /** Where the window was before the first folder sprang open, and nothing if none has. */
        private File before;
        /** A window opened by springing, which is shut again rather than gone back in. */
        private FinderWindow opened;

        /**
         * The folder resting here would open.
         *
         * Not the folder this view is already showing, which is what the space between the
         * icons answers with and is not somewhere to go. Not a disk that is not in the
         * drive either, and not a bundle, since opening one of those means running it.
         */
        @Override public File wouldSpringOpen(Point where) {
            Node under = nodeAt(where);
            if (under == null || under.file == null || !under.isContainer()) return null;
            if (org.fractalmicro.bundle.Bundle.looksLikeBundle(under.file)) return null;
            if (under.isVolume() && !under.isMounted()) return null;
            return under.file;
        }

        /**
         * Goes into it, with the drag still going.
         *
         * The window this view is in, where there is one. The desktop is in no window, so a
         * folder springing open there opens one, which is what a Mac does: the desktop
         * cannot show a folder's contents in place because it is showing the desktop.
         */
        @Override public void springOpen(File folder) {
            FinderWindow window = windowOf();
            if (window != null) {
                if (before == null) before = window.currentFolder();
                window.navigateTo(folder);
                return;
            }
            if (opened == null) opened = Finder.newWindow(folder);
            else opened.navigateTo(folder);
        }

        /**
         * Puts back what springing opened, once the drag is over.
         *
         * A window that was already there goes back to the folder it was showing. One that
         * springing opened is shut. Either way a drag through four folders leaves the screen
         * as it found it, which is the whole reason a spring-loaded folder springs back.
         */
        @Override public void springBack() {
            if (before != null) {
                FinderWindow window = windowOf();
                if (window != null) window.navigateTo(before);
                before = null;
            }
            if (opened != null) {
                opened.dispose();
                opened = null;
            }
        }

        private FinderWindow windowOf() {
            return (FinderWindow) javax.swing.SwingUtilities
                .getAncestorOfClass(FinderWindow.class, view);
        }

        /** What the view is drawing at this point, or nothing where there is nothing. */
        protected abstract Node nodeAt(Point where);

        @Override protected File folderAt(Point where) {
            Node under = nodeAt(where);
            if (under != null) {
                if (under.kind == Node.Kind.TRASH) return null;   // taken by its own case below
                if (under.isVolume() && !under.isMounted()) return null;
                // A bundle is a folder on disk and a single thing on the screen. Dropping
                // onto one is dropping onto the program, not filing something inside it,
                // and this system has no way to hand a program a file it did not open. So
                // it falls through to the folder behind, which is what a person sees.
                if (under.isContainer() && under.file != null
                        && !org.fractalmicro.bundle.Bundle.looksLikeBundle(under.file)) {
                    return under.file;
                }
            }
            return showing == null ? null : showing.get();
        }

        @Override public FMDragOperation operationAt(Point where, List<File> files, int keys) {
            Node under = nodeAt(where);
            if (under != null && under.kind == Node.Kind.TRASH) {
                return files.isEmpty() || !Trash.canMoveToTrash()
                    ? FMDragOperation.NONE : FMDragOperation.MOVE;
            }
            return super.operationAt(where, files, keys);
        }

        @Override public boolean take(Point where, List<File> files, FMDragOperation how) {
            Node under = nodeAt(where);
            if (under != null && under.kind == Node.Kind.TRASH) {
                return theTrash().take(where, files, how);
            }
            return super.take(where, files, how);
        }

        @Override protected boolean receive(List<File> files, File into, FMDragOperation how) {
            return Finder.receiveDrop(files, into, how);
        }
    }

    private static List<File> filesOf(List<Node> nodes) {
        List<File> out = new ArrayList<>();
        for (Node n : nodes) {
            // A disk cannot be dragged anywhere and neither can the Trash. Both are places
            // rather than files, and a drag that carried one would be offering to move it.
            if (n == null || n.file == null || n.isVolume() || n.kind == Node.Kind.TRASH) continue;
            out.add(n.file);
        }
        return out;
    }
}
