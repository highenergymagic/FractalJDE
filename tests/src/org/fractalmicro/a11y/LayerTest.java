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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which library may use which.
 *
 * The system is a stack, and a stack only means something if the layers only ever call
 * downwards. LibSystem is the boundary to the host and knows nothing about a window;
 * Foundation knows about files and property lists and not about a program; CoreServices
 * knows about bundles and the loader; AppKit draws; the Finder is an application that sits
 * on all of it.
 *
 * A layer that reaches upward is not a layer. It compiles perfectly well, so nothing but a
 * check like this one notices, and the shape stays true only for as long as somebody keeps
 * looking. This looks.
 *
 * The source is read rather than the classes, because what matters is what the code says,
 * and it is read with the comments and the string literals taken out first: a package name
 * inside a sentence is a mention, and a class name inside a string is a name, and neither
 * is a dependency.
 */
public final class LayerTest {
    private LayerTest() {}

    public static int count() { return 4; }

    /**
     * The one place the stack is not true, and how far it is allowed to go.
     *
     * The screen furniture reaches into the file browser: the Dock, the desktop icons, the
     * menu bar and Spotlight all name the Finder. They are compiled as one stage for that
     * reason, and until they are separated this is a fact about the system rather than a
     * mistake somebody left in.
     *
     * Recording it here is the difference between a known exception and a hole. The count
     * may go down and may not go up, so the boundary that is already crossed cannot be
     * crossed in a new place while the check goes on saying the stack is fine.
     */
    private static final String ALLOWED_FROM = "AppKit";
    private static final String ALLOWED_TO = "Finder";
    private static final int ALLOWED_MOST = 13;

    /** The stack, lowest first. A package may use its own layer and anything below it. */
    private static final String[][] LAYERS = {
        {"LibSystem",    "win", "core"},
        {"Foundation",   "plist", "fs", "alias", "os", "kernel", "xpc", "icns"},
        {"CoreServices", "bundle", "macho", "dyld", "mds", "launchd"},
        {"AppKit",       "appkit", "nib", "windowserver", "theme", "a11y", "app"},
        {"Finder",       "ui"},
    };

    private static final Pattern USE = Pattern.compile("\\borg\\.fractalmicro\\.([a-z0-9]+)\\.([A-Z]\\w+)");

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("the layers:");

        Map<String, Integer> level = new LinkedHashMap<>();
        Map<String, String> framework = new LinkedHashMap<>();
        for (int i = 0; i < LAYERS.length; i++) {
            for (int j = 1; j < LAYERS[i].length; j++) {
                level.put(LAYERS[i][j], i);
                framework.put(LAYERS[i][j], LAYERS[i][0]);
            }
        }

        List<Path> roots = sourceRoots();
        if (roots.isEmpty()) {
            out.println("      no source to read; this is a built copy");
            return 0;
        }

        List<String> upward = new ArrayList<>();
        int read = 0;
        for (Path root : roots) {
        try (var walk = Files.walk(root)) {
            for (Path file : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(file) || !file.toString().endsWith(".java")) continue;
                String pkg = packageOf(root, file);
                if (pkg == null || !level.containsKey(pkg)) continue;
                read++;
                String code = codeOnly(Files.readString(file));
                Matcher m = USE.matcher(code);
                while (m.find()) {
                    String used = m.group(1);
                    if (!level.containsKey(used) || used.equals(pkg)) continue;
                    if (level.get(used) > level.get(pkg)) {
                        upward.add(framework.get(pkg) + " (" + pkg + "/"
                                   + file.getFileName() + ") uses "
                                   + framework.get(used) + "." + m.group(2));
                    }
                }
            }
        } catch (Exception e) {
            out.println("FAIL  the source could be read: " + e);
            return count();
        }
        }

        out.println("      read " + read + " files across " + LAYERS.length + " layers, from "
                    + roots.size() + " framework directories");
        // A check that read nothing has checked nothing, whatever it goes on to say. The
        // path it reads from moved once already, and it reported success for every build
        // in between.
        failures += check(out, "every package belongs to exactly one framework",
            read > 0 && level.size() == new java.util.HashSet<>(level.keySet()).size());

        java.util.Set<String> distinct = new java.util.TreeSet<>(upward);
        List<String> unexpected = distinct.stream()
            .filter(one -> !one.startsWith(ALLOWED_FROM + " ")
                        || !one.contains(" uses " + ALLOWED_TO + "."))
            .toList();
        for (String one : unexpected) out.println("      " + one);
        failures += check(out, "no library uses one above it in the stack, apart from the "
                               + "one that has not been split yet", unexpected.isEmpty());

        // A ratchet rather than a pass. The exception is real and is not being fixed here,
        // but it can only get smaller: a new reference across the same boundary is the
        // same mistake as a new boundary, and without this the exception is a hole that
        // widens quietly.
        out.println("      " + ALLOWED_FROM + " reaches into " + ALLOWED_TO + " "
                    + distinct.size() + " times, and may not reach further than "
                    + ALLOWED_MOST);
        failures += check(out, "and that one is no worse than it was",
            distinct.size() <= ALLOWED_MOST);

        // The lowest layer is the one that matters most: it is the boundary to the
        // host system, and anything it knows about a window is a mistake with a long tail.
        boolean lowestIsClean = upward.stream().noneMatch(u -> u.startsWith("LibSystem"));
        failures += check(out, "the system library knows nothing about a screen", lowestIsClean);

        out.println("      " + (failures == 0 ? "the stack only calls downwards"
                                              : failures + " failed"));
        return failures;
    }

    /**
     * Where the source is, when the checks are running from a checkout.
     *
     * One directory per framework, because that is how the tree is arranged: each is built
     * against only the frameworks below it, and each keeps its packages under its own src.
     *
     * This used to name a single path, from when the whole system was compiled in one go.
     * The split left it naming a directory that no longer exists, and a check that cannot
     * find the source says so and passes. It said so for a while, in a line nobody reads,
     * while counting three checks it was not making.
     */
    private static List<Path> sourceRoots() {
        List<Path> found = new ArrayList<>();
        for (String from : new String[]{"system", "../system"}) {
            Path system = Path.of(from);
            if (!Files.isDirectory(system)) continue;
            try (var frameworks = Files.list(system)) {
                for (Path framework : frameworks.sorted().toList()) {
                    Path src = framework.resolve("src/org/fractalmicro");
                    if (Files.isDirectory(src)) found.add(src);
                }
            } catch (java.io.IOException unreadable) {
                return found;
            }
            if (!found.isEmpty()) return found;
        }
        return found;
    }

    private static String packageOf(Path root, Path file) {
        Path relative = root.relativize(file);
        return relative.getNameCount() < 2 ? null : relative.getName(0).toString();
    }

    /**
     * The source with its comments and string literals taken out.
     *
     * Walked a character at a time rather than matched. A pattern for a Java string
     * literal backtracks its way through a long file and runs the checking thread out of
     * stack, which is a strange way to be told that a file is big.
     */
    private static String codeOnly(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) i++;
                i = Math.min(i + 2, n);
            } else if (c == '"' || c == '\'') {
                char quote = c;
                i++;
                while (i < n && source.charAt(i) != quote) {
                    if (source.charAt(i) == '\\') i++;
                    i++;
                }
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
