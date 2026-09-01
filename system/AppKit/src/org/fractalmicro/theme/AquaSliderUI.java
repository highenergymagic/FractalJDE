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
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;

/**
 * The slider: a sunken groove with a round knob, or a pointed knob when the slider has
 * tick marks, which is the distinction Aqua draws between the two kinds.
 *
 * Only the painting is here. Where the knob goes, what the arrow keys do and what the
 * value is remain Swing's business, so the keyboard and everything else see the same
 * slider they always did.
 */
public class AquaSliderUI extends BasicSliderUI {

    private static final int TRACK_THICKNESS = 5;
    private static final int KNOB = 15;

    public AquaSliderUI(JSlider slider) { super(slider); }

    public static ComponentUI createUI(JComponent c) {
        return new AquaSliderUI((JSlider) c);
    }

    @Override protected Dimension getThumbSize() {
        boolean pointed = slider.getPaintTicks();
        return slider.getOrientation() == JSlider.HORIZONTAL
            ? new Dimension(KNOB, pointed ? KNOB + 4 : KNOB)
            : new Dimension(pointed ? KNOB + 4 : KNOB, KNOB);
    }

    @Override public void paintTrack(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Aqua.antialias(g);
        Rectangle t = trackRect;
        boolean horizontal = slider.getOrientation() == JSlider.HORIZONTAL;

        int x = horizontal ? t.x : t.x + (t.width - TRACK_THICKNESS) / 2;
        int y = horizontal ? t.y + (t.height - TRACK_THICKNESS) / 2 : t.y;
        int w = horizontal ? t.width : TRACK_THICKNESS;
        int h = horizontal ? TRACK_THICKNESS : t.height;

        g.setPaint(new GradientPaint(x, y, new Color(0xB0B0B0), x, y + h, new Color(0xE8E8E8)));
        g.fillRoundRect(x, y, w, h, TRACK_THICKNESS, TRACK_THICKNESS);
        g.setColor(new Color(0x8A8A8A));
        g.drawRoundRect(x, y, w - 1, h - 1, TRACK_THICKNESS, TRACK_THICKNESS);
        g.dispose();
    }

    @Override public void paintThumb(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Aqua.antialias(g);
        Rectangle t = thumbRect;
        boolean enabled = slider.isEnabled();
        boolean pointed = slider.getPaintTicks();

        Shape shape;
        if (pointed && slider.getOrientation() == JSlider.HORIZONTAL) {
            Polygon p = new Polygon();
            p.addPoint(t.x + 1, t.y + 1);
            p.addPoint(t.x + t.width - 1, t.y + 1);
            p.addPoint(t.x + t.width - 1, t.y + t.height - 6);
            p.addPoint(t.x + t.width / 2, t.y + t.height - 1);
            p.addPoint(t.x + 1, t.y + t.height - 6);
            shape = p;
        } else {
            shape = new java.awt.geom.Ellipse2D.Float(t.x + 1, t.y + 1, t.width - 2, t.height - 2);
        }

        Color top = enabled ? Color.WHITE : new Color(0xF0F0F0);
        Color bottom = enabled ? new Color(0xC6C6C6) : new Color(0xDCDCDC);
        g.setPaint(new GradientPaint(t.x, t.y, top, t.x, t.y + t.height, bottom));
        g.fill(shape);
        g.setColor(enabled ? new Color(0x6E6E6E) : new Color(0xA8A8A8));
        g.draw(shape);

        if (slider.hasFocus()) {
            Rectangle r = shape.getBounds();
            AquaPainter.paintFocusRing(g, r.x - 1, r.y - 1, r.width + 2, r.height + 2, r.height);
        }
        g.dispose();
    }

    /** Ticks are hairlines below the groove, not the heavy marks the default draws. */
    @Override public void paintTicks(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(new Color(0x8A8A8A));
        int major = slider.getMajorTickSpacing();
        if (major <= 0) {
            g.dispose();
            return;
        }
        for (int value = slider.getMinimum(); value <= slider.getMaximum(); value += major) {
            if (slider.getOrientation() == JSlider.HORIZONTAL) {
                int x = xPositionForValue(value);
                g.drawLine(x, tickRect.y + 1, x, tickRect.y + 5);
            } else {
                int y = yPositionForValue(value);
                g.drawLine(tickRect.x + 1, y, tickRect.x + 5, y);
            }
        }
        g.dispose();
    }
}
