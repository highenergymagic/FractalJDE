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
package org.fractalmicro.foundation;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * What to do to put something back, and what to call it.
 *
 * NSUndoManager, and the shape is the interesting part: nothing here records what happened.
 * What is registered is the way back. Something about to rename a file registers the rename
 * that would undo it, and a manager holding a stack of those can walk backwards without
 * knowing what any of them were for.
 *
 * That is why the name is registered with it. "Undo" on its own is a menu item that gives a
 * person no idea what is about to change; "Undo Rename" is a promise. Cocoa has said so in
 * its menus since the beginning, and a manager that could not name its own actions would
 * make that impossible for every program at once.
 *
 * Redo is the same stack read the other way. While an undo is running, anything registered
 * goes on the redo stack rather than the undo one, which is how one method serves both: the
 * way back from the way back is the way forward, and nothing has to be written twice.
 */
public final class FMUndoManager {

    /** One step back: what to do, and what a menu should call it. */
    private record Step(FMString name, Runnable how) { }

    /**
     * How many steps are kept.
     *
     * A stack that grew without bound would hold every file operation of a session, and
     * with them everything each one closed over. Cocoa's has no fixed limit and relies on
     * documents being closed; the Finder's undo belongs to no document and is never closed,
     * so it has one here.
     */
    public static final int LEVELS_OF_UNDO = 32;

    private final Deque<Step> undo = new ArrayDeque<>();
    private final Deque<Step> redo = new ArrayDeque<>();
    private boolean undoing;
    private boolean redoing;

    /**
     * Registers the way back from something about to be done.
     *
     * Called by whatever is doing it, before or after, but with everything it needs already
     * decided: the way back cannot be worked out later, because by then the thing it would
     * have been worked out from has changed.
     */
    public synchronized void registerUndo(FMString actionName, Runnable how) {
        if (how == null) return;
        Step step = new Step(actionName == null ? FMString.EMPTY : actionName, how);
        if (undoing) {
            redo.push(step);
            return;
        }
        undo.push(step);
        // Anything registered outside an undo is a new thing done, and a new thing done
        // makes the forward history untrue. Keeping it would offer to redo a change that
        // no longer follows from what is there.
        if (!redoing) redo.clear();
        while (undo.size() > LEVELS_OF_UNDO) undo.removeLast();
    }

    public synchronized boolean canUndo() { return !undo.isEmpty(); }
    public synchronized boolean canRedo() { return !redo.isEmpty(); }

    /** What the next step back is called, or nothing when there is not one. */
    public synchronized FMString undoActionName() {
        return undo.isEmpty() ? FMString.EMPTY : undo.peek().name();
    }

    public synchronized FMString redoActionName() {
        return redo.isEmpty() ? FMString.EMPTY : redo.peek().name();
    }

    /**
     * Takes one step back, and answers whether there was one to take.
     *
     * What it runs is expected to register the way forward, which is why the flag is set
     * while it runs. A step that registers nothing is a step that cannot be redone, which
     * is honest rather than broken: not everything that can be undone can be done again.
     */
    public boolean undo() {
        Step step;
        synchronized (this) {
            if (undo.isEmpty()) return false;
            step = undo.pop();
            undoing = true;
        }
        try {
            step.how().run();
        } finally {
            synchronized (this) { undoing = false; }
        }
        return true;
    }

    /** And one step forward. */
    public boolean redo() {
        Step step;
        synchronized (this) {
            if (redo.isEmpty()) return false;
            step = redo.pop();
            redoing = true;
        }
        try {
            step.how().run();
        } finally {
            synchronized (this) { redoing = false; }
        }
        return true;
    }

    /** Forgets everything, which is what closing whatever this belonged to means. */
    public synchronized void removeAllActions() {
        undo.clear();
        redo.clear();
    }
}
