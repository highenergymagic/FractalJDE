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

import javax.swing.*;

/**
 * A text view with the text system in it: the same as {@link FMTextField}, for text that
 * runs to more than a line. Documents are written in these.
 */
public class FMTextView extends JTextPane {

    private final FMText.Support support;

    public FMTextView() {
        support = FMText.install(this);
    }

    public FMText.Support textSupport() { return support; }

    /**
     * The text system has to be told when the document underneath is replaced, which
     * happens when a document changes between rich text and plain.
     */
    public void documentReplaced() {
        FMText.installSubstitutions(this);
        getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) {
                support.textChanged();
            }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) {
                support.textChanged();
            }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });
        support.refresh();
    }
}
