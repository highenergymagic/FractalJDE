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
import org.fractalmicro.launchd.Job;
import org.fractalmicro.launchd.Launchd;
import org.fractalmicro.plist.Plist;
import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMMutableArray;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMURL;
import org.fractalmicro.xpc.Connection;
import org.fractalmicro.xpc.Message;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Asking the metadata server, from anything that wants to search.
 *
 * The server is a separate process, which may be running, may be starting, or may not be
 * installed at all. None of that is the caller's problem: ask here, and if the server
 * cannot be reached the same question is answered by walking the disk, more slowly, in
 * this process. A search that works slowly is better than a search that fails because a
 * daemon is not up.
 */
public final class Metadata {
    private Metadata() {}

    /**
     * One thing found: where it is, and what it is called.
     *
     * The location is this system's own, so a program handed a result can open it, ask
     * the file manager about it or put it in a message without ever naming a type from
     * the runtime underneath.
     */
    public record Hit(FMURL location, FMString name) {

        /** The same as a file, for the parts of the system written against the runtime. */
        public File file() { return location.asFile(); }
    }

    /** Writes the job description that starts the server, if it is not there already. */
    public static Path installJob() throws IOException {
        Path file = Launchd.daemonsFolder().resolve(Server.LABEL + ".plist");
        Map<String, Object> job = new LinkedHashMap<>();
        job.put(Job.LABEL, Server.LABEL);

        // Written the way every job in an image has to be: naming nothing that belongs to
        // the machine it was written on. The server is started the way any other program
        // is, by handing the loader an image, so what runs here is the same arrangement as
        // everything else rather than a class path put together for this one daemon.
        List<Object> command = new ArrayList<>();
        command.add(Job.RUNTIME);
        command.add("--enable-preview");
        command.add("--enable-native-access=ALL-UNNAMED");
        command.add("-D" + org.fractalmicro.dyld.Start.ROOT_PROPERTY + "=" + Job.VOLUME);
        command.add("-cp");
        command.add(Job.VOLUME + "/usr/lib/dyld");
        command.add(org.fractalmicro.dyld.Start.class.getName());
        command.add(Job.VOLUME + "/" + org.fractalmicro.bundle.Images.MDS_PATH);
        job.put(Job.PROGRAM_ARGUMENTS, command);

        job.put(Job.RUN_AT_LOAD, Boolean.TRUE);
        job.put(Job.KEEP_ALIVE, Boolean.TRUE);
        job.put(Job.THROTTLE, 10L);
        Map<String, Object> services = new LinkedHashMap<>();
        services.put(Server.SERVICE, Boolean.TRUE);
        job.put(Job.MACH_SERVICES, services);
        job.put(Job.STANDARD_OUT, Job.LOGS + "/metadata.log");
        job.put(Job.STANDARD_ERROR, Job.LOGS + "/metadata.log");
        Plist.write(file, job);
        return file;
    }

    /** Whether the server is listening right now. */
    public static boolean running() {
        return Connection.available(Server.SERVICE);
    }

    /** What the server says about itself, or null when it is not there. */
    public static Message status() {
        try {
            return Connection.ask(Server.SERVICE, Message.of(Server.STATUS));
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Searches: through the server when it is running, and by walking when it is not.
     * The answer is the same shape either way, so nothing above here has two paths.
     */
    public static FMArray<Hit> search(FMString text, int limit) {
        if (text == null || text.isBlank()) return FMArray.empty();
        if (running()) {
            try {
                Message reply = Connection.ask(Server.SERVICE,
                    Message.of(Server.QUERY).put("text", text.toString())
                                            .put("limit", (long) limit));
                if (!reply.isError()) {
                    FMMutableArray<Hit> hits = FMMutableArray.empty();
                    for (String path : reply.strings("paths")) {
                        hits.add(hitFor(new File(path)));
                    }
                    return hits.asArray();
                }
                Log.info("the metadata server said: " + reply.errorText());
            } catch (IOException e) {
                Log.info("the metadata server could not be asked: " + e.getMessage());
            }
        }
        return walkFor(text, limit);
    }

    /**
     * Everything the index knows about one file, which is the question mdls asks.
     *
     * What was indexed rather than what a fresh read would say, and nothing at all when
     * the server is not running. Reading the file here instead would answer a different
     * question in the same shape, which is worse than saying nothing.
     */
    public static FMDictionary attributesOf(FMURL file) {
        if (file == null || !running()) return FMDictionary.fromMap(Map.of());
        try {
            Message reply = Connection.ask(Server.SERVICE,
                Message.of(Server.ATTRIBUTES)
                       .put("path", file.asFile().getAbsolutePath()));
            if (!reply.isError() && reply.get(Server.ATTRIBUTES) instanceof Map<?, ?> said) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> one : said.entrySet()) {
                    out.put(String.valueOf(one.getKey()), one.getValue());
                }
                return FMDictionary.fromMap(out);
            }
        } catch (IOException e) {
            Log.info("the metadata server could not be asked: " + e.getMessage());
        }
        return FMDictionary.fromMap(Map.of());
    }

    /** The slow way, for when there is no server: look through the same places by hand. */
    public static FMArray<Hit> walkFor(FMString text, int limit) {
        FMMutableArray<Hit> hits = FMMutableArray.empty();
        FMString wanted = text.lowercase();
        for (File place : Server.placesToIndex()) {
            walk(place, wanted, hits, limit, 0);
            if (hits.count() >= limit) break;
        }
        return hits.asArray();
    }

    private static Hit hitFor(File file) {
        return new Hit(FMURL.of(file), FMString.of(file.getName()));
    }

    private static void walk(File folder, FMString wanted, FMMutableArray<Hit> hits,
                             int limit, int depth) {
        if (depth > 4 || hits.count() >= limit || folder == null || !folder.isDirectory()) {
            return;
        }
        File[] children = folder.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (hits.count() >= limit) return;
            if (child.isHidden()) continue;
            if (FMString.of(child.getName()).lowercase().contains(wanted)) {
                hits.add(hitFor(child));
            }
            if (child.isDirectory() && !org.fractalmicro.fs.FS.looksLikeBundle(child)) {
                walk(child, wanted, hits, limit, depth + 1);
            }
        }
    }
}
