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
import javax.swing.plaf.basic.BasicMenuUI;
import java.awt.*;

/** Draws menu-bar titles and submenu rows; behaviour is inherited untouched. */
public class AquaMenuUI extends BasicMenuUI {

    public static ComponentUI createUI(JComponent c) {
        return new AquaMenuUI();
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        menuItem.setOpaque(!isTopLevel());
        menuItem.setFont(Aqua.menuFont());
        menuItem.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
        selectionBackground = Aqua.HILITE_TOP;
        selectionForeground = Color.WHITE;
    }

    private boolean isTopLevel() {
        return menuItem != null && menuItem.getParent() instanceof JMenuBar;
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        JMenu menu = (JMenu) c;
        if (menu.getParent() instanceof JMenuBar) {
            AquaMenuPainter.paintTitle(g, menu, menu.isSelected());
        } else {
            AquaMenuPainter.paintItem(g, menu, menu.isArmed() || menu.isSelected(), true);
        }
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        JMenu menu = (JMenu) c;
        if (menu.getParent() instanceof JMenuBar) {
            FontMetrics fm = menu.getFontMetrics(Aqua.menuFont());
            int w = menu.getIcon() != null
                    ? menu.getIcon().getIconWidth() + 18
                    : fm.stringWidth(menu.getText() == null ? "" : menu.getText()) + 20;
            return new Dimension(w, Aqua.MENU_BAR_HEIGHT);
        }
        return AquaMenuPainter.itemSize(menu, true);
    }
}
