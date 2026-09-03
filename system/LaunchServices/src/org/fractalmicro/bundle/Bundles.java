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

import org.fractalmicro.foundation.FMMutableDictionary;
import org.fractalmicro.foundation.FMString;

import org.fractalmicro.core.Log;
import org.fractalmicro.os.OSPaths;
import org.fractalmicro.os.Version;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * The bundles this system knows about, and the ones it installs for itself.
 *
 * Applications live in ~/.fractaldt/Applications, utilities in Applications/Utilities,
 * and the Finder in System/Library/CoreServices, which is where Mac OS X keeps it. Each
 * is a real bundle on disk with an Info.plist, so it can be looked at, copied, or opened
 * from Windows as well as from here.
 */
public final class Bundles {

    /** How a bundle with no icon gets one drawn. Drawing belongs to the layer that draws. */
    public interface IconWriter {
        void draw(String name, java.io.File into);
    }

    private static volatile IconWriter drawIcon;

    public static void setIconWriter(IconWriter how) { drawIcon = how; }
    private Bundles() {}

    public static final String PREFIX = "org.fractalmicro.";

    private static final Map<String, Bundle> BY_IDENTIFIER = new LinkedHashMap<>();

    /** The bundles this system ships. */
    private static final Spec[] BUILT_IN = {
        // Finder opens other programs and asks what is installed, so it links CoreServices
        // as well. No other program here does, and none of them can.
        //
        // Its own code goes in its own bundle, like every other program's. It used to have
        // none, because the file manager was compiled into AppKit and every program that
        // linked AppKit got the Finder with it, which is not what linking a window library
        // should hand anybody.
        new Spec("Finder", PREFIX + "finder", "org.fractalmicro.app.FinderApp",
                 Where.CORE_SERVICES, true,
                 java.util.List.of("org.fractalmicro.ui", "org.fractalmicro.app"),
                 Frameworks.COCOA_AND_SERVICES, false),
        new Spec("System Preferences", PREFIX + "systempreferences", "org.fractalmicro.systempreferences.SystemPreferences",
                 Where.SYSTEM_APPLICATIONS, false,
                 java.util.List.of("org.fractalmicro.systempreferences"), Frameworks.COCOA,
                 true),
        new Spec("System Profiler", PREFIX + "systemprofiler", "org.fractalmicro.systemprofiler.SystemProfiler",
                 Where.SYSTEM_UTILITIES, false,
                 java.util.List.of("org.fractalmicro.systemprofiler"), Frameworks.COCOA,
                 true),
        new Spec("Activity Monitor", PREFIX + "activitymonitor", "org.fractalmicro.activitymonitor.ActivityMonitor",
                 Where.SYSTEM_UTILITIES, false,
                 java.util.List.of("org.fractalmicro.activitymonitor"), Frameworks.COCOA,
                 true),
        // The one program here that opens documents, so the one that says which. Everything
        // that conforms to public.text, which is the plain kinds, the marked up ones and
        // every kind of source: an editor that can open a .txt can open a .java, and saying
        // so by naming the family is the difference between a type and a list of extensions.
        new Spec("TextEdit", PREFIX + "textedit", "org.fractalmicro.textedit.TextEdit",
                 Where.SYSTEM_APPLICATIONS, false,
                 java.util.List.of("org.fractalmicro.textedit"), Frameworks.COCOA,
                 true, java.util.List.of("public.text", "public.rtf")),
        new Spec("Terminal", PREFIX + "terminal", "org.fractalmicro.terminal.Terminal",
                 Where.SYSTEM_UTILITIES, false,
                 java.util.List.of("org.fractalmicro.terminal"), Frameworks.COCOA,
                 true),
        new Spec("Calculator", PREFIX + "calculator", "org.fractalmicro.calculator.Calculator",
                 Where.SYSTEM_APPLICATIONS, false,
                 java.util.List.of("org.fractalmicro.calculator"), Frameworks.COCOA,
                 true),
        new Spec("Clock", PREFIX + "menuextra.clock",
                 "org.fractalmicro.menuextras.ClockExtra", Where.MENU_EXTRAS, true),
        new Spec("User", PREFIX + "menuextra.user",
                 "org.fractalmicro.menuextras.UserExtra", Where.MENU_EXTRAS, true),
        new Spec("Network", PREFIX + "menuextra.network",
                 "org.fractalmicro.menuextras.NetworkExtra", Where.MENU_EXTRAS, true),
        new Spec("Volume", PREFIX + "menuextra.volume",
                 "org.fractalmicro.menuextras.VolumeExtra", Where.MENU_EXTRAS, true),
        new Spec("Spotlight", PREFIX + "menuextra.spotlight",
                 "org.fractalmicro.menuextras.SpotlightExtra", Where.MENU_EXTRAS, true),
    };

