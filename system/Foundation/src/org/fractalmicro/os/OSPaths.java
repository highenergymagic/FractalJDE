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
package org.fractalmicro.os;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The pretend system volume, at ~/.fractaldt, laid out the way a Mac is laid out.
 * Preferences, icon resources and application aliases live where their real
 * counterparts live, so the paths in this program read like Mac paths.
 *
 *   ~/.fractaldt/System/Library/CoreServices/CoreTypes.bundle/Contents/Resources/
 *   ~/.fractaldt/System/Library/CoreServices/Dock.app/Contents/Resources/
 *   ~/.fractaldt/Users/&lt;user&gt;/Library/Preferences/org.fractalmicro.finder.plist
 *   ~/.fractaldt/Applications/
 *   ~/.fractaldt/Volumes/
 *
 * The user's visible desktop stays outside it, at ~/Desktop-Folder, because that is
 * the folder the desktop is documented to show.
 */
public final class OSPaths {
    private OSPaths() {}

    /**
     * The creator code: four characters saying who made a file, as an OSType.
     *
     * "FMI " with the space, because an OSType is four characters and Fractal Microsystems
     * has three. It goes in PkgInfo, in a bundle's CFBundleSignature and in the Finder
     * information of anything this system writes.
     */
    public static final String CREATOR = "FMI ";

    public static final Path USER_HOME = Paths.get(System.getProperty("user.home"));
    /**
     * The volume this desktop runs from.
     *
     * Everything the system is made of lives under here: the framework, the applications
     * that ship with it, the preferences, the logs. It is one directory so that it can be
     * archived, copied to another machine and run, which is what a system volume is for.
     */
    public static final Path ROOT = chooseRoot();

    /**
     * Where the volume is.
     *
     * Normally .fractaldt in the home directory, and said otherwise in two ways that mean
     * different things. org.fractalmicro.root is the volume this process was booted from,
     * which the kernel passes on to everything it starts, and is asked first because a
     * process booted from somewhere is running from there whatever anybody thinks.
     * org.fractalmicro.volume is a volume being built, which is not the one the build
     * machine runs.
     *
     * Only the second used to be read, so a session booted onto any other volume ran
     * against the usual one: it drew a desktop and the checks passed, and the programs it
     * opened were the ones installed at home rather than the ones that had just shipped.
     */
    private static Path chooseRoot() {
        for (String property : new String[]{"org.fractalmicro.root", "org.fractalmicro.volume"}) {
            String named = System.getProperty(property, "");
            if (!named.isBlank()) return Paths.get(named).toAbsolutePath().normalize();
        }
        return USER_HOME.resolve(".fractaldt");
    }

    /** What the volume was called before, kept only so that one can be moved to the other. */
    public static final Path FORMER_ROOT = USER_HOME.resolve(".fractalos");

    /**
     * Moves a volume left under the old name to the new one, once.
     *
     * The directory was called .fractalos while this was a program that looked like a
     * desktop. Anyone who ran an earlier build has their preferences, logs and programs in
     * the old place, so the whole directory is moved rather than started again.
     *
     * A volume with something running out of it cannot be renamed on Windows, so the
     * daemons are asked to stop first. A move that still cannot be made is said loudly,
     * since carrying on would leave an empty volume beside a full one.
     */
    public static synchronized boolean adoptFormerVolume() {
        if (!Files.isDirectory(FORMER_ROOT)) return true;
        // An empty new volume means an earlier attempt made the directories and then could
        // not move anything into them, so it is still this migration's to do.
        if (Files.exists(ROOT) && !isEmpty(ROOT)) return true;
        try {
            if (Files.exists(ROOT)) Files.delete(ROOT);
            Files.move(FORMER_ROOT, ROOT);
            System.out.println("the volume moved from " + FORMER_ROOT.getFileName()
                               + " to " + ROOT.getFileName());
            return true;
        } catch (IOException blocked) {
            // Something is running out of it. Whoever can stop that is above this layer,
            // so this says no and lets them try again.
            return false;
        }
    }

