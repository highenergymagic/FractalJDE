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

/** Prints what the native layer reports. Run with --native-report. */
public final class Probe {
    private Probe() {}

    public static void report(java.io.PrintStream out) {
        out.println("drives:");
        for (String root : Kernel32.logicalDrives()) {
            long[] space = Kernel32.diskSpace(root);
            out.printf("  %s type=%d label=%-16s fs=%-6s total=%d free=%d%n",
                root, Kernel32.driveType(root), Kernel32.volumeLabel(root),
                Kernel32.fileSystem(root), space[0], space[1]);
        }
        out.println("memory bytes: " + Kernel32.totalMemory());
        out.println("physical cores: " + Kernel32.physicalCores());
        out.println("logical cores: " + Runtime.getRuntime().availableProcessors());
        out.println("cpu name: " + Registry.string(Registry.HKEY_LOCAL_MACHINE,
            "HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", "ProcessorNameString"));
        out.println("cpu MHz: " + Registry.dword(Registry.HKEY_LOCAL_MACHINE,
            "HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", "~MHz", 0));
        String browser = Registry.string(Registry.HKEY_CURRENT_USER,
            "Software\\Microsoft\\Windows\\Shell\\Associations\\UrlAssociations\\http\\UserChoice",
            "ProgId");
        out.println("browser progid: " + browser);
        out.println("browser command: " + Registry.string(Registry.HKEY_CLASSES_ROOT,
            browser + "\\shell\\open\\command", null));
        long[] bin = Shell32.recycleBinInfo();
        out.println("recycle bin: " + bin[0] + " items, " + bin[1] + " bytes");
    }
}
