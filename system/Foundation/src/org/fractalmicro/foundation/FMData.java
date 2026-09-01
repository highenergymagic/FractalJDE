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
 * A run of bytes.
 *
 * What a file holds, what goes over a port, what a property list is written as. It copies
 * on the way in and on the way out, so nothing handed a piece of data can change it under
 * whoever handed it over.
 */
public final class FMData {

    public static final FMData EMPTY = new FMData(new byte[0]);

    private final byte[] bytes;

    private FMData(byte[] bytes) { this.bytes = bytes; }

    /**
     * Text as bytes.
     *
     * In UTF-8, because a file written in anything else is a file somebody else cannot
     * read, and because the argument for the alternatives ended some time ago.
     */
    public static FMData of(FMString text) {
        return of(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static FMData of(byte[] bytes) {
        return bytes == null || bytes.length == 0 ? EMPTY : new FMData(bytes.clone());
    }

    /** Everything in a file, or nothing at all if it could not be read. */
    public static FMData withContentsOf(FMURL url) {
        try {
            return of(java.nio.file.Files.readAllBytes(url.asPath()));
        } catch (java.io.IOException e) {
            return null;
        }
    }

    public int length() { return bytes.length; }

    public boolean isEmpty() { return bytes.length == 0; }

    public byte[] asBytes() { return bytes.clone(); }

    /** The bytes read as text in the encoding this system writes everything in. */
    public FMString asString() {
        return FMString.of(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Writes it, answering whether it went. */
    public boolean writeTo(FMURL url) {
        try {
            java.nio.file.Path p = url.asPath();
            if (p.getParent() != null) java.nio.file.Files.createDirectories(p.getParent());
            java.nio.file.Files.write(p, bytes);
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    @Override public boolean equals(Object other) {
        return other instanceof FMData d && java.util.Arrays.equals(bytes, d.bytes);
    }

    @Override public int hashCode() { return java.util.Arrays.hashCode(bytes); }

    @Override public String toString() { return "<" + bytes.length + " bytes>"; }
}
