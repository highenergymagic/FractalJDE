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

/** the network, and the servers on it. */
public final class NetworkExtra implements FMMenuExtra {

    private static final String SYSTEM_PREFERENCES = "org.fractalmicro.systempreferences";

    @Override public FMString title() { return FMString.of("Network"); }

    @Override public int position() { return 40; }

    @Override public JMenu menu() {
        return build();
    }

    /** The program that shows files, asked for by name when a server is mounted. */
    private static final String FILE_BROWSER = "org.fractalmicro.finder";

    private JMenu build() {
        JMenu m = new JMenu(FMLocalized.of(FMString.of("extra.network")).toString());
        m.add(MainMenu.item(FMLocalized.of(FMString.of("extra.networkPreferences")).toString(), null,
                            e -> Bundles.openPart(SYSTEM_PREFERENCES, "system")));
        m.add(MainMenu.item(FMLocalized.of(FMString.of("extra.connectToServer")).toString(), null,
                            e -> Bundles.openPart(FILE_BROWSER, "connect-to-server")));
        return m;
    }
}
