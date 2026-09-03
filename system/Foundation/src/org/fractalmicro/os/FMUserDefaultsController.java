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
package org.fractalmicro.os;

import org.fractalmicro.foundation.FMString;

/**
 * A setting named by a path, for something that binds itself to one.
 *
 * NSUserDefaultsController is what a control binds to when the thing it shows is a
 * preference, and the path is written the way Cocoa writes it: values, then the setting.
 * There are several domains here where a Mac has one, so the domain is the middle part.
 *
 *     values.finder.ShowHardDrivesOnDesktop
 *     values.global.AppleShowAllExtensions
 *     values.dock.tilesize
 *
 * A control bound to a path reads it, writes it and hears it change, with no program in
 * between.
 */
public final class FMUserDefaultsController {
    private FMUserDefaultsController() {}

    /** What every bound path begins with, as NSUserDefaultsController exposes them. */
    public static final String VALUES = "values.";

    /** The short names the paths use, so a path does not carry a reverse domain in it. */
    private static final String FINDER = "finder";
    private static final String GLOBAL = "global";
    private static final String DOCK = "dock";
    private static final String ACCESS = "access";

    /** Whether a path names a setting at all. */
    public static boolean isSetting(FMString path) {
        return path != null && path.toString().startsWith(VALUES) && parts(path) != null;
    }

    /** What is there now, as whatever kind of thing it was written as. */
    public static Object value(FMString path) {
        String[] where = parts(path);
        if (where == null) return null;
        return defaults(where[0]).get(FMString.of(where[1]));
    }

    /**
     * Writes it, and tells everything that cares.
     *
     * The telling crosses to the other processes on its own, because a setting written in
     * one is a distributed notification in all of them. A window bound to a setting somebody
     * changed elsewhere catches up without anything asking it to.
     */
    public static void setValue(FMString path, Object value) {
        String[] where = parts(path);
        if (where == null) return;
        FMUserDefaults domain = defaults(where[0]);
        domain.set(FMString.of(where[1]), value);
        domain.save();
    }

    /** Whether a change that has just been announced is the one this path names. */
    public static boolean names(FMString path, String domain, String key) {
        String[] where = parts(path);
        return where != null
            && defaults(where[0]).domain().toString().equals(domain)
            && where[1].equals(key);
    }

    /** The domain and the key, or nothing at all when the path is not one of these. */
    private static String[] parts(FMString path) {
        if (path == null) return null;
        String text = path.toString();
        if (!text.startsWith(VALUES)) return null;
        String rest = text.substring(VALUES.length());
        int dot = rest.indexOf('.');
        if (dot <= 0 || dot == rest.length() - 1) return null;
        String domain = rest.substring(0, dot);
        if (!FINDER.equals(domain) && !GLOBAL.equals(domain) && !DOCK.equals(domain)
                && !ACCESS.equals(domain)) {
            return null;
        }
        return new String[]{domain, rest.substring(dot + 1)};
    }

    private static FMUserDefaults defaults(String named) {
        return switch (named) {
            case GLOBAL -> FMUserDefaults.of(FMUserDefaults.GLOBAL);
            case DOCK -> FMUserDefaults.of(FMUserDefaults.DOCK);
            case ACCESS -> FMUserDefaults.of(FMUserDefaults.UNIVERSAL_ACCESS);
            default -> FMUserDefaults.of(FMUserDefaults.FINDER);
        };
    }
}
