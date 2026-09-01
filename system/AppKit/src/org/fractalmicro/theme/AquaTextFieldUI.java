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
import javax.swing.plaf.basic.BasicTextFieldUI;
import java.awt.*;

/**
 * Text fields: white, a thin grey edge, a shadow just inside the top, and the blue glow
 * when they have the keyboard. The text itself is still drawn by Swing.
 */
public class AquaTextFieldUI extends BasicTextFieldUI {

    public static ComponentUI createUI(JComponent c) {
        return new AquaTextFieldUI();
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        JTextField field = (JTextField) getComponent();
        // Swing only paints a background for an opaque component, and this one
        // paints its own, so opaque it stays.
        field.setOpaque(true);
        field.setFont(Aqua.systemFont());
        field.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
    }

    @Override
    protected void paintBackground(Graphics g0) {
        JComponent c = getComponent();
        Graphics2D g = (Graphics2D) g0.create();
        AquaPainter.paintTextField(g, c.getWidth(), c.getHeight(),
                                   c.isEnabled(), c.hasFocus());
        g.dispose();
    }
}
