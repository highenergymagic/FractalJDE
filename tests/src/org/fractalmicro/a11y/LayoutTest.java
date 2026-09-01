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

import org.fractalmicro.macho.MachO;
import org.fractalmicro.os.OSPaths;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where the system's own parts are, and what kind of thing each one is.
 *
 * Mac OS X does not keep the system in one bag, and the places it keeps things say what
 * they are. The loader is /usr/lib/dyld and its Mach-O header says MH_DYLINKER, which is
 * its own file type and not a library, not a program, not a bundle. The first process is
 * /sbin/launchd. The system library is /usr/lib/libSystem.B.dylib, a dylib rather than a
 * framework. CoreServices is an umbrella whose Versions/A holds a Frameworks directory,
 * and the metadata server is a helper inside the framework whose work it does.
 *
 * None of that is decoration. A system where the loader is a framework and the daemons sit
 * wherever was convenient looks identical from the outside and is a different system, and
 * nothing but a check like this one notices the difference.
 *
 * Skipped where the parts have not been laid out, which is a build running from one jar
 * with no per-image code to take apart.
 */
public final class LayoutTest {
    private LayoutTest() {}

    public static int count() { return 7; }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("where the system's parts are:");

        if (!Files.isRegularFile(OSPaths.dyld())) {
            out.println("      the parts are not laid out here; this build runs from one jar");
            return 0;
        }

        failures += is(out, "the loader is /usr/lib/dyld, and is a dylinker",
                       OSPaths.dyld(), MachO.MH_DYLINKER);
        failures += is(out, "the system library is /usr/lib/libSystem.B.dylib, a dylib",
                       OSPaths.libSystem(), MachO.MH_DYLIB);
        failures += is(out, "the first process is /sbin/launchd, a program",
                       OSPaths.launchd(), MachO.MH_EXECUTE);
        failures += is(out, "Foundation is a framework, and a dylib inside it",
                       OSPaths.framework("Foundation").resolve("Versions/A/Foundation"),
                       MachO.MH_DYLIB);

        // The umbrella: a Frameworks directory inside Versions/A, each entry a whole
        // framework again with its own Versions and its own Current.
        Path launchServices = OSPaths.subframework("CoreServices", "LaunchServices");
        Path metadata = OSPaths.subframework("CoreServices", "Metadata");
        out.println("      CoreServices holds: "
                    + (Files.isDirectory(launchServices) ? "LaunchServices " : "")
                    + (Files.isDirectory(metadata) ? "Metadata" : ""));
        failures += check(out, "CoreServices is an umbrella over whole frameworks",
            Files.isRegularFile(launchServices.resolve("Versions/A/LaunchServices"))
            && Files.isRegularFile(metadata.resolve("Versions/A/Metadata"))
            && Files.exists(launchServices.resolve("Versions/Current")));

        failures += check(out, "the metadata server is a helper inside its own framework",
            Files.isRegularFile(OSPaths.frameworkSupport(metadata).resolve("mds")));

        // A framework is not a directory with a jar in it: it has the versions and the
        // links that let one version be replaced without anything else being touched.
        Path foundation = OSPaths.framework("Foundation");
        failures += check(out, "a framework has its versions and its links",
            Files.exists(foundation.resolve("Versions/Current"))
            && Files.exists(foundation.resolve("Foundation"))
            && Files.isRegularFile(foundation.resolve("Versions/A/Resources/Info.plist")));

        out.println("      " + (failures == 0
            ? "the parts are where Mac OS X keeps them"
            : failures + " failed"));
        return failures;
    }

    /** Whether a file is there and its header says what it should. */
    private static int is(PrintStream out, String what, Path file, int fileType) {
        boolean ok = false;
        try {
            ok = Files.isRegularFile(file) && MachO.read(file).fileType() == fileType;
        } catch (Exception e) {
            out.println("      " + e);
        }
        return check(out, what, ok);
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
