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
package org.fractalmicro.menuextras;

import org.fractalmicro.foundation.FMLocalized;
import org.fractalmicro.appkit.FMMenuExtra;
import org.fractalmicro.bundle.Bundles;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.windowserver.MainMenu;

import javax.swing.JMenu;
import javax.swing.Timer;

/** The clock, furthest right, as it is on the system this imitates. */
public final class ClockExtra implements FMMenuExtra {

    private static final String SYSTEM_PREFERENCES = "org.fractalmicro.systempreferences";

    private final JMenu menu = new JMenu();

    @Override public FMString title() { return FMString.of("Clock"); }

    @Override public int position() { return 0; }

    @Override public JMenu menu() {
        menu.add(MainMenu.item(FMLocalized.of(FMString.of("extra.dateAndTimePreferences")).toString(), null,
                               e -> Bundles.openPart(SYSTEM_PREFERENCES, "system")));
        tick();
        Timer every = new Timer(1000, e -> tick());
        every.start();
        return menu;
    }

    /** The time as the menu bar writes it. Only touched when the minute changes. */
    private void tick() {
        // The pattern is words as much as the words are: a language that writes the
        // day after the time, or the hour on a 24 clock, needs a different one.
        String text = new java.text.SimpleDateFormat(
            FMLocalized.of(FMString.of("extra.clockFormat")).toString())
            .format(new java.util.Date());
        if (text.equals(menu.getText())) return;
        menu.setText(text);
        menu.getAccessibleContext().setAccessibleName(text);
        menu.revalidate();
        menu.repaint();
    }
}
