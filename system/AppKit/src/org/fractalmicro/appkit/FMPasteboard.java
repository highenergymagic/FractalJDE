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

import org.fractalmicro.foundation.FMString;

/**
 * What Copy puts things on and Paste takes them off.
 *
 * One board, shared with everything else running on the machine, because that is what a
 * person means by copying: it goes somewhere the next program can find it. Nothing here
 * throws; a board that will not answer is a board with nothing on it.
 */
public final class FMPasteboard {

    private static final FMPasteboard GENERAL = new FMPasteboard();

    private FMPasteboard() {}

    /** The board a person's Copy and Paste use. */
    public static FMPasteboard general() { return GENERAL; }

    /** Puts text on the board, answering whether it went. */
    public boolean setString(FMString text) {
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new java.awt.datatransfer.StringSelection(
                    text == null ? "" : text.toString()), null);
            return true;
        } catch (Exception nothingDoing) {
            return false;
        }
    }

    /** The text on the board, or nothing at all when there is none to be had. */
    public FMString string() {
        try {
            Object what = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .getData(java.awt.datatransfer.DataFlavor.stringFlavor);
            return what == null ? FMString.EMPTY : FMString.describing(what);
        } catch (Exception nothingThere) {
            return FMString.EMPTY;
        }
    }
}
