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
package org.fractalmicro.bundle;

import java.util.List;
import java.util.Set;

/**
 * The libraries this system offers, by the names a program links them under.
 *
 * A program says what it links and gets that and nothing else. The two every application
 * links are Foundation and AppKit. Apple's own documentation puts it plainly: an
 * application cannot be written for the system without both, because AppKit depends
 * directly on Foundation and neither is optional for something that puts a window on a
 * screen. CoreServices is linked by a program that opens other programs or asks about
 * what is installed, and it is linked as the umbrella rather than by reaching into
 * LaunchServices, which is the rule Apple states for its own sub-frameworks.
 *
 * What each library holds is not written down here. It is in the library, as the list of
 * symbols it exports, and read from there when the loader needs it.
 */
public final class Frameworks {
    private Frameworks() {}

    /** What a load command calls each library. */
    public static final String LIB_SYSTEM = "/usr/lib/libSystem.B.dylib";
    public static final String FOUNDATION = "@rpath/Foundation.framework/Versions/A/Foundation";
    public static final String APPKIT = "@rpath/AppKit.framework/Versions/A/AppKit";
    public static final String CORE_SERVICES =
        "@rpath/CoreServices.framework/Versions/A/CoreServices";
    public static final String LAUNCH_SERVICES =
        "@rpath/CoreServices.framework/Versions/A/Frameworks/LaunchServices.framework"
        + "/Versions/A/LaunchServices";
    public static final String METADATA =
        "@rpath/CoreServices.framework/Versions/A/Frameworks/Metadata.framework"
        + "/Versions/A/Metadata";
    public static final String DYLD = "/usr/lib/dyld";
    public static final String LAUNCHD = "/sbin/launchd";

    /** What every application links, and could not run without. */
    public static final List<String> COCOA = List.of(FOUNDATION, APPKIT);

    /** The same, for a program that also opens other programs. */
    public static final List<String> COCOA_AND_SERVICES =
        List.of(FOUNDATION, APPKIT, CORE_SERVICES);

    /** Every library this system installs, in the order they depend on each other. */
    private static final List<String> ALL = List.of(
        LIB_SYSTEM, DYLD, LAUNCHD, FOUNDATION, LAUNCH_SERVICES, METADATA, APPKIT,
        CORE_SERVICES);

    /**
     * Tells the loader that all of these are already loaded, and what each one holds.
     *
     * Everything the system is made of was loaded before the loader existed, by whatever
     * started the virtual machine. Registering them means a program that links Foundation
     * is handed these classes rather than a second copy, so an object it makes is the same
     * kind of thing when the desktop is given it back.
     *
     * What each one holds is read out of the image on disk, from the same symbol table a
     * program would resolve against had it been started in a process of its own. Nothing
     * about the division is written down here: a class moving from one framework to
     * another changes which library exports it, and this follows without being edited.
     *
     * With no images installed there is nothing to read and nothing to divide, so each is
     * registered as holding everything. That is a development build running out of one
     * archive, where the division has not been made yet and pretending otherwise would
     * only produce failures that the installed system does not have.
     */
    public static void registerRunning(ClassLoader running) {
        int described = 0;
        for (String installName : ALL) {
            // A process the loader started already has these mapped, properly, each from
            // its own image. Saying they are running here would replace that with one
            // loader standing in for all of them, and a class that lives in AppKit would
            // be looked for in whichever loader happened to be passed.
            if (org.fractalmicro.dyld.Dyld.isLoaded(installName)) continue;
            Set<String> exports = exportsOf(installName);
            org.fractalmicro.dyld.Dyld.registerRunning(installName, running, exports);
            if (!exports.isEmpty()) described++;
        }
        if (described == 0) {
            org.fractalmicro.core.Log.info("dyld: no images installed, so no image is divided");
        }
    }

    /**
     * What one installed library offers, including what it passes on.
     *
     * An umbrella exports nothing of its own. A program that reached CoreServices for a
     * class LaunchServices defines has to be answered by the umbrella, so the umbrella is
     * registered as holding what it re-exports.
     */
    private static Set<String> exportsOf(String installName) {
        java.nio.file.Path binary = Dyld.resolveFramework(installName);
        if (binary == null) return Set.of();
        try {
            org.fractalmicro.macho.MachO image = org.fractalmicro.macho.MachO.read(binary);
            Set<String> out = new java.util.LinkedHashSet<>(image.exports());
            for (String passedOn : image.reexported()) out.addAll(exportsOf(passedOn));
            return out;
        } catch (java.io.IOException notAnImage) {
            return Set.of();
        }
    }

    /** Every library this system installs. */
    public static List<String> all() { return ALL; }

    /** Where a program looks when a name begins with @rpath. */
    public static List<String> runpaths() { return Images.RUNPATHS; }
}
