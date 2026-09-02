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
package org.fractalmicro.os;

import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMMutableArray;
import org.fractalmicro.foundation.FMNumber;
import org.fractalmicro.foundation.FMDistributedNotificationCenter;
import org.fractalmicro.foundation.FMString;

import org.fractalmicro.plist.Plist;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * The preferences system: one property list per domain, in
 * ~/.fractaldt/Users/&lt;user&gt;/Library/Preferences, under the documented key names,
 * so a settings file written elsewhere can be read as it stands, XML or binary.
 */
public final class FMUserDefaults {

    public static final FMString FINDER = FMString.of("org.fractalmicro.finder");
    public static final FMString DOCK = FMString.of("org.fractalmicro.dock");
    public static final FMString GLOBAL = FMString.of(".GlobalPreferences");
    public static final FMString UNIVERSAL_ACCESS = FMString.of("org.fractalmicro.universalaccess");
    public static final FMString SIDEBAR_LISTS = FMString.of("org.fractalmicro.sidebarlists");
    public static final FMString TEXT_EDIT = FMString.of("org.fractalmicro.textedit");

    /** What each domain used to be called, so old settings are not left behind. */
    private static final FMString[][] RENAMED = {
        {FMString.of("com.apple.finder"), FINDER},
        {FMString.of("com.apple.dock"), DOCK},
        {FMString.of("com.apple.universalaccess"), UNIVERSAL_ACCESS},
        {FMString.of("com.apple.sidebarlists"), SIDEBAR_LISTS},
        {FMString.of("com.fractalmicro.finder"), FINDER},
        {FMString.of("com.apple.TextEdit"), TEXT_EDIT},
    };

    /**
     * Moves settings written under the old domain names. Run once at start-up, before
     * anything reads a domain, so nobody loses their preferences to a rename.
     */
    public static synchronized void migrate() {
        for (FMString[] pair : RENAMED) {
            Path from = OSPaths.userPreferences().resolve(pair[0] + ".plist");
            Path to = OSPaths.userPreferences().resolve(pair[1] + ".plist");
            if (!Files.isReadable(from)) continue;
            try {
                if (Files.exists(to)) {
                    // Both exist: fold the old keys in without overwriting the new ones.
                    Map<String, Object> older = Plist.readDictionary(from);
                    Map<String, Object> newer = Plist.readDictionary(to);
                    for (Map.Entry<String, Object> e : older.entrySet()) {
                        newer.putIfAbsent(e.getKey(), e.getValue());
                    }
                    Plist.write(to, newer);
                    Files.delete(from);
                } else {
                    Files.move(from, to);
                }
                org.fractalmicro.core.Log.info("settings moved from " + pair[0] + " to " + pair[1]);
            } catch (IOException e) {
                org.fractalmicro.core.Log.error("could not move the settings in " + pair[0], e);
            }
        }
    }

    private static final Map<String, FMUserDefaults> DOMAINS = new HashMap<>();
    private static final List<BiConsumer<String, String>> LISTENERS = new ArrayList<>();

    private final String domain;
    private final Path file;
    private final Map<String, Object> values;

    private FMUserDefaults(String domain) {
        this.domain = domain;
        this.file = OSPaths.userPreferences().resolve(domain + ".plist");
        Map<String, Object> loaded = new LinkedHashMap<>();
        if (Files.isReadable(file)) {
            try {
                loaded = Plist.readDictionary(file);
            } catch (Exception e) {
                System.err.println("could not read " + file + ": " + e.getMessage());
            }
        }
        this.values = loaded;
    }

    /**
     * Reads the file again, because another process wrote it.
     *
     * What is held here was read when this domain was first asked for, and it was right
     * until somebody else changed the file. Merging rather than replacing would keep
     * whatever this process wrote and lost, which is the wrong half to keep: the file is
     * what both processes agree on.
     */
    private synchronized void reload() {
        if (!Files.isReadable(file)) return;
        try {
            Map<String, Object> again = Plist.readDictionary(file);
            values.clear();
            values.putAll(again);
        } catch (Exception unreadable) {
            System.err.println("could not read " + file + ": " + unreadable.getMessage());
        }
    }

    public static synchronized FMUserDefaults of(FMString domain) {
        return DOMAINS.computeIfAbsent(domain.toString(), FMUserDefaults::new);
    }

