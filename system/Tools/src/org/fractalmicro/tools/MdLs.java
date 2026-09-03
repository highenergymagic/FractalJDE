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

import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMURL;
import org.fractalmicro.mds.Metadata;

import java.io.File;

/**
 * mdls: everything the index knows about one file.
 *
 * What was indexed, not what a fresh read would say. A file the index has never seen has
 * no attributes here, which is the true answer and not the same as having none.
 */
public final class MdLs {
    private MdLs() {}

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("usage: mdls file");
            System.exit(64);
        }
        File file = new File(args[0]).getAbsoluteFile();
        if (!file.exists()) {
            System.err.println("mdls: " + args[0] + ": no such file");
            System.exit(66);
        }
        if (!Metadata.running()) {
            System.err.println("mdls: the metadata server is not running");
            System.exit(69);
        }

        FMDictionary said = Metadata.attributesOf(FMURL.of(file));
        if (said.count() == 0) {
            System.out.println(file + ": no attributes; the index has not seen this file");
            return;
        }
        for (FMString key : said.keys()) {
            System.out.println(key + " = " + oneLine(said.value(key)));
        }
    }

    /** An attribute as one line, because a file full of newlines is still one attribute. */
    private static String oneLine(Object value) {
        String said = String.valueOf(value);
        return said.replace("\r", "").replace("\n", " ");
    }
}
