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
import org.fractalmicro.foundation.FMArray;
import java.util.List;
import java.util.ArrayList;
import org.fractalmicro.foundation.FMError;
import org.fractalmicro.foundation.FMNumber;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMURL;
import org.fractalmicro.nib.Nib;
import org.fractalmicro.windowserver.WindowServer;
import org.fractalmicro.xpc.Connection;
import org.fractalmicro.xpc.Message;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A program, from the program's own side.
 *
 * It opens a window by handing over a description, refers to controls by the names that
 * description gave them, and handles events as they arrive. It never touches a control and
 * never draws. That is what allows it to run in a different process.
 *
 * The shape of a program written against this is the shape such programs have always had:
 *
 *   FMApplication app = FMApplication.named("Counter");
 *   app.openWindow(description);
 *   app.on("increase", event -> app.set("total", ++count));
 *   app.run();
 *
 * The run loop asks for the next event and waits until there is one, so a program that is
 * doing nothing costs nothing.
 */
public final class FMApplication implements AutoCloseable {

    private final FMString name;
    private Connection connection;
    private int window = -1;

    /**
     * One of a program's windows.
     *
     * A program with one window can talk about "the window" and ignore these. One with
     * several has to say which, and this is that number said in a way that cannot be
     * passed where a control was meant.
     */
    public record FMWindow(int id) {
        public boolean isOpen() { return id > 0; }
    }
    private final Map<FMString, Handler> handlers = new LinkedHashMap<>();
    private volatile boolean running;
    private Runnable onClose;
    private java.util.function.Consumer<FMArray<FMURL>> onOpenFiles;

    /**
     * What to do when the program is opened again on some files while it is running.
     *
     * Set by the entry point to the delegate's openURLs, so a program that has said what
     * opening a document means has already said what this means too.
     */
    public void onOpenFiles(java.util.function.Consumer<FMArray<FMURL>> what) {
        this.onOpenFiles = what;
    }

    /** One thing that happened: which control or command, and what it was for. */
    public record Event(FMString kind, int window, FMString control, FMString action,
                        Object value, FMString where, FMArray<FMURL> urls, int row) {

        /**
         * Which one was chosen, where what it says is not what it is.
         *
         * A list holds titles somebody reads and a program acts on what they mean, and
         * those are not the same string: a title is translated and a position is not.
         * Anything that was not a choice out of a list answers -1.
         */
        public int row() { return row; }

        /** What the control held, as text, which is what most handlers want. */
        public FMString text() {
            return value == null ? FMString.EMPTY : FMString.describing(value);
        }

        public boolean isClosed() {
            return kind.sameAs(FMString.of(WindowServer.EVENT_CLOSED));
        }

        /** Whether this came from a menu rather than from something in the window. */
        public boolean isMenu() { return kind.sameAs(FMString.of(WindowServer.EVENT_MENU)); }

        /**
         * Whether somebody opened what they had chosen, rather than merely choosing it.
         *
         * A double click, or Return on a selection. The difference matters to anything
         * showing a folder: choosing one is looking at it, opening it is going into it.
         */
        public boolean isOpen() {
            return kind.sameAs(FMString.of(WindowServer.EVENT_OPEN));
        }

        /**
         * Whether this is the program being opened again, on the files it carries.
         *
         * Nothing in the window was used and there may not be a window: this is the system
         * saying somebody double-clicked a document while the program was already running.
         */
        public boolean isOpenFiles() {
            return kind.sameAs(FMString.of(WindowServer.EVENT_OPEN_FILES));
        }
    }

    private FMApplication(FMString name) {
        this.name = name;
    }

    public static FMApplication named(FMString name) {
        FMApplication made = new FMApplication(name);
        if (SHARED == null) SHARED = made;
        return made;
    }

    private static volatile FMApplication SHARED;

