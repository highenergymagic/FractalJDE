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
package org.fractalmicro.theme;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * The progress bar: a capsule with a blue fill and the diagonal stripes moving through
 * it, and the barber pole for the kind that cannot say how far along it is.
 *
 * The stripes move on the timer Swing already runs for indeterminate bars; a
 * determinate bar keeps them still, as it does in Aqua.
 */
public class AquaProgressBarUI extends BasicProgressBarUI {

    private static final int HEIGHT = 16;
    private static final int STRIPE = 12;

    public static ComponentUI createUI(JComponent c) { return new AquaProgressBarUI(); }

    @Override public Dimension getPreferredSize(JComponent c) {
        Dimension size = super.getPreferredSize(c);
        if (progressBar.getOrientation() == JProgressBar.HORIZONTAL) {
            return new Dimension(Math.max(size.width, 100), HEIGHT);
        }
        return new Dimension(HEIGHT, Math.max(size.height, 100));
    }

    @Override protected void paintDeterminate(Graphics g0, JComponent c) {
        Graphics2D g = (Graphics2D) g0.create();
        Aqua.antialias(g);
        int w = progressBar.getWidth();
        int h = progressBar.getHeight();
        Shape capsule = trough(g, w, h);

        int amount = (int) Math.round(progressBar.getPercentComplete()
                                      * (progressBar.getOrientation() == JProgressBar.HORIZONTAL
                                         ? w - 2 : h - 2));
        if (amount > 0) {
            Shape clip = progressBar.getOrientation() == JProgressBar.HORIZONTAL
                ? new Rectangle(1, 1, amount, h - 2)
                : new Rectangle(1, h - 1 - amount, w - 2, amount);
            g.setClip(clip);
            g.clip(capsule);
            fillBlue(g, w, h);
            stripes(g, w, h, 0);
        }
        g.dispose();
        // Aqua puts no number inside the bar unless the bar was asked for one.
        if (progressBar.isStringPainted()) {
            paintString(g0, 0, 0, w, h, amount, progressBar.getInsets());
        }
    }

    @Override protected void paintIndeterminate(Graphics g0, JComponent c) {
        Graphics2D g = (Graphics2D) g0.create();
        Aqua.antialias(g);
        int w = progressBar.getWidth();
        int h = progressBar.getHeight();
        Shape capsule = trough(g, w, h);
        g.clip(capsule);
        fillBlue(g, w, h);
        stripes(g, w, h, getAnimationIndex() * 2);
        g.dispose();
    }

    private Shape trough(Graphics2D g, int w, int h) {
        int arc = Math.min(w, h);
        RoundRectangle2D capsule = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, arc, arc);
        g.setPaint(new GradientPaint(0, 0, new Color(0xD2D2D2), 0, h, new Color(0xF4F4F4)));
        g.fill(capsule);
        g.setColor(new Color(0x8A8A8A));
        g.draw(capsule);
        return capsule;
    }

    private void fillBlue(Graphics2D g, int w, int h) {
        g.setPaint(new GradientPaint(0, 0, new Color(0x8FC0F4), 0, h, new Color(0x2C6FD0)));
        g.fillRect(0, 0, w, h);
    }

    /** The diagonal lines that run through the fill. */
    private void stripes(Graphics2D g, int w, int h, int offset) {
        g.setColor(new Color(255, 255, 255, 60));
        g.setStroke(new BasicStroke(4f));
        for (int x = -h + (offset % STRIPE); x < w + h; x += STRIPE) {
            g.drawLine(x, h, x + h, 0);
        }
    }
}
