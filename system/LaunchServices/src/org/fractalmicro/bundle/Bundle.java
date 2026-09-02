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
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMURL;

import org.fractalmicro.os.OSPaths;
import org.fractalmicro.plist.Plist;
import org.fractalmicro.dyld.Start;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * An application bundle: a directory the Finder shows as a single object.
 *
 * The layout is NeXTSTEP's, by way of Mac OS X:
 *
 *   Finder.app/
 *     Contents/
 *       Info.plist             name, identifier, what to run
 *       PkgInfo                eight bytes, "APPLFMI "
 *       Fractal/Finder         the executable
 *       Resources/Finder.icns  the icon
 *
 * Info.plist uses Apple's key names: CFBundleIdentifier, CFBundleName,
 * CFBundleExecutable, CFBundleIconFile, CFBundlePackageType, CFBundleShortVersionString,
 * NSPrincipalClass.
 *
 * NSPrincipalClass is the Java class the desktop instantiates to open the bundle in
 * process. CFBundleExecutable names the Mach-O; a .cmd and a .sh sit beside it so Windows
 * can start the bundle with the desktop not running.
 */
public final class Bundle {

    public static final String EXTENSION = ".app";

    /**
     * Contents/Fractal, where Mac OS X uses Contents/MacOS. Bundles written before the
     * rename are still read from the old name.
     */
    public static final String EXECUTABLE_DIRECTORY = "Contents/Fractal";
    public static final String OLD_EXECUTABLE_DIRECTORY = "Contents/MacOS";

    /** The four character package type for an application. */
    public static final String PACKAGE_APPLICATION = "APPL";
    /** The creator code: Fractal Microsystems, where another system would say AAPL. */
    public static final String CREATOR = org.fractalmicro.os.OSPaths.CREATOR;

    public static final FMString IDENTIFIER = FMString.of("CFBundleIdentifier");
    public static final FMString NAME = FMString.of("CFBundleName");
    public static final FMString DISPLAY_NAME = FMString.of("CFBundleDisplayName");
    public static final FMString EXECUTABLE = FMString.of("CFBundleExecutable");
    public static final FMString ICON_FILE = FMString.of("CFBundleIconFile");
    public static final FMString PACKAGE_TYPE = FMString.of("CFBundlePackageType");
    public static final FMString SIGNATURE = FMString.of("CFBundleSignature");
    public static final FMString SHORT_VERSION = FMString.of("CFBundleShortVersionString");
    public static final FMString VERSION = FMString.of("CFBundleVersion");
    public static final FMString INFO_DICTIONARY_VERSION = FMString.of("CFBundleInfoDictionaryVersion");
    public static final FMString PRINCIPAL_CLASS = FMString.of("NSPrincipalClass");

    /**
     * The class the loader calls when a program has a process of its own.
     *
     * Named as a string rather than linked, because this layer is below the one it names
     * and has no business knowing what a window is. It is a name in a manifest, which is
     * what an entry point has always been.
     */
    private static final String APPLICATION_MAIN = "org.fractalmicro.appkit.FMApplicationMain";
    public static final FMString MINIMUM_SYSTEM = FMString.of("LSMinimumSystemVersion");
    public static final FMString BACKGROUND_ONLY = FMString.of("LSBackgroundOnly");
    public static final FMString CATEGORY = FMString.of("LSApplicationCategoryType");

    private final File root;
    private final Map<String, Object> info;

    private Bundle(File root, Map<String, Object> info) {
        this.root = root;
        this.info = info;
    }

    /** Reads a bundle, or returns null if this folder is not one. */
    public static Bundle read(File folder) {
        if (folder == null || !folder.isDirectory()) return null;
        if (!isBundleName(folder.getName())) return null;
        File plist = new File(folder, "Contents/Info.plist");
        if (!plist.isFile()) return null;
        try {
            return new Bundle(folder, Plist.readDictionary(plist.toPath()));
        } catch (IOException e) {
            org.fractalmicro.core.Log.info("unreadable bundle at " + folder + ": " + e.getMessage());
            return null;
        }
    }

