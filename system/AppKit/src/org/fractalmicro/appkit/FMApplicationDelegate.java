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

import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMURL;

/**
 * What a program does when the system asks something of it.
 *
 * A bundle names one of these in its Info.plist as NSPrincipalClass. Opening the bundle
 * finds that class, makes one, and calls {@link #open}, which is what NSPrincipalClass
 * means on a Mac and means here. Everything after that is the program's own business.
 *
 * This is part of AppKit rather than of the machinery that finds programs, for the reason
 * Cocoa puts NSApplicationDelegate there: the thing being asked is a program with windows,
 * and what does the asking runs inside it. LaunchServices starts programs; it does not
 * reach into them.
 *
 * Implementations need a constructor taking no arguments.
 */
public interface FMApplicationDelegate {

    /** Called when the program is opened. Runs on the main thread. */
    void open();

    /**
     * Called when it is opened on some files, as by dragging them onto its icon.
     *
     * The locations are the system's own, not the runtime's, so a program can be written
     * without naming anything outside this system.
     */
    default void openURLs(FMArray<FMURL> urls) {
        open();
    }

    /**
     * Opens a named part of the program: one preference pane, one particular window.
     *
     * A menu item that goes straight to a pane needs to say which, and it cannot say so by
     * calling a method on a class it is not allowed to know about. It names the part, and
     * the program decides what that means. A program with no parts opens normally.
     */
    default void openPart(FMString part) {
        open();
    }

    /**
     * Opens a new document holding this text, which is how a service hands a selection to
     * the program that will do something with it.
     */
    default void openText(FMString text) {
        open();
    }
}
