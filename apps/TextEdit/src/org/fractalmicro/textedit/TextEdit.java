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
package org.fractalmicro.textedit;

import org.fractalmicro.appkit.FMAlert;
import org.fractalmicro.appkit.FMApplication;
import org.fractalmicro.appkit.FMTextAction;
import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMData;
import org.fractalmicro.foundation.FMLog;
import org.fractalmicro.foundation.FMMutableArray;
import org.fractalmicro.appkit.FMOpenPanel;
import org.fractalmicro.appkit.FMSavePanel;
import org.fractalmicro.foundation.FMLocalized;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMURL;

/**
 * TextEdit: a document, in a process of its own.
 *
 * The document is a control in a window the window server owns, and everything that
 * happens to the text happens there: cut, paste, bold, centre. This program does not touch
 * it. It sends the command and the view does the work, which is what a program has always
 * done on a Mac even when both were in the same address space. The menu item does not
 * edit anything; it sends an action down the responder chain until something that knows
 * what "bold" means catches it. Here the chain runs across a process boundary, and the
 * only difference that makes is that the two ends are separately survivable.
 *
 * What this program does itself is the part that is actually its own: reading and writing
 * files, remembering what was opened, deciding what a document is called and whether it
 * has been edited, and finding text in it.
 */
public final class TextEdit implements org.fractalmicro.appkit.FMApplicationDelegate {

    public static final FMString NAME = FMString.of("TextEdit");

    /** The interface file this program opens, inside its own bundle. */
    private static final FMString INTERFACE = FMString.of("Document");

    /** The find panel, which is its own window and so its own file. */
    private static final FMString FIND_PANEL = FMString.of("Find");

    /** The one control a document window has, and the panels' few. */
    private static final FMString BODY = FMString.of("body");
    private static final FMString FIND_FIELD = FMString.of("find");
    private static final FMString REPLACE_FIELD = FMString.of("replace");

    /* ------------------------------------------------------- what the menus send */

    private static final FMString NEW = FMString.of("new");
    private static final FMString OPEN = FMString.of("open");
    private static final FMString SAVE = FMString.of("save");
    private static final FMString SAVE_AS = FMString.of("save as");
    private static final FMString REVERT = FMString.of("revert");
    private static final FMString CLOSE = FMString.of("close");
    private static final FMString SHOW_FIND = FMString.of("show find");
    private static final FMString FIND_NEXT = FMString.of("find next");
    private static final FMString FIND_PREVIOUS = FMString.of("find previous");
    private static final FMString REPLACE_ALL = FMString.of("replace all");
    private static final FMString USE_SELECTION = FMString.of("use selection");
    /** What separates one line from the next, said once. */
    private static final char NEWLINE = '\n';

    private static final FMString SELECT_LINE = FMString.of("select line");
    private static final FMString JUMP_TO_SELECTION = FMString.of("jump to selection");
    private static final FMString DUPLICATE = FMString.of("duplicate");

    /**
     * The editing commands, and the name each one has in a text view.
     *
     * These are not invented. They are the actions a styled text view has always had, and
     * naming them here rather than reimplementing them means Cut behaves the way Cut
     * behaves everywhere, including for the selections nobody thinks to test.
     */
    public record Command(FMString sends, FMString title, FMString key, FMString action) {}

    /** The editing and formatting commands, for anything that wants to check them. */
    public static FMArray<Command> commands() {
        FMMutableArray<Command> out = FMMutableArray.empty();
        for (Command one : EDITING) out.add(one);
        for (Command one : FORMATTING) out.add(one);
        return out.asArray();
    }

    private static Command edit(String sends, String title, String key, FMString action) {
        return new Command(FMString.of(sends), FMString.of(title), FMString.of(key), action);
    }

    private static final Command[] EDITING = {
        edit("undo", "Undo", "z", FMTextAction.UNDO),
        edit("redo", "Redo", "Z", FMTextAction.REDO),
        edit("cut", "Cut", "x", FMTextAction.CUT),
        edit("copy", "Copy", "c", FMTextAction.COPY),
        edit("paste", "Paste", "v", FMTextAction.PASTE),
        edit("select all", "Select All", "a", FMTextAction.SELECT_ALL),
    };

