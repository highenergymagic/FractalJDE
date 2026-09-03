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


import java.awt.Desktop;
import java.io.File;
import java.util.concurrent.*;

/**
 * Opening things, and a thread pool for work that must stay off the event thread.
 *
 * Everything the system is asked for goes through the native layer in org.fractalmicro.win.
 * The only processes started here are the ones the user asked to start: an
 * application, a browser, a terminal window.
 */
public final class Shell {
    private Shell() {}

    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "fractal-worker");
        t.setDaemon(true);
        return t;
    });

    public static void async(Runnable task) { POOL.execute(task); }

    public static <T> Future<T> async(Callable<T> task) { return POOL.submit(task); }

    /** Opens a file the way a double click would. */
    public static void open(File target) {
        if (target == null) return;
        try {
            Desktop.getDesktop().open(target);
        } catch (Exception e) {
            launch(target.getAbsolutePath());
        }
    }

    public static void browse(String url) {
        try {
            Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception e) {
            launch(url);
        }
    }

    /** Starts a program. Used for the Terminal and Explorer menu items. */
    public static void launch(String... command) {
        try {
            new ProcessBuilder(command).start();
        } catch (Exception e) {
            System.err.println("could not start " + String.join(" ", command) + ": " + e.getMessage());
        }
    }

    /** Opens Windows Explorer with the item selected, for the escape-hatch menu item. */
    public static void revealInExplorer(File target) {
        if (target == null) return;
        launch("explorer.exe", "/select," + target.getAbsolutePath());
    }

    /**
     * A command line where you are, running the shell this system has.
     *
     * Which program that is arrives, since it is a fact about the volume and this layer
     * knows nothing about volumes. The console of its own is the part the runtime cannot
     * ask for, and a console is what a terminal is made of.
     */
    public static long openTerminal(File directory, java.util.List<String> command,
                                    boolean visible) {
        File dir = directory != null && directory.isDirectory()
            ? directory : new File(System.getProperty("user.home"));
        if (command == null || command.isEmpty()) {
            System.err.println("no shell to open a terminal with");
            return 0;
        }
        long pid = org.fractalmicro.win.Console32.startWithConsole(command, dir, visible);
        if (pid == 0) System.err.println("could not open a terminal");
        return pid;
    }
}
