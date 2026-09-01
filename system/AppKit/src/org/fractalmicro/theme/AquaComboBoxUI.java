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
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * The pop-up button: a rounded rectangle with the blue square at its right end and the
 * two facing arrows on it. An editable combo box is drawn as a text field with the same
 * square beside it, which is the other of the two shapes Aqua gives this control.
 *
 * The list that drops down is Swing's, so the arrow keys, type-ahead and the screen
 * reader all keep working.
 */
public class AquaComboBoxUI extends BasicComboBoxUI {

    private static final int ARROW_WIDTH = 19;
    private static final int HEIGHT = 20;

    public static ComponentUI createUI(JComponent c) { return new AquaComboBoxUI(); }

    @Override protected JButton createArrowButton() {
        JButton button = new JButton() {
            @Override public void paint(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                Aqua.antialias(g);
                paintArrows(g, 0, 0, getWidth(), getHeight(), comboBox.isEnabled());
                g.dispose();
            }
        };
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setFocusable(false);
        // Named. An unnamed button says nothing about what pressing it would do.
        button.getAccessibleContext().setAccessibleName("Show choices");
        return button;
    }

    @Override protected ComboPopup createPopup() {
        BasicComboPopup popup = (BasicComboPopup) super.createPopup();
        popup.setBorder(BorderFactory.createLineBorder(Aqua.MENU_BORDER));
        return popup;
    }

    @Override public void paint(Graphics g0, JComponent c) {
        Graphics2D g = (Graphics2D) g0.create();
        Aqua.antialias(g);
        int w = c.getWidth();
        int h = c.getHeight();
        boolean enabled = c.isEnabled();

        RoundRectangle2D body = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1,
                                                           AquaPainter.BUTTON_ARC,
                                                           AquaPainter.BUTTON_ARC);
        g.setPaint(new GradientPaint(0, 0, enabled ? Color.WHITE : new Color(0xF4F4F4),
                                     0, h, enabled ? new Color(0xDCDCDC) : new Color(0xEDEDED)));
        g.fill(body);
        g.setColor(enabled ? new Color(0x7A7A7A) : new Color(0xB4B4B4));
        g.draw(body);

        if (comboBox.hasFocus() && !comboBox.isEditable()) {
            AquaPainter.paintFocusRing(g, -1, -1, w + 2, h + 2, AquaPainter.BUTTON_ARC + 2);
        }
        g.dispose();
        // The value itself is drawn by the renderer Swing already has.
        Rectangle bounds = rectangleForCurrentValue();
        paintCurrentValue(g0, bounds, hasFocus);
    }

    /** The blue end of the button, with the arrows that mean a list is under here. */
    static void paintArrows(Graphics2D g, int x, int y, int w, int h, boolean enabled) {
        RoundRectangle2D square = new RoundRectangle2D.Float(x, y + 1, w - 2, h - 2, 6, 6);
        g.setPaint(enabled
            ? new GradientPaint(x, y, new Color(0x7CB1F2), x, y + h, new Color(0x1D5FCB))
            : new GradientPaint(x, y, new Color(0xDADADA), x, y + h, new Color(0xBEBEBE)));
        g.fill(square);
        g.setColor(enabled ? new Color(0x18509E) : new Color(0xAAAAAA));
        g.draw(square);

        g.setColor(enabled ? Color.WHITE : new Color(0x8A8A8A));
        int cx = x + w / 2 - 1;
        int cy = y + h / 2;
        int size = 3;
        g.fillPolygon(new int[]{cx - size, cx + size, cx},
                      new int[]{cy - 2, cy - 2, cy - 2 - size}, 3);
        g.fillPolygon(new int[]{cx - size, cx + size, cx},
                      new int[]{cy + 2, cy + 2, cy + 2 + size}, 3);
    }

    @Override public Dimension getPreferredSize(JComponent c) {
        Dimension size = super.getPreferredSize(c);
        return new Dimension(size.width + ARROW_WIDTH, Math.max(size.height, HEIGHT + 2));
    }

    @Override protected Rectangle rectangleForCurrentValue() {
        Rectangle r = super.rectangleForCurrentValue();
        r.x += 6;
        r.width -= 8;
        return r;
    }
}
