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
package org.fractalmicro.alias;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An alias record: what an alias file knows about the thing it points at.
 *
 * This is the version 2 record the Alias Manager defines. The fixed part is 150 bytes,
 * everything big-endian, and after it come tagged entries of any length, ending with a
 * tag of -1:
 *
 *   0    user type                     4
 *   4    record size                   2
 *   6    version                       2   (2)
 *   8    kind                          2   (0 a file, 1 a folder)
 *   10   volume name                   28  (a Pascal string)
 *   38   volume created                4   (seconds since 1904)
 *   42   volume signature              2
 *   44   volume type                   2
 *   46   parent directory id           4
 *   50   file name                     64  (a Pascal string)
 *   114  file number                   4
 *   118  file created                  4
 *   122  file type                     4
 *   126  file creator                  4
 *   130  levels from, levels to        4
 *   134  volume attributes             4
 *   138  volume file system id         2
 *   140  reserved                      10
 *
 * The path is one entry among several; the file number and the parent are others. See
 * {@link Alias} for the order they are tried in.
 */
public final class AliasRecord {

    public static final int VERSION = 2;
    public static final int KIND_FILE = 0;
    public static final int KIND_FOLDER = 1;

    private static final int FIXED_SIZE = 150;

    /** The tags this system writes and reads. */
    public static final int TAG_PARENT_NAME = 0;
    public static final int TAG_DIRECTORY_NAME = 1;
    public static final int TAG_ABSOLUTE_PATH = 2;
    public static final int TAG_UNICODE_NAME = 14;
    public static final int TAG_UNICODE_VOLUME_NAME = 15;
    public static final int TAG_POSIX_PATH = 18;
    public static final int TAG_POSIX_VOLUME_PATH = 19;
    /** This system's own: the file reference number the volume keeps. */
    public static final int TAG_FILE_REFERENCE = 0x4652;    // 'FR'
    public static final int TAG_END = -1;

    /** Seconds between 1904, which the record counts from, and 1970, which Java does. */
    private static final long EPOCH_OFFSET = 2082844800L;

    public int kind = KIND_FILE;
    public String volumeName = "";
    public long volumeCreated;
    public int volumeSignature = 0x4244;      // 'BD'
    public int volumeType = 5;                // a foreign file system
    public long parentDirectoryId;
    public String fileName = "";
    public long fileNumber;
    public long fileCreated;
    public String fileType = "";
    public String fileCreator = "";
    public int levelsFrom = -1;
    public int levelsTo = -1;
    public long volumeAttributes;
    public int volumeFileSystemId;

    private final Map<Integer, byte[]> entries = new LinkedHashMap<>();

    public AliasRecord entry(int tag, byte[] value) {
        entries.put(tag, value);
        return this;
    }

