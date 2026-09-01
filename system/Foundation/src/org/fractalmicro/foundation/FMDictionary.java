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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Values kept under names.
 *
 * A property list is one of these, a bundle's description is one, a message's contents are
 * one, and a program that has this does not need the runtime's own map to read any of
 * them. Keys are text; values are whatever this system's values are.
 *
 * Order is kept. A property list read from a file and written back should come out in the
 * order it went in, because a person may be reading the file, and because a difference
 * between two of them should be the difference and not the order.
 *
 * This cannot be changed once made. Something that builds one up wants
 * {@link FMMutableDictionary}, and hands over the finished thing with
 * {@link FMMutableDictionary#asDictionary}.
 */
public class FMDictionary {

    /** Nothing under any name. */
    public static final FMDictionary EMPTY = new FMDictionary(new LinkedHashMap<>());

    final Map<FMString, Object> values;

    FMDictionary(Map<FMString, Object> values) {
        this.values = values;
    }

    public static FMDictionary of(Map<FMString, Object> values) {
        return new FMDictionary(new LinkedHashMap<>(values));
    }

    /** One name and one value, which is the shape most of them start as. */
    public static FMDictionary of(FMString key, Object value) {
        Map<FMString, Object> one = new LinkedHashMap<>();
        one.put(key, value);
        return new FMDictionary(one);
    }

    public int count() { return values.size(); }

    public boolean isEmpty() { return values.isEmpty(); }

    public boolean has(FMString key) { return values.containsKey(key); }

    /** The names, in the order they were put in. */
    public FMArray<FMString> keys() {
        FMMutableArray<FMString> out = FMMutableArray.empty();
        for (FMString key : values.keySet()) out.add(key);
        return out.asArray();
    }

    /** Whatever is under a name, or nothing. */
    public Object value(FMString key) { return values.get(key); }

    /**
     * The text under a name.
     *
     * A value that is not text is described rather than refused: a property list holding
     * the number 10 where a program expects a version is holding something the program can
     * read, and answering nothing would be less true than answering "10".
     */
    public FMString string(FMString key, FMString fallback) {
        Object found = values.get(key);
        if (found == null) return fallback;
        if (found instanceof FMString text) return text;
        return FMString.describing(found);
    }

    public FMString string(FMString key) { return string(key, FMString.EMPTY); }

    /**
     * The number under a name, reading text as a number where it is one.
     *
     * Property lists are full of numbers written as text, and a program asking for a count
     * should not have to know which of the two it is going to find.
     */
    public FMNumber number(FMString key, FMNumber fallback) {
        Object found = values.get(key);
        if (found instanceof FMNumber number) return number;
        if (found instanceof Boolean truth) return FMNumber.of(truth);
        if (found instanceof Number n) {
            return n instanceof Double || n instanceof Float
                ? FMNumber.of(n.doubleValue()) : FMNumber.of(n.longValue());
        }
        if (found instanceof FMString text) {
            FMNumber parsed = FMNumber.parsing(text);
            if (parsed != null) return parsed;
        }
        return fallback;
    }

    public boolean truth(FMString key, boolean fallback) {
        FMNumber found = number(key, null);
        return found == null ? fallback : found.isTrue();
    }

    public long whole(FMString key, long fallback) {
        FMNumber found = number(key, null);
        return found == null ? fallback : found.asWhole();
    }

    /** The dictionary under a name, for a property list holding another one. */
    public FMDictionary dictionary(FMString key) {
        return adopting(values.get(key)) instanceof FMDictionary inner ? inner : EMPTY;
    }

    /** The values under a name, for a property list holding a list. */
    @SuppressWarnings("unchecked")
    public FMArray<Object> array(FMString key) {
        return adopting(values.get(key)) instanceof FMArray<?> found
            ? (FMArray<Object>) found : FMArray.empty();
    }

    /** The same values with one more, since this one cannot be changed. */
    public FMDictionary adding(FMString key, Object value) {
        Map<FMString, Object> more = new LinkedHashMap<>(values);
        more.put(key, value);
        return new FMDictionary(more);
    }

    /**
     * The runtime's own map, for the parts of the system written against it.
     *
     * All the way down. A property list holds lists and other dictionaries, and handing
     * over the outside of one while leaving this system's own values inside it would give
     * the caller something it could not read: the writer underneath knows text, numbers,
     * lists and maps, and has never heard of an FMString.
     */
    public Map<String, Object> asMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<FMString, Object> one : values.entrySet()) {
            out.put(one.getKey().toString(), surrendering(one.getValue()));
        }
        return out;
    }

    /** One value as the runtime spells it. */
    private static Object surrendering(Object value) {
        if (value instanceof FMString text) return text.toString();
        if (value instanceof FMNumber number) {
            return switch (number.kind()) {
                case TRUTH -> number.isTrue();
                case WHOLE -> number.asWhole();
                case REAL -> number.asReal();
            };
        }
        if (value instanceof FMDictionary inner) return inner.asMap();
        if (value instanceof FMArray<?> list) {
            java.util.List<Object> out = new java.util.ArrayList<>();
            for (int i = 0; i < list.count(); i++) out.add(surrendering(list.at(i)));
            return out;
        }
        return value;
    }

    /**
     * One made from the runtime's own map, which is how a value crosses into this system.
     *
     * All the way down as well, and for the same reason from the other side: a dictionary
     * read out of a file holds the parser's own strings and maps, and something asking it
     * for the dictionary under a name should get one rather than whatever the parser
     * happened to build.
     */
    public static FMDictionary fromMap(Map<String, Object> map) {
        Map<FMString, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> one : map.entrySet()) {
            out.put(FMString.of(one.getKey()), adopting(one.getValue()));
        }
        return new FMDictionary(out);
    }

    /** One value as this system spells it. */
    @SuppressWarnings("unchecked")
    static Object adopting(Object value) {
        if (value instanceof String text) return FMString.of(text);
        if (value instanceof Boolean truth) return FMNumber.of(truth);
        if (value instanceof Double || value instanceof Float) {
            return FMNumber.of(((Number) value).doubleValue());
        }
        if (value instanceof Number whole) return FMNumber.of(whole.longValue());
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> plain = new LinkedHashMap<>();
            for (Map.Entry<?, ?> one : map.entrySet()) {
                plain.put(String.valueOf(one.getKey()), one.getValue());
            }
            return fromMap(plain);
        }
        if (value instanceof java.util.List<?> list) {
            FMMutableArray<Object> out = FMMutableArray.empty();
            for (Object one : list) out.add(adopting(one));
            return out.asArray();
        }
        return value;
    }

    @Override public String toString() { return values.toString(); }

    @Override public boolean equals(Object other) {
        return other instanceof FMDictionary d && values.equals(d.values);
    }

    @Override public int hashCode() { return values.hashCode(); }
}
