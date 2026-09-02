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
 * Swing will find a window's first control by walking its focus policy, and for most
 * windows that is the right one. A window where it is not says so here: a file browser
 * opens on its files, not on the search field at the far end of its toolbar.
 *
 * This is an interface rather than the screen knowing which windows are which. The screen
 * used to ask whether the window was a Finder window and call a method on it, which meant
 * the layer that draws had to be able to name a class in the file manager, and so had to be
 * built after it. What it actually wanted was this question, and any window can answer it.
 */
public interface KeyWindow {

    /**
     * What the keyboard goes to when the window opens, or null for the usual first control.
     */
    Component initialFirstResponder();

    /**
     * Puts the keyboard on the window's toolbar, the way Control F5 does on a Mac.
     *
     * Answers false when there is no toolbar showing, which is a thing to say out loud
     * rather than a key that silently does nothing.
     */
    default boolean focusToolbar() { return false; }
}
