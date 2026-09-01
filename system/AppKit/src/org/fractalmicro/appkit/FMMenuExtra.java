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

import org.fractalmicro.foundation.FMString;

import javax.swing.JMenu;

/**
 * Something that puts an indicator at the right of the menu bar.
 *
 * The clock, the volume, the network. They are not part of anybody's menus: the menus on
 * the left belong to whichever program is in front and change as the front program
 * changes, while these stay where they are no matter what is running. That is why they are
 * a different kind of thing with a different owner.
 *
 * A menu extra is a bundle: a directory ending in .menu, kept in Menu Extras, whose
 * principal class is one of these. It is loaded the same way an application is, by the loader, out of
 * its own executable, which is what lets one be added without the bar knowing it exists.
 *
 * Implementations need a constructor taking no arguments.
 */
public interface FMMenuExtra {

    /** What it is called, which is also what the settings order them by. */
    FMString title();

    /**
     * The menu it shows, made once.
     *
     * Whatever the extra wants to keep, it keeps itself: a timer for a clock, a listener
     * for a volume. Nothing asks for this twice.
     */
    JMenu menu();

    /**
     * Where it sits among the others, lower numbers further right.
     *
     * The clock is furthest right on the system this imitates, so it asks for the lowest
     * number. Two extras asking for the same place are ordered by name, so that the bar is
     * the same on every start-up.
     */
    default int position() { return 100; }
}
