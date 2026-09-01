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
package org.fractalmicro.xpc;

import org.fractalmicro.plist.Plist;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A message between two programs.
 *
 * A message is a dictionary, and it goes across as a property list, which is the same
 * thing this system's settings and bundles are written in. That is not a coincidence: one
 * format for everything on disk and on the wire means one reader, one writer, and one set
 * of types to think about.
 *
 * Every message says what it is under "type". A reply carries the same type back, plus
 * whatever was asked for, or "error" if the service could not do it.
 */
public final class Message {

    public static final String TYPE = "type";
    public static final String ERROR = "error";

    private final Map<String, Object> values;

    public Message(String type) {
        this.values = new LinkedHashMap<>();
        values.put(TYPE, type);
    }

    private Message(Map<String, Object> values) {
        this.values = values;
    }

    public static Message of(String type) { return new Message(type); }

    /** A reply saying why something could not be done. */
    public static Message error(String why) {
        Message message = new Message("error");
        message.put(ERROR, why);
        return message;
    }

    public String type() { return string(TYPE, ""); }

    public boolean isError() { return values.containsKey(ERROR); }

    public String errorText() { return string(ERROR, ""); }

    public Message put(String key, Object value) {
        values.put(key, value);
        return this;
    }

    public Object get(String key) { return values.get(key); }

    public String string(String key, String fallback) {
        Object value = values.get(key);
        return value instanceof String s ? s : fallback;
    }

    public long integer(String key, long fallback) {
        Object value = values.get(key);
        return value instanceof Number n ? n.longValue() : fallback;
    }

    public boolean bool(String key, boolean fallback) {
        Object value = values.get(key);
        return value instanceof Boolean b ? b : fallback;
    }

    @SuppressWarnings("unchecked")
    public List<Object> array(String key) {
        Object value = values.get(key);
        return value instanceof List ? (List<Object>) value : new ArrayList<>();
    }

    public List<String> strings(String key) {
        List<String> out = new ArrayList<>();
        for (Object value : array(key)) out.add(String.valueOf(value));
        return out;
    }

    public Map<String, Object> values() { return new LinkedHashMap<>(values); }

    /* ------------------------------------------------------------ the wire */

    public byte[] toBytes() {
        return Plist.toBytes(values);
    }

    public static Message parse(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) throw new IOException("an empty message");
        return new Message(Plist.readDictionary(bytes));
    }

    @Override public String toString() {
        return "message " + type() + " " + values;
    }
}
