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
import org.fractalmicro.foundation.FMLocalized;
import org.fractalmicro.foundation.FMMutableArray;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMURL;

import java.io.File;

/**
 * The panel a person saves through.
 *
 * Belongs to the system, not the program. The program says a suggested name, where to
 * start, which types it can write, and what the button says.
 *
 * Two forms, as since Mac OS X shipped. Collapsed: a name and a pop-up of the usual
 * places. Expanded, from the triangle beside the name: a browser with the sidebar, the
 * folders and a button to make one. Which it was last in is remembered.
 *
 * The names are NSSavePanel's: nameFieldStringValue, directoryURL, allowedFileTypes,
 * prompt, message, accessory.
 */
public class FMSavePanel {

    /** What comes back: which button ended it. */
    public static final int OK = 0;
    public static final int CANCELLED = 1;

    private FMString nameFieldStringValue = FMString.EMPTY;
    private FMString nameFieldLabel = FMString.EMPTY;
    private FMString prompt = FMString.EMPTY;
    private FMString title = FMString.EMPTY;
    private FMString message = FMString.EMPTY;
    private FMURL directory;
    private FMArray<FMString> allowedFileTypes = FMArray.empty();
    private boolean canCreateDirectories = true;
    private boolean treatsFilePackagesAsDirectories;

    /**
     * The extra controls a program puts at the bottom of the panel.
     *
     * TextEdit puts the file format there, and every program that can write more than one
     * kind of file puts something like it. It goes in the panel rather than in a second
     * dialog because choosing the name and choosing the kind are one decision.
     */
    private FMArray<FMString> formats = FMArray.empty();
    private FMString formatLabel = FMString.EMPTY;
    private int chosenFormat;

    private FMURL chosen;

    public static FMSavePanel savePanel() { return new FMSavePanel(); }

    /* ------------------------------------------------------------- what to show */

    public FMSavePanel nameFieldStringValue(FMString name) {
        this.nameFieldStringValue = name == null ? FMString.EMPTY : name;
        return this;
    }

    public FMString nameFieldStringValue() { return nameFieldStringValue; }

    /** What the name field is called. "Save As:" when nothing says otherwise. */
    public FMSavePanel nameFieldLabel(FMString label) {
        this.nameFieldLabel = label == null ? FMString.EMPTY : label;
        return this;
    }

    public FMString nameFieldLabel() {
        return nameFieldLabel.isEmpty() ? FMLocalized.of(SAVE_AS_LABEL) : nameFieldLabel;
    }

    /** What the button that goes ahead says. "Save" when nothing says otherwise. */
    public FMSavePanel prompt(FMString prompt) {
        this.prompt = prompt == null ? FMString.EMPTY : prompt;
        return this;
    }

    public FMString prompt() {
        return prompt.isEmpty() ? FMLocalized.of(SAVE_BUTTON) : prompt;
    }

    public FMSavePanel title(FMString title) {
        this.title = title == null ? FMString.EMPTY : title;
        return this;
    }

    public FMString title() { return title; }

    /** A line above the browser, for a panel that has something to explain. */
    public FMSavePanel message(FMString message) {
        this.message = message == null ? FMString.EMPTY : message;
        return this;
    }

    public FMString message() { return message; }

    /** Where it opens. The documents folder when nothing says otherwise. */
    public FMSavePanel directoryURL(FMURL where) {
        this.directory = where;
        return this;
    }

    public FMURL directoryURL() {
        if (directory != null && directory.isDirectory()) return directory;
        return FMURL.of(org.fractalmicro.fs.FS.documents());
    }

    /**
     * The extensions this panel will write, without their dots.
     *
     * Nothing listed means anything is allowed. What it does is add the extension to a name
     * typed without one, which is the behaviour that keeps somebody from making a file the
     * system cannot open by leaving three characters off the end of it.
     */
    public FMSavePanel allowedFileTypes(FMArray<FMString> types) {
        this.allowedFileTypes = types == null ? FMArray.empty() : types;
        return this;
    }

    public FMArray<FMString> allowedFileTypes() { return allowedFileTypes; }

    public FMSavePanel canCreateDirectories(boolean allowed) {
        this.canCreateDirectories = allowed;
        return this;
    }

    public boolean canCreateDirectories() { return canCreateDirectories; }

    public FMSavePanel treatsFilePackagesAsDirectories(boolean asFolders) {
        this.treatsFilePackagesAsDirectories = asFolders;
        return this;
    }

    public boolean treatsFilePackagesAsDirectories() { return treatsFilePackagesAsDirectories; }