    /**
     * The one this program is, which is NSApp.
     *
     * One connection to the window server, for the reason a Cocoa program has one
     * NSApplication: it is the program as the screen sees it. The first one made is it,
     * normally by FMApplicationMain before the program's own code runs.
     */
    public static FMApplication sharedApplication() {
        FMApplication one = SHARED;
        if (one == null) {
            synchronized (FMApplication.class) {
                if (SHARED == null) SHARED = new FMApplication(FMString.of("Program"));
                one = SHARED;
            }
        }
        return one;
    }

    /** Names the shared one before anything asks for it, which the entry point does. */
    static synchronized void becomeShared(FMApplication one) { SHARED = one; }

    /** Whether there is a window server to talk to at all. */
    public static boolean serverAvailable() {
        return Connection.available(WindowServer.SERVICE);
    }

    public FMString name() { return name; }

    public int windowId() { return window; }

    private Connection connection() throws IOException {
        if (connection == null) {
            connection = Connection.to(WindowServer.SERVICE);
            if (connection == null) throw new IOException("there is no window server running");
        }
        return connection;
    }

    /* ------------------------------------------------------------- windows */

    /**
     * Opens another window, and answers which one it is.
     *
     * The first window a program opens is also the one the methods that name no window
     * work on, because a program with one window should not have to hold on to a handle
     * for it.
     */
    public FMWindow openAnother(Nib description) {
        try {
            int made = openWindow(description);
            return new FMWindow(made);
        } catch (IOException e) {
            failed(e);
            return new FMWindow(-1);
        }
    }

    /** The window the methods that name none work on. */
    public FMWindow mainWindow() { return new FMWindow(window); }

    /** Closes one window of several, answering whether it closed. */
    public boolean close(FMWindow which) {
        try {
            connection().send(Message.of(WindowServer.CLOSE)
                .put("window", (long) which.id()));
            return true;
        } catch (IOException e) {
            return failed(e);
        }
    }

    /** Puts a value into a control of a named window. */
    public boolean setValue(FMWindow which, FMString control, FMString value) {
        try {
            Message reply = connection().send(Message.of(WindowServer.SET)
                .put("window", (long) which.id()).put("control", control.toString())
                .put("value", value.toString()));
            if (reply.isError()) throw new IOException(reply.errorText());
            return true;
        } catch (IOException e) {
            return failed(e);
        }
    }

    /** What is in a control of a named window. */
    public FMString valueOf(FMWindow which, FMString control) {
        try {
            Message reply = connection().send(Message.of(WindowServer.GET)
                .put("window", (long) which.id()).put("control", control.toString()));
            if (reply.isError()) throw new IOException(reply.errorText());
            Object value = reply.get("value");
            return value == null ? FMString.EMPTY : FMString.describing(value);
        } catch (IOException e) {
            failed(e);
            return FMString.EMPTY;
        }
    }

    /** Renames a named window. */
    public boolean setTitle(FMWindow which, FMString title) {
        try {
            connection().send(Message.of(WindowServer.SET_TITLE)
                .put("window", (long) which.id()).put("title", title.toString()));
            return true;
        } catch (IOException e) {
            return failed(e);
        }
    }

    /** Opens a window from a description held in memory. */
    private int openWindow(Nib description) throws IOException {
        Message reply = connection().send(Message.of(WindowServer.OPEN)
            .put("application", name.toString())
            .put("description", description.toBytes().asString().toString()));
        if (reply.isError()) throw new IOException(reply.errorText());
        int made = (int) reply.integer("window", -1);
        if (window < 0) window = made;
        return made;
    }

    /**
     * Opens the window an interface file describes, in the language this account reads.
     *
     * The program names the file and nothing else. Where it is, which language folder it
     * comes from and which words go in it all follow from the bundle, so a translation is
     * added by putting a directory in there and the program never learns of it.
     */
    public boolean showWindow(FMString interfaceName) {
        Nib described = load(interfaceName);
        if (described == null) return false;
        return showWindow(described);
    }

    /** Another window from another interface file, for a panel or a second document. */
    public FMWindow openAnother(FMString interfaceName) {
        Nib described = load(interfaceName);
        return described == null ? new FMWindow(-1) : openAnother(described);
    }

