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

import org.fractalmicro.bundle.Bundle;
import org.fractalmicro.bundle.Bundles;
import org.fractalmicro.fs.FS;
import org.fractalmicro.fs.Node;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Checks the application bundles: that they are on disk in the right shape, that the
 * Info.plist written can be read back, that Finder treats one as an application rather
 * than a folder, and that opening one by identifier actually starts it.
 */
public final class BundleTest {
    private BundleTest() {}

    private static final String[] EXPECTED = {
        "org.fractalmicro.finder",
        "org.fractalmicro.systempreferences",
        "org.fractalmicro.systemprofiler",
        "org.fractalmicro.activitymonitor",
        "org.fractalmicro.textedit",
        "org.fractalmicro.terminal",
    };

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("application bundles:");

        List<Bundle> all = Bundles.all();
        out.println("      " + all.size() + " installed");
        for (Bundle b : all) {
            out.println("      " + b.displayName() + "  " + b.identifier()
                        + "  " + b.root().getName());
        }

        for (String identifier : EXPECTED) {
            Bundle bundle = Bundles.byIdentifier(identifier);
            failures += check(out, identifier + " is installed", bundle != null);
            if (bundle == null) continue;

            // A program says how it starts, one way or the other. A hosted one names the
            // class the desktop is to make; one with a process of its own names where that
            // process begins. A bundle with neither is a folder nothing can open.
            failures += check(out, identifier + " says how it starts",
                !bundle.principalClass().isEmpty()
                || bundle.flag(org.fractalmicro.bundle.Bundles.OWN_PROCESS));
            failures += check(out, identifier + " has an Info.plist that reads back",
                              new File(bundle.root(), "Contents/Info.plist").isFile()
                              && Bundle.read(bundle.root()) != null);
            // The executable and the two launchers, all in Contents/Fractal: the
            // program itself, the script the format calls for, and the one Windows runs.
            String name = bundle.displayName().toString();
            File executables = new File(bundle.root(), Bundle.EXECUTABLE_DIRECTORY);
            failures += check(out, identifier + " has its executable and both launchers",
                              new File(executables, name).isFile()
                              && new File(executables, name + ".sh").isFile()
                              && new File(executables, name + ".cmd").isFile());
            failures += check(out, identifier + " says APPL in its PkgInfo", pkgInfoIsApp(bundle));
        }

        Bundle textEdit = Bundles.byIdentifier("org.fractalmicro.textedit");
        if (textEdit != null) {
            Node node = FS.node(textEdit.root());
            failures += check(out, "a bundle is an application, not a folder",
                              node.kind == Node.Kind.APPLICATION);
            failures += check(out, "a bundle shows its name without the extension",
                              "TextEdit".equals(node.name));
            failures += check(out, "a bundle is still a package underneath",
                              Bundle.looksLikeBundle(textEdit.root()));
        }

        failures += check(out, "opening a bundle by identifier starts it",
                          Bundles.openIdentifier("org.fractalmicro.systemprofiler"));

        out.println("      " + (failures == 0 ? "bundles are sound" : failures + " failed"));
        return failures;
    }

    private static boolean pkgInfoIsApp(Bundle bundle) {
        try {
            File pkgInfo = new File(bundle.root(), "Contents/PkgInfo");
            if (!pkgInfo.isFile()) return false;
            String text = new String(Files.readAllBytes(pkgInfo.toPath()), StandardCharsets.US_ASCII);
            return text.startsWith("APPL");
        } catch (Exception e) {
            return false;
        }
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }

    public static int count() {
        return EXPECTED.length * 5 + 4;
    }
}
