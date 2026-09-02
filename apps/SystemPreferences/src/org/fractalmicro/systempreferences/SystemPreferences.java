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

    public static final FMString NAME = FMString.of("System Preferences");

    /** The interface file this program opens, inside its own bundle. */
    private static final FMString INTERFACE = FMString.of("SystemPreferences");

    private static final FMString PANES = FMString.of("panes");

    /**
     * One switch: what it is called, what it reads, and what it writes.
     *
     * Keeping the two halves together is the point. A checkbox whose label and whose
     * setting are written in different places is a checkbox that eventually says one thing
     * and does another.
     */
    private record Switch(FMString id, FMString label, FMString pane,
                          java.util.function.BooleanSupplier reads,
                          java.util.function.Consumer<Boolean> writes) {}

    private static final FMString DESKTOP = FMString.of("Desktop");
    private static final FMString SIDEBAR = FMString.of("Sidebar");
    private static final FMString ADVANCED = FMString.of("Advanced");
    private static final FMString APPEARANCE = FMString.of("Appearance");
    private static final FMString DOCK = FMString.of("Dock");

    private static final FMArray<FMString> PANE_NAMES =
        FMArray.of(DESKTOP, SIDEBAR, ADVANCED, APPEARANCE, DOCK);

    private static final Switch[] SWITCHES = {
        sw(FMString.of("hard disks"), FMString.of("Hard disks on the desktop"), DESKTOP,
           FinderSettings::showHardDisks, FinderSettings::setShowHardDisks),
        sw(FMString.of("external disks"), FMString.of("External disks on the desktop"), DESKTOP,
           FinderSettings::showExternalDisks, FinderSettings::setShowExternalDisks),
        sw(FMString.of("removable media"), FMString.of("CDs, DVDs and iPods on the desktop"), DESKTOP,
           FinderSettings::showRemovableMedia, FinderSettings::setShowRemovableMedia),
        sw(FMString.of("servers"), FMString.of("Connected servers on the desktop"), DESKTOP,
           FinderSettings::showServers, FinderSettings::setShowServers),

        sw(FMString.of("sidebar devices"), FMString.of("Devices in the sidebar"), SIDEBAR,
           FinderSettings::sidebarShowDevices, FinderSettings::setSidebarShowDevices),
        sw(FMString.of("sidebar places"), FMString.of("Places in the sidebar"), SIDEBAR,
           FinderSettings::sidebarShowPlaces, FinderSettings::setSidebarShowPlaces),
        sw(FMString.of("sidebar search"), FMString.of("Search For in the sidebar"), SIDEBAR,
           FinderSettings::sidebarShowSearch, FinderSettings::setSidebarShowSearch),

        sw(FMString.of("all extensions"), FMString.of("Show all filename extensions"), ADVANCED,
           FinderSettings::showAllExtensions, FinderSettings::setShowAllExtensions),
        sw(FMString.of("warn on trash"), FMString.of("Show warning before emptying the Trash"), ADVANCED,
           FinderSettings::warnOnEmptyTrash, FinderSettings::setWarnOnEmptyTrash),
        sw(FMString.of("system icons"), FMString.of("Use Windows icons for applications"), ADVANCED,
           FinderSettings::systemIconsForApplications,
           FinderSettings::setSystemIconsForApplications),
        sw(FMString.of("show labels"), FMString.of("Show labels behind names"), ADVANCED,
           FinderSettings::showLabels, FinderSettings::setShowLabels),

        sw(FMString.of("white on black"), FMString.of("Use white on black"), APPEARANCE,
           FinderSettings::highContrast, FinderSettings::setHighContrast),

        sw(FMString.of("magnification"), FMString.of("Magnification"), DOCK,
           DockSettings::magnification, DockSettings::setMagnification),
    };

    private static Switch sw(FMString id, FMString label, FMString pane,
                             java.util.function.BooleanSupplier reads,
                             java.util.function.Consumer<Boolean> writes) {
        return new Switch(id, label, pane, reads, writes);
    }

    /** The one control that is not a switch: how big the Dock's tiles are. */
    private static final FMString DOCK_SIZE = FMString.of("dock size");

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

        for (Switch one : SWITCHES) {
            app.setValue(one.id(), FMNumber.of(one.reads().getAsBoolean()));
            app.on(one.id(), event -> one.writes().accept(
                FMNumber.parsing(event.text()) != null
                && FMNumber.parsing(event.text()).isTrue()));
        }
        app.setValue(DOCK_SIZE, FMNumber.of(DockSettings.tileSize()));
        app.on(DOCK_SIZE, event -> {
            FMNumber size = FMNumber.parsing(event.text());
            if (size != null) DockSettings.setTileSize(size.asInteger());
        });

        app.on(PANES, event -> showPane(event.text()));
        app.on(FMString.of("quit"), event -> app.stop());
        app.on(FMString.of("close"), event -> app.stop());

        showPane(PANE_NAMES.contains(opening) ? opening : DESKTOP);
    }

    /** Shows one pane's controls and hides the rest. */
    private void showPane(FMString pane) {
        FMString wanted = pane.isBlank() ? DESKTOP : pane;
        for (Switch one : SWITCHES) {
            app.setVisible(one.id(), one.pane().sameAs(wanted));
        }
        app.setVisible(DOCK_SIZE, DOCK.sameAs(wanted));
        app.setVisible(FMString.of("dock size label"), DOCK.sameAs(wanted));
    }

    /* ------------------------------------------------------------- the description */

}
