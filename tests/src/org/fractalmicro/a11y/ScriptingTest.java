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
package org.fractalmicro.a11y;

import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMMutableArray;
import org.fractalmicro.foundation.FMMutableDictionary;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.scripting.FMAppleEvent;
import org.fractalmicro.scripting.FMAppleEventManager;
import org.fractalmicro.scripting.FMScriptError;
import org.fractalmicro.scripting.FMScriptObjectSpecifier;
import org.fractalmicro.scripting.FMScriptTerminology;
import org.fractalmicro.windowserver.Desktop;
import org.fractalmicro.windowserver.WindowServer;

import javax.swing.JInternalFrame;
import java.io.PrintStream;

/**
 * Telling a program to do something from outside it.
 *
 * An Apple event names a suite, a command in that suite, and who it is for. What makes it
 * worth having is that none of those is a word anybody reads: "quit" is the command
 * whatever language the person sending it speaks, so a translated program is still a
 * program that can be told what to do.
 *
 * The events go the way everything else goes here, on the queue the window server already
 * holds for each program. What is answered inside this process is answered directly, and
 * the Finder is the reason: it runs in the window server, so an event for it has nowhere
 * to travel to.
 */
public final class ScriptingTest {
    private ScriptingTest() {}

    public static int count() { return 32; }

    /** A suite of this system's own, for checks and nothing else. */
    private static final FMString CHECKING = FMString.of("fmck");
    private static final FMString PING = FMString.of("ping");
    private static final FMString REFUSE = FMString.of("refu");