    /**
     * Reads an interface file out of the running program's bundle, translated.
     *
     * The words come from a strings file of the same name, so the interface and the
     * translation of it are found together and neither has to name the other.
     */
    private Nib load(FMString interfaceName) {
        org.fractalmicro.bundle.Bundle bundle = org.fractalmicro.bundle.Bundle.main();
        if (bundle == null) {
            complaint = FMError.of(FMError.COCOA_DOMAIN, 0,
                FMLocalized.of(FMString.of("error.noBundle")));
            return null;
        }
        java.io.File file = bundle.resource(interfaceName, org.fractalmicro.nib.Xib.EXTENSION);
        if (file == null) {
            complaint = FMError.of(FMError.COCOA_DOMAIN, 0,
                FMLocalized.filled(FMString.of("error.noInterface"), interfaceName));
            return null;
        }
        try {
            Nib described = org.fractalmicro.nib.Xib.read(FMURL.of(file));
            return org.fractalmicro.nib.Xib.localized(described, bundle.strings(interfaceName));
        } catch (IOException e) {
            failed(e);
            return null;
        }
    }

    /** Opens a window from a description in a file, which is where descriptions live. */
    public boolean showWindow(FMURL nibFile) {
        try {
            openWindow(Nib.read(nibFile));
            return true;
        } catch (IOException e) {
            return failed(e);
        }
    }

    private void closeWindow() throws IOException {
        if (window < 0) return;
        connection().send(Message.of(WindowServer.CLOSE).put("window", (long) window));
    }

    /** Renames the window, answering whether the server took it. */
    public boolean setTitle(FMString title) {
        try {
            connection().send(Message.of(WindowServer.SET_TITLE)
                .put("window", (long) window).put("title", title.toString()));
            return true;
        } catch (IOException e) {
            return failed(e);
        }
    }

    /* -------------------------------------------------------------- values */

    /** Puts a value into a control. */
    private void set(String control, Object value) throws IOException {
        Message reply = connection().send(Message.of(WindowServer.SET)
            .put("window", (long) window).put("control", control).put("value", value));
        if (reply.isError()) throw new IOException(reply.errorText());
    }

    /** Reads what is in a control. */
    private String get(String control) throws IOException {
        Message reply = connection().send(Message.of(WindowServer.GET)
            .put("window", (long) window).put("control", control));
        if (reply.isError()) throw new IOException(reply.errorText());
        Object value = reply.get("value");
        return value == null ? "" : String.valueOf(value);
    }

    /** Turns a control on or off, answering whether the server took it. */
    public boolean setEnabled(FMString control, boolean enabled) {
        try {
            connection().send(Message.of(WindowServer.SET_ENABLED)
                .put("window", (long) window).put("control", control.toString())
                .put("enabled", enabled));
            return true;
        } catch (IOException e) {
            return failed(e);
        }
    }

    /* -------------------------------------------------------------- events */

    /* ------------------------------------------------- the face a program writes to */

    /**
     * What a program hands over to be told about an event.
     *
     * Its own kind rather than the runtime's, so that an application can be written
     * without naming anything outside this system.
     */
    public interface Handler {
        void handle(Event event);
    }

    /** Says what to do when a control with this action is used. */
    public FMApplication on(FMString action, Handler handler) {
        handlers.put(action, handler);
        return this;
    }

    /**
     * Whether a command can be done right now.
     *
     * NSMenuValidation, asked as a menu opens rather than answered once when the menu was
     * described. Save is live when there is something to save, which only the program
     * knows and only when asked. A program that says nothing gets the default Cocoa uses:
     * live when it has said what the command does, grey when it has not.
     */
    public interface Validator {
        boolean canPerform(FMString action);
    }

    private Validator validator;

    /** Says how to answer whether a command can be done. */
    public FMApplication onValidate(Validator validator) {
        this.validator = validator;
        return this;
    }