    public static boolean looksLikeBundle(File folder) {
        return folder != null
            && folder.isDirectory()
            && isBundleName(folder.getName())
            && new File(folder, "Contents/Info.plist").isFile();
    }

    /**
     * The names a bundle directory may have.
     *
     * Not every bundle is an application. A menu extra is a .menu, a set of loadable
     * resources is a .bundle, and a framework is a .framework. They are the same thing
     * inside, and what the directory is called says what opens it rather than what it is.
     */
    public static boolean isBundleName(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        for (String kind : BUNDLE_EXTENSIONS) {
            if (lower.endsWith(kind)) return true;
        }
        return false;
    }

    private static final String[] BUNDLE_EXTENSIONS = {".app", ".menu", ".bundle", ".framework"};

    /**
     * Where the bundle is.
     *
     * A location, not a file. A program given this can ask the file manager about it, hand
     * it to something else, or put it in a message, and at no point has to name a type
     * belonging to the runtime rather than to this system.
     */
    public FMURL location() { return FMURL.of(root); }

    /** The same as a file, for the parts of the system written against the runtime. */
    public File root() { return root; }

    /**
     * What a bundle says about itself, which is its Info.plist.
     *
     * A property list is values kept under names, and that is what this hands over. What
     * the bundle happens to be storing them in is not the caller's concern.
     */
    public FMDictionary info() { return FMDictionary.fromMap(info); }

    public FMString string(FMString key, FMString fallback) {
        return info().string(key, fallback);
    }

    public FMString string(FMString key) { return string(key, FMString.EMPTY); }

    /** Whether a bundle says yes to something, which a property list writes as a truth. */
    public boolean flag(FMString key) { return info().truth(key, false); }

    public FMString identifier() { return string(IDENTIFIER); }

    /** The name to show: the display name if there is one, else the bundle name. */
    public FMString displayName() {
        FMString display = string(DISPLAY_NAME);
        if (!display.isEmpty()) return display;
        FMString name = string(NAME);
        if (!name.isEmpty()) return name;
        String folder = root.getName();
        int dot = folder.lastIndexOf('.');
        return FMString.of(dot > 0 ? folder.substring(0, dot) : folder);
    }

    public FMString principalClass() { return string(PRINCIPAL_CLASS); }

    public FMString version() { return string(SHORT_VERSION); }

    /** What the rest of the system calls these, where it is still written in runtime text. */
    String text(String key) { return string(FMString.of(key)).toString(); }

    /**
     * The bundle's icon. Info.plist names an .icns; a PNG of the same name is accepted
     * too, since this program reads icns but cannot write it.
     */
    public File iconFile() {
        String name = text("CFBundleIconFile");
        if (name.isEmpty()) return null;
        String bare = name.toLowerCase(java.util.Locale.ROOT).endsWith(".icns")
            ? name.substring(0, name.length() - 5) : name;
        File resources = new File(root, "Contents/Resources");
        for (String candidate : new String[]{bare + ".icns", bare + ".png", name}) {
            File icon = new File(resources, candidate);
            if (icon.isFile()) return icon;
        }
        return null;
    }

    /**
     * The Mach-O executable. Windows cannot start it directly, so
     * {@link #windowsLauncher()} answers with the .cmd instead.
     */
    public File executable() {
        String name = text("CFBundleExecutable");
        if (name.isEmpty()) return null;
        for (String dir : new String[]{EXECUTABLE_DIRECTORY, OLD_EXECUTABLE_DIRECTORY}) {
            for (String candidate : new String[]{name, name + ".cmd"}) {
                File f = new File(root, dir + "/" + candidate);
                if (f.isFile()) return f;
            }
        }
        return null;
    }

    /**
     * The bundle the running program came out of.
     *
     * Everything that is not code: interface files, the words in each language, the icon.
     * Worked out rather than told, since the executable is at Contents/Fractal/Name and
     * the bundle is three directories up.
     */
    public static Bundle main() {
        String executable = System.getProperty(
            org.fractalmicro.dyld.Start.EXECUTABLE_PROPERTY, "");
        if (executable.isBlank()) return null;
        File at = new File(executable).getParentFile();
        for (int up = 0; up < 3 && at != null; up++) {
            at = at.getParentFile();
            if (at != null && looksLikeBundle(at)) return read(at);
        }
        return null;
    }

