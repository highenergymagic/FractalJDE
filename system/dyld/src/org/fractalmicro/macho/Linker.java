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
package org.fractalmicro.macho;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deciding, at build time, which library each symbol will come from.
 *
 * The code mentions a class; the linker finds the linked library that exports it and
 * writes that down. At run time the loader follows what was decided here, so a two level
 * namespace costs nothing to resolve.
 *
 * A library reached through an umbrella is recorded as the umbrella: the symbol is found
 * in LaunchServices, the program named CoreServices, and the load command carries the name
 * the program gave.
 *
 * Runtime classes are not symbols. Every image gets those and no image exports them.
 */
public final class Linker {

    private final Map<String, List<String>> exportsOf = new LinkedHashMap<>();
    private final Map<String, List<String>> reexportsOf = new LinkedHashMap<>();

    /** Remembers what a library offers, under the name clients will link it by. */
    public void add(String installName, List<String> exports, List<String> reexports) {
        exportsOf.put(installName, List.copyOf(exports));
        reexportsOf.put(installName, List.copyOf(reexports));
    }

    /** The same, read out of a library that has already been written. */
    public void addImage(Path binary) throws IOException {
        MachO image = MachO.read(binary);
        String name = image.installName();
        if (name.isEmpty()) return;
        add(name, image.exports(), image.reexported());
    }

    /** Whether anything known here offers this class. */
    public boolean isKnown(String className) {
        for (List<String> exports : exportsOf.values()) {
            if (exports.contains(className)) return true;
        }
        return false;
    }

    /**
     * Works out where each referenced class will come from.
     *
     * Libraries are tried in the order the image links them, because that is the order a
     * loader would try them and the answer should not depend on which one happened to be
     * asked first. A class no linked library offers is left out: either the runtime
     * provides it, in which case there is nothing to record, or nothing does, and the
     * program will fail at the point it actually asks, with the name of what was missing.
     */
    public Map<String, String> resolve(Set<String> referenced, List<String> linked) {
        Map<String, String> out = new LinkedHashMap<>();
        Map<String, Set<String>> reachable = new LinkedHashMap<>();
        for (String library : linked) reachable.put(library, through(library));

        for (String className : referenced) {
            for (String library : linked) {
                if (reachable.get(library).contains(className)) {
                    out.put(className, library);
                    break;
                }
            }
        }
        return out;
    }

    /**
     * Everything a library offers, including everything it passes on.
     *
     * An umbrella exports nothing of its own; what a client gets from it is what the
     * frameworks inside it export. Following that here rather than at run time is the
     * point: the client records the umbrella, and never has to know what is behind it.
     */
    private Set<String> through(String installName) {
        Set<String> out = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> waiting = new ArrayDeque<>();
        waiting.add(installName);
        while (!waiting.isEmpty()) {
            String library = waiting.removeFirst();
            if (!seen.add(library)) continue;
            out.addAll(exportsOf.getOrDefault(library, List.of()));
            waiting.addAll(reexportsOf.getOrDefault(library, List.of()));
        }
        return out;
    }

    /** What is known here, for a report. */
    public List<String> libraries() { return new ArrayList<>(exportsOf.keySet()); }

    /** How many classes a library offers, counting what it passes on. */
    public int countThrough(String installName) { return through(installName).size(); }
}
