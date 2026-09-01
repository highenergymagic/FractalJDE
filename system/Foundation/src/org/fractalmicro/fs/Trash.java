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
import org.fractalmicro.win.Shell32;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * The Trash, which here is the Recycle Bin.
 *
 * The count comes from SHQueryRecycleBin. The contents are read from the $I metadata
 * files Windows writes beside each deleted item: an eight byte version, the original
 * size, a FILETIME, and the original path as UTF-16. Version 1 keeps the path in a
 * fixed 520 byte field; version 2 puts a character count in front of it.
 */
public final class Trash {
    private Trash() {}

    private static volatile int itemCount;
    private static volatile long byteCount;
    private static final List<Runnable> LISTENERS = new ArrayList<>();

    public static int count() { return itemCount; }
    public static long bytes() { return byteCount; }
    public static boolean isEmpty() { return itemCount == 0; }

    public static void onChange(Runnable r) { LISTENERS.add(r); }

    private static void fireChanged() {
        javax.swing.SwingUtilities.invokeLater(() -> {
            for (Runnable r : new ArrayList<>(LISTENERS)) r.run();
        });
    }

    public static void refresh() {
        Shell.async(() -> {
            long[] info = Shell32.recycleBinInfo();
            int items = (int) info[0];
            if (items != itemCount || info[1] != byteCount) {
                itemCount = items;
                byteCount = info[1];
                fireChanged();
            }
        });
    }

    /* ------------------------------------------------------------ contents */

    /** Everything in the bin, across every drive this account can read. */
    public static List<Node> list() {
        List<Node> out = new ArrayList<>();
        for (String root : Kernel32.logicalDrives()) {
            File bin = new File(root, "$Recycle.Bin");
            File[] perUser = bin.listFiles();
            if (perUser == null) continue;
            for (File userBin : perUser) {
                File[] entries = userBin.listFiles();
                if (entries == null) continue;                 // another account's folder
                for (File entry : entries) {
                    if (!entry.getName().startsWith("$I")) continue;
                    Node n = readMetadata(entry);
                    if (n != null) out.add(n);
                }
            }
        }
        out.sort((a, b) -> Long.compare(b.modified, a.modified));
        return out;
    }

    /** Parses one $I file into a node whose file points at the matching $R item. */
    private static Node readMetadata(File index) {
        try {
            byte[] bytes = Files.readAllBytes(index.toPath());
            if (bytes.length < 24) return null;
            ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            long version = b.getLong(0);
            long size = b.getLong(8);
            long fileTime = b.getLong(16);

            String path;
            if (version == 1) {
                if (bytes.length < 24 + 4) return null;
                path = readWide(bytes, 24, Math.min(520, bytes.length - 24) / 2);
            } else if (version == 2) {
                int characters = b.getInt(24);
                if (characters <= 0 || 28 + characters * 2 > bytes.length) return null;
                path = readWide(bytes, 28, characters);
            } else {
                return null;
            }

            File original = new File(path);
            File deleted = new File(index.getParentFile(), "$R" + index.getName().substring(2));

            Node n = new Node(deleted.isDirectory() ? Node.Kind.FOLDER : FS.kindOf(original),
                              original.getName(), deleted.exists() ? deleted : null);
            n.size = size;
            n.modified = fileTimeToMillis(fileTime);
            n.detail = "Original: " + (original.getParent() == null ? path : original.getParent());
            return n;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String readWide(byte[] bytes, int at, int characters) {
        int end = at;
        int limit = Math.min(bytes.length - 1, at + characters * 2);
        while (end < limit && !(bytes[end] == 0 && bytes[end + 1] == 0)) end += 2;
        return new String(bytes, at, end - at, StandardCharsets.UTF_16LE);
    }

    /** FILETIME is 100 nanosecond ticks since 1601. */
    private static long fileTimeToMillis(long fileTime) {
        if (fileTime <= 0) return 0;
        return fileTime / 10000L - 11644473600000L;
    }

    /* ------------------------------------------------------------ actions */

    /**
     * Moves files to the Recycle Bin through the desktop integration, which calls
     * the same shell operation Explorer does, undo entry and all.
     */
    public static int moveToTrash(List<File> files) {
        Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
        boolean supported = desktop != null && desktop.isSupported(Desktop.Action.MOVE_TO_TRASH);
        int moved = 0;
        for (File f : files) {
            if (f == null || !f.exists()) continue;
            boolean ok = false;
            if (supported) {
                try {
                    ok = desktop.moveToTrash(f);
                } catch (Exception e) {
                    ok = false;
                }
            }
            if (ok) moved++;
        }
        refresh();
        return moved;
    }

    public static boolean canMoveToTrash() {
        return Desktop.isDesktopSupported()
            && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
    }

    public static void empty(boolean silent) {
        Shell.async(() -> {
            Shell32.emptyRecycleBin(silent);
            itemCount = 0;
            byteCount = 0;
            fireChanged();
        });
    }
}
