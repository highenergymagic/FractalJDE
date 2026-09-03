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

import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMMutableDictionary;
import org.fractalmicro.foundation.FMString;

/**
 * A strings file: what the words are, in one language.
 *
 * A property list in the original ASCII form, where a top level dictionary needs no
 * braces, so
 *
 *     "Empty Trash" = "Papierkorb entleeren";
 *
 * is a dictionary of one entry. Comments are C comments; by convention the one before an
 * entry says what the words are for, which is what a translator works from.
 *
 * A missing key answers with itself, so an untranslated entry shows as English rather
 * than as a blank.
 */
public final class Strings {
    private Strings() {}

    /** What a strings file is called, when nothing says otherwise. */
    public static final FMString DEFAULT_TABLE = FMString.of("Localizable");

    /** The extension, which is also how the format is usually named. */
    public static final FMString EXTENSION = FMString.of("strings");

    /**
     * Reads a strings file.
     *
     * Anything it cannot make sense of is skipped rather than thrown. A strings file is
     * edited by people who are translating rather than programming, and one bad line
     * should cost that line and not the language.
     */
    public static FMDictionary parse(FMString text) {
        FMMutableDictionary out = FMMutableDictionary.empty();
        String source = text.toString();
        int at = 0;
        while (at < source.length()) {
            at = skipSpaceAndComments(source, at);
            if (at >= source.length()) break;
            if (source.charAt(at) != '"') {
                at = toNextLine(source, at);
                continue;
            }
            int[] key = quoted(source, at);
            if (key == null) break;
            int after = skipSpaceAndComments(source, key[1]);
            if (after >= source.length() || source.charAt(after) != '=') {
                at = toNextLine(source, key[1]);
                continue;
            }
            int valueAt = skipSpaceAndComments(source, after + 1);
            if (valueAt >= source.length() || source.charAt(valueAt) != '"') {
                at = toNextLine(source, valueAt);
                continue;
            }
            int[] value = quoted(source, valueAt);
            if (value == null) break;
            out.set(FMString.of(unescape(source.substring(at + 1, key[0]))),
                    FMString.of(unescape(source.substring(valueAt + 1, value[0]))));
            at = value[1];
        }
        return out.asDictionary();
    }

    /**
     * Writes one, with a comment above each entry saying what it is for.
     *
     * The comment is the whole reason the format has one. A translator given
     * "Open" = "Open"; has no way to know whether it is a verb on a button or the name of
     * a menu, and those are different words in most languages.
     */
    public static FMString write(FMDictionary table, FMDictionary comments) {
        StringBuilder sb = new StringBuilder();
        for (FMString key : table.keys()) {
            FMString about = comments.string(key);
            if (!about.isEmpty()) sb.append("/* ").append(about).append(" */\n");
            sb.append('"').append(escape(key.toString())).append("\" = \"")
              .append(escape(table.string(key).toString())).append("\";\n\n");
        }
        return FMString.of(sb.toString());
    }

    /* ---------------------------------------------------------------- reading */

    /** Where a quoted run ends, and where to carry on: {closing quote, after it}. */
    private static int[] quoted(String source, int open) {
        int i = open + 1;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '"') return new int[]{i, i + 1};
            i++;
        }
        return null;
    }

    private static int skipSpaceAndComments(String source, int at) {
        while (at < source.length()) {
            char c = source.charAt(at);
            if (Character.isWhitespace(c) || c == ';') {
                at++;
            } else if (source.startsWith("/*", at)) {
                int end = source.indexOf("*/", at + 2);
                at = end < 0 ? source.length() : end + 2;
            } else if (source.startsWith("//", at)) {
                at = toNextLine(source, at);
            } else {
                return at;
            }
        }
        return at;
    }

    private static int toNextLine(String source, int at) {
        int end = source.indexOf('\n', at);
        return end < 0 ? source.length() : end + 1;
    }

    private static String unescape(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\\' || i + 1 >= text.length()) {
                sb.append(c);
                continue;
            }
            char next = text.charAt(++i);
            switch (next) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'u' -> {
                    if (i + 4 < text.length()) {
                        sb.append((char) Integer.parseInt(text.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                }
                default -> sb.append(next);
            }
        }
        return sb.toString();
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                   .replace("\n", "\n").replace("\t", "\t");
    }
}
