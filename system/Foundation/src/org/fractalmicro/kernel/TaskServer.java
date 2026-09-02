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

import org.fractalmicro.xpc.Connection;
import org.fractalmicro.xpc.Message;
import org.fractalmicro.xpc.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * The process table, from outside the process that holds it.
 *
 * A table kept in one process is a table of that process's own tasks, and a listing built
 * from it shows a fraction of what is running with no way to tell which fraction. That is
 * not a process table; it is a local note. What makes the numbers mean anything across a
 * system is that there is one table, in one place, and everything else asks.
 *
 * The place is whatever holds it, which is task 1: everything is descended from it, so it
 * is the only thing certain to be there for as long as any of the rest. A process that
 * starts a task announces it here, and a process that wants to know what is running asks
 * here.
 *
 * Nothing depends on the server being up. A process that cannot reach it keeps its own
 * table and answers from that, which is what it would have done anyway.
 */
public final class TaskServer {

    public static final String SERVICE = "org.fractalmicro.kernel";

    /** What this answers. */
    public static final String LIST = "list";
    public static final String ANNOUNCE = "announce";
    public static final String KILL = "kill";

    /**
     * Asking for a number.
     *
     * The one message that has to go through the table rather than round it. A process
     * handing out numbers from a counter of its own hands out numbers another process has
     * already used, and two tasks with the same number is not a namespace. So the table
     * gives them out, and every process asks.
     */
    public static final String ALLOCATE = "allocate";

    private static volatile TaskServer running;

    private Service service;

    private TaskServer() {}

    /** Starts serving this process's table. Answers whether it took the name. */
    public static synchronized boolean start() {
        if (running != null) return true;
        TaskServer server = new TaskServer();
        server.service = new Service(SERVICE, server::answer);
        if (!server.service.start()) return false;
        running = server;
        return true;
    }

    /** Whether this process is the one holding the table. */
    public static boolean isServing() { return running != null; }

    public static synchronized void stop() {
        if (running != null && running.service != null) running.service.close();
        running = null;
    }

    /* --------------------------------------------------------------- answering */

    private Message answer(Message request) {
        return switch (request.type()) {
            case LIST -> listing();
            case ANNOUNCE -> {
                Row row = Row.fromLine(request.string("row", ""));
                yield accept(row)
                    ? Message.of(ANNOUNCE).put("ok", Boolean.TRUE)
                    : Message.error("that is not a task number this table handed out");
            }
            case ALLOCATE -> {
                int given = Tasks.takeNumber();
                handedOut.add(given);
                yield Message.of(ALLOCATE).put("pid", (long) given);
            }
            case KILL -> Message.of(KILL)
                .put("ok", Tasks.kill((int) request.integer("pid", -1)));
            default -> Message.error("the task table does not answer " + request.type());
        };
    }

    /**
     * Tasks other processes have told this one about.
     *
     * Kept apart from the table itself, because they are not this process's to stop, to
     * reap or to reparent. What they are is part of the answer when somebody asks what is
     * running.
     */
    private static final java.util.Map<Integer, Row> elsewhere =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The numbers this table handed out.
     *
     * Everything on the connection is another process of this account, which is the right
     * boundary for a desktop: a person may stop their own programs. It is not a reason to
     * believe what one of them says about a task it did not start. A number that was never
     * handed out is a number nobody was given, and a row claiming it is a row claiming to
     * be something else.
     */
    private static final java.util.Set<Integer> handedOut =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Whether a row is one this table will believe.
     *
     * It has to be a number this table gave to somebody, and it must not be one the table
     * already holds itself. Without the second test a process could announce over a task
     * that is really running and change what a listing says about it.
     */
    private static boolean accept(Row row) {
        if (row == null) return false;
        if (!handedOut.contains(row.pid())) return false;
        if (Tasks.byPid(row.pid()) != null) return false;
        elsewhere.put(row.pid(), row);
        return true;
    }

    /**
     * Every task, as lines.
     *
     * One line each, fields in a fixed order, separated by the character the ASCII table
     * set aside for exactly this and which cannot occur in any of the fields. A structure
     * would be tidier and would mean agreeing on one across a boundary that already has
     * enough to agree on.
     */
    private Message listing() {
        List<String> rows = new ArrayList<>();
        for (Task task : Tasks.all()) rows.add(Row.of(task).toLine());
        for (Row row : elsewhere.values()) rows.add(row.toLine());
        return Message.of(LIST).put("tasks", rows);
    }

