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

import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMMutableDictionary;
import org.fractalmicro.foundation.FMString;

import org.fractalmicro.core.Log;
import org.fractalmicro.macho.MachO;
import org.fractalmicro.macho.Symbols;
import org.fractalmicro.macho.Linker;
import org.fractalmicro.os.OSPaths;
import org.fractalmicro.os.Version;
import org.fractalmicro.plist.Plist;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Putting the system's own parts where they belong.
 *
 * Mac OS X does not keep the system in one place, and the places it does keep things say
 * what those things are. The loader is at /usr/lib/dyld, and it is a Mach-O of its own
 * kind, MH_DYLINKER, which is the name every executable carries in its LC_LOAD_DYLINKER
 * command. The first process is /sbin/launchd, which the kernel starts and nothing else
 * may. The system library is a dylib, /usr/lib/libSystem.B.dylib, and not a framework.
 *
 * What are frameworks are Foundation and AppKit, and CoreServices, which is an umbrella:
 * its Versions/A holds a Frameworks directory, and inside that LaunchServices and Metadata
 * are whole frameworks again, each with its own Versions and its own Current. The metadata
 * server is not a program in its own right but a helper inside the framework whose work it
 * does, at Metadata.framework/Versions/A/Support/mds.
 *
 * This writes that. It is a layout rather than a pile because the layout is the thing being
 * imitated: a system where the loader is a framework and the daemons live wherever is
 * convenient looks the same from outside and is not the same system.
 */
public final class Images {
    private Images() {}

    /**
     * One image: what it is called, where it goes, what kind of Mach-O it is, whether it
     * is wrapped in a framework, and for a program, where it starts.
     *
     * A library has no entry point because nothing runs it. A program has one written into
     * the image rather than known by whatever starts it, which is the only way a program
     * can be started by something that has never heard of it.
     */
    private record Image(String name, Path binary, int fileType, boolean framework,
                         String entry) {
        Image(String name, Path binary, int fileType, boolean framework) {
            this(name, binary, fileType, framework, "");
        }
    }

    /**
     * Where the metadata server starts, and where its program sits on a volume.
     *
     * Named here rather than in the Metadata framework because this is what writes the
     * program, and the job that starts it has to name the same file.
     */
    public static final String MDS_ENTRY = "org.fractalmicro.mds.Server";
    public static final String MDS_PATH =
        "System/Library/Frameworks/CoreServices.framework/Versions/A/Frameworks/"
        + "Metadata.framework/Versions/A/Support/mds";

    /** Where the per-image code is, when a build has said. */
    public static Path builtImages() {
        String named = System.getProperty("org.fractalmicro.images", "");
        return named.isBlank() ? null : Path.of(named);
    }

    /**
     * Lays out the system, one image at a time.
     *
     * Answers how many were written. Nothing happens without a directory of built images:
     * running from a single jar there is nothing to take them apart into, and the one
     * framework that jar already is stays as it is.
     */
    public static int installAll() {
        Path built = builtImages();
        if (built == null || !Files.isDirectory(built)) return 0;

        List<Image> images = List.of(
            new Image("LibSystem", OSPaths.libSystem(), MachO.MH_DYLIB, false),
            new Image("dyld", OSPaths.dyld(), MachO.MH_DYLINKER, false,
                      "org.fractalmicro.dyld.Start"),
            new Image("launchd", OSPaths.launchd(), MachO.MH_EXECUTE, false,
                      "org.fractalmicro.launchd.Init"),
            new Image("Foundation",
                      binaryIn(OSPaths.framework("Foundation"), "Foundation"),
                      MachO.MH_DYLIB, true),
            new Image("LaunchServices",
                      binaryIn(OSPaths.subframework("CoreServices", "LaunchServices"),
                               "LaunchServices"),
                      MachO.MH_DYLIB, true),
            new Image("Metadata",
                      binaryIn(OSPaths.subframework("CoreServices", "Metadata"), "Metadata"),
                      MachO.MH_DYLIB, true),
            new Image("AppKit", binaryIn(OSPaths.framework("AppKit"), "AppKit"),
                      MachO.MH_DYLIB, true),
            // What launchd starts to bring up a screen. On a Mac this is loginwindow, and
            // it is where a session begins: the window server, the Dock and the Finder are
            // its doing, and when it goes they go with it.
            new Image("loginwindow", OSPaths.coreServices().resolve("loginwindow"),
                      MachO.MH_EXECUTE, false, "org.fractalmicro.Main"));

        // Linking happens in two passes, as it does anywhere else. The first reads every
        // image and writes down what it offers; only then can the second say, for each
        // class an image uses, which library will be supplying it. One pass cannot do it,
        // because AppKit uses Foundation and Foundation is no more written yet than AppKit.
        Linker linker = new Linker();
        Map<String, byte[]> code = new LinkedHashMap<>();
        for (Image image : images) {
            Path from = codeFor(built, image);
            if (from == null) {
                Log.info("no built image for " + image.name());
                continue;
            }
            try {
                byte[] bytes = Files.readAllBytes(from);
                code.put(image.name(), bytes);
                linker.add(installPathOf(image.name()),
                           List.copyOf(Symbols.of(bytes).defined()), List.of());
            } catch (IOException e) {
                Log.error("could not read the built image for " + image.name(), e);
            }
        }
        // The umbrella has nothing of its own and is written last, but anything linking it
        // has to be resolvable before then, so it goes in as what it passes on.
        linker.add(installPathOf("CoreServices"), List.of(),
                   List.of(installPathOf("LaunchServices"), installPathOf("Metadata")));

        int written = 0;
        for (Image image : images) {
            byte[] bytes = code.get(image.name());
            if (bytes == null) continue;
            try {
                write(image, bytes, linker);
                written++;
            } catch (IOException e) {
                Log.error("could not install " + image.name(), e);
            }
        }

        try {
            umbrella();
            metadataSupport(built, linker);
        } catch (IOException e) {
            Log.error("the CoreServices umbrella could not be finished", e);
        }
        Log.info("images installed: " + written);
        return written;
    }

