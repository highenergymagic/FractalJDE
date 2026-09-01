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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Reader for the bplist00 format, so a property list copied from a real Mac can be
 * dropped in and used. Writing is always done as XML, which round-trips fine because
 * both formats carry the same value types.
 *
 * This reads bytes that arrive over a port and bytes out of bundles that were not made
 * here, so it treats every field as something an attacker chose. A count is checked
 * against the size of the data before an array that big is asked for; an object being
 * read is remembered so a reference that points back at it is caught rather than followed
 * forever; and a length that runs past the end is refused. A property list is small; one
 * that claims to be enormous is not a property list.
 */
final class BinaryPlist {
    private BinaryPlist() {}

    /** Seconds between 1 January 1904 (Apple epoch) and 1 January 1970. */
    private static final long APPLE_EPOCH_OFFSET = 978307200L;

    static Object parse(byte[] data) throws IOException {
        if (data.length < 40) throw new IOException("binary plist too short");
        int trailer = data.length - 32;
        int offsetSize = data[trailer + 6] & 0xFF;
        int refSize = data[trailer + 7] & 0xFF;
        long count = readLong(data, trailer + 8, 8);
        long top = readLong(data, trailer + 16, 8);
        long tableOffset = readLong(data, trailer + 24, 8);

        // Nothing past here is trusted. An offset is one to eight bytes; there cannot be
        // more objects than the file has bytes; the table has to fit inside the file; and
        // the object it starts from has to be one that exists.
        if (offsetSize < 1 || offsetSize > 8 || refSize < 1 || refSize > 8) {
            throw new IOException("binary plist has an impossible offset or reference size");
        }
        if (count < 0 || count > data.length) {
            throw new IOException("binary plist claims more objects than it could hold");
        }
        if (tableOffset < 0 || tableOffset + count * offsetSize > data.length) {
            throw new IOException("binary plist offset table runs past the end");
        }
        if (top < 0 || top >= count) {
            throw new IOException("binary plist starts from an object that is not there");
        }

        long[] offsets = new long[(int) count];
        for (int i = 0; i < count; i++) {
            long where = readLong(data, (int) (tableOffset + (long) i * offsetSize), offsetSize);
            if (where < 0 || where >= data.length) {
                throw new IOException("binary plist points an object past the end");
            }
            offsets[i] = where;
        }
        return new Reader(data, offsets, refSize).object((int) top);
    }

    private static long readLong(byte[] d, int at, int size) {
        long v = 0;
        for (int i = 0; i < size; i++) v = (v << 8) | (d[at + i] & 0xFFL);
        return v;
    }

    private static final class Reader {
        private final byte[] d;
        private final long[] offsets;
        private final int refSize;
        // The objects currently being read, down the stack. A container whose reference
        // leads back to one of these is a cycle, and following it never ends.
        private final java.util.BitSet reading;

        Reader(byte[] d, long[] offsets, int refSize) {
            this.d = d;
            this.offsets = offsets;
            this.refSize = refSize;
            this.reading = new java.util.BitSet(offsets.length);
        }

        Object object(int index) throws IOException {
            if (index < 0 || index >= offsets.length) return null;
            if (reading.get(index)) throw new IOException("binary plist refers back to itself");
            int at = (int) offsets[index];
            if (at < 0 || at >= d.length) return null;
            int marker = d[at] & 0xFF;
            int type = marker >> 4;
            int info = marker & 0x0F;

            switch (type) {
                case 0x0:
                    if (info == 0) return null;
                    if (info == 8) return Boolean.FALSE;
                    if (info == 9) return Boolean.TRUE;
                    return null;
                case 0x1: {                                   // integer
                    int size = 1 << info;
                    return readLong(d, at + 1, size);
                }
                case 0x2: {                                   // real
                    int size = 1 << info;
                    long bits = readLong(d, at + 1, size);
                    return size == 4 ? (double) Float.intBitsToFloat((int) bits)
                                     : Double.longBitsToDouble(bits);
                }
                case 0x3: {                                   // date
                    double seconds = Double.longBitsToDouble(readLong(d, at + 1, 8));
                    return new Date((long) ((seconds + APPLE_EPOCH_OFFSET) * 1000));
                }
                case 0x4: {                                   // data
                    int[] cursor = {at};
                    int length = length(cursor, info);
                    ensureWithin(cursor[0], length);
                    return Arrays.copyOfRange(d, cursor[0], cursor[0] + length);
                }
                case 0x5: {                                   // ASCII string
                    int[] cursor = {at};
                    int length = length(cursor, info);
                    ensureWithin(cursor[0], length);
                    return new String(d, cursor[0], length, StandardCharsets.US_ASCII);
                }
                case 0x6: {                                   // UTF-16BE string
                    int[] cursor = {at};
                    int length = length(cursor, info);
                    ensureWithin(cursor[0], (long) length * 2);
                    return new String(d, cursor[0], length * 2, StandardCharsets.UTF_16BE);
                }
                case 0xA: case 0xC: {                         // array, set
                    int[] cursor = {at};
                    int length = length(cursor, info);
                    ensureWithin(cursor[0], (long) length * refSize);
                    reading.set(index);
                    // Do not preallocate to a length the file merely claims; grow into it.
                    List<Object> list = new ArrayList<>();
                    for (int i = 0; i < length; i++) {
                        list.add(object((int) readLong(d, cursor[0] + i * refSize, refSize)));
                    }
                    reading.clear(index);
                    return list;
                }
                case 0xD: {                                   // dictionary
                    int[] cursor = {at};
                    int length = length(cursor, info);
                    ensureWithin(cursor[0], 2L * length * refSize);
                    reading.set(index);
                    Map<String, Object> map = new LinkedHashMap<>();
                    int keysAt = cursor[0];
                    int valuesAt = keysAt + length * refSize;
                    for (int i = 0; i < length; i++) {
                        Object key = object((int) readLong(d, keysAt + i * refSize, refSize));
                        Object value = object((int) readLong(d, valuesAt + i * refSize, refSize));
                        map.put(String.valueOf(key), value);
                    }
                    reading.clear(index);
                    return map;
                }
                default:
                    return null;
            }
        }

        /** Reads the count that follows a marker, which may itself be an integer object. */
        private int length(int[] cursor, int info) throws IOException {
            int at = cursor[0];
            if (info != 0x0F) {
                cursor[0] = at + 1;
                return info;
            }
            if (at + 1 >= d.length) throw new IOException("length marker past the end");
            int sizeMarker = d[at + 1] & 0xFF;
            if ((sizeMarker >> 4) != 0x1) throw new IOException("bad length marker");
            int size = 1 << (sizeMarker & 0x0F);
            if (size < 1 || size > 4 || at + 2 + size > d.length) {
                throw new IOException("bad length size");
            }
            long value = readLong(d, at + 2, size);
            cursor[0] = at + 2 + size;
            if (value < 0 || value > d.length) throw new IOException("length larger than the file");
            return (int) value;
        }

        /** Refuses a run of bytes that would reach past the end of the data. */
        private void ensureWithin(int start, long count) throws IOException {
            if (start < 0 || count < 0 || start + count > d.length) {
                throw new IOException("binary plist value runs past the end");
            }
        }
    }
}
