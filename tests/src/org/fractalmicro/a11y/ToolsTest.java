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

import org.fractalmicro.bundle.Images;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.macho.MachO;
import org.fractalmicro.os.FMUserDefaults;
import org.fractalmicro.os.OSPaths;
import org.fractalmicro.plist.Plist;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What is in usr/bin, and whether it is a program.
 *
 * They had a batch file in front of each, because CreateProcess wants a PE and every
 * program here is a Mach-O, so a foreign shell could never run one. This volume has a
 * shell of its own now, and nothing stands in front of anything.
 */
public final class ToolsTest {
    private ToolsTest() {}

    /** A domain no program has, so writing to it disturbs nothing that is running. */
    private static final FMString DOMAIN = FMString.of("org.fractalmicro.checking");

    public static int count() { return 10; }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("the command line tools:");

        List<String> wanted = Images.toolNames();
        List<String> found = new ArrayList<>();
        File[] kids = OSPaths.usrBin().toFile().listFiles();
        if (kids != null) {
            for (File f : kids) if (f.isFile() && !f.getName().contains(".")) {
                found.add(f.getName());
            }
        }
        java.util.Collections.sort(found);
        out.println("      in usr/bin: " + String.join(", ", found));
        failures += check(out, "the tools are on the volume, one program each",
            found.containsAll(wanted) && wanted.size() >= 5);

        /* ---------------------------------------- and each one is a program */

        Path swVers = OSPaths.usrBin().resolve("sw_vers");
        MachO image = null;
        try {
            image = MachO.read(swVers);
        } catch (Exception notAnImage) {
            out.println("      sw_vers could not be read: " + notAnImage);
        }
        failures += check(out, "a tool is a Mach-O executable, not a script",
            image != null && image.fileType() == MachO.MH_EXECUTE);
        failures += check(out, "and links Foundation and the umbrella, and not AppKit",
            image != null
            && image.linkedLibraries().stream().anyMatch(l -> l.contains("Foundation"))
            && image.linkedLibraries().stream().anyMatch(l -> l.contains("CoreServices"))
            && image.linkedLibraries().stream().noneMatch(l -> l.contains("AppKit")));

        // Nothing stands in front of a program. There were two scripts beside each of
        // these that started the loader on it, because the machine's own shell can start
        // nothing but a PE. This volume has a shell of its own now.
        List<String> scripts = new ArrayList<>();
        for (File f : listed(OSPaths.usrBin())) {
            if (f.getName().endsWith(".cmd") || f.getName().endsWith(".sh")) {
                scripts.add(f.getName());
            }
        }
        failures += check(out, "and no script stands in front of any of them",
            scripts.isEmpty());

        // The shell is a program on the volume like the rest, in the folder a Mac keeps
        // it in, and it is started the same way: by handing the loader its image.
        File shell = Images.shell().toFile();
        MachO asImage = null;
        try {
            asImage = MachO.read(shell.toPath());
        } catch (Exception notAnImage) {
            out.println("      the shell could not be read: " + notAnImage);
        }
        failures += check(out, "the shell is /bin/sh, and is a program like any other",
            shell.isFile() && shell.getParentFile().getName().equals("bin")
            && asImage != null && asImage.fileType() == MachO.MH_EXECUTE);

        /* -------------------------------------------- what the volume says it is */

        Map<String, Object> version = Map.of();
        try {
            version = Plist.readDictionary(
                OSPaths.coreServices().resolve("SystemVersion.plist"));
        } catch (Exception notThere) {
            out.println("      no SystemVersion.plist: " + notThere);
        }
        out.println("      the volume says it is " + version.get("ProductName") + " "
                    + version.get("ProductVersion"));
        failures += check(out, "the volume says what it is, where sw_vers looks",
            "FractalJDE".equals(version.get("ProductName"))
            && String.valueOf(version.get("ProductVersion"))
                     .equals(org.fractalmicro.os.Version.number()));

        /* ------------------------------------------- and what defaults works on */

        FMUserDefaults checking = FMUserDefaults.of(DOMAIN);
        checking.set(FMString.of("Written"), 48L);
        failures += check(out, "a preference written by name is a file on the volume",
            checking.file().toFile().isFile()
            && checking.integer(FMString.of("Written"), 0) == 48);

        List<String> domains = new ArrayList<>();
        for (FMString one : FMUserDefaults.domains()) domains.add(one.toString());
        failures += check(out, "and the domains are what there are files for",
            domains.contains(DOMAIN.toString()));

        checking.remove(FMString.of("Written"));
        failures += check(out, "and a key that is deleted is gone from the file",
            checking.get(FMString.of("Written")) == null
            && !readBack(checking.file()).contains("Written"));

        checking.file().toFile().delete();
        failures += check(out, "and the domain goes with its file",
            !new ArrayList<>(namesOf(FMUserDefaults.domains())).contains(DOMAIN.toString()));

        out.println("      " + (failures == 0
            ? "usr/bin holds programs, and they work on this volume's own files"
            : failures + " failed"));
        return failures;
    }

    private static List<String> namesOf(Iterable<FMString> them) {
        List<String> said = new ArrayList<>();
        for (FMString one : them) said.add(one.toString());
        return said;
    }

    /** What is in a folder, or nothing when there is no folder. */
    private static File[] listed(Path folder) {
        File[] kids = folder.toFile().listFiles();
        return kids == null ? new File[0] : kids;
    }

    private static String readBack(Path file) {
        try {
            return Files.isReadable(file) ? Files.readString(file) : "";
        } catch (java.io.IOException e) {
            return "";
        }
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