    public static int run(Desktop desktop, PrintStream out) {
        int failures = 0;
        out.println();
        out.println("being told what to do:");

        /* --------------------------------------------------------------- the codes */

        failures += check(out, "a code is four characters, padded and cut",
            FMAppleEvent.code(FMString.of("get")).sameAs(FMString.of("get "))
            && FMAppleEvent.code(FMString.of("openwide")).sameAs(FMString.of("open"))
            && FMAppleEvent.code(null).sameAs(FMString.of("    ")));

        FMAppleEvent open = FMAppleEvent.of(FMAppleEvent.REQUIRED_SUITE,
            FMAppleEvent.OPEN_DOCUMENTS, FMString.of("Finder"), FMString.of("C:/"));
        failures += check(out, "an event names a suite, a command and who it is for",
            open.is(FMAppleEvent.REQUIRED_SUITE, FMAppleEvent.OPEN_DOCUMENTS)
            && open.target().sameAs(FMString.of("Finder"))
            && FMString.describing(open.directObject()).sameAs(FMString.of("C:/")));

        /* ------------------------------------------------------------ the manager */

        FMAppleEventManager manager = FMAppleEventManager.sharedManager();
        manager.setEventHandler(CHECKING, PING, event ->
            FMString.describing(event.directObject()).appending(FMString.of(" back")));
        manager.setEventHandler(CHECKING, REFUSE, event -> {
            throw new FMScriptError(17, FMString.of("this one always refuses"));
        });
        try {
            FMDictionary answered = manager.handle(FMAppleEvent.of(CHECKING, PING,
                FMString.EMPTY, FMString.of("hello")));
            failures += check(out, "a handler is found by its suite and its command",
                !FMAppleEventManager.failed(answered)
                && FMString.describing(FMAppleEventManager.result(answered))
                           .sameAs(FMString.of("hello back")));

            FMDictionary unknown = manager.handle(FMAppleEvent.of(CHECKING,
                FMString.of("nope"), FMString.EMPTY, null));
            failures += check(out, "a command nothing answers comes back as not handled",
                FMAppleEventManager.failed(unknown)
                && unknown.whole(FMAppleEvent.ERROR_NUMBER, 0)
                   == FMAppleEventManager.EVENT_NOT_HANDLED);

            FMDictionary refused = manager.handle(FMAppleEvent.of(CHECKING, REFUSE,
                FMString.EMPTY, null));
            failures += check(out, "and one that refuses says so with its own number",
                FMAppleEventManager.failed(refused)
                && refused.whole(FMAppleEvent.ERROR_NUMBER, 0) == 17
                && FMAppleEventManager.whyFailed(refused)
                       .sameAs(FMString.of("this one always refuses")));
        } finally {
            manager.removeEventHandler(CHECKING, PING);
            manager.removeEventHandler(CHECKING, REFUSE);
        }

        /* ------------------------------------------------------------- the courier */

        WindowServer server = WindowServer.sharedServer();
        if (!server.start() && !server.isRunning()) {
            out.println("FAIL  the window server is not running");
            return failures + 5;
        }

        failures += check(out, "there is something to carry an event", manager.canSend());

        FMDictionary nobody = manager.sendEvent(FMAppleEvent.of(FMAppleEvent.REQUIRED_SUITE,
            FMAppleEvent.OPEN_APPLICATION, FMString.of("Nothing At All"), null), 500);
        failures += check(out, "an event for a program that is not there fails rather than waits",
            FMAppleEventManager.failed(nobody));

        /* -------------------------------------------------------------- the Finder */

        int before = countFinderWindows(desktop);
        FMDictionary opened = manager.sendEvent(FMAppleEvent.of(FMAppleEvent.REQUIRED_SUITE,
            FMAppleEvent.OPEN_APPLICATION, FMString.of("Finder"), null));
        drain();
        failures += check(out, "the Finder opens a window when it is told to",
            !FMAppleEventManager.failed(opened)
            && countFinderWindows(desktop) == before + 1);

        FMMutableArray<FMString> paths = FMMutableArray.empty();
        paths.add(FMString.describing(org.fractalmicro.fs.FS.home().getAbsolutePath()));
        FMDictionary onAFolder = manager.sendEvent(new FMAppleEvent(
            FMAppleEvent.REQUIRED_SUITE, FMAppleEvent.OPEN_DOCUMENTS, FMString.of("Finder"),
            FMDictionary.of(FMAppleEvent.DIRECT_OBJECT, paths.asArray())));
        drain();
        failures += check(out, "and one on a folder, when it is told which",
            !FMAppleEventManager.failed(onAFolder)
            && countFinderWindows(desktop) == before + 2);

        FMDictionary nowhere = manager.sendEvent(FMAppleEvent.of(FMAppleEvent.REQUIRED_SUITE,
            FMAppleEvent.OPEN_DOCUMENTS, FMString.of("Finder"),
            FMString.of("C:/there-is-no-such-folder-here")));
        drain();
        failures += check(out, "a folder that is not there is a reply saying so",
            FMAppleEventManager.failed(nowhere)
            && countFinderWindows(desktop) == before + 2);

        FMDictionary emptied = manager.sendEvent(FMAppleEvent.of(
            FMAppleEvent.REQUIRED_SUITE, FMAppleEvent.QUIT, FMString.of("Finder"), null));
        drain();
        failures += check(out, "telling the Finder to quit relaunches it, windows and all",
            !FMAppleEventManager.failed(emptied)
            && countFinderWindows(desktop) == 0);

        /* --------------------------------------------------- asking what it holds */

        FMDictionary opened2 = manager.sendEvent(FMAppleEvent.of(FMAppleEvent.REQUIRED_SUITE,
            FMAppleEvent.OPEN_APPLICATION, FMString.of("Finder"), null));
        drain();
        failures += check(out, "a window to ask about", !FMAppleEventManager.failed(opened2));

        FMScriptObjectSpecifier everyWindow = FMScriptObjectSpecifier.every(
            FMScriptObjectSpecifier.WINDOW, null);
        failures += check(out, "there is one window, counted by asking",
            FMString.describing(get(manager, FMAppleEvent.COUNT, everyWindow))
                    .sameAs(FMString.of("1")));

        FMScriptObjectSpecifier firstWindow = FMScriptObjectSpecifier.at(
            FMScriptObjectSpecifier.WINDOW, 1, null);
        Object called = get(manager, FMAppleEvent.GET_DATA,
            FMScriptObjectSpecifier.property(FMScriptObjectSpecifier.NAME, firstWindow));
        failures += check(out, "and the name of the first one can be asked for",
            !FMString.describing(called).isEmpty());

        Object where = get(manager, FMAppleEvent.GET_DATA,
            FMScriptObjectSpecifier.property(FMScriptObjectSpecifier.TARGET, firstWindow));
        failures += check(out, "and what it is showing",
            new java.io.File(FMString.describing(where).toString()).isDirectory());

        // Two deep, which is the whole reason a specifier is a chain: the items are in
        // the window, and nothing had to hand out a window to ask about them.
        FMScriptObjectSpecifier everyItem = FMScriptObjectSpecifier.every(
            FMScriptObjectSpecifier.ITEM, firstWindow);
        long many = whole(get(manager, FMAppleEvent.COUNT, everyItem));
        failures += check(out, "what is in that window can be counted through it", many > 0);

        Object firstNamed = get(manager, FMAppleEvent.GET_DATA,
            FMScriptObjectSpecifier.property(FMScriptObjectSpecifier.NAME,
                FMScriptObjectSpecifier.at(FMScriptObjectSpecifier.ITEM, 1, firstWindow)));
        failures += check(out, "and the first of them named",
            firstNamed != null && !FMString.describing(firstNamed).isEmpty());

        // Set the property, which is the same specifier the other way round: this is what
        // a script means by setting the target of a window.
        FMMutableDictionary going = FMMutableDictionary.empty();
        going.set(FMAppleEvent.DIRECT_OBJECT, FMScriptObjectSpecifier
            .property(FMScriptObjectSpecifier.TARGET, firstWindow).asDictionary());
        going.set(FMAppleEvent.DATA,
            FMString.describing(org.fractalmicro.fs.FS.desktopFolder().getAbsolutePath()));
        FMDictionary went = manager.sendEvent(new FMAppleEvent(FMAppleEvent.CORE_SUITE,
            FMAppleEvent.SET_DATA, FMString.of("Finder"), going.asDictionary()));
        drain();
        Object now = get(manager, FMAppleEvent.GET_DATA,
            FMScriptObjectSpecifier.property(FMScriptObjectSpecifier.TARGET, firstWindow));
        failures += check(out, "setting what it is showing moves it there",
            !FMAppleEventManager.failed(went)
            && FMString.describing(now).sameAs(FMString.describing(
                   org.fractalmicro.fs.FS.desktopFolder().getAbsolutePath())));

        failures += check(out, "a window that is there is said to be there",
            isYes(get(manager, FMAppleEvent.EXISTS, firstWindow)));

        failures += check(out, "and one that is not, is not",
            !isYes(get(manager, FMAppleEvent.EXISTS,
                FMScriptObjectSpecifier.at(FMScriptObjectSpecifier.WINDOW, 9, null))));

        FMDictionary tooFar = manager.sendEvent(FMAppleEvent.of(FMAppleEvent.CORE_SUITE,
            FMAppleEvent.GET_DATA, FMString.of("Finder"),
            FMScriptObjectSpecifier.at(FMScriptObjectSpecifier.WINDOW, 9, null)
                .asDictionary()));
        failures += check(out, "but asking about it is a refusal, not an empty answer",
            FMAppleEventManager.failed(tooFar));

        FMDictionary relaunched = manager.sendEvent(FMAppleEvent.of(
            FMAppleEvent.REQUIRED_SUITE, FMAppleEvent.QUIT, FMString.of("Finder"), null));
        drain();
        failures += check(out, "and telling the Finder to quit relaunches it, windows and all",
            !FMAppleEventManager.failed(relaunched)
            && countFinderWindows(desktop) == 0);

        /* ----------------------------------------------------- what it is called */

        // The words are in a file in the bundle, the way the words in a window are, so
        // something that has never been compiled against this program can still put its
        // commands into English.
        org.fractalmicro.bundle.Bundle finder =
            org.fractalmicro.bundle.Bundles.byIdentifier("org.fractalmicro.finder");
        java.io.File written = finder == null ? null
            : finder.resource(FMString.of("Finder"), FMScriptTerminology.EXTENSION);
        failures += check(out, "the Finder ships a terminology, and says which file it is in",
            written != null
            && finder.info().string(FMScriptTerminology.DEFINITION_KEY, FMString.EMPTY)
                     .sameAs(FMString.of("Finder.sdef")));

        FMScriptTerminology words = null;
        if (written != null) {
            try {
                words = FMScriptTerminology.read(written);
            } catch (java.io.IOException unreadable) {
                out.println("      " + unreadable.getMessage());
            }
        }
        failures += check(out, "and it reads", words != null && !words.commandWords().isEmpty());

        if (words != null) {
            failures += check(out, "a word stands for the four characters it stands for",
                words.commandNamed(FMString.of("count")).sameAs(FMString.of("corecnte"))
                && words.propertyNamed(FMString.of("name")).sameAs(FMScriptObjectSpecifier.NAME)
                && words.classNamed(FMString.of("window")).sameAs(FMScriptObjectSpecifier.WINDOW));

            failures += check(out, "and the plural is the same thing said of many",
                words.classNamed(FMString.of("windows")).sameAs(FMScriptObjectSpecifier.WINDOW)
                && words.isPlural(FMString.of("windows"))
                && !words.isPlural(FMString.of("window")));

            // Both ways round, because either half going stale is the same defect: a
            // command nobody can name, or a name for a command nothing answers.
            java.util.List<String> missing = new java.util.ArrayList<>();
            java.util.List<FMString> answers = manager.commandsFor(
                FMString.of("org.fractalmicro.finder"));
            for (FMString code : answers) {
                if (!words.commandCodes().contains(code)) missing.add(code + " has no name");
            }
            for (FMString code : words.commandCodes()) {
                if (!answers.contains(code)) missing.add(code + " is named and not answered");
            }
            for (String one : missing) out.println("      " + one);
            failures += check(out,
                "and every command the Finder answers is one the terminology names",
                missing.isEmpty());
        } else {
            failures += 3;
        }

        /* ------------------------------------------------------------ in words */

        // The words are the program's, not the parser's: every one of them was looked up
        // in the terminology that shipped with the Finder.
        failures += check(out, "a script opens a window",
            ran(out, "tell application " + Q + "Finder" + Q + " to run") != null
            && countFinderWindows(desktop) == 1);

        FMString howMany = ran(out,
            "tell application " + Q + "Finder" + Q + " to count every window");
        failures += check(out, "and counts what it opened",
            howMany != null && howMany.sameAs(FMString.of("1")));

        FMString said = ran(out,
            "tell application " + Q + "Finder" + Q + " to get the name of window 1");
        failures += check(out, "and asks what a window is called",
            said != null && !said.isEmpty());

        FMString deep = ran(out, "tell application " + Q + "Finder" + Q
            + " to get the name of item 1 of window 1");
        failures += check(out, "and reads a chain right through",
            deep != null && !deep.isEmpty());

        FMString several = ran(out, "tell application " + Q + "Finder" + Q + "\n"
            + "  count every window\n"
            + "end tell");
        failures += check(out, "a script written over several lines runs to the end",
            several != null && several.sameAs(FMString.of("1")));

        failures += check(out, "a word the program has no name for is refused",
            refused("tell application " + Q + "Finder" + Q + " to fold every window"));

        failures += check(out, "and so is a script that tells nothing anything",
            refused("count every window"));

        ran(out, "tell application " + Q + "Finder" + Q + " to quit");

        out.println("      " + (failures == 0
            ? "a program can be told what to do without anybody reading its words"
            : failures + " failed"));
        return failures;
    }