    private static final Command[] FORMATTING = {
        edit("bold", "Bold", "b", FMTextAction.BOLD),
        edit("italic", "Italic", "i", FMTextAction.ITALIC),
        edit("underline", "Underline", "u", FMTextAction.UNDERLINE),
        edit("align left", "Align Left", "{", FMTextAction.ALIGN_LEFT),
        edit("center", "Center", "|", FMTextAction.CENTER),
        edit("align right", "Align Right", "}", FMTextAction.ALIGN_RIGHT),
    };

    /* ---------------------------------------------------------------- the document */

    private final FMApplication app = FMApplication.sharedApplication();

    /** Where the document came from, and whether it has been changed since. */
    /**
     * What is being edited: where it came from, and whether it has changed since.
     *
     * The two facts this program used to keep in two fields of its own, and the five things
     * that follow from them, which it used to arrange for itself and now does not: the
     * title, the dot in the close button, the question when it is closed, and the entry in
     * the recent items.
     */
    private final org.fractalmicro.appkit.FMDocument document =
        new org.fractalmicro.appkit.FMDocument(FMApplication.sharedApplication());
    private FMApplication.FMWindow findPanel = new FMApplication.FMWindow(-1);

    /** Opened with nothing, which is a new untitled document. */
    @Override public void open() { run(null); }

    /** Opened on a document, which is what dropping one on the icon means. */
    @Override public void openURLs(org.fractalmicro.foundation.FMArray<FMURL> urls) {
        run(urls == null || urls.count() == 0 ? null : urls.at(0));
    }

    private void run(FMURL opening) {
        if (!app.showWindow(INTERFACE)) {
            FMLog.say(FMString.of("the window would not open: ")
                              .appending(app.lastError().description()));
            return;
        }

        for (Command one : EDITING) app.on(one.sends(), e -> app.perform(BODY, one.action()));
        for (Command one : FORMATTING) app.on(one.sends(), e -> app.perform(BODY, one.action()));

        app.on(NEW, e -> newDocument());
        app.on(OPEN, e -> openNamed());
        app.on(SAVE, e -> save());
        app.on(SAVE_AS, e -> saveAs());
        app.on(REVERT, e -> revert());
        app.on(SHOW_FIND, e -> showFind());
        app.on(FIND_NEXT, e -> find(true));
        app.on(FIND_PREVIOUS, e -> find(false));
        app.on(REPLACE_ALL, e -> replaceAll());
        app.on(USE_SELECTION, e -> useSelection());
        app.on(SELECT_LINE, e -> selectLine());
        app.on(JUMP_TO_SELECTION, e -> jumpToSelection());
        app.on(DUPLICATE, e -> duplicate());
        app.on(CLOSE, e -> closeDocument());
        app.on(FMString.of("quit"), e -> closeDocument());

        if (opening != null) open(opening);
    }

    /* ------------------------------------------------------------------ documents */

    private void newDocument() {
        app.setValue(BODY, FMString.EMPTY);
        document.noteNew();
    }

    /**
     * Opens a document, through the panel every program opens through.
     *
     * The panel is the system's and is drawn by the window server: this program has a
     * process of its own and no screen in it. All this says is which kinds it can read, so
     * that the browser does not offer somebody a disk image and then refuse it.
     */
    private void openNamed() {
        FMURL where = app.runOpenPanel(FMOpenPanel.openPanel()
            .allowedFileTypes(readableTypes())
            .directoryURL(lastPlace()));
        if (where == null) return;
        open(where);
    }

    private void open(FMURL where) {
        FMData held = FMData.withContentsOf(where);
        if (held == null) {
            app.tell(FMLocalized.of(OPEN_FAILED), FMLocalized.of(OPEN_FAILED_WHY));
            return;
        }
        app.setValue(BODY, held.asString());
        document.noteWritten(where, held.asString());
        Settings.rememberRecent(where);
    }

    private boolean save() {
        if (document.fileURL() == null) return saveAs();
        return writeTo(document.fileURL());
    }

    /**
     * Saves under a chosen name, through the panel every program saves through.
     *
     * The format goes in the panel rather than in a dialog after it, because choosing what
     * to call a document and choosing what kind it is are one decision: somebody who picks
     * Plain Text expects the name to end in .txt, and the panel is where they can see both.
     */
    private boolean saveAs() {
        FMSavePanel panel = FMSavePanel.savePanel()
            .nameFieldStringValue(document.fileURL() != null
                                  ? document.fileURL().lastComponent()
                                  : FMLocalized.of(UNTITLED_DOCUMENT))
            .directoryURL(lastPlace())
            .formats(FORMAT_NAMES, FMString.EMPTY)
            .chosenFormat(format)
            .allowedFileTypes(typesFor(format));

        FMURL where = app.runSavePanel(panel);
        if (where == null) return false;
        format = panel.chosenFormat();
        return writeTo(where);
    }

