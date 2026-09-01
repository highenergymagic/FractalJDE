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
package org.fractalmicro.a11y;

import org.fractalmicro.fs.FS;
import org.fractalmicro.fs.Node;
import org.fractalmicro.windowserver.Desktop;
import org.fractalmicro.ui.InfoWindow;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * How big things are said to be.
 *
 * A volume is not sized, it is measured: it has a capacity, an amount left and an amount
 * used, and each is its own line. A file has one size. Get Info had been showing a volume
 * a made up line reading "2.0 TB (555 GB available)" as its size, and then showing the
 * capacity and the amount available again underneath: the same numbers twice, in two
 * shapes, one of which was not a shape this system uses anywhere else.
 *
 * Sizes are counted in thousands rather than units of 1024, as the system
 * being imitated changed to in the version this imitates. A drive sold as two terabytes
 * says two terabytes.
 */
public final class SizeTest {
    private SizeTest() {}

    public static int count() { return 10; }

    public static int run(Desktop desktop, PrintStream out) {
        int failures = 0;
        out.println();
        out.println("sizes:");

        /* ------------------------------------------------------- the counting */
        failures += check(out, "a thousand bytes is a kilobyte, not one thousand and twenty four",
            "1.0 KB".equals(FS.formatBytes(1000))
            && "2.00 TB".equals(FS.formatBytes(2_000_000_000_000L)));

        // The figures from real Get Info windows: two places for the large units and one
        // for the small, rather than rounding a disk to whole gigabytes.
        failures += check(out, "the figures are written the way Get Info writes them",
            "999.86 GB".equals(FS.formatBytes(999_860_000_000L))
            && "50.24 GB".equals(FS.formatBytes(50_240_000_000L))
            && "965.7 MB".equals(FS.formatBytes(965_700_000L)));

        failures += check(out, "the exact number carries its separators",
            "151,372,126,512 bytes".equals(FS.formatExactBytes(151_372_126_512L)));

        // Two different numbers, in the order Get Info writes them: what is in the file,
        // then what it takes up. The second is bigger for almost everything, because a
        // file lands on whole blocks.
        failures += check(out, "a size is the data, then the room it takes on the disk",
            "875,094,400 bytes (965.7 MB on disk)"
                .equals(FS.formatSize(875_094_400L, 965_700_000L)));

        failures += check(out, "a size with nothing known about the disk is just the data",
            "12 bytes".equals(FS.formatSize(12, -1)) && "1 byte".equals(FS.formatExactBytes(1)));

        java.io.File real = new java.io.File(System.getProperty("java.home"), "release");
        long onDisk = org.fractalmicro.win.Files32.allocatedSize(real);
        if (real.isFile()) {
            out.println("      " + real.getName() + " holds " + real.length()
                        + " bytes and takes " + onDisk + " on the disk");
        }
        failures += check(out, "the room a real file takes is asked of the file system",
            !real.isFile() || (onDisk >= real.length() && onDisk > 0));

        /* -------------------------------------------------- what Get Info shows */
        Node volume = org.fractalmicro.fs.Volumes.startupDisk();
        if (volume == null) {
            out.println("      no volume to look at");
            return failures + 4;
        }
        List<String> labels = labelsOf(new InfoWindow(volume));
        out.println("      a volume shows: " + String.join(", ", labels));

        failures += check(out, "a volume is measured, not sized",
            labels.contains("Capacity:") && labels.contains("Available:")
            && labels.contains("Used:"));
        failures += check(out, "and is not given a size line as well",
            !labels.contains("Size:"));

        Node file = FS.node(new java.io.File(System.getProperty("java.home"), "release"));
        List<String> fileLabels = file.file != null && file.file.isFile()
            ? labelsOf(new InfoWindow(file)) : List.of("Size:");
        out.println("      a file shows: " + String.join(", ", fileLabels));
        failures += check(out, "a file has one size and no capacity",
            fileLabels.contains("Size:") && !fileLabels.contains("Capacity:"));

        failures += check(out, "nothing is measured twice",
            labels.stream().distinct().count() == labels.size());

        out.println("      " + (failures == 0 ? "sizes are said once and said properly"
                                              : failures + " failed"));
        return failures;
    }

    /** The labels down the left of an info window, in the order they are shown. */
    private static List<String> labelsOf(InfoWindow window) {
        List<String> labels = new ArrayList<>();
        collect(window, labels);
        return labels;
    }

    private static void collect(Container root, List<String> labels) {
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label && label.getText() != null
                    && label.getText().endsWith(":")) {
                labels.add(label.getText());
            }
            if (child instanceof Container inner) collect(inner, labels);
        }
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
