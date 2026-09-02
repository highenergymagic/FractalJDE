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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Cover Flow: the sliding row of large icons above a list. The list underneath is the
 * same table used by list view, so what the view actually holds is the table
 * rather than the animation.
 */
public class CoverFlowView extends JPanel implements FileView {

    private final ListView list;
    private final Flow flow = new Flow();

    public CoverFlowView(Consumer<Node> onOpen) {
        super(new BorderLayout());
        list = new ListView(onOpen);
        add(flow, BorderLayout.NORTH);
        add(list, BorderLayout.CENTER);
        list.onSelectionChange(() -> {
            flow.setSelection(list.selection());
            flow.repaint();
        });
        getAccessibleContext().setAccessibleName("Cover Flow view");
    }

    @Override public JComponent component() { return this; }

    @Override public void setContents(List<Node> nodes) {
        list.setContents(nodes);
        flow.setAll(nodes);
    }

    @Override public void allowDragging(java.util.function.Supplier<java.io.File> showing) {
        list.allowDragging(showing);
    }

    @Override public List<Node> selection() { return list.selection(); }
    @Override public void selectAll() { list.selectAll(); }
    @Override public void focusView() { list.focusView(); }
    @Override public void arrangeBy(String key) { list.arrangeBy(key); }
    @Override public void setIconSize(int px) { }

    public void setPopup(JPopupMenu menu) { list.setPopup(menu); }
    public void onSelectionChange(Runnable r) { list.onSelectionChange(r); }

    /** The reflective strip of covers. Decorative; the table below carries the content. */
    private static class Flow extends JPanel {
        private List<Node> all = new ArrayList<>();
        private Node selected;

        Flow() {
            setPreferredSize(new Dimension(100, 180));
            getAccessibleContext().setAccessibleName("Cover Flow preview");
        }

        void setAll(List<Node> nodes) {
            all = new ArrayList<>(nodes);
            selected = all.isEmpty() ? null : all.get(0);
            repaint();
        }

        void setSelection(List<Node> sel) {
            selected = sel.isEmpty() ? selected : sel.get(0);
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Aqua.antialias(g);
            int w = getWidth(), h = getHeight();
            g.setPaint(new GradientPaint(0, 0, new Color(0x3A3A3A), 0, h, new Color(0x101010)));
            g.fillRect(0, 0, w, h);
            if (all.isEmpty()) { g.dispose(); return; }

            int centre = Math.max(0, all.indexOf(selected));
            int cx = w / 2;
            for (int offset = -3; offset <= 3; offset++) {
                int idx = centre + offset;
                if (idx < 0 || idx >= all.size()) continue;
                int size = offset == 0 ? 120 : 78;
                int x = cx + offset * 86 - size / 2;
                int y = (h - size) / 2 - 6;
                Image img = Icons.forNode(all.get(idx), size);
                g.drawImage(img, x, y, null);
                Composite old = g.getComposite();
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
                g.drawImage(img, x, y + size + size / 3, size, -size / 3, null);
                g.setComposite(old);
            }
            g.dispose();
        }
    }
}
