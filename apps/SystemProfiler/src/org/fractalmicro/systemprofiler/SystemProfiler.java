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
package org.fractalmicro.systemprofiler;

import org.fractalmicro.appkit.FMApplication;
import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMLog;
import org.fractalmicro.foundation.FMMutableArray;
import org.fractalmicro.foundation.FMMutableDictionary;
import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.os.OSPaths;
import org.fractalmicro.os.SystemProfile;
import org.fractalmicro.os.Version;

/**
 * System Profiler: what this machine is.
 *
 * A process of its own, like every program here. It has no window of its own to draw and
 * nothing on the screen belongs to it: it hands over a description of what it wants, and
 * the window server puts real controls up in the process that owns the screen. What comes
 * back are events, and what goes across afterwards are rows.
 *
 * That arrangement is the reason a program crashing takes nothing with it, and the reason
 * the window it opened is still a real window: the controls are real controls, they are
 * simply somewhere else.
 */
public final class SystemProfiler {

    public static final FMString NAME = FMString.of("System Profiler");

    /** The interface file this program opens, inside its own bundle. */
    private static final FMString INTERFACE = FMString.of("SystemProfiler");

    private static final FMString SECTIONS = FMString.of("sections");
    private static final FMString DETAILS = FMString.of("details");

    /** The four things this can tell you about, in the order the panel lists them. */
    private static final FMString HARDWARE = FMString.of("Hardware");
    private static final FMString SOFTWARE = FMString.of("Software");
    private static final FMString VOLUMES = FMString.of("Volumes");
    private static final FMString LOCATIONS = FMString.of("Locations");

    private final FMApplication app = FMApplication.named(NAME);

    public static void main(String[] arguments) {
        if (!FMApplication.serverAvailable()) {
            FMLog.say(FMString.of("there is no window server to draw a window on"));
            return;
        }
        new SystemProfiler().run();
    }

    private void run() {
        if (!app.showWindow(INTERFACE)) {
            FMLog.say(FMString.of("the window would not open: ")
                              .appending(app.lastError().description()));
            return;
        }
        app.onClose(app::stop);
        app.on(SECTIONS, event -> show(event.text()));
        app.on(FMString.of("quit"), event -> app.stop());
        app.on(FMString.of("close"), event -> app.stop());

        show(HARDWARE);
        app.run();
        app.close();
    }

    /* ------------------------------------------------------------------ the facts */

    /** Puts one section's facts in the details list. */
    private void show(FMString section) {
        FMDictionary facts = factsFor(section.isBlank() ? HARDWARE : section);
        FMMutableArray<FMString> rows = FMMutableArray.empty();
        FMArray<FMString> names = facts.keys();
        for (int i = 0; i < names.count(); i++) {
            rows.add(names.at(i).appending(FMString.of(":  "))
                          .appending(facts.string(names.at(i))));
        }
        app.setRows(DETAILS, rows.asArray());
    }

    private static FMDictionary factsFor(FMString section) {
        FMMutableDictionary facts = FMMutableDictionary.empty();
        if (section.sameAs(SOFTWARE)) {
            facts.set(FMString.of("System Version"),
                FMString.of(SystemProfile.OS_NAME + " " + SystemProfile.version()
                            + " (" + SystemProfile.build() + ")"));
            facts.set(FMString.of("System Name"), FMString.of(SystemProfile.OS_LONG_NAME));
            facts.set(FMString.of("Built"), FMString.of(Version.builtAt()));
            facts.set(FMString.of("Computer Name"),
                      FMString.of(SystemProfile.computerName()));
        } else if (section.sameAs(VOLUMES)) {
            for (org.fractalmicro.fs.Node v : org.fractalmicro.fs.Volumes.all()) {
                facts.set(FMString.of(v.name), FMString.of(v.mountPoint + "  "
                    + (v.size > 0
                       ? org.fractalmicro.fs.FS.formatBytes(v.size) + ", "
                         + org.fractalmicro.fs.FS.formatBytes(v.free) + " free"
                       : "not ready")
                    + (v.fileSystem.isEmpty() ? "" : "  " + v.fileSystem)));
            }
        } else if (section.sameAs(LOCATIONS)) {
            facts.set(FMString.of("System Volume"), FMString.describing(OSPaths.ROOT));
            facts.set(FMString.of("Preferences"),
                      FMString.describing(OSPaths.userPreferences()));
            facts.set(FMString.of("Icon Resources"),
                      FMString.describing(OSPaths.coreTypesResources()));
            facts.set(FMString.of("Applications"),
                      FMString.describing(OSPaths.applications()));
        } else {
            facts.set(FMString.of("Processor"), FMString.of(SystemProfile.processor()));
            facts.set(FMString.of("Memory"), FMString.of(SystemProfile.memory()));
            facts.set(FMString.of("Startup Disk"), FMString.of(SystemProfile.startupDisk()));
            facts.set(FMString.of("Computer Name"),
                      FMString.of(SystemProfile.computerName()));
        }
        return facts.asDictionary();
    }

    /* ------------------------------------------------------------- the description */

}
