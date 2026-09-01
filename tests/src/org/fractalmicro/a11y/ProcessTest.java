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

import org.fractalmicro.foundation.FMString;

import org.fractalmicro.launchd.Job;
import org.fractalmicro.launchd.Launchd;
import org.fractalmicro.mds.Metadata;
import org.fractalmicro.mds.Server;
import org.fractalmicro.plist.Plist;
import org.fractalmicro.xpc.Connection;
import org.fractalmicro.xpc.Message;
import org.fractalmicro.xpc.Service;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes talking to each other, and the thing that starts them.
 *
 * These are the checks that have to be real: a port that is not listened on, a job that is
 * not started, a supervisor that does not notice a job has died are all things that look
 * fine in a diagram. So a service is served and asked, a job is run as a process of the
 * host system and killed to see it come back, and a job whose name is already served is
 * not started twice.
 *
 * Everything started here is stopped again before this returns, because a check that
 * leaves daemons running on someone's machine is a worse bug than the one it was looking
 * for.
 */
public final class ProcessTest {
    private ProcessTest() {}

    public static int count() { return 18; }

    private static final String TEST_SERVICE = "org.fractalmicro.checking.echo";
    private static final String TEST_LABEL = "org.fractalmicro.checking.job";

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("processes and ports:");

        /* --------------------------------------------------------- the port */
        Service service = new Service(TEST_SERVICE, request -> switch (request.type()) {
            case "echo" -> Message.of("echo").put("said", request.string("say", ""));
            case "boom" -> throw new IllegalStateException("this one always fails");
            default -> Message.error("no such thing as " + request.type());
        });

        boolean started = service.start();
        failures += check(out, "a service can claim a name", started);
        failures += check(out, "and the name is then listed as served",
            Connection.available(TEST_SERVICE));

        try {
            Message reply = Connection.ask(TEST_SERVICE,
                Message.of("echo").put("say", "over the port"));
            failures += check(out, "a message goes out and an answer comes back",
                "over the port".equals(reply.string("said", "")));
        } catch (Exception e) {
            out.println("FAIL  a message goes out and an answer comes back: " + e);
            failures++;
        }

        try {
            Message reply = Connection.ask(TEST_SERVICE, Message.of("nonsense"));
            failures += check(out, "a service that will not answer says why",
                reply.isError() && reply.errorText().contains("nonsense"));
        } catch (Exception e) {
            out.println("FAIL  a service that will not answer says why: " + e);
            failures++;
        }

        try {
            Message reply = Connection.ask(TEST_SERVICE, Message.of("boom"));
            failures += check(out, "a service that throws answers rather than dying",
                reply.isError() && Connection.available(TEST_SERVICE));
        } catch (Exception e) {
            out.println("FAIL  a service that throws answers rather than dying: " + e);
            failures++;
        }

        Service second = new Service(TEST_SERVICE, request -> Message.of("no"));
        failures += check(out, "two services cannot hold the same name", !second.start());

        /* ---------------------------------------------------------- the job */

        // Its own supervisor, not the session's: two of these can exist, which is half the
        // reason for it being an object at all.
        try (Launchd launchd = new Launchd()) {
            failures += check(out, "a supervisor can be made without being the only one",
                launchd != Launchd.session());

            Path jobFile = null;
            try {
                jobFile = writeJob();
                Launchd.Entry entry = launchd.load(jobFile);
                failures += check(out, "a job description is read and its label kept",
                    entry != null && TEST_LABEL.equals(entry.job.label())
                    && entry.job.keepAlive());

                // The job stops at once, so what is watched is whether the supervisor
                // notices and starts it again, and that it waits before doing so, even
                // though the description asks for no wait at all.
                long firstStart = System.currentTimeMillis();
                long deadline = firstStart + 8000;
                while (launchd.entry(TEST_LABEL).starts() < 2
                        && System.currentTimeMillis() < deadline) {
                    Thread.sleep(100);
                }
                Launchd.Entry watched = launchd.entry(TEST_LABEL);
                long gap = watched.lastStarted() - firstStart;
                out.println("      the job ran " + watched.starts() + " times, "
                            + gap + "ms apart, after " + watched.failures() + " failure(s)");
                failures += check(out, "a job that says KeepAlive is started again when it stops",
                    watched.starts() >= 2);
                failures += check(out,
                    "a job asking for no wait at all still waits, so it cannot spin",
                    gap >= Launchd.FLOOR_MILLIS);

                launchd.stop(TEST_LABEL);
                int afterStop = launchd.entry(TEST_LABEL).starts();
                Thread.sleep(2500);
                failures += check(out, "a job that has been stopped stays stopped",
                    launchd.entry(TEST_LABEL).starts() == afterStop);
            } catch (Exception e) {
                out.println("FAIL  the job checks ran: " + e);
                failures++;
            } finally {
                launchd.unload(TEST_LABEL);
                if (jobFile != null) {
                    try {
                        Files.deleteIfExists(jobFile);
                    } catch (Exception ignored) {
                        // A left over description is untidy, not broken.
                    }
                }
            }

            /* --------------------------------------------- what it will not do */
            failures += check(out, "nothing waits less than the floor, whatever is asked",
                Launchd.backoffMillis(0, 0) >= Launchd.FLOOR_MILLIS
                && Launchd.backoffMillis(-5, 0) >= Launchd.FLOOR_MILLIS);
            failures += check(out, "the wait grows while a job keeps failing",
                Launchd.backoffMillis(1, 3) > Launchd.backoffMillis(1, 0));
            failures += check(out, "and stops growing at the ceiling",
                Launchd.backoffMillis(30, 20) == Launchd.CEILING_MILLIS);

            Path missing = null;
            try {
                missing = writeMissingProgramJob();
                Launchd.Entry entry = launchd.load(missing, false);
                boolean startedNothing = launchd.start(entry.job.label());
                failures += check(out,
                    "a job whose program is not there fails rather than being started",
                    !startedNothing && launchd.entry(entry.job.label()).failures() > 0);
                launchd.unload(entry.job.label());
            } catch (Exception e) {
                out.println("FAIL  a job whose program is not there fails: " + e);
                failures++;
            } finally {
                if (missing != null) {
                    try {
                        Files.deleteIfExists(missing);
                    } catch (Exception ignored) {
                        // As above.
                    }
                }
            }

            /* ------------------------------------------- a name already served */
            Path second2 = null;
            try {
                second2 = writeJobServing(TEST_SERVICE);
                Launchd.Entry entry = launchd.load(second2, false);
                boolean startedAgain = launchd.start(entry.job.label());
                failures += check(out, "a job whose name is already served is not started again",
                    startedAgain && !launchd.entry(entry.job.label()).isRunning()
                    && launchd.entry(entry.job.label()).isServedElsewhere());
                launchd.unload(entry.job.label());
            } catch (Exception e) {
                out.println("FAIL  a job whose name is already served is not started again: " + e);
                failures++;
            } finally {
                if (second2 != null) {
                    try {
                        Files.deleteIfExists(second2);
                    } catch (Exception ignored) {
                        // As above.
                    }
                }
            }

            failures += check(out, "closing a supervisor twice is not two closings",
                closesQuietly(launchd));
        }