    /** Whether a directory holds nothing, which is what a failed move leaves behind. */
    private static boolean isEmpty(Path dir) {
        try (java.util.stream.Stream<Path> kids = Files.list(dir)) {
            return kids.findAny().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * The runtime to start another program with.
     *
     * Which java this is running on is a fact about the machine, not about bundles, so
     * anything that needs to start a process can ask here without knowing what a bundle
     * is. Falls back to whatever is on the path.
     */
    public static String javaCommand() {
        String home = System.getProperty("java.home", "");
        if (home.isEmpty()) return "java";
        java.io.File javaw = new java.io.File(home, "bin/javaw.exe");
        if (javaw.isFile()) return javaw.getAbsolutePath();
        java.io.File java = new java.io.File(home, "bin/java");
        return java.isFile() ? java.getAbsolutePath() : "java";
    }

    public static String shortName() {
        String name = System.getProperty("user.name", "user");
        return name.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
    }

    public static Path system()            { return ROOT.resolve("System"); }
    public static Path systemLibrary()     { return system().resolve("Library"); }
    public static Path coreServices()      { return systemLibrary().resolve("CoreServices"); }
    public static Path coreTypesResources() {
        return coreServices().resolve("CoreTypes.bundle/Contents/Resources");
    }
    public static Path dockResources() {
        return coreServices().resolve("Dock.app/Contents/Resources");
    }
    /**
     * Where applications that ship with the system live.
     *
     * These are not the user's to keep: a new build replaces them, and nothing here is
     * preserved between installs. That is the difference between this and
     * {@link #applications()}, which is the user's own and is never written by an install.
     */
    public static Path systemApplications() { return systemLibrary().resolve("Applications"); }

    /** Where a person puts applications of their own. An install never touches this. */
    public static Path applications()      { return ROOT.resolve("Applications"); }
    public static Path applicationsUtilities() { return applications().resolve("Utilities"); }
    public static Path volumes()           { return ROOT.resolve("Volumes"); }
    public static Path libraryPreferences() { return systemLibrary().resolve("Preferences"); }
    public static Path frameworks()        { return systemLibrary().resolve("Frameworks"); }

    /* ------------------------------------------------- where the system's own parts live
     *
     * These are the paths Mac OS X uses, and they are not arbitrary. The loader is not a
     * framework: it is a Mach-O of its own kind at /usr/lib/dyld, which is the name every
     * executable carries in its LC_LOAD_DYLINKER command. The first process is not a
     * framework either: it is /sbin/launchd, which the kernel starts and nothing else may.
     * The system library is a dylib rather than a framework, at /usr/lib/libSystem.B.dylib.
     */

    /** /usr/lib, where the loader and the system library live. */
    public static Path usrLib() { return ROOT.resolve("usr/lib"); }

    /** The loader itself. Every program written here names this path. */
    public static Path dyld() { return usrLib().resolve("dyld"); }

    /** The system library, a dylib and not a framework, as it is on Mac OS X. */
    public static Path libSystem() { return usrLib().resolve("libSystem.B.dylib"); }

    /** /sbin, where the first process lives. */
    public static Path sbin() { return ROOT.resolve("sbin"); }

    /** launchd. The kernel starts it; nothing else may. */
    public static Path launchd() { return sbin().resolve("launchd"); }

    /** A framework's own directory, by name: Foundation.framework and so on. */
    public static Path framework(String name) {
        return frameworks().resolve(name + ".framework");
    }

    /**
     * A framework inside an umbrella, the way CoreServices keeps LaunchServices.
     *
     * The umbrella's Versions/A holds a Frameworks directory, and each thing in it is a
     * whole framework again, with its own Versions and its own Current.
     */
    public static Path subframework(String umbrella, String name) {
        return framework(umbrella).resolve("Versions/A/Frameworks")
                                  .resolve(name + ".framework");
    }

    /**
     * Where a framework keeps a helper it runs.
     *
     * The metadata server is at Metadata.framework/Versions/A/Support/mds on Mac OS X: a
     * plain executable inside the framework whose work it does, rather than a program in
     * its own right.
     */
    public static Path frameworkSupport(Path frameworkDirectory) {
        return frameworkDirectory.resolve("Versions/A/Support");
    }
    /** The one framework this system ships: every program here links against it. */
    public static Path fractalFramework() {
        return frameworks().resolve("Fractal.framework/Versions/A/Fractal.jar");
    }

    public static Path userHome()          { return ROOT.resolve("Users").resolve(shortName()); }
    public static Path userLibrary()       { return userHome().resolve("Library"); }
    public static Path userPreferences()   { return userLibrary().resolve("Preferences"); }
    public static Path userCaches()        { return userLibrary().resolve("Caches"); }

    /** The folder whose contents are shown on the desktop. */
    public static File desktopFolder() {
        File f = USER_HOME.resolve("Desktop-Folder").toFile();
        if (!f.isDirectory()) f.mkdirs();
        return f;
    }

    /** Creates the tree. Cheap enough to call at every start-up. */
    public static void ensure() {
        adoptFormerVolume();
        for (Path p : new Path[]{
                coreTypesResources(), dockResources(), applicationsUtilities(),
                systemApplications(),
                volumes(), libraryPreferences(), userPreferences(), userCaches(),
                frameworks()}) {
            try {
                Files.createDirectories(p);
            } catch (IOException e) {
                System.err.println("could not create " + p + ": " + e.getMessage());
            }
        }
    }
}
