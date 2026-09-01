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
package org.fractalmicro.win;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Reader for Windows shortcut files, following the published shell link format
 * (MS-SHLLINK). Only what is needed to answer "what does this alias point at":
 * the LinkInfo local base path, falling back to the relative path in the string data.
 */
public final class LnkFile {
    private LnkFile() {}

    private static final int HAS_LINK_TARGET_ID_LIST = 1;
    private static final int HAS_LINK_INFO = 1 << 1;
    private static final int HAS_NAME = 1 << 2;
    private static final int HAS_RELATIVE_PATH = 1 << 3;
    private static final int IS_UNICODE = 1 << 7;

    /** The file a shortcut points at, or null if it cannot be worked out. */
    public static File target(File shortcut) {
        try {
            byte[] bytes = Files.readAllBytes(shortcut.toPath());
            if (bytes.length < 76) return null;
            ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if (b.getInt(0) != 0x0000004C) return null;

            int flags = b.getInt(20);
            int pos = 76;

            if ((flags & HAS_LINK_TARGET_ID_LIST) != 0) {
                int idListSize = Short.toUnsignedInt(b.getShort(pos));
                pos += 2 + idListSize;
            }

            String path = null;
            if ((flags & HAS_LINK_INFO) != 0 && pos + 24 <= bytes.length) {
                int start = pos;
                int linkInfoSize = b.getInt(start);
                int headerSize = b.getInt(start + 4);
                int localBasePathOffset = b.getInt(start + 16);
                int commonPathSuffixOffset = b.getInt(start + 20);

                if (headerSize >= 0x24 && start + 32 <= bytes.length) {
                    int unicodeBase = b.getInt(start + 28);
                    int unicodeSuffix = b.getInt(start + 32 - 4);
                    if (unicodeBase > 0) {
                        path = readWide(bytes, start + unicodeBase)
                             + (unicodeSuffix > 0 ? readWide(bytes, start + unicodeSuffix) : "");
                    }
                }
                if (path == null && localBasePathOffset > 0) {
                    path = readAnsi(bytes, start + localBasePathOffset)
                         + (commonPathSuffixOffset > 0 ? readAnsi(bytes, start + commonPathSuffixOffset) : "");
                }
                pos = start + linkInfoSize;
            }

            if (path == null || path.isBlank()) {
                path = relativePath(bytes, b, flags, pos, shortcut);
            }
            if (path == null || path.isBlank()) return null;

            File target = new File(path);
            return target.exists() ? target : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Walks the string data sections looking for RELATIVE_PATH. */
    private static String relativePath(byte[] bytes, ByteBuffer b, int flags, int pos, File shortcut) {
        boolean unicode = (flags & IS_UNICODE) != 0;
        if ((flags & HAS_NAME) != 0) pos = skipString(bytes, b, pos, unicode);
        if ((flags & HAS_RELATIVE_PATH) == 0) return null;
        if (pos + 2 > bytes.length) return null;
        int count = Short.toUnsignedInt(b.getShort(pos));
        pos += 2;
        String relative = unicode
            ? new String(bytes, pos, Math.min(count * 2, bytes.length - pos), StandardCharsets.UTF_16LE)
            : new String(bytes, pos, Math.min(count, bytes.length - pos), StandardCharsets.ISO_8859_1);
        try {
            return new File(shortcut.getParentFile(), relative).getCanonicalPath();
        } catch (IOException e) {
            return null;
        }
    }

    private static int skipString(byte[] bytes, ByteBuffer b, int pos, boolean unicode) {
        if (pos + 2 > bytes.length) return pos;
        int count = Short.toUnsignedInt(b.getShort(pos));
        return pos + 2 + (unicode ? count * 2 : count);
    }

    private static String readAnsi(byte[] bytes, int at) {
        if (at < 0 || at >= bytes.length) return "";
        int end = at;
        while (end < bytes.length && bytes[end] != 0) end++;
        return new String(bytes, at, end - at, StandardCharsets.ISO_8859_1);
    }

    private static String readWide(byte[] bytes, int at) {
        if (at < 0 || at + 1 >= bytes.length) return "";
        int end = at;
        while (end + 1 < bytes.length && !(bytes[end] == 0 && bytes[end + 1] == 0)) end += 2;
        return new String(bytes, at, end - at, StandardCharsets.UTF_16LE);
    }
}
