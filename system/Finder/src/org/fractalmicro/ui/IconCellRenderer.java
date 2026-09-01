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
package org.fractalmicro.ui;

import org.fractalmicro.fs.Node;
import org.fractalmicro.theme.Aqua;
import org.fractalmicro.theme.Icons;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Icon-with-label cell used on the desktop and in icon view. It is a JLabel so that
 * the accessible name, role and text all come from Swing rather than from painting.
 */
public class IconCellRenderer extends JLabel implements ListCellRenderer<Node> {
    private boolean selected;
    private boolean focused;
    private final boolean onDesktop;
    private int iconSize;

    public IconCellRenderer(boolean onDesktop, int iconSize) {
        this.onDesktop = onDesktop;
        this.iconSize = iconSize;
        setHorizontalAlignment(CENTER);
        setHorizontalTextPosition(CENTER);
        setVerticalTextPosition(BOTTOM);
        setVerticalAlignment(TOP);
        setOpaque(false);
        setFont(Aqua.viewFont());
        setForeground(onDesktop ? Color.WHITE : Color.BLACK);
        setBorder(BorderFactory.createEmptyBorder(6, 4, 4, 4));
    }

    public void setIconSize(int size) { this.iconSize = size; }

    /** The label of the item being drawn, so the pill behind the name is its colour. */
    private int label;

    /** Where this label puts its text, from the same layout the label itself uses. */
    private Rectangle textBounds(FontMetrics fm) {
        Rectangle view = new Rectangle();
        Rectangle icon = new Rectangle();
        Rectangle text = new Rectangle();
        java.awt.Insets insets = getInsets();
        view.x = insets.left;
        view.y = insets.top;
        view.width = getWidth() - insets.left - insets.right;
        view.height = getHeight() - insets.top - insets.bottom;
        javax.swing.SwingUtilities.layoutCompoundLabel(this, fm, getText(), getIcon(),
            getVerticalAlignment(), getHorizontalAlignment(),
            getVerticalTextPosition(), getHorizontalTextPosition(),
            view, icon, text, getIconTextGap());
        return text;
    }

    /** Black or white, whichever stands out against a colour. */
    static Color readableOn(Color background) {
        double luminance = (0.299 * background.getRed()
                          + 0.587 * background.getGreen()
                          + 0.114 * background.getBlue()) / 255.0;
        return luminance > 0.55 ? Color.BLACK : Color.WHITE;
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends Node> list, Node value,
                                                  int index, boolean isSelected, boolean cellHasFocus) {
        selected = isSelected;
        focused = cellHasFocus;
        setIcon(new ImageIcon(Icons.forNode(value, iconSize)));
        FontMetrics fm = getFontMetrics(getFont());
        int max = list.getFixedCellWidth() - 12;
        setText(Aqua.clipMiddle(fm, value.name, max > 20 ? max : 80));
        // On a label, the name is drawn in whichever of black or white can be read
        // against it: a yellow label with white text is not a label anyone can read.
        java.awt.Color pill = org.fractalmicro.fs.Labels.showing() && !isSelected
            ? org.fractalmicro.fs.Labels.colorOf(value.label) : null;
        setForeground(pill != null ? readableOn(pill)
                      : onDesktop || isSelected ? Color.WHITE : Color.BLACK);
        getAccessibleContext().setAccessibleName(value.accessibleName());
        // Finder's own wording: the desktop says "icon", a view says what the item is.
        String description = onDesktop ? "selected icon" : "selected " + value.kindPhrase();
        String label = org.fractalmicro.fs.Labels.nameOf(value.label);
        if (value.label > 0) description = description + ", " + label + " label";
        getAccessibleContext().setAccessibleDescription(description);
        this.label = value.label;
        return this;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Aqua.antialias(g);
        FontMetrics fm = getFontMetrics(getFont());
        // Where the name will actually be drawn, worked out the way the label works it
        // out, so the pill behind it lines up with it rather than near it.
        Rectangle text = textBounds(fm);
        int textW = text.width + 8;
        int textH = text.height + 1;
        int textX = text.x - 4;
        int textY = text.y;

        // The label is a coloured pill behind the name. A selected item keeps the blue
        // of the selection and shows its label as a ring around it, so both can be seen.
        java.awt.Color labelColor = org.fractalmicro.fs.Labels.showing()
            ? org.fractalmicro.fs.Labels.colorOf(label) : null;

        if (selected) {
            g.setColor(Aqua.highContrast() ? Color.WHITE : new Color(255, 255, 255, 60));
            g.fill(new RoundRectangle2D.Float(6, 2, getWidth() - 12, getHeight() - 8, 8, 8));
            if (labelColor != null) {
                g.setColor(labelColor);
                g.fill(new RoundRectangle2D.Float(textX - 3, textY - 2, textW + 6, textH + 4,
                                                  10, 10));
            }
            g.setColor(Aqua.highContrast() ? Color.BLACK : Aqua.SELECTION);
            g.fill(new RoundRectangle2D.Float(textX, textY, textW, textH, 8, 8));
        } else if (labelColor != null) {
            g.setColor(labelColor);
            g.fill(new RoundRectangle2D.Float(textX, textY, textW, textH, 8, 8));
        } else if (onDesktop) {
            g.setColor(new Color(0, 0, 0, 70));
            g.fill(new RoundRectangle2D.Float(textX, textY, textW, textH, 8, 8));
        }
        g.dispose();
        super.paintComponent(g0);
        if (focused) {
            Graphics2D g2 = (Graphics2D) g0.create();
            Aqua.paintFocusRing(g2, 3, 0, getWidth() - 6, getHeight() - 4, 10);
            g2.dispose();
        }
    }
}