    /**
     * Where a program installs.
     *
     * SYSTEM_APPLICATIONS is the system's own, replaced by every install.
     * SYSTEM_UTILITIES is the same directory's Utilities. CORE_SERVICES is for the parts
     * of the desktop that are programs but are not things a person opens.
     */
    private enum Where { SYSTEM_APPLICATIONS, SYSTEM_UTILITIES, CORE_SERVICES,
                         MENU_EXTRAS }

    /**
     * One built-in program.
     *
     * `own` names the packages that go inside its executable. Most are a single class over
     * the desktop's own windows, because that is all they are. The two that are separate
     * programs say so by naming a package.
     */
    private record Spec(String name, String identifier, String principalClass,
                        Where where, boolean background,
                        java.util.List<String> own, java.util.List<String> linked,
                        boolean ownProcess, java.util.List<String> opens) {
        Spec(String name, String identifier, String principalClass,
             Where where, boolean background) {
            this(name, identifier, principalClass, where, background,
                 java.util.List.of(), Frameworks.COCOA, false, java.util.List.of());
        }

        Spec(String name, String identifier, String principalClass,
             Where where, boolean background,
             java.util.List<String> own) {
            this(name, identifier, principalClass, where, background,
                 own, Frameworks.COCOA, false, java.util.List.of());
        }

        Spec(String name, String identifier, String principalClass,
             Where where, boolean background,
             java.util.List<String> own, java.util.List<String> linked,
             boolean ownProcess) {
            this(name, identifier, principalClass, where, background,
                 own, linked, ownProcess, java.util.List.of());
        }
    }

    /**
     * Whether there is anywhere to take an application's code from.
     *
     * A build making a volume says where it put the class files. A system running from one
     * archive has that archive. A system running from its own images has neither, and
     * nothing it could write into a bundle that is not already in the bundle.
     */
    private static boolean canWriteBundles() {
        if (!System.getProperty("org.fractalmicro.appcode", "").isBlank()) return true;
        File running = org.fractalmicro.bundle.Install.runningCode();
        return running != null && running.isFile();
    }

    /**
     * Writes the built-in bundles, then reads everything that is installed.
     *
     * Writing them needs somewhere to take the code from. A system started from its own
     * images has no archive to copy out of, so with no source what is installed is read
     * and left alone rather than replaced with empty bundles.
     */
    public static synchronized void install() {
        // Programs link against the framework, so it has to be there before they are
        // written.
        org.fractalmicro.bundle.Install.ensureInstalled();
        if (!canWriteBundles()) {
            Log.info("no source for application code; the installed programs are left alone");
            scan();
            Log.info("bundles read: " + BY_IDENTIFIER.size());
            return;
        }
        for (Spec spec : BUILT_IN) {
            try {
                File parent = switch (spec.where()) {
                    case SYSTEM_APPLICATIONS -> OSPaths.systemApplications().toFile();
                    case SYSTEM_UTILITIES ->
                        OSPaths.systemApplications().resolve("Utilities").toFile();
                    case CORE_SERVICES -> OSPaths.coreServices().toFile();
                    case MENU_EXTRAS ->
                        OSPaths.coreServices().resolve("Menu Extras").toFile();
                };
                if (!parent.isDirectory() && !parent.mkdirs()) continue;

                FMMutableDictionary info = FMMutableDictionary.empty();
                info.set(Bundle.IDENTIFIER, spec.identifier());
                info.set(Bundle.NAME, spec.name());
                info.set(Bundle.DISPLAY_NAME, spec.name());
                info.set(Bundle.EXECUTABLE, spec.name());
                info.set(Bundle.ICON_FILE, spec.name() + ".icns");
                info.set(Bundle.PRINCIPAL_CLASS, spec.principalClass());
                info.set(Bundle.SHORT_VERSION, Version.number());
                info.set(Bundle.VERSION, Version.build());
                info.set(Bundle.MINIMUM_SYSTEM, "10.6");
                info.set(Bundle.CATEGORY, "public.app-category.utilities");
                if (spec.ownProcess()) info.set(OWN_PROCESS, Boolean.TRUE);
                if (spec.background()) info.set(Bundle.BACKGROUND_ONLY, Boolean.TRUE);
                if (!spec.opens().isEmpty()) {
                    info.set(Bundle.DOCUMENT_TYPES, documentTypes(spec));
                }

                Bundle bundle = spec.where() == Where.MENU_EXTRAS
                    ? Bundle.create(parent, spec.name(), info, "", spec.own(), ".menu",
                                    spec.linked())
                    : Bundle.create(parent, spec.name(), info,
                                    "--open-app " + spec.identifier(), spec.own(),
                                    Bundle.EXTENSION, spec.linked());
                if (bundle != null) {
                    writeIcon(bundle, spec.name());
                    copyResources(spec, bundle);
                    BY_IDENTIFIER.put(bundle.identifier().toString(), bundle);
                }
            } catch (IOException e) {
                Log.error("could not install the bundle " + spec.name(), e);
            }
        }
        retireMovedBuiltIns();
        scan();
        Log.info("bundles installed: " + BY_IDENTIFIER.size());
    }