    /** A quotation mark, which a script has a great many of and Java spells awkwardly. */
    private static final String Q = "" + '"';

    /** Runs a script, answering what it answered, or nothing when it would not run. */
    private static FMString ran(java.io.PrintStream out, String text) {
        try {
            return org.fractalmicro.scripting.FMScript.run(FMString.of(text));
        } catch (org.fractalmicro.scripting.FMScriptError refused) {
            out.println("      " + refused.said());
            return null;
        }
    }

    /** Whether a script was refused, which for some of them is the right answer. */
    private static boolean refused(String text) {
        try {
            org.fractalmicro.scripting.FMScript.run(FMString.of(text));
            return false;
        } catch (org.fractalmicro.scripting.FMScriptError expected) {
            return true;
        }
    }

    /** A number back off the wire, however it was spelled on the way. */
    private static long whole(Object answer) {
        if (answer instanceof org.fractalmicro.foundation.FMNumber number) {
            return number.asWhole();
        }
        return answer instanceof Number n ? n.longValue() : 0;
    }

    /** Whether an answer means yes, however the wire happened to spell it. */
    private static boolean isYes(Object answer) {
        if (answer instanceof org.fractalmicro.foundation.FMNumber number) {
            return number.isTrue();
        }
        return answer instanceof Boolean truth && truth;
    }

    /** One question, asked the way a script asks it, with the answer or a refusal. */
    private static Object get(FMAppleEventManager manager, FMString command,
                              FMScriptObjectSpecifier what) {
        FMDictionary reply = manager.sendEvent(FMAppleEvent.of(FMAppleEvent.CORE_SUITE,
            command, FMString.of("Finder"), what.asDictionary()));
        return FMAppleEventManager.failed(reply) ? null : FMAppleEventManager.result(reply);
    }

    private static int countFinderWindows(Desktop desktop) {
        int found = 0;
        for (JInternalFrame frame : desktop.windows()) {
            if (frame instanceof org.fractalmicro.ui.FinderWindow) found++;
        }
        return found;
    }

    private static void drain() {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) { }
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
