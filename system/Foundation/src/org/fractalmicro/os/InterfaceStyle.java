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
 * What a screen is.
 *
 * Two systems disagree here, and the disagreement is older than either of them. On one,
 * the menu bar belongs to the screen: there is one of it, at the top, and it shows the
 * menus of whichever program is in front. On the other, the menu bar belongs to the
 * window: every window carries its own, and the screen has no opinion.
 *
 * GNUstep, which has had to run OpenStep programs on both, names this rather than picking
 * a side: NSMenuInterfaceStyle is a user default, and NSMacintoshInterfaceStyle means the
 * menu goes at the top of the screen while NSWindows95InterfaceStyle means it goes in the
 * window. The same key with the same values decides it here.
 *
 * There is a second question underneath, which GNUstep does not have to ask because X11
 * answers it: whether a program's windows are windows of the host system, with their own
 * places in its window list, or drawings inside one big window of this program's own.
 * That is WindowStyle below. Separate windows are what a desktop environment means; the
 * contained kind is what a checking run uses, because it can be built and painted without
 * ever being put on a screen.
 */
public final class InterfaceStyle {
    private InterfaceStyle() {}

    /** The user default GNUstep defines, in the global domain, with its two values. */
    public static final FMString MENU_KEY = FMString.of("NSMenuInterfaceStyle");
    public static final FMString MACINTOSH = FMString.of("NSMacintoshInterfaceStyle");
    public static final FMString WINDOWS_95 = FMString.of("NSWindows95InterfaceStyle");

    /** This system's own: whether windows are the host's windows or drawn inside one. */
    public static final FMString WINDOW_KEY = FMString.of("FractalWindowStyle");
    public static final FMString SEPARATE = FMString.of("Separate");
    public static final FMString CONTAINED = FMString.of("Contained");

    private static Boolean forcedContained;

    private static FMUserDefaults global() { return FMUserDefaults.of(FMUserDefaults.GLOBAL); }

    /** Set once the stored window style has been looked at by a version that can. */
    private static final FMString CHECKED_KEY = FMString.of("FractalWindowStyleChecked");

    public static void installDefaults() {
        FMUserDefaults d = global();
        d.applyDefault(MENU_KEY, MACINTOSH);
        d.applyDefault(WINDOW_KEY, CONTAINED);

        // Separate windows shipped once with the menu bar and the Dock unreachable,
        // because both had moved into windows of their own
        // and nothing followed them there. Anyone left holding that setting is put back to
        // the style that works, once, and can turn it on again deliberately.
        if (SEPARATE.equals(d.string(WINDOW_KEY, CONTAINED)) && !d.bool(CHECKED_KEY, false)) {
            d.set(WINDOW_KEY, CONTAINED);
            org.fractalmicro.core.Log.info("window style put back to " + CONTAINED
                + ": separate windows left the menu bar out of reach");
        }
        d.set(CHECKED_KEY, Boolean.TRUE);
        d.save();
    }

    /** True when the menu bar is one strip across the top of the screen. */
    public static boolean screenMenuBar() {
        return !WINDOWS_95.equals(global().string(MENU_KEY, MACINTOSH));
    }

    public static void setScreenMenuBar(boolean on) {
        global().set(MENU_KEY, on ? MACINTOSH : WINDOWS_95);
    }

    /**
     * True when each window is a window of the host system in its own right.
     *
     * This is off unless it is asked for. It is the truer shape for a desktop and it is
     * where this is going, but a menu bar has to be reachable
     * and a Dock that live in windows of their own, and until every one of those paths is
     * checked the style that works is the one that runs.
     */
    public static boolean separateWindows() {
        if (forcedContained != null) return !forcedContained;
        return !CONTAINED.equals(global().string(WINDOW_KEY, SEPARATE));
    }

    public static void setSeparateWindows(boolean on) {
        global().set(WINDOW_KEY, on ? SEPARATE : CONTAINED);
    }

    /**
     * Holds the windows inside one for this run whatever the settings say. The checking
     * modes use this: a window that is never shown cannot be a window of the host system,
     * and a check that changed the user's settings to run would be no check at all.
     */
    public static void forceContained() { forcedContained = Boolean.TRUE; }

    public static String describe() {
        return (screenMenuBar() ? "menu bar across the screen" : "a menu bar in each window")
             + ", " + (separateWindows() ? "separate windows" : "windows inside one");
    }
}
