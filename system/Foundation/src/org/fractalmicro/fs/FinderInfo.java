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

import org.fractalmicro.win.Streams;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Finder information: the type, the creator, the flags and the label colour a file
 * carries about itself.
 *
 * On a volume that is not the Mac's own file system, a Mac keeps this in a stream called
 * AFP_AfpInfo, in the structure the Apple Filing Protocol defines: a signature, a
 * version, a backup time, then the thirty two bytes of Finder info that used to live in
 * the catalogue. This writes the same structure into the same stream, so it means the
 * same thing to anything else that reads it.
 *
 *   0   signature  'AFP\0'
 *   4   version    0x00010000
 *   8   reserved
 *   12  backup time
 *   16  FInfo      type, creator, flags, position                      (16 bytes)
 *   32  FXInfo     icon id, script, comment id, folder                 (16 bytes)
 *   48  ProDOS info                                                    (6 bytes)
 *   54  reserved                                                       (6 bytes)
 *
 * The label lives in bits 1 to 3 of the flags, which is where the Finder has always kept
 * it, and an alias is the flag at 0x8000. A volume that cannot hold streams keeps the
 * same records in {@link Sidecar} instead, and nothing above here has to know which.
 */
public final class FinderInfo {

    public static final String STREAM = "AFP_AfpInfo";
    private static final int SIZE = 60;
    private static final int SIGNATURE = 0x41465000;      // 'AFP\0', big-endian
    private static final int VERSION = 0x00010000;
    private static final int FINDER_INFO_AT = 16;

    /** Flags, as the Finder defines them. */
    public static final int IS_ALIAS = 0x8000;
    public static final int IS_INVISIBLE = 0x4000;
    public static final int HAS_CUSTOM_ICON = 0x0400;
    public static final int IS_STATIONERY = 0x0800;
    public static final int NAME_LOCKED = 0x1000;
    public static final int IS_LOCKED = 0x0004;
    /** Bits 1 to 3: the label. */
    public static final int COLOR_MASK = 0x000E;

    private String type = "";
    private String creator = "";
    private int flags;
    private long backupTime;

    public String type() { return type; }
    public String creator() { return creator; }
    public int flags() { return flags; }

    public FinderInfo type(String value) { this.type = pad(value); return this; }
    public FinderInfo creator(String value) { this.creator = pad(value); return this; }

    public boolean isAlias() { return (flags & IS_ALIAS) != 0; }

    public FinderInfo alias(boolean yes) {
        flags = yes ? (flags | IS_ALIAS) : (flags & ~IS_ALIAS);
        return this;
    }

    public boolean hasCustomIcon() { return (flags & HAS_CUSTOM_ICON) != 0; }
    public boolean isInvisible() { return (flags & IS_INVISIBLE) != 0; }

    /** The label, 0 for none through 7, in the flag bits the Finder uses. */
    public int label() { return (flags & COLOR_MASK) >> 1; }

    public FinderInfo label(int value) {
        int clamped = Math.max(0, Math.min(7, value));
        flags = (flags & ~COLOR_MASK) | (clamped << 1);
        return this;
    }

    /* ------------------------------------------------------------ reading */

    /** What a file says about itself, or an empty record if it says nothing. */
    public static FinderInfo of(File file) {
        byte[] bytes = Streams.read(file, STREAM);
        if (bytes == null || bytes.length < SIZE) bytes = Sidecar.read(file, STREAM);
        FinderInfo info = new FinderInfo();
        if (bytes == null || bytes.length < SIZE) return info;

        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        if (b.getInt(0) != SIGNATURE) return info;
        info.backupTime = b.getInt(12) & 0xFFFFFFFFL;
        info.type = string(bytes, FINDER_INFO_AT);
        info.creator = string(bytes, FINDER_INFO_AT + 4);
        info.flags = b.getShort(FINDER_INFO_AT + 8) & 0xFFFF;
        return info;
    }

    /* ------------------------------------------------------------ writing */

    /**
     * Writes the record back. Answers whether it went into the file's own stream; if it
     * did not, it went to the sidecar, and the caller may want to say so.
     */
    public boolean writeTo(File file) {
        byte[] bytes = new byte[SIZE];
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        b.putInt(0, SIGNATURE);
        b.putInt(4, VERSION);
        b.putInt(12, (int) backupTime);
        put(bytes, FINDER_INFO_AT, type);
        put(bytes, FINDER_INFO_AT + 4, creator);
        b.putShort(FINDER_INFO_AT + 8, (short) flags);

        if (Streams.write(file, STREAM, bytes)) return true;
        Sidecar.write(file, STREAM, bytes);
        return false;
    }

    /** Clears everything this system wrote about a file. */
    public static void clear(File file) {
        Streams.remove(file, STREAM);
        Sidecar.remove(file, STREAM);
    }

    /* -------------------------------------------------------------- pieces */

    private static String string(byte[] bytes, int at) {
        String s = new String(bytes, at, 4, StandardCharsets.US_ASCII);
        return s.replace("\0", "").trim();
    }

    private static void put(byte[] bytes, int at, String value) {
        byte[] src = pad(value).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(src, 0, bytes, at, 4);
    }

    /** An OSType: four characters, space padded. */
    private static String pad(String value) {
        String s = value == null ? "" : value;
        if (s.length() > 4) s = s.substring(0, 4);
        return (s + "    ").substring(0, 4);
    }

    @Override public String toString() {
        return "type " + (type.isBlank() ? "none" : type)
             + ", creator " + (creator.isBlank() ? "none" : creator)
             + ", flags " + String.format("0x%04X", flags)
             + ", label " + label();
    }
}
