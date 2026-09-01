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

import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * The shapes Aqua is made of: the bevelled push button, the blue default button and its
 * pulse, the glow that marks the focused control, and the capsule of a scroll bar thumb.
 *
 * Written here rather than borrowed. Aqua's own drawing code was never opened: Darwin
 * covers the kernel and the low level libraries, not AppKit, and OpenJDK's Aqua look
 * and feel is macOS only and carries the GPL, which would decide this program's licence
 * for it. So these are drawn from the published measurements and from screenshots.
 */
public final class AquaPainter {
    private AquaPainter() {}

    // Push button, unpressed.
    private static final Color FACE_TOP = new Color(0xFFFFFF);
    private static final Color FACE_BOTTOM = new Color(0xE4E4E4);
    private static final Color FACE_PRESSED_TOP = new Color(0xCFCFCF);
    private static final Color FACE_PRESSED_BOTTOM = new Color(0xB4B4B4);
    private static final Color EDGE = new Color(0x9A9A9A);

    // The default button: Aqua blue.
    private static final Color BLUE_TOP = new Color(0x8FBDF4);
    private static final Color BLUE_BOTTOM = new Color(0x2C6CD0);
    private static final Color BLUE_EDGE = new Color(0x2159A8);
    private static final Color BLUE_PRESSED_TOP = new Color(0x5A93DC);
    private static final Color BLUE_PRESSED_BOTTOM = new Color(0x1B4FA0);

    public static final int BUTTON_ARC = 8;
    public static final int BUTTON_HEIGHT = 20;
    public static final int BUTTON_MIN_WIDTH = 68;

    /** How far through its pulse the default button is, from 0 to 1 and back. */
    public static float pulsePhase() {
        double period = 1100;                              // roughly one beat a second
        double t = (System.currentTimeMillis() % (long) period) / period;
        return (float) ((1 - Math.cos(t * Math.PI * 2)) / 2);
    }

