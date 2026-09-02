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
package org.fractalmicro.a11y;

import org.fractalmicro.bundle.Bundle;
import org.fractalmicro.bundle.Frameworks;
import org.fractalmicro.bundle.Bundles;
import org.fractalmicro.bundle.Dyld;
import org.fractalmicro.macho.MachO;
import org.fractalmicro.os.OSPaths;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * Checks the executables: that what this system writes is really Mach-O, that the code
 * resources come back out of it unchanged, and that nothing points at the folder the
 * program was built in.
 *
 * The last one is the point of the exercise. A bundle whose launcher names a build
 * directory works on one machine and nowhere else.
 */
public final class MachOTest {
    private MachOTest() {}

    public static int count() { return 19; }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("programs:");

        byte[] payload = "code resources, whatever they are".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] built = MachO.build("Test", Frameworks.COCOA, payload);

        MachO parsed = null;
        try {
            Path temp = Files.createTempFile("fractal-program", "");
            Files.write(temp, built);
            parsed = MachO.read(temp);
            temp.toFile().deleteOnExit();
        } catch (Exception e) {
            out.println("FAIL  a written program can be read back: " + e);
            return count();
        }

        failures += check(out, "the magic is a 64 bit Mach-O",
            parsed.cpuType() == MachO.CPU_TYPE_X86_64 && parsed.fileType() == MachO.MH_EXECUTE);
        // The segments, then the loader, the uuid, the entry point, the symbol table and
        // how it is divided, and then one load command for each library. Counting it
        // rather than writing the total down means the check keeps meaning something when
        // a program links a different set.
        int fixedCommands = parsed.segments().size() + 5;
        failures += check(out, "the load commands are all there",
            parsed.commandCount() == fixedCommands + Frameworks.COCOA.size()
            && parsed.segments().size() == 4);
        failures += check(out, "the segments are the ones written",
            parsed.segments().contains("__PAGEZERO") && parsed.segments().contains("__TEXT")
            && parsed.segments().contains("__LINKEDIT")
            && parsed.segments().contains(MachO.CODE_SEGMENT));
        failures += check(out, "the dynamic loader is named",
            MachO.DYLINKER.equals(parsed.dynamicLoader()));
        failures += check(out, "the framework is linked",
            parsed.linkedLibraries().containsAll(Frameworks.COCOA));

        MachO.Section code = parsed.section(MachO.CODE_SEGMENT, MachO.CODE_SECTION);
        failures += check(out, "the code resources are where the section says",
            code != null && code.size == payload.length
            && java.util.Arrays.equals(readAt(built, code.offset, (int) code.size), payload));

        long entry = parsed.entryOffset();
        failures += check(out, "the entry point is inside the text segment",
            entry > 0 && entry + MachO.ENTRY_CODE.length <= built.length
            && java.util.Arrays.equals(
                readAt(built, (int) entry, MachO.ENTRY_CODE.length), MachO.ENTRY_CODE));

        failures += check(out, "the identifier repeats for the same program",
            java.util.Arrays.equals(built, MachO.build("Test",
                Frameworks.COCOA, payload)));

        /* --------------------------------------------------- the real bundles */

        // Nothing is installed as an archive any more. A library's code is inside the
        // library, and a copy beside it would be a way to load one without linking it.
        failures += check(out, "no library ships its code beside itself",
            noLooseArchives());

        Bundle textEdit = Bundles.byIdentifier("org.fractalmicro.textedit");
        if (textEdit == null) {
            out.println("FAIL  TextEdit is installed");
            return failures + 3;
        }

        File binary = textEdit.machOExecutable();
        boolean readsBack = false;
        boolean alsoZip = false;
        try {
            MachO program = MachO.read(binary.toPath());
            // An application links Foundation and AppKit, which is the pair Apple says
            // no Cocoa program can do without, and nothing else. What it needs beyond
            // those it reaches through them.
            readsBack = program.section(MachO.CODE_SEGMENT, MachO.CODE_SECTION) != null
                     && program.linkedLibraries().equals(Frameworks.COCOA);
            // The code resources are a zip appended whole, so the executable answers to
            // a zip reader as well: the same bytes, read from the other end.
            try (ZipFile zip = new ZipFile(binary)) {
                alsoZip = zip.getEntry("META-INF/MANIFEST.MF") != null;
            }
        } catch (Exception e) {
            out.println("      " + e);
        }
        failures += check(out, "an installed program is Mach-O and links Foundation and AppKit",
                          readsBack);
        failures += check(out, "an installed program is also a readable archive", alsoZip);

        // Nothing is unpacked. The entry point is read out of the image where it lies,
        // and there is no working copy anywhere for it to be read out of instead.
        //
        // What it names is not the program. On a Mac the entry point is main and every
        // application writes one line in it, handing over to NSApplicationMain, which reads
        // NSPrincipalClass out of the bundle. That line is the same in every program, so it
        // lives in the framework they all link and the image names it there. The program
        // itself is named by the bundle, which is the only place it needs saying.
        String entryClass = "";
        boolean nothingUnpacked = !Files.isDirectory(Dyld.workingRoot())
            || isEmptyDirectory(Dyld.workingRoot());
        try {
            entryClass = org.fractalmicro.dyld.Start.entryClass(MachO.read(binary.toPath()));
        } catch (Exception e) {
            out.println("      " + e);
        }
        out.println("      the image starts at " + entryClass);
        failures += check(out, "the entry class is read out of the image, with nothing unpacked",
            nothingUnpacked
            && "org.fractalmicro.appkit.FMApplicationMain".equals(entryClass));

        failures += check(out, "and the program itself is named by the bundle, once",
            textEdit != null
            && "org.fractalmicro.textedit.TextEdit"
                   .equals(textEdit.principalClass().toString())
            && textEdit.flag(org.fractalmicro.bundle.Bundles.OWN_PROCESS));

        /* ------------------------------------------- the program is in the program */

        // A bundle has to carry its own code. An executable holding only a manifest leaves
        // every program running on the framework's copy of its classes, which works until
        // the framework is a version behind and a program cannot find its own entry.
        java.util.List<String> carried = classesIn(textEdit);
        out.println("      TextEdit carries " + carried.size() + " classes");
        failures += check(out, "a program carries its own classes",
            carried.contains("org/fractalmicro/textedit/TextEdit.class")
            && carried.contains("org/fractalmicro/textedit/Settings.class"));
        failures += check(out, "and not the whole system along with them",
            !carried.isEmpty() && carried.size() < 200
            && carried.stream().noneMatch(c -> c.startsWith("org/fractalmicro/ui/")));

        // A program's own process is started with the loader, not with its libraries
        // laid out on a class path. A class path is a search order, and everything on one
        // can reach everything else on it whether it linked it or not.
        String bootstrap = Dyld.bootstrapClassPath();
        failures += check(out, "a program's process starts with the loader and nothing else",
            bootstrap.endsWith("dyld") && !bootstrap.contains(File.pathSeparator));

        /* -------------------------------------------- and nowhere else is named */

        String launcher = readLauncher(textEdit);
        String source = new File(System.getProperty("user.dir")).getAbsolutePath();
        failures += check(out, "no launcher points at the folder this was built in",
            !launcher.isEmpty() && !launcher.contains(source));

        // A launcher that spells out this account's home folder, or where this machine
        // keeps its runtime, is one that stops working the moment the program is copied
        // anywhere. Both are read from the environment instead.
        String home = OSPaths.USER_HOME.toString();
        String runtime = System.getProperty("java.home", "");
        failures += check(out, "no launcher names the machine it was written on",
            !launcher.contains(home)
            && (runtime.isEmpty() || !launcher.contains(runtime))
            && !readShellLauncher(textEdit).contains(home));
        // They used to name the home directory and work out the rest from there, which is
        // right until a release is staged somewhere that is not a home directory. Now they
        // name no location at all: each walks up from itself until it finds usr/lib/dyld,
        // so a bundle runs against the volume it is actually on. The runtime is still the
        // environment's to say, since it is not on the volume.
        failures += check(out, "they find the volume they are on instead",
            launcher.contains("usr\\lib\\dyld") && launcher.contains("%~dp0")
            && launcher.contains("%JAVA_HOME%")
            && readShellLauncher(textEdit).contains("usr/lib/dyld"));

        out.println("      " + (failures == 0 ? "the programs hold together" : failures + " failed"));
        return failures;
    }

    /** The class files a program's executable carries, which is the program itself. */
    private static java.util.List<String> classesIn(Bundle bundle) {
        java.util.List<String> found = new java.util.ArrayList<>();
        File binary = bundle.machOExecutable();
        if (binary == null) return found;
        try (ZipFile zip = new ZipFile(binary)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> each = zip.entries();
            while (each.hasMoreElements()) {
                String name = each.nextElement().getName();
                if (name.endsWith(".class")) found.add(name);
            }
        } catch (Exception e) {
            // A program that cannot be read carries nothing, which the checks will say.
        }
        return found;
    }

    private static String readShellLauncher(Bundle bundle) {
        File sh = new File(bundle.machOExecutable().getParentFile(),
                           bundle.machOExecutable().getName() + ".sh");
        try {
            return sh.isFile() ? Files.readString(sh.toPath()) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String readLauncher(Bundle bundle) {
        File cmd = bundle.windowsLauncher();
        if (cmd == null) return "";
        try {
            return Files.readString(cmd.toPath());
        } catch (Exception e) {
            return "";
        }
    }

    private static byte[] readAt(byte[] bytes, int offset, int length) {
        byte[] out = new byte[length];
        System.arraycopy(bytes, offset, out, 0, length);
        return out;
    }

    private static boolean isEmptyDirectory(Path directory) {
        try (java.util.stream.Stream<Path> inside = Files.list(directory)) {
            return inside.findAny().isEmpty();
        } catch (java.io.IOException unreadable) {
            return true;
        }
    }

    /** Whether any installed library has a copy of its code sitting next to it. */
    private static boolean noLooseArchives() {
        for (String installName : org.fractalmicro.bundle.Frameworks.all()) {
            java.nio.file.Path binary = org.fractalmicro.bundle.Dyld.resolveFramework(installName);
            if (binary == null) continue;
            if (Files.isReadable(binary.resolveSibling(binary.getFileName() + ".jar"))) {
                return false;
            }
        }
        return true;
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