    /**
     * The kinds TextEdit writes, in the order Mac OS X offers them.
     *
     * Only the two it can really write are offered. A pop-up listing every format the
     * program on a Mac can produce would be a pop-up where most of the choices do nothing,
     * which is worse than a short list that is true.
     */
    private static final FMArray<FMString> FORMAT_NAMES = names(
        "textedit.formatRichText", "textedit.formatPlainText");

    private static final String[] FORMAT_EXTENSIONS = {"rtf", "txt"};

    private int format;

    private static FMArray<FMString> names(String... keys) {
        FMMutableArray<FMString> out = FMMutableArray.empty();
        for (String key : keys) out.add(FMLocalized.of(FMString.of(key)));
        return out.asArray();
    }

    private static FMArray<FMString> typesFor(int format) {
        FMMutableArray<FMString> out = FMMutableArray.empty();
        out.add(FMString.of(FORMAT_EXTENSIONS[Math.max(0,
            Math.min(format, FORMAT_EXTENSIONS.length - 1))]));
        return out.asArray();
    }

    /** Everything TextEdit will open, so the panel shows those and not the rest. */
    private static FMArray<FMString> readableTypes() {
        FMMutableArray<FMString> out = FMMutableArray.empty();
        for (String one : new String[]{"rtf", "txt", "text", "md", "markdown", "log"}) {
            out.add(FMString.of(one));
        }
        return out.asArray();
    }

    /**
     * Where the panel opens: beside the document being edited, or the documents folder.
     *
     * Somebody saving a second time nearly always means the same folder as the first, and
     * a panel that starts somewhere else makes them navigate back to where they already
     * were.
     */
    private FMURL lastPlace() {
        FMURL file = document.fileURL();
        if (file != null) {
            FMURL folder = file.deletingLastComponent();
            if (folder.isDirectory()) return folder;
        }
        return org.fractalmicro.foundation.FMFileManager.defaultManager().documents();
    }

    private boolean writeTo(FMURL where) {
        FMString text = app.valueOf(BODY);
        if (!FMData.of(text).writeTo(where)) {
            app.tell(FMLocalized.of(SAVE_FAILED), FMLocalized.of(SAVE_FAILED_WHY));
            return false;
        }
        document.noteWritten(where, text);
        Settings.rememberRecent(where);
        return true;
    }

    private void revert() {
        FMURL file = document.fileURL();
        if (file == null) return;
        boolean go = app.confirm(FMLocalized.filled(REVERT_QUESTION, file.lastComponent()),
                                 FMLocalized.of(REVERT_WARNING),
                                 FMLocalized.of(REVERT_BUTTON));
        if (go) open(file);
    }

    /**
     * Closes the document, asking first when there is something to lose.
     *
     * What was saved is kept here so that the question can be asked honestly: a program
     * that asked every time would train somebody to say yes without reading it.
     */
    private void closeDocument() {
        switch (document.shouldClose(app.valueOf(BODY))) {
            case CANCEL -> { return; }
            case SAVE -> { if (!save()) return; }
            case DISCARD -> { }
        }
        app.stop();
    }

    /**
     * Selects a line by number, counting from one as a person does.
     *
     * Where a line starts is a matter of counting newlines, which this program can do
     * because it has the text; what it cannot do is move the caret, so what goes back is
     * the range and the view does the moving.
     */
    private void selectLine() {
        FMString asked = app.ask(FMLocalized.of(GO_TO_LINE), FMLocalized.of(LINE_LABEL),
                                 FMString.of("1"), FMLocalized.of(SELECT_BUTTON));
        if (asked.isBlank()) return;
        int wanted;
        try {
            wanted = Integer.parseInt(asked.trimmed().toString());
        } catch (NumberFormatException notANumber) {
            return;
        }
        String text = app.valueOf(BODY).toString();
        int at = 0;
        for (int line = 1; line < wanted; line++) {
            int next = text.indexOf(NEWLINE, at);
            if (next < 0) return;
            at = next + 1;
        }
        int end = text.indexOf(NEWLINE, at);
        app.select(BODY, at, end < 0 ? text.length() : end);
    }

