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

import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMLocalized;
import org.fractalmicro.plist.Strings;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whether the words are in the files that hold words.
 *
 * A program names a key and a strings file says what the key means. Both halves can go
 * wrong quietly. A key with no entry falls back to showing the key itself, which reads as
 * nearly-English in the language it was written in and as nothing at all in any other, and
 * nobody who only speaks the first one will ever see it. An entry with no key is a
 * translator's work that reaches no screen.
 *
 * So this reads the source for every key asked for, reads every English table on disk, and
 * requires the two to agree. It reads files rather than running anything, because what is
 * being checked is what a translator would be handed.
 *
 * The last check is a count rather than a rule. Text written straight into the source is
 * being taken out a piece at a time, and until that is finished the number of places left
 * is recorded here: it may fall and may not rise. Without that the work stops being
 * finished and starts being maintained.
 */
public final class LocalizationTest {
    private LocalizationTest() {}

    public static int count() { return 6; }

    /**
     * How many pieces of text are still written into the source.
     *
     * Every one is a sentence a translator cannot reach. The number goes down as they are
     * moved into the tables and may not go up, which is the only thing that makes a long
     * job finish rather than drift.
     */
    private static final int LITERALS_ALLOWED = 110;

    /**
     * A key being made into this system's own text type.
     *
     * Matched on FMString.of rather than on the string alone. A dotted word is also how a
     * bundle names itself, how the runtime names a property and how a library file is
     * called, and none of those is something a translator should ever be handed.
     */
    private static final Pattern KEY = Pattern.compile(
        "FMString\\.of\\(\\s*"
        + "\"([a-z][a-zA-Z0-9]*(?:\\.[a-zA-Z][a-zA-Z0-9]*)+)\"");

    /** Text a person would read: has a space in it and a letter, and is not a path. */
    private static final Pattern READABLE = Pattern.compile(
        "\"([A-Z][^\"\\\\]* [^\"\\\\]{2,})\"");

    public static int run(PrintStream out) {
        out.println();
        out.println("the words:");

        List<Path> roots = sourceRoots();
        if (roots.isEmpty()) {
            out.println("      no source to read; this is a built copy");
            return 0;
        }

        int failures = 0;

        /* ------------------------------------------------- what the tables offer */

        Set<String> offered = new LinkedHashSet<>();
        List<Path> tables = englishTables();
        for (Path table : tables) {
            FMDictionary words = read(table);
            for (FMString key : words.keys()) offered.add(key.toString());
        }
        out.println("      " + offered.size() + " entries in " + tables.size()
                    + " English tables");
        failures += check(out, "there are words to look up at all",
            !tables.isEmpty() && offered.size() > 100);

        /* --------------------------------------------------- what the source asks */

        Set<String> asked = new LinkedHashSet<>();
        int read = 0;
        for (Path file : javaIn(roots)) {
            String source = source(file);
            if (source == null) continue;
            read++;
            for (String line : source.split("\n")) {
                String t = line.trim();
                if (isComment(t)) continue;
                // Only where a key is being made into an FMString, which is the one way
                // this system names one. A dotted word anywhere else is a class or a path.
                Matcher m = KEY.matcher(line);
                while (m.find()) {
                    if (!isReverseDomain(m.group(1))) asked.add(m.group(1));
                }
            }
        }
        out.println("      " + asked.size() + " keys asked for across " + read + " files");

        List<String> missing = new ArrayList<>();
        for (String key : asked) {
            if (!offered.contains(key)) missing.add(key);
        }
        for (String one : missing) out.println("      no English for " + one);
        failures += check(out, "every key the source asks for has words in English",
            missing.isEmpty());

        /* -------------------------------------- and every interface file has a table */

        List<String> without = new ArrayList<>();
        for (Path xib : interfaceFiles(roots)) {
            Path table = xib.getParent().resolve("en.lproj")
                            .resolve(name(xib) + "." + Strings.EXTENSION);
            if (!Files.isReadable(table)) without.add(xib.getFileName().toString());
        }
        for (String one : without) out.println("      no table beside " + one);
        failures += check(out, "and every window that ships has one beside it",
            without.isEmpty());

        /* ------------------------------------ and the long text is a file as well */

        // A page of help is too long to be one entry in a table, so it is a file in the
        // language directories. Missing, it falls back to a sentence saying so, which is
        // the one way this could break without anything else noticing.
        FMString help = FMLocalized.resource(FMString.of("Help.txt"));
        failures += check(out, "the help page is a file beside the words, not a page of source",
            help.length() > 200);

        /* ------------------------------------------ what is still in the source */

        int literals = 0;
        List<String> worst = new ArrayList<>();
        for (Path file : javaIn(roots)) {
            String source = source(file);
            if (source == null) continue;
            int here = 0;
            for (String line : source.split("\n")) {
                String t = line.trim();
                if (isComment(t)) continue;
                Matcher m = READABLE.matcher(line);
                while (m.find()) {
                    if (looksTechnical(m.group(1))) continue;
                    here++;
                }
            }
            literals += here;
            if (here >= 10) worst.add(here + "  " + file.getFileName());
        }
        java.util.Collections.sort(worst, java.util.Collections.reverseOrder());
        for (String one : worst) out.println("      " + one);
        out.println("      " + literals + " sentences still written into the source, and "
                    + LITERALS_ALLOWED + " allowed");
        failures += check(out, "no more text is written into the source than was before",
            literals <= LITERALS_ALLOWED);

        out.println("      " + (failures == 0
            ? "what a person reads is in a file a translator can open"
            : failures + " failed"));
        return failures;
    }