    /**
     * A resource inside the bundle, in the language this account reads.
     *
     * One directory per language, ending in .lproj, searched in the usual order: each
     * language the account asked for, the one the program was written in, then the resource
     * outside the language directories. That last is what makes a program with no
     * translations work.
     */
    public File resource(FMString name, FMString type) {
        File resources = new File(root, "Contents/Resources");
        String file = name + "." + type;
        for (FMString language : org.fractalmicro.os.Languages.preferred()) {
            File in = new File(resources, language + ".lproj/" + file);
            if (in.isFile()) return in;
        }
        File beside = new File(resources, file);
        return beside.isFile() ? beside : null;
    }

    /**
     * The words this bundle uses, in one table, in the language this account reads.
     *
     * A missing table is an empty one rather than a failure: a program with no translations
     * shows what it was written with, which is what lets one be added afterwards without
     * the program knowing.
     */
    public FMDictionary strings(FMString table) {
        File file = resource(table, org.fractalmicro.plist.Strings.EXTENSION);
        if (file == null) return FMDictionary.EMPTY;
        try {
            return org.fractalmicro.plist.Strings.parse(
                FMString.of(java.nio.file.Files.readString(file.toPath())));
        } catch (IOException unreadable) {
            return FMDictionary.EMPTY;
        }
    }

    /**
     * One piece of text, by the key it is filed under.
     *
     * A key with no entry answers with itself. That is what makes an untranslated program
     * readable: a missing translation shows the key, and a key written as the English text
     * shows the English text.
     */
    public FMString localizedString(FMString key, FMString table) {
        return strings(table).string(key, key);
    }

    public FMString localizedString(FMString key) {
        return localizedString(key, org.fractalmicro.plist.Strings.DEFAULT_TABLE);
    }

    /** The Mach-O executable itself, the one that carries the code resources. */
    public File machOExecutable() {
        String name = text("CFBundleExecutable");
        if (name.isEmpty()) return null;
        for (String dir : new String[]{EXECUTABLE_DIRECTORY, OLD_EXECUTABLE_DIRECTORY}) {
            File f = new File(root, dir + "/" + name);
            if (f.isFile()) return f;
        }
        return null;
    }

    /** The launcher Windows runs when the bundle is opened from outside this desktop. */
    public File windowsLauncher() {
        String name = text("CFBundleExecutable");
        if (name.isEmpty()) return null;
        for (String dir : new String[]{EXECUTABLE_DIRECTORY, OLD_EXECUTABLE_DIRECTORY}) {
            File f = new File(root, dir + "/" + name + ".cmd");
            if (f.isFile()) return f;
        }
        return null;
    }



    /* ------------------------------------------------------------ writing */

    /** Builds a bundle on disk. Existing bundles are rewritten, not merged. */
    public static Bundle create(File parent, String name, FMDictionary info,
                                String launchArguments) throws IOException {
        return create(parent, name, info, launchArguments, List.of());
    }

    /**
     * Builds a bundle that carries its own code.
     *
     * The program's classes go inside the executable; shared code stays in the framework
     * it links. Opening it then needs the framework and this file, nothing else.
     *
     * @param ownPackages packages belonging to this program, copied in alongside the entry
     *                    class. Anything outside them is framework code and is left there.
     */
    public static Bundle create(File parent, String name, FMDictionary info,
                                String launchArguments, List<String> ownPackages)
            throws IOException {
        return create(parent, name, info, launchArguments, ownPackages, EXTENSION);
    }

    /**
     * The same, for a bundle that is not an application.
     *
     * A menu extra is a .menu, a loadable set of resources is a .bundle. Inside they are the
     * same shape: Contents, an Info.plist, an executable carrying the code. They differ only
     * in what the directory is called and what opens them.
     */
    public static Bundle create(File parent, String name, FMDictionary info,
                                String launchArguments, List<String> ownPackages,
                                String extension)
            throws IOException {
        // Foundation and AppKit unless the caller says otherwise: the pair no program
        // with a window can do without, and the least a program can link.
        return create(parent, name, info, launchArguments, ownPackages, extension,
                      Frameworks.COCOA);
    }

