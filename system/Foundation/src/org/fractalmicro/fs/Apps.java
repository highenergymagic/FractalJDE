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
package org.fractalmicro.fs;

import org.fractalmicro.core.Shell;
import org.fractalmicro.os.OSPaths;
import org.fractalmicro.win.LnkFile;
import org.fractalmicro.win.Registry;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * The Applications folder. Windows keeps its list of installed programs as shortcuts
 * in the Start menu, so those are read and mirrored into ~/.fractaldt/Applications,
 * with the ones Windows files under Accessories and Administrative Tools going to
 * Applications/Utilities.
 *
 * The default browser and mail client come from the UserChoice keys in the registry.
 */
public final class Apps {
    private Apps() {}

    private static volatile List<Node> apps = Collections.emptyList();
    private static volatile List<Node> utilities = Collections.emptyList();
    private static volatile Node browser;
    private static volatile Node mail;
    private static volatile Map<String, String> namesByExecutable = Collections.emptyMap();

    public static List<Node> applications() { return apps; }
    public static List<Node> utilities() { return utilities; }
    public static Node defaultBrowser() { return browser; }
    public static Node defaultMail() { return mail; }

    private static final String[] START_MENU = {
        System.getenv("ProgramData") + "\\Microsoft\\Windows\\Start Menu\\Programs",
        System.getenv("APPDATA") + "\\Microsoft\\Windows\\Start Menu\\Programs"
    };

    private static final Set<String> UTILITY_FOLDERS = new HashSet<>(Arrays.asList(
        "accessories", "system tools", "administrative tools", "windows tools",
        "windows administrative tools", "accessibility", "windows accessories",
        "windows powershell", "windows system"));

    public static void refresh(Runnable whenDone) {
        Shell.async(() -> {
            List<Node> all = new ArrayList<>();
            List<Node> util = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String root : START_MENU) {
                if (root == null) continue;
                scan(new File(root), all, util, seen, 0, false);
            }
            FS.sort(all, "name");
            FS.sort(util, "name");
            apps = all;
            utilities = util;
            indexExecutables(all, util);
            findDefaults();
            mirrorIntoApplicationsFolder(all, util);
            if (whenDone != null) javax.swing.SwingUtilities.invokeLater(whenDone);
        });
    }

    private static void scan(File dir, List<Node> all, List<Node> util,
                             Set<String> seen, int depth, boolean inUtilities) {
        if (depth > 4 || dir == null || !dir.isDirectory()) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.isDirectory()) {
                boolean u = inUtilities
                    || UTILITY_FOLDERS.contains(f.getName().toLowerCase(Locale.ROOT));
                scan(f, all, util, seen, depth + 1, u);
            } else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".lnk")) {
                String name = f.getName().substring(0, f.getName().length() - 4);
                if (!seen.add(name.toLowerCase(Locale.ROOT))) continue;
                Node n = new Node(Node.Kind.APPLICATION, name, f);
                if (inUtilities) util.add(n); else all.add(n);
            }
        }
    }

    /**
     * Keeps ~/.fractaldt/Applications looking like an Applications folder, with a
     * shortcut per program. Existing entries are left alone.
     */
    private static void mirrorIntoApplicationsFolder(List<Node> all, List<Node> util) {
        try {
            Files.createDirectories(OSPaths.applicationsUtilities());
            link(OSPaths.applications(), all);
            link(OSPaths.applicationsUtilities(), util);
        } catch (Exception e) {
            System.err.println("could not mirror the Applications folder: " + e.getMessage());
        }
    }

    private static void link(Path folder, List<Node> nodes) {
        for (Node n : nodes) {
            if (n.file == null) continue;
            Path target = folder.resolve(n.file.getName());
            if (Files.exists(target)) continue;
            try {
                Files.copy(n.file.toPath(), target);
            } catch (Exception ignored) {
                // A program that will not copy is not worth stopping start-up for.
            }
        }
    }

    /**
     * Maps each program's executable to the name its Start menu entry uses, so a window
     * belonging to 7zFM.exe can be labelled "7-Zip File Manager" rather than "7zFM".
     */
    private static void indexExecutables(List<Node> all, List<Node> util) {
        Map<String, String> index = new HashMap<>();
        List<Node> everything = new ArrayList<>(all);
        everything.addAll(util);
        for (Node n : everything) {
            if (n.file == null) continue;
            File target = LnkFile.target(n.file);
            if (target == null) continue;
            index.putIfAbsent(target.getAbsolutePath().toLowerCase(Locale.ROOT), n.name);
        }
        namesByExecutable = index;
    }

    /** The name the Start menu gives an executable, or null when it lists no such thing. */
    public static String nameForExecutable(File executable) {
        if (executable == null) return null;
        return namesByExecutable.get(executable.getAbsolutePath().toLowerCase(Locale.ROOT));
    }

    /* --------------------------------------------------------- defaults */

    private static void findDefaults() {
        browser = handlerFor("http", "web browser");
        mail = handlerFor("mailto", "mail client");
    }

    private static Node handlerFor(String scheme, String role) {
        String progId = Registry.string(Registry.HKEY_CURRENT_USER,
            "Software\\Microsoft\\Windows\\Shell\\Associations\\UrlAssociations\\"
            + scheme + "\\UserChoice", "ProgId");
        String command = progId == null ? null
            : Registry.string(Registry.HKEY_CLASSES_ROOT, progId + "\\shell\\open\\command", null);
        File exe = command == null ? null : executableFrom(command);

        String name = progId == null ? null : friendlyName(progId);
        if ((name == null || name.isBlank()) && exe != null) name = stripExtension(exe.getName());
        if (name == null || name.isBlank()) name = role;

        Node n = new Node(Node.Kind.APPLICATION, name, exe);
        n.detail = "Default " + role;
        return n;
    }

    private static String friendlyName(String progId) {
        String s = Registry.string(Registry.HKEY_CLASSES_ROOT, progId + "\\Application", "ApplicationName");
        if (s == null) s = Registry.string(Registry.HKEY_CLASSES_ROOT, progId, null);
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("@")) return null;
        return s.replace(" URL", "").replace(" Document", "").replace(" HTML", "").trim();
    }

    private static File executableFrom(String command) {
        String c = command.trim();
        String path;
        if (c.startsWith("\"")) {
            int end = c.indexOf('"', 1);
            path = end > 0 ? c.substring(1, end) : c;
        } else {
            int at = c.toLowerCase(Locale.ROOT).indexOf(".exe");
            path = at > 0 ? c.substring(0, at + 4) : c.split("\\s+")[0];
        }
        File f = new File(path);
        return f.exists() ? f : null;
    }

    private static String stripExtension(String n) {
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    /** Where a shortcut actually points, read out of the shortcut file itself. */
    public static File resolve(File shortcut) {
        if (shortcut == null) return null;
        if (!shortcut.getName().toLowerCase(Locale.ROOT).endsWith(".lnk")) return shortcut;
        File target = LnkFile.target(shortcut);
        return target == null ? shortcut : target;
    }
}
