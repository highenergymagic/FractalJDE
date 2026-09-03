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
import org.fractalmicro.foundation.FMLocalized;
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

    /** The interface file this program opens, inside its own bundle. */
    private static final FMString INTERFACE = FMString.of("SystemProfiler");

    private static final FMString SECTIONS = FMString.of("sections");
    private static final FMString DETAILS = FMString.of("details");

    /**
     * The four things this can tell you about, in the order the panel lists them.
     *
     * Which one somebody picked is the position in the list rather than what the row said.
     * The rows are translated and a program cannot switch on words that change language;
     * the order they sit in is the same in every one.
     */
    private static final int HARDWARE = 0;
    private static final int SOFTWARE = 1;
    private static final int VOLUMES = 2;
    private static final int LOCATIONS = 3;

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
        app.on(SECTIONS, event -> show(event.row()));
        app.on(FMString.of("quit"), event -> app.stop());
        app.on(FMString.of("close"), event -> app.stop());

        show(HARDWARE);
    }

    /* ------------------------------------------------------------------ the facts */

    /** Puts one section's facts in the details list. */
    private void show(int section) {
        FMDictionary facts = factsFor(section < 0 ? HARDWARE : section);
        FMMutableArray<FMString> rows = FMMutableArray.empty();
        FMArray<FMString> names = facts.keys();
        for (int i = 0; i < names.count(); i++) {
            rows.add(FMLocalized.filled(FMString.of("profiler.line"),
                                        names.at(i), facts.string(names.at(i))));
        }
        app.setRows(DETAILS, rows.asArray());
    }

    private static FMString word(FMString key) { return FMLocalized.of(key); }

    private static FMDictionary factsFor(int section) {
        FMMutableDictionary facts = FMMutableDictionary.empty();
        org.fractalmicro.foundation.FMProcessInfo machine =
            org.fractalmicro.foundation.FMProcessInfo.processInfo();
        if (section == SOFTWARE) {
            facts.set(word(FMString.of("profiler.systemVersion")),
                      machine.operatingSystemVersionString());
            facts.set(word(FMString.of("profiler.systemName")), machine.operatingSystemLongName());
            facts.set(word(FMString.of("profiler.built")), machine.operatingSystemBuiltAt());
            facts.set(word(FMString.of("profiler.computerName")), machine.hostName());
        } else if (section == VOLUMES) {
            org.fractalmicro.foundation.FMByteCountFormatter sizes =
                org.fractalmicro.foundation.FMByteCountFormatter.formatter();
            for (org.fractalmicro.appkit.FMVolume v
                     : org.fractalmicro.appkit.FMWorkspace.sharedWorkspace().mountedVolumes()) {
                FMString said = v.isReady()
                    ? FMLocalized.filled(FMString.of("profiler.volumeSizes"),
                          sizes.stringFromByteCount(v.totalCapacity()),
                          sizes.stringFromByteCount(v.availableCapacity()))
                    : FMLocalized.of(FMString.of("profiler.notReady"));
                facts.set(v.name(), FMString.of(
                    (v.url() == null ? "" : v.url().path().toString()) + "  " + said
                    + (v.fileSystem().isEmpty() ? "" : "  " + v.fileSystem())));
            }
        } else if (section == LOCATIONS) {
            facts.set(word(FMString.of("profiler.systemVolume")), FMString.describing(OSPaths.ROOT));
            facts.set(word(FMString.of("profiler.preferences")),
                      FMString.describing(OSPaths.userPreferences()));
            facts.set(word(FMString.of("profiler.iconResources")),
                      FMString.describing(OSPaths.coreTypesResources()));
            facts.set(word(FMString.of("profiler.applications")),
                      FMString.describing(OSPaths.applications()));
        } else {
            facts.set(word(FMString.of("profiler.processor")), machine.processorDescription());
            facts.set(word(FMString.of("profiler.memory")), machine.physicalMemoryDescription());
            facts.set(word(FMString.of("profiler.startupDisk")), machine.startupDisk());
            facts.set(word(FMString.of("profiler.computerName")), machine.hostName());
        }
        return facts.asDictionary();
    }

    /* ------------------------------------------------------------- the description */

}
