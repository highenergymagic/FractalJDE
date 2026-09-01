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

import org.fractalmicro.os.OSPaths;
import org.fractalmicro.plist.Plist;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where a fork goes when the volume will not hold one.
 *
 * FAT, exFAT and most network shares have no room for a second stream on a file, so the
 * records that would live in one are kept here instead, in a property list under the
 * user's library, keyed by the full path of the file they belong to.
 *
 * This is worse in the way sidecar files are always worse: move the file with something
 * else and the record is orphaned. It is used only where the file system leaves no
 * choice, and {@link #inUse} reports when that has happened so the rest of the program can
 * say so.
 */
public final class Sidecar {
    private Sidecar() {}

    private static final String FILE_NAME = "org.fractalmicro.finderinfo.plist";
    private static Map<String, Object> records;
    private static boolean used;

    /** Whether anything has had to fall back to here this session. */
    public static boolean inUse() { return used; }

    public static Path file() { return OSPaths.userPreferences().resolve(FILE_NAME); }

    private static synchronized Map<String, Object> records() {
        if (records != null) return records;
        records = new LinkedHashMap<>();
        Path path = file();
        if (Files.isReadable(path)) {
            try {
                records = Plist.readDictionary(path);
            } catch (IOException e) {
                org.fractalmicro.core.Log.info("could not read " + path + ": " + e.getMessage());
            }
        }
        return records;
    }

    private static String key(File file, String stream) {
        return file.getAbsolutePath() + ":" + stream;
    }

    public static synchronized byte[] read(File file, String stream) {
        if (file == null) return null;
        Object value = records().get(key(file, stream));
        if (!(value instanceof String encoded)) return null;
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static synchronized void write(File file, String stream, byte[] bytes) {
        if (file == null || bytes == null) return;
        used = true;
        records().put(key(file, stream), Base64.getEncoder().encodeToString(bytes));
        save();
    }

    public static synchronized void remove(File file, String stream) {
        if (file == null) return;
        if (records().remove(key(file, stream)) != null) save();
    }

    /** Follows a record when the file it belongs to is renamed here. */
    public static synchronized void moved(File from, File to) {
        if (from == null || to == null) return;
        boolean changed = false;
        for (Map.Entry<String, Object> entry
                : new LinkedHashMap<>(records()).entrySet()) {
            String prefix = from.getAbsolutePath() + ":";
            if (entry.getKey().startsWith(prefix)) {
                records().remove(entry.getKey());
                records().put(to.getAbsolutePath() + entry.getKey().substring(
                    from.getAbsolutePath().length()), entry.getValue());
                changed = true;
            }
        }
        if (changed) save();
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            Plist.write(file(), records());
        } catch (IOException e) {
            org.fractalmicro.core.Log.info("could not write " + file() + ": " + e.getMessage());
        }
    }
}
