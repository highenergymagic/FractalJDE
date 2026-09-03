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

import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMURL;

/**
 * What Copy puts things on and Paste takes them off.
 *
 * One board, shared with everything on the machine, because that is what copying means:
 * it goes where the next program can find it. Nothing here throws; a board that will not
 * answer has nothing on it. A checking run gets one of its own: {@link #useAPrivateBoard}.
 */
public final class FMPasteboard {

    private static final FMPasteboard GENERAL = new FMPasteboard();

    private FMPasteboard() {}

    /** The board a person's Copy and Paste use. */
    public static FMPasteboard general() { return GENERAL; }

    /**
     * A board of this process's own, put in the general one's place.
     *
     * A checking run copies and pastes for real, and the machine it runs on belongs to
     * somebody who was in the middle of something. Called before any of the checks, the
     * way a checking run also contains its windows.
     */
    public static void useAPrivateBoard() {
        privateBoard = new java.awt.datatransfer.Clipboard("checking");
    }

    /** Whether the general board is the machine's. */
    public static boolean isShared() { return privateBoard == null; }

    private static volatile java.awt.datatransfer.Clipboard privateBoard;

    /** The board in use. Asked each time: a Toolkit with no clipboard throws. */
    private static java.awt.datatransfer.Clipboard board() {
        java.awt.datatransfer.Clipboard mine = privateBoard;
        return mine != null ? mine
                            : java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
    }

    /** Puts text on the board, answering whether it went. */
    public boolean setString(FMString text) {
        try {
            board().setContents(new java.awt.datatransfer.StringSelection(
                text == null ? "" : text.toString()), null);
            return true;
        } catch (Exception nothingDoing) {
            return false;
        }
    }

    /**
     * Puts files on the board, answering whether they went.
     *
     * Files rather than their names. What goes on the board is the list itself, so the
     * program that takes it off gets somewhere to look rather than a line of text it would
     * have to guess the meaning of. On this host that is CF_HDROP, which is what every
     * other program on the machine copies files as, so a copy here pastes into Explorer.
     */
    public boolean setFiles(FMArray<FMURL> files) {
        if (files == null || files.count() == 0) return false;
        java.util.List<java.io.File> list = new java.util.ArrayList<>();
        for (FMURL url : files) if (url != null) list.add(url.asFile());
        try {
            board().setContents(new FileList(list), null);
            return true;
        } catch (Exception nothingDoing) {
            return false;
        }
    }

    /** The files on the board, which is an empty list when there are none. */
    public FMArray<FMURL> files() {
        try {
            return filesIn(board().getContents(null));
        } catch (Exception nothingThere) {
            return FMArray.empty();
        }
    }

    /** Whether there is anything on it that could be pasted as text. */
    public boolean hasText() {
        try {
            return board().isDataFlavorAvailable(
                java.awt.datatransfer.DataFlavor.stringFlavor);
        } catch (Exception nothingThere) {
            return false;
        }
    }

    /** Whether there is anything on it that could be pasted as files. */
    public boolean hasFiles() {
        try {
            return board().isDataFlavorAvailable(
                java.awt.datatransfer.DataFlavor.javaFileListFlavor);
        } catch (Exception nothingThere) {
            return false;
        }
    }

    /**
     * The files in something being pasted or dropped, which is the same question either
     * way: a drag carries what a copy carries, and the answer is read the same.
     */
    @SuppressWarnings("unchecked")
    public static FMArray<FMURL> filesIn(java.awt.datatransfer.Transferable what) {
        if (what == null) return FMArray.empty();
        try {
            if (!what.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) {
                return FMArray.empty();
            }
            java.util.List<java.io.File> given = (java.util.List<java.io.File>)
                what.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
            org.fractalmicro.foundation.FMMutableArray<FMURL> out =
                org.fractalmicro.foundation.FMMutableArray.empty();
            for (java.io.File file : given) if (file != null) out.add(FMURL.of(file));
            return out.asArray();
        } catch (Exception nothingUsable) {
            return FMArray.empty();
        }
    }

    /** Files on their way somewhere, as the host wants them. */
    public static java.awt.datatransfer.Transferable carrying(
            java.util.List<java.io.File> files) {
        return new FileList(new java.util.ArrayList<>(files));
    }

    private record FileList(java.util.List<java.io.File> files)
            implements java.awt.datatransfer.Transferable {
        @Override public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
            return new java.awt.datatransfer.DataFlavor[]{
                java.awt.datatransfer.DataFlavor.javaFileListFlavor};
        }
        @Override public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
            return java.awt.datatransfer.DataFlavor.javaFileListFlavor.equals(flavor);
        }
        @Override public Object getTransferData(java.awt.datatransfer.DataFlavor flavor)
                throws java.awt.datatransfer.UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new java.awt.datatransfer.UnsupportedFlavorException(flavor);
            }
            return files;
        }
    }

    /** The text on the board, or nothing at all when there is none to be had. */
    public FMString string() {
        try {
            Object what = board().getData(java.awt.datatransfer.DataFlavor.stringFlavor);
            return what == null ? FMString.EMPTY : FMString.describing(what);
        } catch (Exception nothingThere) {
            return FMString.EMPTY;
        }
    }
}
