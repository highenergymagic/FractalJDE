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

import java.awt.event.InputEvent;
import java.io.File;

/**
 * What letting go of a drag would do.
 *
 * Within one disk a drag moves; across two it copies. The keys held down say otherwise,
 * and they are the same three everywhere in the system:
 *
 *   nothing      move on one disk, copy across two
 *   Option       copy, wherever it is going
 *   Command      move, wherever it is going
 *   both         make an alias to it and leave it where it is
 *
 * The pointer says which of these is about to happen, so a person can change their mind
 * while still holding the mouse down. That is the whole reason the answer is worked out
 * before the drop rather than at it.
 */
public enum FMDragOperation {
    /** Nothing would happen; the drop is refused. */
    NONE,
    /** The files are copied, and the ones dragged stay where they were. */
    COPY,
    /** The files are moved, and are no longer where they came from. */
    MOVE,
    /** An alias to each is made in the destination. */
    LINK;

    /**
     * What a drop would do, from the keys held and where it is going.
     *
     * The volume is asked about rather than the path, because "the same disk" is a question
     * about what a rename can do: within one volume a move is a rename and is instant,
     * across two it is a copy and a delete however it is dressed up.
     */
    public static FMDragOperation forDrop(int modifiers, File from, File to) {
        boolean option = (modifiers & InputEvent.ALT_DOWN_MASK) != 0;
        boolean command = (modifiers & InputEvent.META_DOWN_MASK) != 0
                       || (modifiers & InputEvent.CTRL_DOWN_MASK) != 0;
        if (option && command) return LINK;
        if (option) return COPY;
        if (command) return MOVE;
        return sameVolume(from, to) ? MOVE : COPY;
    }

    /**
     * Whether two paths are on one volume.
     *
     * Answered by asking the file store rather than by comparing the first letter of the
     * path, which is right on this host by accident and wrong wherever a folder is a
     * mounted disk of its own.
     */
    public static boolean sameVolume(File one, File other) {
        if (one == null || other == null) return false;
        try {
            java.nio.file.FileStore a = java.nio.file.Files.getFileStore(nearest(one).toPath());
            java.nio.file.FileStore b = java.nio.file.Files.getFileStore(nearest(other).toPath());
            return a.equals(b);
        } catch (Exception cannotTell) {
            // A disk that will not say is treated as another one, so the files are copied.
            // Copying when it should have moved leaves the original where it was, which a
            // person can undo by hand; moving when it should have copied cannot be undone
            // by hand if the copy went wrong halfway.
            return false;
        }
    }

    /** The nearest part of a path that exists, since a file about to be made does not. */
    private static File nearest(File file) {
        File at = file;
        while (at != null && !at.exists()) at = at.getParentFile();
        return at == null ? file : at;
    }

    /** What the pointer shows, in the numbers Swing speaks. */
    public int asSwing() {
        return switch (this) {
            case COPY, LINK -> javax.swing.TransferHandler.COPY;
            case MOVE -> javax.swing.TransferHandler.MOVE;
            case NONE -> javax.swing.TransferHandler.NONE;
        };
    }
}
