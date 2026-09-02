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
package org.fractalmicro.activitymonitor;

import org.fractalmicro.appkit.FMApplication;
import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMLog;
import org.fractalmicro.foundation.FMMutableArray;
import org.fractalmicro.foundation.FMLocalized;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.appkit.FMRunningApplication;
import org.fractalmicro.appkit.FMWorkspace;

/**
 * Activity Monitor: what is running.
 *
 * A process of its own, which is worth saying twice for this one in particular: a listing
 * of what is running that could only see its own process would be showing one row and
 * calling it the system. What it shows comes from the task table, which lives in task 1
 * and is asked over a connection, so the answer is the same whoever asks and from wherever.
 *
 * The list is refreshed rather than rebuilt: the window server keeps the selection where it
 * can, because a listing that jumps back to the top every few seconds is one nobody can
 * read a line of.
 */
public final class ActivityMonitor {

    public static final FMString NAME = FMString.of("Activity Monitor");

    /** The interface file this program opens, inside its own bundle. */
    private static final FMString INTERFACE = FMString.of("ActivityMonitor");

    private static final FMString PROCESSES = FMString.of("processes");
    private static final FMString QUIT_PROCESS = FMString.of("quit process");
    private static final FMString REFRESH = FMString.of("refresh");

    /** How the columns line up: the number, whose it is, and what it is. */
    private static final FMString COLUMNS =
        FMString.of("%-6d %-6d %-22s %-11s %-16s %s");

    /** How often the listing catches up, in seconds. */
    private static final int REFRESH_SECONDS = 3;

    private final FMApplication app = FMApplication.named(NAME);

    /** What is on each row, in the order the rows are shown, so a choice can be acted on. */
    private FMArray<FMRunningApplication> showing = FMArray.empty();

    public static void main(String[] arguments) {
        if (!FMApplication.serverAvailable()) {
            FMLog.say(FMString.of("there is no window server to draw a window on"));
            return;
        }
        new ActivityMonitor().run();
    }

    private void run() {
        if (!app.showWindow(INTERFACE)) {
            FMLog.say(FMString.of("the window would not open: ")
                              .appending(app.lastError().description()));
            return;
        }
        app.onClose(app::stop);
        app.on(QUIT_PROCESS, event -> quitChosen());
        app.on(REFRESH, event -> reload());
        app.on(FMString.of("quit"), event -> app.stop());
        app.on(FMString.of("close"), event -> app.stop());

        reload();
        catchUpEvery(REFRESH_SECONDS);
        app.run();
        app.close();
    }

    /**
     * Keeps the listing current on a thread of its own.
     *
     * The run loop is waiting for events and should stay waiting: a program that woke up
     * every three seconds to see whether anything had happened would cost something even
     * when nothing had.
     */
    private void catchUpEvery(int seconds) {
        Thread ticking = new Thread(() -> {
            while (app.isRunning()) {
                try {
                    Thread.sleep(seconds * 1000L);
                } catch (InterruptedException stopped) {
                    return;
                }
                reload();
            }
        }, "activity monitor refresh");
        ticking.setDaemon(true);
        ticking.start();
    }

    /* -------------------------------------------------------------- the listing */

    private void reload() {
        FMArray<FMRunningApplication> tasks = FMWorkspace.sharedWorkspace().runningApplications();
        FMMutableArray<FMString> rows = FMMutableArray.empty();
        for (FMRunningApplication task : tasks) rows.add(lineFor(task));
        showing = tasks;
        app.setRows(PROCESSES, rows.asArray());
    }

    /**
     * One task as a line.
     *
     * The number first, because that is what identifies it and what somebody reading the
     * listing is looking for; then what it is, whose it is, and where it is actually
     * running, which for a task inside another process is that process's own number.
     */
    private static FMString lineFor(FMRunningApplication task) {
        return FMString.withFormat(COLUMNS,
            task.processIdentifier(), task.parentProcessIdentifier(), trimmed(task.localizedName(), 22), task.kind(),
            task.host(), task.state());
    }

    /** A name cut to fit a column, with a mark to say it was cut. */
    private static FMString trimmed(FMString name, int width) {
        return name.length() <= width ? name
            : FMString.of(name.subSequence(0, width - 1).toString() + "…");
    }

    /* --------------------------------------------------------------- stopping one */

    /**
     * Stops the task on the chosen row, having asked first.
     *
     * Asking is not politeness. Anything with unsaved work in it loses that work, and the
     * listing shows every task in the system including the ones holding the screen up.
     */
    private void quitChosen() {
        FMRunningApplication chosen = chosenRow();
        if (chosen == null) return;
        // Asked by the window server, not here: this program has a process of its own and
        // no screen in it, and a dialog drawn here would appear outside the desktop.
        boolean go = app.confirm(
            FMLocalized.filled(QUIT_QUESTION, chosen.localizedName()),
            FMLocalized.of(QUIT_WARNING), FMLocalized.of(QUIT_BUTTON));
        if (!go) return;
        if (!FMWorkspace.sharedWorkspace().terminateApplication(chosen.processIdentifier())) {
            FMLog.say(FMString.of("task ").appending(FMString.describing(chosen.processIdentifier()))
                              .appending(FMString.of(" would not stop")));
        }
        reload();
    }

    /** Which task the chosen line is, found by the number it starts with. */
    private FMRunningApplication chosenRow() {
        FMString line = app.valueOf(PROCESSES);
        if (line.isBlank()) return null;
        FMArray<FMString> parts = line.trimmed().split(FMString.of(" "));
        if (parts.count() == 0) return null;
        try {
            int pid = Integer.parseInt(parts.at(0).toString());
            for (int i = 0; i < showing.count(); i++) {
                if (showing.at(i).processIdentifier() == pid) return showing.at(i);
            }
        } catch (NumberFormatException notANumber) {
            return null;
        }
        return null;
    }

    /* ------------------------------------------------------------- the description */

    /* --------------------------------------------- what this program says */

    private static final FMString QUIT_QUESTION = FMString.of("activitymonitor.quitQuestion");
    private static final FMString QUIT_WARNING = FMString.of("activitymonitor.quitWarning");
    private static final FMString QUIT_BUTTON = FMString.of("activitymonitor.quit");

}
