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
package org.fractalmicro.foundation;

import org.fractalmicro.os.Languages;
import org.fractalmicro.os.OSPaths;
import org.fractalmicro.plist.Strings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The words, which are not in the program.
 *
 * Every piece of text a person reads is filed under a key in a strings file, and this is
 * what looks one up. A program names the key; what the key stands for is in
 * Localizable.strings, in the language the account asked for, inside whichever bundle the
 * text belongs to.
 *
 * Text written into the source cannot be translated by anyone who is not also a programmer,
 * cannot be corrected without a build, and cannot be found without reading every file.
 *
 * Where it looks, in order:
 *
 *   the running program's bundle       Contents/Resources/<language>.lproj/
 *   each framework it is running with  Versions/A/Resources/<language>.lproj/
 *
 * The program first, so it can say something differently from the framework it got it from
 * without either of them arranging it, and so a translation can be added to a program that
 * was built without one.
 *
 * A key with no entry answers with itself, which keeps an untranslated program readable
 * rather than empty. That is a defect rather than a design: {@code LocalizationTest} fails
 * when the English table has no words for a key the source asks for.
 */
public final class FMLocalized {
    private FMLocalized() {}

    /** What a table is called when nothing says otherwise. */
    public static final FMString TABLE = Strings.DEFAULT_TABLE;

    /**
     * Where the running program is, as the loader left it.
     *
     * Named here rather than taken from the loader because the loader is above this
     * framework and cannot be called from it. It is one string, and a check keeps the two
     * spellings the same.
     */
    public static final String EXECUTABLE_PROPERTY = "org.fractalmicro.executable";

    /** Parsed tables, by the file they came from. A file that is not there is remembered
        as not being there, which is the half that was missing and cost the most. */
    private static final Map<Path, FMDictionary> LOADED = new java.util.HashMap<>();

    /** Whole tables, merged across every bundle, for the language in use now. */
    private static volatile Map<FMString, FMDictionary> mergedTables = new java.util.HashMap<>();

    /** A table nothing was found in, so a miss can be remembered as one. */
    private static final FMDictionary NOTHING = FMDictionary.EMPTY;

    /** The resource directories to search, in order, worked out once. */
    private static List<Path> places;

    /** The languages the search was built for, so a change is noticed. */
    private static String builtFor = "";

    /**
     * The words for a key, from the default table.
     *
     * This is the one almost everything calls. The key is an identifier rather than a
     * sentence: "finder.emptyTrash" rather than "Empty Trash…", so that the English is in
     * the English table with every other language and not in one place of its own.
     */
    public static FMString of(FMString key) { return of(key, TABLE); }

    /**
     * The same, from a table of its own. A window's words are usually in one.
     *
     * One table is read from every bundle it might be in, merged once, and kept. Asking a
     * question of a merged table is a lookup; asking it of a list of directories is a walk
     * over the disk, and this is asked for every word on the screen.
     *
     * It went the other way first, and the difference was a hundred and thirty microseconds
     * per word. The cost was not the reading: it was the not-reading. A bundle without a
     * table has no file to open, so nothing was kept, so every later question asked the
     * file system again whether the file had appeared. Listing a folder of a thousand items
     * asks for a thousand kinds, and the Finder stopped for a sixth of a second to name
     * files it had already named.
     */
    public static FMString of(FMString key, FMString table) {
        if (key == null || key.isEmpty()) return FMString.EMPTY;
        FMString found = merged(table).string(key, FMString.EMPTY);
        return found.isEmpty() ? key : found;
    }

    /** One table, gathered from every bundle that has one, in the order they are asked. */
    private static FMDictionary merged(FMString table) {
        Map<FMString, FMDictionary> tables = current();
        FMDictionary already = tables.get(table);
        if (already != null) return already;
        synchronized (FMLocalized.class) {
            already = tables.get(table);
            if (already != null) return already;

            // Merged from the back, so that a bundle earlier in the search order writes
            // over one later in it: a program saying something differently from the
            // framework it got it from is the whole reason there is an order.
            FMMutableDictionary into = FMMutableDictionary.empty();
            List<Path> path = searchPath();
            for (int i = path.size() - 1; i >= 0; i--) {
                FMDictionary words = tableIn(path.get(i), table);
                for (FMString key : words.keys()) into.set(key, words.string(key));
            }
            FMDictionary done = into.asDictionary();
            tables.put(table, done);
            return done;
        }
    }

    /**
     * The words for a key with something filled into them.
     *
     * The blanks are numbered in the table rather than ordered by the call, because word
     * order is one of the things that changes between languages and a translator who
     * cannot move them has to write a worse sentence.
     */
    public static FMString filled(FMString key, FMArray<FMString> values) {
        FMString text = of(key);
        for (int i = 0; i < values.count(); i++) {
            text = text.replacing(FMString.of("%" + (i + 1) + "$@"), values.at(i));
        }
        return text;
    }

    /** One blank and two, which is what nearly every such sentence has. */
    public static FMString filled(FMString key, FMString one) {
        FMMutableArray<FMString> values = FMMutableArray.empty();
        values.add(one);
        return filled(key, values.asArray());
    }

    public static FMString filled(FMString key, FMString one, FMString two) {
        FMMutableArray<FMString> values = FMMutableArray.empty();
        values.add(one);
        values.add(two);
        return filled(key, values.asArray());
    }

    /**
     * Forgets what was read, so the next question is answered in the language asked for
     * now. Changing the language changes every window, and nothing would change if the
     * tables read before it were kept.
     */
    public static synchronized void reload() {
        LOADED.clear();
        mergedTables = new java.util.HashMap<>();
        places = null;
        builtFor = "";
    }

