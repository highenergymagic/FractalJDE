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
package org.fractalmicro;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What runs before there is a system.
 *
 * A release is two files. One is a base image holding a whole volume. The other is this,
 * which is small on purpose and does three things in order.
 *
 * It finds the volume, which is .fractaldt in the home directory and is usually already
 * there.
 *
 * If it is not there, or the image beside this one is a different build from the one
 * installed, it unpacks the image onto it. Unpacking is the whole of installing: no step
 * assembles a volume out of parts on the machine it is being installed on, so there is
 * nothing that can come out differently there than it came out here.
 *
 * Then it hands over. The loader is at /usr/lib/dyld on the volume, and the way a system
 * starts is that something small reads the loader off the disk and calls it. That is what
 * happens: the loader is opened as the image it is, its code comes out, and /sbin/launchd
 * is started through it. After that nothing here is involved in anything, which is the
 * point of it being this size.
 *
 * It carries no part of the system. Foundation, AppKit, the window server, launchd and
 * every program are on the volume and nowhere else, so a new system is a new image and
 * this file does not change.
 */
public final class Kernel {
    private Kernel() {}

    /** Where the loader lives on a volume, and the first program it is asked to run. */
    public static final String LOADER = "usr/lib/dyld";
    public static final String INIT = "sbin/launchd";

    /** The class inside the loader that starts a program. */
    private static final String LOADER_ENTRY = "org.fractalmicro.dyld.Start";

    /** How the loader is told which volume it is running from. */
    private static final String ROOT_PROPERTY = "org.fractalmicro.root";

    /** How a volume other than this machine's is asked for, which the build does. */
    private static final String VOLUME_PROPERTY = "org.fractalmicro.volume";

    /** How an image somewhere other than beside this program is asked for. */
    private static final String IMAGE_PROPERTY = "org.fractalmicro.image";

    /** How everything started afterwards is told when the machine started. */
    private static final String SINCE_PROPERTY = "org.fractalmicro.booted";

    /** When this one did, which is when the machine did. */
    private static final long SINCE = System.currentTimeMillis();

    public static void main(String[] arguments) throws Exception {
        // Said once, here, because this is the first thing to run: everything else measures
        // its own progress from this moment rather than from whenever it was started.
        System.setProperty(SINCE_PROPERTY, Long.toString(SINCE));

        Path volume = volume();
        Path image = image();

        if (image != null && needsInstalling(volume, image)) {
            install(image, volume);
        }

        Path loader = volume.resolve(LOADER);
        if (!Files.isReadable(loader)) {
            say("there is no system at " + volume);
            say(image == null
                ? "and no " + BaseImage.FILE_NAME + " beside this one to install from"
                : "and " + image + " did not produce one");
            System.exit(70);
            return;
        }

        start(volume, loader, arguments);
    }

    /* ------------------------------------------------------------------- finding */

    /**
     * The volume this starts.
     *
     * Normally .fractaldt in the home directory. A property moves it, which is how the
     * build lays out a volume to ship without touching the one the build machine runs.
     */
    public static Path volume() {
        String said = System.getProperty(VOLUME_PROPERTY, "");
        if (!said.isBlank()) return Path.of(said).toAbsolutePath();
        return Path.of(System.getProperty("user.home"), ".fractaldt");
    }

    /**
     * The image this installs from, which ships beside this program.
     *
     * Run out of a checkout there is no image and none is wanted: a build has just written
     * the volume in place and unpacking over the top of it would put the last release back.
     */
    private static Path image() {
        String said = System.getProperty(IMAGE_PROPERTY, "");
        if (!said.isBlank()) {
            Path named = Path.of(said);
            return Files.isReadable(named) ? named : null;
        }
        try {
            Path self = Path.of(Kernel.class.getProtectionDomain().getCodeSource()
                                            .getLocation().toURI());
            Path beside = (Files.isDirectory(self) ? self : self.getParent())
                          .resolve(BaseImage.FILE_NAME);
            return Files.isReadable(beside) ? beside : null;
        } catch (Exception notPackaged) {
            return null;
        }
    }

    /* ---------------------------------------------------------------- installing */

    /**
     * Whether what is on the volume needs replacing with what is in the image.
     *
     * The volume records the build it was unpacked from. A different build is a different
     * system and goes on over the top; the same build is what is already there, and
     * unpacking it again would be several seconds of work with no result.
     */
    private static boolean needsInstalling(Path volume, Path image) throws IOException {
        if (!Files.isReadable(volume.resolve(LOADER))) return true;
        String installed = BaseImage.buildOn(volume);
        if (installed.isEmpty()) return true;
        return !installed.equals(BaseImage.fields(BaseImage.manifestIn(image))
                                          .getOrDefault("Build", ""));
    }

    private static void install(Path image, Path volume) throws IOException {
        Map<String, String> says = BaseImage.fields(BaseImage.manifestIn(image));
        String was = BaseImage.buildOn(volume);
        say((was.isEmpty() ? "installing " : "updating to ")
            + says.getOrDefault("Version", "?")
            + " (" + says.getOrDefault("Build", "?") + ") on " + volume);
        say(BaseImage.unpack(image, volume) + " files written");
    }

    /* --------------------------------------------------------------- handing over */

    /**
     * Reads the loader off the volume and starts the first program with it.
     *
     * The loader is a Mach-O carrying its own code, and everything after this point is
     * loaded by it. Reading it from the volume rather than keeping a copy here is the
     * arrangement it describes: one loader, on the disk, opened by whatever starts the
     * machine. It is also what makes a release replaceable by swapping one file, since
     * a copy kept here would be the one that ran.
     */
    private static void start(Path volume, Path loader, String[] arguments) throws Exception {
        say("the volume is " + volume);
        System.setProperty(ROOT_PROPERTY, volume.toString());

        List<String> passed = new ArrayList<>();
        passed.add(volume.resolve(INIT).toString());
        for (String one : arguments) passed.add(one);

        // The parent is the class path this was started from, which holds nothing but the
        // kernel. Anything the loader needs it finds in itself or on the volume, so a
        // stray copy of a framework beside the kernel jar cannot be picked up instead.
        URLClassLoader system = new URLClassLoader("dyld",
            new URL[]{loader.toUri().toURL()}, Kernel.class.getClassLoader());
        Thread.currentThread().setContextClassLoader(system);

        say("reading the loader");
        Method main = Class.forName(LOADER_ENTRY, true, system)
                           .getMethod("main", String[].class);
        say("starting " + INIT);
        main.invoke(null, (Object) passed.toArray(new String[0]));
    }

    /**
     * What the kernel has to say.
     *
     * The error stream, because there is no log until there is a volume and no window until
     * there is a system, and whoever watches a machine start is watching a terminal.
     *
     * The shape is the one everything else uses on the way up, and this is the second copy
     * of it. The other is org.fractalmicro.core.Progress, in the system library, which is on
     * the volume this has not opened yet: the kernel carries no framework, and a kernel that
     * had to load one to say what it was doing could not say anything until it had.
     */
    private static void say(String what) {
        System.err.printf(java.util.Locale.ROOT, "%6.1f  kernel: %s%n",
                          (System.currentTimeMillis() - SINCE) / 1000.0, what);
        System.err.flush();
    }
}
