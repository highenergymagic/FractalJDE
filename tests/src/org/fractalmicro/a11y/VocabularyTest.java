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

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What an application is allowed to know about the system it runs on.
 *
 * A platform is what its programs can see. Everything published for them to use carries
 * the FM prefix, the way everything in Cocoa carries NS: FMString, FMArray, FMURL,
 * FMApplication, FMSavePanel, FMWorkspace. Anything without it is the plumbing underneath,
 * and a program naming one of those has reached past the platform into the inside of it.
 *
 * That is not a style rule. It is the difference between an interface and an
 * implementation: every class an application names is a class that cannot then change
 * without breaking it, so the set of them is the promise this system is making. When the
 * applications shipped with it name seventeen classes and only five of them are published,
 * the promise is twelve classes wider than anybody meant.
 *
 * Checked against the applications, because they are the only honest witness. Anything can
 * claim to have an interface; a program either used it or went round it.
 */
public final class VocabularyTest {
    private VocabularyTest() {}

    public static int count() { return 4; }

    /**
     * Types an application may name without them being part of the published vocabulary.
     *
     * A ratchet, in the way of the others here: the list may get shorter and may not get
     * longer. Each one is something Cocoa has a name for and this system has not published
     * yet, and the note beside it is which name that is.
     */
    private static final String[] STILL_ALLOWED = {
        // NSUserDefaults with a suite name. These are preference schemas rather than
        // classes: typed accessors for one domain, which Cocoa has no equivalent of and
        // System Preferences is the one program with a reason to want.
        "org.fractalmicro.os.DockSettings",
        "org.fractalmicro.os.FinderSettings",
        // NSSearchPathForDirectoriesInDomains. System Profiler lists where the system
        // keeps things, which is the one program whose subject is the layout itself.
        "org.fractalmicro.os.OSPaths",
    };

    /** Where the applications are, when the checks are running from a checkout. */
    private static final Path APPS = Path.of("apps");

    private static final Pattern NAMED =
        Pattern.compile("\\borg\\.fractalmicro\\.([a-z0-9]+)\\.([A-Z]\\w+)");

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("what a program can see:");

        if (!Files.isDirectory(APPS)) {
            out.println("      no applications here, so there is nobody to ask");
            return 0;
        }

        List<String> sources = new ArrayList<>();
        java.util.Set<String> named = new TreeSet<>();
        try (var walk = Files.walk(APPS)) {
            for (Path file : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(file) || !file.toString().endsWith(".java")) continue;
                sources.add(file.toString());
                Matcher found = NAMED.matcher(codeOnly(Files.readString(file)));
                while (found.find()) {
                    named.add("org.fractalmicro." + found.group(1) + "." + found.group(2));
                }
            }
        } catch (Exception e) {
            out.println("FAIL  the applications could be read: " + e);
            return count();
        }

        failures += check(out, "there are applications to ask", !sources.isEmpty());
        out.println("      " + sources.size() + " files across the applications name "
                    + named.size() + " of this system's classes");

        List<String> published = new ArrayList<>();
        List<String> plumbing = new ArrayList<>();
        for (String one : named) {
            String simple = one.substring(one.lastIndexOf('.') + 1);
            if (simple.startsWith("FM")) published.add(one); else plumbing.add(one);
        }
        for (String one : published) out.println("      published: " + one);

        List<String> unexpected = new ArrayList<>(plumbing);
        unexpected.removeAll(List.of(STILL_ALLOWED));
        for (String one : unexpected) out.println("      reaches inside: " + one);
        failures += check(out, "an application names nothing but the published vocabulary, "
                               + "apart from what is written down", unexpected.isEmpty());

        // Shorter, never longer. A class that comes off the list cannot go back on it
        // without somebody saying so here, which is the whole use of writing it down.
        out.println("      " + plumbing.size() + " reaches inside, and no more than "
                    + STILL_ALLOWED.length + " are allowed");
        failures += check(out, "and no more of them than there were",
            plumbing.size() <= STILL_ALLOWED.length);

        // The published half has to be worth having. A vocabulary of two classes would
        // pass everything above and mean the applications do nothing.
        failures += check(out, "and the published half is most of what they name",
            published.size() >= plumbing.size() * 2);

        out.println("      " + (failures == 0
            ? "the applications use the platform rather than its inside"
            : failures + " failed"));
        return failures;
    }

    /**
     * The code with the comments and the strings taken out.
     *
     * A class named in a sentence is a mention and a class named in a string is a name,
     * and neither is a program reaching for anything.
     */
    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ")
                     .replaceAll("(?m)//.*$", " ")
                     .replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