    /** Whether this command is live, which is what the menu bar is about to draw. */
    public boolean canPerform(FMString action) {
        if (action == null || action.isEmpty()) return false;
        if (!handlers.containsKey(action)) return false;
        return validator == null || validator.canPerform(action);
    }

    /**
     * Why the last thing this program asked for did not work.
     *
     * A value rather than a throw. Nearly everything that goes wrong between a program and
     * the window server is worth telling about and not worth unwinding the stack for: no
     * answer, no window, no such control.
     */
    public FMError lastError() {
        FMError said = complaint;
        return said == null
            ? FMError.of(FMError.COCOA_DOMAIN, 0, FMString.EMPTY) : said;
    }

    private volatile FMError complaint;

    private boolean failed(IOException e) {
        complaint = FMError.from(e, FMLocalized.of(FMString.of("error.noWindowServer")));
        return false;
    }

    /** Opens the window a description asks for. Answers whether it opened. */
    public boolean showWindow(Nib description) {
        try {
            openWindow(description);
            return true;
        } catch (IOException e) {
            return failed(e);
        }
    }

    /** Puts a value into a control, answering whether it went. */
    public boolean setValue(FMString control, FMString value) {
        try {
            set(control.toString(), value.toString());
            return true;
        } catch (IOException e) {
            return failed(e);
        }
    }

    /** Puts a number into a control, for the ones that hold one. */
    public boolean setValue(FMString control, FMNumber value) {
        try {
            set(control.toString(), value.asWhole());
            return true;
        } catch (IOException e) {
            return failed(e);
        }
    }

    /**
     * Puts different rows in a list, answering whether they went.
     *
     * How a window shows something that changes: what is running, what was found, what is
     * in a folder. The description said there was a list; this says what is in it now.
     */
    public boolean setRows(FMString control, FMArray<FMString> rows) {
        try {
            List<String> text = new ArrayList<>();
            for (int i = 0; i < rows.count(); i++) text.add(rows.at(i).toString());
            Message reply = connection().send(Message.of(WindowServer.SET_ROWS)
                .put("window", (long) window).put("control", control.toString())
                .put("rows", text));
            if (reply.isError()) throw new IOException(reply.errorText());
            return true;
        } catch (IOException e) {
            return failed(e);
        }
    }

    /**
     * Shows or hides a control, answering whether it went.
     *
     * This is how a window has panes without being several windows: everything is
     * described once, in the same place, and the program shows the one that is wanted.
     */
    public boolean setVisible(FMString control, boolean shown) {
        try {
            Message reply = connection().send(Message.of(WindowServer.SET_VISIBLE)
                .put("window", (long) window).put("control", control.toString())
                .put("visible", shown));
            if (reply.isError()) throw new IOException(reply.errorText());
            return true;
        } catch (IOException e) {
            return failed(e);
        }
    }

    /**
     * Asks a control to do something to itself, answering whether it could.
     *
     * The names are the editing commands a text view already knows. A program sends the
     * command rather than doing the work, because the text is in the view and the view is
     * somewhere else.
     */
    public boolean perform(FMString control, FMString action) {
        try {
            Message reply = connection().send(Message.of(WindowServer.PERFORM)
                .put("window", (long) window).put("control", control.toString())
                .put("action", action.toString()));
            if (reply.isError()) throw new IOException(reply.errorText());
            return true;
        } catch (IOException e) {
            return failed(e);
        }
    }

    /**
     * Looks for something in a control that can look, answering whether it could.
     *
     * Nothing to look for ends the search and puts back what was being shown, which is
     * what emptying a search field means everywhere else.
     */
    public boolean find(FMString control, FMString text) {
        try {
            Message reply = connection().send(Message.of(WindowServer.FIND)
                .put("window", (long) window).put("control", control.toString())
                .put("text", text == null ? "" : text.toString()));
            if (reply.isError()) throw new IOException(reply.errorText());
            return true;
        } catch (IOException e) {
            return failed(e);
        }
    }