    /* ---------------------------------------------------------------- speaking */

    /**
     * Whether there is a table to ask at all.
     *
     * By looking for the name, not by connecting to it. Connecting waits, on purpose and
     * for two seconds, because a service asked for the moment after it was started has not
     * finished claiming its name yet and giving up on it would be wrong. That is not this
     * question. The table is task 1's, and task 1 is running before anything that could ask
     * exists; a process that cannot find it is a process running without one, and it can
     * know that now.
     *
     * The difference is the whole of a slow start-up. Registering a task asks twice, once
     * for a number and once to announce it, and the session registers two before it draws
     * anything. Waiting to find out that nothing is listening cost eight seconds of every
     * start where the table was not there, with nothing to show for the wait.
     */
    private static boolean somewhereToAsk() {
        return running == null && Connection.available(SERVICE);
    }

    /** Tells the table about a task this process started, when the table is elsewhere. */
    public static void publish(Task task) {
        if (!somewhereToAsk()) return;
        try {
            Connection.ask(SERVICE, Message.of(ANNOUNCE).put("row", Row.of(task).toLine()));
        } catch (java.io.IOException notThere) {
            // No table to tell. This process keeps its own, which is all it ever had.
        }
    }

    /**
     * Everything running, everywhere, or this process's own when there is nowhere to ask.
     *
     * The second answer is not a failure. A system with no task table server is a system
     * where every process is its own, and saying so is more use than an empty listing.
     */
    public static org.fractalmicro.foundation.FMArray<Row> everything() {
        if (somewhereToAsk()) {
            try {
                Message reply = Connection.ask(SERVICE, Message.of(LIST));
                if (!reply.isError()) {
                    org.fractalmicro.foundation.FMMutableArray<Row> out =
                        org.fractalmicro.foundation.FMMutableArray.empty();
                    for (String line : reply.strings("tasks")) {
                        Row row = Row.fromLine(line);
                        if (row != null) out.add(row);
                    }
                    if (out.count() > 0) return out.asArray();
                }
            } catch (java.io.IOException notThere) {
                // Fall through to what this process knows.
            }
        }
        org.fractalmicro.foundation.FMMutableArray<Row> out =
            org.fractalmicro.foundation.FMMutableArray.empty();
        for (Task task : Tasks.all()) out.add(Row.of(task));
        for (Row row : elsewhere.values()) out.add(row);
        return out.asArray();
    }

    /**
     * A number for a new task, from whoever is keeping them unique.
     *
     * Answers -1 when there is nowhere to ask, and the caller falls back to its own
     * counter. A system with no table is a system where nothing else is handing out
     * numbers either, so there is nothing to collide with.
     */
    public static int takeNumber() {
        if (!somewhereToAsk()) return -1;
        try {
            Message reply = Connection.ask(SERVICE, Message.of(ALLOCATE));
            if (!reply.isError()) return (int) reply.integer("pid", -1);
        } catch (java.io.IOException notThere) {
            // Nowhere to ask.
        }
        return -1;
    }

