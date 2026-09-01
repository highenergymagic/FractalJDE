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

/**
 * That the numbers mean something.
 *
 * A process table is not a list of what is running. A list would do for a listing, and
 * would be wrong for everything else a system does with one: a number identifies a task,
 * a parent knows to come and ask what became of its children, a group takes a signal
 * together, and a number that has been handed back can be handed out again.
 *
 * The one that is easy to get wrong is the zombie. A task that ended and was immediately
 * forgotten leaves its parent with no way to tell success from failure, and the parent
 * would have to have been watching at the moment it happened. So a task that ends keeps
 * its number and its status until asked, and only then goes.
 *
 * The other is the orphan. When a parent ends first, its children have to be handed to
 * something that will still be there, or they stay zombies forever holding numbers that
 * can never be handed out again. That something is task 1, and it is most of the reason
 * task 1 exists.
 */
public final class ProcessNamespaceTest {
    private ProcessNamespaceTest() {}

    public static int count() { return 9; }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("the process table:");

        /* ------------------------------------------------- the two that were always there */

        Task kernel = Tasks.byPid(Tasks.KERNEL);
        Task launchd = Tasks.byPid(Tasks.LAUNCHD);
        failures += check(out, "the system and what starts it are 0 and 1",
            kernel != null && launchd != null
            && kernel.pid() == 0 && launchd.pid() == 1
            && launchd.parent() == Tasks.KERNEL);

        failures += check(out, "and neither can be stopped",
            !Tasks.kill(Tasks.KERNEL) && !Tasks.kill(Tasks.LAUNCHD));

        /* ------------------------------------------------------------ parents and children */

        int was = Tasks.self();
        Task parent = Tasks.register("checking.parent", "a parent", Task.Kind.DAEMON,
                                     List.of());
        Tasks.setSelf(parent.pid());
        Task child = Tasks.register("checking.child", "a child", Task.Kind.DAEMON,
                                    List.of());
        Task grandchild;
        Tasks.setSelf(child.pid());
        grandchild = Tasks.register("checking.grandchild", "a grandchild",
                                    Task.Kind.DAEMON, List.of());
        Tasks.setSelf(was);

        failures += check(out, "a task started by another is that one's child",
            child.parent() == parent.pid() && grandchild.parent() == child.pid());

        failures += check(out, "and everything under a task can be named",
            Tasks.children(parent.pid()).size() == 1
            && Tasks.descendants(parent.pid()).size() == 2);

        // A group is inherited, so a program and everything it starts is one thing.
        failures += check(out, "a task is in the group of whatever started it",
            child.group() == parent.group() && grandchild.group() == parent.group());

        /* ------------------------------------------------------------------ ending */

        Tasks.exited(child.pid(), 0);
        failures += check(out, "a task that ends keeps its number until it is asked about",
            child.isZombie() && Tasks.byPid(child.pid()) != null
            && child.exitStatus() == 0);

        // And the grandchild, whose parent has gone, is task 1's now.
        failures += check(out, "a task whose parent has gone is handed to task 1",
            grandchild.parent() == Tasks.LAUNCHD);

        Integer status = Tasks.reapStatus(child.pid());
        failures += check(out, "reaping answers with what it exited with, and frees the number",
            status != null && status == 0 && Tasks.byPid(child.pid()) == null);

        /* ------------------------------------------------------------- and they run out */

        out.println("      numbers run from " + Tasks.FIRST_PID + " to " + Tasks.MAX_PID);
        failures += check(out, "the numbers are ones a person could read out",
            Tasks.MAX_PID < 1_000_000 && Tasks.FIRST_PID == 2);

        Tasks.reap(grandchild.pid());
        Tasks.reap(parent.pid());

        out.println("      " + (failures == 0
            ? "a number identifies a task, and says whose it is"
            : failures + " failed"));
        return failures;
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
