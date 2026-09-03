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

import org.fractalmicro.foundation.FMLocalized;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.quicklook.FMQuickLookGenerator;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

import java.io.File;

/**
 * The picture in a file that holds one.
 *
 * Declares public.image, so it is asked about a kind of image it has no idea about as long
 * as something declared that kind as one.
 */
public final class ImageGenerator implements FMQuickLookGenerator {

    @Override
    public JComponent preview(File file) {
        ImageIcon icon = new ImageIcon(file.getAbsolutePath());
        // A file that claims to be an image and is not comes back with no size at all.
        if (icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0) return null;

        JLabel label = new JLabel(icon);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.getAccessibleContext().setAccessibleName(
            FMLocalized.filled(FMString.of("quicklook.imagePreview"),
                               FMString.of(file.getName()),
                               FMString.of(icon.getIconWidth() + " × "
                                           + icon.getIconHeight())).toString());
        return new JScrollPane(label);
    }
}
