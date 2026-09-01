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
package org.fractalmicro.kernel;

import org.fractalmicro.core.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The task table: this system's own numbering of what it is running.
 *
 * Numbers are handed out in order and never reused, so a number identifies one running
 * thing and keeps identifying it after that thing is gone. Two are reserved:
 *
 *   0   kernel_task, the system itself
 *   1   launchd, what starts everything else
 *
 * Both are needed for the same reason the real ones are: something has to have been running
 * before the numbering started, and something has to parent everything with no other
 * parent.
 *
 * These numbers are not the host's PIDs. Where both exist, both are shown.
 */
public final class Tasks {
    private Tasks() {}

    /** The system itself: everything is descended from this. */
    public static final int KERNEL = 0;
    /** What starts and watches everything else. */
    public static final int LAUNCHD = 1;

    /**
     * The highest number that will be handed out before starting again from the bottom.
     *
     * Every system has one, and it is always smaller than a machine word: numbers get
     * printed, typed and read out, and a number nobody can hold in their head is a number
     * nobody can use. Real ones settle around this, for the same reason.
     */
    public static final int MAX_PID = 99999;

    /** The first number a task can have, since 0 and 1 are taken. */
    public static final int FIRST_PID = 2;

    private static final AtomicInteger NEXT = new AtomicInteger(FIRST_PID);
    private static final Map<Integer, Task> TABLE = new ConcurrentHashMap<>();
    private static final List<java.util.function.Consumer<Task>> LISTENERS =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    static {
        Task kernel = new Task(KERNEL, KERNEL, KERNEL, KERNEL,
                               "org.fractalmicro.kernel", "kernel_task",
                               Task.Kind.SYSTEM, Task.Host.INTERNAL, List.of());
        TABLE.put(KERNEL, kernel);
        Task launchd = new Task(LAUNCHD, KERNEL, LAUNCHD, LAUNCHD,
                                "org.fractalmicro.launchd", "launchd",
                                Task.Kind.SYSTEM, Task.Host.INTERNAL, List.of());
        TABLE.put(LAUNCHD, launchd);
    }

    /**
     * Who is asking.
     *
     * Every task registered from here is a child of this one. In the process that starts
     * the system that is launchd, and it is left alone; in a process launchd started, the
     * number it was given arrives in a property, because a process cannot work out its own
     * number by looking at itself any more than a person can.
     */
    private static volatile int self = readSelf();

    /** The property a started process is told its number in. */
    public static final String PID_PROPERTY = "org.fractalmicro.pid";

    private static int readSelf() {
        try {
            int said = Integer.parseInt(System.getProperty(PID_PROPERTY, ""));
            return said >= FIRST_PID ? said : LAUNCHD;
        } catch (NumberFormatException notSaid) {
            return LAUNCHD;
        }
    }

    /** The number of the task this process is. */
    public static int self() { return self; }

    public static void setSelf(int pid) { self = pid; }

    /* ------------------------------------------------------------ registering */

    /**
     * Registers a task backed by a host process. Exit is picked up from the process itself,
     * so the task stops being running when the process does, with nothing polling.
     */
    public static Task register(String label, String name, Task.Kind kind,
                                List<String> services, Process process) {
        Task task = make(label, name, kind, Task.Host.EXTERNAL, services);
        task.attach(process);
        announce(task);
        return task;
    }

    /**
     * Registers a task running on a thread in this process.
     *
     * @param stopper how to ask it to stop, since a thread cannot be killed from outside.
     *                May be null: the task is registered anyway and reports that it has no
     *                way to stop, rather than offering a button that does nothing.
     */
    public static Task register(String label, String name, Task.Kind kind,
                                List<String> services, Thread thread, Runnable stopper) {
        Task task = make(label, name, kind, Task.Host.INTERNAL, services);
        task.attach(thread, stopper);
        announce(task);
        return task;
    }

    /**
     * Registers a task for a process this program did not start. A service already
     * answering is still part of the system, and it gets a real host PID in the listing.
     */
    public static Task adopt(String label, String name, Task.Kind kind,
                             List<String> services, long hostPid) {
        Task task = make(label, name, kind, Task.Host.EXTERNAL, services);
        task.adoptedAt(hostPid);
        announce(task);
        return task;
    }

