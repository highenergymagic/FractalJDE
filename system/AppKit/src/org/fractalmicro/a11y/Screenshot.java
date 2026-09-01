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
package org.fractalmicro.a11y;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Saves a picture of the desktop window. Used when checking the look. */
public final class Screenshot {
    private Screenshot() {}

    public static void capture(Window window, String path) {
        try {
            BufferedImage img = new BufferedImage(window.getWidth(), window.getHeight(),
                                                  BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            // printAll rather than paint: it renders a window that was never shown,
            // which is the reason offscreen mode exists.
            if (window instanceof javax.swing.JFrame) {
                javax.swing.JRootPane root = ((javax.swing.JFrame) window).getRootPane();
                root.printAll(g);
            } else {
                window.printAll(g);
            }
            g.dispose();
            File out = new File(path);
            if (out.getParentFile() != null) out.getParentFile().mkdirs();
            ImageIO.write(img, "png", out);
            System.out.println("wrote " + out.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("screenshot failed: " + e.getMessage());
        }
    }
}
