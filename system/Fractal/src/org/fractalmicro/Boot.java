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
package org.fractalmicro;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The launcher.
 *
 * The layer that talks to Windows uses the foreign function interface, which is a
 * preview feature on Java 21, so those classes will not load unless the virtual
 * machine was started with --enable-preview. Started without it the program half
 * works: no disk names, no Trash count, and About This Computer throws on the way up.
 *
 * This class is compiled without preview, so it always loads. If the rest of the
 * program cannot, it starts a second virtual machine with the right flags and steps
 * aside. Nothing else in the program has to care how it was launched.
 *
 * What it then starts is named in the manifest of the jar it came out of. A released
 * copy names the kernel, which finds a volume and boots it; a jar built from a checkout
 * names the development entry, which is the whole system in one file. The flag handling
 * is the same problem in both, and this is the only part of either that has to run
 * before it is known whether preview is on.
 */
public final class Boot {
    private Boot() {}

    private static final String CANARY = "org.fractalmicro.Preview";
    private static final String[] FLAGS = {"--enable-preview", "--enable-native-access=ALL-UNNAMED"};

    /** The manifest attribute naming what to start, and what to start without one. */
    private static final String ENTRY_ATTRIBUTE = "Fractal-Entry";
    private static final String DEFAULT_ENTRY = "org.fractalmicro.Main";

    public static void main(String[] args) throws Exception {
        if (previewEnabled()) {
            start(entry(), args);
            return;
        }
        if (!relaunch(args)) {
            String message = "FractalJDE needs Java 21 with preview features enabled.\n\n"
                + "Start it with:\n"
                + "  java --enable-preview --enable-native-access=ALL-UNNAMED -jar FractalJDE.jar";
            System.err.println(message);
            try {
                javax.swing.JOptionPane.showMessageDialog(null, message,
                    "Fractal Finder", javax.swing.JOptionPane.ERROR_MESSAGE);
            } catch (Throwable ignored) {
                // No display either; the console message is all there is.
            }
            System.exit(1);
        }
    }

    private static boolean previewEnabled() {
        try {
            Class.forName(CANARY, false, Boot.class.getClassLoader());
            return true;
        } catch (UnsupportedClassVersionError e) {
            return false;
        } catch (Throwable other) {
            // Something else is wrong, but not the flag; let the program report it.
            return true;
        }
    }

    /** Starts the same program again in a virtual machine that has the flags. */
    private static boolean relaunch(String[] args) {
        try {
            String java = javaCommand();
            String classPath = System.getProperty("java.class.path");
            if (java == null || classPath == null || classPath.isEmpty()) return false;

            List<String> command = new ArrayList<>();
            command.add(java);
            command.addAll(List.of(FLAGS));
            command.add("-cp");
            command.add(classPath);
            command.add(entry());
            command.addAll(List.of(args));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.inheritIO();
            pb.start();
            return true;
        } catch (Exception e) {
            System.err.println("could not restart with preview enabled: " + e.getMessage());
            return false;
        }
    }

    /**
     * What this launcher is a launcher for.
     *
     * Read out of the manifest rather than compiled in, because the same launcher ships in
     * two jars that start different things, and the alternative is two launchers that have
     * to be kept saying the same thing about preview flags.
     */
    private static String entry() {
        try {
            java.util.Enumeration<java.net.URL> manifests =
                Boot.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (manifests.hasMoreElements()) {
                try (java.io.InputStream in = manifests.nextElement().openStream()) {
                    String named = new java.util.jar.Manifest(in).getMainAttributes()
                                       .getValue(ENTRY_ATTRIBUTE);
                    if (named != null && !named.isBlank()) return named.trim();
                }
            }
        } catch (Exception unreadable) {
            // A jar without a readable manifest is a jar built the old way.
        }
        return DEFAULT_ENTRY;
    }

    /** Starts it, now that the flags are right. */
    private static void start(String named, String[] args) throws Exception {
        Class.forName(named).getMethod("main", String[].class).invoke(null, (Object) args);
    }

    /** The java or javaw this process was started with, so a window app stays one. */
    private static String javaCommand() {
        String running = ProcessHandle.current().info().command().orElse(null);
        if (running != null && new File(running).canExecute()) return running;
        String home = System.getProperty("java.home");
        if (home == null) return null;
        File javaw = new File(home, "bin\\javaw.exe");
        File java = new File(home, "bin\\java.exe");
        if (javaw.canExecute()) return javaw.getAbsolutePath();
        return java.canExecute() ? java.getAbsolutePath() : null;
    }
}
