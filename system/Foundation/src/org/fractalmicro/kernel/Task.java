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

import java.util.ArrayList;
import java.util.List;

/**
 * One entry in the task table: a single thing this system is running.
 *
 * A task has an identity of its own: a number in this system's numbering rather than the
 * host's, and one that does not depend on where the task actually lives. Some tasks
 * are processes of the host system, with a process identifier of their own underneath.
 * Some are threads in this one. Which of the two a task is remains its own business:
 * everything else refers to it by its number, its name, and the services it serves.
 *
 * That is deliberate. A program running on a thread today because it is small and trusted
 * can become a process tomorrow when it stops being either, and nothing that talks to it
 * needs to change.
 */
public final class Task {

    /** What kind of thing a task is. Listings group by this. */
    public enum Kind { SYSTEM, DAEMON, AGENT, APPLICATION }

    /** Where a task actually runs. */
    public enum Host {
        /** A process of the host system, with a process identifier of its own. */
        EXTERNAL,
        /** A thread of this process, which is cheap and shares everything. */
        INTERNAL
    }

    /**
     * What has become of a task.
     *
     * A task that has ended is not gone. Its number stays taken and its exit status
     * readable until whatever started it asks, or a parent looking a moment too late would
     * find nothing and have no way to tell success from failure. That state is a zombie.
     */
    public enum State { RUNNING, STOPPED, FAILED, ZOMBIE }

    private final int pid;
    private volatile int parent;
    private final int group;
    private final int session;
    private final String label;
    private final String name;
    private final Kind kind;
    private final Host host;
    private final long started;
    private final List<String> services;

    private volatile State state = State.RUNNING;
    private volatile long hostPid = -1;
    private volatile Process process;
    private volatile Thread thread;
    private volatile Runnable stopper;
    private volatile long ended;
    private volatile boolean adopted;
    private volatile int exitStatus = -1;

    Task(int pid, int parent, int group, int session, String label, String name,
         Kind kind, Host host, List<String> services) {
        this.pid = pid;
        this.parent = parent;
        this.group = group;
        this.session = session;
        this.label = label;
        this.name = name;
        this.kind = kind;
        this.host = host;
        this.started = System.currentTimeMillis();
        this.services = List.copyOf(services);
    }

    /** This system's own number for the task. Not the host's. */
    public int pid() { return pid; }

    /**
     * The task that started it.
     *
     * Not fixed for life. When a parent ends before its children do, they are handed to
     * the task that starts everything, so that something is still there to reap them.
     * Orphans without a parent would stay zombies forever and their numbers with them.
     */
    public int parent() { return parent; }

    void reparent(int to) { this.parent = to; }

    /**
     * The group this task belongs to.
     *
     * A program that opens a window and then starts something to do the work is one thing
     * to a person: quitting it should quit both. A group is how that is said, and it is
     * why a signal can go to a group rather than to a number.
     */
    public int group() { return group; }

    /** The session it belongs to, which is one login. */
    public int session() { return session; }

    /**
     * What it exited with, or -1 while it is still running.
     *
     * Zero is success, because that is what it has meant since the first system that had
     * an exit status at all, and every script written since assumes it.
     */
    public int exitStatus() { return exitStatus; }

    /** Whether it has ended and is waiting to be reaped. */
    public boolean isZombie() { return state == State.ZOMBIE; }

    public String label() { return label; }

    public String name() { return name; }

    public Kind kind() { return kind; }

    public Host host() { return host; }

    public State state() { return state; }

    public long started() { return started; }

    public long ended() { return ended; }

    /** The names it serves, which is how anything finds it. */
    public List<String> services() { return services; }

    /**
     * The host system's number for this task, where there is one. A task that is a thread
     * here answers with the number of this process, because that is the truth: it is
     * running inside it.
     */
    public long hostPid() {
        if (hostPid >= 0) return hostPid;
        return host == Host.INTERNAL ? ProcessHandle.current().pid() : -1;
    }

