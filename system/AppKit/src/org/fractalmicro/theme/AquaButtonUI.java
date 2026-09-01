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
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;

/**
 * Push buttons. Painting only: the behaviour, the mnemonics and the accessible role all
 * stay with Swing.
 *
 * The default button pulses, which it did in 10.6 and stopped doing afterwards. The
 * pulse is driven by a shared timer that only runs while a default button is on screen.
 */
public class AquaButtonUI extends BasicButtonUI {

    private static Timer pulseTimer;
    private static final java.util.List<AbstractButton> PULSING = new java.util.ArrayList<>();

    public static ComponentUI createUI(JComponent c) {
        return new AquaButtonUI();
    }

    @Override
    protected void installDefaults(AbstractButton b) {
        super.installDefaults(b);
        b.setFont(Aqua.systemFont());
        b.setOpaque(false);
        b.setRolloverEnabled(true);
        b.setBorder(BorderFactory.createEmptyBorder(1, 12, 1, 12));
        b.setForeground(Aqua.MENU_TEXT);
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        registerPulse((AbstractButton) c);
    }

    @Override
    public void uninstallUI(JComponent c) {
        PULSING.remove((AbstractButton) c);
        super.uninstallUI(c);
    }

    @Override
    public void paint(Graphics g0, JComponent c) {
        AbstractButton b = (AbstractButton) c;
        Graphics2D g = (Graphics2D) g0.create();
        Aqua.antialias(g);

        boolean isDefault = b instanceof JButton && ((JButton) b).isDefaultButton();
        ButtonModel model = b.getModel();
        AquaPainter.paintButton(g, b.getWidth(), b.getHeight(), isDefault,
                                model.isArmed() && model.isPressed(), b.isEnabled(),
                                b.hasFocus() && b.isFocusPainted());

        FontMetrics fm = g.getFontMetrics(b.getFont());
        String text = b.getText() == null ? "" : b.getText();
        int textWidth = fm.stringWidth(text);
        int x = (b.getWidth() - textWidth) / 2;
        int y = (b.getHeight() + fm.getAscent() - fm.getDescent()) / 2;

        Icon icon = b.getIcon();
        if (icon != null) {
            int total = icon.getIconWidth() + (text.isEmpty() ? 0 : 4 + textWidth);
            int iconX = (b.getWidth() - total) / 2;
            icon.paintIcon(b, g, iconX, (b.getHeight() - icon.getIconHeight()) / 2);
            x = iconX + icon.getIconWidth() + 4;
        }

        g.setFont(b.getFont());
        g.setColor(!b.isEnabled() ? Aqua.MENU_DISABLED
                 : isDefault ? Color.WHITE
                 : (Aqua.highContrast() ? Color.WHITE : Aqua.MENU_TEXT));
        if (!text.isEmpty()) g.drawString(text, x, y);
        g.dispose();
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        Dimension size = super.getPreferredSize(c);
        if (size == null) return null;
        AbstractButton b = (AbstractButton) c;
        int width = Math.max(size.width + 12,
                             b.getIcon() != null ? size.width + 8 : AquaPainter.BUTTON_MIN_WIDTH);
        int height = Math.min(Math.max(size.height, AquaPainter.BUTTON_HEIGHT + 2),
                              AquaPainter.BUTTON_HEIGHT + 4);
        return new Dimension(width, height);
    }

    /** Keeps one timer for every default button on screen, and stops when there are none. */
    private static synchronized void registerPulse(AbstractButton b) {
        PULSING.add(b);
        if (pulseTimer == null) {
            pulseTimer = new Timer(60, e -> {
                boolean anyDefault = false;
                for (AbstractButton button : new java.util.ArrayList<>(PULSING)) {
                    if (button instanceof JButton && button.isShowing()
                            && ((JButton) button).isDefaultButton() && button.isEnabled()) {
                        anyDefault = true;
                        button.repaint();
                    }
                }
                if (!anyDefault) pulseTimer.stop();
            });
        }
        if (!pulseTimer.isRunning()) pulseTimer.start();
    }
}
