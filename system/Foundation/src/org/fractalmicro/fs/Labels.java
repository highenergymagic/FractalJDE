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
package org.fractalmicro.fs;

import org.fractalmicro.foundation.FMLocalized;
import org.fractalmicro.foundation.FMArray;

import org.fractalmicro.foundation.FMString;

import org.fractalmicro.os.FMUserDefaults;
import org.fractalmicro.os.FinderSettings;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Labels: the seven colours a file can be marked with, plus none.
 *
 * The label is not kept in a database of this program's own. It lives in bits 1 to 3 of
 * the file's Finder flags, which is where the Finder has kept it since long before any
 * of this, and on this file system those flags are in the file's own AFP_AfpInfo stream.
 * Copy the file somewhere else with anything at all and the label travels with it.
 *
 * The names can be changed, as they can in Finder preferences, and are kept in the
 * Finder's settings domain.
 */
public final class Labels {
    private Labels() {}

    public static final int NONE = 0;
    public static final int COUNT = 8;

    /** The settings key holding the seven names, in order. */
    public static final FMString NAMES_KEY = FMString.of("LabelNames");

    /**
     * What each label is called before anybody renames one.
     *
     * Keys rather than the words: a label can be renamed and then the name is the
     * person's own, but until it is, it is the system's word and is read in the language.
     */
    private static final FMString[] DEFAULT_NAMES = {
        FMString.of("label.none"), FMString.of("label.red"), FMString.of("label.orange"),
        FMString.of("label.yellow"), FMString.of("label.green"), FMString.of("label.blue"),
        FMString.of("label.purple"), FMString.of("label.gray")
    };

    /** The colours 10.6 draws, in the order the Finder lists them. */
    private static final Color[] COLORS = {
        null,
        new Color(0xF8, 0x6A, 0x6A),
        new Color(0xF6, 0xA8, 0x4B),
        new Color(0xE9, 0xD8, 0x51),
        new Color(0xA5, 0xD6, 0x6A),
        new Color(0x77, 0xB6, 0xEE),
        new Color(0xCE, 0x9B, 0xE6),
        new Color(0xC0, 0xC0, 0xC0)
    };

    /** Reading a stream for every file in a big folder is slow, so answers are kept. */
    private static final Map<String, Integer> CACHE = new ConcurrentHashMap<>();

    public static Color colorOf(int label) {
        return label > 0 && label < COLORS.length ? COLORS[label] : null;
    }

    /** The name of one label, as it is set now. */
    public static String nameOf(int label) {
        if (label <= 0 || label >= COUNT) {
            return FMLocalized.of(DEFAULT_NAMES[0]).toString();
        }
        FMArray<Object> names = FMUserDefaults.of(FMUserDefaults.FINDER).array(NAMES_KEY);
        if (names.count() >= COUNT - 1) {
            Object name = names.at(label - 1);
            if (name instanceof FMString text && !text.isBlank()) return text.toString();
            if (name instanceof String s && !s.isBlank()) return s;
        }
        return FMLocalized.of(DEFAULT_NAMES[label]).toString();
    }

    public static List<String> names() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) out.add(nameOf(i));
        return out;
    }

    /** Renames one label, the way the Labels pane of Finder preferences does. */
    public static void rename(int label, String name) {
        if (label <= 0 || label >= COUNT) return;
        java.util.List<Object> names = new ArrayList<>(FMUserDefaults.of(FMUserDefaults.FINDER).array(NAMES_KEY).asList());
        while (names.size() < COUNT - 1) names.add(DEFAULT_NAMES[names.size() + 1]);
        names.set(label - 1, name);
        FMUserDefaults.of(FMUserDefaults.FINDER).set(NAMES_KEY, names);
    }

    /* --------------------------------------------------------- one file */

    /** The label on a file, 0 when it has none. */
    public static int of(File file) {
        if (file == null) return NONE;
        Integer cached = CACHE.get(key(file));
        if (cached != null) return cached;
        int label = FinderInfo.of(file).label();
        CACHE.put(key(file), label);
        return label;
    }

    /**
     * Marks a file. Answers whether the mark went into the file itself rather than into
     * the sidecar, so the Finder can say when a volume could not hold it.
     */
    public static boolean set(File file, int label) {
        if (file == null) return false;
        boolean onTheFile = FinderInfo.of(file).label(label).writeTo(file);
        CACHE.put(key(file), Math.max(0, Math.min(7, label)));
        return onTheFile;
    }

    public static void forget(File file) {
        if (file != null) CACHE.remove(key(file));
    }

    public static void forgetAll() { CACHE.clear(); }

    private static String key(File file) {
        return file.getAbsolutePath();
    }

    /** What a labelled file is called, or nothing when it has no label. */
    public static String spoken(File file) {
        int label = of(file);
        return label == NONE ? "" : nameOf(label) + " label";
    }

    /** Whether labels are shown at all: they are, unless the settings say otherwise. */
    public static boolean showing() {
        return FinderSettings.showLabels();
    }
}
