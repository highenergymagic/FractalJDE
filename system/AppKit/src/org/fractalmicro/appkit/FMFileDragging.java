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
package org.fractalmicro.appkit;

import org.fractalmicro.foundation.FMURL;

import javax.swing.JComponent;
import javax.swing.TransferHandler;
import java.awt.Point;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Dragging files out of a view, and dropping them into one.
 *
 * Two questions, neither about how the view draws: which files are under the pointer when
 * a drag starts, and what would happen if it ended where it is now.
 *
 * A destination answers with an operation rather than a place, as in Cocoa. The Trash
 * takes a drop and is not a folder; a sidebar takes one and makes a shortcut rather than
 * moving anything. {@link IntoFolders} covers the ordinary kind.
 *
 * The answer is worked out while the mouse is down and shown on the pointer, so a refusal
 * arrives in time to be heeded.
 */
public class FMFileDragging extends TransferHandler {

    /** A view something can be dragged out of. */
    public interface Source {
        /** The files a drag starting now would carry, empty for a drag that should not start. */
        List<File> filesToDrag();

        /**
         * What is drawn under the pointer while the drag is going, and where the pointer is
         * in it.
         *
         * A Mac drags ghosts of the things being carried, where they were when they were
         * picked up. Swing offers a plain rectangle, which says only that something is
         * happening. The offset is where in the picture the pointer is, so the icons stay
         * under the finger that picked them up.
         */
        default java.awt.Image pictureOfTheDrag() { return null; }
        default Point pointerInThePicture() { return new Point(0, 0); }
    }

    /** A view something can be dropped into. */
    public interface Destination {
        /**
         * What letting go here would do, and NONE for a drop that will not be taken. Asked
         * for every twitch of the pointer, so it should do no work that would be felt.
         */
        FMDragOperation operationAt(Point where, List<File> files, int keysHeld);

        /** Does it, answering whether anything happened. */
        boolean take(Point where, List<File> files, FMDragOperation how);

        /**
         * Told when a drag arrives over the view and when it leaves again, so the view can
         * show that letting go here would do something.
         *
         * Swing has nowhere to put this: a transfer handler is asked whether it would take
         * a drop and never told the pointer has left, so a view that lit up would stay lit.
         * The drop target underneath does know, and {@link #install} listens to it.
         */
        default void aimedAt(boolean yes) {}

        /**
         * What resting the drag here would open, or nothing where resting does nothing.
         *
         * A spring-loaded folder. Filing something three levels down would otherwise mean
         * putting it somewhere, opening the way down and picking it up again, all while
         * holding something you cannot put down.
         */
        default File wouldSpringOpen(Point where) { return null; }

        /** Opens it, with the drag still going. */
        default void springOpen(File folder) { }

        /**
         * Puts back whatever springing opened, once the drag is over.
         *
         * Called however the drag ended: dropped here, dropped elsewhere, or given up on.
         * Without it a drag through four folders leaves four windows open, which is the
         * thing spring loading exists to avoid.
         */
        default void springBack() { }
    }

    /**
     * The ordinary kind of destination: a view of folders, where a drop goes into whichever
     * one is under the pointer, or into the folder the view is showing when none is.
     *
     * The refusals are here because they are the same everywhere and getting them wrong
     * loses files. A folder cannot go into itself or anything inside it, which would move
     * it out from under the path saying where it was going.
     */
    public abstract static class IntoFolders implements Destination {

        /** The folder a drop at this point goes into, or nothing at all. */
        protected abstract File folderAt(Point where);

        /** Does it. */
        protected abstract boolean receive(List<File> files, File into, FMDragOperation how);

        /** Whether the folder will take a drop at all: a read-only one says no. */
        protected boolean canTake(File into) {
            return into != null && into.isDirectory() && into.canWrite();
        }

        @Override public FMDragOperation operationAt(Point where, List<File> files, int keysHeld) {
            File into = folderAt(where);
            if (files.isEmpty() || !canTake(into)) return FMDragOperation.NONE;
            for (File file : files) if (!makesSense(file, into)) return FMDragOperation.NONE;

            FMDragOperation how = FMDragOperation.forDrop(keysHeld, files.get(0), into);
            if (how == FMDragOperation.MOVE && allAlreadyIn(files, into)) return FMDragOperation.NONE;
            return how;
        }

