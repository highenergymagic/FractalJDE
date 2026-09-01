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

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The licence, checked rather than assumed.
 *
 * The licence this program is under says the header goes in every file, so a file without
 * one is a real problem and not a tidiness question. This walks the source and says which
 * files are missing it. Because the header names a licence file that has to travel
 * with the code, checks that the licence file is there and is the licence it claims.
 *
 * It looks for the source beside the program when there is source to look at. A copy
 * running from a jar has no source tree, and says so rather than passing on an empty walk.
 */
public final class LicenseTest {
    private LicenseTest() {}

    public static int count() { return 4; }

    private static final String START = "CDDL HEADER START";
    private static final String END = "CDDL HEADER END";
    private static final String OWNER = "Copyright (C) 2026 by Fractal Microsystems, Inc.";

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("the licence:");

        // Every tree the source lives in. There is no single one: each image is built on
        // its own so that a library cannot use something above it, and the applications
        // and the checks are separate again. Walking one path was how this check came to
        // pass without looking at anything.
        List<Path> roots = new ArrayList<>();
        for (String base : new String[]{"system", "apps", "tests"}) {
            Path at = Path.of(base);
            if (Files.isDirectory(at)) roots.add(at);
        }
        if (roots.isEmpty()) {
            out.println("      no source tree here, so the headers are not walked");
            out.println("      (run this from the folder the program is built in)");
            return 0;
        }

        List<String> missing = new ArrayList<>();
        List<String> wrongOwner = new ArrayList<>();
        int walked = 0;
        for (Path source : roots) {
            try (var files = Files.walk(source)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    walked++;
                    String head = head(file);
                    if (!head.contains(START) || !head.contains(END)) {
                        missing.add(file.toString());
                    } else if (!head.contains(OWNER)) {
                        wrongOwner.add(file.toString());
                    }
                }
            } catch (IOException e) {
                out.println("FAIL  the source could not be walked: " + e);
                return count();
            }
        }

        // A walk that found nothing is a walk that checked nothing, and saying so is the
        // difference between this check passing and this check working.
        if (walked == 0) {
            out.println("FAIL  the source trees are there but hold no files to check");
            return count();
        }

        out.println("      " + walked + " source files");
        failures += check(out, "every source file carries the licence header",
            missing.isEmpty());
        for (String file : missing) out.println("      missing: " + file);
        failures += check(out, "every header names the same copyright holder",
            wrongOwner.isEmpty());
        for (String file : wrongOwner) out.println("      wrong owner: " + file);

        Path licence = Path.of("LICENSE");
        boolean present = Files.isReadable(licence);
        failures += check(out, "the licence file is beside the source", present);

        String text = present ? head(licence) : "";
        failures += check(out, "the licence file is the licence the headers name",
            text.contains("COMMON DEVELOPMENT AND DISTRIBUTION LICENSE")
            && text.contains("Version 1.0"));

        out.println("      " + (failures == 0 ? "the terms are on every file"
                                              : failures + " failed"));
        return failures;
    }

    private static String head(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            int length = Math.min(bytes.length, 2048);
            return new String(bytes, 0, length, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
