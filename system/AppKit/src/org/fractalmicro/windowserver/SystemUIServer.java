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
package org.fractalmicro.windowserver;

import org.fractalmicro.appkit.FMMenuExtra;
import org.fractalmicro.bundle.Bundle;
import org.fractalmicro.bundle.Dyld;
import org.fractalmicro.foundation.FMLog;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.os.OSPaths;

import javax.swing.JMenu;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What puts the indicators at the right of the menu bar.
 *
 * The menus on the left belong to whichever program is in front. The clock, the volume and
 * the network are nobody's program's and stay where they are.
 *
 * Each is a bundle: a directory ending in .menu, in CoreServices under Menu Extras, whose
 * principal class is an {@link FMMenuExtra}. This finds them, loads each through the
 * loader out of its own executable, and places what it hands back in the order it asks
 * for. Adding one is putting a bundle in a directory.
 *
 * The system this imitates gives this a process of its own, and one day this will too.
 */
public final class SystemUIServer {

    /** Where the extras live, as on the system this imitates. */
    public static final String MENU_EXTRAS = "Menu Extras";

    /** What a menu extra's bundle is called. */
    public static final String EXTENSION = ".menu";

    private final MainMenu bar;
    private final List<FMMenuExtra> loaded = new ArrayList<>();

    private SystemUIServer(MainMenu bar) {
        this.bar = bar;
    }

    /** Where the extras are kept. */
    public static File menuExtrasFolder() {
        return OSPaths.coreServices().resolve(MENU_EXTRAS).toFile();
    }

    /**
     * Finds every menu extra, loads it, and puts its indicator in the bar.
     *
     * An extra that will not load is complained about and passed over. One bad indicator
     * is not a reason for a person to lose their clock.
     */
    public static SystemUIServer start(MainMenu bar) {
        SystemUIServer server = new SystemUIServer(bar);
        server.loadAll();
        server.install();
        return server;
    }

    private void loadAll() {
        File[] found = menuExtrasFolder().listFiles();
        if (found == null) return;
        for (File each : found) {
            if (!each.isDirectory() || !each.getName().endsWith(EXTENSION)) continue;
            Bundle bundle = Bundle.read(each);
            if (bundle == null) {
                FMLog.say(FMString.of("a menu extra could not be read: " + each.getName()));
                continue;
            }
            try {
                Object made = Dyld.load(bundle);
                if (made instanceof FMMenuExtra extra) {
                    loaded.add(extra);
                } else {
                    FMLog.say(FMString.of(bundle.displayName() + " is not a menu extra"));
                }
            } catch (Exception wouldNotLoad) {
                FMLog.wrong(FMString.of("the menu extra " + each.getName()
                                        + " would not load"), wouldNotLoad);
            }
        }
    }

    /** Puts them in the bar, furthest right first, and by name where two agree. */
    private void install() {
        // Added left to right, so the one asking for the lowest place has to go in last:
        // the clock is at the end of the bar, not the start of the group.
        loaded.sort(Comparator.comparingInt(FMMenuExtra::position).reversed()
                              .thenComparing(e -> e.title().toString()));
        List<JMenu> menus = new ArrayList<>();
        for (FMMenuExtra extra : loaded) {
            try {
                JMenu m = extra.menu();
                if (m != null) menus.add(m);
            } catch (RuntimeException badExtra) {
                FMLog.wrong(FMString.of("the menu extra ").appending(extra.title())
                                     .appending(FMString.of(" would not draw")), badExtra);
            }
        }
        bar.setStatusItems(menus);
        FMLog.say(FMString.of("menu extras loaded: " + menus.size()));
    }

    /** How many are showing, which a check wants to know. */
    public int count() { return loaded.size(); }
}
