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

import java.awt.Component;

/**
 * A window that says where the keyboard lands inside it.
 *
 * Swing walks the focus policy and gets it right for most windows. A window where it does
 * not says so here: a file browser opens on its files, not on the search field at the far
 * end of its toolbar. An interface rather than the screen knowing which windows are which,
 * so the layer that draws does not have to name a class in the file manager.
 */
public interface KeyWindow {

    /** Null for the usual first control. */
    Component initialFirstResponder();

    /** Control F5 on a Mac. False when there is no toolbar, rather than a key doing nothing. */
    default boolean focusToolbar() { return false; }
}
