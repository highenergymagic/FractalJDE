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
package org.fractalmicro.uti;

import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMMutableArray;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.os.OSPaths;
import org.fractalmicro.plist.Plist;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every type this machine knows, and the questions worth asking about one.
 *
 * The system's own types are declared in Foundation's resources and end up in its
 * Info.plist. Every other framework and every application may declare more, in their own
 * bundles, and Launch Services registers those as it finds them. One database, built from
 * whatever is installed, which is what makes a type a thing the system knows rather than a
 * list somebody has to keep up to date.
 *
 * Three questions get asked of it. What type is this file. Does this type conform to that
 * one. And what is this type called, which is the Kind column of a window.
 *
 * Conformance is asked upwards and answered by walking, with a limit on the walk because a
 * declaration is data and data can say that a type conforms to itself.
 */
public final class UTTypes {
    private UTTypes() {}

    /** The root every type reaches, and the two under it everything else is one of. */
    public static final FMString ITEM = FMString.of("public.item");
    public static final FMString DATA = FMString.of("public.data");
    public static final FMString CONTENT = FMString.of("public.content");
    public static final FMString DIRECTORY = FMString.of("public.directory");
    public static final FMString FOLDER = FMString.of("public.folder");
    public static final FMString VOLUME = FMString.of("public.volume");
    public static final FMString TEXT = FMString.of("public.text");
    public static final FMString PLAIN_TEXT = FMString.of("public.plain-text");
    public static final FMString IMAGE = FMString.of("public.image");
    public static final FMString AUDIO = FMString.of("public.audio");
    public static final FMString MOVIE = FMString.of("public.movie");
    public static final FMString ARCHIVE = FMString.of("public.archive");
    public static final FMString EXECUTABLE = FMString.of("public.executable");
    public static final FMString SOURCE_CODE = FMString.of("public.source-code");

    /** What this system calls its own, which no other system owns. */
    public static final FMString BUNDLE = FMString.of("org.fractalmicro.bundle");
    public static final FMString APPLICATION = FMString.of("org.fractalmicro.application");
    public static final FMString COMPUTER = FMString.of("org.fractalmicro.computer");
    public static final FMString NETWORK = FMString.of("org.fractalmicro.network");
    public static final FMString SAVED_SEARCH = FMString.of("org.fractalmicro.saved-search");
    public static final FMString ALIAS = FMString.of("com.apple.alias-file");

    /**
     * The one every file that is not anything else gets.
     *
     * public.data rather than nothing, because a file whose type is unknown is still data
     * and something asking whether it can be opened as data should be told yes.
     */
    public static final FMString UNKNOWN = DATA;

    /** How far a conformance walk goes before deciding the declarations are circular. */
    private static final int DEPTH = 32;

    private static final Map<FMString, UTType> BY_IDENTIFIER = new LinkedHashMap<>();
    private static final Map<FMString, FMString> BY_EXTENSION = new LinkedHashMap<>();
    private static boolean readTheFrameworks;

    /* --------------------------------------------------------------- declaring */

    /**
     * Adds one type. The first declaration of an identifier wins, and the first claim on an
     * extension wins, so an application installed later cannot take a file's type away from
     * the system or from a program that was there first.
     */
    public static synchronized void declare(UTType type) {
        if (type == null || type.identifier().isBlank()) return;
        if (BY_IDENTIFIER.putIfAbsent(type.identifier(), type) != null) return;
        for (FMString extension : type.extensions()) {
            BY_EXTENSION.putIfAbsent(extension, type.identifier());
        }
    }

    /** Adds everything a bundle exports and everything it says it merely understands. */
    public static void declareAll(FMDictionary info) {
        if (info == null) return;
        declareList(info.array(UTType.EXPORTED));
        declareList(info.array(UTType.IMPORTED));
    }

    private static void declareList(FMArray<Object> declarations) {
        for (Object each : declarations) {
            FMDictionary one = asDictionary(each);
            if (one != null) declare(UTType.from(one));
        }
    }