    /**
     * Tells this process's watchers about a change made somewhere else.
     *
     * The value is already on the volume; what is missing in a process that did not make
     * the change is the knowledge that it happened. This supplies that and nothing more.
     */
    public static void announce(String domain, String key) {
        FMUserDefaults held = DOMAINS.get(domain);
        if (held != null) held.reload();
        for (BiConsumer<String, String> l : new ArrayList<>(LISTENERS)) l.accept(domain, key);
    }

    /** Called with (domain, key) whenever anything is written. */
    public static void onChange(BiConsumer<String, String> listener) {
        LISTENERS.add(listener);
    }

    public FMString domain() { return FMString.of(domain); }
    public Path file() { return file; }

    /* -------------------------------------------------------------- reading */

    public Object get(FMString key) { return values.get(key.toString()); }

    public boolean bool(FMString key, boolean fallback) {
        Object v = values.get(key.toString());
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        if (v instanceof String) return Boolean.parseBoolean((String) v);
        return fallback;
    }

    public long integer(FMString key, long fallback) {
        Object v = values.get(key.toString());
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String) {
            try { return Long.parseLong((String) v); } catch (NumberFormatException ignored) { }
        }
        return fallback;
    }

    public double real(FMString key, double fallback) {
        Object v = values.get(key.toString());
        return v instanceof Number ? ((Number) v).doubleValue() : fallback;
    }

    public FMString string(FMString key, FMString fallback) {
        Object v = values.get(key.toString());
        return v instanceof String text ? FMString.of(text) : fallback;
    }

    public FMString string(FMString key) { return string(key, FMString.EMPTY); }

    @SuppressWarnings("unchecked")
    public FMArray<Object> array(FMString key) {
        Object v = values.get(key.toString());
        if (!(v instanceof List)) return FMArray.empty();
        FMMutableArray<Object> out = FMMutableArray.empty();
        for (Object one : (List<Object>) v) out.add(one);
        return out.asArray();
    }

    @SuppressWarnings("unchecked")
    public FMDictionary dictionary(FMString key) {
        Object v = values.get(key.toString());
        return v instanceof Map
            ? FMDictionary.fromMap((Map<String, Object>) v) : FMDictionary.EMPTY;
    }

    /** Reads a value nested inside dictionaries, as Finder's view settings are. */
    @SuppressWarnings("unchecked")
    public Object nested(FMString... path) {
        Object current = values;
        for (FMString key : path) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(key.toString());
        }
        return current;
    }

    /* -------------------------------------------------------------- writing */

    public void set(FMString key, Object value) {
        String name = key.toString();
        Object held = value instanceof FMString text ? text.toString()
                    : value instanceof FMNumber number ? number.asWhole()
                    : value;
        Object old = values.get(name);
        if (Objects.equals(old, held)) return;
        values.put(name, held);
        save();
        fire(name);
    }

    /** Writes a truth, which is the commonest thing a preference holds. */
    public void set(FMString key, boolean value) { set(key, (Object) value); }

    /** Writes a value nested inside dictionaries, creating them as needed. */
    @SuppressWarnings("unchecked")
    public void setNested(Object value, FMString... path) {
        Map<String, Object> current = values;
        for (int i = 0; i < path.length - 1; i++) {
            Object next = current.get(path[i].toString());
            if (!(next instanceof Map)) {
                next = new LinkedHashMap<String, Object>();
                current.put(path[i].toString(), next);
            }
            current = (Map<String, Object>) next;
        }
        current.put(path[path.length - 1].toString(), value);
        save();
        fire(path[0].toString());
    }

    public void applyDefault(FMString key, Object value) {
        String name = key.toString();
        Object held = value instanceof FMString text ? text.toString() : value;
        if (!values.containsKey(name)) values.put(name, held);
    }

    public void save() {
        try {
            Plist.write(file, values);
        } catch (IOException e) {
            System.err.println("could not write " + file + ": " + e.getMessage());
        }
    }

    /** What a preference change is called when it crosses to another process. */
    public static final FMString CHANGED = FMString.of("FMUserDefaultsDidChangeNotification");

    /**
     * Tells whatever is watching that a preference changed.
     *
     * Here first, and then everywhere else. A setting written by System Preferences has to
     * reach the desktop or nothing on the screen changes until something restarts, and the
     * two have been different programs since the day applications stopped sharing an
     * address space with the thing drawing for them.
     */
    private void fire(String key) {
        for (BiConsumer<String, String> l : new ArrayList<>(LISTENERS)) l.accept(domain, key);
        FMDistributedNotificationCenter.defaultCenter()
            .post(CHANGED, FMString.of(domain + " " + key));
    }
}