    /** Takes a number for something with no process and no thread: the desktop itself. */
    public static Task register(String label, String name, Task.Kind kind,
                                List<String> services) {
        Task task = make(label, name, kind, Task.Host.INTERNAL, services);
        announce(task);
        return task;
    }

    private static Task make(String label, String name, Task.Kind kind, Task.Host host,
                             List<String> services) {
        int pid = allocate();
        int parent = self;
        Task existing = TABLE.get(parent);
        // A task belongs to the group and the session of whatever started it, unless it
        // starts one of its own. Inheriting is the ordinary case and the reason a program
        // and everything it starts can be quit together.
        int group = existing == null ? parent : existing.group();
        int session = existing == null ? parent : existing.session();
        Task task = new Task(pid, parent, group, session, label, name, kind, host,
                             services == null ? List.of() : services);
        TABLE.put(pid, task);
        return task;
    }

    /** Takes a number for a new task, unique across every process in the system. */
    private static synchronized int allocate() {
        // The table hands them out, so that two processes never pick the same one. Only
        // when there is no table does this process fall back to its own counter.
        int given = TaskServer.takeNumber();
        if (given >= FIRST_PID) return given;
        return takeNumber();
    }

    /**
     * The next free number from this process's own counter.
     *
     * This is what the table itself uses, and what a process with no table to ask falls
     * back to. Numbers run up and then start again, skipping any still in use, which is
     * what every system that has run long enough to need a second lap does.
     */
    static synchronized int takeNumber() {
        for (int tried = 0; tried <= MAX_PID; tried++) {
            int pid = NEXT.getAndIncrement();
            if (pid > MAX_PID) {
                NEXT.set(FIRST_PID);
                pid = NEXT.getAndIncrement();
            }
            if (!TABLE.containsKey(pid)) return pid;
        }
        throw new IllegalStateException("there are no task numbers left");
    }

    private static void announce(Task task) {
        // And the table, wherever it is. A process that started something is the only one
        // that knows it happened, so it is the one that has to say.
        TaskServer.publish(task);
        Log.info("task " + task.pid() + " is " + task.name()
                 + (task.host() == Task.Host.EXTERNAL
                    ? " (process " + task.hostPid() + ")" : " (in this process)"));
        for (var listener : LISTENERS) {
            try {
                listener.accept(task);
            } catch (RuntimeException e) {
                Log.error("something objected to a new task", e);
            }
        }
    }

    /** Called whenever a task appears, for anything showing a list of them. */
    public static void onChange(java.util.function.Consumer<Task> listener) {
        LISTENERS.add(listener);
    }

    /* ---------------------------------------------------------------- asking */

    public static Task byPid(int pid) { return TABLE.get(pid); }

    public static Task byLabel(String label) {
        for (Task task : TABLE.values()) {
            if (task.label().equals(label)) return task;
        }
        return null;
    }

    /** The task serving a name, which is how a client finds who answers it. */
    public static Task serving(String service) {
        for (Task task : TABLE.values()) {
            if (task.services().contains(service)) return task;
        }
        return null;
    }

    /** Everything, in the order the numbers were handed out. */
    public static List<Task> all() {
        List<Task> out = new ArrayList<>(TABLE.values());
        out.sort(Comparator.comparingInt(Task::pid));
        return out;
    }

    /** Everything still running. */
    public static List<Task> running() {
        List<Task> out = new ArrayList<>();
        for (Task task : all()) {
            if (task.isRunning()) out.add(task);
        }
        return out;
    }

    /* --------------------------------------------------------------- stopping */

    /**
     * Stops a task by number. Tasks 0 and 1 cannot be stopped; task 1 is doing the
     * stopping.
     */
    public static boolean kill(int pid) {
        if (pid == KERNEL || pid == LAUNCHD) {
            Log.info("task " + pid + " cannot be stopped");
            return false;
        }
        Task task = TABLE.get(pid);
        if (task == null) return false;
        boolean stopped = task.stop();
        if (!stopped) Log.info("task " + pid + " does not know how to stop");
        return stopped;
    }

