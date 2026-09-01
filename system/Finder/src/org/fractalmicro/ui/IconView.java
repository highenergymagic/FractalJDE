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

import org.fractalmicro.os.FinderSettings;
import org.fractalmicro.fs.FS;
import org.fractalmicro.fs.Node;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Icon view: a wrapping grid of icons, backed by a JList so keyboard use is free. */
public class IconView extends JScrollPane implements FileView {

    private final DefaultListModel<Node> model = new DefaultListModel<>();
    private final JList<Node> list = new JList<>(model);
    private final IconCellRenderer renderer;
    private String sortKey = "name";
    private int iconSize;

    public IconView(Consumer<Node> onOpen) {
        iconSize = FinderSettings.windowIconSize();
        renderer = new IconCellRenderer(false, iconSize);
        list.setCellRenderer(renderer);
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1);
        list.setFixedCellWidth(iconSize + 44);
        list.setFixedCellHeight(iconSize + 30);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setBackground(Color.WHITE);
        list.getAccessibleContext().setAccessibleName("Icon view");

        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int i = list.locationToIndex(e.getPoint());
                    if (i >= 0 && list.getCellBounds(i, i).contains(e.getPoint())) onOpen.accept(model.get(i));
                }
            }
            @Override public void mouseReleased(MouseEvent e) {
                // A single click on something already selected, a moment after it was
                // selected, is a rename. Any quicker and it is half of a double click.
                if (e.getClickCount() != 1 || e.isPopupTrigger()) return;
                int i = list.locationToIndex(e.getPoint());
                if (i < 0 || i != list.getSelectedIndex()) return;
                if (!list.getCellBounds(i, i).contains(e.getPoint())) return;
                if (e.getWhen() - selectedAt < NameEditor.SLOW_CLICK) return;
                renameSelection();
            }
        });
        list.addListSelectionListener(e -> selectedAt = System.currentTimeMillis());
        list.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "rename");
        list.getActionMap().put("rename", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { renameSelection(); }
        });

        setViewportView(list);
        setBorder(BorderFactory.createEmptyBorder());
        getViewport().setBackground(Color.WHITE);
        getVerticalScrollBar().setUnitIncrement(24);
    }

    public JList<Node> iconList() { return list; }

    /** When the selection last changed, to tell a slow click from a double one. */
    private long selectedAt;

    /** Renames the selected item under its icon, where the name is. */
    public void renameSelection() {
        int index = list.getSelectedIndex();
        if (index < 0) {
            Finder.beep("Select an item to rename.");
            return;
        }
        Rectangle cell = list.getCellBounds(index, index);
        FontMetrics metrics = list.getFontMetrics(list.getFont());
        int height = metrics.getHeight() + 4;
        Rectangle name = new Rectangle(cell.x + 4, cell.y + cell.height - height - 4,
                                       cell.width - 8, height);
        NameEditor.begin(list, name, list.getSelectedValue(), list::repaint);
    }

    @Override public JComponent component() { return this; }

    @Override public void setContents(List<Node> nodes) {
        List<Node> sorted = new ArrayList<>(nodes);
        FS.sort(sorted, sortKey);
        model.clear();
        for (Node n : sorted) model.addElement(n);
    }

    @Override public List<Node> selection() { return new ArrayList<>(list.getSelectedValuesList()); }

    @Override public void selectAll() {
        if (model.isEmpty()) return;
        list.setSelectionInterval(0, model.size() - 1);
    }

    @Override public void focusView() { list.requestFocusInWindow(); }

    @Override public void arrangeBy(String key) {
        sortKey = key;
        List<Node> all = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) all.add(model.get(i));
        setContents(all);
    }

    @Override public void setIconSize(int px) {
        iconSize = px;
        renderer.setIconSize(px);
        list.setFixedCellWidth(px + 44);
        list.setFixedCellHeight(px + 30);
        list.revalidate();
        list.repaint();
    }

    public void setPopup(JPopupMenu menu) { list.setComponentPopupMenu(menu); }

    public void onSelectionChange(Runnable r) {
        list.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) r.run(); });
    }
}
