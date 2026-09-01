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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A resource fork, in the format resource forks have always been in.
 *
 *   header (16 bytes)   offset to the data, offset to the map, their two lengths
 *   data                each resource as a four byte length then its bytes
 *   map                 a copy of the header, then the type list and the reference lists
 *
 * The type list is a count, then one entry per type: the four character type, how many
 * of that type there are, and where its reference list starts. Each reference is a
 * resource id, an offset into the name list, one byte of attributes, and a three byte
 * offset into the data.
 *
 * On this system the fork is kept in the AFP_Resource stream, which is where a Mac
 * writing to a volume like this one puts it.
 */
public final class ResourceFork {

    public static final String STREAM = "AFP_Resource";

    private static final int HEADER_SIZE = 256;   // the header is padded out to 256
    private static final int MAP_HEADER = 28;

    /** The resources, by type then by id. */
    private final Map<String, Map<Integer, byte[]>> resources = new LinkedHashMap<>();

    public Map<String, Map<Integer, byte[]>> resources() { return resources; }

    public byte[] get(String type, int id) {
        Map<Integer, byte[]> ofType = resources.get(type);
        return ofType == null ? null : ofType.get(id);
    }

    public ResourceFork put(String type, int id, byte[] bytes) {
        resources.computeIfAbsent(type, t -> new LinkedHashMap<>()).put(id, bytes);
        return this;
    }

    public List<String> types() { return new ArrayList<>(resources.keySet()); }

    public boolean isEmpty() { return resources.isEmpty(); }

    /* ------------------------------------------------------------- writing */

    public byte[] toBytes() {
        // The data comes first, each resource preceded by its length.
        java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream();
        Map<String, Map<Integer, Integer>> offsets = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Integer, byte[]>> byType : resources.entrySet()) {
            Map<Integer, Integer> here = new LinkedHashMap<>();
            for (Map.Entry<Integer, byte[]> resource : byType.getValue().entrySet()) {
                here.put(resource.getKey(), data.size());
                byte[] bytes = resource.getValue();
                data.write((bytes.length >> 24) & 0xFF);
                data.write((bytes.length >> 16) & 0xFF);
                data.write((bytes.length >> 8) & 0xFF);
                data.write(bytes.length & 0xFF);
                data.write(bytes, 0, bytes.length);
            }
            offsets.put(byType.getKey(), here);
        }

        int typeCount = resources.size();
        int referenceCount = resources.values().stream().mapToInt(Map::size).sum();
        int typeListSize = 2 + (typeCount * 8);
        int referenceListSize = referenceCount * 12;
        int mapSize = MAP_HEADER + typeListSize + referenceListSize;

        byte[] dataBytes = data.toByteArray();
        ByteBuffer out = ByteBuffer.allocate(HEADER_SIZE + dataBytes.length + mapSize)
                                   .order(ByteOrder.BIG_ENDIAN);
        out.putInt(HEADER_SIZE);                          // where the data starts
        out.putInt(HEADER_SIZE + dataBytes.length);       // where the map starts
        out.putInt(dataBytes.length);
        out.putInt(mapSize);
        out.position(HEADER_SIZE);
        out.put(dataBytes);

        int mapAt = HEADER_SIZE + dataBytes.length;
        out.position(mapAt);
        out.putInt(HEADER_SIZE);                          // the header, copied
        out.putInt(mapAt);
        out.putInt(dataBytes.length);
        out.putInt(mapSize);
        out.putInt(0);                                    // the next map, of which there is none
        out.putShort((short) 0);                          // the file reference
        out.putShort((short) 0);                          // the fork's attributes
        out.putShort((short) MAP_HEADER);                 // where the type list starts
        out.putShort((short) (MAP_HEADER + typeListSize + referenceListSize));  // the names

        out.putShort((short) (typeCount - 1));
        int referenceAt = typeListSize;
        for (Map.Entry<String, Map<Integer, byte[]>> byType : resources.entrySet()) {
            out.put(osType(byType.getKey()));
            out.putShort((short) (byType.getValue().size() - 1));
            out.putShort((short) referenceAt);
            referenceAt += byType.getValue().size() * 12;
        }
        for (Map.Entry<String, Map<Integer, byte[]>> byType : resources.entrySet()) {
            for (Map.Entry<Integer, byte[]> resource : byType.getValue().entrySet()) {
                out.putShort((short) (int) resource.getKey());
                out.putShort((short) -1);                 // no name
                int offset = offsets.get(byType.getKey()).get(resource.getKey());
                out.put((byte) 0);                        // attributes
                out.put((byte) ((offset >> 16) & 0xFF));
                out.put((byte) ((offset >> 8) & 0xFF));
                out.put((byte) (offset & 0xFF));
                out.putInt(0);                            // the handle, always zero on disk
            }
        }
        return out.array();
    }

    /* ------------------------------------------------------------- reading */

    public static ResourceFork parse(byte[] bytes) {
        ResourceFork fork = new ResourceFork();
        if (bytes == null || bytes.length < 16) return fork;
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int dataAt = b.getInt(0);
        int mapAt = b.getInt(4);
        int dataLength = b.getInt(8);
        if (mapAt < 0 || mapAt + MAP_HEADER > bytes.length) return fork;

        int typeListAt = mapAt + (b.getShort(mapAt + 24) & 0xFFFF);
        if (typeListAt + 2 > bytes.length) return fork;
        int typeCount = (b.getShort(typeListAt) & 0xFFFF) + 1;

        for (int i = 0; i < typeCount; i++) {
            int entry = typeListAt + 2 + (i * 8);
            if (entry + 8 > bytes.length) break;
            String type = new String(bytes, entry, 4, StandardCharsets.US_ASCII);
            int count = (b.getShort(entry + 4) & 0xFFFF) + 1;
            int referenceAt = typeListAt + (b.getShort(entry + 6) & 0xFFFF);
            for (int j = 0; j < count; j++) {
                int reference = referenceAt + (j * 12);
                if (reference + 12 > bytes.length) break;
                int id = b.getShort(reference);
                int offset = ((bytes[reference + 5] & 0xFF) << 16)
                           | ((bytes[reference + 6] & 0xFF) << 8)
                           | (bytes[reference + 7] & 0xFF);
                int at = dataAt + offset;
                if (at + 4 > bytes.length) continue;
                int length = b.getInt(at);
                if (length < 0 || at + 4 + length > bytes.length
                    || length > dataLength) continue;
                byte[] resource = new byte[length];
                System.arraycopy(bytes, at + 4, resource, 0, length);
                fork.put(type, id, resource);
            }
        }
        return fork;
    }

    /* ---------------------------------------------------------- the stream */

    public static ResourceFork of(File file) {
        byte[] bytes = Streams.read(file, STREAM);
        if (bytes == null || bytes.length == 0) bytes = Sidecar.read(file, STREAM);
        return parse(bytes);
    }

    /** Answers whether the fork went into the file itself rather than the sidecar. */
    public boolean writeTo(File file) {
        byte[] bytes = toBytes();
        if (Streams.write(file, STREAM, bytes)) return true;
        Sidecar.write(file, STREAM, bytes);
        return false;
    }

    /** How big the fork on this file is, for Get Info to report. */
    public static long sizeOn(File file) {
        byte[] bytes = Streams.read(file, STREAM);
        if (bytes == null) bytes = Sidecar.read(file, STREAM);
        return bytes == null ? 0 : bytes.length;
    }

    private static byte[] osType(String type) {
        byte[] out = new byte[4];
        byte[] src = (type + "    ").getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(src, 0, out, 0, 4);
        return out;
    }
}
