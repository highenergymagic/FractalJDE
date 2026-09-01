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

import org.fractalmicro.appkit.FMMenuExtra;
import org.fractalmicro.bundle.Bundles;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.windowserver.MainMenu;

import javax.swing.JMenu;

/** the account, and the way out of it. */
public final class UserExtra implements FMMenuExtra {

    private static final String SYSTEM_PREFERENCES = "org.fractalmicro.systempreferences";

    @Override public FMString title() { return FMString.of("User"); }

    @Override public int position() { return 20; }

    @Override public JMenu menu() {
        return build();
    }

    private JMenu build() {
        String user = System.getProperty("user.name");
        JMenu m = new JMenu(user);
        m.getAccessibleContext().setAccessibleName(user);
        m.add(MainMenu.item("Account Preferences…", null,
                            e -> Bundles.openPart(SYSTEM_PREFERENCES, "system")));
        m.add(MainMenu.item("Login Window…", null, e -> {
            int chose = org.fractalmicro.appkit.Alert.confirmIrreversible(
                FMString.of("Are you sure you want to quit all applications and log out now?"),
                FMString.of("If you do nothing, you will be logged out automatically."),
                FMString.of("Log Out"), FMString.of(null));
            if (chose == 0) org.fractalmicro.win.Session.logOut(false);
        }));
        return m;
    }
}
