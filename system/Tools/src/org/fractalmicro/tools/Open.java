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

import org.fractalmicro.bundle.Bundle;
import org.fractalmicro.bundle.Bundles;
import org.fractalmicro.bundle.LaunchServices;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * open: hand something to whatever should have it.
 *
 * The same question the Finder asks when a file is double-clicked, asked from a command
 * line. With -a it is asked of a named program instead, which is Open With.
 */
public final class Open {
    private Open() {}

    public static void main(String[] args) {
        // What is installed is read off the volume, because this process has just started
        // and knows nothing. Without it every file falls through to the host system, which
        // looks like working and is a different program opening it.
        Bundles.scan();

        String program = null;
        List<File> files = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if ("-a".equals(args[i]) && i + 1 < args.length) {
                program = args[++i];
            } else {
                files.add(new File(args[i]).getAbsoluteFile());
            }
        }
        if (files.isEmpty() && program == null) {
            System.err.println("usage: open [-a program] file ...");
            System.exit(64);
        }

        if (program != null) {
            Bundle named = Bundles.byName(program);
            if (named == null) named = Bundles.byIdentifier(program);
            if (named == null) {
                System.err.println("open: no program called " + program);
                System.exit(66);
            }
            if (!Bundles.openFiles(named.identifier().toString(), files)) {
                System.err.println("open: " + program + " would not start");
                System.exit(70);
            }
            return;
        }

        int failed = 0;
        for (File one : files) {
            if (!one.exists()) {
                System.err.println("open: " + one + ": no such file");
                failed++;
            } else if (!LaunchServices.open(one)) {
                System.err.println("open: nothing here opens " + one);
                failed++;
            }
        }
        if (failed > 0) System.exit(failed);
    }
}
