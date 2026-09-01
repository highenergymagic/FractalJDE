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

import org.fractalmicro.plist.Plist;

import org.fractalmicro.os.OSPaths;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One job: something the system runs, described by a property list.
 *
 * The keys are launchd's own, since the format is documented and there is no reason to
 * invent another:
 *
 *   Label               what the job is called, and how it is referred to afterwards
 *   ProgramArguments    the command and its arguments
 *   RunAtLoad           start it as soon as it is loaded
 *   KeepAlive           start it again when it stops
 *   ThrottleInterval    how long to wait before starting it again, in seconds
 *   MachServices        the names it will serve, so callers know what it provides
 *   StandardOutPath     where what it writes goes
 *   StandardErrorPath   and where its complaints go
 *   WorkingDirectory    where it runs
 *   EnvironmentVariables what it runs with
 *   Disabled            leave it alone entirely
 */
public final class Job {

    public static final String LABEL = "Label";
    public static final String PROGRAM_ARGUMENTS = "ProgramArguments";
    public static final String RUN_AT_LOAD = "RunAtLoad";
    public static final String KEEP_ALIVE = "KeepAlive";
    public static final String THROTTLE = "ThrottleInterval";
    public static final String MACH_SERVICES = "MachServices";
    public static final String STANDARD_OUT = "StandardOutPath";
    public static final String STANDARD_ERROR = "StandardErrorPath";
    public static final String WORKING_DIRECTORY = "WorkingDirectory";
    public static final String ENVIRONMENT = "EnvironmentVariables";
    public static final String DISABLED = "Disabled";

    /** The shortest a job may wait before being started again, as launchd has it. */
    public static final int DEFAULT_THROTTLE = 10;

    private final Map<String, Object> values;
    private final Path file;

    private Job(Map<String, Object> values, Path file) {
        this.values = values;
        this.file = file;
    }

    public static Job read(Path file) throws IOException {
        Map<String, Object> values = Plist.readDictionary(file);
        if (!(values.get(LABEL) instanceof String label) || label.isBlank()) {
            throw new IOException(file.getFileName() + " has no Label");
        }
        return new Job(values, file);
    }

    public static Job of(Map<String, Object> values) {
        return new Job(new LinkedHashMap<>(values), null);
    }

    public Path file() { return file; }

    public String label() { return string(LABEL, ""); }

    public boolean disabled() { return bool(DISABLED, false); }

    public boolean runAtLoad() { return bool(RUN_AT_LOAD, false); }

    public boolean keepAlive() { return bool(KEEP_ALIVE, false); }

    /**
     * How long this job asks to wait before being started again, in seconds.
     *
     * A description may say zero, and one that does is asking for something the supervisor
     * will not do: what is actually waited is this or the supervisor's own floor, whichever
     * is longer. This answers what was asked for; {@link Launchd} decides what happens.
     */
    public int throttle() {
        Object value = values.get(THROTTLE);
        return value instanceof Number n ? Math.max(0, n.intValue()) : DEFAULT_THROTTLE;
    }

    public List<String> programArguments() {
        List<String> out = new ArrayList<>();
        Object value = values.get(PROGRAM_ARGUMENTS);
        if (value instanceof List<?> list) {
            for (Object one : list) out.add(resolved(String.valueOf(one)));
        }
        return out;
    }

    /** What a job may write instead of a path that would only be right on one machine. */
    public static final String VOLUME = "${ROOT}";
    public static final String RUNTIME = "${JAVA}";
    public static final String LOGS = "${LOGS}";

    /**
     * Fills in the things a job cannot know when it is written.
     *
     * A job file ships inside a system image. It is written on one machine and unpacked on
     * another: under a different home directory, for an account with a different name,
     * against a runtime installed somewhere else entirely. Everything else in a job is the
     * same everywhere; these are not, and a job that spelled any of them out would start
     * nothing anywhere but where it was written.
     *
     * Mac OS X does not need this because its volume is / and its runtime is at a fixed
     * path. Here the volume is wherever somebody's home directory is, so the job says what
     * it means and launchd, which is running from that volume under that runtime, is the
     * one that knows.
     */
    private static String resolved(String argument) {
        if (!argument.contains("${")) return argument;
        return argument.replace(VOLUME, OSPaths.ROOT.toString())
                       .replace(LOGS, OSPaths.userLibrary().resolve("Logs").toString())
                       .replace(RUNTIME, OSPaths.javaCommand());
    }

    /** The names this job says it will serve, so a caller knows what to wait for. */
    public List<String> machServices() {
        List<String> out = new ArrayList<>();
        Object value = values.get(MACH_SERVICES);
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!Boolean.FALSE.equals(entry.getValue())) out.add(String.valueOf(entry.getKey()));
            }
        }
        return out;
    }

    public Path workingDirectory() { return path(WORKING_DIRECTORY); }

    public Path standardOut() { return path(STANDARD_OUT); }

    public Path standardError() { return path(STANDARD_ERROR); }

    private Path path(String key) {
        String value = resolved(string(key, ""));
        if (value.isEmpty()) return null;
        try {
            return Path.of(value);
        } catch (java.nio.file.InvalidPathException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> environment() {
        Map<String, String> out = new LinkedHashMap<>();
        Object value = values.get(ENVIRONMENT);
        if (value instanceof Map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                out.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return out;
    }

    public Map<String, Object> values() { return new LinkedHashMap<>(values); }

    private String string(String key, String fallback) {
        Object value = values.get(key);
        return value instanceof String s ? s : fallback;
    }

    private boolean bool(String key, boolean fallback) {
        Object value = values.get(key);
        if (value instanceof Boolean b) return b;
        // KeepAlive is also allowed to be a dictionary of conditions; anything there at
        // all means the job is to be kept alive, which is as much as this reads of it.
        if (KEEP_ALIVE.equals(key) && value instanceof Map<?, ?> map) return !map.isEmpty();
        return fallback;
    }

    @Override public String toString() {
        return label() + (disabled() ? " (disabled)" : "")
             + (machServices().isEmpty() ? "" : " serving " + String.join(", ", machServices()));
    }
}
