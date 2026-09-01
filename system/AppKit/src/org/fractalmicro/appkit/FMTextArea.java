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
 * A plain text area with the text system in it: {@link FMTextField} for text that runs to
 * more than one line but is not a document. Comment boxes are written in these.
 */
public class FMTextArea extends JTextArea {

    private final FMText.Support support;

    public FMTextArea() { this(FMString.of(""), 0, 0); }

    public FMTextArea(FMString text) { this(text, 0, 0); }

    public FMTextArea(int rows, int columns) { this(FMString.of(""), rows, columns); }

    public FMTextArea(FMString text, int rows, int columns) {
        super(text == null ? "" : text.toString(), rows, columns);
        support = FMText.install(this);
    }

    public FMText.Support textSupport() { return support; }
}
