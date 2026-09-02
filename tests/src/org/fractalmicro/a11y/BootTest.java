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

import org.fractalmicro.core.Progress;
import org.fractalmicro.kernel.Task;
import org.fractalmicro.kernel.TaskServer;
import org.fractalmicro.kernel.Tasks;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Coming up: what the system says while it does it, and how long it takes to say it.
 *
 * Two things read the narration. A terminal, where anything readable will do, and the boot
 * screen, which is a Windows program in another language that picks the lines out of
 * everything else the runtime prints. That second reader is the reason the shape is checked
 * here: a change to it would not break anything, it would leave a boot screen blank, and a
 * boot screen that says nothing looks exactly like a boot screen for a system that is not
 * doing anything.
 *
 * The waiting is checked too. Registering a task asks the table for a number and tells it
 * what started, and when there was no table both of those used to wait two seconds to find
 * that out. Two tasks are registered before the desktop is drawn, so that was eight seconds
 * of every start, spent on nothing.
 */
public final class BootTest {
    private BootTest() {}

    public static int count() { return 9; }

    /** How long registering a task may take when there is no table to ask. */
    private static final long PATIENCE_MILLISECONDS = 500;

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("coming up:");

        /* ------------------------------------------------- what it says, and how */

        List<String> said = whatItSays();
        for (String line : said) out.println("      " + line);

        failures += check(out, "every line is a time, a speaker and a stage",
            said.size() == 3 && said.stream().allMatch(BootTest::readable));
        failures += check(out, "the reader picks the stage out of the line",
            said.size() == 3
            && "installing the look".equals(stage(said.get(0)))
            && Progress.READY.equals(stage(said.get(1)))
            && stage(said.get(2)).startsWith("failed: "));
        failures += check(out, "the times are seconds since the machine started, and rise",
            said.size() == 3 && seconds(said.get(0)) >= 0
            && seconds(said.get(2)) >= seconds(said.get(0)));

        // A stack trace, a warning or somebody's own output must not be mistaken for it.
        failures += check(out, "nothing else the runtime prints looks like one",
            !readable("Exception in thread \"main\" java.lang.Error: no")
            && !readable("\tat org.fractalmicro.Main.main(Main.java:1)")
            && !readable("WARNING: preview features are enabled")
            && !readable("  1.0  two words: no"));

        failures += check(out, "the moment the machine started is passed on, not measured again",
            Progress.began() > 0
            && Progress.began() <= System.currentTimeMillis());

        /* ----------------------------------------------- and how long it waits */

        long started = System.nanoTime();
        Task registered = Tasks.register("org.fractalmicro.check.boot", "boot check",
                                         Task.Kind.APPLICATION, List.of());
        long took = (System.nanoTime() - started) / 1_000_000;
        out.println("      registering a task took " + took + " ms"
                    + (TaskServer.isServing() ? ", with the table in this process"
                       : ", asking whoever has the table"));
        failures += check(out, "registering a task does not wait for a table to appear",
            took < PATIENCE_MILLISECONDS);
        failures += check(out, "the task got a number of its own",
            registered != null && registered.pid() >= Tasks.FIRST_PID);
        // Ended and then reaped, which is the way a task leaves the table. Left running it
        // would sit in every listing this system prints for as long as the desktop is up.
        Tasks.exited(registered.pid(), 0);
        Tasks.reap(registered.pid());

        /* --------------------------------------------------------- the tree */

        String tree = TaskServer.describeAsTree();
        failures += check(out, "everything is descended from task 1",
            tree.contains("kernel_task") && tree.contains("launchd")
            && indentOf(tree, "launchd") > indentOf(tree, "kernel_task"));

        /* ------------------------------------------ and the launcher that reads it */

        Path launcher = Path.of("tools", "launcher", "src", "main.rs");
        String reader = read(launcher);
        failures += check(out, "the boot screen waits for the word this one says",
            reader.isEmpty()
            || (reader.contains("\"" + Progress.READY + "\"")
                && reader.contains("org.fractalmicro.core.Progress")));
        if (reader.isEmpty()) {
            out.println("      the launcher source is not here, so its half is not checked");
        }

        out.println("      " + (failures == 0 ? "the system says where it has got to"
                                              : failures + " failed"));
        return failures;
    }

    /* -------------------------------------------------------------- the narration */

    /** Three lines of real narration, caught as they are written. */
    private static List<String> whatItSays() {
        PrintStream was = System.err;
        ByteArrayOutputStream caught = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(caught, true, StandardCharsets.UTF_8));
            Progress.speakingAs("loginwindow");
            Progress.say("installing the look");
            Progress.ready();
            Progress.failed("there is no screen");
        } finally {
            System.setErr(was);
        }
        return caught.toString(StandardCharsets.UTF_8).lines().toList();
    }

    /**
     * Whether a line is narration, read the way the boot screen reads it.
     *
     * Deliberately the same rules and not a looser check of the same idea: seconds, a
     * point, more seconds, a speaker with no space in it, a colon and a space, and
     * something after it. A check that accepted more than the launcher does would pass
     * over exactly the change that breaks it.
     */
    private static boolean readable(String line) {
        return stage(line) != null;
    }

    private static String stage(String line) {
        String rest = line.stripLeading();
        int digits = 0;
        while (digits < rest.length() && Character.isDigit(rest.charAt(digits))) digits++;
        if (digits == 0 || digits >= rest.length() || rest.charAt(digits) != '.') return null;
        rest = rest.substring(digits + 1);
        int after = 0;
        while (after < rest.length() && Character.isDigit(rest.charAt(after))) after++;
        if (after == 0) return null;
        rest = rest.substring(after).stripLeading();
        int colon = rest.indexOf(": ");
        if (colon <= 0 || rest.substring(0, colon).contains(" ")) return null;
        return rest.substring(colon + 2).strip();
    }

    private static double seconds(String line) {
        String rest = line.strip();
        int end = 0;
        while (end < rest.length()
               && (Character.isDigit(rest.charAt(end)) || rest.charAt(end) == '.')) end++;
        try {
            return Double.parseDouble(rest.substring(0, end));
        } catch (RuntimeException notANumber) {
            return -1;
        }
    }

    /** How far in a name sits in the tree, which is how deep it hangs. */
    private static int indentOf(String tree, String name) {
        for (String line : tree.split("\\R")) {
            int at = line.indexOf(name);
            if (at >= 0) return at;
        }
        return -1;
    }

    private static String read(Path file) {
        try {
            return Files.isReadable(file) ? Files.readString(file) : "";
        } catch (java.io.IOException unreadable) {
            return "";
        }
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