    /**
     * The tables as they stand, thrown away whole when the language changes.
     *
     * Checked on every lookup, which is why it is a field read rather than a question put
     * to the preferences: what changes is rare and what asks is constant.
     */
    private static Map<FMString, FMDictionary> current() {
        if (!listening) listen();
        return mergedTables;
    }

    private static volatile boolean listening;

    /**
     * Waits to be told the language changed, rather than asking.
     *
     * Asking meant reading a preference domain for every word on the screen. What is being
     * watched for happens when somebody opens a preference pane and picks a language, so
     * it is announced once and heard once, and the reading in between costs nothing.
     */
    private static synchronized void listen() {
        if (listening) return;
        listening = true;
        try {
            FMDistributedNotificationCenter.defaultCenter()
                .addObserver(Languages.CHANGED, (name, about) -> {
                    Languages.forget();
                    reload();
                });
        } catch (RuntimeException noCenter) {
            // No notifications in this process. A language change will be seen the next
            // time it starts, which is what a program with no way to be told can do.
        }
    }

    /** Bundles searched as well, because something in this process belongs to them. */
    private static final List<Path> ALSO = new ArrayList<>();

    /**
     * Says that a bundle's words belong in this process too.
     *
     * The running program's own bundle is found without being told, and for a program in a
     * process of its own that is the whole answer. It is not the whole answer here: the
     * Finder, the Dock and the window server all run inside one process, and only one of
     * them can be the program that started it. The others say so.
     *
     * Searched before the frameworks and after the program that started the process, which
     * is the order the same question has anywhere else: the more particular bundle wins.
     */
    public static synchronized void searchAlso(Path resources) {
        if (resources == null || ALSO.contains(resources)) return;
        ALSO.add(resources);
        places = null;
        mergedTables = new java.util.HashMap<>();
    }

    /* ----------------------------------------------------------------- the search */

    /**
     * Every place a table might be, in the order they are asked.
     *
     * Worked out once and kept, because this is asked for every piece of text on the
     * screen and the answer is a handful of directories that do not move.
     */
    private static List<Path> searchPath() {
        if (places != null) return places;

        List<Path> found = new ArrayList<>();
        Path program = mainBundleResources();
        if (program != null) found.add(program);
        for (Path also : ALSO) {
            if (!found.contains(also) && Files.isDirectory(also)) found.add(also);
        }
        for (Path framework : frameworkResources()) found.add(framework);

        places = List.copyOf(found);
        return places;
    }

    /**
     * The resources of the program running in this process.
     *
     * The loader says which executable it started; a bundle keeps it at
     * Contents/Fractal/Name, so the resources are two directories up and one along. A
     * process the loader did not start has no bundle, and only the frameworks are searched.
     */
    private static Path mainBundleResources() {
        String executable = System.getProperty(EXECUTABLE_PROPERTY, "");
        if (executable.isBlank()) return null;
        Path at = Path.of(executable).toAbsolutePath().getParent();   // Contents/Fractal
        if (at == null || at.getParent() == null) return null;
        Path resources = at.getParent().resolve("Resources");         // Contents/Resources
        return Files.isDirectory(resources) ? resources : null;
    }

    /**
     * The resources of every framework installed, umbrella frameworks included.
     *
     * Read off the disk rather than listed here, so a framework added later is searched
     * without this having to be told about it.
     */
    private static List<Path> frameworkResources() {
        List<Path> found = new ArrayList<>();
        collectFrameworks(OSPaths.frameworks(), found, 0);
        return found;
    }

    private static void collectFrameworks(Path directory, List<Path> into, int depth) {
        if (depth > 2 || !Files.isDirectory(directory)) return;
        try (java.util.stream.Stream<Path> entries = Files.list(directory)) {
            for (Path each : entries.sorted().toList()) {
                if (!each.getFileName().toString().endsWith(".framework")) continue;
                Path resources = each.resolve("Versions/A/Resources");
                if (Files.isDirectory(resources)) into.add(resources);
                collectFrameworks(each.resolve("Versions/A/Frameworks"), into, depth + 1);
            }
        } catch (IOException unreadable) {
            // A volume being written while it is read. The tables found so far still work.
        }
    }

    /* ----------------------------------------------------------------- the tables */

    /**
     * One table out of one resource directory, in the language this account reads.
     *
     * The languages are tried in the order the account asked for them, then the one the
     * program was written in, then a table sitting outside the language directories
     * altogether, which is what a bundle with no translations has.
     */
    private static FMDictionary tableIn(Path resources, FMString table) {
        String file = table + "." + Strings.EXTENSION;
        for (FMString language : Languages.preferred()) {
            FMDictionary words = read(resources.resolve(language + ".lproj").resolve(file));
            if (words != null) return words;
        }
        FMDictionary beside = read(resources.resolve(file));
        return beside == null ? FMDictionary.EMPTY : beside;
    }

    /**
     * A table off the disk, parsed once. Null means there is no such file.
     *
     * A file that is not there is remembered as not being there. Most bundles have no
     * table for most tables, and asking the file system that question again for every word
     * on the screen was the whole of what made this slow.
     */
    private static FMDictionary read(Path file) {
        FMDictionary already = LOADED.get(file);
        if (already != null) return already == NOTHING ? null : already;
        if (!Files.isReadable(file)) {
            LOADED.put(file, NOTHING);
            return null;
        }
        try {
            FMDictionary words = Strings.parse(FMString.of(Files.readString(file)));
            LOADED.put(file, words);
            return words;
        } catch (IOException unreadable) {
            LOADED.put(file, NOTHING);
            return null;
        }
    }

    private static List<String> asStrings(FMArray<FMString> values) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < values.count(); i++) out.add(values.at(i).toString());
        return out;
    }
}
