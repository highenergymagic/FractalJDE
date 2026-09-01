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
import org.fractalmicro.win.Kernel32;

import java.io.File;
import java.util.*;

/**
 * Mounted volumes, read from kernel32. The drive type decides which of Finder's four
 * desktop checkboxes a volume answers to: fixed disks are hard disks, removable ones
 * are external disks, optical drives stand in for CDs and DVDs, and network drives are
 * mounted servers.
 */
public final class Volumes {
    private Volumes() {}

    private static volatile List<Node> cache = Collections.emptyList();
    private static final List<Runnable> LISTENERS = new ArrayList<>();

    public static List<Node> all() { return cache; }

    public static void onChange(Runnable r) { LISTENERS.add(r); }

    public static List<Node> ofKind(Node.Kind kind) {
        List<Node> out = new ArrayList<>();
        for (Node n : cache) if (n.kind == kind) out.add(n);
        return out;
    }

    /** Reads the drive table off the event thread; a not-ready drive can take a moment. */
    public static void refresh(Runnable whenDone) {
        Shell.async(() -> {
            List<Node> found = query();
            boolean changed = !found.equals(cache);
            cache = found;
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (changed) for (Runnable r : new ArrayList<>(LISTENERS)) r.run();
                if (whenDone != null) whenDone.run();
            });
        });
    }

    private static List<Node> query() {
        List<Node> list = new ArrayList<>();
        try {
            list = queryNative();
        } catch (Throwable t) {
            org.fractalmicro.core.Log.error("the drive list could not be read", t);
        }
        if (list.isEmpty()) {
            // Better a plain drive letter than an empty desktop.
            for (File root : File.listRoots()) {
                Node n = new Node(Node.Kind.HARD_DISK, root.getAbsolutePath(), root);
                n.mountPoint = root.getAbsolutePath();
                n.size = root.getTotalSpace();
                n.free = root.getFreeSpace();
                list.add(n);
            }
            org.fractalmicro.core.Log.info("fell back to File.listRoots, " + list.size() + " drives");
        }
        return list;
    }

    private static List<Node> queryNative() {
        List<Node> list = new ArrayList<>();
        for (String root : Kernel32.logicalDrives()) {
            int type = Kernel32.driveType(root);
            Node.Kind kind;
            switch (type) {
                case Kernel32.DRIVE_REMOVABLE: kind = Node.Kind.EXTERNAL_DISK; break;
                case Kernel32.DRIVE_REMOTE:    kind = Node.Kind.SERVER; break;
                case Kernel32.DRIVE_CDROM:     kind = Node.Kind.REMOVABLE_MEDIA; break;
                case Kernel32.DRIVE_NO_ROOT_DIR: continue;
                default:                       kind = Node.Kind.HARD_DISK; break;
            }

            String label = Kernel32.volumeLabel(root);
            if (label.isEmpty()) label = shellName(root);
            if (label.isEmpty()) label = defaultName(kind, root);
            long[] space = Kernel32.diskSpace(root);

            Node n = new Node(kind, label, new File(root));
            n.size = space[0];
            n.free = space[1];
            n.fileSystem = Kernel32.fileSystem(root);
            n.mountPoint = root;
            list.add(n);
        }
        org.fractalmicro.core.Log.info("volumes: " + list.size() + " found");
        return list;
    }

    /**
     * The name Windows shows for a drive, with the drive letter taken off the end:
     * "Local Disk (C:)" becomes "Local Disk". A volume with a label uses the label.
     */
    private static String shellName(String root) {
        String display = org.fractalmicro.win.Shell32.displayName(root).trim();
        if (display.isEmpty()) return "";
        int bracket = display.lastIndexOf(" (");
        if (bracket > 0 && display.endsWith(":)")) display = display.substring(0, bracket);
        return display.trim();
    }

    /** A drive that answers to nothing at all still needs a name. */
    private static String defaultName(Node.Kind kind, String root) {
        switch (kind) {
            case EXTERNAL_DISK: return "Removable Disk";
            case REMOVABLE_MEDIA: return "Optical Drive";
            case SERVER: return "Network Drive";
            default: return "Local Disk";
        }
    }

    public static Node startupDisk() {
        Node known = startupIn(cache);
        if (known != null) return known;
        // Nothing has been read yet, which happens early and in checking runs. The disk is
        // asked about directly rather than a disk being made up: an invented one has no
        // size, and a window about it says the drive is empty when it plainly is not.
        Node found = startupIn(query());
        if (found != null) return found;
        // Last of all, the drive the host booted from. Asking is one call and the answer
        // is right on a machine that boots from something other than C, which is not
        // exotic: a machine with two systems on it, or one imaged from another.
        String root = systemDrive();
        Node n = new Node(Node.Kind.HARD_DISK, defaultName(Node.Kind.HARD_DISK, root),
                          new File(root));
        n.mountPoint = root;
        return n;
    }

    /**
     * The drive the host system booted from, as a root.
     *
     * Windows says so in an environment variable it sets for every process. Assuming C is
     * right almost always and wrong in a way that is hard to see when it is.
     */
    public static String systemDrive() {
        String said = System.getenv("SystemDrive");
        return said == null || said.isBlank() ? "C:\\" : said + "\\";
    }

    private static Node startupIn(List<Node> volumes) {
        for (Node n : volumes) {
            if (n.mountPoint != null && n.mountPoint.toUpperCase(Locale.ROOT).startsWith("C")) {
                return n;
            }
        }
        return null;
    }

    /** The volume a path sits on, used for the free space in a window's status bar. */
    public static Node containing(File file) {
        if (file == null) return null;
        String path = file.getAbsolutePath().toUpperCase(Locale.ROOT);
        for (Node n : cache) {
            if (n.mountPoint != null && path.startsWith(n.mountPoint.toUpperCase(Locale.ROOT))) return n;
        }
        return null;
    }
}
