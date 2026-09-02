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

import javax.swing.text.JTextComponent;

/**
 * What a text control answers when it has the keyboard.
 *
 * The five editing commands, which mean the text and not whatever the window behind is
 * showing. Cocoa has one selector for each of these and every responder that can do one
 * implements it, so Copy is Copy whether a field or a file list is in front.
 *
 * Cannot do it is the important half. A field with nothing selected cannot copy and says
 * so, and the command carries on to the window behind, which is what a Mac does: with the
 * cursor in an empty search field, Copy still copies the files that are selected.
 */
final class FMEditingResponder {
    private FMEditingResponder() {}

    static boolean canPerform(JTextComponent text, FMString action) {
        if (text == null) return false;
        String named = action.toString();
        boolean selected = text.getSelectionEnd() > text.getSelectionStart();
        return switch (named) {
            case "copy" -> selected;
            case "cut" -> selected && text.isEditable();
            case "paste" -> text.isEditable() && FMPasteboard.general().hasText();
            case "selectAll" -> !text.getText().isEmpty();
            case "delete" -> selected && text.isEditable();
            default -> false;
        };
    }

    static boolean perform(JTextComponent text, FMString action) {
        if (!canPerform(text, action)) return false;
        switch (action.toString()) {
            case "copy" -> text.copy();
            case "cut" -> text.cut();
            case "paste" -> text.paste();
            case "selectAll" -> text.selectAll();
            case "delete" -> text.replaceSelection("");
            default -> {
                return false;
            }
        }
        return true;
    }
}
