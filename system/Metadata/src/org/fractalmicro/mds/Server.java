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
import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.os.OSPaths;
import org.fractalmicro.plist.Plist;
import org.fractalmicro.spotlight.FMImporters;
import org.fractalmicro.spotlight.FMMetadataAttributes;
import org.fractalmicro.xpc.Message;
import org.fractalmicro.xpc.Service;

import java.io.File;
import java.io.IOException;
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
    public static final String ATTRIBUTES = "attributes";
    public static final String STATUS = "status";
    public static final String REINDEX = "reindex";
    public static final String STOP = "stop";

    /**
     * One file in the index.
     *
     * The name is what a search matches first. The text is whatever an importer read out
     * of the file, folded down, so a word inside a document finds it. The attributes are
     * what the importer said, kept as it said them, for anything that asks about one file.
     */
    private record Item(String name, String text, Map<String, Object> attributes) {}

    private final Map<String, Item> index = new ConcurrentHashMap<>();
    private volatile boolean indexing;
    private volatile long indexedAt;
    private Service service;

    public static void main(String[] args) throws Exception {
        Log.install();
        // The importers are plug-ins, and a plug-in loaded before the loader knows what is
        // already running comes back as a class of the same name and a different kind.
        org.fractalmicro.bundle.Install.registerRunningFramework();
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
            case ATTRIBUTES -> attributes(request.string("path", ""));
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
            List<Map.Entry<String, Item>> hits = new ArrayList<>();
            for (Map.Entry<String, Item> entry : index.entrySet()) {
                if (rank(entry.getValue(), wanted) >= 0) hits.add(entry);
            }
            // Named for it, then starting with it, then holding it, then saying it
            // somewhere inside. A shorter name breaks the tie, as the shorter answer.
            hits.sort((a, b) -> {
                int byRank = Integer.compare(rank(a.getValue(), wanted),
                                             rank(b.getValue(), wanted));
                if (byRank != 0) return byRank;
                return Integer.compare(a.getValue().name().length(),
                                       b.getValue().name().length());
            });
            for (Map.Entry<String, Item> hit : hits) {
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

    /**
     * How good a match this is, lower being better, or -1 for none at all.
     *
     * A file called what was typed is what somebody looking for it meant. A file that
     * merely says the word somewhere is an answer too, and a worse one.
     */
    private static int rank(Item item, String wanted) {
        if (item.name().equals(wanted)) return 0;
        if (item.name().startsWith(wanted)) return 1;
        if (item.name().contains(wanted)) return 2;
        if (item.text().contains(wanted)) return 3;
        return -1;
    }

    /**
     * Everything known about one file, which is what mdls asks.
     *
     * From the index rather than from the file: the point of having indexed it is not
     * reading it again, and a file that has gone away still has an answer until the walk.
     */
    public Message attributes(String path) {
        Item item = path == null ? null : index.get(path);
        return Message.of(ATTRIBUTES)
            .put("path", path == null ? "" : path)
            .put("known", item != null)
            .put(ATTRIBUTES, item == null ? new LinkedHashMap<String, Object>()
                                          : item.attributes());
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

    /** Walks the folders worth knowing about and remembers what is in them. */
    public int index() {
        int held = index(placesToIndex());
        save();
        return held;
    }

    /**
     * The same over named folders, without writing the store.
     *
     * What the server does on a schedule is this over the places a person keeps things.
     * Taking the places as an argument is what lets anything else index a folder and ask
     * about it without a walk of somebody's home appearing in the answer.
     */
    public int index(List<File> places) {
        indexing = true;
        try {
            Map<String, Item> found = new LinkedHashMap<>();
            for (File place : places) walk(place, found, 0);
            index.clear();
            index.putAll(found);
            indexedAt = System.currentTimeMillis();
            Log.info("the index holds " + index.size() + " items");
            return index.size();
        } finally {
            indexing = false;
        }
    }

    private void walk(File folder, Map<String, Item> found, int depth) {
        if (depth > 6 || folder == null || !folder.isDirectory()) return;
        File[] children = folder.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isHidden()) continue;
            found.put(child.getAbsolutePath(), itemFor(child));
            if (child.isDirectory() && !org.fractalmicro.fs.FS.looksLikeBundle(child)) {
                walk(child, found, depth + 1);
            }
        }
    }

    /**
     * One file, as the index holds it: its name, and whatever an importer made of it.
     *
     * The server does not know how to read anything. It asks what is installed, which is
     * why a kind it has never heard of becomes searchable by installing a bundle.
     */
    private static Item itemFor(File file) {
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        FMDictionary said = FMImporters.attributesFor(file);
        return new Item(name, searchableText(said), said.asMap());
    }

    /** The attributes worth matching a query against, folded to one string. */
    private static String searchableText(FMDictionary said) {
        StringBuilder text = new StringBuilder();
        for (org.fractalmicro.foundation.FMString key
                : new org.fractalmicro.foundation.FMString[]{
                    FMMetadataAttributes.TEXT_CONTENT,
                    FMMetadataAttributes.DISPLAY_NAME,
                    FMMetadataAttributes.BUNDLE_IDENTIFIER,
                    FMMetadataAttributes.VERSION}) {
            org.fractalmicro.foundation.FMString one = said.string(key);
            if (!one.isEmpty()) text.append(one).append(' ');
        }
        return text.toString().toLowerCase(java.util.Locale.ROOT);
    }

    /* --------------------------------------------------------------- store */

    /**
     * Where the index is kept, which is where a system keeps its own databases.
     *
     * A property list, because what is kept is a dictionary per file now rather than a
     * line per file. An earlier index.txt beside it is a list of paths and nothing else.
     */
    public static Path store() {
        return OSPaths.ROOT.resolve("private/var/db/Spotlight-V100/index.plist");
    }

    private void save() {
        try {
            Path file = store();
            Files.createDirectories(file.getParent());
            Map<String, Object> written = new LinkedHashMap<>();
            for (Map.Entry<String, Item> one : index.entrySet()) {
                written.put(one.getKey(), one.getValue().attributes());
            }
            Plist.write(file, written);
        } catch (IOException e) {
            Log.info("the index could not be written: " + e.getMessage());
        }
    }

    private void load() {
        Path file = store();
        if (!Files.isReadable(file)) return;
        try {
            for (Map.Entry<String, Object> one
                    : Plist.readDictionary(file).entrySet()) {
                FMDictionary said = one.getValue() instanceof Map<?, ?> map
                    ? asDictionary(map) : FMDictionary.of(Map.of());
                index.put(one.getKey(),
                          new Item(new File(one.getKey()).getName()
                                       .toLowerCase(java.util.Locale.ROOT),
                                   searchableText(said), said.asMap()));
            }
            Log.info("the index came back with " + index.size() + " items");
        } catch (IOException e) {
            Log.info("the index could not be read: " + e.getMessage());
        }
    }

    /** One file's attributes as they came back off the disk. */
    private static FMDictionary asDictionary(Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> one : map.entrySet()) {
            out.put(String.valueOf(one.getKey()), one.getValue());
        }
        return FMDictionary.fromMap(out);
    }

    /** How many things are known about, for anything that wants to say so. */
    public int size() { return index.size(); }
}