    /**
     * What a program says it opens, in the shape a bundle says it.
     *
     * One entry, because a built-in program handles one family. A program with several
     * kinds of document writes several, which is why this is a list.
     */
    private static java.util.List<Object> documentTypes(Spec spec) {
        FMMutableDictionary one = FMMutableDictionary.empty();
        one.set(Bundle.TYPE_NAME, spec.name() + " document");
        one.set(Bundle.TYPE_ROLE, "Editor");
        one.set(Bundle.HANDLER_RANK, "Default");
        one.set(Bundle.CONTENT_TYPES, java.util.List.copyOf(spec.opens()));
        return java.util.List.of(one.asDictionary().asMap());
    }

    /**
     * Copies a program's own resources into its bundle.
     *
     * Interface files and the words in each language. They are not code: needing a rebuild
     * to add a translation would defeat keeping them apart. Nothing to copy is ordinary
     * for a program whose window is still described in code.
     */
    private static void copyResources(Spec spec, Bundle bundle) {
        File from = resourcesOf(spec);
        if (from == null) return;
        File into = new File(bundle.root(), "Contents/Resources");
        try {
            copyTree(from.toPath(), into.toPath());
        } catch (IOException e) {
            Log.info("could not copy the resources of " + spec.name() + ": " + e.getMessage());
        }
    }

    /**
     * Where a program's resources are, whichever way this is running.
     *
     * A build says where it put the compiled code and stages resources beside it. Run out
     * of a checkout they are still in the tree. The staged copy is tried first, so a volume
     * being built cannot pick up whatever is in the directory the build started from.
     */
    private static File resourcesOf(Spec spec) {
        String plain = spec.name().replace(" ", "");
        String staged = System.getProperty("org.fractalmicro.appcode", "");
        if (!staged.isBlank()) {
            File beside = new File(staged, plain + ".resources");
            return beside.isDirectory() ? beside : null;
        }
        for (String where : new String[]{"apps/" + plain + "/resources",
                                         "system/" + plain + "/resources"}) {
            File in = new File(where);
            if (in.isDirectory()) return in;
        }
        return null;
    }

