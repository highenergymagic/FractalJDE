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
package org.fractalmicro.icns;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Reader for icon image files, the icns format.
 *
 * The file is "icns", a big-endian length, then a run of elements, each an OSType, a
 * big-endian length that counts its own header, and the data. Modern elements hold a
 * PNG; the older ones hold three run-length encoded colour channels with a separate
 * 8 bit mask element.
 */
public final class IcnsFile {

    /** One icon in the file. */
    public static final class Entry {
        public final String type;
        public final int size;
        public final byte[] data;

        Entry(String type, int size, byte[] data) {
            this.type = type;
            this.size = size;
            this.data = data;
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    private IcnsFile() {}

    public static IcnsFile read(Path file) throws IOException {
        return parse(Files.readAllBytes(file));
    }

    public static IcnsFile parse(byte[] bytes) throws IOException {
        if (bytes.length < 8 || !"icns".equals(type(bytes, 0))) {
            throw new IOException("not an icns file");
        }
        IcnsFile icns = new IcnsFile();
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int total = Math.min(b.getInt(4), bytes.length);
        int pos = 8;
        while (pos + 8 <= total) {
            String type = type(bytes, pos);
            int length = b.getInt(pos + 4);
            // Compared this way round because pos + length overflows for a length near
            // Integer.MAX_VALUE, and an overflowed sum passes a "> total" test.
            if (length < 8 || length > total - pos) break;
            byte[] data = Arrays.copyOfRange(bytes, pos + 8, pos + length);
            icns.entries.put(type, new Entry(type, sizeOf(type), data));
            pos += length;
        }
        return icns;
    }

    private static String type(byte[] bytes, int at) {
        return new String(bytes, at, 4, StandardCharsets.US_ASCII);
    }

    public Set<String> types() { return entries.keySet(); }

    /** The icon closest to the size asked for, scaled if nothing matches exactly. */
    public BufferedImage image(int wanted) {
        List<Entry> candidates = new ArrayList<>(entries.values());
        candidates.removeIf(e -> e.size <= 0);
        candidates.sort(Comparator.comparingInt(e -> scoreFor(e, wanted)));
        for (Entry e : candidates) {
            BufferedImage img = decode(e);
            if (img != null) return img;
        }
        return null;
    }

    /** Prefers an exact size, then a larger one to scale down, then anything. */
    private int scoreFor(Entry e, int wanted) {
        if (e.size == wanted) return 0;
        if (e.size > wanted) return e.size - wanted;
        return 1000 + (wanted - e.size);
    }

    private BufferedImage decode(Entry e) {
        try {
            if (isPngLike(e.data)) {
                return ImageIO.read(new ByteArrayInputStream(e.data));
            }
            if (isRle24(e.type)) {
                byte[] mask = maskFor(e.type);
                return decodeRle24(e.data, e.size, mask);
            }
            if (e.type.equals("ic04") || e.type.equals("ic05")) {
                return decodeArgb(e.data, e.size);
            }
        } catch (IOException | RuntimeException ignored) {
            // A chunk this reader cannot handle is not a reason to give up on the file.
        }
        return null;
    }

    private boolean isPngLike(byte[] data) {
        return data.length > 8
            && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G';
    }

    private boolean isRle24(String type) {
        return type.equals("is32") || type.equals("il32")
            || type.equals("ih32") || type.equals("it32");
    }

    private byte[] maskFor(String type) {
        String maskType;
        switch (type) {
            case "is32": maskType = "s8mk"; break;
            case "il32": maskType = "l8mk"; break;
            case "ih32": maskType = "h8mk"; break;
            case "it32": maskType = "t8mk"; break;
            default: return null;
        }
        Entry mask = entries.get(maskType);
        return mask == null ? null : mask.data;
    }

    /**
     * Three channels, each packed with the run-length scheme icns uses: a byte with
     * the high bit set means the next byte repeats, otherwise the byte counts literals.
     */
    private BufferedImage decodeRle24(byte[] data, int size, byte[] mask) {
        int pixels = size * size;
        int offset = 0;
        // 'it32' carries four zero bytes before the channels.
        if (data.length > 4 && data[0] == 0 && data[1] == 0 && data[2] == 0 && data[3] == 0
                && size == 128) {
            offset = 4;
        }
        byte[] channels = new byte[pixels * 3];
        int written = 0;
        int pos = offset;
        while (written < channels.length && pos < data.length) {
            int control = data[pos++] & 0xFF;
            if (control < 128) {
                int count = control + 1;
                for (int i = 0; i < count && pos < data.length && written < channels.length; i++) {
                    channels[written++] = data[pos++];
                }
            } else {
                int count = control - 125;
                if (pos >= data.length) break;
                byte value = data[pos++];
                for (int i = 0; i < count && written < channels.length; i++) {
                    channels[written++] = value;
                }
            }
        }

        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < pixels; i++) {
            int r = channels[i] & 0xFF;
            int g = channels[pixels + i] & 0xFF;
            int b = channels[pixels * 2 + i] & 0xFF;
            int a = mask != null && i < mask.length ? mask[i] & 0xFF : 0xFF;
            img.setRGB(i % size, i / size, (a << 24) | (r << 16) | (g << 8) | b);
        }
        return img;
    }

    /** ic04 and ic05: "ARGB" then four run-length encoded channels. */
    private BufferedImage decodeArgb(byte[] data, int size) {
        if (data.length < 4 || data[0] != 'A' || data[1] != 'R') return null;
        int pixels = size * size;
        byte[] channels = new byte[pixels * 4];
        int written = 0;
        int pos = 4;
        while (written < channels.length && pos < data.length) {
            int control = data[pos++] & 0xFF;
            if (control < 128) {
                int count = control + 1;
                for (int i = 0; i < count && pos < data.length && written < channels.length; i++) {
                    channels[written++] = data[pos++];
                }
            } else {
                int count = control - 125;
                if (pos >= data.length) break;
                byte value = data[pos++];
                for (int i = 0; i < count && written < channels.length; i++) {
                    channels[written++] = value;
                }
            }
        }
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < pixels; i++) {
            int a = channels[i] & 0xFF;
            int r = channels[pixels + i] & 0xFF;
            int g = channels[pixels * 2 + i] & 0xFF;
            int b = channels[pixels * 3 + i] & 0xFF;
            img.setRGB(i % size, i / size, (a << 24) | (r << 16) | (g << 8) | b);
        }
        return img;
    }

    /** The pixel size each OSType stands for. */
    private static int sizeOf(String type) {
        switch (type) {
            case "icp4": case "is32": case "ics#": case "ics8": case "ic04": return 16;
            case "icp5": case "il32": case "icl8": case "ic11": case "ic05": return 32;
            case "icp6": case "ic12": return 64;
            case "ich#": case "ih32": case "ic07": return 128;
            case "it32": return 128;
            case "ic08": return 256;
            case "ic13": return 256;
            case "ic09": return 512;
            case "ic14": return 512;
            case "ic10": return 1024;
            case "icsb": return 18;
            default: return 0;
        }
    }
}