    /** Scrolls back to what is selected, which is what a view does when asked again. */
    private void jumpToSelection() {
        FMApplication.Selection chosen = app.selectionIn(BODY);
        app.select(BODY, chosen.from(), chosen.to());
    }

    /**
     * Makes another document holding the same text.
     *
     * It is not saved anywhere yet, which is what duplicating means: the copy is the one
     * being worked on and has not been given a name.
     */
    private void duplicate() {
        FMString text = app.valueOf(BODY);
        document.noteNew();
        app.setValue(BODY, text);
        // A copy nobody has saved is a copy with changes in it, and its close button
        // says so from the moment it exists.
        document.showEdited(text);
    }

    /* ---------------------------------------------------------------- finding */

    private void showFind() {
        if (findPanel.isOpen()) return;
        findPanel = app.openAnother(FIND_PANEL);
    }

    /**
     * Finds the next occurrence, or the one before.
     *
     * The text comes here, the search happens here, and what goes back is where to put the
     * selection. The view has the text and the screen; which stretch of it matters is the
     * program's business.
     */
    private void find(boolean forwards) {
        FMString wanted = findPanel.isOpen()
            ? app.valueOf(findPanel, FIND_FIELD) : FMString.EMPTY;
        if (wanted.isBlank()) {
            showFind();
            return;
        }
        String text = app.valueOf(BODY).toString();
        String needle = wanted.toString();
        FMApplication.Selection at = app.selectionIn(BODY);
        int found = forwards
            ? text.indexOf(needle, Math.min(at.to(), text.length()))
            : text.lastIndexOf(needle, Math.max(0, at.from() - 1));
        // Round the ends, which is what every find in every editor has done since they
        // stopped simply stopping.
        if (found < 0) found = forwards ? text.indexOf(needle) : text.lastIndexOf(needle);
        if (found < 0) return;
        app.select(BODY, found, found + needle.length());
    }

    private void replaceAll() {
        if (!findPanel.isOpen()) { showFind(); return; }
        FMString wanted = app.valueOf(findPanel, FIND_FIELD);
        if (wanted.isBlank()) return;
        FMString with = app.valueOf(findPanel, REPLACE_FIELD);
        FMString text = app.valueOf(BODY);
        app.setValue(BODY, FMString.of(
            text.toString().replace(wanted.toString(), with.toString())));
    }

    /** Uses what is selected as what to look for, which saves typing it again. */
    private void useSelection() {
        FMApplication.Selection chosen = app.selectionIn(BODY);
        if (chosen.isEmpty()) return;
        showFind();
        app.setValue(findPanel, FIND_FIELD, chosen.text());
    }

    /* ------------------------------------------------------------- the description */

    /* --------------------------------------------- what this program says */

    private static final FMString OPEN_FAILED = FMString.of("textedit.openFailed");
    private static final FMString OPEN_FAILED_WHY = FMString.of("textedit.openFailedWhy");
    private static final FMString SAVE_BUTTON = FMString.of("textedit.save");
    private static final FMString SAVE_FAILED = FMString.of("textedit.saveFailed");
    private static final FMString SAVE_FAILED_WHY = FMString.of("textedit.saveFailedWhy");
    private static final FMString REVERT_QUESTION = FMString.of("textedit.revertQuestion");
    private static final FMString REVERT_WARNING = FMString.of("textedit.revertWarning");
    private static final FMString REVERT_BUTTON = FMString.of("textedit.revert");
    private static final FMString SAVE_CHANGES = FMString.of("textedit.saveChanges");
    private static final FMString SAVE_CHANGES_TO = FMString.of("textedit.saveChangesTo");
    private static final FMString CHANGES_LOST = FMString.of("textedit.changesLost");
    private static final FMString DONT_SAVE_BUTTON = FMString.of("textedit.dontSave");
    private static final FMString GO_TO_LINE = FMString.of("textedit.goToLine");
    private static final FMString LINE_LABEL = FMString.of("textedit.lineLabel");
    private static final FMString SELECT_BUTTON = FMString.of("textedit.select");
    private static final FMString UNTITLED_DOCUMENT = FMString.of("textedit.untitled");

}
