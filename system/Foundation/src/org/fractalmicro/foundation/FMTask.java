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
package org.fractalmicro.foundation;

/**
 * Something the system runs, in a process of its own.
 *
 * A program that wants another program running says what to run and where its output goes,
 * and gets back something it can ask about and stop. It never handles a process of the host
 * system, because a task here is not always one: the number it is known by belongs to this
 * system's own numbering, and whether there is a host process underneath is not the
 * caller's business.
 *
 * Nothing here throws. A task that would not start answers as not running, and says why.
 */
public final class FMTask {

    private final org.fractalmicro.kernel.Task task;
    private final FMString complaint;

    private FMTask(org.fractalmicro.kernel.Task task, FMString complaint) {
        this.task = task;
        this.complaint = complaint;
    }

    /**
     * Starts something and registers it, so it appears in the task table like anything else.
     *
     * @param label     what the system knows it by, as a reverse domain name
     * @param name      what a person calls it
     * @param arguments the command and everything after it
     * @param output    where what it writes goes, or nothing to let it go nowhere
     */
    public static FMTask launch(FMString label, FMString name,
                                FMArray<FMString> arguments, FMURL output) {
        java.util.List<String> command = new java.util.ArrayList<>();
        for (FMString one : arguments) command.add(one.toString());
        if (command.isEmpty()) {
            return new FMTask(null, FMString.of("there is nothing to run"));
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            if (output != null) {
                builder.redirectOutput(ProcessBuilder.Redirect.appendTo(output.asFile()));
            }
            Process process = builder.start();
            org.fractalmicro.kernel.Task registered = org.fractalmicro.kernel.Tasks.register(
                label.toString(), name.toString(),
                org.fractalmicro.kernel.Task.Kind.APPLICATION, java.util.List.of(), process);
            FMLog.say(name.appending(FMString.of(" started as task "))
                          .appending(FMString.describing(registered.pid())));
            return new FMTask(registered, FMString.EMPTY);
        } catch (java.io.IOException wouldNotStart) {
            FMString why = FMString.describing(wouldNotStart.getMessage());
            FMLog.say(name.appending(FMString.of(" would not start: ")).appending(why));
            return new FMTask(null, why);
        }
    }

    /** The one already running under this label, or nothing when there is none. */
    public static FMTask running(FMString label) {
        org.fractalmicro.kernel.Task found = org.fractalmicro.kernel.Tasks.byLabel(label.toString());
        return found == null ? null : new FMTask(found, FMString.EMPTY);
    }

    public boolean isRunning() { return task != null && task.isRunning(); }

    /** This system's number for it, or -1 when it never started. */
    public int number() { return task == null ? -1 : task.pid(); }

    public FMString name() {
        return task == null ? FMString.EMPTY : FMString.of(task.name());
    }

    /** Why it would not start, when it did not. */
    public FMString complaint() { return complaint; }

    /** Asks it to stop. Answers whether it did. */
    public boolean stop() {
        return task != null && org.fractalmicro.kernel.Tasks.kill(task.pid());
    }
}