    /**
     * A framework's words and pictures, copied in beside its binary.
     *
     * A framework has text a person reads, and it belongs to the framework rather than to
     * whichever program loaded it: AppKit says "Cancel" on a button in every program on
     * the volume, so a translation of it has to live where AppKit is.
     */
    private static void copyResources(String name, Path into) throws IOException {
        Path built = builtImages();
        Path from = built == null ? null : built.resolve(name + ".resources");
        if (from == null || !Files.isDirectory(from)) return;
        try (java.util.stream.Stream<Path> walk = Files.walk(from)) {
            for (Path each : walk.toList()) {
                Path target = into.resolve(from.relativize(each).toString());
                if (Files.isDirectory(each)) Files.createDirectories(target);
                else Files.copy(each, target,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * The built code for one image.
     *
     * AppKit is still built in one stage with the Finder, so what comes out of the build
     * carries that stage's name. The image it becomes is AppKit either way.
     */
    private static Path codeFor(Path built, Image image) {
        Path code = built.resolve(image.name() + ".jar");
        if (!Files.isReadable(code) && "AppKit".equals(image.name())) {
            code = built.resolve("AppKitFinder.jar");
        }
        return Files.isReadable(code) ? code : null;
    }

    /**
     * A linker that knows what the installed libraries offer.
     *
     * Anything built after the frameworks are installed, which means every application,
     * needs this to work out where each class it uses will come from. What it knows is
     * read out of the images themselves rather than described here.
     */
    public static Linker installedLinker() {
        Linker linker = new Linker();
        for (String installName : Frameworks.all()) {
            Path binary = Dyld.resolveFramework(installName);
            if (binary == null) continue;
            try {
                linker.addImage(binary);
            } catch (IOException notAnImage) {
                Log.info("not a readable image: " + installName);
            }
        }
        return linker;
    }

    private static Path binaryIn(Path frameworkDirectory, String name) {
        return frameworkDirectory.resolve("Versions/A").resolve(name);
    }

    /**
     * Writes one image: its executable, its code beside it, and for a framework the
     * Versions and the symlinks that make it one.
     */
    private static void write(Image image, byte[] resources, Linker linker)
            throws IOException {
        Path binary = image.binary();
        Files.createDirectories(binary.getParent());

        // A program says where it starts, inside itself. Nothing that starts one needs to
        // be told separately, and nothing can start it somewhere else.
        if (!image.entry().isEmpty()) resources = withEntry(resources, image.entry());

        // What this image defines, and for everything it uses and does not define, which
        // of the libraries it links will be supplying it.
        Symbols.Set2 symbols = Symbols.of(resources);
        List<String> linked = linkedBy(image.name());
        Map<String, String> imports = linker.resolve(symbols.referenced(), linked);

        // The install name goes in as LC_ID_DYLIB, because that is the name a client
        // records when it links this: not where the file happens to be sitting now.
        byte[] program = MachO.build(installPathOf(image.name()), linked, resources,
                                     image.fileType(), List.of(), RUNPATHS,
                                     List.copyOf(symbols.defined()), imports);
        Files.write(binary, program);
        binary.toFile().setExecutable(true);

        if (!image.framework()) return;

        Path versions = binary.getParent().getParent();
        Path root = versions.getParent();
        Files.createDirectories(binary.getParent().resolve("Resources"));
        copyResources(image.name(), binary.getParent().resolve("Resources"));
        Plist.write(binary.getParent().resolve("Resources/Info.plist"),
                    frameworkInfo(image.name()));
        Install.link(versions.resolve("Current"), "A");
        Install.link(root.resolve(image.name()), "Versions/Current/" + image.name());
        Install.link(root.resolve("Resources"), "Versions/Current/Resources");
    }

    /**
     * What @rpath stands for.
     *
     * The frameworks are in the system's own directory and an application carries this so
     * a name beginning  is found there. One shipping a framework of its own would
     * add /../Frameworks and be found either way, which is why  exists.
     */
    public static final List<String> RUNPATHS =
        List.of("/System/Library/Frameworks", "@loader_path/../Frameworks");

    /** What an image says it links, in its load commands. */
    private static List<String> linkedBy(String name) {
        return switch (name) {
            case "LibSystem", "dyld" -> List.of();
            case "Foundation" -> List.of(installPathOf("LibSystem"));
            // What starts everything else needs the loader, because starting a program is
            // asking the loader to map one, and it needs Foundation for the task table and
            // for knowing where anything is.
            case "launchd" -> List.of(installPathOf("Foundation"), installPathOf("dyld"),
                                      installPathOf("LibSystem"));
            case "LaunchServices", "Metadata" ->
                List.of(installPathOf("Foundation"), installPathOf("LibSystem"));
            // The support program inside Metadata.framework. It carries the framework's
            // code, so it links what the framework links; without the log it gets as far
            // as its own first line and no further, on every restart, forever.
            case "mds" -> List.of(installPathOf("Metadata"), installPathOf("Foundation"),
                                  installPathOf("LibSystem"));
            // AppKit reaches LaunchServices to open a program and Metadata to search,
            // and links the umbrella rather than the frameworks inside it.
            case "AppKit" -> List.of(installPathOf("Foundation"), installPathOf("CoreServices"),
                                     installPathOf("LibSystem"));
            // The session links everything a program can link, because it is the program
            // that puts the screen up and then hands it to everything else.
            // The session links launchd because launchctl lives in this executable: the
            // job commands are a client of task 1 and have to be able to name its jobs.
            case "loginwindow" -> List.of(installPathOf("Foundation"), installPathOf("AppKit"),
                                          installPathOf("CoreServices"),
                                          installPathOf("launchd"),
                                          installPathOf("LibSystem"));
            default -> List.of();
        };
    }

    /**
     * Writes the entry point into a copy of the image's code.
     *
     * The archive carries a manifest, and the manifest carries the name of the class that
     * runs first. Anything already in it is kept: the entry is one more line, not a
     * replacement for whatever else was recorded when the code was built.
     */
    private static byte[] withEntry(byte[] code, String entry) throws IOException {
        java.util.Map<String, byte[]> held = new LinkedHashMap<>();
        try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(code))) {
            java.util.zip.ZipEntry one;
            while ((one = in.getNextEntry()) != null) {
                if (!one.isDirectory()) held.put(one.getName(), in.readAllBytes());
            }
        }

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        byte[] existing = held.get("META-INF/MANIFEST.MF");
        if (existing != null) {
            manifest.read(new java.io.ByteArrayInputStream(existing));
        }
        manifest.getMainAttributes().put(
            java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
            new java.util.jar.Attributes.Name(org.fractalmicro.dyld.Start.ENTRY_ATTRIBUTE), entry);

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.jar.JarOutputStream jar = new java.util.jar.JarOutputStream(out, manifest)) {
            for (java.util.Map.Entry<String, byte[]> file : held.entrySet()) {
                if ("META-INF/MANIFEST.MF".equals(file.getKey())) continue;
                jar.putNextEntry(new java.util.zip.ZipEntry(file.getKey()));
                jar.write(file.getValue());
                jar.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /** What a load command calls an image. */
    private static String installPathOf(String name) {
        return switch (name) {
            case "LibSystem" -> "/usr/lib/libSystem.B.dylib";
            case "dyld" -> "/usr/lib/dyld";
            case "launchd" -> "/sbin/launchd";
            case "LaunchServices", "Metadata" ->
                "@rpath/CoreServices.framework/Versions/A/Frameworks/"
                + name + ".framework/Versions/A/" + name;
            case "CoreServices" -> "@rpath/CoreServices.framework/Versions/A/CoreServices";
            case "loginwindow" -> "/System/Library/CoreServices/loginwindow";
            default -> "@rpath/" + name + ".framework/Versions/A/" + name;
        };
    }

    /**
     * Finishes the umbrella: the Frameworks directory it holds its sub-frameworks in, and
     * the symlinks that make the umbrella itself a framework.
     */
    private static void umbrella() throws IOException {
        Path root = OSPaths.framework("CoreServices");
        Path versionA = root.resolve("Versions/A");
        Files.createDirectories(versionA.resolve("Frameworks"));
        Files.createDirectories(versionA.resolve("Resources"));

        // An umbrella has a binary of its own that re-exports what it covers, so a program
        // links CoreServices and gets LaunchServices and Metadata without naming either.
        // Apple's own rule is that you do not link a sub-framework directly.
        byte[] program = MachO.build(installPathOf("CoreServices"),
            List.of(installPathOf("Foundation")), new byte[0], MachO.MH_DYLIB,
            List.of(installPathOf("LaunchServices"), installPathOf("Metadata")), RUNPATHS);
        Path binary = versionA.resolve("CoreServices");
        Files.write(binary, program);
        binary.toFile().setExecutable(true);
        Install.link(root.resolve("CoreServices"), "Versions/Current/CoreServices");
        Plist.write(versionA.resolve("Resources/Info.plist"), frameworkInfo("CoreServices"));
        Install.link(root.resolve("Versions/Current"), "A");
        Install.link(root.resolve("Frameworks"), "Versions/Current/Frameworks");
        Install.link(root.resolve("Resources"), "Versions/Current/Resources");
    }

    /**
     * The metadata server, inside the framework whose work it does.
     *
     * Mac OS X keeps it at Metadata.framework/Versions/A/Support/mds, a plain executable
     * rather than a program a person opens. It says where it starts, as every program
     * image does: without that the loader has nothing to run and launchd starts it again
     * every ten seconds for as long as the machine is on.
     */
    private static void metadataSupport(Path built, Linker linker) throws IOException {
        Path support = OSPaths.frameworkSupport(
            OSPaths.subframework("CoreServices", "Metadata"));
        Files.createDirectories(support);
        Path code = built.resolve("Metadata.jar");
        if (!Files.isReadable(code)) return;
        byte[] resources = withEntry(Files.readAllBytes(code), MDS_ENTRY);
        Symbols.Set2 symbols = Symbols.of(resources);
        List<String> linked = linkedBy("mds");
        byte[] program = MachO.build("mds", linked, resources, MachO.MH_EXECUTE,
                                     List.of(), RUNPATHS,
                                     List.copyOf(symbols.defined()),
                                     linker.resolve(symbols.referenced(), linked));
        Path mds = support.resolve("mds");
        Files.write(mds, program);
        mds.toFile().setExecutable(true);
    }

    /** A framework's Info.plist. FMWK is the package type a framework carries. */
    private static FMDictionary frameworkInfo(String name) {
        FMMutableDictionary info = FMMutableDictionary.empty();
        // The framework's own name, with its case, which is how a framework bundle is
        // identified everywhere it matters: com.apple.Foundation, not com.apple.foundation.
        // Applications are lowercase because that is how application identifiers read; a
        // framework is named after the framework.
        info.set(Bundle.IDENTIFIER, FMString.of("org.fractalmicro." + name));
        info.set(Bundle.NAME, name);
        info.set(Bundle.EXECUTABLE, name);
        info.set(Bundle.PACKAGE_TYPE, "FMWK");
        info.set(Bundle.SIGNATURE, OSPaths.CREATOR);
        info.set(Bundle.SHORT_VERSION, Version.number());
        info.set(Bundle.VERSION, Version.build());
        info.set(Bundle.INFO_DICTIONARY_VERSION, "6.0");
        return info.asDictionary();
    }
}