    /**
     * What a sheet came back with: the button that ended it, and what was in it.
     *
     * A sheet is a question, so its answer is one value and not a window to look after.
     * Nothing chosen means it was dismissed without a button, which is what Escape does.
     */
    public record Answer(FMString action, FMArray<FMString> controls,
                         FMArray<FMString> values) {

        public boolean isNothing() { return action.isEmpty(); }

        /** What one control in the sheet held, by the identifier the description gave it. */
        public FMString valueOf(FMString control) {
            for (int i = 0; i < controls.count() && i < values.count(); i++) {
                if (controls.at(i).sameAs(control)) return values.at(i);
            }
            return FMString.EMPTY;
        }
    }

    /**
     * Runs a described window as a sheet on this program's window, and waits for it.
     *
     * The waiting is what makes it a sheet rather than a second window: it hangs off one
     * window, which cannot be used until it is answered, and nor can the program.
     */
    public Answer sheet(Nib description) {
        try {
            Message reply = connection().send(Message.of(WindowServer.SHEET)
                .put("window", (long) window)
                .put("description", description.toBytes().asBytes()));
            if (reply.isError()) throw new IOException(reply.errorText());
            return new Answer(FMString.of(reply.string("action", "")),
                              names(reply.strings("controls")),
                              names(reply.strings("values")));
        } catch (IOException e) {
            failed(e);
            return new Answer(FMString.EMPTY, FMArray.empty(), FMArray.empty());
        }
    }

    private static FMArray<FMString> names(java.util.List<String> said) {
        org.fractalmicro.foundation.FMMutableArray<FMString> out =
            org.fractalmicro.foundation.FMMutableArray.empty();
        for (String one : said) out.add(FMString.of(one));
        return out.asArray();
    }

    /**
     * Says this program's window is holding changes that have not been written.
     *
     * The window server draws a dot in the close button. A program says it rather than
     * being asked, because nothing outside it can work out whether a document has changed.
     */
    public boolean setDocumentEdited(boolean edited) {
        try {
            Message reply = connection().send(Message.of(WindowServer.SET_EDITED)
                .put("window", (long) window).put("edited", edited));
            if (reply.isError()) throw new IOException(reply.errorText());
            return true;
        } catch (IOException e) {
            return failed(e);
        }
    }

    /** Where a control's selection starts and ends, and what is in it. */
    public record Selection(int from, int to, FMString text) {
        public boolean isEmpty() { return from >= to; }
    }

    public Selection selectionIn(FMString control) {
        try {
            Message reply = connection().send(Message.of(WindowServer.SELECTION)
                .put("window", (long) window).put("control", control.toString()));
            if (reply.isError()) throw new IOException(reply.errorText());
            return new Selection((int) reply.integer("from", 0),
                                 (int) reply.integer("to", 0),
                                 FMString.of(reply.string("text", "")));
        } catch (IOException e) {
            failed(e);
            return new Selection(0, 0, FMString.EMPTY);
        }
    }

    /** Chooses a stretch of text and shows it, which is what finding something means. */
    public boolean select(FMString control, int from, int to) {
        try {
            connection().send(Message.of(WindowServer.SELECT_RANGE)
                .put("window", (long) window).put("control", control.toString())
                .put("from", (long) from).put("to", (long) to));
            return true;
        } catch (IOException e) {
            return failed(e);
        }
    }

    /** What is in a control, or nothing at all when it could not be asked. */
    public FMString valueOf(FMString control) {
        try {
            return FMString.of(get(control.toString()));
        } catch (IOException e) {
            failed(e);
            return FMString.EMPTY;
        }
    }

    /* ---------------------------------------------------- asking a person */

    /**
     * Asks for a piece of text, and answers what was typed.
     *
     * Nothing back means the person cancelled, which is an answer and not a failure.
     *
     * Drawn by the window server: a program in its own process has no screen, and a window
     * it opened itself would appear outside the desktop behind whatever was in front,
     * which looks like a command that did nothing.
     */
    public FMString ask(FMString message, FMString label, FMString initial,
                        FMString actionButton) {
        try {
            Message reply = connection().send(Message.of(WindowServer.ASK)
                .put("message", message.toString()).put("label", label.toString())
                .put("value", initial.toString()).put("action", actionButton.toString()));
            if (reply.isError()) throw new IOException(reply.errorText());
            Object value = reply.get("value");
            return value == null ? FMString.EMPTY : FMString.describing(value);
        } catch (IOException e) {
            failed(e);
            return FMString.EMPTY;
        }
    }

