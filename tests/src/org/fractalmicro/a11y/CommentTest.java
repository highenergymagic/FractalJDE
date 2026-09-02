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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * How much of this is prose.
 *
 * Length is the problem rather than number. A long comment is skipped, and a comment
 * everybody skips is worse than none because it hides the ones worth reading. A member's
 * doc should fit in a line or two; anything longer belongs at the top of the class, once.
 * A ratchet rather than a limit, so the number goes down as files are worked on.
 */
public final class CommentTest {
    private CommentTest() {}

    public static int count() { return 2; }

    /** Where "long" starts. Above this, a doc comment is an essay somebody will skip. */
    private static final int LONG = 10;

    /** How many long ones there still are. It goes down. */
    private static final int LONG_ONES_ALLOWED = 206;

    public static int run(PrintStream out) {
        out.println();
        out.println("how much of this is prose:");

        List<Path> roots = sourceRoots();
        if (roots.isEmpty()) {
            out.println("      no source to read; this is a built copy");
            return 0;
        }

        int failures = 0;
        int comment = 0;
        int code = 0;
        int longOnes = 0;
        List<String> worst = new ArrayList<>();

        for (Path file : javaIn(roots)) {
            String source = read(file);
            if (source == null) continue;
            String[] lines = source.split("\n");
            boolean inBlock = false;
            boolean inDoc = false;
            int docFrom = 0;
            int docLines = 0;
            // The licence header is not prose anybody wrote about the code and is on every
            // file by a check of its own, so counting it would measure the number of files.
            boolean pastHeader = false;

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (!pastHeader) {
                    if (line.startsWith("package ")) pastHeader = true;
                    continue;
                }
                boolean opensDoc = line.startsWith("/**");
                boolean opensBlock = line.startsWith("/*");
                boolean closes = line.contains("*/");

                if (opensDoc && !closes) {
                    inDoc = true;
                    docFrom = i + 1;
                    docLines = 1;
                }
                if (inDoc && !opensDoc) {
                    docLines++;
                    if (closes) {
                        if (docLines >= LONG) {
                            longOnes++;
                            worst.add(docLines + "  " + file + ":" + docFrom);
                        }
                        inDoc = false;
                    }
                }

                if (opensBlock && !closes) inBlock = true;
                if (inBlock || opensBlock || line.startsWith("//")) {
                    comment++;
                    if (closes) inBlock = false;
                } else if (!line.isEmpty()) {
                    code++;
                }
            }
        }

        Collections.sort(worst, Collections.reverseOrder());
        for (int i = 0; i < Math.min(8, worst.size()); i++) out.println("      " + worst.get(i));

        int share = comment + code == 0 ? 0 : comment * 100 / (comment + code);
        out.println("      " + comment + " lines of comment against " + code + " of code, "
                    + share + " per cent");
        out.println("      " + longOnes + " doc comments of " + LONG + " lines or more, and "
                    + LONG_ONES_ALLOWED + " allowed");

        failures += check(out, "no more essays are written than were before",
            longOnes <= LONG_ONES_ALLOWED);
        // A ceiling well above where it is, so this fails on a change of habit rather than
        // on a file being worked on. What brings the number down is the ratchet above.
        failures += check(out, "and the code is still mostly code",
            share < 40);
        return failures;
    }

    /* ----------------------------------------------------------------- plumbing */

    private static List<Path> sourceRoots() {
        List<Path> found = new ArrayList<>();
        for (String from : new String[]{".", ".."}) {
            for (String area : new String[]{"system", "apps", "tests"}) {
                Path at = Path.of(from, area);
                if (Files.isDirectory(at)) found.add(at);
            }
            if (!found.isEmpty()) return found;
        }
        return found;
    }

    private static List<Path> javaIn(List<Path> roots) {
        List<Path> found = new ArrayList<>();
        for (Path root : roots) {
            try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
                for (Path each : walk.toList()) {
                    if (each.toString().endsWith(".java")) found.add(each);
                }
            } catch (IOException unreadable) {
                // A directory that will not open is one this check cannot speak for.
            }
        }
        return found;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException unreadable) {
            return null;
        }
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