        @Override public boolean take(Point where, List<File> files, FMDragOperation how) {
            return receive(files, folderAt(where), how);
        }

        private static boolean allAlreadyIn(List<File> files, File folder) {
            for (File file : files) {
                File parent = file.getParentFile();
                if (parent == null || !parent.equals(folder)) return false;
            }
            return true;
        }

        private static boolean makesSense(File file, File into) {
            if (file == null || into == null) return false;
            if (file.equals(into)) return false;
            try {
                java.nio.file.Path source = file.getCanonicalFile().toPath();
                java.nio.file.Path target = into.getCanonicalFile().toPath();
                return !target.startsWith(source);
            } catch (java.io.IOException cannotTell) {
                return false;
            }
        }
    }

    private final Source source;
    private final Destination destination;

    /**
     * Puts dragging on a view. Either half may be left out: a view files can only be taken
     * out of passes no destination, and one they can only be put into passes no source.
     */
    public static void install(JComponent view, Source from, Destination to) {
        view.setTransferHandler(new FMFileDragging(from, to));
        if (from != null) {
            if (view instanceof javax.swing.JList<?> list) list.setDragEnabled(true);
            else if (view instanceof javax.swing.JTable table) table.setDragEnabled(true);
            else if (view instanceof javax.swing.JTree tree) tree.setDragEnabled(true);
        }
        if (to != null) {
            // On an item, not between them. A file manager has no order to insert into: the
            // listing is sorted, and a gap between two rows of it is not a place a file can
            // be put.
            if (view instanceof javax.swing.JList<?> list) {
                list.setDropMode(javax.swing.DropMode.ON);
            } else if (view instanceof javax.swing.JTable table) {
                table.setDropMode(javax.swing.DropMode.ON);
            }
            watchForTheDrag(view, to);
        }
    }

    /**
     * Tells the destination when a drag is over it.
     *
     * Setting a transfer handler makes Swing put a drop target on the view, and a listener
     * on that is the only place the arriving and the leaving are both heard. Nothing here
     * consumes the events: Swing's own listener is what performs the drop.
     */
    private static void watchForTheDrag(JComponent view, Destination to) {
        java.awt.dnd.DropTarget target = view.getDropTarget();
        if (target == null) return;
        try {
            FMFileDragging handler = (FMFileDragging) view.getTransferHandler();
            target.addDropTargetListener(new java.awt.dnd.DropTargetAdapter() {
                @Override public void dragEnter(java.awt.dnd.DropTargetDragEvent e) {
                    to.aimedAt(true);
                }
                @Override public void dragExit(java.awt.dnd.DropTargetEvent e) {
                    to.aimedAt(false);
                    handler.stopWaiting();
                    to.springBack();
                }
                // After Swing's own listener, which is added first and is the one that
                // performs the drop. By here the files have landed, so what springing
                // opened can be put back.
                @Override public void drop(java.awt.dnd.DropTargetDropEvent e) {
                    to.aimedAt(false);
                    handler.stopWaiting();
                    to.springBack();
                }
            });
        } catch (java.util.TooManyListenersException alreadyWatched) {
            // One is enough, and a view set up twice is a view somebody wired twice.
        }
    }

    /** What answers for this view: what a drop would do, and what resting would open. */
    public Destination destination() { return destination; }

    /** And what it hands over when a drag starts. */
    public Source source() { return source; }

    protected FMFileDragging(Source source, Destination destination) {
        this.source = source;
        this.destination = destination;
    }

    /* ------------------------------------------------------------------ out of */

    @Override public int getSourceActions(JComponent view) {
        return source == null ? NONE : COPY_OR_MOVE | LINK;
    }

    @Override protected java.awt.datatransfer.Transferable createTransferable(JComponent view) {
        if (source == null) return null;
        List<File> files = source.filesToDrag();
        if (files == null || files.isEmpty()) return null;
        // Asked for here, at the one moment it is true: the picture is of what is being
        // carried, and what is being carried is not settled until the drag begins.
        java.awt.Image picture = source.pictureOfTheDrag();
        if (picture != null) {
            setDragImage(picture);
            setDragImageOffset(source.pointerInThePicture());
        }
        return FMPasteboard.carrying(files);
    }