    public boolean isRunning() {
        if (state != State.RUNNING) return false;
        if (adopted) {
            return services.isEmpty() || org.fractalmicro.win.Pipes.exists(services.get(0));
        }
        Process p = process;
        if (p != null) return p.isAlive();
        Thread t = thread;
        if (t != null) return t.isAlive();
        return true;
    }

    /** How long it has been running, or how long it ran. */
    public long ageMillis() {
        long end = ended > 0 ? ended : System.currentTimeMillis();
        return end - started;
    }

    /* ------------------------------------------------------- what it is made of */

    void attach(Process process) {
        this.process = process;
        this.hostPid = process.pid();
        process.onExit().thenAccept(finished -> {
            exitStatus = finished.exitValue();
            finished(exitStatus == 0 ? State.STOPPED : State.FAILED);
        });
    }

    /** Says which process of the host system is running this, when it is not ours. */
    void adoptedAt(long pid) {
        this.hostPid = pid;
        this.adopted = true;
    }

    void attach(Thread thread, Runnable stopper) {
        this.thread = thread;
        this.stopper = stopper;
    }

    void finished(State how) {
        if (state != State.RUNNING) return;
        state = how;
        ended = System.currentTimeMillis();
        if (exitStatus < 0) exitStatus = how == State.STOPPED ? 0 : 1;
        org.fractalmicro.kernel.Tasks.ended(this);
    }

    /** Ends it with a status, which is what a task that reports its own exit does. */
    void exitedWith(int status) {
        this.exitStatus = status;
        finished(status == 0 ? State.STOPPED : State.FAILED);
    }

    /** Marks it as ended and waiting for whatever started it to come and ask. */
    void becomeZombie() {
        state = State.ZOMBIE;
    }

    /**
     * Asks a task to stop.
     *
     * A task that is a host process is ended the way processes are. One that is a thread
     * here is asked, because a thread cannot be ended from outside without leaving what it
     * held in an unknown state. An internal task says how to stop it when it registers.
     */
    boolean stop() {
        if (adopted && hostPid > 0) {
            boolean ended2 = org.fractalmicro.win.Process32.terminate(hostPid);
            if (ended2) finished(State.STOPPED);
            return ended2;
        }
        Process p = process;
        if (p != null) {
            p.destroy();
            try {
                if (!p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            finished(State.STOPPED);
            return true;
        }
        Runnable how = stopper;
        if (how != null) {
            try {
                how.run();
            } catch (RuntimeException e) {
                org.fractalmicro.core.Log.error("task " + label + " would not stop cleanly", e);
            }
            finished(State.STOPPED);
            return true;
        }
        Thread t = thread;
        if (t != null) {
            t.interrupt();
            finished(State.STOPPED);
            return true;
        }
        return false;
    }

    /** Whether this task can be stopped at all, which not every internal one can. */
    public boolean canStop() {
        return process != null || stopper != null || thread != null
            || (adopted && hostPid > 0);
    }

    /** Whether this was already running when the system found it. */
    public boolean isAdopted() { return adopted; }

    /**
     * How much memory this task is using, or -1 where that cannot be said.
     *
     * A task in a process of its own has a working set the host can report. A task on a
     * thread here does not: it shares one heap with every other internal task, so reporting
     * the heap would print the same figure against each of them and invite anyone reading
     * the listing to add them up. The listing shows a dash instead.
     */
    public long memoryBytes() {
        if (host == Host.INTERNAL) return -1;
        long id = hostPid();
        if (id < 0) return -1;
        return org.fractalmicro.win.Process32.memoryOf(id);
    }

    @Override public String toString() {
        List<String> parts = new ArrayList<>();
        parts.add("pid " + pid);
        parts.add(name);
        parts.add(kind.name().toLowerCase(java.util.Locale.ROOT));
        parts.add(host == Host.EXTERNAL ? "process " + hostPid() : "in this process");
        if (!services.isEmpty()) parts.add("serving " + String.join(", ", services));
        return String.join(", ", parts);
    }
}