    /**
     * One line per task, for a listing on a terminal.
     *
     * From the table rather than from this process, so what it prints is the system rather
     * than whichever part of it happened to be asked.
     */
    public static String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-6s %-6s %-22s %-12s %-16s %s%n",
                                "PID", "PPID", "NAME", "KIND", "HOST", "STATE"));
        for (Row task : everything()) {
            sb.append(String.format("%-6d %-6d %-22s %-12s %-16s %s%n",
                task.pid(), task.parent(), task.name(), task.kind(),
                task.where(), task.state()));
        }
        return sb.toString();
    }

    /**
     * The same listing arranged as what it is: a tree with task 1 at the root.
     *
     * The flat table says every task's parent in a column, which is the same information
     * and is unreadable as an answer to the question people actually ask, which is what
     * started what. Anything whose parent is missing is hung under task 1, because that is
     * where an orphan goes and a listing that quietly dropped one would be wrong.
     */
    public static String describeAsTree() {
        org.fractalmicro.foundation.FMArray<Row> all = everything();
        java.util.Map<Integer, java.util.List<Row>> children = new java.util.TreeMap<>();
        java.util.Set<Integer> known = new java.util.HashSet<>();
        for (Row task : all) known.add(task.pid());
        for (Row task : all) {
            int parent = task.pid() == KERNEL_TASK ? -1
                : known.contains(task.parent()) && task.parent() != task.pid()
                  ? task.parent() : Tasks.LAUNCHD;
            children.computeIfAbsent(parent, whoever -> new ArrayList<>()).add(task);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-6s %-30s %-12s %-16s %s%n",
                                "PID", "NAME", "KIND", "HOST", "STATE"));
        appendBranch(sb, children, -1, 0);
        return sb.toString();
    }

    /** The task everything hangs from, which has no parent to be listed under. */
    private static final int KERNEL_TASK = 0;

    private static void appendBranch(StringBuilder sb,
                                     java.util.Map<Integer, java.util.List<Row>> children,
                                     int parent, int depth) {
        java.util.List<Row> here = children.get(parent);
        if (here == null || depth > 16) return;
        here.sort(java.util.Comparator.comparingInt(Row::pid));
        for (Row task : here) {
            sb.append(String.format("%-6d %-30s %-12s %-16s %s%n", task.pid(),
                " ".repeat(depth * 2) + task.name(), task.kind(), task.where(), task.state()));
            appendBranch(sb, children, task.pid(), depth + 1);
        }
    }

    /** Asks the table to stop a task, wherever it is. */
    public static boolean kill(int pid) {
        if (!somewhereToAsk()) return Tasks.kill(pid);
        try {
            Message reply = Connection.ask(SERVICE, Message.of(KILL).put("pid", (long) pid));
            if (!reply.isError()) return true;
        } catch (java.io.IOException notThere) {
            // Nowhere to ask, so it can only be one of this process's own.
        }
        return Tasks.kill(pid);
    }

    /**
     * One task, as it travels.
     *
     * A copy rather than a reference: the task itself is in another process and cannot be
     * handed over, and what a listing needs is what it says about itself.
     */
    public record Row(int pid, int parent, int group, org.fractalmicro.foundation.FMString name,
                      org.fractalmicro.foundation.FMString kind, long hostPid,
                      org.fractalmicro.foundation.FMString state, long memory) {

        /** The unit separator, which is what it was put in the character set for. */
        private static final String BETWEEN = "";

        public static Row of(Task task) {
            return new Row(task.pid(), task.parent(), task.group(),
                           org.fractalmicro.foundation.FMString.of(task.name()),
                           org.fractalmicro.foundation.FMString.of(task.kind().name()).lowercase(),
                           task.hostPid(),
                           org.fractalmicro.foundation.FMString.of(task.isRunning()
                               ? "running" : task.state().name()).lowercase(),
                           task.memoryBytes());
        }

        public String toLine() {
            return String.join(BETWEEN, String.valueOf(pid), String.valueOf(parent),
                               String.valueOf(group), plain(name), plain(kind),
                               String.valueOf(hostPid), plain(state),
                               String.valueOf(memory));
        }

        /**
         * A field with the separator taken out of it.
         *
         * A program chooses its own name, and a name holding the character the fields are
         * separated by would split one field into two and shift everything after it. The
         * separator was set aside for this and nothing legitimate contains it, so taking
         * it out loses nothing and a malformed line stays impossible.
         */
        private static String plain(org.fractalmicro.foundation.FMString value) {
            return value.toString().replace(BETWEEN, "");
        }

        public static Row fromLine(String line) {
            String[] parts = line.split(BETWEEN, -1);
            if (parts.length < 8) return null;
            try {
                return new Row(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                               Integer.parseInt(parts[2]),
                               org.fractalmicro.foundation.FMString.of(parts[3]),
                               org.fractalmicro.foundation.FMString.of(parts[4]),
                               Long.parseLong(parts[5]),
                               org.fractalmicro.foundation.FMString.of(parts[6]),
                               Long.parseLong(parts[7]));
            } catch (NumberFormatException notANumber) {
                return null;
            }
        }

        /** Where it is actually running, as a listing shows it. */
        public org.fractalmicro.foundation.FMString where() {
            return org.fractalmicro.foundation.FMString.of(
                hostPid > 0 ? String.valueOf(hostPid) : "in this process");
        }
    }
}