    /**
     * What is left to do once the drop has happened, which here is nothing.
     *
     * The destination moved the files itself, because it is the only one that knows where
     * they went and so the only one that can register the way back. A source that deleted
     * them as well would delete them a second time, from wherever they now are.
     */
    @Override protected void exportDone(JComponent view, java.awt.datatransfer.Transferable what,
                                        int action) {
    }

    /* -------------------------------------------------------------------- into */

    @Override public boolean canImport(TransferSupport support) {
        if (destination == null || !support.isDrop()) return false;
        FMDragOperation how = whatWouldHappen(support);
        // Asked on every twitch of the pointer, which is what makes it the place to notice
        // that the pointer has stopped twitching.
        watchForResting(destination.wouldSpringOpen(pointOf(support)));
        if (how != FMDragOperation.NONE) support.setDropAction(how.asSwing());
        return how != FMDragOperation.NONE;
    }

    @Override public boolean importData(TransferSupport support) {
        if (destination == null || !support.isDrop()) return false;
        stopWaiting();
        FMDragOperation how = whatWouldHappen(support);
        if (how == FMDragOperation.NONE) return false;
        return destination.take(pointOf(support), filesIn(support), how);
    }

    /* ------------------------------------------------------- resting on a folder */

    /**
     * How often the wait is looked at.
     *
     * Often enough that the space bar feels immediate and rarely enough to cost nothing. A
     * tick rather than one alarm at the end of the wait, because the space bar can be
     * pressed at any point and while the mouse is down the keyboard belongs to the drag.
     */
    private static final int TICK_MILLIS = 50;

    private javax.swing.Timer resting;
    private File restingOn;
    private long restingSince;

    /**
     * Notices that the pointer has stopped over something that would open.
     *
     * The clock starts again every time the answer changes, so crossing four folders on the
     * way to a fifth opens none: what counts is resting, and moving off something and back
     * onto it is not resting on it.
     */
    private void watchForResting(File wouldOpen) {
        if (java.util.Objects.equals(wouldOpen, restingOn)) return;
        restingOn = wouldOpen;
        stopWaiting();
        if (wouldOpen == null || !org.fractalmicro.os.FinderSettings.springLoaded()) return;
        long waitFor = Math.round(org.fractalmicro.os.FinderSettings.springDelay() * 1000);
        restingSince = System.currentTimeMillis();
        resting = new javax.swing.Timer(TICK_MILLIS, e -> {
            boolean asked = org.fractalmicro.win.User32.isKeyDown(
                org.fractalmicro.win.User32.VK_SPACE);
            if (!asked && System.currentTimeMillis() - restingSince < waitFor) return;
            stopWaiting();
            File open = restingOn;
            if (open != null) destination.springOpen(open);
        });
        resting.start();
    }

    private void stopWaiting() {
        if (resting != null) resting.stop();
        resting = null;
    }

    private FMDragOperation whatWouldHappen(TransferSupport support) {
        List<File> files = filesIn(support);
        if (files.isEmpty()) return FMDragOperation.NONE;
        return destination.operationAt(pointOf(support), files, keysHeld());
    }

    private static Point pointOf(TransferSupport support) {
        DropLocation where = support.getDropLocation();
        return where == null ? new Point(0, 0) : where.getDropPoint();
    }

    /**
     * The keys held down.
     *
     * Read from the event being handled rather than remembered from the last one the view
     * saw, because during a drag the view is receiving nothing: the pointer belongs to the
     * drag until it ends.
     */
    private static int keysHeld() {
        java.awt.AWTEvent event = java.awt.EventQueue.getCurrentEvent();
        return event instanceof java.awt.event.InputEvent input ? input.getModifiersEx() : 0;
    }

    private static List<File> filesIn(TransferSupport support) {
        List<File> out = new ArrayList<>();
        for (FMURL url : FMPasteboard.filesIn(support.getTransferable())) out.add(url.asFile());
        return out;
    }
}