    /**
     * One declaration as a dictionary.
     *
     * A plist read off the disk hands back the runtime's own maps inside its arrays, so a
     * declaration arrives as one of those rather than as something already adopted.
     */
    static FMDictionary asDictionary(Object value) {
        if (value instanceof FMDictionary already) return already;
        if (!(value instanceof Map<?, ?> map)) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> one : map.entrySet()) {
            out.put(String.valueOf(one.getKey()), one.getValue());
        }
        return FMDictionary.fromMap(out);
    }

    /* ---------------------------------------------------------------- asking */

    /** The type with this identifier, or nothing at all when nothing declared it. */
    public static UTType type(FMString identifier) {
        ensureRead();
        return identifier == null ? null : BY_IDENTIFIER.get(identifier);
    }

    /** How many types are known, which is what a check counts to know the file was read. */
    public static int count() {
        ensureRead();
        return BY_IDENTIFIER.size();
    }

    /** The type an extension means, or nothing when no declaration claims it. */
    public static FMString preferredType(FMString extension) {
        ensureRead();
        if (extension == null || extension.isBlank()) return null;
        return BY_EXTENSION.get(extension.lowercase());
    }

    /**
     * Whether one type is the other, or is a kind of it.
     *
     * A type conforms to itself, which is what makes asking about an exact type and asking
     * about a family the same question.
     */
    public static boolean conforms(FMString type, FMString to) {
        ensureRead();
        if (type == null || to == null) return false;
        if (type.sameAs(to)) return true;
        return walk(type, to, DEPTH);
    }

    private static boolean walk(FMString from, FMString to, int left) {
        if (left <= 0) return false;
        UTType declared = BY_IDENTIFIER.get(from);
        if (declared == null) return false;
        for (FMString parent : declared.conformsTo()) {
            if (parent.sameAs(to) || walk(parent, to, left - 1)) return true;
        }
        return false;
    }

    /** Everything a type is, from itself up to the root, for anything that wants the chain. */
    public static FMArray<FMString> conformance(FMString type) {
        ensureRead();
        FMMutableArray<FMString> out = FMMutableArray.empty();
        gather(type, out, DEPTH);
        return out.asArray();
    }

    private static void gather(FMString from, FMMutableArray<FMString> into, int left) {
        if (from == null || left <= 0 || into.contains(from)) return;
        into.add(from);
        UTType declared = BY_IDENTIFIER.get(from);
        if (declared == null) return;
        for (FMString parent : declared.conformsTo()) gather(parent, into, left - 1);
    }

    /**
     * The key a type's words are under, for whoever is going to look them up.
     *
     * A key rather than the words, because this is Foundation and the words are a bundle's.
     * A type nobody declared has no description, and the caller says what to do about that.
     */
    public static FMString descriptionKey(FMString type) {
        UTType declared = type(type);
        return declared == null ? FMString.EMPTY : declared.description();
    }

    /* ------------------------------------------------------- what the frameworks say */

    /**
     * Reads the declarations out of every installed framework, once.
     *
     * Off the disk rather than listed here, so a framework that declares a type is found
     * without this being told about it, which is the same rule the words follow.
     */
    private static synchronized void ensureRead() {
        if (readTheFrameworks) return;
        readTheFrameworks = true;
        for (Path info : frameworkInfoPlists()) {
            try {
                declareAll(Plist.dictionary(info));
            } catch (java.io.IOException unreadable) {
                // A framework whose Info.plist will not parse declares nothing, which is
                // what it declared before anybody wrote one.
            }
        }
    }

    /** Forgets what was read, so a volume laid out after this was first asked is seen. */
    public static synchronized void reload() {
        BY_IDENTIFIER.clear();
        BY_EXTENSION.clear();
        readTheFrameworks = false;
    }

    private static java.util.List<Path> frameworkInfoPlists() {
        java.util.List<Path> found = new java.util.ArrayList<>();
        collect(OSPaths.frameworks(), found, 0);
        return found;
    }

    private static void collect(Path directory, java.util.List<Path> into, int depth) {
        if (depth > 4 || !Files.isDirectory(directory)) return;
        try (java.util.stream.Stream<Path> kids = Files.list(directory)) {
            for (Path each : kids.toList()) {
                if (!Files.isDirectory(each)) continue;
                if (each.getFileName().toString().endsWith(".framework")) {
                    Path info = each.resolve("Versions/A/Resources/Info.plist");
                    if (Files.isReadable(info)) into.add(info);
                    collect(each.resolve("Versions/A/Frameworks"), into, depth + 1);
                }
            }
        } catch (java.io.IOException cannotList) {
            // A directory that will not open holds no frameworks this can speak for.
        }
    }
}
