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


import java.io.File;
import java.util.*;

/**
 * The applications this desktop has launched. Windows keeps the real process list;
 * this is just what the Dock and the Force Quit window need to show a light next to
 * the things the user started from here.
 */
public final class Running {
    private Running() {}

    public static class Entry {
        public final String name;
        public final File launcher;
        public final long startedAt;
        public Process process;

        Entry(String name, File launcher) {
            this.name = name;
            this.launcher = launcher;
            this.startedAt = System.currentTimeMillis();
        }

        public boolean alive() {
            return process == null || process.isAlive();
        }
    }

    private static final LinkedHashMap<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static final List<Runnable> LISTENERS = new ArrayList<>();

    public static synchronized List<Entry> all() {
        return new ArrayList<>(ENTRIES.values());
    }

    public static synchronized boolean isRunning(String name) {
        return ENTRIES.containsKey(key(name));
    }

    public static synchronized Entry note(String name, File launcher) {
        Entry e = ENTRIES.computeIfAbsent(key(name), k -> new Entry(name, launcher));
        fire();
        return e;
    }

    public static synchronized void forget(String name) {
        if (ENTRIES.remove(key(name)) != null) fire();
    }

    public static void onChange(Runnable r) { LISTENERS.add(r); }

    private static void fire() {
        List<Runnable> copy = new ArrayList<>(LISTENERS);
        javax.swing.SwingUtilities.invokeLater(() -> copy.forEach(Runnable::run));
    }

    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }
}