        /* -------------------------------------------------- claiming a name */

        // The claim is a single step. Two cannot both succeed, which is what makes it
        // safe where looking and then taking is not.
        org.fractalmicro.win.Mutex first = org.fractalmicro.win.Mutex.claim("org.fractalmicro.checking.claim");
        org.fractalmicro.win.Mutex clash = org.fractalmicro.win.Mutex.claim("org.fractalmicro.checking.claim");
        failures += check(out, "a name can be claimed once and not twice",
            first != null && clash == null);
        if (first != null) first.close();
        failures += check(out, "and is free again once it is let go",
            !org.fractalmicro.win.Mutex.taken("org.fractalmicro.checking.claim"));

        service.close();

        /* ------------------------------------------------------- the search */
        org.fractalmicro.foundation.FMArray<Metadata.Hit> walked = Metadata.walkFor(FMString.of("Applications"), 5);
        failures += check(out, "searching works with no server to ask", walked != null);

        String how = Metadata.running()
            ? "the metadata server is running; searches go to it"
            : "no metadata server is running; searches walk the disk";
        out.println("      " + how);
        failures += check(out, "the search says which way it went",
            Metadata.running() == Connection.available(Server.SERVICE));

        out.println("      " + (failures == 0 ? "the processes talk and the jobs are watched"
                                              : failures + " failed"));
        return failures;
    }

    /** Closing twice must be quiet, since a hook and a caller may both do it. */
    private static boolean closesQuietly(Launchd launchd) {
        try {
            launchd.close();
            launchd.close();
            return launchd.isClosed();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** A job naming a program that is not there, to see a failure counted rather than lost. */
    private static Path writeMissingProgramJob() throws Exception {
        Map<String, Object> job = new LinkedHashMap<>();
        job.put(Job.LABEL, TEST_LABEL + ".missing");
        job.put(Job.PROGRAM_ARGUMENTS,
                List.of("org.fractalmicro.this-program-does-not-exist"));
        Path file = Launchd.agentsFolder().resolve(TEST_LABEL + ".missing.plist");
        Plist.write(file, job);
        return file;
    }

    /** A job that stops at once, to see the supervisor start it again. */
    private static Path writeJob() throws Exception {
        Map<String, Object> job = new LinkedHashMap<>();
        job.put(Job.LABEL, TEST_LABEL);
        List<Object> command = new ArrayList<>();
        command.add(System.getenv().getOrDefault("ComSpec", "cmd.exe"));
        command.add("/c");
        command.add("exit");
        job.put(Job.PROGRAM_ARGUMENTS, command);
        job.put(Job.RUN_AT_LOAD, Boolean.TRUE);
        job.put(Job.KEEP_ALIVE, Boolean.TRUE);
        job.put(Job.THROTTLE, 0L);
        Path file = Launchd.agentsFolder().resolve(TEST_LABEL + ".plist");
        Plist.write(file, job);
        return file;
    }

    /** A job that says it serves a name something else is already serving. */
    private static Path writeJobServing(String name) throws Exception {
        Map<String, Object> job = new LinkedHashMap<>();
        job.put(Job.LABEL, TEST_LABEL + ".second");
        List<Object> command = new ArrayList<>();
        command.add(System.getenv().getOrDefault("ComSpec", "cmd.exe"));
        command.add("/c");
        command.add("exit");
        job.put(Job.PROGRAM_ARGUMENTS, command);
        Map<String, Object> services = new LinkedHashMap<>();
        services.put(name, Boolean.TRUE);
        job.put(Job.MACH_SERVICES, services);
        Path file = Launchd.agentsFolder().resolve(TEST_LABEL + ".second.plist");
        Plist.write(file, job);
        return file;
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
