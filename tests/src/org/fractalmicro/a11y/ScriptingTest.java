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
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.scripting.FMAppleEvent;
import org.fractalmicro.scripting.FMAppleEventManager;
import org.fractalmicro.scripting.FMScriptError;
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

    public static int count() { return 11; }

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

        FMDictionary relaunched = manager.sendEvent(FMAppleEvent.of(
            FMAppleEvent.REQUIRED_SUITE, FMAppleEvent.QUIT, FMString.of("Finder"), null));
        drain();
        failures += check(out, "and telling the Finder to quit relaunches it, windows and all",
            !FMAppleEventManager.failed(relaunched)
            && countFinderWindows(desktop) == 0);

        out.println("      " + (failures == 0
            ? "a program can be told what to do without anybody reading its words"
            : failures + " failed"));
        return failures;
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
