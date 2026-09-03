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
package org.fractalmicro.scripting;

import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMMutableArray;
import org.fractalmicro.foundation.FMString;

/**
 * Something a script can reach: a window, a file, a document, the program itself.
 *
 * A scriptable object says what kind of thing it is, what it holds, and what can be asked
 * of it. All three are four character codes, so none of them is a word anybody reads and
 * a translated program answers the same questions as an untranslated one.
 *
 * Cocoa does this with key value coding and a class description read out of the
 * terminology. This is the same shape written out, which suits a language that has no
 * key value coding to lean on.
 */
public interface FMScriptable {

    /** Which kind of thing this is: cwin for a window, cobj for an item. */
    FMString scriptClass();

    /** What it is called, which nearly everything has and scripts lean on. */
    FMString scriptName();

    /**
     * What one property holds, or nothing when there is no such property.
     *
     * The name is a code rather than a word for the same reason the commands are.
     */
    default Object property(FMString code) {
        if (FMScriptObjectSpecifier.NAME.sameAs(code)) return scriptName();
        if (FMScriptObjectSpecifier.CLASS.sameAs(code)) return scriptClass();
        return null;
    }

    /** Puts something in a property, answering whether it took. */
    default boolean setProperty(FMString code, Object value) { return false; }

    /** The things of one kind that this one holds. */
    default FMArray<FMScriptable> elements(FMString wantClass) {
        return FMMutableArray.<FMScriptable>empty().asArray();
    }

    /** Whether it can be got rid of, and getting rid of it. */
    default boolean delete() { return false; }
}
