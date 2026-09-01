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
import javax.swing.plaf.basic.BasicTableHeaderUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;

/**
 * The column headings of a list view: a light gradient, hairline dividers, and the
 * triangle on the column the list is sorted by.
 *
 * The heading is a renderer rather than a painted delegate, which is how the header keeps
 * its text. A painted one has only the drawing, and a drawing of a word is not a word.
 */
public class AquaTableHeaderUI extends BasicTableHeaderUI {

    public static final int HEIGHT = 17;

    public static ComponentUI createUI(JComponent c) { return new AquaTableHeaderUI(); }

    @Override public void installUI(JComponent c) {
        super.installUI(c);
        JTableHeader h = (JTableHeader) c;
        h.setDefaultRenderer(new Renderer());
        h.setPreferredSize(new Dimension(h.getPreferredSize().width, HEIGHT));
        h.setReorderingAllowed(true);
    }

    /** One heading. */
    public static final class Renderer extends DefaultTableCellRenderer {

        @Override public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setFont(Aqua.smallFont());
            setForeground(new Color(0x1E1E1E));
            setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
            setHorizontalAlignment(SwingConstants.LEADING);
            boolean sorted = isSortColumn(table, column);
            putClientProperty("sorted", sorted ? sortAscending(table) : null);
            return this;
        }

        private boolean isSortColumn(JTable table, int column) {
            if (table == null || table.getRowSorter() == null) return false;
            java.util.List<? extends javax.swing.RowSorter.SortKey> keys =
                table.getRowSorter().getSortKeys();
            if (keys.isEmpty() || column < 0) return false;
            return keys.get(0).getColumn() == table.convertColumnIndexToModel(column);
        }

        private Boolean sortAscending(JTable table) {
            java.util.List<? extends javax.swing.RowSorter.SortKey> keys =
                table.getRowSorter().getSortKeys();
            return keys.isEmpty() ? null
                 : keys.get(0).getSortOrder() == javax.swing.SortOrder.ASCENDING;
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Aqua.antialias(g);
            int w = getWidth();
            int h = getHeight();
            g.setPaint(new GradientPaint(0, 0, Color.WHITE, 0, h, new Color(0xDCDCDC)));
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(0xB4B4B4));
            g.drawLine(w - 1, 2, w - 1, h - 3);
            g.setColor(new Color(0x9A9A9A));
            g.drawLine(0, h - 1, w, h - 1);

            Object sorted = getClientProperty("sorted");
            if (sorted instanceof Boolean up) {
                int x = w - 14;
                int y = h / 2;
                g.setColor(new Color(0x5A5A5A));
                if (up) {
                    g.fillPolygon(new int[]{x, x + 8, x + 4}, new int[]{y + 2, y + 2, y - 3}, 3);
                } else {
                    g.fillPolygon(new int[]{x, x + 8, x + 4}, new int[]{y - 3, y - 3, y + 2}, 3);
                }
            }
            g.dispose();
            super.paintComponent(g0);
        }

        @Override public boolean isOpaque() { return false; }
    }

    /** Fills the space to the right of the last column, so the strip runs the full width. */
    @Override public void paint(Graphics g0, JComponent c) {
        JTableHeader header = (JTableHeader) c;
        int used = 0;
        for (int i = 0; i < header.getColumnModel().getColumnCount(); i++) {
            TableColumn column = header.getColumnModel().getColumn(i);
            used += column.getWidth();
        }
        if (used < header.getWidth()) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setPaint(new GradientPaint(0, 0, Color.WHITE, 0, header.getHeight(),
                                         new Color(0xDCDCDC)));
            g.fillRect(used, 0, header.getWidth() - used, header.getHeight());
            g.setColor(new Color(0x9A9A9A));
            g.drawLine(used, header.getHeight() - 1, header.getWidth(), header.getHeight() - 1);
            g.dispose();
        }
        super.paint(g0, c);
    }

    /** Only so the type is named somewhere: the renderer above is the header's own. */
    public static TableCellRenderer renderer() { return new Renderer(); }
}
