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
 * The clock, the volume, the network. Not part of anybody's menus: the menus on the left
 * belong to whichever program is in front and change with it, while these stay put whatever
 * is running.
 *
 * A bundle like any other, a directory ending in .menu kept in Menu Extras, loaded out of
 * its own executable by the loader. That is what lets one be added without the bar knowing
 * it exists. Implementations need a constructor taking no arguments.
 */
public interface FMMenuExtra {

    /** Also what the settings order them by. */
    FMString title();

    /** Asked for once. Whatever the extra keeps, a timer or a listener, it keeps itself. */
    JMenu menu();

    /**
     * Lower numbers further right, so the clock asks for the lowest. Two asking for the
     * same place are ordered by name, so the bar is the same on every start-up.
     */
    default int position() { return 100; }
}
