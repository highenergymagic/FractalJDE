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
package org.fractalmicro.appkit;


import javax.swing.JMenu;
import java.util.List;

/**
 * A window that brings its own menus.
 *
 * The menu bar belongs to whichever program is in front, not to the window, so a
 * window that is not Finder's says here what its program is called and which menus
 * should sit in the bar while it is in front.
 */
public interface AppWindow {

    /** The program's name, shown in the second slot of the menu bar. */
    String applicationName();

    /** The menus between the program menu and Window, in the order they appear. */
    List<JMenu> applicationMenus();

    /**
     * What the program menu's Preferences item opens. A program with no settings of its own
     * leaves this alone and the settings open on their first pane, which is what naming no
     * pane means. It used to name one called "system", which is not a pane.
     */
    default void showPreferences() {
        org.fractalmicro.bundle.Bundles.openPart("org.fractalmicro.systempreferences", "");
    }
}
