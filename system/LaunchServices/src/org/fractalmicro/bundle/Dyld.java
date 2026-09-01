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
package org.fractalmicro.bundle;

import org.fractalmicro.core.Log;
import org.fractalmicro.macho.MachO;
import org.fractalmicro.os.OSPaths;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The loader: what runs between opening a program and the program starting.
 *
 * It reads the bundle's Mach-O executable, pulls the code resources out of __FRACTAL,
 * unpacks them under ~/.fractaldt/private/var/folders, resolves the LC_LOAD_DYLIB paths to
 * installed frameworks, and hands the class path and entry class to the runtime.
 *
 * The unpack directory is named for a digest of the resources, so an unchanged program
 * reuses what is already there and a changed one can never run from a stale copy.
 */
public final class Dyld {
    private Dyld() {}

    /** What a framework's LC_LOAD_DYLIB path looks like. */
    public static final String FRAMEWORK_PREFIX = "@rpath/";

    /** The manifest attribute naming the class to run. */
    public static final String ENTRY_ATTRIBUTE = org.fractalmicro.dyld.Start.ENTRY_ATTRIBUTE;
    public static final String IDENTIFIER_ATTRIBUTE = "Fractal-Identifier";

    /**
     * Runs a bundle from outside this desktop: the launcher scripts call in here.
     * The first argument is the bundle, the rest are documents to open.
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: Dyld <program.app> [documents...]");
            System.exit(2);
        }
        Bundle bundle = Bundle.read(new File(args[0]));
        if (bundle == null) {
            System.err.println(args[0] + " is not a program bundle");
            System.exit(2);
            return;
        }
        List<String> rest = new ArrayList<>();
        for (int i = 1; i < args.length; i++) rest.add(args[i]);
        // No desktop running means no screen to open a window on, so start the desktop
        // and tell it which program to open.
        List<String> command = new ArrayList<>();
        command.add(javaCommand());
        command.add("-cp");
        command.add(classPath(bundle));
        command.add("org.fractalmicro.Boot");
        command.add("--open-app");
        command.add(bundle.identifier().toString());
        for (String r : rest) {
            command.add("--open");
            command.add(r);
        }
        new ProcessBuilder(command).inheritIO().start();
    }

    /* ---------------------------------------------------------- frameworks */

    /**
     * Turns the paths in the executable's LC_LOAD_DYLIB commands into the framework
     * files on this system. A framework that is not installed is left out, and said so.
     */
    public static List<Path> frameworksFor(Bundle bundle) {
        File binary = bundle.machOExecutable();
        if (binary == null) return List.of();
        try {
            MachO program = MachO.read(binary.toPath());
            return closureOf(program.linkedLibraries(), program.runpaths());
        } catch (IOException e) {
            Log.error("could not read the frameworks of " + bundle.displayName(), e);
            return List.of();
        }
    }

    /**
     * Everything a program needs, not only what it named.
     *
     * A program links Foundation; Foundation links the system library; an umbrella passes
     * on the frameworks inside it. None of that is written in the program, and none of it
     * can be left out, so the names it does carry are followed to the ones they carry, and
     * so on, until nothing new turns up.
     *
     * Order is kept as the load commands gave it, because that is the order a symbol is
     * looked for in, and a library reached twice is only added once.
     */
    private static List<Path> closureOf(List<String> named, List<String> runpaths) {
        List<Path> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        java.util.Deque<String> waiting = new java.util.ArrayDeque<>(named);
        while (!waiting.isEmpty()) {
            String name = waiting.removeFirst();
            if (!seen.add(name)) continue;
            Path resolved = resolveFramework(name, runpaths, null);
            if (resolved == null) {
                Log.info("a library named in a load command is not installed: " + name);
                continue;
            }
            out.add(resolved);
            try {
                MachO image = MachO.read(resolved);
                waiting.addAll(image.reexported());
                waiting.addAll(image.linkedLibraries());
            } catch (IOException notReadable) {
                // An umbrella that is only a directory of symlinks links nothing.
                Log.info("could not read what " + name + " itself links");
            }
        }
        return out;
    }

    /**
     * Turns a name in a load command into a file on this volume.
     *
     * The work is the loader's, and this is where the rest of the system asks for it. What
     * is added here is the volume: the loader is given a root rather than finding one,
     * because it runs before the library that knows where anything is.
     */
    public static Path resolveFramework(String installPath, List<String> runpaths,
                                        Path loader) {
        Path found = runpath().resolve(installPath, runpaths, loader);
        if (found == null) Log.info("nothing is installed at " + installPath);
        return found;
    }

    /** The old way of asking, for callers with no image to ask on behalf of. */
    public static Path resolveFramework(String installPath) {
        return resolveFramework(installPath, Images.RUNPATHS, null);
    }

    private static org.fractalmicro.dyld.Runpath runpath() {
        return new org.fractalmicro.dyld.Runpath(OSPaths.ROOT, null);
    }

