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
import java.nio.file.*;
import java.util.*;

/** Recent Items and Recent Folders, kept between sessions in a short text file. */
public final class Recent {
    private Recent() {}

    private static final int LIMIT = 10;
    private static final Path STORE =
        Paths.get(System.getProperty("user.home"), ".fractalfinder", "recents.txt");

    private static final LinkedList<File> ITEMS = new LinkedList<>();
    private static final LinkedList<File> FOLDERS = new LinkedList<>();

    static { load(); }

    public static synchronized List<File> items() { return new ArrayList<>(ITEMS); }
    public static synchronized List<File> folders() { return new ArrayList<>(FOLDERS); }

    public static synchronized void noteItem(File f) {
        if (f == null) return;
        push(ITEMS, f);
        save();
    }

    public static synchronized void noteFolder(File f) {
        if (f == null || !f.isDirectory()) return;
        push(FOLDERS, f);
        save();
    }

    private static void push(LinkedList<File> list, File f) {
        list.removeIf(x -> x.getAbsolutePath().equalsIgnoreCase(f.getAbsolutePath()));
        list.addFirst(f);
        while (list.size() > LIMIT) list.removeLast();
    }

    public static synchronized void clear() {
        ITEMS.clear();
        FOLDERS.clear();
        save();
    }

    private static void load() {
        try {
            if (!Files.exists(STORE)) return;
            for (String line : Files.readAllLines(STORE)) {
                if (line.startsWith("item\t")) ITEMS.add(new File(line.substring(5)));
                else if (line.startsWith("folder\t")) FOLDERS.add(new File(line.substring(7)));
            }
        } catch (Exception ignored) { }
    }

    private static void save() {
        try {
            Files.createDirectories(STORE.getParent());
            List<String> lines = new ArrayList<>();
            for (File f : ITEMS) lines.add("item\t" + f.getAbsolutePath());
            for (File f : FOLDERS) lines.add("folder\t" + f.getAbsolutePath());
            Files.write(STORE, lines);
        } catch (Exception ignored) { }
    }
}
