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
import org.fractalmicro.bundle.Bundles;
import org.fractalmicro.dyld.Dyld;
import org.fractalmicro.macho.MachO;
import org.fractalmicro.dyld.ImageLoader;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

/**
 * The loader, and whether linking means anything.
 *
 * A program says what libraries it links in its load commands. Until those words decide
 * what it can resolve, they are a comment: every earlier build put the whole system on one
 * class path and handed it to every program, so a program could call anything at all and
 * its load commands described a rule nothing enforced.
 *
 * These are the four things that have to be true for the words to mean something. An image
 * can use its own code. An image cannot use a library it did not link. An image can use one
 * it did. And a class from a library is the same class on both sides of the boundary, which
 * is the one that is easy to get wrong: load a library twice and every value handed across
 * fails to cast, for a reason that looks like nothing at all.
 */
public final class DyldTest {
    private DyldTest() {}

    public static int count() { return 7; }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("the loader:");

        boolean wasStrict = Dyld.isStrict();
        Bundle calculator = Bundles.byIdentifier("org.fractalmicro.calculator");
        if (calculator == null || calculator.machOExecutable() == null) {
            out.println("      no installed program to map; this build runs from one archive");
            return 0;
        }

        try {
            Dyld.setStrict(true);
            Path program = calculator.machOExecutable().toPath();
            MachO image = MachO.read(program);

            // The image says what it needs and where each of it comes from. Nothing else
            // is handed over: this is the file, and the loader takes it from there.
            out.println("      Calculator exports " + image.exports().size()
                        + " and imports " + image.imports().size());
            failures += check(out, "a program carries a symbol table",
                              image.isTwoLevel() && !image.imports().isEmpty());

            ImageLoader mapped = Dyld.load("@checking/Calculator", program,
                org.fractalmicro.bundle.Dyld::locate);

            failures += check(out, "an image can use the code it carries",
                              mapped.loadClass("org.fractalmicro.calculator.Calculator") != null);

            // AppKit is linked, so what AppKit exports is Calculator's to use.
            Class<?> reached = null;
            try {
                reached = mapped.loadClass("org.fractalmicro.appkit.FMApplication");
            } catch (ClassNotFoundException e) {
                out.println("      " + e.getMessage());
            }
            failures += check(out, "an image can use a library it linked", reached != null);
            failures += check(out, "and gets the same class the system is running, not a copy",
                              reached == org.fractalmicro.appkit.FMApplication.class);

            // AppKit links CoreServices; Calculator does not. A library a dependency uses
            // privately was never named here and is not this image's to reach.
            boolean refused = false;
            try {
                mapped.loadClass("org.fractalmicro.bundle.Bundles");
            } catch (ClassNotFoundException expected) {
                refused = true;
            }
            failures += check(out,
                "and cannot reach what a library it linked uses privately", refused);

            failures += check(out, "the runtime is every image's, linked or not",
                              mapped.loadClass("java.util.ArrayList") == java.util.ArrayList.class);

            List<String> graph = mapped.reachable();
            out.println("      the image reaches: " + graph.size() + " images");
            failures += check(out, "an image's graph names itself and what it links",
                              graph.size() > 1 && graph.contains("@checking/Calculator"));
        } catch (Exception e) {
            out.println("FAIL  the loader checks ran: " + e);
            failures++;
        } finally {
            Dyld.setStrict(wasStrict);
        }

        out.println("      " + (failures == 0 ? "linking decides what a program can resolve"
                                              : failures + " failed"));
        return failures;
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
