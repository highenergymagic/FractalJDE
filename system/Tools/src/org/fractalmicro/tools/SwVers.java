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

import org.fractalmicro.os.OSPaths;
import org.fractalmicro.os.Version;
import org.fractalmicro.plist.Plist;

import java.util.Map;

/**
 * sw_vers: what system this is.
 *
 * Read out of SystemVersion.plist, where a Mac keeps it, rather than out of the program
 * asking. A volume says what it is; a tool that answered from its own build would say what
 * it was built from, which is a different question on a volume built by something else.
 */
public final class SwVers {
    private SwVers() {}

    public static final String PRODUCT_NAME = "ProductName";
    public static final String PRODUCT_VERSION = "ProductVersion";
    public static final String BUILD_VERSION = "ProductBuildVersion";

    /** Where the volume says what it is. */
    public static java.nio.file.Path file() {
        return OSPaths.coreServices().resolve("SystemVersion.plist");
    }

    public static void main(String[] args) {
        Map<String, Object> said = read();
        String only = args.length > 0 ? args[0] : "";
        switch (only) {
            case "-productName" -> System.out.println(said.get(PRODUCT_NAME));
            case "-productVersion" -> System.out.println(said.get(PRODUCT_VERSION));
            case "-buildVersion" -> System.out.println(said.get(BUILD_VERSION));
            case "" -> {
                System.out.println("ProductName:\t" + said.get(PRODUCT_NAME));
                System.out.println("ProductVersion:\t" + said.get(PRODUCT_VERSION));
                System.out.println("BuildVersion:\t" + said.get(BUILD_VERSION));
            }
            default -> {
                System.err.println("usage: sw_vers [-productName|-productVersion"
                                   + "|-buildVersion]");
                System.exit(64);
            }
        }
    }

    /** What the volume says, or what this build is when no volume has said. */
    private static Map<String, Object> read() {
        try {
            Map<String, Object> said = Plist.readDictionary(file());
            if (said.get(PRODUCT_VERSION) != null) return said;
        } catch (Exception noFile) {
            // A volume from before this file was written, or none at all.
        }
        return Map.of(PRODUCT_NAME, "FractalJDE",
                      PRODUCT_VERSION, Version.number(),
                      BUILD_VERSION, Version.build());
    }
}
