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
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Tabs, drawn the way Aqua draws them: one capsule of joined segments centred above a
 * bordered box, the selected segment darker and the dividing lines hairlines.
 *
 * Swing keeps the tab order, the arrow keys and Control Tab, so only the shape changes.
 */
public class AquaTabbedPaneUI extends BasicTabbedPaneUI {

    private static final int TAB_HEIGHT = 22;
    private static final int ARC = 9;

    public static ComponentUI createUI(JComponent c) { return new AquaTabbedPaneUI(); }

    @Override protected void installDefaults() {
        super.installDefaults();
        tabAreaInsets = new Insets(4, 0, 2, 0);
        contentBorderInsets = new Insets(2, 2, 2, 2);
        selectedTabPadInsets = new Insets(0, 0, 0, 0);
        tabInsets = new Insets(2, 14, 2, 14);
    }

    @Override protected int calculateTabHeight(int placement, int index, int fontHeight) {
        return TAB_HEIGHT;
    }

    /** Aqua centres the strip of tabs over the box rather than pushing it left. */
    @Override protected LayoutManager createLayoutManager() {
        LayoutManager layout = super.createLayoutManager();
        if (!(layout instanceof TabbedPaneLayout)) return layout;
        return new TabbedPaneLayout() {
            @Override protected void calculateTabRects(int placement, int count) {
                super.calculateTabRects(placement, count);
                if (count == 0 || (placement != TOP && placement != BOTTOM)) return;
                int leftmost = rects[0].x;
                int rightmost = rects[count - 1].x + rects[count - 1].width;
                int shift = (tabPane.getWidth() - (rightmost - leftmost)) / 2 - leftmost;
                if (shift <= 0) return;
                for (int i = 0; i < count; i++) rects[i].x += shift;
            }
        };
    }

    /** The whole strip is one capsule, so a segment is only rounded at the ends. */
    @Override protected void paintTabBackground(Graphics g0, int placement, int index,
                                                int x, int y, int w, int h, boolean selected) {
        Graphics2D g = (Graphics2D) g0.create();
        Aqua.antialias(g);
        int count = tabPane.getTabCount();
        boolean first = index == 0;
        boolean last = index == count - 1;

        Shape shape = segment(x, y, w, h, first, last);
        if (selected) {
            g.setPaint(new GradientPaint(x, y, new Color(0xC3C3C3), x, y + h, new Color(0xA9A9A9)));
        } else {
            g.setPaint(new GradientPaint(x, y, Color.WHITE, x, y + h, new Color(0xDDDDDD)));
        }
        g.fill(shape);
        g.setColor(new Color(0x8A8A8A));
        g.draw(shape);
        if (!first) g.drawLine(x, y + 1, x, y + h - 1);
        g.dispose();
    }

    private Shape segment(int x, int y, int w, int h, boolean first, boolean last) {
        if (first && last) {
            return new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1, h - 1, ARC, ARC);
        }
        java.awt.geom.Area area = new java.awt.geom.Area(
            new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1, h - 1, ARC, ARC));
        if (!first) {
            area.add(new java.awt.geom.Area(
                new Rectangle2DFloat(x + 0.5f, y + 0.5f, ARC, h - 1)));
        }
        if (!last) {
            area.add(new java.awt.geom.Area(
                new Rectangle2DFloat(x + w - ARC - 0.5f, y + 0.5f, ARC, h - 1)));
        }
        return area;
    }

    /** A rectangle with float corners, so the squared ends line up with the capsule. */
    private static final class Rectangle2DFloat extends java.awt.geom.Rectangle2D.Float {
        Rectangle2DFloat(float x, float y, float w, float h) { super(x, y, w, h); }
    }

    @Override protected void paintTabBorder(Graphics g, int placement, int index,
                                            int x, int y, int w, int h, boolean selected) {
        // The background already draws the outline, in one piece.
    }

    @Override protected void paintContentBorder(Graphics g0, int placement, int selectedIndex) {
        Graphics2D g = (Graphics2D) g0.create();
        Aqua.antialias(g);
        Insets insets = tabPane.getInsets();
        int x = insets.left;
        int y = insets.top + calculateTabAreaHeight(placement, runCount, maxTabHeight);
        int w = tabPane.getWidth() - insets.left - insets.right;
        int h = tabPane.getHeight() - y - insets.bottom;
        g.setColor(new Color(0xF2F2F2));
        g.fillRoundRect(x, y, w - 1, h - 1, 6, 6);
        g.setColor(new Color(0x9A9A9A));
        g.drawRoundRect(x, y, w - 1, h - 1, 6, 6);
        g.dispose();
    }

    @Override protected void paintFocusIndicator(Graphics g0, int placement, Rectangle[] rects,
                                                 int index, Rectangle iconRect,
                                                 Rectangle textRect, boolean selected) {
        if (!selected || !tabPane.hasFocus()) return;
        Graphics2D g = (Graphics2D) g0.create();
        Aqua.antialias(g);
        Rectangle r = rects[index];
        AquaPainter.paintFocusRing(g, r.x - 1, r.y - 1, r.width + 2, r.height + 2, ARC + 2);
        g.dispose();
    }

    @Override protected void paintText(Graphics g, int placement, Font font, FontMetrics metrics,
                                       int index, String title, Rectangle textRect,
                                       boolean selected) {
        g.setFont(font);
        g.setColor(tabPane.isEnabledAt(index) ? new Color(0x1E1E1E) : Aqua.MENU_DISABLED);
        g.drawString(title, textRect.x, textRect.y + metrics.getAscent());
    }
}
