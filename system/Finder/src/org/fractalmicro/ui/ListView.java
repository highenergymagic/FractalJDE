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
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** List view: name, date modified, size and kind, in a sortable table. */
public class ListView extends JScrollPane implements FileView {

    private final Model model = new Model();
    private final JTable table = new JTable(model);

    public ListView(Consumer<Node> onOpen) {
        table.setRowHeight(Aqua.LIST_ROW_HEIGHT);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setFont(Aqua.viewFont());
        table.getTableHeader().setFont(Aqua.smallFont());
        table.getTableHeader().setReorderingAllowed(false);
        table.getAccessibleContext().setAccessibleName(word(FMString.of("finder.listView")));

        table.getColumnModel().getColumn(0).setPreferredWidth(280);
        table.getColumnModel().getColumn(0).setCellRenderer(new NameRenderer());
        table.getColumnModel().getColumn(1).setPreferredWidth(160);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);

        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0) onOpen.accept(model.rows.get(table.convertRowIndexToModel(row)));
                }
            }
        });
        table.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "rename");
        table.getActionMap().put("rename", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { renameSelection(); }
        });

        setViewportView(table);
        setBorder(BorderFactory.createEmptyBorder());
        getViewport().setBackground(Color.WHITE);
    }

    public JTable table() { return table; }

    /** Renames the selected row in its own name column, where the name is shown. */
    public void renameSelection() {
        int row = table.getSelectedRow();
        if (row < 0) {
            Finder.beep();
            return;
        }
        java.util.List<Node> selected = selection();
        if (selected.isEmpty()) return;
        Rectangle cell = table.getCellRect(row, 0, false);
        // The name column starts past the icon, so the field starts there too.
        Rectangle name = new Rectangle(cell.x + 20, cell.y, Math.max(120, cell.width - 24),
                                       cell.height);
        NameEditor.begin(table, name, selected.get(0), table::repaint);
    }

    @Override public void allowDragging(java.util.function.Supplier<java.io.File> showing) {
        FileDrops.install(table, row -> row < 0 || row >= model.rows.size() ? null : model.rows.get(row),
                          showing);
    }

    @Override public JComponent component() { return this; }

    @Override public void setContents(List<Node> nodes) {
        model.rows = new ArrayList<>(nodes);
        model.fireTableDataChanged();
    }

    @Override public List<Node> selection() {
        List<Node> out = new ArrayList<>();
        for (int row : table.getSelectedRows()) out.add(model.rows.get(table.convertRowIndexToModel(row)));
        return out;
    }

    @Override public void selectAll() {
        if (model.getRowCount() > 0) table.setRowSelectionInterval(0, model.getRowCount() - 1);
    }

    @Override public void focusView() { table.requestFocusInWindow(); }

    @Override public void arrangeBy(String key) {
        List<Node> all = new ArrayList<>(model.rows);
        FS.sort(all, key);
        setContents(all);
    }

    @Override public void setIconSize(int px) { /* list view keeps its small icons */ }

    public void setPopup(JPopupMenu menu) { table.setComponentPopupMenu(menu); }

    public void onSelectionChange(Runnable r) {
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) r.run();
        });
    }


    private static String word(FMString key) {
        return FMLocalized.of(key).toString();
    }

    private static class Model extends AbstractTableModel {
        private final String[] columns = {
            word(FMString.of("browser.name")), word(FMString.of("browser.dateModified")),
            word(FMString.of("browser.size")), word(FMString.of("browser.kind"))};
        private List<Node> rows = new ArrayList<>();

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int c) { return columns[c]; }

        @Override public Class<?> getColumnClass(int c) {
            return c == 0 ? Node.class : String.class;
        }

        @Override public Object getValueAt(int r, int c) {
            Node n = rows.get(r);
            switch (c) {
                case 0: return n;
                case 1: return FS.formatDate(n.modified);
                case 2: return n.size >= 0 ? FS.formatBytes(n.size) : "--";
                default: return n.kindLabel();
            }
        }

        private String capitalise(String s) {
            return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
    }

    /** Name column: small icon, the name, and an accessible description with the rest. */
    private static class NameRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                                 boolean focused, int row, int column) {
            Node n = (Node) value;
            super.getTableCellRendererComponent(t, n == null ? "" : n.name, selected, focused, row, column);
            if (n != null) {
                setIcon(new ImageIcon(Icons.forNode(n, 16)));
                getAccessibleContext().setAccessibleName(n.accessibleName());
                getAccessibleContext().setAccessibleDescription(
                    FMLocalized.filled(FMString.of("finder.selectedKind"),
                                       FMString.of(n.kindPhrase())).toString());
            }
            setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
            // The row a drag would drop into, drawn behind the name so it does not look
            // like a second selection while the first one is what is being dragged.
            JTable.DropLocation drop = t.getDropLocation();
            boolean aimedAt = drop != null && drop.getRow() == row && n != null
                && (n.isContainer() || n.kind == Node.Kind.TRASH);
            setBackground(aimedAt
                ? new Color(Aqua.SELECTION.getRed(), Aqua.SELECTION.getGreen(),
                            Aqua.SELECTION.getBlue(), 90)
                : selected ? t.getSelectionBackground() : t.getBackground());
            setOpaque(true);
            return this;
        }
    }
}
