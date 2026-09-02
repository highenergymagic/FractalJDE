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

/**
 * Something a command can be offered to.
 *
 * This is NSResponder, and the point of it is that a command is not sent to a program. It
 * is offered to whatever has the keyboard, and if that cannot do it the offer passes to
 * whatever is behind it, and so on out to the program itself. Copy means copy the text when
 * a field has the keyboard and copy the files when the file list does, and neither of them
 * had to be told about the other.
 *
 * Answering no is how the offer passes on. A field with nothing selected cannot copy, says
 * so, and the command reaches the window behind it, which is what a Mac does: with the
 * cursor in an empty search field, Copy still copies the files that are selected.
 */
public interface FMResponder {

    /** Whether this could do it right now. No means the offer passes to whatever is behind. */
    boolean canPerform(FMString action);

    /** Does it. Answering false passes the offer on, the same as not being able to. */
    boolean perform(FMString action);

    /** Whether a command that shows a tick has one, for whoever is drawing the menu. */
    default boolean isOn(FMString action) { return false; }
}
