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
package org.fractalmicro.systempreferences;

import org.fractalmicro.appkit.FMApplication;
import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMLog;
import org.fractalmicro.foundation.FMNumber;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.os.DockSettings;
import org.fractalmicro.os.FinderSettings;

/**
 * System Preferences: the settings, in a process of its own.
 *
 * Nothing here reaches into the desktop to make it repaint. It writes a preference, and
 * the desktop hears about it: a setting written in one process crosses to every other as a
 * distributed notification, and what each of them does about it is its own business. That
 * is the arrangement a settings program has to have once it stops sharing an address space
 * with the thing it is settings for, and it is better than the arrangement it replaces,
 * where this program knew the names of the classes it had to tell to redraw.
 *
 * The panes are all in one window, in the same place, and one is shown. A pane is not a
 * window and should not close and reopen when somebody clicks a name in the list.
 */
public final class SystemPreferences implements org.fractalmicro.appkit.FMApplicationDelegate {

    /** The interface file this program opens, inside its own bundle. */
    private static final FMString INTERFACE = FMString.of("SystemPreferences");

    private static final FMString PANES = FMString.of("panes");

    /**
     * One switch: which control it is and which pane it is on.
     *
     * Nothing about what it means. The words are in the interface file and so is the
     * setting it shows, and all that is left here is which pane to put it on, because the
     * description has no notion of a pane yet.
     */
    private record Switch(FMString id, FMString pane) {}

    private static final FMString DESKTOP = FMString.of("Desktop");
    private static final FMString SIDEBAR = FMString.of("Sidebar");
    private static final FMString ADVANCED = FMString.of("Advanced");
    private static final FMString APPEARANCE = FMString.of("Appearance");
    private static final FMString DOCK = FMString.of("Dock");

    private static final FMArray<FMString> PANE_NAMES =
        FMArray.of(DESKTOP, SIDEBAR, ADVANCED, APPEARANCE, DOCK);

    private static final Switch[] SWITCHES = {
        sw(FMString.of("hard disks"), DESKTOP),
        sw(FMString.of("external disks"), DESKTOP),
        sw(FMString.of("removable media"), DESKTOP),
        sw(FMString.of("servers"), DESKTOP),

        sw(FMString.of("sidebar devices"), SIDEBAR),
        sw(FMString.of("sidebar places"), SIDEBAR),
        sw(FMString.of("sidebar search"), SIDEBAR),

        sw(FMString.of("all extensions"), ADVANCED),
        sw(FMString.of("warn on trash"), ADVANCED),
        sw(FMString.of("system icons"), ADVANCED),
        sw(FMString.of("show labels"), ADVANCED),
        sw(FMString.of("spring loaded"), ADVANCED),

        sw(FMString.of("white on black"), APPEARANCE),

        sw(FMString.of("magnification"), DOCK),
    };

    private static Switch sw(FMString id, FMString pane) {
        return new Switch(id, pane);
    }

    /** The one control that is not a switch: how big the Dock's tiles are. */
    private static final FMString DOCK_SIZE = FMString.of("dock size");

    /**
     * How long a drag rests on a folder before it opens.
     *
     * A slider rather than a number, because nobody knows what they want in milliseconds
     * and everybody knows whether folders open too eagerly. Set in tenths of a second, so
     * the slider holds the setting rather than a position.
     */
    private static final FMString SPRING_DELAY = FMString.of("spring delay");
    private static final FMString SPRING_DELAY_LABEL = FMString.of("spring delay label");

    private final FMApplication app = FMApplication.sharedApplication();

    /** Opened with no pane named, which means the one a person sees first. */
    @Override public void open() { run(DESKTOP); }

    /** Opened straight onto one pane, which is what a menu item that names one asks for. */
    @Override public void openPart(FMString pane) { run(pane.isBlank() ? DESKTOP : pane); }

    private void run(FMString opening) {
        if (!app.showWindow(INTERFACE)) {
            FMLog.say(FMString.of("the window would not open: ")
                              .appending(app.lastError().description()));
            return;
        }

        // Nothing here reads or writes a setting. Every switch and the Dock size are bound
        // to theirs in the interface file, so the control reads it, writes it and hears it
        // change without this program being told, which is what a binding is for. What was
        // here was fourteen pairs of a getter and a setter, and every one of them was a
        // chance for the switch and the setting to disagree.
        //
        // The delay is the exception, because the slider is in tenths of a second and the
        // setting is in seconds. Cocoa binds through an NSValueTransformer for exactly
        // this and there is not one here yet, so it stays wired by hand and says so.
        app.setValue(SPRING_DELAY,
                     FMNumber.of((int) Math.round(FinderSettings.springDelay() * 10)));
        app.on(SPRING_DELAY, event -> {
            FMNumber tenths = FMNumber.parsing(event.text());
            if (tenths != null) FinderSettings.setSpringDelay(tenths.asInteger() / 10.0);
        });

        app.on(PANES, event -> showPane(event.text()));
        app.on(FMString.of("quit"), event -> app.stop());
        app.on(FMString.of("close"), event -> app.stop());

        showPane(paneNamed(opening));
    }

    /**
     * The pane a name asks for.
     *
     * Matched without regard for case, since every other part name here is lower case: the
     * file manager asks for "desktop" and the list says "Desktop". No name opens the first
     * pane. A name that is not a pane says so first, because showing somebody a different
     * pane silently is how a wrong name goes unnoticed, which it had been doing.
     */
    private static FMString paneNamed(FMString asked) {
        if (asked == null || asked.isBlank()) return DESKTOP;
        for (FMString pane : PANE_NAMES) {
            if (pane.toString().equalsIgnoreCase(asked.toString())) return pane;
        }
        FMLog.say(FMString.of("there is no settings pane called ").appending(asked));
        return DESKTOP;
    }

    /** Shows one pane's controls and hides the rest. */
    private void showPane(FMString pane) {
        FMString wanted = pane.isBlank() ? DESKTOP : pane;
        for (Switch one : SWITCHES) {
            app.setVisible(one.id(), one.pane().sameAs(wanted));
        }
        app.setVisible(DOCK_SIZE, DOCK.sameAs(wanted));
        app.setVisible(FMString.of("dock size label"), DOCK.sameAs(wanted));
        app.setVisible(SPRING_DELAY, ADVANCED.sameAs(wanted));
        app.setVisible(SPRING_DELAY_LABEL, ADVANCED.sameAs(wanted));
    }

    /* ------------------------------------------------------------- the description */

}