    /** Says something that needs no answer, on the screen the program does not own. */
    public void tell(FMString message, FMString informative) {
        try {
            connection().send(Message.of(WindowServer.TELL)
                .put("message", message.toString())
                .put("informative", informative.toString()));
        } catch (IOException e) {
            failed(e);
        }
    }

    /**
     * Asks something that can be answered yes or no.
     *
     * The button is named for what it does rather than saying OK, so that somebody reading
     * only the buttons still knows which one goes ahead.
     */
    public boolean confirm(FMString message, FMString informative, FMString actionButton) {
        try {
            Message reply = connection().send(Message.of(WindowServer.CONFIRM)
                .put("message", message.toString())
                .put("informative", informative.toString())
                .put("action", actionButton.toString()));
            if (reply.isError()) throw new IOException(reply.errorText());
            return Boolean.TRUE.equals(reply.get("chose"));
        } catch (IOException e) {
            failed(e);
            return false;
        }
    }

    /**
     * Asks a question with three answers, and says which was chosen.
     *
     * Nought is the action, one is Cancel, two is the other choice, which is the order the
     * buttons are offered in. Cancel is in the middle because both the others cannot be
     * undone.
     */
    public int choose(FMString message, FMString informative, FMString actionButton,
                      FMString otherChoice) {
        try {
            Message reply = connection().send(Message.of(WindowServer.CHOOSE)
                .put("message", message.toString())
                .put("informative", informative.toString())
                .put("action", actionButton.toString())
                .put("other", otherChoice.toString()));
            if (reply.isError()) throw new IOException(reply.errorText());
            Object chosen = reply.get("chosen");
            return chosen instanceof Number n ? n.intValue() : CANCELLED;
        } catch (IOException e) {
            failed(e);
            return CANCELLED;
        }
    }

    /** What comes back when the person said no, or nothing could be asked at all. */
    public static final int CANCELLED = 1;

    /* ------------------------------------------------- the save and open panels */

    /**
     * Runs a save panel, and answers where the person put the document.
     *
     * Nothing back means they cancelled. Built by the window server, which has the screen,
     * and the panel is the system's anyway so every program that saves asks the same way.
     */
    public FMURL runSavePanel(FMSavePanel panel) {
        return runPanel(panel, WindowServer.SAVE_PANEL);
    }

    /** The same for opening, where there is nothing to name and something to choose. */
    public FMURL runOpenPanel(FMOpenPanel panel) {
        return runPanel(panel, WindowServer.OPEN_PANEL);
    }

    private FMURL runPanel(FMSavePanel panel, String verb) {
        try {
            List<Object> types = new ArrayList<>();
            for (FMString one : panel.allowedFileTypes()) types.add(one.toString());
            List<Object> formats = new ArrayList<>();
            for (FMString one : panel.formats()) formats.add(one.toString());

            Message reply = connection().send(Message.of(verb)
                .put("name", panel.nameFieldStringValue().toString())
                .put("title", panel.title().toString())
                .put("message", panel.message().toString())
                .put("prompt", panel.prompt().toString())
                .put("directory", panel.directoryURL().path().toString())
                .put("types", types)
                .put("formats", formats)
                .put("formatLabel", panel.formatLabel().toString())
                .put("format", (long) panel.chosenFormat()));
            if (reply.isError()) throw new IOException(reply.errorText());

            panel.chosenFormat((int) reply.integer("format", 0));
            if (reply.integer("answer", FMSavePanel.CANCELLED) != FMSavePanel.OK) return null;
            Object path = reply.get("path");
            return path == null ? null : FMURL.ofPath(FMString.describing(path));
        } catch (IOException e) {
            failed(e);
            return null;
        }
    }

