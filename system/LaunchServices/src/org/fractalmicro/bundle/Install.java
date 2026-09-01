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
import org.fractalmicro.os.OSPaths;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Putting this system where it belongs.
 *
 * {@link Images} writes the libraries; what is left here is the rest of laying out a
 * volume. Making the pointers a framework is held together by, since Windows will not
 * always give out a symbolic link and something has to be written either way. Moving a
 * volume left under the name this used to use. And taking away what earlier versions put
 * on a volume and later versions must not find.
 *
 * That last part is most of it. An installation is not a fresh directory: it is whatever
 * the machine had, which may have been written by a version that kept the whole system in
 * one framework, or kept a copy of each library's code in an archive beside it. Both were
 * ways to reach a class without linking anything that offered it, which is the arrangement
 * the images exist to end, so both are removed rather than left for something to find.
 */
public final class Install {
    private Install() {}

    /** The framework earlier versions kept the entire system in, kept to know what to remove. */
    public static final String FRAMEWORK_NAME = "Fractal.framework";
    public static final String IDENTIFIER = "org.fractalmicro.Fractal";

    public static Path frameworkRoot() { return OSPaths.frameworks().resolve(FRAMEWORK_NAME); }

    /**
     * Lays the system out on the volume, and answers whether anything was written.
     *
     * There was a time when this also installed the whole system as one framework, so that
     * a program could be given it and have everything. What replaced that is the images:
     * each library is its own file, carrying its own code and its own symbol table, and a
     * program is given the ones it linked. A single framework holding all of them would be
     * a way around that, so it is taken out when it is found rather than left to be picked
     * up by anything still looking for one.
     */
    public static synchronized boolean ensureInstalled() {
        int written = Images.installAll();
        retireCombinedFramework();
        retireLooseArchives();
        return written > 0;
    }

    /**
     * Removes the framework that used to hold the entire system.
     *
     * Anything that linked it got every class this system has, which is what the images
     * exist to stop. An installation carried forward from before them still has one.
     */
    private static void retireCombinedFramework() {
        Path old = frameworkRoot();
        if (!Files.exists(old)) return;
        try {
            clear(old);
            Log.info("removed the combined framework: the images have replaced it");
        } catch (IOException inUse) {
            Log.info("the combined framework could not be removed yet: " + inUse.getMessage());
        }
    }

    /**
     * Removes the archives earlier versions wrote beside each image.
     *
     * A library's code is in the library. A copy of it sitting next to the file, in a
     * format anything can open without reading a single load command, is a way to load a
     * library without linking it.
     */
    private static void retireLooseArchives() {
        for (String installName : Frameworks.all()) {
            Path binary = Dyld.resolveFramework(installName);
            if (binary == null) continue;
            Path beside = binary.resolveSibling(binary.getFileName() + ".jar");
            try {
                if (Files.deleteIfExists(beside)) {
                    Log.info("removed " + beside.getFileName() + ": the image carries its code");
                }
            } catch (IOException inUse) {
                Log.info("could not remove " + beside + ": " + inUse.getMessage());
            }
        }
    }

    /**
     * Moves a volume left under the old name, stopping whatever holds it if need be.
     *
     * Foundation tries the move and says whether it worked. It cannot do more than that: a
     * directory with a running program inside it will not rename on Windows, and stopping
     * that program means knowing what a daemon is, which is this layer's business.
     */
    public static void adoptFormerVolume() {
        if (OSPaths.adoptFormerVolume()) return;
        stopDaemons();
        if (OSPaths.adoptFormerVolume()) return;
        System.err.println("The volume could not be moved from " + OSPaths.FORMER_ROOT
                           + " to " + OSPaths.ROOT + ".");
        System.err.println("Close anything running from it and move it by hand;"
                           + " until then this starts from an empty volume.");
    }

    /**
     * Stops what the system started, so the files it holds can be replaced.
     *
     * Asked of launchd rather than of any particular daemon: what is running is launchd's
     * to know, and an installer that names one daemon has to be edited every time another
     * is added. They are started again by whoever loads their jobs next.
     */
    static void stopDaemons() {
        org.fractalmicro.launchd.Launchd.session().stopAll();
        try {
            // A process has to actually go before its hold on a file does.
            Thread.sleep(400);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Removes whatever is at a path, directory or not, so a link can be put there. */
    private static void clear(Path at) throws IOException {
        if (Files.isDirectory(at) && !Files.isSymbolicLink(at)) {
            try (var walk = Files.walk(at)) {
                for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
            }
        } else {
            Files.deleteIfExists(at);
        }
    }

    /**
     * A symbolic link where the file system allows one, and a file holding the path
     * where it does not. Windows only allows the real thing to a privileged account or
     * with developer mode on, and this has to work either way.
     */
    static void link(Path at, String target) {
        try {
            // A directory sitting where the link belongs is the remains of an install that
            // could not make one and copied instead. It has to go, or the name keeps
            // resolving to whatever version was current when it was made.
            clear(at);
            Files.createSymbolicLink(at, at.getParent().relativize(
                at.getParent().resolve(target)));
        } catch (IOException | UnsupportedOperationException e) {
            try {
                Files.writeString(at, target, StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                Log.info("could not point " + at + " at " + target);
            }
        }
    }

    /** Follows one of those pointers, whichever kind it turned out to be. */
    public static Path follow(Path pointer) throws IOException {
        if (Files.isSymbolicLink(pointer)) return pointer.getParent().resolve(
            Files.readSymbolicLink(pointer)).normalize();
        if (Files.isRegularFile(pointer) && Files.size(pointer) < 512) {
            String text = Files.readString(pointer, StandardCharsets.UTF_8).trim();
            if (!text.isEmpty() && !text.contains("\n")) {
                return pointer.getParent().resolve(text).normalize();
            }
        }
        return pointer;
    }

    /**
     * The archive this program is running from, or nothing when it is running from an
     * image or from loose classes.
     *
     * What asks are the parts that write bundles, and the answer is what they copy a
     * program's classes out of. Started from an image there is no archive and no copying to
     * do: the code is already where it belongs, and a bundle written from nothing would
     * replace a working program with a plist.
     */
    public static File runningCode() {
        try {
            File self = new File(Install.class.getProtectionDomain().getCodeSource()
                                              .getLocation().toURI());
            if (self.isFile()) return self;
            File beside = new File(self.getParentFile(), "FractalJDE.jar");
            return beside.isFile() ? beside : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Tells the loader that the framework this process is running is already loaded.
     *
     * Everything the desktop is made of was loaded before the loader existed, by whatever
     * started the virtual machine. Registering it means a program that links the framework
     * is given these classes rather than a second copy of them, so an object made in a
     * program is the same kind of thing when the desktop is handed it.
     */
    public static void registerRunningFramework() {
        // Every library, each with what belongs to it, so that linking one does not hand
        // a program all the others.
        Frameworks.registerRunning(Install.class.getClassLoader());
    }

    /**
     * A line for the log and for the tests: what is on the volume.
     *
     * The libraries are counted by asking the loader to find each one, which is the same
     * question a program asks. Counting the files in a directory would say a number
     * whether or not any of them could be linked.
     */
    public static String describe() {
        int found = 0;
        for (String installName : Frameworks.all()) {
            if (Dyld.resolveFramework(installName) != null) found++;
        }
        return "volume " + OSPaths.ROOT + ": " + found + " of "
               + Frameworks.all().size() + " libraries, loader "
               + (Files.isReadable(OSPaths.dyld()) ? "present" : "missing");
    }
}
