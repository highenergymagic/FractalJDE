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

import org.fractalmicro.appkit.FMDragOperation;
import org.fractalmicro.appkit.FMFileDragging;
import org.fractalmicro.foundation.FMString;

import org.fractalmicro.fs.FS;
import org.fractalmicro.fs.Places;
import org.fractalmicro.foundation.FMLocalized;
import org.fractalmicro.fs.Node;
import org.fractalmicro.fs.Volumes;
import org.fractalmicro.os.FMUserDefaults;
import org.fractalmicro.os.FinderSettings;
import org.fractalmicro.theme.Aqua;
import org.fractalmicro.theme.Icons;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The sidebar: a source list of devices, places and searches.
 *
 * It is a table with one column, not a tree. That is what it is in the system this
 * imitates, where a source list is a table with group rows in it, and it is what it should be
 * here for the same reason: a tree announces itself as a tree,
 * with expanding and collapsing and levels, and none of that is true of this. There is
 * nothing to expand. There are headings and there are places, in one flat column, and a
 * table says exactly that.
 *
 * A heading is a row that cannot be selected and is skipped by the arrow keys, so moving
 * through the list goes from one place to the next without stopping on words that do not
 * lead anywhere.
 */
public class Sidebar extends JScrollPane {

    /** One place in the list: what it is called and what it opens. */
    public static class Target {
        public final String label;
        public final File file;
        public final String special;
        public final Node.Kind kind;

        public Target(String label, File file, String special, Node.Kind kind) {
            this.label = label;
            this.file = file;
            this.special = special;
            this.kind = kind;
        }

        @Override public String toString() { return label; }
    }

    /** One row: either a heading, or a place. */
    private record Row(String heading, Target target) {
        boolean isHeading() { return target == null; }

        String label() { return isHeading() ? heading : target.label; }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Model model = new Model();
    private final JTable table = new JTable(model) {
        @Override public void paint(Graphics g) {
            super.paint(g);
            if (!insertingHere || insertAbove < 0) return;
            int y = insertAbove >= getRowCount()
                ? getRowCount() * getRowHeight()
                : getCellRect(insertAbove, 0, true).y;
            Graphics2D line = (Graphics2D) g.create();
            Aqua.antialias(line);
            line.setColor(Aqua.SELECTION);
            line.fillRect(2, Math.max(0, y - 1), getWidth() - 4, 2);
            line.fillOval(0, Math.max(0, y - 3), 6, 6);
            line.dispose();
        }
    };
    private final Consumer<Target> onSelect;

    public Sidebar(Consumer<Target> onSelect) {
        this.onSelect = onSelect;

        table.setRowHeight(20);
        table.setBackground(Aqua.SIDEBAR_BG);
        table.setFont(Aqua.viewFont());
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setTableHeader(null);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(Object.class, new Renderer());
        // Named, and nothing more. The role already says it is a table, and instructions
        // for which keys to press are repeated every time the sidebar is reached while
        // being useful exactly once.
        table.getAccessibleContext().setAccessibleName("Sidebar");

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            Row row = rowAt(table.getSelectedRow());
            if (row != null && !row.isHeading()) onSelect.accept(row.target());
        });

