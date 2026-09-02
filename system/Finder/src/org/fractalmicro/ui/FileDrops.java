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
        FMFileDragging.install(list,
            () -> filesOf(list.getSelectedValuesList()),
            new IntoNodes(showing) {
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
        FMFileDragging.install(table,
            () -> {
                List<Node> chosen = new ArrayList<>();
                for (int row : table.getSelectedRows()) {
                    Node n = rowAt.apply(table.convertRowIndexToModel(row));
                    if (n != null) chosen.add(n);
                }
                return filesOf(chosen);
            },
            new IntoNodes(showing) {
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

    /* ------------------------------------------------------------------- inside */

    /** A destination that reads a listing: whatever is under the pointer decides. */
    private abstract static class IntoNodes extends FMFileDragging.IntoFolders {
        private final Supplier<File> showing;

        IntoNodes(Supplier<File> showing) { this.showing = showing; }

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
