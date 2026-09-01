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
import javax.swing.plaf.basic.BasicCheckBoxUI;
import javax.swing.plaf.basic.BasicRadioButtonUI;
import java.awt.*;

/**
 * Checkboxes and radio buttons.
 *
 * Both are the same idea drawn twice: a small control at the left, the label beside it
 * in the system font, and the blue fill when it is on. The label is written in sentence
 * case, which is the guidelines' rule for these two and not for push buttons.
 */
public final class AquaToggleUI {
    private AquaToggleUI() {}

    private static final int BOX = 14;
    private static final int GAP = 6;

    /** The checkbox. */
    public static class Check extends BasicCheckBoxUI {
        public static ComponentUI createUI(JComponent c) { return new Check(); }

        @Override protected void installDefaults(AbstractButton b) {
            super.installDefaults(b);
            b.setFont(Aqua.systemFont());
            b.setOpaque(false);
            b.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        }

        @Override public synchronized void paint(Graphics g0, JComponent c) {
            paintToggle((AbstractButton) c, g0, false);
        }

        @Override public Dimension getPreferredSize(JComponent c) {
            return toggleSize((AbstractButton) c);
        }
    }

    /** The radio button. */
    public static class Radio extends BasicRadioButtonUI {
        public static ComponentUI createUI(JComponent c) { return new Radio(); }

        @Override protected void installDefaults(AbstractButton b) {
            super.installDefaults(b);
            b.setFont(Aqua.systemFont());
            b.setOpaque(false);
            b.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        }

        @Override public synchronized void paint(Graphics g0, JComponent c) {
            paintToggle((AbstractButton) c, g0, true);
        }

        @Override public Dimension getPreferredSize(JComponent c) {
            return toggleSize((AbstractButton) c);
        }
    }

    private static void paintToggle(AbstractButton b, Graphics g0, boolean round) {
        Graphics2D g = (Graphics2D) g0.create();
        Aqua.antialias(g);
        ButtonModel model = b.getModel();
        int y = (b.getHeight() - BOX) / 2;
        boolean pressed = model.isArmed() && model.isPressed();

        if (round) {
            AquaPainter.paintRadioButton(g, 0, y, BOX, model.isSelected(), pressed, b.isEnabled());
        } else {
            AquaPainter.paintCheckBox(g, 0, y, BOX, model.isSelected(), pressed, b.isEnabled());
        }
        if (b.hasFocus() && b.isFocusPainted()) {
            AquaPainter.paintFocusRing(g, -2, y - 2, BOX + 4, BOX + 4, round ? BOX + 4 : 7);
        }

        String text = b.getText() == null ? "" : b.getText();
        if (!text.isEmpty()) {
            g.setFont(b.getFont());
            FontMetrics fm = g.getFontMetrics();
            g.setColor(b.isEnabled()
                ? (Aqua.highContrast() ? Color.WHITE : Aqua.MENU_TEXT)
                : Aqua.MENU_DISABLED);
            g.drawString(text, BOX + GAP,
                         (b.getHeight() + fm.getAscent() - fm.getDescent()) / 2);
        }
        g.dispose();
    }

    private static Dimension toggleSize(AbstractButton b) {
        FontMetrics fm = b.getFontMetrics(b.getFont() == null ? Aqua.systemFont() : b.getFont());
        String text = b.getText() == null ? "" : b.getText();
        int width = BOX + (text.isEmpty() ? 0 : GAP + fm.stringWidth(text));
        return new Dimension(width + 2, Math.max(BOX + 4, fm.getHeight() + 4));
    }
}
