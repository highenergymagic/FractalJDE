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
package org.fractalmicro.launchd;

import org.fractalmicro.kernel.Task;
import org.fractalmicro.kernel.Tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The first thing that runs, and the last thing left.
 *
 * The loader maps this and calls it, and from here everything else in the system is
 * started. It is task 1, which is not a convention so much as an arithmetic fact: numbers
 * are handed out in order and this one was first, so everything started afterwards is
 * descended from it.
 *
 * Three things belong to task 1 and to nothing else.
 *
 * It brings the system up, in an order it decides. The metadata server before the screen,
 * because searching should work the moment there is somewhere to type; the screen before
 * anything a person can open, because there is nowhere to put a window until then.
 *
 * It is the parent of last resort. When something ends while it still has children, those
 * children are handed here, because a task whose parent is gone would otherwise have
 * nobody to report to and would sit as a zombie forever, holding a number.
 *
 * And it reaps. A task that has ended keeps its number and its exit status until somebody
 * asks, and for anything orphaned that somebody is this. A system without that leaks a
 * number every time a program quits, and eventually cannot start one.
 *
 * It does not exit. If it did there would be no system.
 */
public final class Init {
    private Init() {}

    /** How often the orphans are collected. Nothing is waiting on it, so it is unhurried. */
    public static final long REAP_SECONDS = 5;

    /** What brings up a screen. Everything a person sees is descended from this. */
    public static final String SESSION = "/System/Library/CoreServices/loginwindow";

    private static volatile boolean running = true;

    public static void main(String[] arguments) throws Exception {
        Tasks.setSelf(Tasks.LAUNCHD);
        say("starting, as task " + Tasks.LAUNCHD);

        // The table lives here, because everything is descended from here and this is the
        // one thing certain to still be running when anything asks.
        if (org.fractalmicro.kernel.TaskServer.start()) {
            say("the task table is here");
        } else {
            say("something else is already holding the task table");
        }

        Path root = rootOf(arguments);
        Launchd launchd = Launchd.session();
        launchd.setStopOnExit(true);

        // The jobs on disk first: they are what this system says should be running before
        // anybody logs in, and the metadata server is among them.
        // loadAll starts what it reads; readAll only reads. What is on disk is meant to
        // be running, so it is started.
        int jobs = launchd.loadAll();
        say(jobs + " jobs loaded");

        Task session = startSession(root, arguments);
        if (session == null) {
            say("there is no session to start; nothing would be on the screen");
            return;
        }

        reapUntilTheSessionEnds(session);
        say("the session ended; stopping what is left");
        stopEverything(launchd);
    }

    /**
     * Starts what puts a screen up, as a task of its own.
     *
     * It goes through the loader, like anything else: the image says what it links and
     * where it starts, and nothing here needs to know either. What is added is the number
     * the task was given, because a process cannot work out its own.
     */
    private static Task startSession(Path root, String[] arguments) {
        Path image = root.resolve(SESSION.substring(1));
        if (!Files.isReadable(image)) {
            say("no session image at " + image);
            return null;
        }
        try {
            int pid = Tasks.nextPid();
            List<String> command = new ArrayList<>(List.of(
                org.fractalmicro.os.OSPaths.javaCommand(),
                "--enable-preview",
                "--enable-native-access=ALL-UNNAMED",
                "-D" + org.fractalmicro.dyld.Start.ROOT_PROPERTY + "=" + root,
                "-D" + Tasks.PID_PROPERTY + "=" + pid,
                "-cp", root.resolve("usr/lib/dyld").toString(),
                "org.fractalmicro.dyld.Start", image.toString()));
            command.addAll(List.of(arguments));

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            Process process = builder.start();
            Task task = Tasks.register("org.fractalmicro.loginwindow", "loginwindow",
                                       Task.Kind.SYSTEM, List.of(), process);
            say("the session is task " + task.pid() + ", process " + process.pid());
            return task;
        } catch (IOException e) {
            say("the session would not start: " + e.getMessage());
            return null;
        }
    }

    /**
     * Waits, collecting whatever has ended, until the session does.
     *
     * Nothing here is polling for the session: it is asked how it is, and asked again in a
     * while. What the waiting is actually for is the reaping, which has to happen whether
     * or not anything is asking, because the tasks being reaped are the ones with nobody
     * left to ask about them.
     */
    private static void reapUntilTheSessionEnds(Task session) throws InterruptedException {
        while (running && session.isRunning()) {
            Thread.sleep(REAP_SECONDS * 1000);
            int reaped = Tasks.reapChildren(Tasks.LAUNCHD);
            if (reaped > 0) say("reaped " + reaped);
        }
    }

    /** Asks everything still running to stop, youngest first. */
    private static void stopEverything(Launchd launchd) {
        running = false;
        List<Task> left = Tasks.descendants(Tasks.LAUNCHD);
        for (int i = left.size() - 1; i >= 0; i--) {
            Task task = left.get(i);
            if (task.isRunning()) {
                say("stopping " + task.name() + " (" + task.pid() + ")");
                Tasks.kill(task.pid());
            }
        }
        launchd.stopAll();
    }

    /** Where this system is installed, which the loader was told and passes on. */
    private static Path rootOf(String[] arguments) {
        String said = System.getProperty(org.fractalmicro.dyld.Start.ROOT_PROPERTY, "");
        if (!said.isBlank()) return Path.of(said);
        return org.fractalmicro.os.OSPaths.ROOT;
    }

    /**
     * What task 1 has to say.
     *
     * To the error stream, because the log is a file somewhere on a volume this may be the
     * thing that mounted, and because whoever is watching a system come up is watching a
     * terminal.
     */
    private static void say(String what) {
        System.err.println("launchd: " + what);
    }
}
