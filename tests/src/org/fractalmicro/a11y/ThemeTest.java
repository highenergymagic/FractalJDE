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

import org.fractalmicro.theme.*;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.PrintStream;

/**
 * Checks that the controls are this system's controls.
 *
 * A look and feel that is registered but not reached leaves the default drawing on
 * screen, and the difference is only visible to someone looking. So each control is
 * asked what delegate it got, and then painted into an image and asked what colour came
 * out. A control drawn by the wrong delegate fails both.
 */
public final class ThemeTest {
    private ThemeTest() {}

    public static int count() { return 8; }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("controls:");

        failures += check(out, "sliders use the Aqua delegate",
            new JSlider().getUI() instanceof AquaSliderUI);
        failures += check(out, "progress bars use the Aqua delegate",
            new JProgressBar().getUI() instanceof AquaProgressBarUI);
        failures += check(out, "pop-up buttons use the Aqua delegate",
            new JComboBox<String>().getUI() instanceof AquaComboBoxUI);
        failures += check(out, "tabs use the Aqua delegate",
            new JTabbedPane().getUI() instanceof AquaTabbedPaneUI);
        failures += check(out, "column headings use the Aqua delegate",
            new JTable(new Object[][]{{"a"}}, new Object[]{"Name"})
                .getTableHeader().getUI() instanceof AquaTableHeaderUI);

        // A blue progress bar is the quickest proof that the painting ran: the default
        // delegate draws nothing like this colour.
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(100);
        bar.setSize(120, 16);
        Color middle = pixel(bar, 60, 8);
        failures += check(out, "a full progress bar is drawn blue",
            middle.getBlue() > middle.getRed() + 40);

        JSlider slider = new JSlider(0, 100, 50);
        slider.setSize(140, 24);
        Color groove = pixel(slider, 20, 12);
        failures += check(out, "a slider groove is drawn grey, not filled",
            Math.abs(groove.getRed() - groove.getBlue()) < 24);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("One", new JPanel());
        tabs.addTab("Two", new JPanel());
        tabs.setSize(300, 80);
        // Centred tabs leave the left edge of the strip empty; left aligned ones do not.
        Color leftEdge = pixel(tabs, 3, 12);
        failures += check(out, "the strip of tabs is centred",
            leftEdge.equals(pixel(tabs, 6, 12)));

        out.println("      " + (failures == 0 ? "the controls are this system's"
                                              : failures + " failed"));
        return failures;
    }

    /** Paints a control offscreen and reads one pixel back out of it. */
    private static Color pixel(JComponent c, int x, int y) {
        BufferedImage image = new BufferedImage(Math.max(1, c.getWidth()),
                                                Math.max(1, c.getHeight()),
                                                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        c.doLayout();
        c.paint(g);
        g.dispose();
        x = Math.min(Math.max(0, x), image.getWidth() - 1);
        y = Math.min(Math.max(0, y), image.getHeight() - 1);
        return new Color(image.getRGB(x, y));
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
