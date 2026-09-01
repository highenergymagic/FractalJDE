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
package org.fractalmicro.plist;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Apple property lists, read and written in the XML format Apple documents
 * (PUBLIC "-//Apple//DTD PLIST 1.0//EN"). Values map onto Java as:
 *
 *   dict    -> LinkedHashMap&lt;String,Object&gt;   array -> List&lt;Object&gt;
 *   string  -> String                             integer -> Long
 *   real    -> Double                             true/false -> Boolean
 *   date    -> Date                               data -> byte[]
 *
 * Written files keep key order, so they stay readable by hand.
 */
public final class Plist {
    private Plist() {}

    private static final String HEADER =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" "
      + "\"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n";

    /* ------------------------------------------------------------- reading */

    @SuppressWarnings("unchecked")
    public static Map<String, Object> readDictionary(Path file) throws IOException {
        Object root = read(file);
        return root instanceof Map ? (Map<String, Object>) root : new LinkedHashMap<>();
    }

    public static Object read(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length >= 8 && new String(bytes, 0, 6, StandardCharsets.US_ASCII).equals("bplist")) {
            return BinaryPlist.parse(bytes);
        }
        return new XmlParser(new String(bytes, StandardCharsets.UTF_8)).parse();
    }

    /** Reads a property list from bytes, which is how one arrives over a connection. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> readDictionary(byte[] bytes) throws IOException {
        Object root = parse(bytes);
        return root instanceof Map ? (Map<String, Object>) root : new LinkedHashMap<>();
    }

    public static Object parse(byte[] bytes) throws IOException {
        if (bytes.length >= 8
                && new String(bytes, 0, 6, StandardCharsets.US_ASCII).equals("bplist")) {
            return BinaryPlist.parse(bytes);
        }
        return new XmlParser(new String(bytes, StandardCharsets.UTF_8)).parse();
    }

    /** Writes a property list to bytes, which is how one is sent over a connection. */
    public static byte[] toBytes(Object root) {
        StringBuilder sb = new StringBuilder(HEADER);
        sb.append("<plist version=\"1.0\">\n");
        writeValue(sb, root, 0);
        sb.append("</plist>\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /* ------------------------------------------------------------- writing */

    /**
     * Writes a property list, given as what a property list is.
     *
     * A property list is values kept under names, which is a dictionary. Taking one means
     * the caller never assembles a map of the runtime's and hands it over hoping the keys
     * are the right kind of thing.
     */
    public static void write(Path file, org.fractalmicro.foundation.FMDictionary root)
            throws IOException {
        write(file, root.asMap());
    }

    /** One read back, as a dictionary. */
    public static org.fractalmicro.foundation.FMDictionary dictionary(Path file) throws IOException {
        return org.fractalmicro.foundation.FMDictionary.fromMap(readDictionary(file));
    }

    public static void write(Path file, Object root) throws IOException {
        StringBuilder sb = new StringBuilder(HEADER);
        sb.append("<plist version=\"1.0\">\n");
        writeValue(sb, root, 0);
        sb.append("</plist>\n");
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, sb.toString().getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(StringBuilder sb, Object value, int depth) {
        String pad = "\t".repeat(depth);
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            if (map.isEmpty()) { sb.append(pad).append("<dict/>\n"); return; }
            sb.append(pad).append("<dict>\n");
            for (Map.Entry<String, Object> e : map.entrySet()) {
                sb.append(pad).append("\t<key>").append(escape(e.getKey())).append("</key>\n");
                writeValue(sb, e.getValue(), depth + 1);
            }
            sb.append(pad).append("</dict>\n");
        } else if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            if (list.isEmpty()) { sb.append(pad).append("<array/>\n"); return; }
            sb.append(pad).append("<array>\n");
            for (Object o : list) writeValue(sb, o, depth + 1);
            sb.append(pad).append("</array>\n");
        } else if (value instanceof Boolean) {
            sb.append(pad).append((Boolean) value ? "<true/>\n" : "<false/>\n");
        } else if (value instanceof Integer || value instanceof Long || value instanceof Short) {
            sb.append(pad).append("<integer>").append(value).append("</integer>\n");
        } else if (value instanceof Double || value instanceof Float) {
            sb.append(pad).append("<real>").append(value).append("</real>\n");
        } else if (value instanceof Date) {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            sb.append(pad).append("<date>").append(f.format((Date) value)).append("</date>\n");
        } else if (value instanceof byte[]) {
            sb.append(pad).append("<data>")
              .append(Base64.getEncoder().encodeToString((byte[]) value))
              .append("</data>\n");
        } else {
            sb.append(pad).append("<string>").append(escape(String.valueOf(value))).append("</string>\n");
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /* -------------------------------------------------------- xml parsing */

    /**
     * A small hand-written parser. Using the JDK's XML stack would pull in a DTD
     * fetch over the network for Apple's doctype, which is not wanted here.
     */
    private static final class XmlParser {
        // A property list nests as deep as a person nests folders, not deeper. A file that
        // opens a thousand arrays inside each other is not describing anything; it is trying
        // to run the parser out of stack, and it arrives the same way over a port as on disk.
        private static final int MAX_DEPTH = 100;

        private final String xml;
        private int pos;
        private int depth;

        XmlParser(String xml) { this.xml = xml; }

        Object parse() throws IOException {
            skipTo("<plist");
            skipPast(">");
            Object value = readValue();
            return value == null ? new LinkedHashMap<String, Object>() : value;
        }

        private Object readValue() throws IOException {
            if (depth > MAX_DEPTH) throw new IOException("property list nested too deep");
            String tag = nextTag();
            if (tag == null) return null;
            switch (tag) {
                case "dict": return readDict();
                case "array": return readArray();
                case "dict/": return new LinkedHashMap<String, Object>();
                case "array/": return new ArrayList<>();
                case "true/": return Boolean.TRUE;
                case "false/": return Boolean.FALSE;
                case "string": return unescape(readUntilClose("string"));
                case "key": return unescape(readUntilClose("key"));
                case "integer": return Long.parseLong(readUntilClose("integer").trim());
                case "real": return Double.parseDouble(readUntilClose("real").trim());
                case "data": return Base64.getMimeDecoder().decode(readUntilClose("data").trim());
                case "date": return parseDate(readUntilClose("date").trim());
                case "/plist": return null;
                default: return null;
            }
        }

        private Map<String, Object> readDict() throws IOException {
            depth++;
            try {
                return readDictBody();
            } finally {
                depth--;
            }
        }

        private Map<String, Object> readDictBody() throws IOException {
            Map<String, Object> map = new LinkedHashMap<>();
            while (true) {
                int save = pos;
                String tag = nextTag();
                if (tag == null || tag.equals("/dict")) return map;
                if (!tag.equals("key")) { pos = save; return map; }
                String key = unescape(readUntilClose("key"));
                Object value = readValue();
                map.put(key, value);
            }
        }

        private List<Object> readArray() throws IOException {
            depth++;
            try {
                return readArrayBody();
            } finally {
                depth--;
            }
        }

        private List<Object> readArrayBody() throws IOException {
            List<Object> list = new ArrayList<>();
            while (true) {
                int save = pos;
                String tag = peekTag();
                if (tag == null || tag.equals("/array")) { nextTag(); return list; }
                pos = save;
                Object value = readValue();
                if (value == null) return list;
                list.add(value);
            }
        }

        private String peekTag() {
            int save = pos;
            String tag = nextTag();
            pos = save;
            return tag;
        }

        private String nextTag() {
            while (pos < xml.length() && xml.charAt(pos) != '<') pos++;
            if (pos >= xml.length()) return null;
            int end = xml.indexOf('>', pos);
            if (end < 0) return null;
            String tag = xml.substring(pos + 1, end).trim();
            pos = end + 1;
            if (tag.startsWith("!") || tag.startsWith("?")) return nextTag();
            if (tag.endsWith("/")) return tag.substring(0, tag.length() - 1).trim() + "/";
            return tag;
        }

        private String readUntilClose(String tag) throws IOException {
            int end = xml.indexOf("</" + tag + ">", pos);
            if (end < 0) throw new IOException("unterminated <" + tag + ">");
            String body = xml.substring(pos, end);
            pos = end + tag.length() + 3;
            return body;
        }

        private void skipTo(String needle) throws IOException {
            int at = xml.indexOf(needle, pos);
            if (at < 0) throw new IOException("not a plist: no " + needle);
            pos = at;
        }

        private void skipPast(String needle) throws IOException {
            int at = xml.indexOf(needle, pos);
            if (at < 0) throw new IOException("malformed plist");
            pos = at + needle.length();
        }

        private String unescape(String s) {
            return s.replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
        }

        private Date parseDate(String s) {
            try {
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                return f.parse(s);
            } catch (Exception e) {
                return new Date(0);
            }
        }
    }
}
