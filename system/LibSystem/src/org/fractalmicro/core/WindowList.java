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


import org.fractalmicro.win.User32;

import java.io.File;
import java.util.*;

/**
 * What is running, taken from the windows on screen rather than from what this program
 * happened to start. With the taskbar gone this is the only list of running programs
 * there is, so the Dock and the window menus are built from it.
 *
 * Windows are grouped by the program they belong to rather than by process, the way a
 * Dock groups them: seven Notepad windows are seven processes on Windows and one
 * application here. Names come from the Start menu entry pointing at the same
 * executable, so "7zFM.exe" reads as "7-Zip File Manager".
 */
public final class WindowList {

    /**
     * How an executable's name becomes the name a person knows it by.
     *
     * The list of windows comes from the host system and holds file names. What a program
     * is called is a question about what is installed, which belongs to a layer above this
     * one, so it is handed in rather than reached for.
     */
    private static volatile java.util.function.Function<java.io.File, String> naming = f -> null;

    public static void setNaming(java.util.function.Function<java.io.File, String> how) {
        naming = how == null ? f -> null : how;
    }
    private WindowList() {}

    /** One running program and the windows it has open. */
    public static final class App {
        public final String name;
        public final File executable;
        public final long pid;
        public final List<User32.Win> windows;

        App(String name, File executable, long pid, List<User32.Win> windows) {
            this.name = name;
            this.executable = executable;
            this.pid = pid;
            this.windows = windows;
        }

        public boolean allMinimized() {
            for (User32.Win w : windows) if (!w.minimized) return false;
            return !windows.isEmpty();
        }

        /** Whether any of its windows has stopped taking anything off its queue. */
        public boolean notResponding() {
            for (User32.Win w : windows) if (w.notResponding) return true;
            return false;
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof App)) return false;
            App a = (App) o;
            return pid == a.pid && name.equals(a.name) && windows.size() == a.windows.size();
        }

        @Override public int hashCode() { return Objects.hash(pid, name, windows.size()); }
    }

    private static volatile List<App> apps = Collections.emptyList();
    private static final List<Runnable> LISTENERS = new ArrayList<>();
    private static javax.swing.Timer poll;
    private static long ownProcess = ProcessHandle.current().pid();

    public static List<App> applications() { return apps; }

    /** Whether the program of that name has stopped answering. */
    public static boolean notResponding(String name) {
        for (App app : apps) {
            if (app.name.equals(name)) return app.notResponding();
        }
        return false;
    }

    public static void onChange(Runnable r) { LISTENERS.add(r); }

    /** Starts watching. Polling for now; a shell hook can replace this later. */
    public static void start() {
        if (poll != null) return;
        refresh();
        poll = new javax.swing.Timer(1500, e -> refresh());
        poll.start();
    }

    public static void refresh() {
        Shell.async(() -> {
            List<App> found = gather();
            if (found.equals(apps)) return;
            apps = found;
            javax.swing.SwingUtilities.invokeLater(() -> {
                for (Runnable r : new ArrayList<>(LISTENERS)) r.run();
            });
        });
    }

    private static List<App> gather() {
        // Grouped by program, not by process. Seven Notepad windows are seven processes
        // on Windows and one application in a Dock, and the Dock is right.
        Map<String, List<User32.Win>> byProgram = new LinkedHashMap<>();
        Map<String, File> executables = new LinkedHashMap<>();
        Map<String, Long> firstPid = new LinkedHashMap<>();

        for (User32.Win w : User32.taskWindows()) {
            if (w.pid == ownProcess) continue;             // our own desktop is not a Dock tile
            File executable = ProcessHandle.of(w.pid)
                .flatMap(p -> p.info().command())
                .map(File::new)
                .orElse(null);
            String key = executable != null
                ? executable.getAbsolutePath().toLowerCase(Locale.ROOT)
                : "pid:" + w.pid;
            byProgram.computeIfAbsent(key, k -> new ArrayList<>()).add(w);
            executables.putIfAbsent(key, executable);
            firstPid.putIfAbsent(key, w.pid);
        }

        List<App> out = new ArrayList<>();
        for (Map.Entry<String, List<User32.Win>> entry : byProgram.entrySet()) {
            File executable = executables.get(entry.getKey());
            out.add(new App(nameFor(executable, entry.getValue()), executable,
                            firstPid.get(entry.getKey()), entry.getValue()));
        }
        out.sort(Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    /** The Start menu's name for the program, or the file name without its extension. */
    private static String nameFor(File executable, List<User32.Win> windows) {
        if (executable != null) {
            String known = naming.apply(executable);
            if (known != null) return known;
            String name = executable.getName();
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            return name;
        }
        return windows.isEmpty() ? "Application" : windows.get(0).title;
    }

    /* ----------------------------------------------------------- commands */

    /** Brings a program forward: its front window, or the next one if it is already there. */
    public static void activate(App app) {
        if (app == null || app.windows.isEmpty()) return;
        long foreground = User32.foregroundWindow();
        int index = 0;
        for (int i = 0; i < app.windows.size(); i++) {
            if (app.windows.get(i).handle == foreground) {
                index = (i + 1) % app.windows.size();
                break;
            }
        }
        User32.activate(app.windows.get(index).handle);
        refresh();
    }

    public static void activate(User32.Win window) {
        if (window == null) return;
        User32.activate(window.handle);
        refresh();
    }

    public static void hide(App app) {
        if (app == null) return;
        for (User32.Win w : app.windows) User32.minimize(w.handle);
        refresh();
    }

    /** Asks every window of a program to close, which is how a program is quit. */
    public static void quit(App app) {
        if (app == null) return;
        for (User32.Win w : app.windows) User32.close(w.handle);
        refresh();
    }
}
