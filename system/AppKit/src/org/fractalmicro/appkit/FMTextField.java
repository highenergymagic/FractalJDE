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

import javax.swing.*;

/**
 * A text field with the text system in it.
 *
 * The same field as ever, with the same painting, keyboard and accessible name, plus
 * spelling, substitutions, detected things and the services menu, because
 * those belong to text and not to any one program. Anywhere this system asks for text,
 * this is what asks.
 *
 * A field can turn any of it off: a field for a file name has no use for a services menu
 * offering to make a document out of the name, and one for a number has no use for
 * spelling. Saying so is one call rather than a different class.
 */
public class FMTextField extends JTextField implements FMResponder {

    /** The editing commands, which mean this text while this has the keyboard. */
    @Override public boolean canPerform(org.fractalmicro.foundation.FMString action) {
        return FMEditingResponder.canPerform(this, action);
    }

    @Override public boolean perform(org.fractalmicro.foundation.FMString action) {
        return FMEditingResponder.perform(this, action);
    }


    private final FMText.Support support;

    public FMTextField() { this(FMString.of(""), 0); }

    public FMTextField(FMString text) { this(text, 0); }

    public FMTextField(int columns) { this(FMString.of(""), columns); }

    public FMTextField(FMString text, int columns) {
        super(text == null ? "" : text.toString(), columns);
        support = FMText.install(this);
    }

    /** What the text system installed here, for a program that wants to drive it. */
    public FMText.Support textSupport() { return support; }

    /** Turns spelling off for this field, for one that holds something that is not prose. */
    public FMTextField withoutSpelling() {
        support.setSpellingOn(false);
        return this;
    }

    /** Turns off looking for things in the text, for a field where there are none. */
    public FMTextField withoutDetection() {
        support.setDetectingOn(false);
        return this;
    }

    /** A field for a name, a number or a path: no spelling, nothing detected. */
    public FMTextField plain() {
        return withoutSpelling().withoutDetection();
    }
}
