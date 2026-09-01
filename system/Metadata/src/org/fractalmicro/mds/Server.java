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
package org.fractalmicro.mds;


import org.fractalmicro.core.Log;
import org.fractalmicro.os.OSPaths;
import org.fractalmicro.xpc.Message;
import org.fractalmicro.xpc.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The metadata server: the index behind Spotlight.
 *
 * It runs as a process of its own, started by the system rather than by whoever wants to
 * search. It walks the folders worth knowing about, keeps what it found, and answers
 * questions about it over a named port. Searching then costs a message rather than a walk
 * of the disk, which is the entire reason for indexing anything.
 *
 * This is the first thing in this system to be a program in its own right rather than a
 * part of the desktop, and the shape of it is the shape the rest will take: a job
 * description, a name it serves, a store under private/var/db, and no idea who is asking.
 */
public final class Server {

    public static final String SERVICE = "org.fractalmicro.metadata";
    public static final String LABEL = "org.fractalmicro.metadata";

    /** Messages this server answers. */
    public static final String QUERY = "query";
    public static final String STATUS = "status";
    public static final String REINDEX = "reindex";
    public static final String STOP = "stop";

    private final Map<String, String> index = new ConcurrentHashMap<>();
    private volatile boolean indexing;
    private volatile long indexedAt;
    private Service service;

    public static void main(String[] args) throws Exception {
        Log.install();
        Server server = new Server();
        if (!server.start()) {
            System.err.println("another metadata server is already running");
            System.exit(1);
        }
        server.indexInBackground();
        // The process stays up until it is stopped; the service answers on its own threads.
        Object forever = new Object();
        synchronized (forever) {
            forever.wait();
        }
    }

    public boolean start() {
        load();
        service = new Service(SERVICE, this::answer);
        return service.start();
    }

    public void stop() {
        if (service != null) service.close();
    }

    /* ------------------------------------------------------------ answering */

    private Message answer(Message request) {
        return switch (request.type()) {
            case QUERY -> query(request.string("text", ""),
                                (int) request.integer("limit", 50));
            case STATUS -> Message.of(STATUS)
                .put("items", (long) index.size())
                .put("indexing", indexing)
                .put("indexedAt", indexedAt)
                .put("pid", ProcessHandle.current().pid());
            case REINDEX -> {
                indexInBackground();
                yield Message.of(REINDEX).put("started", Boolean.TRUE);
            }
            case STOP -> {
                new Thread(() -> {
                    save();
                    System.exit(0);
                }, "metadata-stop").start();
                yield Message.of(STOP).put("stopping", Boolean.TRUE);
            }
            default -> Message.error("this server does not answer " + request.type());
        };
    }

    /** Everything whose name contains what was asked for, nearest matches first. */
    public Message query(String text, int limit) {
        List<Object> paths = new ArrayList<>();
        List<Object> names = new ArrayList<>();
        String wanted = text == null ? "" : text.trim().toLowerCase(java.util.Locale.ROOT);
        if (!wanted.isEmpty()) {
            List<Map.Entry<String, String>> hits = new ArrayList<>();
            for (Map.Entry<String, String> entry : index.entrySet()) {
                if (entry.getValue().contains(wanted)) hits.add(entry);
            }
            // A name that starts with what was typed is a better answer than one that
            // merely contains it, and a shorter name is a better answer than a longer one.
            hits.sort((a, b) -> {
                boolean aStarts = a.getValue().startsWith(wanted);
                boolean bStarts = b.getValue().startsWith(wanted);
                if (aStarts != bStarts) return aStarts ? -1 : 1;
                return Integer.compare(a.getValue().length(), b.getValue().length());
            });
            for (Map.Entry<String, String> hit : hits) {
                if (paths.size() >= Math.max(1, limit)) break;
                paths.add(hit.getKey());
                names.add(new File(hit.getKey()).getName());
            }
        }
        return Message.of(QUERY)
            .put("text", text == null ? "" : text)
            .put("paths", paths)
            .put("names", names)
            .put("searched", (long) index.size());
    }

    /* ------------------------------------------------------------ indexing */

    /** The folders worth knowing about, which is where a person keeps things. */
    public static List<File> placesToIndex() {
        List<File> places = new ArrayList<>();
        File home = new File(System.getProperty("user.home"));
        for (String name : new String[]{"Desktop-Folder", "Documents", "Downloads",
                                        "Pictures", "Music", "Movies", "Videos"}) {
            File folder = new File(home, name);
            if (folder.isDirectory()) places.add(folder);
        }
        places.add(OSPaths.applications().toFile());
        places.add(OSPaths.applicationsUtilities().toFile());
        return places;
    }

    public void indexInBackground() {
        if (indexing) return;
        Thread worker = new Thread(this::index, "metadata-index");
        worker.setDaemon(true);
        worker.start();
    }

    /** Walks the folders and remembers what is in them. */
    public int index() {
        indexing = true;
        try {
            Map<String, String> found = new LinkedHashMap<>();
            for (File place : placesToIndex()) walk(place, found, 0);
            index.clear();
            index.putAll(found);
            indexedAt = System.currentTimeMillis();
            save();
            Log.info("the index holds " + index.size() + " items");
            return index.size();
        } finally {
            indexing = false;
        }
    }

    private void walk(File folder, Map<String, String> found, int depth) {
        if (depth > 6 || folder == null || !folder.isDirectory()) return;
        File[] children = folder.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isHidden()) continue;
            found.put(child.getAbsolutePath(),
                      child.getName().toLowerCase(java.util.Locale.ROOT));
            if (child.isDirectory() && !org.fractalmicro.fs.FS.looksLikeBundle(child)) {
                walk(child, found, depth + 1);
            }
        }
    }

    /* --------------------------------------------------------------- store */

    /** Where the index is kept, which is where a system keeps its own databases. */
    public static Path store() {
        return OSPaths.ROOT.resolve("private/var/db/Spotlight-V100/index.txt");
    }

    private void save() {
        try {
            Path file = store();
            Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder();
            for (String path : index.keySet()) sb.append(path).append('\n');
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.info("the index could not be written: " + e.getMessage());
        }
    }

    private void load() {
        Path file = store();
        if (!Files.isReadable(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                index.put(line, new File(line).getName().toLowerCase(java.util.Locale.ROOT));
            }
            Log.info("the index came back with " + index.size() + " items");
        } catch (IOException e) {
            Log.info("the index could not be read: " + e.getMessage());
        }
    }

    /** How many things are known about, for anything that wants to say so. */
    public int size() { return index.size(); }
}