    /**
     * The kinds this program can write, shown as a pop-up at the bottom.
     *
     * Given as words a person reads rather than as extensions: somebody choosing what to
     * save knows they want a Word document and does not necessarily know that is .docx.
     */
    public FMSavePanel formats(FMArray<FMString> named, FMString label) {
        this.formats = named == null ? FMArray.empty() : named;
        this.formatLabel = label == null ? FMString.EMPTY : label;
        return this;
    }

    public FMArray<FMString> formats() { return formats; }

    public FMString formatLabel() {
        return formatLabel.isEmpty() ? FMLocalized.of(FILE_FORMAT_LABEL) : formatLabel;
    }

    /** Which of the formats was chosen, as a place in the list given. */
    public int chosenFormat() { return chosenFormat; }

    public FMSavePanel chosenFormat(int which) {
        this.chosenFormat = which;
        return this;
    }

    /** Where the panel was left, once it has been run. */
    public FMURL url() { return chosen; }

    void chose(FMURL where, int format) {
        this.chosen = where;
        this.chosenFormat = format;
    }

    /**
     * Puts the extension on a name that has none.
     *
     * Only when the panel was told which types it writes, and only when the name has no
     * extension at all: somebody who typed one meant it, even if it is not one of the ones
     * offered, and quietly correcting them is how a file ends up called notes.txt.rtf.
     */
    FMURL completing(FMURL where) {
        if (where == null || allowedFileTypes.count() == 0) return where;
        String name = where.lastComponent().toString();
        if (name.contains(".")) return where;
        return where.deletingLastComponent()
                    .appending(FMString.of(name + "." + allowedFileTypes.at(0)));
    }

    /* ---------------------------------------------------------------- running it */

    /**
     * Shows the panel and waits, answering which button ended it.
     *
     * In the process that owns the screen this builds the panel. In a program with a
     * process of its own there is no screen to build it on, so the request goes to the
     * window server and the panel is built there, over the desktop, where the person
     * saving is already looking. Which of the two happened is not the program's concern:
     * {@link FMApplication} decides, because it is the one that knows.
     */
    public int runModal() {
        return FMPanelHost.run(this);
    }

    /* ------------------------------------------------------------------ the words */

    static final FMString SAVE_AS_LABEL = FMString.of("panel.saveAs");
    static final FMString OPEN_LABEL = FMString.of("panel.open");
    static final FMString SAVE_BUTTON = FMString.of("panel.save");
    static final FMString CANCEL_BUTTON = FMString.of("panel.cancel");
    static final FMString WHERE_LABEL = FMString.of("panel.where");
    static final FMString NEW_FOLDER = FMString.of("panel.newFolder");
    static final FMString FILE_FORMAT_LABEL = FMString.of("panel.fileFormat");
    static final FMString REPLACE_QUESTION = FMString.of("panel.replaceQuestion");
    static final FMString REPLACE_WARNING = FMString.of("panel.replaceWarning");
    static final FMString REPLACE_BUTTON = FMString.of("panel.replace");
    static final FMString UNTITLED_FOLDER = FMString.of("panel.untitledFolder");
    static final FMString EXPAND = FMString.of("panel.expand");
    static final FMString COLLAPSE = FMString.of("panel.collapse");
    static final FMString PLACES = FMString.of("panel.places");
    static final FMString FILES = FMString.of("panel.files");
    static final FMString BACK = FMString.of("panel.back");
    static final FMString FORWARD = FMString.of("panel.forward");
    static final FMString SEARCH = FMString.of("panel.search");
    static final FMString AS_ICONS = FMString.of("panel.asIcons");
    static final FMString AS_LIST = FMString.of("panel.asList");
    static final FMString AS_COLUMNS = FMString.of("panel.asColumns");
    static final FMString GO_TO_PROMPT = FMString.of("panel.goToPrompt");
    static final FMString GO_TO_LABEL = FMString.of("panel.goToLabel");
    static final FMString GO_TO_BUTTON = FMString.of("panel.goToButton");
    static final FMString NO_SUCH_FOLDER = FMString.of("panel.noSuchFolder");
    static final FMString CHECK_SPELLING = FMString.of("panel.checkSpelling");

    /** The places the collapsed panel offers, which are the ones a person saves into. */
    static FMArray<FMURL> usualPlaces() {
        FMMutableArray<FMURL> out = FMMutableArray.empty();
        for (File one : new File[]{org.fractalmicro.fs.FS.documents(),
                                   org.fractalmicro.fs.FS.desktopFolder(),
                                   org.fractalmicro.fs.FS.downloads(),
                                   org.fractalmicro.fs.FS.home()}) {
            if (one != null && one.isDirectory()) out.add(FMURL.of(one));
        }
        return out.asArray();
    }
}
