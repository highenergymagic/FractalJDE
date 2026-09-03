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
        // In Applications rather than in Utilities, where a Mac keeps it. A terminal on
        // this system is how its own programs are run at all, which makes it one of the
        // things a person came here to use rather than one they go looking for.
        new Spec("Terminal", PREFIX + "terminal", "org.fractalmicro.terminal.Terminal",
                 Where.SYSTEM_APPLICATIONS, false,
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
        // The Quick Look generators, which say what they show as a type rather than as a
        // list of suffixes. Between them that is most of a volume: the image one is asked
        // about a kind of image it has never heard of, and the text one about a language
        // nobody had written when it was built.
        new Spec("Image", PREFIX + "quicklook.image",
                 "org.fractalmicro.qlgenerators.ImageGenerator", Where.QUICK_LOOK, true,
                 java.util.List.of(), Frameworks.COCOA, false,
                 java.util.List.of("public.image")),
        new Spec("Text", PREFIX + "quicklook.text",
                 "org.fractalmicro.qlgenerators.TextGenerator", Where.QUICK_LOOK, true,
                 java.util.List.of(), Frameworks.COCOA, false,
                 java.util.List.of("public.text")),
        new Spec("PropertyList", PREFIX + "quicklook.propertylist",
                 "org.fractalmicro.qlgenerators.PropertyListGenerator", Where.QUICK_LOOK,
                 true, java.util.List.of(), Frameworks.COCOA, false,
                 java.util.List.of("com.apple.property-list")),
        // The Spotlight importers, which say what they can read the same way. Without one
        // the index knows a file is called something and nothing about what it holds.
        new Spec("Text", PREFIX + "spotlight.text",
                 "org.fractalmicro.mdimporters.TextImporter", Where.SPOTLIGHT, true,
                 java.util.List.of(), Frameworks.IMPORTER, false,
                 java.util.List.of("public.text")),
        new Spec("Image", PREFIX + "spotlight.image",
                 "org.fractalmicro.mdimporters.ImageImporter", Where.SPOTLIGHT, true,
                 java.util.List.of(), Frameworks.IMPORTER, false,
                 java.util.List.of("public.image")),
        new Spec("Application", PREFIX + "spotlight.application",
                 "org.fractalmicro.mdimporters.ApplicationImporter", Where.SPOTLIGHT, true,
                 java.util.List.of(), Frameworks.IMPORTER, false,
                 java.util.List.of("org.fractalmicro.application")),
    };

    /**
     * Where a program installs.
     *
     * SYSTEM_APPLICATIONS is the system's own, SYSTEM_UTILITIES its Utilities, and
     * CORE_SERVICES the parts of the desktop that are programs but are not things a person
     * opens. The last two are plug-ins: loaded by whoever needs one rather than started.
     */
    private enum Where { SYSTEM_APPLICATIONS, SYSTEM_UTILITIES, CORE_SERVICES,
                         MENU_EXTRAS, QUICK_LOOK, SPOTLIGHT }

    /** Where the Quick Look generators live, and what one is called. */
    static final String QUICK_LOOK_FOLDER = "QuickLook";
    static final String QUICK_LOOK_EXTENSION = ".qlgenerator";

    /** The same for the Spotlight importers. */
    static final String SPOTLIGHT_FOLDER = "Spotlight";
    static final String SPOTLIGHT_EXTENSION = ".mdimporter";

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
                File parent = folderFor(spec.where());
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
                // A program with terminology says which file it is in, the way a Mac
                // does. Without one it is still scriptable; nothing can put the commands
                // into words, which is the difference between the two.
                if (terminologyOf(spec) != null) {
                    info.set(org.fractalmicro.scripting.FMScriptTerminology.DEFINITION_KEY,
                             spec.name().replace(" ", "") + ".sdef");
                }

                String extension = switch (spec.where()) {
                    case MENU_EXTRAS -> ".menu";
                    case QUICK_LOOK -> QUICK_LOOK_EXTENSION;
                    case SPOTLIGHT -> SPOTLIGHT_EXTENSION;
                    default -> Bundle.EXTENSION;
                };
                Bundle bundle = Bundle.create(parent, spec.name(), info, spec.own(),
                                              extension, spec.linked());
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
        String role = switch (spec.where()) {
            case QUICK_LOOK -> "QLGenerator";
            case SPOTLIGHT -> "MDImporter";
            default -> "Editor";
        };
        boolean plugIn = !"Editor".equals(role);
        FMMutableDictionary one = FMMutableDictionary.empty();
        one.set(Bundle.TYPE_NAME, spec.name() + " document");
        // What the declaration is for. A plug-in shows or reads a kind of file; it does
        // not open one, and Launch Services has to be able to tell the two apart.
        one.set(Bundle.TYPE_ROLE, role);
        one.set(Bundle.HANDLER_RANK, plugIn ? "None" : "Default");
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
    /**
     * Says how a program's terminology is found, which is this layer's to know.
     *
     * Read once each and kept. A terminology is a file that ships with a program and does
     * not change while it runs, and a script asking for it a hundred times is a script
     * asking the same question a hundred times.
     */
    private static void installTerminology() {
        Map<String, org.fractalmicro.scripting.FMScriptTerminology> read =
            new java.util.concurrent.ConcurrentHashMap<>();
        org.fractalmicro.scripting.FMScript.setDictionaries(name -> {
            Bundle bundle = byIdentifierOrName(name.toString());
            if (bundle == null) return null;
            String key = bundle.identifier().toString();
            org.fractalmicro.scripting.FMScriptTerminology already = read.get(key);
            if (already != null) return already;
            File file = bundle.resource(
                org.fractalmicro.foundation.FMString.of(
                    bundle.displayName().toString().replace(" ", "")),
                org.fractalmicro.scripting.FMScriptTerminology.EXTENSION);
            if (file == null) return null;
            try {
                org.fractalmicro.scripting.FMScriptTerminology words =
                    org.fractalmicro.scripting.FMScriptTerminology.read(file);
                read.put(key, words);
                return words;
            } catch (IOException unreadable) {
                Log.info("the terminology of " + bundle.displayName()
                         + " could not be read: " + unreadable.getMessage());
                return null;
            }
        });
    }

    /** The program's terminology, when it ships one. */
    private static File terminologyOf(Spec spec) {
        File resources = resourcesOf(spec);
        if (resources == null) return null;
        File found = new File(resources, spec.name().replace(" ", "") + ".sdef");
        return found.isFile() ? found : null;
    }

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

    /** Where a program of each kind installs. */
    private static File folderFor(Where where) {
        return switch (where) {
            case SYSTEM_APPLICATIONS -> OSPaths.systemApplications().toFile();
            case SYSTEM_UTILITIES ->
                OSPaths.systemApplications().resolve("Utilities").toFile();
            case CORE_SERVICES -> OSPaths.coreServices().toFile();
            case MENU_EXTRAS -> OSPaths.coreServices().resolve("Menu Extras").toFile();
            case QUICK_LOOK -> OSPaths.systemLibrary().resolve(QUICK_LOOK_FOLDER).toFile();
            case SPOTLIGHT -> OSPaths.systemLibrary().resolve(SPOTLIGHT_FOLDER).toFile();
        };
    }

    /**
     * Removes a copy of a built-in program from anywhere it no longer belongs.
     *
     * A copy in a person's own Applications hides the system's, so it is the stale one
     * that opens. A copy left where a program used to ship is the same problem: it would
     * be in the list twice, and one of the two would be last year's.
     */
    private static void retireMovedBuiltIns() {
        for (Spec spec : BUILT_IN) {
            File belongs = folderFor(spec.where());
            for (File folder : new File[]{OSPaths.applications().toFile(),
                                          OSPaths.applicationsUtilities().toFile(),
                                          OSPaths.systemApplications().toFile(),
                                          OSPaths.systemApplications()
                                                 .resolve("Utilities").toFile()}) {
                if (folder.equals(belongs)) continue;
                File left = new File(folder, spec.name() + Bundle.EXTENSION);
                if (!left.isDirectory()) continue;
                Bundle found = Bundle.read(left);
                if (found == null
                        || !found.identifier().sameAs(FMString.of(spec.identifier()))) {
                    continue;
                }
                if (delete(left)) {
                    Log.info("retired " + spec.name() + " from " + folder.getName()
                             + "; it is not where that program lives");
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
        installTerminology();
        for (File folder : new File[]{
                OSPaths.systemApplications().toFile(),
                OSPaths.systemApplications().resolve("Utilities").toFile(),
                OSPaths.coreServices().toFile(),
                OSPaths.applications().toFile(),
                OSPaths.applicationsUtilities().toFile(),
                OSPaths.coreServices().resolve("Menu Extras").toFile(),
                OSPaths.systemLibrary().resolve(QUICK_LOOK_FOLDER).toFile(),
                OSPaths.systemLibrary().resolve(SPOTLIGHT_FOLDER).toFile()}) {
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

    /**
     * The program called that, by the name on the screen rather than by its identifier.
     *
     * Both names are used. One program has another's identifier and somebody writing a
     * script has the name, and either has to find the same program.
     */
    public static synchronized Bundle byName(String name) {
        if (name == null || name.isEmpty()) return null;
        for (Bundle one : BY_IDENTIFIER.values()) {
            if (one.displayName().toString().equals(name)) return one;
        }
        return null;
    }

    /** Either name, whichever the caller happens to have. */
    public static synchronized Bundle byIdentifierOrName(String name) {
        Bundle found = byIdentifier(name);
        return found != null ? found : byName(name);
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
     * A program of this system is handed to whatever knows how to run one, and started
     * through the loader when nothing does, which is a process outside the desktop: a
     * command line tool has no AppKit in it and still has to be able to open something.
     * A bundle naming no principal class wraps a program belonging to the host, and the
     * host is asked to open that.
     */
    public static boolean open(Bundle bundle, List<File> files) {
        if (bundle == null) return false;
        Launcher who = launcher;
        boolean isProgram = !bundle.principalClass().isEmpty();
        if (isProgram) {
            if (who != null && who.open(bundle, files)) return true;
            return Dyld.start(bundle, files);
        }
        File executable = bundle.machOExecutable();
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
