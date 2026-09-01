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

import java.io.File;
import java.util.*;

/**
 * Spotlight's search engine: a bounded walk of the file tree, matching on name.
 * No index, so searches are capped by result count and by how deep they go.
 */
public final class Search {

    /**
     * An index that can answer faster than a walk of the tree, when one is running.
     *
     * Foundation knows how to look through a directory. Whether something is keeping an
     * index of the whole volume, and how to ask it, belongs above; it is handed in.
     */
    public interface Index {
        boolean running();
        java.util.List<java.io.File> search(String query, int limit);
    }

    private static volatile Index index;

    public static void setIndex(Index how) { index = how; }
    private Search() {}

    public static List<Node> inFolder(File root, String query, int limit) {
        List<Node> out = new ArrayList<>();
        if (root == null || query == null || query.isBlank()) return out;
        walk(root, query.toLowerCase(Locale.ROOT), out, limit, 0);
        return out;
    }

    /**
     * Searching everywhere. The metadata server knows where things are and answers in a
     * message; when it is not running the same search is done here by walking the disk,
     * which is slower and finds the same things.
     */
    public static List<Node> everywhere(String query, int limit) {
        List<Node> out = new ArrayList<>();
        if (query == null || query.isBlank()) return out;
        String q = query.toLowerCase(Locale.ROOT);

        // Programs first, whoever is answering: someone searching for a program wants the
        // program, not a document that happens to mention it.
        for (Node app : Apps.applications()) {
            if (app.name.toLowerCase(Locale.ROOT).contains(q)) out.add(app);
            if (out.size() >= limit) return out;
        }
        for (Node app : Apps.utilities()) {
            if (app.name.toLowerCase(Locale.ROOT).contains(q)) out.add(app);
            if (out.size() >= limit) return out;
        }

        Index fast = index;
        if (fast != null && fast.running()) {
            for (java.io.File hit : fast.search(query, limit)) {
                if (out.size() >= limit) break;
                if (hit.exists()) out.add(FS.node(hit));
            }
            return out;
        }

        return walkEverywhere(q, out, limit);
    }

    /** Searches the usual places: home, the desktop folder and the applications list. */
    private static List<Node> walkEverywhere(String q, List<Node> out, int limit) {
        for (Node app : Apps.applications()) {
            if (app.name.toLowerCase(Locale.ROOT).contains(q)) out.add(app);
            if (out.size() >= limit) return out;
        }
        for (Node app : Apps.utilities()) {
            if (app.name.toLowerCase(Locale.ROOT).contains(q)) out.add(app);
            if (out.size() >= limit) return out;
        }
        walk(FS.desktopFolder(), q, out, limit, 0);
        walk(FS.home(), q, out, limit, 0);
        return out;
    }

    /** Which of the two ways the last search went, for the window to say so. */
    public static boolean serverAnswering() {
        Index fast = index;
        return fast != null && fast.running();
    }

    private static void walk(File dir, String query, List<Node> out, int limit, int depth) {
        if (out.size() >= limit || depth > 6 || dir == null) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (out.size() >= limit) return;
            if (FS.isHidden(f)) continue;
            if (f.getName().toLowerCase(Locale.ROOT).contains(query)) out.add(FS.node(f));
            if (f.isDirectory()) walk(f, query, out, limit, depth + 1);
        }
    }

    /** The saved searches in the sidebar. */
    public static List<Node> saved(String which, int limit) {
        long now = System.currentTimeMillis();
        long day = 24L * 60 * 60 * 1000;
        List<Node> out = new ArrayList<>();
        collect(FS.home(), out, limit, 0, n -> {
            switch (which) {
                case "today": return n.modified > now - day;
                case "week": return n.modified > now - 7 * day;
                case "images": return matchesExtension(n, "png", "jpg", "jpeg", "gif", "bmp", "tif", "tiff", "webp");
                default: return matchesExtension(n, "txt", "rtf", "pdf", "doc", "docx", "odt", "md", "pages");
            }
        });
        return out;
    }

    private static boolean matchesExtension(Node n, String... exts) {
        if (n.file == null) return false;
        String name = n.file.getName().toLowerCase(Locale.ROOT);
        for (String e : exts) if (name.endsWith("." + e)) return true;
        return false;
    }

    private static void collect(File dir, List<Node> out, int limit, int depth,
                                java.util.function.Predicate<Node> test) {
        if (out.size() >= limit || depth > 5 || dir == null) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (out.size() >= limit) return;
            if (FS.isHidden(f)) continue;
            if (f.isDirectory()) {
                collect(f, out, limit, depth + 1, test);
            } else {
                Node n = FS.node(f);
                if (test.test(n)) out.add(n);
            }
        }
    }
}