    /**
     * The same, saying what the program links.
     *
     * The install name of each library, which is what that library calls itself. A program
     * naming Foundation and AppKit gets those and whatever they pass on and nothing else,
     * so reaching for a class from a library it did not name fails.
     */
    public static Bundle create(File parent, String name, FMDictionary info,
                                String launchArguments, List<String> ownPackages,
                                String extension, List<String> linked)
            throws IOException {
        File root = new File(parent, name + extension);
        File contents = new File(root, "Contents");
        File executables = new File(root, EXECUTABLE_DIRECTORY);
        File resources = new File(contents, "Resources");
        for (File dir : new File[]{root, contents, executables, resources}) {
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        }

        Map<String, Object> plist = info.asMap();
        plist.putIfAbsent(INFO_DICTIONARY_VERSION.toString(), "6.0");
        plist.putIfAbsent(PACKAGE_TYPE.toString(), "APPL");
        plist.putIfAbsent(SIGNATURE.toString(), CREATOR);
        plist.putIfAbsent(NAME.toString(), name);
        plist.putIfAbsent(EXECUTABLE.toString(), name);
        Plist.write(new File(contents, "Info.plist").toPath(), plist);

        // PkgInfo: package type then creator code, eight bytes, no newline.
        Files.write(new File(contents, "PkgInfo").toPath(),
                    (PACKAGE_APPLICATION + CREATOR).getBytes(StandardCharsets.US_ASCII));

        byte[] plistBytes = Files.readAllBytes(new File(contents, "Info.plist").toPath());
        String principal = plist.get(PRINCIPAL_CLASS.toString()) instanceof String named
            ? named : "";

        // What the loader calls, which for a program with a process of its own is not the
        // program. On a Mac the entry point is main, and what every application writes in
        // it is one line handing over to NSApplicationMain, which reads NSPrincipalClass
        // out of the bundle and takes it from there. That line is the same in every
        // program, so it lives in the framework they all link and the image names it.
        boolean ownProcess = Boolean.TRUE.equals(plist.get(Bundles.OWN_PROCESS.toString()));
        String entry = ownProcess ? APPLICATION_MAIN : principal;

        byte[] code = codeResource(name, (String) plist.getOrDefault(IDENTIFIER.toString(), ""),
                                   entry, principal,
                                   (String) plist.getOrDefault(SHORT_VERSION.toString(), "1.0"),
                                   plistBytes, ownPackages);
        // The same link every library gets: what this defines, and for everything it uses
        // and does not define, which of the libraries it links will be supplying it.
        org.fractalmicro.macho.Symbols.Set2 symbols = org.fractalmicro.macho.Symbols.of(code);
        java.util.Map<String, String> imports =
            Images.installedLinker().resolve(symbols.referenced(), linked);

        byte[] program = org.fractalmicro.macho.MachO.build(
            "@executable_path/" + name, linked, code, org.fractalmicro.macho.MachO.MH_EXECUTE,
            List.of(), Frameworks.runpaths(),
            List.copyOf(symbols.defined()), imports);
        Path binary = new File(executables, name).toPath();
        Files.write(binary, program);
        binary.toFile().setExecutable(true);

        writeLaunchers(executables, name, launchArguments);
        removeOldExecutableFolder(new File(root, OLD_EXECUTABLE_DIRECTORY));
        return read(root);
    }

