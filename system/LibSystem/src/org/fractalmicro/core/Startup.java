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

import org.fractalmicro.win.Registry;
import org.fractalmicro.win.Session;

import java.io.File;
import java.util.*;

/**
 * The programs that start when you log in.
 *
 * Explorer runs these: the Run keys in the registry for the machine and for the user,
 * and the two Startup folders. Nobody else will, so as the shell this program has to.
 * As a program sitting on top of Explorer it must not, or everything would start twice,
 * so nothing is launched unless this really is the shell.
 *
 * RunOnce is read but never run here. Its entries are deleted as they are used, and
 * getting that wrong during someone's install is not worth the tidiness.
 */
public final class Startup {
    private Startup() {}

    private static final String RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String RUN_ONCE_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\RunOnce";

    /** One thing that wants to start at login. */
    public static final class Item {
        public final String name;
        public final String command;
        public final String source;
        public final boolean runOnce;

        Item(String name, String command, String source, boolean runOnce) {
            this.name = name;
            this.command = command;
            this.source = source;
            this.runOnce = runOnce;
        }

        @Override public String toString() { return name + "  (" + source + ")"; }
    }

    /** Everything that would start, wherever it is listed. */
    public static List<Item> items() {
        List<Item> out = new ArrayList<>();

        for (Map.Entry<String, String> e :
                Registry.values(Registry.HKEY_CURRENT_USER, RUN_KEY).entrySet()) {
            out.add(new Item(e.getKey(), e.getValue(), "this account", false));
        }
        for (Map.Entry<String, String> e :
                Registry.values(Registry.HKEY_LOCAL_MACHINE, RUN_KEY).entrySet()) {
            out.add(new Item(e.getKey(), e.getValue(), "this machine", false));
        }
        for (Map.Entry<String, String> e :
                Registry.values(Registry.HKEY_CURRENT_USER, RUN_ONCE_KEY).entrySet()) {
            out.add(new Item(e.getKey(), e.getValue(), "this account, once", true));
        }

        for (File folder : startupFolders()) {
            File[] kids = folder.listFiles();
            if (kids == null) continue;
            for (File f : kids) {
                if (f.isDirectory() || f.getName().equalsIgnoreCase("desktop.ini")) continue;
                out.add(new Item(stripExtension(f.getName()), f.getAbsolutePath(),
                                 folder.getName(), false));
            }
        }
        return out;
    }

    public static List<File> startupFolders() {
        List<File> folders = new ArrayList<>();
        String appData = System.getenv("APPDATA");
        String programData = System.getenv("ProgramData");
        if (appData != null) {
            folders.add(new File(appData, "Microsoft\\Windows\\Start Menu\\Programs\\Startup"));
        }
        if (programData != null) {
            folders.add(new File(programData, "Microsoft\\Windows\\Start Menu\\Programs\\Startup"));
        }
        folders.removeIf(f -> !f.isDirectory());
        return folders;
    }

    /**
     * Starts everything that should start. Does nothing unless this is the shell, since
     * otherwise Explorer has already done it. Returns what was started, or what would
     * have been.
     */
    public static List<Item> runAll(boolean dryRun) {
        List<Item> started = new ArrayList<>();
        boolean shell = Session.actingAsShell();
        if (!shell && !dryRun) {
            Log.info("start-up items left alone: Explorer is the shell here");
            return started;
        }
        for (Item item : items()) {
            if (item.runOnce) continue;                    // read, never run
            if (dryRun) {
                started.add(item);
                continue;
            }
            if (launch(item)) started.add(item);
        }
        Log.info((dryRun ? "would start " : "started ") + started.size() + " login items");
        return started;
    }

    /** Starts one item now, whoever the shell is. */
    public static boolean start(Item item) {
        return item != null && launch(item);
    }

    private static boolean launch(Item item) {
        try {
            File asFile = new File(item.command);
            if (asFile.isFile()) {
                Shell.open(asFile);
                return true;
            }
            List<String> command = splitCommand(item.command);
            if (command.isEmpty()) return false;
            Shell.launch(command.toArray(new String[0]));
            return true;
        } catch (Exception e) {
            Log.error("could not start the login item " + item.name, e);
            return false;
        }
    }

    /** Splits a command line, keeping a quoted program path in one piece. */
    public static List<String> splitCommand(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (char c : line.trim().toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            } else if (c == ' ' && !quoted) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) parts.add(current.toString());
        return parts;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