        // The arrow keys skip the headings, because a heading is not somewhere to go.
        table.getInputMap(JComponent.WHEN_FOCUSED)
             .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0), "nextPlace");
        table.getInputMap(JComponent.WHEN_FOCUSED)
             .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0), "previousPlace");
        table.getActionMap().put("nextPlace", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { move(1); }
        });
        table.getActionMap().put("previousPlace", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { move(-1); }
        });

        allowDrops();

        setViewportView(table);
        setBorder(BorderFactory.createEmptyBorder());
        getViewport().setBackground(Aqua.SIDEBAR_BG);
        setPreferredSize(new Dimension(180, 400));

        rebuild();
        FMUserDefaults.onChange((domain, key) -> rebuild());
        Volumes.onChange(this::rebuild);
    }

    /** The table itself, for anything that needs the control rather than the pane. */
    public JTable table() { return table; }

    /**
     * Dropping onto the sidebar, which means one of two things depending on where in it.
     *
     * Onto a place: the files go into that folder, the same as dropping onto its icon in a
     * window. This is what the sidebar is for. Somebody filing a download into Documents
     * should not have to open Documents first.
     *
     * Between two places: the folder joins the list. A shortcut rather than a copy of
     * anything, so it is not a file operation at all and none of the move-or-copy rules
     * apply to it.
     *
     * Which one is meant is read from where in the row the pointer is, the same way every
     * list that can be both dropped on and inserted into reads it: near an edge means
     * between, the middle means on. It has to be a quarter of a twenty pixel row, so the
     * answer is five pixels either way, which is why the highlight showing which it will be
     * is not a nicety.
     */
    private void allowDrops() {
        FMFileDragging.install(table, null, new FMFileDragging.IntoFolders() {
            @Override public void aimedAt(boolean yes) {
                if (!yes) showInsertion(null);
            }

            @Override public FMDragOperation operationAt(Point where, List<File> files, int keys) {
                if (files.isEmpty()) return FMDragOperation.NONE;
                showInsertion(where);
                if (wouldJoinTheList(where)) {
                    for (File f : files) if (f.isDirectory()) return FMDragOperation.LINK;
                    return FMDragOperation.NONE;
                }
                return super.operationAt(where, files, keys);
            }

            @Override public boolean take(Point where, List<File> files, FMDragOperation how) {
                boolean joining = wouldJoinTheList(where);
                showInsertion(null);
                if (!joining) return super.take(where, files, how);
                List<Node> folders = new ArrayList<>();
                for (File f : files) if (f.isDirectory()) folders.add(FS.node(f));
                if (folders.isEmpty()) return false;
                Finder.addToSidebar(folders);
                return true;
            }

            @Override protected File folderAt(Point where) {
                Row row = rowAt(table.rowAtPoint(where));
                if (row == null || row.isHeading()) return null;
                File file = row.target().file;
                return file != null && file.isDirectory() ? file : null;
            }

            @Override protected boolean receive(List<File> files, File into, FMDragOperation how) {
                return Finder.receiveDrop(files, into, how);
            }
        });
        table.setDropMode(javax.swing.DropMode.ON);
    }

    /**
     * Whether a drop here would add to the list rather than file something into a place.
     *
     * True near the top or bottom edge of a row, and true anywhere below the last one,
     * which is the empty part of the sidebar and the easiest place to aim for.
     */
    private boolean wouldJoinTheList(Point where) {
        int row = table.rowAtPoint(where);
        if (row < 0) return true;
        Rectangle bounds = table.getCellRect(row, 0, true);
        int edge = Math.max(3, bounds.height / 4);
        boolean nearEdge = where.y - bounds.y < edge
                        || bounds.y + bounds.height - where.y < edge;
        Row here = rowAt(row);
        return nearEdge || here == null || here.isHeading();
    }

    private Row rowAt(int index) {
        return index < 0 || index >= rows.size() ? null : rows.get(index);
    }

    /** Moves to the next place in a direction, stepping over any headings on the way. */
    private void move(int by) {
        int from = table.getSelectedRow();
        int at = from < 0 ? (by > 0 ? -1 : rows.size()) : from;
        for (int i = at + by; i >= 0 && i < rows.size(); i += by) {
            if (!rows.get(i).isHeading()) {
                table.setRowSelectionInterval(i, i);
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
                return;
            }
        }
    }

    /* -------------------------------------------------------------- content */

    /**
     * Fills the sidebar from the one list of places this system keeps.
     *
     * The list used to be written out here, which meant the save panel showed a different
     * set: somebody who dragged a folder in found it in the Finder and not when they went
     * to save something into it. There is one list now, in Foundation, and both read it.
     */
    public void rebuild() {
        Target chosen = selectedTarget();

        // Built to one side and put in at the end, rather than emptied and refilled.
        // Reading the places touches the disk, and a repaint that lands in the middle of
        // that asks a table that still thinks it has sixteen rows for a list that has
        // none. It threw where it was painting, which is nowhere anybody can catch it.
        List<Row> built = new ArrayList<>();
        for (Places.Group section : Places.all()) {
            List<Row> made = new ArrayList<>();
            for (Places.Place one : section.places()) {
                made.add(place(new Target(one.name().toString(), one.file(),
                                          one.token().isEmpty() ? null : one.token().toString(),
                                          one.kind())));
            }
            group(built, FMLocalized.of(section.heading()).toString(), made);
        }
        group(built, "TRASH",
              List.of(place(new Target("Trash", null, "trash", Node.Kind.TRASH))));

        rows.clear();
        rows.addAll(built);
        model.fireTableDataChanged();
        reselect(chosen);
    }

    private void group(List<Row> into, String heading, List<Row> members) {
        if (members.isEmpty()) return;
        into.add(new Row(heading, null));
        into.addAll(members);
    }

    private Row place(Target target) { return new Row(null, target); }

    private Target selectedTarget() {
        Row row = rowAt(table.getSelectedRow());
        return row == null ? null : row.target();
    }

    /** Keeps the same place selected across a rebuild, when it is still there. */
    private void reselect(Target was) {
        if (was == null) return;
        for (int i = 0; i < rows.size(); i++) {
            Target target = rows.get(i).target();
            if (target != null && target.label.equals(was.label)) {
                table.setRowSelectionInterval(i, i);
                return;
            }
        }
    }

    /** How many rows there are, headings included, for anything that wants to know. */
    public int rowCount() { return rows.size(); }

    /* --------------------------------------------------------------- the table */

    private final class Model extends AbstractTableModel {
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return 1; }
        @Override public String getColumnName(int column) { return "Place"; }
        @Override public Object getValueAt(int row, int column) { return rows.get(row).label(); }
        @Override public boolean isCellEditable(int row, int column) { return false; }
    }

    /**
     * A heading is drawn small and grey; a place is drawn with its icon. The name says
     * which of the two a row is, because "DEVICES" on its own sounds like somewhere to
     * go and is not.
     */
    private final class Renderer extends JLabel implements TableCellRenderer {

        Renderer() {
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 6));
        }

        @Override public Component getTableCellRendererComponent(JTable t, Object value,
                boolean selected, boolean focused, int row, int column) {
            Row entry = rowAt(row);
            String label = value == null ? "" : String.valueOf(value);
            setText(label);

            if (entry != null && entry.isHeading()) {
                setFont(Aqua.emphasizedSmallFont());
                setForeground(Aqua.SIDEBAR_HEADER);
                setBackground(Aqua.SIDEBAR_BG);
                setIcon(null);
                getAccessibleContext().setAccessibleName(label + ", heading");
                getAccessibleContext().setAccessibleDescription(null);
            } else {
                setFont(Aqua.viewFont());
                setForeground(selected ? Color.WHITE : Aqua.SIDEBAR_TEXT);
                setBackground(selected ? Aqua.SELECTION : Aqua.SIDEBAR_BG);
                Node.Kind kind = entry == null ? Node.Kind.FOLDER : entry.target().kind;
                setIcon(new ImageIcon(Icons.forKind(kind, 16)));
                getAccessibleContext().setAccessibleName(label);
                getAccessibleContext().setAccessibleDescription(
                    selected ? "selected" : null);
            }
            // The place a drop would go into. Only shown when the drop would file something
            // there rather than add to the list, since those two are told apart by five
            // pixels and a person aiming at one has to be able to see which they have.
            JTable.DropLocation drop = t.getDropLocation();
            if (drop != null && drop.getRow() == row && entry != null && !entry.isHeading()
                    && !insertingHere) {
                setBackground(new Color(Aqua.SELECTION.getRed(), Aqua.SELECTION.getGreen(),
                                        Aqua.SELECTION.getBlue(), 90));
            }
            return this;
        }
    }

    /**
     * The line drawn between two places while a drag over the sidebar would add to the list.
     *
     * Painted over the table rather than by the renderer, because it belongs between two
     * rows and a renderer only ever draws inside one. It is the same line every list that
     * can be inserted into draws, and it is here for the same reason: without it, whether a
     * folder is about to be filed into Documents or added below Documents is a guess.
     */
    private boolean insertingHere;
    private int insertAbove = -1;

    private void showInsertion(Point where) {
        boolean inserting = where != null && wouldJoinTheList(where);
        int row = where == null ? -1 : table.rowAtPoint(where);
        if (row < 0 && where != null) row = table.getRowCount();
        int above = inserting ? row : -1;
        if (inserting == insertingHere && above == insertAbove) return;
        insertingHere = inserting;
        insertAbove = above;
        table.repaint();
    }
}
