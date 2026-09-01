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
 * The things a text view can be asked to do to itself.
 *
 * A program sends one of these names and the view does the work, because the text is in
 * the view and the view is in another process. What the names are matters: they have to be
 * the ones the view already answers to, or the command arrives and nothing happens.
 *
 * They are published here rather than left for each program to spell, so that a program
 * names this system's vocabulary and not the toolkit's. A program that wrote the toolkit's
 * names would be a program that had to change when the toolkit did, which is the whole
 * thing a framework is for.
 */
public final class FMTextAction {
    private FMTextAction() {}

    /* -------------------------------------------------------------- editing */

    public static final FMString UNDO = FMString.of("undo");
    public static final FMString REDO = FMString.of("redo");
    public static final FMString CUT =
        FMString.of(javax.swing.text.DefaultEditorKit.cutAction);
    public static final FMString COPY =
        FMString.of(javax.swing.text.DefaultEditorKit.copyAction);
    public static final FMString PASTE =
        FMString.of(javax.swing.text.DefaultEditorKit.pasteAction);
    public static final FMString SELECT_ALL =
        FMString.of(javax.swing.text.DefaultEditorKit.selectAllAction);

    /* ------------------------------------------------------------ styling */

    public static final FMString BOLD = FMString.of("font-bold");
    public static final FMString ITALIC = FMString.of("font-italic");
    public static final FMString UNDERLINE = FMString.of("font-underline");

    /* ---------------------------------------------------------- alignment */

    public static final FMString ALIGN_LEFT = FMString.of("left-justify");
    public static final FMString CENTER = FMString.of("center-justify");
    public static final FMString ALIGN_RIGHT = FMString.of("right-justify");
}
