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
package org.fractalmicro.core;

import java.util.Locale;

/**
 * What a system says while it is coming up.
 *
 * Starting takes several seconds and used to say nothing at all for most of them, which
 * from outside is indistinguishable from having hung. So every part of the way up says
 * where it has got to, and says it in one shape:
 *
 * <pre>
 *    2.1  launchd: starting, as task 1
 *    3.4  loginwindow: installing the look
 *   11.2  loginwindow: ready
 * </pre>
 *
 * The number is seconds since the machine started, not since this process did. Three
 * processes are involved in a boot and each one's clock starts when it does, so measuring
 * locally would make the times run backwards halfway through; whoever starts a process
 * passes the moment on in {@link #SINCE_PROPERTY}.
 *
 * A terminal reads these, and so does the boot screen, which knows the system is up
 * because the last line is {@code ready}. One narration and two readers, so there is no
 * second copy to keep true. They go to the error stream, since the log is a file on a
 * volume that may not be there yet, and to the log as well once there is one.
 */
public final class Progress {
    private Progress() {}

    /** The moment the machine started, in milliseconds, as passed from process to process. */
    public static final String SINCE_PROPERTY = "org.fractalmicro.booted";

    /** The last thing said on the way up, and what a boot screen waits for. */
    public static final String READY = "ready";

    /** Who is talking, once it has been said, so every line does not have to name it. */
    private static volatile String speaker = "system";

    private static final long SINCE = since();

    /**
     * When this boot began.
     *
     * From whoever started this process if they said, and otherwise from when this process
     * started. A system booted from the kernel is told; a desktop run straight out of a
     * checkout is the whole of its own boot and has nobody to be told by.
     */
    private static long since() {
        try {
            long said = Long.parseLong(System.getProperty(SINCE_PROPERTY, ""));
            if (said > 0) return said;
        } catch (NumberFormatException notSaid) {
            // Nobody said, so this process starting is the machine starting.
        }
        return System.currentTimeMillis() - upFor();
    }

    /** How long this process has been running, asked of the runtime rather than guessed. */
    private static long upFor() {
        return ProcessHandle.current().info().startInstant()
            .map(started -> System.currentTimeMillis() - started.toEpochMilli())
            .orElse(0L);
    }

    /** The moment this boot began, for passing to a process about to be started. */
    public static long began() { return SINCE; }

    /** Names whoever is talking: launchd, loginwindow, the name of a program. */
    public static void speakingAs(String who) { speaker = who; }

    /** One stage of coming up, said as it is reached rather than after it is done. */
    public static void say(String what) {
        line(String.format(Locale.ROOT, "%6.1f  %s: %s",
                           (System.currentTimeMillis() - SINCE) / 1000.0, speaker, what));
    }

    /** Said by the last thing to finish. A boot screen comes down when it sees this. */
    public static void ready() {
        say(READY);
    }

    /**
     * Said instead of {@link #ready()}, by something that cannot go on.
     *
     * A boot screen watching for the end of a boot has to hear about this end of one too,
     * or it sits over a machine that has stopped, spinning.
     */
    public static void failed(String why) {
        say("failed: " + why);
    }

    private static void line(String said) {
        System.err.println(said);
        System.err.flush();
        Log.info(said);
    }
}