    /**
     * The zip written into __FRACTAL,__bytecode: a manifest naming the entry class, a
     * copy of Info.plist so the resources still describe themselves once unpacked, and the
     * program's class files.
     *
     * The loader unpacks this and puts it on the class path ahead of the framework.
     */
    private static byte[] codeResource(String name, String identifier, String entry,
                                       String principal,
                                       String version, byte[] infoPlist,
                                       List<String> ownPackages) throws IOException {
        Manifest manifest = new Manifest();
        Attributes main = manifest.getMainAttributes();
        main.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        main.put(new Attributes.Name(org.fractalmicro.bundle.Dyld.ENTRY_ATTRIBUTE), entry);
        main.put(new Attributes.Name(org.fractalmicro.bundle.Dyld.IDENTIFIER_ATTRIBUTE), identifier);
        main.put(new Attributes.Name("Implementation-Title"), name);
        main.put(new Attributes.Name("Implementation-Version"), version);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(bytes, manifest)) {
            ZipEntry info = new ZipEntry("Resources/Info.plist");
            info.setTime(0);
            jar.putNextEntry(info);
            jar.write(infoPlist);
            jar.closeEntry();
            writeOwnCode(jar, principal, ownPackages);
        }
        return bytes.toByteArray();
    }

    /**
     * Copies the program's classes out of the jar this is running from: the entry class,
     * its nested classes, and anything under {@code ownPackages}. Everything else is
     * framework code and stays there.
     */
    private static void writeOwnCode(JarOutputStream jar, String entry,
                                     List<String> ownPackages) throws IOException {
        String entryPath = entry.replace('.', '/');
        java.util.Set<String> written = new java.util.LinkedHashSet<>();

        // A build that is making a volume compiles each application on its own and says
        // where the class files landed. Taking them from there rather than from the jar
        // this is running keeps the framework free of application code: what ships in
        // Fractal.jar is the framework, and what ships in a bundle is that program.
        String built = System.getProperty("org.fractalmicro.appcode", "");
        if (!built.isBlank()) {
            writeBuiltCode(jar, new File(built), entryPath, ownPackages, written);
            org.fractalmicro.core.Log.info("code resources for " + entry + ": " + written.size()
                                  + " classes");
            return;
        }

        File source = org.fractalmicro.bundle.Install.runningCode();
        if (source == null || !source.isFile()) {
            org.fractalmicro.core.Log.info("no jar to take the code of " + entry
                                  + " from; the bundle carries none");
            return;
        }
        try (java.util.zip.ZipFile archive = new java.util.zip.ZipFile(source)) {
            java.util.Enumeration<? extends ZipEntry> found = archive.entries();
            while (found.hasMoreElements()) {
                ZipEntry each = found.nextElement();
                String path = each.getName();
                if (each.isDirectory() || !path.endsWith(".class")) continue;
                if (!belongs(path, entryPath, ownPackages)) continue;
                if (!written.add(path)) continue;
                ZipEntry copy = new ZipEntry(path);
                copy.setTime(0);
                jar.putNextEntry(copy);
                try (java.io.InputStream in = archive.getInputStream(each)) {
                    in.transferTo(jar);
                }
                jar.closeEntry();
            }
        }
        org.fractalmicro.core.Log.info("code resources for " + entry + ": " + written.size()
                              + " classes");
    }

    /**
     * Copies an application's classes out of a directory of build output.
     *
     * Every application built beside this one is under the same root, so the ones that do
     * not belong to this program are passed over by the same rule the jar is read with.
     */
    private static void writeBuiltCode(JarOutputStream jar, File root, String entryPath,
                                       List<String> ownPackages,
                                       java.util.Set<String> written) throws IOException {
        if (!root.isDirectory()) return;
        java.nio.file.Path base = root.toPath();
        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(base)) {
            for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) walk::iterator) {
                if (!Files.isRegularFile(file) || !file.toString().endsWith(".class")) continue;
                // Everything under <root>/<App>/ is that application's build; the path
                // inside it is the class's own path.
                java.nio.file.Path relative = base.relativize(file);
                if (relative.getNameCount() < 2) continue;
                String path = relative.subpath(1, relative.getNameCount())
                                      .toString().replace('\\', '/');
                if (!belongs(path, entryPath, ownPackages)) continue;
                if (!written.add(path)) continue;
                ZipEntry copy = new ZipEntry(path);
                copy.setTime(0);
                jar.putNextEntry(copy);
                Files.copy(file, jar);
                jar.closeEntry();
            }
        }
    }

    /** Whether a class in the running code is part of this program rather than the system. */
    private static boolean belongs(String path, String entryPath, List<String> ownPackages) {
        if (path.equals(entryPath + ".class") || path.startsWith(entryPath + "$")) return true;
        for (String owned : ownPackages) {
            String folder = owned.replace('.', '/');
            if (!folder.endsWith("/")) folder = folder + "/";
            if (path.startsWith(folder)) return true;
        }
        return false;
    }

    /**
     * Writes the two launchers beside the executable: a .sh, which the bundle format calls
     * for, and a .cmd, which is what Windows actually runs. Both start the loader on the
     * executable, which is the same thing the system does when it opens a program.
     *
     * Neither may contain an absolute path, or unpacking a release finds every program
     * dead. The volume is found rather than named: the launcher walks up until it sees
     * usr/lib/dyld, so a bundle copied onto another volume runs against that one.
     */
    private static void writeLaunchers(File where, String name, String arguments)
            throws IOException {
        String shell = "#!/bin/sh\n"
            + "# Launcher for " + name + ".app\n"
            + "here=$(cd \"$(dirname \"$0\")\" && pwd)\n"
            + "root=\"$here\"\n"
            + "while [ ! -e \"$root/usr/lib/dyld\" ]; do\n"
            + "    up=$(dirname \"$root\")\n"
            + "    [ \"$up\" = \"$root\" ] && { echo \"" + name
            + ".app is not on a FractalJDE volume\" >&2; exit 70; }\n"
            + "    root=\"$up\"\n"
            + "done\n"
            + "java=\"${JAVA_HOME:+$JAVA_HOME/bin/}java\"\n"
            + "exec \"$java\" --enable-preview --enable-native-access=ALL-UNNAMED \\\n"
            + "     \"-D" + Start.ROOT_PROPERTY + "=$root\" -cp \"$root/usr/lib/dyld\" \\\n"
            + "     " + Start.class.getName() + " \"$here/" + name + "\" \"$@\"\n";
        Path script = new File(where, name + ".sh").toPath();
        Files.write(script, shell.getBytes(StandardCharsets.UTF_8));
        script.toFile().setExecutable(true);

        // A bounded walk rather than the shell's open-ended one, because a batch file
        // climbing past the drive root goes on finding the drive root forever.
        StringBuilder up = new StringBuilder();
        StringBuilder climbing = new StringBuilder("%~dp0..");
        for (int levels = 1; levels <= 8; levels++) {
            if (levels > 1) {
                climbing.append("\\..");
                up.append(' ');
            }
            up.append('"').append(climbing).append('"');
        }

        String batch = "@echo off\r\n"
            + "rem Launcher for " + name + ".app\r\n"
            + "setlocal\r\n"
            + "set ROOT=\r\n"
            + "for %%d in (" + up + ") do "
            + "if not defined ROOT if exist \"%%~fd\\usr\\lib\\dyld\" set ROOT=%%~fd\r\n"
            + "if not defined ROOT (\r\n"
            + "  echo " + name + ".app is not on a FractalJDE volume.\r\n"
            + "  exit /b 70\r\n"
            + ")\r\n"
            // JAVA_HOME first, then whatever javaw is on the path.
            + "set JAVAW=javaw\r\n"
            + "if exist \"%JAVA_HOME%\\bin\\javaw.exe\" set JAVAW=%JAVA_HOME%\\bin\\javaw.exe\r\n"
            + "start \"\" \"%JAVAW%\" --enable-preview --enable-native-access=ALL-UNNAMED "
            + "\"-D" + Start.ROOT_PROPERTY + "=%ROOT%\" -cp \"%ROOT%\\usr\\lib\\dyld\" "
            + Start.class.getName() + " \"%~dp0" + name + "\" %*\r\n"
            + "endlocal\r\n";
        Files.write(new File(where, name + ".cmd").toPath(),
                    batch.getBytes(StandardCharsets.UTF_8));
    }

    /** Clears the executables a previous version wrote under the old folder name. */
    private static void removeOldExecutableFolder(File old) {
        if (!old.isDirectory()) return;
        File[] children = old.listFiles();
        if (children != null) for (File f : children) f.delete();
        old.delete();
    }

    @Override public String toString() { return displayName() + " (" + identifier() + ")"; }
}
