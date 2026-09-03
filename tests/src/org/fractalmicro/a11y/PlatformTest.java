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
import java.util.Set;
import java.util.TreeMap;
import java.util.Map;

/**
 * Whether an application can be written without naming the runtime.
 *
 * The rule is blunt: an application's source may not import from java or javax. The
 * language is allowed, so a String literal and an int are fine, needing no import.
 *
 * Applications are listed as they are converted. The ones not yet listed are counted and
 * shown, so what is left is a number rather than a feeling.
 */
public final class PlatformTest {
    private PlatformTest() {}

    public static int count() { return 5; }

    /** The applications written against the platform and nothing else. */
    private static final Set<String> PURE = Set.of(
        "Calculator", "SystemProfiler", "ActivityMonitor", "SystemPreferences",
        "Terminal");

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("applications and the platform:");

        Path apps = Path.of("apps");
        if (!Files.isDirectory(apps)) {
            out.println("      no application source to read; this is a built copy");
            return 0;
        }

        Map<String, List<String>> reaching = new TreeMap<>();
        try (var roots = Files.list(apps)) {
            for (Path app : (Iterable<Path>) roots::iterator) {
                if (!Files.isDirectory(app)) continue;
                List<String> found = importsIn(app);
                reaching.put(app.getFileName().toString(), found);
            }
        } catch (Exception e) {
            out.println("FAIL  the application source could be read: " + e);
            return count();
        }

        List<String> impure = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : reaching.entrySet()) {
            String app = e.getKey();
            int n = e.getValue().size();
            if (PURE.contains(app)) {
                if (n > 0) {
                    impure.add(app + " is listed as written against the platform but imports "
                               + n + ": " + String.join(", ", e.getValue()));
                }
            } else {
                out.println("      " + app + ": " + n + " still to go");
            }
        }

        failures += check(out, "every application said to be written against the platform is",
            impure.isEmpty());
        for (String one : impure) out.println("      " + one);

        failures += check(out, "at least one application names nothing outside this system",
            !PURE.isEmpty() && PURE.stream().allMatch(
                a -> reaching.getOrDefault(a, List.of()).isEmpty()));

        // Counting imports is not enough. java.lang needs none, so a program can be free
        // of every import and still be written in the runtime's text, its lists and its
        // maps. Reaching for those inside a program written for a platform is the same
        // mistake as calling open in a Cocoa application: it works, and it means the layer
        // above was never finished. The one place a program cannot avoid it is the
        // signature the runtime insists on to start a process.
        List<String> runtimeTypes = new ArrayList<>();
        for (String app : PURE) {
            int n = runtimeTypesIn(Path.of("apps", app));
            if (n > 0) runtimeTypes.add(app + " names the runtime's own types " + n + " times");
        }
        failures += check(out,
            "and is written in this system's types, not only free of its imports",
            runtimeTypes.isEmpty());
        for (String one : runtimeTypes) out.println("      " + one);

        // The frameworks are the other half of it: a program can only avoid the runtime if
        // the platform actually offers something in its place.
        boolean offered = has("org.fractalmicro.foundation.FMString") && has("org.fractalmicro.foundation.FMURL")
            && has("org.fractalmicro.foundation.FMArray") && has("org.fractalmicro.foundation.FMDecimal")
            && has("org.fractalmicro.foundation.FMFileManager") && has("org.fractalmicro.appkit.FMPasteboard");
        failures += check(out, "and the platform offers something to use instead", offered);

        // One way to the machine's clipboard, so there is one place to change when a
        // checking run wants a board of its own. There were three, and the two that went
        // round FMPasteboard put a word on the clipboard of whoever ran the checks.
        List<String> roundTheBoard = namesTheClipboard();
        for (String one : roundTheBoard) out.println("      " + one);
        failures += check(out, "and only the pasteboard names the machine's clipboard",
            roundTheBoard.isEmpty());

        out.println("      " + (failures == 0
            ? PURE.size() + " of " + reaching.size() + " written against the platform"
            : failures + " failed"));
        return failures;
    }

    /**
     * Anything but FMPasteboard reaching for the machine's clipboard.
     *
     * Swing's own copy and paste do it too, which is why the text system's editing goes
     * through the board rather than calling JTextComponent.copy.
     */
    private static List<String> namesTheClipboard() {
        List<String> found = new ArrayList<>();
        for (String area : new String[]{"system", "apps"}) {
            Path at = Path.of(area);
            if (!Files.isDirectory(at)) continue;
            try (var walk = Files.walk(at)) {
                for (Path file : (Iterable<Path>) walk::iterator) {
                    String name = file.getFileName().toString();
                    if (!name.endsWith(".java") || name.equals("FMPasteboard.java")) continue;
                    String source = Files.readString(file);
                    if (source.contains("getSystemClipboard")
                            || source.contains(".copy();")
                            || source.contains(".paste();")) {
                        found.add(file + " goes round the board");
                    }
                }
            } catch (java.io.IOException unreadable) {
                // A directory that went away while it was read.
            }
        }
        return found;
    }

    /** Every import of the runtime in one application's source. */
    private static List<String> importsIn(Path app) throws java.io.IOException {
        List<String> found = new ArrayList<>();
        try (var walk = Files.walk(app)) {
            for (Path file : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(file) || !file.toString().endsWith(".java")) continue;
                for (String line : Files.readAllLines(file)) {
                    String t = line.trim();
                    if (t.startsWith("import java.") || t.startsWith("import javax.")) {
                        found.add(t.substring("import ".length()).replace(";", ""));
                    }
                }
            }
        }
        return found;
    }

    private static boolean has(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    /**
     * How often a program names one of the runtime's own types.
     *
     * String, List and Map, where this system offers FMString, FMArray and FMDictionary.
     * The entry signature is not counted: a process has to start somewhere, and the
     * runtime decides what that method looks like.
     */
    private static int runtimeTypesIn(Path app) {
        int found = 0;
        Path source = app.resolve("src");
        if (!Files.isDirectory(source)) return 0;
        try (var walk = Files.walk(source)) {
            for (Path file : (Iterable<Path>) walk.filter(
                    f -> f.toString().endsWith(".java"))::iterator) {
                for (String line : Files.readAllLines(file)) {
                    String code = line.strip();
                    if (code.startsWith("*") || code.startsWith("//")) continue;
                    if (code.contains("static void main(String[]")) continue;
                    found += namings(code);
                }
            }
        } catch (Exception unreadable) {
            return 0;
        }
        return found;
    }

    /** The runtime's types named on one line, not counting this system's own. */
    private static int namings(String code) {
        int found = 0;
        for (String type : new String[]{"String", "List<", "Map<"}) {
            int at = 0;
            while ((at = code.indexOf(type, at)) >= 0) {
                boolean partOfAnother = at > 0
                    && (Character.isLetterOrDigit(code.charAt(at - 1))
                        || code.charAt(at - 1) == '.');
                if (!partOfAnother) found++;
                at += type.length();
            }
        }
        return found;
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
