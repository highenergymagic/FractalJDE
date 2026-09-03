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
package org.fractalmicro.qlgenerators;

import org.fractalmicro.appkit.FMTextArea;
import org.fractalmicro.foundation.FMLocalized;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.quicklook.FMQuickLookGenerator;

import javax.swing.JComponent;
import javax.swing.JScrollPane;

import java.awt.Font;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The words in a file that holds words.
 *
 * Declares public.text, which is the plain kinds, the marked up ones and every kind of
 * source, so a language nobody had written a generator for still previews.
 */
public final class TextGenerator implements FMQuickLookGenerator {

    /** A peek, not the file. Past this much nobody is reading it in a panel anyway. */
    private static final long MOST = 512 * 1024;

    @Override
    public JComponent preview(File file) {
        if (file.length() > MOST) return null;
        String text;
        try {
            text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (java.io.IOException unreadable) {
            text = FMLocalized.filled(FMString.of("quicklook.fileNotRead"),
                                      FMString.describing(unreadable.getMessage())).toString();
        }
        FMTextArea area = new FMTextArea(FMString.of(text));
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setCaretPosition(0);
        area.getAccessibleContext().setAccessibleName(
            FMLocalized.filled(FMString.of("quicklook.contentsOf"),
                               FMString.of(file.getName())).toString());
        return new JScrollPane(area);
    }
}