    private static void copyTree(java.nio.file.Path from, java.nio.file.Path into)
            throws IOException {
        try (var walk = java.nio.file.Files.walk(from)) {
            for (java.nio.file.Path one : walk.toList()) {
                java.nio.file.Path target = into.resolve(from.relativize(one).toString());
                if (java.nio.file.Files.isDirectory(one)) {
                    java.nio.file.Files.createDirectories(target);
                } else {
                    java.nio.file.Files.createDirectories(target.getParent());
                    java.nio.file.Files.copy(one, target,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * Draws an icon into the bundle so it carries its own. Written as a PNG, since this
     * program reads icns but has no writer for it.
     */
    private static void writeIcon(Bundle bundle, String name) {
        File icon = new File(bundle.root(), "Contents/Resources/" + name + ".png");
        if (icon.isFile()) return;
        try {
            if (drawIcon == null) return;
            drawIcon.draw(name, icon);
        } catch (Exception e) {
            Log.info("could not draw an icon for " + name + ": " + e.getMessage());
        }
    }

    /**
     * Removes a program that ships with the system from the folder a person's own live in.
     *
     * These ship in the system's own folder now. A copy left in Applications is not a
     * second program but the same one at an older version, and because a person's copy
     * hides the system's it is the stale one that would open.
     */
    private static void retireMovedBuiltIns() {
        for (Spec spec : BUILT_IN) {
            for (File folder : new File[]{OSPaths.applications().toFile(),
                                          OSPaths.applicationsUtilities().toFile()}) {
                File left = new File(folder, spec.name() + Bundle.EXTENSION);
                if (!left.isDirectory()) continue;
                Bundle found = Bundle.read(left);
                if (found == null || !found.identifier().sameAs(FMString.of(spec.identifier()))) continue;
                if (delete(left)) {
                    Log.info("retired " + spec.name() + " from " + folder.getName()
                             + "; it ships with the system now");
                }
            }
        }
    }

    /** Removes a directory and everything under it. */
    private static boolean delete(File what) {
        File[] kids = what.listFiles();
        if (kids != null) for (File k : kids) delete(k);
        return what.delete();
    }

    /** Reads every bundle in the usual places. */
    public static synchronized void scan() {
        for (File folder : new File[]{
                OSPaths.systemApplications().toFile(),
                OSPaths.systemApplications().resolve("Utilities").toFile(),
                OSPaths.coreServices().toFile(),
                OSPaths.applications().toFile(),
                OSPaths.applicationsUtilities().toFile(),
                OSPaths.coreServices().resolve("Menu Extras").toFile()}) {
            File[] kids = folder.listFiles();
            if (kids == null) continue;
            for (File child : kids) {
                Bundle bundle = Bundle.read(child);
                if (bundle != null && !bundle.identifier().isEmpty()) {
                    BY_IDENTIFIER.put(bundle.identifier().toString(), bundle);
                }
            }
        }
    }

    public static synchronized List<Bundle> all() {
        return new ArrayList<>(BY_IDENTIFIER.values());
    }

    public static synchronized Bundle byIdentifier(String identifier) {
        return BY_IDENTIFIER.get(identifier);
    }

    public static synchronized Bundle byFolder(File folder) {
        for (Bundle b : BY_IDENTIFIER.values()) {
            if (b.root().equals(folder)) return b;
        }
        return Bundle.read(folder);
    }

    /**
     * Whether this program runs in a process of its own.
     *
     * Cocoa has no such key, because on a Mac every application is its own process. Here
     * most are hosted by the desktop and a few are not, and the bundle is the honest place
     * to say which. The day they have all moved out this key goes with them.
     */
    public static final FMString OWN_PROCESS = FMString.of("FMRunsInOwnProcess");

    /**
     * What opening a program actually means, which is not this layer's business.
     *
     * LaunchServices finds a program and knows what it claims. Making the class named by
     * NSPrincipalClass and sending it messages belongs above here with the windows, the
     * way NSApplicationMain does. AppKit puts one in place when the desktop starts.
     */
    public interface Launcher {
        boolean open(Bundle bundle, List<File> files);
        boolean openPart(Bundle bundle, String part);
        boolean openText(Bundle bundle, String text);
    }

    private static volatile Launcher launcher;

    public static void setLauncher(Launcher who) { launcher = who; }

    /**
     * Opens a bundle.
     *
     * A bundle naming a principal class is handed to whatever knows how to run one. A
     * bundle without one, such as a bundle wrapping a program belonging to the host, is
     * handed to the host.
     */
    public static boolean open(Bundle bundle, List<File> files) {
        if (bundle == null) return false;
        Launcher who = launcher;
        boolean isProgram = !bundle.principalClass().isEmpty();
        if (who != null && isProgram && who.open(bundle, files)) return true;
        File executable = bundle.executable();
        if (executable != null) {
            org.fractalmicro.core.Shell.open(executable);
            return true;
        }
        return false;
    }

    public static boolean openIdentifier(String identifier) {
        return open(byIdentifier(identifier), null);
    }

    /** Opens an installed program on some files, the way dropping them on it would. */
    public static synchronized boolean openFiles(String identifier, java.util.List<File> files) {
        return open(byIdentifier(identifier), files);
    }

    /**
     * Opens a named part of an installed program.
     *
     * This is how the desktop reaches a program without linking it. A menu item wanting the
     * Finder preferences names the program and the pane; nothing here knows what class
     * draws it, which is what lets that class ship inside the program rather than here.
     */
    public static synchronized boolean openPart(String identifier, String part) {
        Launcher who = launcher;
        Bundle bundle = byIdentifier(identifier);
        return who != null && bundle != null && who.openPart(bundle, part);
    }

    /** Opens a new document in a program, holding the text a service collected. */
    public static synchronized boolean openText(String identifier, String text) {
        Launcher who = launcher;
        Bundle bundle = byIdentifier(identifier);
        return who != null && bundle != null && who.openText(bundle, text);
    }
}
