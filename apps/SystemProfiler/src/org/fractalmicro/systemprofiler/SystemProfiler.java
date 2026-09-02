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
public final class SystemProfiler implements org.fractalmicro.appkit.FMApplicationDelegate {

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

    private final FMApplication app = FMApplication.sharedApplication();

    /**
     * Opened, which is the whole of this program's start-up.
     *
     * There is no main. The bundle names this class and the loader calls the framework's
     * application main, which reads that name, makes one, and sends it this. Checking for a
     * window server, reading events until told to stop, and closing afterwards were the
     * same lines in every program here and are now in none of them.
     */
    @Override public void open() {
        if (!app.showWindow(INTERFACE)) {
            FMLog.say(FMString.of("the window would not open: ")
                              .appending(app.lastError().description()));
            return;
        }
        app.on(SECTIONS, event -> show(event.text()));
        app.on(FMString.of("quit"), event -> app.stop());
        app.on(FMString.of("close"), event -> app.stop());

        show(HARDWARE);
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
        org.fractalmicro.foundation.FMProcessInfo machine =
            org.fractalmicro.foundation.FMProcessInfo.processInfo();
        if (section.sameAs(SOFTWARE)) {
            facts.set(FMString.of("System Version"),
                      machine.operatingSystemVersionString());
            facts.set(FMString.of("System Name"), machine.operatingSystemLongName());
            facts.set(FMString.of("Built"), machine.operatingSystemBuiltAt());
            facts.set(FMString.of("Computer Name"), machine.hostName());
        } else if (section.sameAs(VOLUMES)) {
            org.fractalmicro.foundation.FMByteCountFormatter sizes =
                org.fractalmicro.foundation.FMByteCountFormatter.formatter();
            for (org.fractalmicro.appkit.FMVolume v
                     : org.fractalmicro.appkit.FMWorkspace.sharedWorkspace().mountedVolumes()) {
                facts.set(v.name(), FMString.of(
                    (v.url() == null ? "" : v.url().path().toString()) + "  "
                    + (v.isReady()
                       ? sizes.stringFromByteCount(v.totalCapacity()) + ", "
                         + sizes.stringFromByteCount(v.availableCapacity()) + " free"
                       : "not ready")
                    + (v.fileSystem().isEmpty() ? "" : "  " + v.fileSystem())));
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
            facts.set(FMString.of("Processor"), machine.processorDescription());
            facts.set(FMString.of("Memory"), machine.physicalMemoryDescription());
            facts.set(FMString.of("Startup Disk"), machine.startupDisk());
            facts.set(FMString.of("Computer Name"), machine.hostName());
        }
        return facts.asDictionary();
    }

    /* ------------------------------------------------------------- the description */

}
