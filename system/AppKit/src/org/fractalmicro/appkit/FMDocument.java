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

import org.fractalmicro.foundation.FMLocalized;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMURL;

/**
 * What a program is editing: where it came from, and whether it has changed since.
 *
 * NSDocument, and the whole of it is those two facts and what follows from them. What
 * follows is more than it sounds. The window is titled after it. The close button shows a
 * dot rather than a cross while it has changes. Closing it asks a question, and the
 * question names the document. Opening or saving it puts it in the recent items menu. Every
 * program that edits anything needs all five, and every program that hand-rolls them gets
 * four of them slightly wrong.
 *
 * It does not know how to read or write anything. A text editor and a picture editor keep
 * different things in a file and this cannot help with that; what it can do is remember
 * what was last written, so that whether there are changes is a fact rather than a flag
 * somebody has to remember to set.
 */
public final class FMDocument {

    /** What an untitled document is called, before it has been anywhere. */
    private static final FMString UNTITLED = FMString.of("document.untitled");

    /** The questions asked when one is about to be closed with changes in it. */
    private static final FMString SAVE_CHANGES = FMString.of("document.saveChanges");
    private static final FMString SAVE_CHANGES_TO = FMString.of("document.saveChangesTo");
    private static final FMString CHANGES_LOST = FMString.of("document.changesWillBeLost");
    private static final FMString SAVE = FMString.of("document.save");
    private static final FMString DISCARD = FMString.of("document.dontSave");

    private final FMApplication app;
    private FMURL url;
    private FMString written = FMString.EMPTY;

    public FMDocument(FMApplication app) {
        this.app = app;
    }

    /* ------------------------------------------------------------------ where */

    /** Where it came from, or nothing when it has never been anywhere. */
    public FMURL fileURL() { return url; }

    /** What a person calls it: the file's name, or Untitled. */
    public FMString displayName() {
        return url == null ? FMLocalized.of(UNTITLED) : url.lastComponent();
    }

    /* ---------------------------------------------------------------- changed */

    /**
     * Whether what is being edited differs from what was last written.
     *
     * Worked out rather than remembered. A flag set by whatever changes the text is a flag
     * something forgets to set, and the failure is silent in the direction that loses work:
     * a document that says it is unchanged closes without asking.
     */
    public boolean isDocumentEdited(FMString contents) {
        return !contents.sameAs(written);
    }

    /** Says the window is holding changes, which is a dot in its close button. */
    public void showEdited(FMString contents) {
        app.setDocumentEdited(isDocumentEdited(contents));
    }

    /* ------------------------------------------------------- opened and written */

    /**
     * Records that this is now the document, holding exactly these contents.
     *
     * Called after reading and after writing, because both make the same thing true: what
     * is on the disk and what is being edited are the same. Noting it in the recent items
     * is part of that, since the two things a person means by "recent" are what they opened
     * and what they saved.
     */
    public void noteWritten(FMURL where, FMString contents) {
        url = where;
        written = contents == null ? FMString.EMPTY : contents;
        if (where != null) org.fractalmicro.core.Recent.noteItem(where.asFile());
        app.setTitle(displayName());
        app.setDocumentEdited(false);
    }

    /** Starts again with nothing, which is what a new untitled document is. */
    public void noteNew() {
        url = null;
        written = FMString.EMPTY;
        app.setTitle(displayName());
        app.setDocumentEdited(false);
    }

    /* ------------------------------------------------------------- closing it */

    /** What a person chose when asked whether to save. */
    public enum Closing { SAVE, DISCARD, CANCEL }

    /**
     * Asks whether to save, when there is anything to save.
     *
     * Three answers and not two, because one of them loses work and a question with two
     * answers would make cancelling the same as discarding. Answers DISCARD without asking
     * when nothing has changed, which is the ordinary case and not worth a sheet.
     */
    public Closing shouldClose(FMString contents) {
        if (!isDocumentEdited(contents)) return Closing.DISCARD;
        int chosen = app.choose(
            url == null ? FMLocalized.of(SAVE_CHANGES)
                        : FMLocalized.filled(SAVE_CHANGES_TO, displayName()),
            FMLocalized.of(CHANGES_LOST),
            FMLocalized.of(SAVE), FMLocalized.of(DISCARD));
        return switch (chosen) {
            case 0 -> Closing.SAVE;
            case 2 -> Closing.DISCARD;
            default -> Closing.CANCEL;
        };
    }
}
