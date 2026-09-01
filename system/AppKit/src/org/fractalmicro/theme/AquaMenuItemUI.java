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
import javax.swing.plaf.basic.BasicMenuItemUI;
import java.awt.*;

/**
 * Painting only. Everything else about menu items (arrow keys, type-ahead, accelerators,
 * the accessible role and name) stays with Swing.
 */
public class AquaMenuItemUI extends BasicMenuItemUI {

    public static ComponentUI createUI(JComponent c) {
        return new AquaMenuItemUI();
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        menuItem.setOpaque(true);
        menuItem.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
        menuItem.setFont(Aqua.menuFont());
        selectionBackground = Aqua.HILITE_TOP;
        selectionForeground = Color.WHITE;
        disabledForeground = Aqua.MENU_DISABLED;
        acceleratorForeground = new Color(0x555555);
        acceleratorSelectionForeground = Color.WHITE;
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        JMenuItem mi = (JMenuItem) c;
        boolean armed = mi.isArmed() || (mi instanceof JMenu && ((JMenu) mi).isSelected());
        AquaMenuPainter.paintItem(g, mi, armed, false);
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        return AquaMenuPainter.itemSize((JMenuItem) c, false);
    }
}