    /* ------------------------------------------------------------------ the files */

    /** Every place a framework or a program keeps its source. The checks are not read:
        nothing a check prints is shown to anybody but whoever ran it. */
    private static List<Path> sourceRoots() {
        List<Path> found = new ArrayList<>();
        for (String from : new String[]{".", ".."}) {
            for (String area : new String[]{"system", "apps"}) {
                Path at = Path.of(from, area);
                if (Files.isDirectory(at)) found.add(at);
            }
            if (!found.isEmpty()) return found;
        }
        return found;
    }

    private static List<Path> javaIn(List<Path> roots) {
        return under(roots, ".java");
    }

    private static List<Path> interfaceFiles(List<Path> roots) {
        return under(roots, ".xib");
    }

    private static List<Path> under(List<Path> roots, String ending) {
        List<Path> found = new ArrayList<>();
        for (Path root : roots) {
            try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
                for (Path each : walk.toList()) {
                    if (Files.isRegularFile(each) && each.toString().endsWith(ending)) {
                        found.add(each);
                    }
                }
            } catch (IOException unreadable) {
                // A directory that went away while it was being read.
            }
        }
        return found;
    }

    /** Every English table, wherever a bundle or a framework keeps one. */
    private static List<Path> englishTables() {
        List<Path> found = new ArrayList<>();
        for (Path root : sourceRoots()) {
            try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
                for (Path each : walk.toList()) {
                    String path = each.toString().replace('\\', '/');
                    if (path.contains("/en.lproj/") && path.endsWith(".strings")) {
                        found.add(each);
                    }
                }
            } catch (IOException unreadable) {
                // As above.
            }
        }
        return found;
    }

    private static FMDictionary read(Path file) {
        try {
            return Strings.parse(FMString.of(Files.readString(file)));
        } catch (IOException unreadable) {
            return FMDictionary.EMPTY;
        }
    }

    private static String source(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException unreadable) {
            return null;
        }
    }

    private static String name(Path file) {
        String plain = file.getFileName().toString();
        int dot = plain.lastIndexOf('.');
        return dot < 0 ? plain : plain.substring(0, dot);
    }

    /**
     * Whether a piece of text is for a machine rather than a person.
     *
     * A guess, and a generous one: what is being counted is the size of a job, so a wrong
     * answer either way moves a number rather than breaking anything. What matters is that
     * it answers the same way twice, since the whole use of it is comparing today with
     * yesterday.
     */
    private static boolean looksTechnical(String text) {
        if (text.startsWith("@") || text.startsWith("/") || text.contains("://")) return true;
        if (text.contains("=") || text.contains("<") || text.contains("{")) return true;
        if (text.contains(".java") || text.contains(".class") || text.contains(".jar")) return true;
        if (text.startsWith("CF") || text.startsWith("NS") || text.startsWith("LS")) return true;
        // A sentence has a lower case letter in it somewhere after the first word.
        return !text.substring(1).matches(".*[a-z].*");
    }

    /**
     * Whether a dotted name is something naming itself rather than a key.
     *
     * A bundle identifier is a domain backwards, and so is a type identifier, and this
     * system keeps both in the same kind of constant as everything else. They are told
     * apart from a key by the first word, which is what the convention exists for.
     * public. is the domain the types everybody shares are in.
     */
    private static boolean isReverseDomain(String key) {
        for (String prefix : new String[]{"org.", "com.", "net.", "io.", "java.", "javax.",
                                          "public.", "dev."}) {
            if (key.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Whether a line is a comment rather than code.
     *
     * A one line doc comment opens with a slash and two stars, which is neither of the two
     * shapes this used to look for. It counted the example inside one as a sentence
     * somebody had failed to translate.
     */
    private static boolean isComment(String trimmed) {
        return trimmed.startsWith("*") || trimmed.startsWith("//")
            || trimmed.startsWith("/*");
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