    public AliasRecord entry(int tag, String value) {
        return entry(tag, value.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] entry(int tag) { return entries.get(tag); }

    public String string(int tag) {
        byte[] value = entries.get(tag);
        return value == null ? null : new String(value, StandardCharsets.UTF_8);
    }

    public Map<Integer, byte[]> entries() { return entries; }

    /* ------------------------------------------------------------- writing */

    public byte[] toBytes() {
        java.io.ByteArrayOutputStream tail = new java.io.ByteArrayOutputStream();
        for (Map.Entry<Integer, byte[]> e : entries.entrySet()) {
            byte[] value = e.getValue();
            tail.write((e.getKey() >> 8) & 0xFF);
            tail.write(e.getKey() & 0xFF);
            tail.write((value.length >> 8) & 0xFF);
            tail.write(value.length & 0xFF);
            tail.write(value, 0, value.length);
            if (value.length % 2 == 1) tail.write(0);     // entries sit on even boundaries
        }
        tail.write(0xFF);
        tail.write(0xFF);
        tail.write(0);
        tail.write(0);

        byte[] tailBytes = tail.toByteArray();
        int size = FIXED_SIZE + tailBytes.length;
        ByteBuffer b = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
        b.putInt(0);                                       // no user type
        b.putShort((short) size);
        b.putShort((short) VERSION);
        b.putShort((short) kind);
        b.put(pascal(volumeName, 28));
        b.putInt((int) toMacTime(volumeCreated));
        b.putShort((short) volumeSignature);
        b.putShort((short) volumeType);
        b.putInt((int) parentDirectoryId);
        b.put(pascal(fileName, 64));
        b.putInt((int) fileNumber);
        b.putInt((int) toMacTime(fileCreated));
        b.put(osType(fileType));
        b.put(osType(fileCreator));
        b.putShort((short) levelsFrom);
        b.putShort((short) levelsTo);
        b.putInt((int) volumeAttributes);
        b.putShort((short) volumeFileSystemId);
        b.put(new byte[10]);
        b.put(tailBytes);
        return b.array();
    }

    /* ------------------------------------------------------------- reading */

    public static AliasRecord parse(byte[] bytes) {
        if (bytes == null || bytes.length < FIXED_SIZE) return null;
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int version = b.getShort(6) & 0xFFFF;
        if (version != VERSION) return null;

        AliasRecord record = new AliasRecord();
        record.kind = b.getShort(8) & 0xFFFF;
        record.volumeName = pascalString(bytes, 10, 28);
        record.volumeCreated = fromMacTime(b.getInt(38) & 0xFFFFFFFFL);
        record.volumeSignature = b.getShort(42) & 0xFFFF;
        record.volumeType = b.getShort(44) & 0xFFFF;
        record.parentDirectoryId = b.getInt(46) & 0xFFFFFFFFL;
        record.fileName = pascalString(bytes, 50, 64);
        record.fileNumber = b.getInt(114) & 0xFFFFFFFFL;
        record.fileCreated = fromMacTime(b.getInt(118) & 0xFFFFFFFFL);
        record.fileType = osTypeString(bytes, 122);
        record.fileCreator = osTypeString(bytes, 126);
        record.levelsFrom = b.getShort(130);
        record.levelsTo = b.getShort(132);
        record.volumeAttributes = b.getInt(134) & 0xFFFFFFFFL;
        record.volumeFileSystemId = b.getShort(138) & 0xFFFF;

        int at = FIXED_SIZE;
        while (at + 4 <= bytes.length) {
            int tag = b.getShort(at);
            int length = b.getShort(at + 2) & 0xFFFF;
            if (tag == TAG_END) break;
            if (at + 4 + length > bytes.length) break;
            byte[] value = new byte[length];
            System.arraycopy(bytes, at + 4, value, 0, length);
            record.entries.put(tag, value);
            at += 4 + length + (length % 2);
        }
        return record;
    }

    /* -------------------------------------------------------------- pieces */

    private static byte[] pascal(String text, int size) {
        byte[] out = new byte[size];
        byte[] src = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        int length = Math.min(src.length, size - 1);
        out[0] = (byte) length;
        System.arraycopy(src, 0, out, 1, length);
        return out;
    }

    private static String pascalString(byte[] bytes, int at, int size) {
        int length = Math.min(bytes[at] & 0xFF, size - 1);
        return new String(bytes, at + 1, length, StandardCharsets.UTF_8);
    }

    private static byte[] osType(String type) {
        byte[] out = new byte[4];
        byte[] src = ((type == null ? "" : type) + "\0\0\0\0")
            .getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(src, 0, out, 0, 4);
        return out;
    }

    private static String osTypeString(byte[] bytes, int at) {
        return new String(bytes, at, 4, StandardCharsets.US_ASCII).replace("\0", "").trim();
    }

    /** Java counts seconds from 1970; this record counts them from 1904. */
    static long toMacTime(long javaSeconds) {
        return javaSeconds <= 0 ? 0 : javaSeconds + EPOCH_OFFSET;
    }

    static long fromMacTime(long macSeconds) {
        return macSeconds <= 0 ? 0 : macSeconds - EPOCH_OFFSET;
    }

    @Override public String toString() {
        return "alias to " + (kind == KIND_FOLDER ? "folder " : "file ") + fileName
             + " on " + volumeName + ", file number " + fileNumber;
    }
}