    /**
     * The class path for an installed program, named by its identifier.
     *
     * A program asking how to start another one should not have to know whether this
     * system is installed, nor assemble a path out of pieces. It names the program; if
     * there is no bundle for it, this answers with the code that is running, so a build
     * directory works the same way an installed system does.
     */
    public static org.fractalmicro.foundation.FMString classPathFor(org.fractalmicro.foundation.FMString identifier) {
        Bundle bundle = Bundles.byIdentifier(identifier.toString());
        if (bundle != null) return org.fractalmicro.foundation.FMString.of(classPath(bundle));
        File running = Install.runningCode();
        if (running != null) {
            return org.fractalmicro.foundation.FMString.of(running.getAbsolutePath());
        }
        return org.fractalmicro.foundation.FMString.of(System.getProperty("java.class.path", "."));
    }

    /** Everything the program needs on its class path, its own resources first. */
    public static String classPath(Bundle bundle) {
        List<String> parts = new ArrayList<>();
        File binary = bundle.machOExecutable();
        if (binary != null) parts.add(binary.getAbsolutePath());
        for (Path framework : frameworksFor(bundle)) parts.add(framework.toString());
        return String.join(File.pathSeparator, parts);
    }

    /**
     * What a program's own process is started with, which is the loader and nothing else.
     *
     * A class path is a search order: everything on it can see everything else on it, and
     * a program started that way could reach a library it never linked simply because the
     * library was standing next to it. So only the loader goes on it. The loader maps the
     * program, follows its load commands, and resolves each class through the symbol
     * tables, which is the arrangement the load commands were written to describe.
     */
    public static String bootstrapClassPath() {
        Path loader = OSPaths.dyld();
        return Files.isReadable(loader)
            ? loader.toString()
            : System.getProperty("java.class.path", ".");
    }

    /** The class that runs first in a program's process. */
    public static String bootstrapClass() { return "org.fractalmicro.dyld.Start"; }

    /* ------------------------------------------------------------ starting */

    /**
     * Starts a program inside this process: unpacks it, builds a loader over its code
     * resources and its frameworks, and calls the entry class. The entry class is
     * whatever the code resources name, falling back to the bundle's NSPrincipalClass.
     */
    public static Object load(Bundle bundle) throws Exception {
        File binary = bundle.machOExecutable();
        if (binary == null) throw new IOException("no executable in " + bundle.root());
        MachO program = MachO.read(binary.toPath());

        String entry = org.fractalmicro.dyld.Start.entryClass(program);
        if (entry.isEmpty()) entry = bundle.principalClass().toString();
        if (entry.isEmpty()) throw new IOException(bundle.displayName() + " names no entry class");

        // The loader builds the graph: this program, then the libraries it named in its
        // load commands, each mapped once and shared with everything else that links it.
        // What it cannot see is anything it did not link, which is the point of saying so.
        String installPath = "@loader_path/" + bundle.displayName();
        ClassLoader loader = org.fractalmicro.dyld.Dyld.load(installPath, binary.toPath(),
                                                    Dyld::locate);
        Class<?> type = Class.forName(entry, true, loader);
        return type.getDeclaredConstructor().newInstance();
    }

    /**
     * Finds an installed library for a name in a load command.
     *
     * The file is the whole answer. What it links, what it offers and what it expects are
     * all inside it, so nothing has to be worked out on its behalf before it is opened.
     */
    public static Path locate(String installPath, Path from, List<String> runpaths)
            throws IOException {
        List<String> where = runpaths.isEmpty() ? Images.RUNPATHS : runpaths;
        return runpath().resolve(installPath, where, from == null ? null : from.getParent());
    }


    /** The java to start, from where this one is running. */
    public static String javaCommand() {
        return OSPaths.javaCommand();
    }

    /** A one line account of a program, for the log and for Get Info. */
    public static String describe(Bundle bundle) {
        File binary = bundle.machOExecutable();
        if (binary == null) return "no executable";
        try {
            MachO program = MachO.read(binary.toPath());
            return program + ", code resources "
                 + program.section(MachO.CODE_SEGMENT, MachO.CODE_SECTION).size + " bytes, "
                 + "links " + String.join(", ", program.linkedLibraries());
        } catch (IOException e) {
            return "unreadable: " + e.getMessage();
        }
    }

    /**
     * Where earlier versions unpacked a program's code before running it.
     *
     * Nothing writes here now: an image is read where it lies, and there is no copy to go
     * stale. What is left is what those versions wrote, which is worth clearing out.
     */
    public static Path workingRoot() {
        return OSPaths.ROOT.resolve("private/var/folders");
    }

    /** Clears what earlier versions unpacked. Nothing puts anything there any more. */
    public static int clearWorkingFiles() throws IOException {
        Path root = workingRoot();
        if (!Files.isDirectory(root)) return 0;
        int[] removed = {0};
        try (var walk = Files.walk(root)) {
            List<Path> all = new ArrayList<>(walk.toList());
            java.util.Collections.reverse(all);
            for (Path p : all) {
                if (p.equals(root)) continue;
                Files.deleteIfExists(p);
                removed[0]++;
            }
        }
        return removed[0];
    }

    static byte[] utf8(String s) { return s.getBytes(StandardCharsets.UTF_8); }
}
