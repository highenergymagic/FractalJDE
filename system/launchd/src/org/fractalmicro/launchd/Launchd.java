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

import org.fractalmicro.core.Log;
import org.fractalmicro.os.OSPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Starts jobs and keeps them running.
 *
 * Job descriptions are property lists, and the directory a job sits in says whose it is:
 *
 *   ~/.fractaldt/System/Library/LaunchDaemons                the system's own
 *   ~/.fractaldt/Users/&lt;user&gt;/Library/LaunchAgents     one person's
 *
 * An instance rather than a set of statics, so a test can run its own supervisor without
 * touching the session's. {@link #session()} is the desktop's.
 *
 * Exit is detected through {@link Process#onExit()}, not polling. The exception is a job
 * this process did not start: there is no exit to subscribe to, so it is watched on a timer
 * by whether the service name it claims still answers.
 *
 * Repeated failures back off exponentially, then give up. The floor is enforced here rather
 * than taken from ThrottleInterval, which a description is allowed to set to zero.
 */
public final class Launchd implements AutoCloseable {

    /** However short a job says its throttle is, nothing restarts faster than this. */
    public static final long FLOOR_MILLIS = 1000;
    /** However long a job keeps failing, nothing waits longer than this between tries. */
    public static final long CEILING_MILLIS = 60_000;
    /** A job that stops sooner than this after starting is failing, not finishing. */
    public static final long TOO_SOON_MILLIS = 2000;
    /** After this many failures in a row a job is left alone until someone asks again. */
    public static final int GIVE_UP_AFTER = 5;
    /** How often a job somebody else is running is looked at, since it cannot be watched. */
    public static final long ADOPTED_CHECK_SECONDS = 5;

    /** What is known about one job while it is loaded. */
    public static final class Entry {
        public final Job job;
        private volatile Process process;
        private volatile long lastStarted;
        private volatile int starts;
        private volatile int failures;
        private volatile boolean stopping;
        private volatile boolean servedElsewhere;
        private volatile boolean givenUp;
        private volatile org.fractalmicro.kernel.Task task;

        Entry(Job job) { this.job = job; }

        /** This system's own number for the job, once it has been started here. */
        public org.fractalmicro.kernel.Task task() { return task; }

        public int pidInSystem() {
            org.fractalmicro.kernel.Task t = task;
            return t == null ? -1 : t.pid();
        }

        public boolean isRunning() {
            Process p = process;
            return p != null && p.isAlive();
        }

        public long pid() {
            Process p = process;
            return p == null ? -1 : p.pid();
        }

        public long lastStarted() { return lastStarted; }
        public int starts() { return starts; }
        public int failures() { return failures; }
        public boolean isServedElsewhere() { return servedElsewhere; }
        public boolean hasBeenGivenUpOn() { return givenUp; }

        /** How long to wait before starting this again, growing while it keeps failing. */
        long backoffMillis() {
            return Launchd.backoffMillis(job.throttle(), failures);
        }
    }

    private final Map<String, Entry> loaded = new ConcurrentHashMap<>();
    private final List<String> order = new ArrayList<>();
    private final ScheduledExecutorService timer;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean hookAdded = new AtomicBoolean();
    private final AtomicBoolean adoptedCheckScheduled = new AtomicBoolean();
    private volatile boolean stopOnExit = true;

    public Launchd() {
        timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "launchd");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * How long to wait before restarting a job: what ThrottleInterval asked for, clamped up
     * to FLOOR_MILLIS, doubled per consecutive failure, capped at CEILING_MILLIS. A
     * description asking for zero cannot produce zero.
     */
    public static long backoffMillis(int throttleSeconds, int failures) {
        long asked = Math.max(throttleSeconds * 1000L, FLOOR_MILLIS);
        long grown = asked << Math.min(Math.max(failures, 0), 6);
        return Math.min(Math.max(grown, FLOOR_MILLIS), CEILING_MILLIS);
    }

    private static volatile Launchd session;

    /** The one the desktop supervises with, made when it is first asked for. */
    public static synchronized Launchd session() {
        if (session == null) session = new Launchd();
        return session;
    }

    public static Path daemonsFolder() {
        return OSPaths.systemLibrary().resolve("LaunchDaemons");
    }

    public static Path agentsFolder() {
        return OSPaths.userLibrary().resolve("LaunchAgents");
    }

    /**
     * Whether jobs stop when this process does.
     *
     * A session takes its jobs down with it; leaving daemons running after logout is how a
     * machine accumulates orphans. A one-shot command that starts a job and exits does the
     * opposite. Supervision ends with the process either way, and the next session adopts
     * what it finds by name.
     */
    public void setStopOnExit(boolean stop) { stopOnExit = stop; }

    public boolean stopsOnExit() { return stopOnExit; }

    /* --------------------------------------------------------------- loading */

    /** Reads every job description, without starting anything. */
    public int readAll() { return loadAll(false); }

    /** Reads every job description, and starts the ones that ask to run at load. */
    public int loadAll() { return loadAll(true); }

    private int loadAll(boolean startThem) {
        int count = 0;
        for (Path folder : new Path[]{daemonsFolder(), agentsFolder()}) {
            try {
                Files.createDirectories(folder);
            } catch (IOException e) {
                // Only fails on a name collision or permissions, and either way nothing
                // here can be read. Worth a complaint rather than a shrug.
                Log.error("the job folder " + folder + " could not be made or opened", e);
                continue;
            }
            List<Path> files = new ArrayList<>();
            try (var listing = Files.list(folder)) {
                listing.filter(f -> f.toString().endsWith(".plist")).forEach(files::add);
            } catch (IOException e) {
                Log.error("could not read the jobs in " + folder, e);
                continue;
            }
            for (Path file : files) {
                if (load(file, startThem) != null) count++;
            }
        }
        addShutdownHook();
        scheduleAdoptedCheck();
        return count;
    }

    /** Reads one job description, and starts it if it says to run at load. */
    public Entry load(Path file) { return load(file, true); }

    public Entry load(Path file, boolean startIfAsked) {
        try {
            Job job = Job.read(file);
            Entry entry = new Entry(job);
            synchronized (order) {
                if (!order.contains(job.label())) order.add(job.label());
            }
            loaded.put(job.label(), entry);
            if (job.disabled()) {
                Log.info("job " + job.label() + " is disabled");
                return entry;
            }
            if (startIfAsked && job.runAtLoad()) {
                start(entry);
            } else if (servedAlready(job)) {
                entry.servedElsewhere = true;
                adopt(entry);
            }
            addShutdownHook();
            return entry;
        } catch (IOException e) {
            Log.error("could not load the job in " + file.getFileName(), e);
            return null;
        }
    }

    /** Forgets a job, stopping it first. */
    public boolean unload(String label) {
        Entry entry = loaded.remove(label);
        synchronized (order) {
            order.remove(label);
        }
        if (entry == null) return false;
        stop(label, entry);
        return true;
    }

    /** The jobs, in the order they were loaded. A copy, so it can be walked safely. */
    public List<Entry> list() {
        List<Entry> out = new ArrayList<>();
        List<String> labels;
        synchronized (order) {
            labels = new ArrayList<>(order);
        }
        for (String label : labels) {
            Entry entry = loaded.get(label);
            if (entry != null) out.add(entry);
        }
        return out;
    }

    public Entry entry(String label) { return loaded.get(label); }

    /* -------------------------------------------------------------- running */

    public boolean start(String label) {
        Entry entry = loaded.get(label);
        if (entry == null) {
            Log.info("no job called " + label + " is loaded");
            return false;
        }
        return start(entry);
    }

    /**
     * Starts a job unless it is already running, or its name is already served.
     *
     * No lock is held across {@link ProcessBuilder#start()}. That call can block, and
     * holding a lock through it would stall every other job and every listing until it
     * returned.
     */
    private boolean start(Entry entry) {
        String label = entry.job.label();
        if (entry.isRunning()) return true;
        if (entry.job.disabled()) return false;

        // Racy by nature: true now says nothing about a moment from now. This is the cheap
        // check. What decides is the named mutex the service claims, which takes the name
        // and reports contention in the same call.
        if (servedAlready(entry.job)) {
            entry.servedElsewhere = true;
            adopt(entry);
            Log.info("job " + label + " is already served by something else");
            return true;
        }

        List<String> command = entry.job.programArguments();
        if (command.isEmpty()) {
            Log.info("job " + label + " says nothing to run");
            return false;
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        Path directory = entry.job.workingDirectory();
        if (directory != null && Files.isDirectory(directory)) {
            builder.directory(directory.toFile());
        }
        builder.environment().putAll(entry.job.environment());
        if (!redirect(builder, entry.job)) return false;

        entry.stopping = false;
        entry.servedElsewhere = false;
        entry.givenUp = false;
        long startedAt = System.currentTimeMillis();

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            Log.error("could not start " + label, e);
            entry.failures++;
            return false;
        }

        entry.process = process;
        entry.lastStarted = startedAt;
        entry.starts++;
        // Register it in the task table. The host's PID is recorded alongside our own
        // number, since both are real and both get shown.
        entry.task = org.fractalmicro.kernel.Tasks.register(label, shortName(label),
            entry.job.machServices().isEmpty()
                ? org.fractalmicro.kernel.Task.Kind.AGENT : org.fractalmicro.kernel.Task.Kind.DAEMON,
            entry.job.machServices(), process);
        Log.info("started " + label + " as task " + entry.task.pid()
                 + ", process " + process.pid());

        // onExit, not a poll loop.
        process.onExit().thenAccept(finished -> jobEnded(entry, finished, startedAt));
        addShutdownHook();
        return true;
    }

    /**
     * Restarts a job that asked for KeepAlive, after its backoff. A job that dies
     * immediately after starting counts as a failure rather than a completion, so the wait
     * grows; after enough of them it is left alone until asked for again.
     */
    private void jobEnded(Entry entry, Process finished, long startedAt) {
        if (closed.get() || entry.stopping) return;
        String label = entry.job.label();
        long ranFor = System.currentTimeMillis() - startedAt;
        entry.failures = ranFor < TOO_SOON_MILLIS ? entry.failures + 1 : 0;

        if (!entry.job.keepAlive()) {
            Log.info("job " + label + " finished with " + finished.exitValue());
            return;
        }
        if (entry.failures >= GIVE_UP_AFTER) {
            entry.givenUp = true;
            Log.info("job " + label + " stopped " + entry.failures
                     + " times straight after starting; leaving it alone until asked again");
            return;
        }

        long wait = entry.backoffMillis();
        Log.info("job " + label + " stopped after " + ranFor + "ms; starting it again in "
                 + wait + "ms");
        schedule(() -> {
            if (!closed.get() && !entry.stopping && !entry.isRunning()) start(entry);
        }, wait);
    }

    private boolean redirect(ProcessBuilder builder, Job job) {
        return redirectOne(builder, job.standardOut(), true)
            && redirectOne(builder, job.standardError(), false);
    }

    /** Sends a job's output to a file, and says so plainly when it cannot. */
    private boolean redirectOne(ProcessBuilder builder, Path file, boolean out) {
        if (file == null) return true;
        Path parent = file.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                Log.error("cannot write " + (out ? "output" : "errors") + " to " + file, e);
                return false;
            }
        }
        ProcessBuilder.Redirect to = ProcessBuilder.Redirect.appendTo(file.toFile());
        if (out) builder.redirectOutput(to); else builder.redirectError(to);
        return true;
    }

    /** Stops a job, and does not start it again however KeepAlive is set. */
    public boolean stop(String label) {
        Entry entry = loaded.get(label);
        return entry != null && stop(label, entry);
    }

    private boolean stop(String label, Entry entry) {
        entry.stopping = true;
        Process process = entry.process;
        if (process == null || !process.isAlive()) return false;
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Log.info("stopped " + label);
        return true;
    }

    /** Stops everything this started, if that is what was asked for. */
    public void stopAll() {
        if (!stopOnExit) {
            Log.info("leaving " + loaded.size() + " job(s) running; nothing supervises them "
                     + "until a session loads them again");
            return;
        }
        for (Entry entry : list()) stop(entry.job.label(), entry);
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        stopAll();
        timer.shutdownNow();
    }

    public boolean isClosed() { return closed.get(); }

    /* ------------------------------------------------------------- watching */

    /**
     * A job this process did not start has no exit to subscribe to. The only signal left is
     * whether the service name it claims still answers, so that is polled, infrequently, and
     * only for jobs in that position.
     */
    private void scheduleAdoptedCheck() {
        if (closed.get() || !adoptedCheckScheduled.compareAndSet(false, true)) return;
        try {
            timer.scheduleWithFixedDelay(() -> {
                if (closed.get()) return;
                for (Entry entry : list()) {
                    if (!entry.servedElsewhere || entry.stopping || entry.givenUp) continue;
                    if (entry.job.machServices().isEmpty()) continue;
                    if (servedAlready(entry.job)) continue;
                    Log.info("the name served by " + entry.job.label()
                             + " has stopped answering; starting it here");
                    entry.servedElsewhere = false;
                    start(entry);
                }
            }, ADOPTED_CHECK_SECONDS, ADOPTED_CHECK_SECONDS, TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            adoptedCheckScheduled.set(false);
        }
    }

    private void schedule(Runnable task, long millis) {
        if (closed.get()) return;
        try {
            timer.schedule(task, millis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // Shutting down; nothing left to start.
        }
    }

    /**
     * Gives a task number to a job that was already running. It belongs to the system
     * either way, and the host can say which process is serving the name.
     */
    private void adopt(Entry entry) {
        if (entry.task != null && entry.task.isRunning()) return;
        List<String> services = entry.job.machServices();
        long hostPid = services.isEmpty() ? -1 : org.fractalmicro.win.Pipes.serverPidOf(services.get(0));
        entry.task = org.fractalmicro.kernel.Tasks.adopt(entry.job.label(),
            shortName(entry.job.label()), org.fractalmicro.kernel.Task.Kind.DAEMON, services, hostPid);
    }

    /** The last component of a label: what the program is actually called. */
    private static String shortName(String label) {
        int dot = label.lastIndexOf('.');
        return dot >= 0 && dot < label.length() - 1 ? label.substring(dot + 1) : label;
    }

    /** Whether any name this job would serve is being served right now. */
    private static boolean servedAlready(Job job) {
        for (String name : job.machServices()) {
            if (org.fractalmicro.win.Pipes.exists(name)) return true;
        }
        return false;
    }

    /** One hook, once, however many times jobs are loaded or started. */
    private void addShutdownHook() {
        if (!hookAdded.compareAndSet(false, true)) return;
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(this::stopAll, "launchd-stop"));
        } catch (IllegalStateException e) {
            // Already shutting down.
            return;
        }
        Log.info("supervising jobs; they " + (stopOnExit ? "stop" : "carry on")
                 + " when this process does");
    }

    /* ------------------------------------------------------------ describing */

    /** One line per job, for the listing. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-9s %-7s %-8s %s%n", "PID", "Starts", "State", "Label"));
        for (Entry entry : list()) {
            String state = entry.isRunning() ? "running"
                : entry.hasBeenGivenUpOn() ? "failed"
                : entry.isServedElsewhere() ? "adopted"
                : "waiting";
            sb.append(String.format("%-9s %-7d %-8s %s%n",
                entry.isRunning() ? String.valueOf(entry.pid()) : "-",
                entry.starts(), state, entry.job));
        }
        return sb.toString();
    }
}
