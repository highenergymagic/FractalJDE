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

import org.fractalmicro.foundation.FMLocalized;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.fs.FS;
import org.fractalmicro.fs.Node;
import org.fractalmicro.theme.Aqua;
import org.fractalmicro.theme.Icons;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Column view: one list per level, each new selection opening the next column to the
 * right. Left and right arrows walk between columns, which is how it behaves on a Mac.
 */
public class ColumnView extends JScrollPane implements FileView {

    private static final int COLUMN_WIDTH = 210;

    private final JPanel strip = new JPanel();
    private final List<JList<Node>> columns = new ArrayList<>();
    private final Consumer<Node> onOpen;
    private final PreviewPane preview = new PreviewPane();
    private String sortKey = "name";
    private java.util.function.Supplier<java.io.File> showing;
    private JPopupMenu popup;
    private Runnable selectionListener = () -> {};

    public ColumnView(Consumer<Node> onOpen) {
        this.onOpen = onOpen;
        strip.setLayout(new BoxLayout(strip, BoxLayout.X_AXIS));
        strip.setBackground(Color.WHITE);
        setViewportView(strip);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_ALWAYS);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_NEVER);
        setBorder(BorderFactory.createEmptyBorder());
        getAccessibleContext().setAccessibleName(word(FMString.of("finder.columnView")));
    }

    @Override public void allowDragging(java.util.function.Supplier<java.io.File> showing) {
        this.showing = showing;
    }

    @Override public JComponent component() { return this; }

    @Override public void setContents(List<Node> nodes) {
        strip.removeAll();
        columns.clear();
        addColumn(nodes, 0, showing == null ? null : showing.get());
        strip.add(preview);
        strip.revalidate();
        strip.repaint();
    }

    private void addColumn(List<Node> nodes, int level, java.io.File folder) {
        List<Node> sorted = new ArrayList<>(nodes);
        FS.sort(sorted, sortKey);
        DefaultListModel<Node> model = new DefaultListModel<>();
        for (Node n : sorted) model.addElement(n);

        JList<Node> list = new JList<>(model);
        list.setCellRenderer(new RowRenderer());
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setBackground(Color.WHITE);
        list.setFixedCellHeight(Aqua.LIST_ROW_HEIGHT);
        list.getAccessibleContext().setAccessibleName(
            FMLocalized.filled(FMString.of("finder.column"),
                               FMString.of(String.valueOf(level + 1))).toString());
        if (popup != null) list.setComponentPopupMenu(popup);

        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            trimTo(level);
            Node sel = list.getSelectedValue();
            if (sel != null && sel.isContainer() && sel.file != null) {
                addColumn(FS.list(sel.file), level + 1, sel.file);
            } else {
                preview.show(sel);
            }
            strip.revalidate();
            strip.repaint();
            selectionListener.run();
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && list.getSelectedValue() != null) {
                    onOpen.accept(list.getSelectedValue());
                }
            }
        });
        list.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "intoColumn");
        list.getActionMap().put("intoColumn", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                int next = columns.indexOf(list) + 1;
                if (next < columns.size()) {
                    JList<Node> target = columns.get(next);
                    target.requestFocusInWindow();
                    if (target.getModel().getSize() > 0 && target.getSelectedIndex() < 0) {
                        target.setSelectedIndex(0);
                    }
                }
            }
        });
        list.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "outOfColumn");
        list.getActionMap().put("outOfColumn", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                int prev = columns.indexOf(list) - 1;
                if (prev >= 0) columns.get(prev).requestFocusInWindow();
            }
        });

        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(COLUMN_WIDTH, 100));
        sp.setMaximumSize(new Dimension(COLUMN_WIDTH, Integer.MAX_VALUE));
        sp.setMinimumSize(new Dimension(COLUMN_WIDTH, 60));
        sp.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0xC8C8C8)));

        strip.remove(preview);
        strip.add(sp);
        strip.add(preview);
        columns.add(list);
        // Each column shows a different folder, so each one is its own destination. A single
        // one for the view would file everything into whichever folder the leftmost column
        // happened to be showing.
        if (showing != null) FileDrops.install(list, () -> folder);
    }

    private void trimTo(int level) {
        while (columns.size() > level + 1) {
            JList<Node> last = columns.remove(columns.size() - 1);
            Container sp = SwingUtilities.getAncestorOfClass(JScrollPane.class, last);
            if (sp != null) strip.remove(sp);
        }
        preview.show(null);
    }

    @Override public List<Node> selection() {
        for (int i = columns.size() - 1; i >= 0; i--) {
            List<Node> sel = columns.get(i).getSelectedValuesList();
            if (!sel.isEmpty()) return new ArrayList<>(sel);
        }
        return new ArrayList<>();
    }

    @Override public void selectAll() {
        if (columns.isEmpty()) return;
        JList<Node> first = columns.get(0);
        if (first.getModel().getSize() > 0) first.setSelectionInterval(0, first.getModel().getSize() - 1);
    }

    @Override public void focusView() {
        if (!columns.isEmpty()) columns.get(0).requestFocusInWindow();
    }


    private static String word(FMString key) {
        return FMLocalized.of(key).toString();
    }

    @Override public void arrangeBy(String key) { sortKey = key; }

    @Override public void setIconSize(int px) { }

    public void setPopup(JPopupMenu menu) {
        popup = menu;
        for (JList<Node> l : columns) l.setComponentPopupMenu(menu);
    }

    public void onSelectionChange(Runnable r) { selectionListener = r; }

    /** One row in a column, with the arrow that means "there is more inside". */
    private static class RowRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                boolean selected, boolean focused) {
            Node n = (Node) value;
            super.getListCellRendererComponent(list, n.name, index, selected, focused);
            setIcon(new ImageIcon(Icons.forNode(n, 16)));
            setFont(Aqua.viewFont());
            getAccessibleContext().setAccessibleName(n.accessibleName());
            getAccessibleContext().setAccessibleDescription(
                FMLocalized.filled(FMString.of("finder.selectedKind"),
                                   FMString.of(n.kindPhrase())).toString());
            // The folder a drag would drop into. It matters more here than in the other
            // views: the columns are narrow, the rows are close together, and every row in
            // a column but the last is a folder, so aiming at the wrong one is easy.
            JList.DropLocation drop = list.getDropLocation();
            boolean aimedAt = drop != null && drop.getIndex() == index
                && (n.isContainer() || n.kind == Node.Kind.TRASH);
            if (aimedAt) {
                setBackground(new Color(Aqua.SELECTION.getRed(), Aqua.SELECTION.getGreen(),
                                        Aqua.SELECTION.getBlue(), 90));
                setOpaque(true);
            }
            return this;
        }
    }

    /** The rightmost pane, showing what is selected when it is not a folder. */
    private static class PreviewPane extends JPanel {
        private Node node;

        PreviewPane() {
            setPreferredSize(new Dimension(COLUMN_WIDTH, 100));
            setBackground(Color.WHITE);
            getAccessibleContext().setAccessibleName(FMLocalized.of(FMString.of("finder.preview")).toString());
        }

        void show(Node n) {
            node = n;
            getAccessibleContext().setAccessibleName(n == null ? "Preview" : n.name);
            repaint();
        }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            if (node == null) return;
            Graphics2D g = (Graphics2D) g0.create();
            Aqua.antialias(g);
            g.drawImage(Icons.forNode(node, 96), (getWidth() - 96) / 2, 24, null);
            g.setFont(Aqua.smallFont());
            g.setColor(Color.BLACK);
            FontMetrics fm = g.getFontMetrics();
            String name = Aqua.clipMiddle(fm, node.name, getWidth() - 16);
            g.drawString(name, (getWidth() - fm.stringWidth(name)) / 2, 140);
            String detail = node.detail;
            if (detail != null && !detail.isEmpty()) {
                g.setColor(new Color(0x5A5A5A));
                g.drawString(detail, (getWidth() - fm.stringWidth(detail)) / 2, 158);
            }
            g.dispose();
        }
    }
}
