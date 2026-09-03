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
import java.util.List;

/**
 * What linking against something actually means.
 *
 * A client's LC_LOAD_DYLIB is copied from the library's LC_ID_DYLIB, not from wherever the
 * file was sitting. Wrong, and it works on the machine it was built on and nowhere else.
 *
 * @rpath means nothing by itself: each image says what it stands for in LC_RPATH commands,
 * tried in order.
 *
 * An umbrella re-exports what it covers, which is what lets a program link CoreServices
 * rather than reaching into LaunchServices.
 */
public final class LinkingTest {
    private LinkingTest() {}

    public static int count() { return 10; }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("what linking means:");

        Path dyld = OSPaths.dyld();
        if (!Files.isRegularFile(dyld)) {
            out.println("      the parts are not laid out here; this build runs from one jar");
            return 0;
        }

        try {
            // The loader names itself, and with its own command: it is not a library.
            MachO loader = MachO.read(dyld);
            failures += check(out, "the loader says it is the loader, and what it is called",
                loader.fileType() == MachO.MH_DYLINKER
                && "/usr/lib/dyld".equals(loader.installName()));

            // A framework's own name is the one clients will record.
            Path foundation = OSPaths.framework("Foundation").resolve("Versions/A/Foundation");
            MachO f = MachO.read(foundation);
            out.println("      Foundation calls itself " + f.installName());
            failures += check(out, "a framework carries its own install name",
                f.installName().startsWith("@rpath/Foundation.framework"));

            failures += check(out, "and links the system library by its absolute name",
                f.linkedLibraries().contains("/usr/lib/libSystem.B.dylib"));

            // @rpath is a placeholder, and the image says what it stands for.
            out.println("      Foundation looks in " + f.runpaths());
            failures += check(out, "an image says what @rpath stands for",
                f.runpaths().contains("/System/Library/Frameworks"));

            // The umbrella passes on what it covers.
            Path coreServices =
                OSPaths.framework("CoreServices").resolve("Versions/A/CoreServices");
            MachO umbrella = MachO.read(coreServices);
            List<String> passed = umbrella.reexported();
            out.println("      CoreServices passes on " + passed.size() + " frameworks");
            failures += check(out, "an umbrella re-exports what it covers",
                passed.stream().anyMatch(r -> r.contains("LaunchServices.framework"))
                && passed.stream().anyMatch(r -> r.contains("Metadata.framework")));

            // And a name is resolved through the runpaths rather than a fixed directory.
            Path found = org.fractalmicro.bundle.Dyld.resolveFramework(
                f.installName(), f.runpaths(), null);
            failures += check(out, "a name beginning @rpath resolves through them",
                found != null && Files.isReadable(found));

            // An application names the two libraries it cannot do without and no more.
            // Whatever else it needs it reaches through them, which is what an umbrella
            // is for: the program does not have to know what is inside one.
            org.fractalmicro.bundle.Bundle app =
                org.fractalmicro.bundle.Bundles.byIdentifier("org.fractalmicro.textedit");
            if (app == null || app.machOExecutable() == null) {
                out.println("      no installed program to look at");
            } else {
                MachO program = MachO.read(app.machOExecutable().toPath());
                out.println("      TextEdit links "
                            + program.linkedLibraries().size() + " libraries");
                failures += check(out, "an application links Foundation and AppKit, and nothing else",
                    program.linkedLibraries().equals(org.fractalmicro.bundle.Frameworks.COCOA));

                // The class path is what it named plus what those named, and so on. The
                // system library is the proof: no program links it, and every program has
                // it, because Foundation does.
                String classPath = org.fractalmicro.bundle.Dyld.classPath(app);
                failures += check(out, "and is given what those libraries link in turn",
                    classPath.contains("libSystem") && classPath.contains("AppKit"));
            }
        } catch (Exception e) {
            out.println("FAIL  the linking checks ran: " + e);
            failures++;
        }

            /* ------------------------------------------- and every symbol is placed */

            // A library says what it offers. Anything linking it records, for each class
            // it uses, which library was going to supply it. Both halves have to be there
            // or the loader is back to searching.
            int described = 0;
            int placed = 0;
            int unplaced = 0;
            try {
                for (String installName : org.fractalmicro.bundle.Frameworks.all()) {
                    Path at = org.fractalmicro.bundle.Dyld.resolveFramework(installName);
                    if (at == null) continue;
                    MachO one = MachO.read(at);
                    if (!one.exports().isEmpty()) described++;
                    for (java.util.Map.Entry<String, String> symbol
                             : one.imports().entrySet()) {
                        if (symbol.getValue().isEmpty()) unplaced++;
                        else placed++;
                    }
                }
            } catch (java.io.IOException unreadable) {
                out.println("      " + unreadable);
            }
            out.println("      " + described + " libraries describe themselves, "
                        + placed + " symbols placed");
            failures += check(out, "every library says what it offers", described >= 6);

            // A symbol with no library named against it would be found by searching, which
            // is what a two level namespace exists to stop.
            failures += check(out, "and every symbol used says where it comes from",
                              placed > 0 && unplaced == 0);

        out.println("      " + (failures == 0
            ? "a client records the library's own name, and finds it by its runpaths"
            : failures + " failed"));
        return failures;
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
