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

/**
 * Text.
 *
 * The runtime has a string of its own and this is written on top of it, which is worth
 * saying plainly rather than hiding: the point is not to store characters differently. The
 * point is that a program written against this system has one vocabulary. A program that
 * takes an {@link FMURL} from the file manager and asks it for its last component gets an
 * FMString back, and never has to know which of the runtime's own types that turned into.
 *
 * So this is deliberately small. It does what text does here: compares without regard to
 * case where a file system would, joins, splits, trims. Where a program needs the
 * runtime's own string, {@link #toString} hands it over without ceremony.
 */
public final class FMString implements Comparable<FMString>, CharSequence {

    public static final FMString EMPTY = new FMString("");

    private final String text;

    private FMString(String text) {
        this.text = text;
    }

    public static FMString of(String text) {
        return text == null ? EMPTY : new FMString(text);
    }

    /** The text of anything at all, which is what a description or a control hands over. */
    public static FMString describing(Object what) {
        return what == null ? EMPTY : new FMString(String.valueOf(what));
    }

    /**
     * Text made to a pattern.
     *
     * A listing lines its columns up, a message has a number in the middle of it, and
     * doing either by joining pieces is how a program ends up with two spaces in one place
     * and none in another. Foundation has had this since it had text, and a platform
     * without it sends every program back to the runtime for the one thing it does most.
     */
    public static FMString withFormat(FMString pattern, Object... values) {
        Object[] plain = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            plain[i] = values[i] instanceof FMString text ? text.toString() : values[i];
        }
        return new FMString(String.format(pattern.toString(), plain));
    }

    public static FMString join(FMString separator, FMArray<FMString> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.count(); i++) {
            if (i > 0) sb.append(separator);
            sb.append(parts.at(i));
        }
        return new FMString(sb.toString());
    }

    @Override public int length() { return text.length(); }

    @Override public char charAt(int index) { return text.charAt(index); }

    @Override public CharSequence subSequence(int from, int to) {
        return new FMString(text.substring(from, to));
    }

    public boolean isEmpty() { return text.isEmpty(); }

    public boolean isBlank() { return text.isBlank(); }

    public FMString appending(FMString other) {
        return new FMString(text + (other == null ? "" : other.text));
    }

    public FMString trimmed() { return new FMString(text.trim()); }

    public FMString lowercase() { return new FMString(text.toLowerCase(java.util.Locale.ROOT)); }

    public FMString uppercase() { return new FMString(text.toUpperCase(java.util.Locale.ROOT)); }

    public boolean contains(FMString other) { return text.contains(other.text); }

    public boolean beginsWith(FMString other) { return text.startsWith(other.text); }

    public boolean endsWith(FMString other) { return text.endsWith(other.text); }

    /**
     * Whether two pieces of text are the same to a file system.
     *
     * The volumes this runs on do not tell one case from another in a file name, so asking
     * whether two names are equal usually means asking this rather than {@link #equals}.
     */
    public boolean sameAs(FMString other) {
        return other != null && text.equalsIgnoreCase(other.text);
    }

    /**
     * Every occurrence of one piece of text swapped for another.
     *
     * What asks for this is a sentence out of a strings file with a blank in it. The blank
     * is a marked place rather than a pattern, so it is matched as the text it is: a
     * translator writing a sentence has no reason to know what a regular expression would
     * make of the characters they typed.
     */
    public FMString replacing(FMString what, FMString with) {
        if (what == null || what.isEmpty()) return this;
        return new FMString(text.replace(what.text, with == null ? "" : with.text));
    }

    public FMArray<FMString> split(FMString separator) {
        FMArray<FMString> out = FMArray.empty();
        for (String part : text.split(java.util.regex.Pattern.quote(separator.text), -1)) {
            out = out.appending(new FMString(part));
        }
        return out;
    }

    /** The characters as they would be written to a file, in the encoding everything uses. */
    public FMData asData() {
        return FMData.of(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override public int compareTo(FMString other) { return text.compareTo(other.text); }

    @Override public boolean equals(Object other) {
        return other instanceof FMString s && text.equals(s.text);
    }

    @Override public int hashCode() { return text.hashCode(); }

    @Override public String toString() { return text; }
}
