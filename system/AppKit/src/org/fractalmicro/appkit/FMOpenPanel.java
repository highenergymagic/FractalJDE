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
 * The panel a person opens a document through.
 *
 * The same panel as the one for saving, with the half that names a new file taken out:
 * opening is choosing something that is already there, so there is nothing to type. That
 * is how Cocoa has it too, an open panel being a save panel that does less, and it is why
 * the two look alike from across the room.
 *
 * What a program says is which kinds it can read. The panel shows folders always and files
 * only of those kinds, so a program that opens text does not offer somebody a disk image
 * and then refuse it.
 */
public final class FMOpenPanel extends FMSavePanel {

    private boolean allowsMultipleSelection;
    private boolean canChooseDirectories;
    private boolean canChooseFiles = true;

    public static FMOpenPanel openPanel() { return new FMOpenPanel(); }

    private FMOpenPanel() {
        prompt(FMString.EMPTY);
    }

    /** What the button that goes ahead says. "Open" here rather than "Save". */
    @Override public FMString prompt() {
        FMString said = super.prompt();
        return said.sameAs(org.fractalmicro.foundation.FMLocalized.of(SAVE_BUTTON))
            ? org.fractalmicro.foundation.FMLocalized.of(OPEN_LABEL) : said;
    }

    /*
     * The settings both panels share, answered as an open panel.
     *
     * Java hands back whatever type the method was declared to return, so a chain of these
     * starting on an open panel would end up holding a save panel and refusing to compile.
     * Overriding them narrows the answer to what the caller actually has.
     */
    @Override public FMOpenPanel allowedFileTypes(
            org.fractalmicro.foundation.FMArray<FMString> types) {
        super.allowedFileTypes(types);
        return this;
    }

    @Override public FMOpenPanel directoryURL(org.fractalmicro.foundation.FMURL where) {
        super.directoryURL(where);
        return this;
    }

    @Override public FMOpenPanel prompt(FMString prompt) {
        super.prompt(prompt);
        return this;
    }

    @Override public FMOpenPanel title(FMString title) {
        super.title(title);
        return this;
    }

    @Override public FMOpenPanel message(FMString message) {
        super.message(message);
        return this;
    }

    @Override public FMOpenPanel canCreateDirectories(boolean allowed) {
        super.canCreateDirectories(allowed);
        return this;
    }

    public FMOpenPanel allowsMultipleSelection(boolean allowed) {
        this.allowsMultipleSelection = allowed;
        return this;
    }

    public boolean allowsMultipleSelection() { return allowsMultipleSelection; }

    public FMOpenPanel canChooseDirectories(boolean allowed) {
        this.canChooseDirectories = allowed;
        return this;
    }

    public boolean canChooseDirectories() { return canChooseDirectories; }

    public FMOpenPanel canChooseFiles(boolean allowed) {
        this.canChooseFiles = allowed;
        return this;
    }

    public boolean canChooseFiles() { return canChooseFiles; }
}