    /**
     * Called when a task ends: its children are handed on, and it waits to be asked about.
     *
     * The order matters. Children are reparented first, so that a parent ending does not
     * leave anything with a number that no longer answers; only then does the task itself
     * become a zombie, holding its number and its exit status until somebody reaps it.
     */
    static void ended(Task task) {
        for (Task child : children(task.pid())) {
            child.reparent(LAUNCHD);
        }
        task.becomeZombie();
        for (var listener : LISTENERS) listener.accept(task);
    }

    /**
     * Records that a task has ended.
     *
     * A task backed by a process reports this for itself, because the runtime tells it
     * when the process goes. One that is a thread here, or one somewhere else that said so
     * over a connection, has to be told, and this is how.
     */
    public static boolean exited(int pid, int status) {
        Task task = TABLE.get(pid);
        if (task == null || !task.isRunning()) return false;
        task.exitedWith(status);
        return true;
    }

    /** The tasks a task started, which are its to reap. */
    public static List<Task> children(int pid) {
        List<Task> out = new ArrayList<>();
        for (Task task : TABLE.values()) {
            if (task.pid() != pid && task.parent() == pid) out.add(task);
        }
        out.sort(Comparator.comparingInt(Task::pid));
        return out;
    }

    /** Everything under a task, however far down. */
    public static List<Task> descendants(int pid) {
        List<Task> out = new ArrayList<>();
        java.util.Deque<Integer> waiting = new java.util.ArrayDeque<>();
        waiting.add(pid);
        java.util.Set<Integer> seen = new java.util.LinkedHashSet<>();
        while (!waiting.isEmpty()) {
            int at = waiting.removeFirst();
            if (!seen.add(at)) continue;
            for (Task child : children(at)) {
                out.add(child);
                waiting.add(child.pid());
            }
        }
        return out;
    }

    /** Every task in one group, which is what a signal to a group goes to. */
    public static List<Task> group(int group) {
        List<Task> out = new ArrayList<>();
        for (Task task : TABLE.values()) {
            if (task.group() == group) out.add(task);
        }
        out.sort(Comparator.comparingInt(Task::pid));
        return out;
    }

    /**
     * Reaps a task that has ended, answering what it exited with.
     *
     * A task still running is not reaped and answers with nothing, which is the difference
     * between asking and waiting. Once reaped its number is free again.
     */
    public static Integer reapStatus(int pid) {
        Task task = TABLE.get(pid);
        if (task == null || task.isRunning()) return null;
        TABLE.remove(pid);
        return task.exitStatus();
    }

    /** Forgets a task that has stopped. Running tasks are kept. */
    public static boolean reap(int pid) {
        return reapStatus(pid) != null;
    }

    /** Reaps everything that has ended under one task, answering how many. */
    public static int reapChildren(int pid) {
        int reaped = 0;
        for (Task child : children(pid)) {
            if (!child.isRunning() && reap(child.pid())) reaped++;
        }
        return reaped;
    }

    /** The next number that will be handed out, for anything that wants to say so. */
    public static int nextPid() { return NEXT.get(); }

    /** One line per task, for the listing. */
    public static String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-5s %-6s %-11s %-9s %-8s %s%n",
                                "PID", "Host", "Kind", "State", "Memory", "Name"));
        for (Task task : all()) {
            long memory = task.memoryBytes();
            sb.append(String.format("%-5d %-6s %-11s %-9s %-8s %s%n",
                task.pid(),
                task.host() == Task.Host.EXTERNAL ? String.valueOf(task.hostPid()) : "self",
                task.kind().name().toLowerCase(java.util.Locale.ROOT),
                task.isRunning() ? "running"
                    : task.state().name().toLowerCase(java.util.Locale.ROOT),
                memory > 0 ? org.fractalmicro.fs.FS.formatBytes(memory) : "-",
                task.name()));
        }
        return sb.toString();
    }
}
