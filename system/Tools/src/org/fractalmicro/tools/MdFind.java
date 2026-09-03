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
package org.fractalmicro.tools;

import org.fractalmicro.foundation.FMString;
import org.fractalmicro.mds.Metadata;

/**
 * mdfind: what the index knows about, by word.
 *
 * One path a line, which is what makes it worth piping into something else. Nothing else
 * is printed: a tool that announced how many it found would need every caller to skip a
 * line before reading the first answer.
 */
public final class MdFind {
    private MdFind() {}

    public static void main(String[] args) {
        int limit = 100;
        StringBuilder wanted = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if ("-count".equals(args[i])) continue;
            if ("-limit".equals(args[i]) && i + 1 < args.length) {
                limit = readNumber(args[++i], limit);
                continue;
            }
            if (!wanted.isEmpty()) wanted.append(' ');
            wanted.append(args[i]);
        }
        if (wanted.isEmpty()) {
            System.err.println("usage: mdfind [-limit n] [-count] words");
            System.exit(64);
        }

        var hits = Metadata.search(FMString.of(wanted.toString()), limit);
        boolean counting = false;
        for (String one : args) if ("-count".equals(one)) counting = true;
        if (counting) {
            System.out.println(hits.count());
            return;
        }
        for (Metadata.Hit hit : hits) System.out.println(hit.file());
    }

    private static int readNumber(String said, int fallback) {
        try {
            return Integer.parseInt(said);
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }
}
