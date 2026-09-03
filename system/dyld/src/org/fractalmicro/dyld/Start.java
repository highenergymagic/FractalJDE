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

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * What runs first in a program's own process.
 *
 * The kernel maps two things and stands back: the program, and the loader named in its
 * LC_LOAD_DYLINKER. The loader follows the load commands outwards until the graph closes.
 *
 * The only image the runtime is handed is this one. Every library is mapped from the file
 * its load command names, and every class resolved through the symbol tables.
 *
 * A class path is a search order, so a program laid out on one can reach any class in any
 * of them. A program started here reaches what it linked and what that linked.
 */
public final class Start {
    private Start() {}

    /** The property the volume root arrives in, since the loader has no library to ask. */
    public static final String ROOT_PROPERTY = "org.fractalmicro.root";

    /**
     * Where the program that is running came from.
     *
     * A process cannot find this out by looking at itself: the runtime knows what is
     * on its class path, which here is the loader and nothing else. So the loader
     * says, and everything that needs to know where its own bundle is starts here.
     */
    public static final String EXECUTABLE_PROPERTY = "org.fractalmicro.executable";

    /**
     * Where a program says what runs first.
     *
     * Not Main-Class. That is the runtime's way of asking, and it names one class in one
     * archive; this names the entry point of an image, which the loader reads after it has
     * mapped the image and before it has run anything in it.
     */
    public static final String ENTRY_ATTRIBUTE = "Fractal-Entry";

    /**
     * Maps a program and runs it.
     *
     * @param arguments the program's image, then its own arguments
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 1) {
            System.err.println("usage: dyld <program> [arguments]");
            System.exit(64);
        }
        Path program = Path.of(arguments[0]).toAbsolutePath();
        Path root = rootFor(program);
        Runpath runpath = new Runpath(root, program);

        // The loader is already mapped: the runtime started in it, which is the whole
        // reason there is anything running at all. Anything that links it gets these
        // classes rather than a second copy, the same way dyld is mapped once into a
        // process and not once per library that calls into it.
        Dyld.registerRunning(MachO.DYLINKER, Start.class.getClassLoader(),
                             java.util.Set.of());

        System.setProperty(EXECUTABLE_PROPERTY, program.toString());

        MachO image = MachO.read(program);
        String name = image.installName().isEmpty()
            ? program.getFileName().toString() : image.installName();

        ImageLoader loader = Dyld.load(name, program,
            (installPath, from, runpaths) -> runpath.resolve(installPath, runpaths,
                                                             from.getParent()));

        String entry = entryClass(image);
        if (entry.isEmpty()) {
            System.err.println("dyld: " + name + " names no entry point");
            System.exit(70);
        }
        Thread.currentThread().setContextClassLoader(loader);
        Class<?> type = Class.forName(entry, true, loader);
        String[] rest = Arrays.copyOfRange(arguments, 1, arguments.length);
        type.getMethod("main", String[].class).invoke(null, (Object) rest);
    }

    /**
     * Where this system is installed.
     *
     * Whoever starts the loader may say. Failing that it is worked out from the program's
     * own path, by climbing until the directory holding the system is found, which is what
     * a loader with no environment to consult has to do.
     */
    private static Path rootFor(Path program) {
        String said = System.getProperty(ROOT_PROPERTY, "");
        if (!said.isBlank()) return Path.of(said);
        for (Path at = program.getParent(); at != null; at = at.getParent()) {
            if (java.nio.file.Files.isDirectory(at.resolve("System/Library/Frameworks"))) {
                return at;
            }
        }
        return program.getRoot() == null ? Path.of(".") : program.getRoot();
    }

    /** The entry point named in the image's own code, which is where a program says it. */
    public static String entryClass(MachO image) {
        try {
            byte[] code = image.codeResource();
            if (code == null) return "";
            try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
                    new java.io.ByteArrayInputStream(code))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (!"META-INF/MANIFEST.MF".equals(entry.getName())) continue;
                    java.util.jar.Manifest manifest =
                        new java.util.jar.Manifest(new java.io.ByteArrayInputStream(
                            zip.readAllBytes()));
                    String main = manifest.getMainAttributes().getValue(ENTRY_ATTRIBUTE);
                    if (main == null || main.isBlank()) {
                        main = manifest.getMainAttributes().getValue("Main-Class");
                    }
                    return main == null ? "" : main;
                }
            }
        } catch (java.io.IOException unreadable) {
            // A program with no manifest names its entry point somewhere else.
        }
        return "";
    }

    /** The images mapped, for anything that wants to say what a program is made of. */
    public static List<String> mapped() { return Dyld.images(); }
}