    /**
     * A push button. The default one is blue and breathes; the rest are white with a
     * grey edge and a highlight along the top: that is where the glassiness comes from.
     */
    public static void paintButton(Graphics2D g, int width, int height,
                                   boolean isDefault, boolean pressed, boolean enabled,
                                   boolean focused) {
        Aqua.antialias(g);
        if (Aqua.highContrast()) {
            g.setColor(pressed ? Color.WHITE : Color.BLACK);
            g.fillRoundRect(0, 0, width - 1, height - 1, BUTTON_ARC, BUTTON_ARC);
            g.setColor(pressed ? Color.BLACK : Color.WHITE);
            g.drawRoundRect(0, 0, width - 1, height - 1, BUTTON_ARC, BUTTON_ARC);
            return;
        }

        Composite old = g.getComposite();
        if (!enabled) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));

        Shape body = new RoundRectangle2D.Float(0.5f, 0.5f, width - 1.5f, height - 1.5f,
                                                BUTTON_ARC, BUTTON_ARC);
        Color top, bottom;
        if (isDefault) {
            top = pressed ? BLUE_PRESSED_TOP : BLUE_TOP;
            bottom = pressed ? BLUE_PRESSED_BOTTOM : BLUE_BOTTOM;
        } else {
            top = pressed ? FACE_PRESSED_TOP : FACE_TOP;
            bottom = pressed ? FACE_PRESSED_BOTTOM : FACE_BOTTOM;
        }
        g.setPaint(new GradientPaint(0, 0, top, 0, height, bottom));
        g.fill(body);

        // The default button pulses. It stopped doing this after 10.6, so it belongs here.
        if (isDefault && enabled && !pressed) {
            float phase = pulsePhase();
            g.setColor(new Color(255, 255, 255, Math.round(40 * phase)));
            g.fill(body);
        }

        // Gloss across the top half.
        Shape gloss = new RoundRectangle2D.Float(1.5f, 1.5f, width - 3.5f, height / 2f - 1f,
                                                 BUTTON_ARC - 2, BUTTON_ARC - 2);
        g.setPaint(new GradientPaint(0, 1, new Color(255, 255, 255, isDefault ? 120 : 200),
                                     0, height / 2f, new Color(255, 255, 255, 20)));
        g.fill(gloss);

        g.setColor(isDefault ? BLUE_EDGE : EDGE);
        g.draw(body);
        g.setColor(new Color(255, 255, 255, 140));
        g.drawLine(3, 1, width - 4, 1);

        g.setComposite(old);
        if (focused) paintFocusRing(g, 0, 0, width, height, BUTTON_ARC + 2);
    }

    /** The soft blue glow Aqua puts around whatever has the keyboard. */
    public static void paintFocusRing(Graphics2D g, int x, int y, int width, int height, int arc) {
        Aqua.antialias(g);
        if (Aqua.highContrast()) {
            g.setColor(Color.YELLOW);
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(x, y, width - 1, height - 1, arc, arc);
            g.setStroke(new BasicStroke(1f));
            return;
        }
        Composite old = g.getComposite();
        for (int i = 3; i >= 1; i--) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.16f * (4 - i)));
            g.setColor(new Color(0x3D7DE8));
            g.setStroke(new BasicStroke(i * 1.6f));
            g.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, width - 1.5f, height - 1.5f,
                                              arc, arc));
        }
        g.setComposite(old);
        g.setStroke(new BasicStroke(1f));
    }

    /** The checkbox: a small rounded square, blue with a white tick when it is on. */
    public static void paintCheckBox(Graphics2D g, int x, int y, int size,
                                     boolean selected, boolean pressed, boolean enabled) {
        Aqua.antialias(g);
        Composite old = g.getComposite();
        if (!enabled) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));

        Shape box = new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, size - 1f, size - 1f, 4, 4);
        if (selected) {
            g.setPaint(new GradientPaint(x, y, pressed ? BLUE_PRESSED_TOP : BLUE_TOP,
                                         x, y + size, pressed ? BLUE_PRESSED_BOTTOM : BLUE_BOTTOM));
            g.fill(box);
            g.setColor(BLUE_EDGE);
            g.draw(box);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(x + 3, y + size / 2, x + size / 2 - 1, y + size - 4);
            g.drawLine(x + size / 2 - 1, y + size - 4, x + size - 3, y + 3);
            g.setStroke(new BasicStroke(1f));
        } else {
            g.setPaint(new GradientPaint(x, y, pressed ? FACE_PRESSED_TOP : FACE_TOP,
                                         x, y + size, pressed ? FACE_PRESSED_BOTTOM : FACE_BOTTOM));
            g.fill(box);
            g.setColor(EDGE);
            g.draw(box);
        }
        g.setComposite(old);
    }

    /** The radio button: the same idea, round, with a dot. */
    public static void paintRadioButton(Graphics2D g, int x, int y, int size,
                                        boolean selected, boolean pressed, boolean enabled) {
        Aqua.antialias(g);
        Composite old = g.getComposite();
        if (!enabled) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));

        if (selected) {
            g.setPaint(new GradientPaint(x, y, pressed ? BLUE_PRESSED_TOP : BLUE_TOP,
                                         x, y + size, pressed ? BLUE_PRESSED_BOTTOM : BLUE_BOTTOM));
            g.fillOval(x, y, size - 1, size - 1);
            g.setColor(BLUE_EDGE);
            g.drawOval(x, y, size - 1, size - 1);
            g.setColor(Color.WHITE);
            int dot = Math.max(4, size / 3);
            g.fillOval(x + (size - dot) / 2, y + (size - dot) / 2, dot, dot);
        } else {
            g.setPaint(new GradientPaint(x, y, pressed ? FACE_PRESSED_TOP : FACE_TOP,
                                         x, y + size, pressed ? FACE_PRESSED_BOTTOM : FACE_BOTTOM));
            g.fillOval(x, y, size - 1, size - 1);
            g.setColor(EDGE);
            g.drawOval(x, y, size - 1, size - 1);
        }
        g.setColor(new Color(255, 255, 255, 150));
        g.drawArc(x + 1, y + 1, size - 3, size - 3, 30, 120);
        g.setComposite(old);
    }

    /** A text field: white, a thin grey edge, and a shadow just inside the top. */
    public static void paintTextField(Graphics2D g, int width, int height,
                                      boolean enabled, boolean focused) {
        Aqua.antialias(g);
        g.setColor(enabled ? (Aqua.highContrast() ? Color.BLACK : Color.WHITE)
                           : new Color(0xF0F0F0));
        g.fillRoundRect(0, 0, width - 1, height - 1, 4, 4);

        g.setColor(new Color(0, 0, 0, 22));
        g.drawLine(2, 1, width - 3, 1);
        g.setColor(new Color(0, 0, 0, 12));
        g.drawLine(2, 2, width - 3, 2);

        g.setColor(Aqua.highContrast() ? Color.WHITE : new Color(0xA6A6A6));
        g.drawRoundRect(0, 0, width - 1, height - 1, 4, 4);
        if (focused) paintFocusRing(g, 0, 0, width, height, 6);
    }

    /** The scroll bar thumb: a blue capsule with a highlight along its length. */
    public static void paintScrollThumb(Graphics2D g, int x, int y, int width, int height,
                                        boolean vertical) {
        Aqua.antialias(g);
        int arc = (vertical ? width : height);
        Shape capsule = new RoundRectangle2D.Float(x + 0.5f, y + 0.5f,
                                                   width - 1f, height - 1f, arc, arc);
        if (Aqua.highContrast()) {
            g.setColor(Color.WHITE);
            g.fill(capsule);
            g.setColor(Color.BLACK);
            g.draw(capsule);
            return;
        }
        g.setPaint(vertical
            ? new GradientPaint(x, 0, new Color(0xA9CDF3), x + width, 0, new Color(0x4B85D6))
            : new GradientPaint(0, y, new Color(0xA9CDF3), 0, y + height, new Color(0x4B85D6)));
        g.fill(capsule);

        g.setPaint(vertical
            ? new GradientPaint(x, 0, new Color(255, 255, 255, 180), x + width * 0.6f, 0,
                                new Color(255, 255, 255, 10))
            : new GradientPaint(0, y, new Color(255, 255, 255, 180), 0, y + height * 0.6f,
                                new Color(255, 255, 255, 10)));
        g.fill(new RoundRectangle2D.Float(x + 1.5f, y + 1.5f,
                                          vertical ? width - 3f : width - 3f,
                                          vertical ? height - 3f : height / 2f, arc, arc));
        g.setColor(new Color(0x2A5FA8));
        g.draw(capsule);
    }

    /** The trough a scroll bar thumb runs in. */
    public static void paintScrollTrack(Graphics2D g, int x, int y, int width, int height,
                                        boolean vertical) {
        if (Aqua.highContrast()) {
            g.setColor(Color.BLACK);
            g.fillRect(x, y, width, height);
            g.setColor(Color.WHITE);
            g.drawRect(x, y, width - 1, height - 1);
            return;
        }
        g.setPaint(vertical
            ? new GradientPaint(x, 0, new Color(0xE9E9E9), x + width, 0, new Color(0xF7F7F7))
            : new GradientPaint(0, y, new Color(0xE9E9E9), 0, y + height, new Color(0xF7F7F7)));
        g.fillRect(x, y, width, height);
        g.setColor(new Color(0xD2D2D2));
        if (vertical) {
            g.drawLine(x, y, x, y + height);
            g.drawLine(x + width - 1, y, x + width - 1, y + height);
        } else {
            g.drawLine(x, y, x + width, y);
            g.drawLine(x, y + height - 1, x + width, y + height - 1);
        }
    }

    /** A scroll arrow, drawn as the small dark triangle Aqua uses. */
    public static void paintScrollArrow(Graphics2D g, int x, int y, int width, int height,
                                        int direction, boolean pressed) {
        Aqua.antialias(g);
        paintScrollTrack(g, x, y, width, height, direction == SwingConstantsNorth
                                                  || direction == SwingConstantsSouth);
        g.setColor(pressed ? new Color(0x2A5FA8) : new Color(0x5A5A5A));
        int cx = x + width / 2;
        int cy = y + height / 2;
        Polygon p = new Polygon();
        switch (direction) {
            case SwingConstantsNorth:
                p.addPoint(cx, cy - 3); p.addPoint(cx - 4, cy + 2); p.addPoint(cx + 4, cy + 2);
                break;
            case SwingConstantsSouth:
                p.addPoint(cx, cy + 3); p.addPoint(cx - 4, cy - 2); p.addPoint(cx + 4, cy - 2);
                break;
            case SwingConstantsWest:
                p.addPoint(cx - 3, cy); p.addPoint(cx + 2, cy - 4); p.addPoint(cx + 2, cy + 4);
                break;
            default:
                p.addPoint(cx + 3, cy); p.addPoint(cx - 2, cy - 4); p.addPoint(cx - 2, cy + 4);
        }
        g.fill(p);
    }

    public static final int SwingConstantsNorth = 1;
    public static final int SwingConstantsSouth = 5;
    public static final int SwingConstantsWest = 7;
    public static final int SwingConstantsEast = 3;
}