    /** Closes the window this program opened, answering whether it closed. */
    public boolean hideWindow() {
        try {
            closeWindow();
            return true;
        } catch (IOException e) {
            return failed(e);
        }
    }

    /** Says what to do when the window is closed, which usually means stopping. */
    public FMApplication onClose(Runnable handler) {
        this.onClose = handler;
        return this;
    }

    /** Waits for one event, up to a time. Answers null when nothing happened. */
    public Event nextEvent(int waitMillis) throws IOException {
        // Which program is asking, because events are kept apart by whose they are.
        Message reply = connection().send(Message.of(WindowServer.NEXT_EVENT)
            .put("application", name)
            .put("timeout", (long) waitMillis));
        String kind = reply.string("event", WindowServer.EVENT_NONE);
        if (WindowServer.EVENT_NONE.equals(kind)) return null;
        // Answered here and never handed on. A program is asked whether a command can be
        // done, not told that somebody asked: Cocoa makes this a method it calls on the
        // program and not an event the program has to know to expect, and a program that
        // had to remember to answer it would be a program whose menus lie by default.
        if (WindowServer.EVENT_VALIDATE.equals(kind)) {
            sayWhatCanBeDone(reply);
            return null;
        }
        // A menu command names the item it came from where a control names itself, so
        // both arrive the same shape and a program handles them the same way.
        String from = WindowServer.EVENT_MENU.equals(kind)
            ? reply.string("item", "") : reply.string("control", "");
        // What comes off the wire is the runtime's text; what a program handles is this
        // system's. The message boundary is where one becomes the other.
        org.fractalmicro.foundation.FMMutableArray<FMURL> files =
            org.fractalmicro.foundation.FMMutableArray.empty();
        for (String one : reply.strings("files")) files.add(FMURL.ofPath(one));
        return new Event(FMString.of(kind), (int) reply.integer("window", -1),
                         FMString.of(from), FMString.of(reply.string("action", "")),
                         reply.get("value"),
                         FMString.of(reply.string("folder", "")),
                         files.asArray(), (int) reply.integer("row", -1));
    }

    /**
     * Answers which of the commands in a menu that is opening can be done.
     *
     * The menu bar is waiting on this, so it does no work beyond asking: whether a command
     * is live has to be something the program already knows.
     */
    private void sayWhatCanBeDone(Message asking) throws IOException {
        java.util.List<String> live = new java.util.ArrayList<>();
        for (String action : asking.strings("actions")) {
            if (canPerform(FMString.of(action))) live.add(action);
        }
        connection().send(Message.of(WindowServer.VALIDATED)
            .put("ticket", asking.integer("ticket", -1))
            .put("enabled", live));
    }

    /** Hands one event to whatever said it wanted it. */
    public void dispatch(Event event) {
        if (event == null) return;
        if (event.isClosed()) {
            if (onClose != null) onClose.run();
            else running = false;
            return;
        }
        // Being opened again is the system's message rather than a control's, so it goes to
        // the one thing that speaks for the whole program. The entry point sets this to the
        // delegate before any of the program's own code runs, so a program handles being
        // opened on a second document by having already said what opening one means.
        if (event.isOpenFiles()) {
            if (onOpenFiles != null) onOpenFiles.accept(event.urls());
            return;
        }
        Handler handler = handlers.get(event.action());
        if (handler == null) handler = handlers.get(event.control());
        if (handler != null) handler.handle(event);
    }

    /** The run loop: wait for something to happen, do it, wait again. */
    public void run() {
        running = true;
        while (running) {
            try {
                dispatch(nextEvent(1000));
            } catch (IOException e) {
                org.fractalmicro.core.Log.info(name + " lost the window server: " + e.getMessage());
                running = false;
            }
        }
    }

    public void stop() { running = false; }

    public boolean isRunning() { return running; }

    @Override public void close() {
        running = false;
        if (connection != null) connection.close();
        connection = null;
    }
}
