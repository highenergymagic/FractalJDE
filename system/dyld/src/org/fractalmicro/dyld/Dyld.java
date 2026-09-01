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
package org.fractalmicro.dyld;
import org.fractalmicro.macho.MachO;


import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The dynamic loader: what turns a set of files into a program that can run.
 *
 * An image is anything loadable: a framework, a library, an application. Each one names
 * the images it links in its load commands, and this resolves those names to files, loads
 * each one once, and gives every image a loader that can see itself and its links and
 * nothing else.
 *
 * Two things make that different from putting everything on a class path.
 *
 * An image is loaded once and shared. Two applications that both link Foundation get the
 * same Foundation, which is what makes a value passed from one to the other the same kind
 * of thing at both ends. A loader that resolved each application's copy separately would
 * hand back objects whose classes have the same name and are not the same class, and every
 * cast across the boundary would fail for no visible reason.
 *
 * And an image already running is an image. The framework the desktop is running from is
 * registered here at start-up with the loader it was loaded by, rather than being read off
 * the disk a second time. That is what {@link #registerRunning} is for, and it is the
 * difference between an application that can be handed a window and one that gets a class
 * cast exception the first time it is.
 */
public final class Dyld {
    private Dyld() {}

    /** What a framework's install path begins with, as written in a load command. */
    public static final String RPATH = "@rpath/";

    /** Images that have been loaded, by install path. Each is loaded once. */
    private static final Map<String, ImageLoader> LOADED = new ConcurrentHashMap<>();

    /** Where an image reached past what it linked, gathered rather than thrown. */
    private static final Map<String, Set<String>> REACHES = new ConcurrentHashMap<>();

    private static volatile boolean strict;

    /**
     * Whether an image reaching past what it linked is an error.
     *
     * Off while code is still being moved between libraries: the reach is recorded and the
     * class is resolved anyway, so a run produces a list of everything still to fix rather
     * than stopping at the first one. On once the layering is finished, when reaching for
     * something a program never linked should fail the way it would anywhere else.
     */
    public static void setStrict(boolean value) { strict = value; }

    public static boolean isStrict() { return strict; }

    /* ------------------------------------------------------------- the image table */

    /**
     * Records an image that is already loaded, with the loader that loaded it.
     *
     * The framework the desktop is running from is one of these. Reading it again from
     * disk would define a second copy of every class in it, and nothing loaded against one
     * copy could be handed to anything expecting the other.
     */
    public static void registerRunning(String installPath, ClassLoader loader) {
        registerRunning(installPath, loader, java.util.Set.of());
    }

    /**
     * Records an image that is already loaded, and what belongs to it.
     *
     * A development build runs everything out of one jar, so every class is in one loader
     * and there is nothing to keep an image's classes apart from its neighbour's. Saying
     * which packages are this image's restores that: the classes are the same classes, so
     * a value handed across a boundary is still the same kind of thing, and a program that
     * links Foundation and reaches for AppKit is still told there is no such symbol.
     *
     * That is what two-level namespace means. A symbol is not a name on its own; it is a
     * name and the library it came from.
     *
     * What it answers for is its export list, read from the image on disk: the same
     * symbols it would have offered had it been mapped here rather than started with the
     * process. Nothing about the division is written down twice.
     *
     * @param exports the classes this image offers, or empty to mean everything the
     *                loader has
     */
    public static void registerRunning(String installPath, ClassLoader loader,
                                       java.util.Set<String> exports) {
        LOADED.put(installPath, new RunningImage(installPath, loader, exports));
        Trace.say(installPath + " is already loaded"
                 + (exports.isEmpty() ? "" : " (" + exports.size() + " symbols)"));
    }

    /** Whether an image is loaded, by install path. */
    public static boolean isLoaded(String installPath) {
        return LOADED.containsKey(installPath);
    }

    /** Every image loaded, in the order they were loaded. */
    public static List<String> images() {
        return new ArrayList<>(new LinkedHashSet<>(LOADED.keySet()));
    }

    /**
     * Loads an image and everything it links, and answers its loader.
     *
     * Everything needed comes out of the file: what it calls itself, what it links, what
     * it defines, what it expects, and the code. Nothing is passed alongside it and
     * nothing is unpacked, because a library that had to be described from outside would
     * not be a library.
     *
     * @param binary the image file
     * @param locate how to find the file for a name one of these links
     */
    public static synchronized ImageLoader load(Path binary, Locator locate)
            throws IOException {
        MachO image = MachO.read(binary);
        String installPath = image.installName().isEmpty()
            ? binary.getFileName().toString() : image.installName();
        return load(installPath, binary, locate, new LinkedHashSet<>());
    }

    /**
     * The same, for something that is not a library and so does not name itself.
     *
     * A program has no LC_ID_DYLIB: nothing links a program, so it never needed a name to
     * be linked by. The caller supplies one so the image has something to be known as.
     */
    public static synchronized ImageLoader load(String installPath, Path binary,
                                                Locator locate) throws IOException {
        return load(installPath, binary, locate, new LinkedHashSet<>());
    }

    private static ImageLoader load(String installPath, Path binary, Locator locate,
                                    Set<String> loading) throws IOException {
        ImageLoader already = LOADED.get(installPath);
        if (already != null) return already;

        // A library may be reached twice in one graph, which is ordinary, but a library
        // that is reached while it is still being loaded is a circle and would not end.
        if (!loading.add(installPath)) {
            throw new IOException("the libraries link each other: "
                                  + String.join(" -> ", loading) + " -> " + installPath);
        }

        MachO image = MachO.read(binary);

        // What it passes on is loaded alongside what it links. An umbrella has no code of
        // its own, and a client that records the umbrella for a symbol has to find the
        // framework behind it here or nowhere.
        List<String> links = new ArrayList<>(image.linkedLibraries());
        links.addAll(image.reexported());

        List<ImageLoader> linked = new ArrayList<>();
        List<ImageLoader> passedOn = new ArrayList<>();
        for (String dependency : links) {
            ImageLoader open = LOADED.get(dependency);
            if (open == null) {
                Path found = locate.locate(dependency, binary, image.runpaths());
                if (found == null) {
                    Trace.warn(installPath + " links " + dependency
                             + ", which is not installed");
                    continue;
                }
                open = load(dependency, found, locate, loading);
            }
            linked.add(open);
            if (image.reexported().contains(dependency)) passedOn.add(open);
        }
        loading.remove(installPath);

        ImageLoader loader = new ImageLoader(installPath, binary, contentsOf(image),
                                             new LinkedHashSet<>(image.exports()),
                                             image.imports(), linked, passedOn,
                                             Dyld::reached);
        LOADED.put(installPath, loader);
        Trace.say("mapped " + installPath
                 + " (" + image.exports().size() + " symbols"
                 + (linked.isEmpty() ? "" : ", linking " + names(linked)) + ")");
        return loader;
    }

    /**
     * The image's own files, out of the segment that carries them.
     *
     * The segment is an archive, read once when the image is mapped. Reading it entry by
     * entry on demand would mean holding the file open for the life of the process and
     * seeking through it for every class, which is slower and no more faithful: a mapped
     * image is in memory either way.
     */
    private static Map<String, byte[]> contentsOf(MachO image) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        byte[] code = image.codeResource();
        if (code == null) return out;
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(code))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                out.put(entry.getName(), zip.readAllBytes());
            }
        }
        return out;
    }

    private static String names(List<ImageLoader> images) {
        List<String> out = new ArrayList<>();
        for (ImageLoader i : images) out.add(shortName(i.image()));
        return String.join(", ", out);
    }

    /** The name a person would use: Foundation, not @rpath/Foundation.framework/... */
    public static String shortName(String installPath) {
        if (installPath == null || installPath.isBlank()) return "";
        // A framework is named by the last .framework in the path, so a framework inside
        // an umbrella is called what it is rather than what it is inside of.
        int framework = installPath.lastIndexOf(".framework");
        if (framework > 0) {
            int from = installPath.lastIndexOf('/', framework) + 1;
            return installPath.substring(from, framework);
        }
        // Anything else is named by its file: /usr/lib/libSystem.B.dylib is libSystem.
        int slash = installPath.lastIndexOf('/');
        String file = slash < 0 ? installPath : installPath.substring(slash + 1);
        int dot = file.indexOf('.');
        return dot > 0 ? file.substring(0, dot) : file;
    }

    /* ------------------------------------------------------- reaching past the links */

    private static ClassLoader reached(String image, String className) {
        // Only this system's own classes are worth reporting. Anything else missing is a
        // an absence and should be left to fail.
        if (!className.startsWith("org.fractalmicro.")) return null;
        REACHES.computeIfAbsent(image, k -> java.util.concurrent.ConcurrentHashMap.newKeySet())
               .add(className);
        if (strict) return null;
        return Dyld.class.getClassLoader();
    }

    /** What each image reached for without linking it, which is the list still to fix. */
    public static Map<String, Set<String>> reaches() {
        return new LinkedHashMap<>(REACHES);
    }

    public static void forgetReaches() { REACHES.clear(); }

    /** A line per image that reached past its links, for a run to print at the end. */
    public static String describeReaches() {
        if (REACHES.isEmpty()) return "every image kept to what it links";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Set<String>> e : REACHES.entrySet()) {
            List<String> classes = new ArrayList<>(e.getValue());
            java.util.Collections.sort(classes);
            sb.append(shortName(e.getKey())).append(" reached ")
              .append(classes.size()).append(" it did not link:\n");
            for (String c : classes) sb.append("    ").append(c).append('\n');
        }
        return sb.toString();
    }

    /* -------------------------------------------------------------------- finding */

    /**
     * How the loader turns a name in a load command into a file.
     *
     * A path is the whole of the answer. What the image links, what it offers and what it
     * expects are all in the file, so nothing else has to be worked out on its behalf.
     */
    public interface Locator {
        /**
         * @param installPath what the load command says
         * @param from        the image doing the linking, for @loader_path
         * @param runpaths    what that image says @rpath stands for
         */
        Path locate(String installPath, Path from, List<String> runpaths) throws IOException;
    }

    /**
     * An image that was loaded before this loader existed.
     *
     * It has no code of its own to search because its loader already holds it, and it
     * links nothing because whatever it needed is in that loader too.
     */
    private static final class RunningImage extends ImageLoader {
        private final ClassLoader running;
        private final java.util.Set<String> symbols;

        RunningImage(String installPath, ClassLoader running,
                     java.util.Set<String> symbols) {
            super(installPath, null, Map.of(), symbols, Map.of(), List.of(), List.of(),
                  null);
            this.running = running;
            this.symbols = java.util.Set.copyOf(symbols);
        }

        /** Whether a class is this image's to answer for, which its exports decide. */
        private boolean holds(String className) {
            return symbols.isEmpty() || symbols.contains(className);
        }

        @Override protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!holds(name)) {
                throw new ClassNotFoundException(name + " is not in " + image());
            }
            Class<?> found = running.loadClass(name);
            if (resolve) resolveClass(found);
            return found;
        }

        /**
         * Answers out of the loader that already holds this image.
         *
         * The graph search has to come through here too, not only {@link #loadClass}: an
         * image that links this one is searching its dependencies directly, and a running
         * image whose classes are in somebody else's loader has none of its own to find.
         */
        @Override Class<?> search(String name, Set<String> seen) {
            return offered(name, seen);
        }

        @Override Class<?> offered(String name, Set<String> seen) {
            if (!seen.add(image())) return null;
            if (!holds(name)) return null;
            try {
                return running.loadClass(name);
            } catch (ClassNotFoundException notHere) {
                return null;
            }
        }

        @Override public URL getResource(String name) {
            return running.getResource(name);
        }
    }
}
