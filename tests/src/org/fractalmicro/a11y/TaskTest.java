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

import org.fractalmicro.kernel.Task;
import org.fractalmicro.kernel.Tasks;

import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The task table: this system's own numbering of what it runs.
 *
 * What is checked here is that a number means one running thing and keeps meaning it: that
 * numbers are not reused, that the two reserved ones are what they say, that a task knows
 * where it is actually running, and that stopping one by number does what stopping means
 * for whichever kind it turned out to be.
 */
public final class TaskTest {
    private TaskTest() {}

    public static int count() { return 13; }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("tasks:");

        /* --------------------------------------------------- the two reserved */
        Task kernel = Tasks.byPid(Tasks.KERNEL);
        Task launchd = Tasks.byPid(Tasks.LAUNCHD);
        failures += check(out, "nothing is running before the system itself",
            kernel != null && kernel.pid() == 0 && "kernel_task".equals(kernel.name()));
        failures += check(out, "what starts everything else is the next one along",
            launchd != null && launchd.pid() == 1 && "launchd".equals(launchd.name())
            && launchd.parent() == Tasks.KERNEL);
        failures += check(out, "neither of them can be stopped",
            !Tasks.kill(Tasks.KERNEL) && !Tasks.kill(Tasks.LAUNCHD));

        /* ----------------------------------------------------- a task in here */
        CountDownLatch asked = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "checking-task");
        worker.setDaemon(true);
        worker.start();

        Task inside = Tasks.register("org.fractalmicro.checking.inside", "inside",
            Task.Kind.AGENT, List.of(), worker, asked::countDown);
        failures += check(out, "a task in this process has a number and says where it is",
            inside.pid() >= 2 && inside.host() == Task.Host.INTERNAL
            && inside.hostPid() == ProcessHandle.current().pid());
        failures += check(out, "and can be found by its number and by its name",
            Tasks.byPid(inside.pid()) == inside
            && Tasks.byLabel("org.fractalmicro.checking.inside") == inside);

        int before = inside.pid();
        Task another = Tasks.register("org.fractalmicro.checking.another", "another",
            Task.Kind.AGENT, List.of());
        failures += check(out, "numbers go up and are not given out twice",
            another.pid() > before && Tasks.byPid(before) == inside);

        // Stopping something in this process asks it to stop; it cannot be taken away.
        boolean stopped = Tasks.kill(inside.pid());
        boolean wasAsked = false;
        try {
            wasAsked = asked.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        failures += check(out, "stopping a task in this process asks it rather than killing it",
            stopped && wasAsked && !inside.isRunning());

        failures += check(out, "a task with no way to stop says so rather than pretending",
            !another.canStop() && !Tasks.kill(another.pid()));

        /* -------------------------------------------------- a task out there */
        Task outside = null;
        try {
            Process process = new ProcessBuilder(
                System.getenv().getOrDefault("ComSpec", "cmd.exe"), "/c", "pause")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            outside = Tasks.register("org.fractalmicro.checking.outside", "outside",
                Task.Kind.DAEMON, List.of("org.fractalmicro.checking.nothing"), process);
            failures += check(out, "a task in a process of its own carries both numbers",
                outside.host() == Task.Host.EXTERNAL
                && outside.hostPid() == process.pid()
                && outside.pid() != process.pid());

            long memory = outside.memoryBytes();
            out.println("      the outside task holds " + (memory > 0
                ? org.fractalmicro.fs.FS.formatBytes(memory) : "an unknown amount"));
            failures += check(out, "and the host says how big it is", memory > 0);

            boolean killed = Tasks.kill(outside.pid());
            boolean gone = process.waitFor(3, TimeUnit.SECONDS);
            failures += check(out, "stopping a task in its own process ends that process",
                killed && gone && !outside.isRunning());
        } catch (Exception e) {
            out.println("FAIL  the checks on a task in its own process ran: " + e);
            failures++;
        }

        /* -------------------------------------------------------- the listing */
        String listing = Tasks.describe();
        failures += check(out, "the listing names every task and both numberings",
            listing.contains("kernel_task") && listing.contains("launchd")
            && listing.contains("PID") && listing.contains("Host"));

        failures += check(out, "a task that has stopped can be forgotten, and only then",
            (outside == null || Tasks.reap(outside.pid()))
            && !Tasks.reap(Tasks.KERNEL));

        out.println("      " + (failures == 0 ? "the numbering holds" : failures + " failed"));
        return failures;
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
