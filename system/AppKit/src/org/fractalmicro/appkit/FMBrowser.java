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
package org.fractalmicro.appkit;

import org.fractalmicro.foundation.FMLocalized;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.fs.FS;
import org.fractalmicro.fs.Kinds;
import org.fractalmicro.fs.Node;
import org.fractalmicro.theme.Aqua;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A column browser: the way this system has shown a tree since NeXTSTEP.
 *
 * One column per level, side by side. Choosing a folder in a column opens the next one
 * beside it; choosing something that is not a folder is the end of that path. What is on
 * screen is therefore the whole route rather than the destination, which is the thing a
 * list cannot show: in a list you know what is in the folder you are in and nothing about
 * how you got there or what was beside it on the way.
 *
 * The columns scroll sideways as a group, so going deep pushes the earlier ones off the
 * left rather than shrinking any of them. The last column is kept in view, because that is
 * the one being worked in.
 *
 * Each column is a real list with a name, so moving through it with the keyboard works the
 * way moving through any list does, and anything reading the screen says which column it is
 * in and what is in it.
 */
public final class FMBrowser extends JPanel {

    /**
     * The three ways of showing a folder, and what each is for.
     *
     * Icons for recognising something by its shape, a list for comparing files against
     * each other by their dates and sizes, columns for finding your way through a tree.
     * They are the Finder's three and the panel's three because they are the same
     * question asked in the same places, and the keys that choose them are the same too.
     */
    public enum Mode { ICON, LIST, COLUMN }

    /** How wide one column is, and how many are in view before it scrolls. */
    private static final int COLUMN_WIDTH = 190;

    private final List<JList<Node>> columns = new ArrayList<>();
    private final JPanel row = new JPanel();
    private final JScrollPane scroller;

    private File root;
    private Mode mode = Mode.COLUMN;
    private FMString searching = FMString.EMPTY;
    private final JPanel flat = new JPanel(new BorderLayout());
    private JList<Node> flatList;
    private JTable flatTable;
    private Predicate<File> shows = file -> true;
    private Consumer<File> onChosen = file -> { };
    private Consumer<File> onOpened = file -> { };

    public FMBrowser() {
        super(new BorderLayout());
        setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(true);
        row.setBackground(Color.WHITE);
        scroller = new JScrollPane(row,
            JScrollPane.VERTICAL_SCROLLBAR_NEVER,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroller.setBorder(BorderFactory.createLineBorder(new Color(0xA0A0A0)));
        add(scroller, BorderLayout.CENTER);
        getAccessibleContext().setAccessibleName(
            FMLocalized.of(FMSavePanel.FILES).toString());
        installActions();
    }

    /* ------------------------------------------------------------ what it can do */

    /** Showing it as icons, as a list, as columns, and going up a level. */
    public static final String AS_ICONS = "viewAsIcons";
    public static final String AS_LIST = "viewAsList";
    public static final String AS_COLUMNS = "viewAsColumns";
    public static final String ENCLOSING_FOLDER = "goUp";

    /**
     * The four things a browser can be asked to do, in its own action map.
     *
     * Named rather than called, so that one name reaches them from three directions: a
     * menu item in an interface file, a key bound in this window, and a program in another
     * process saying perform. None of the three needs a method on this class, and the
     * program in the other process could not have one anyway.
     *
     * They are the selectors the Finder's menus already use, because these are those
     * commands. A second set of names for the same four things would be a second set to
     * keep in step.
     */
    private void installActions() {
        ActionMap actions = getActionMap();
        actions.put(AS_ICONS, does(() -> setMode(Mode.ICON)));
        actions.put(AS_LIST, does(() -> setMode(Mode.LIST)));
        actions.put(AS_COLUMNS, does(() -> setMode(Mode.COLUMN)));
        actions.put(ENCLOSING_FOLDER, does(this::goUp));
    }

    private static Action does(Runnable what) {
        return new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { what.run(); }
        };
    }

    /** Which of the three ways this browser is showing things. */
    public Mode mode() { return mode; }

    /**
     * Shows the same folder a different way.
     *
     * The folder does not change, only how it is drawn, so somebody who switches views is
     * still looking at what they were looking at. That is the whole of what makes the
     * three interchangeable rather than three different places to be.
     */
    public void setMode(Mode wanted) {
        if (wanted == null || wanted == mode) return;
        File showing = currentFolder();
        mode = wanted;
        removeAll();
        add(mode == Mode.COLUMN ? scroller : flat, BorderLayout.CENTER);
        if (mode == Mode.COLUMN) setRoot(showing);
        else showFlat(showing);
        revalidate();
        repaint();
    }

    /**
     * Narrows what is shown to the things whose names contain this.
     *
     * Nothing typed shows everything. What is searched is the folder being looked at
     * rather than the whole disk: a panel is a place to find something you know is there,
     * and the thing that searches everywhere is Spotlight.
     */
    public void search(FMString text) {
        this.searching = text == null ? FMString.EMPTY : text;
        File showing = currentFolder();
        if (mode == Mode.COLUMN) setRoot(showing); else showFlat(showing);
    }

    public FMString searching() { return searching; }

    /** Which files are worth showing. Folders are always shown whatever this says. */
    public FMBrowser showing(Predicate<File> which) {
        this.shows = which == null ? file -> true : which;
        return this;
    }

    /** Told when the selection moves, whether to a folder or to a file. */
    public FMBrowser onChosen(Consumer<File> what) {
        this.onChosen = what == null ? file -> { } : what;
        return this;
    }

    /** Told when something is opened outright, which is a double click. */
    public FMBrowser onOpened(Consumer<File> what) {
        this.onOpened = what == null ? file -> { } : what;
        return this;
    }

    /**
     * Shows a folder as the leftmost column, forgetting whatever was there.
     *
     * Used when the panel is sent somewhere else entirely: a place in the sidebar, or a
     * folder that has just been made. Walking to a folder inside what is already shown
     * adds a column rather than coming through here.
     */
    public void setRoot(File folder) {
        if (mode != Mode.COLUMN) {
            showFlat(folder);
            return;
        }
        this.root = folder;
        row.removeAll();
        columns.clear();
        if (folder != null) addColumn(folder);
        revalidate();
        repaint();
    }

    public File root() { return root; }

    /**
     * Shows a folder, keeping the columns that lead to it where they already do.
     *
     * Somebody who clicks into a folder wants the route they took to stay on screen. One
     * that is nowhere in the current route is a jump, and a jump starts again.
     */
    public void show(File folder) {
        if (folder == null) return;
        if (mode != Mode.COLUMN) { showFlat(folder); return; }
        for (int i = 0; i < columns.size(); i++) {
            Node showing = columns.get(i).getSelectedValue();
            if (showing != null && folder.equals(showing.file)) {
                trimTo(i + 1);
                addColumn(folder);
                return;
            }
        }
        setRoot(folder);
    }

    /** The folder the last column is showing, which is where a save would land. */
    public File currentFolder() {
        if (mode != Mode.COLUMN) return root;
        if (columns.isEmpty()) return root;
        for (int i = columns.size() - 1; i >= 0; i--) {
            File folder = folderOf(columns.get(i));
            if (folder != null) return folder;
        }
        return root;
    }

    /** What is chosen right now, or nothing when it is a folder being browsed. */
    public File selection() {
        if (mode == Mode.ICON && flatList != null) {
            Node picked = flatList.getSelectedValue();
            return picked == null ? null : picked.file;
        }
        if (mode == Mode.LIST && flatTable != null) {
            int at = flatTable.getSelectedRow();
            if (at < 0) return null;
            Object held = flatTable.getModel().getValueAt(at, -1);
            return held instanceof File file ? file : null;
        }
        for (int i = columns.size() - 1; i >= 0; i--) {
            Node chosen = columns.get(i).getSelectedValue();
            if (chosen != null && chosen.file != null) return chosen.file;
        }
        return null;
    }

    /** Goes up one level, which is what Command Up does in a Finder window. */
    public void goUp() {
        if (columns.size() > 1) {
            trimTo(columns.size() - 1);
            columns.get(columns.size() - 1).clearSelection();
            announce();
        } else if (root != null && root.getParentFile() != null) {
            setRoot(root.getParentFile());
        }
    }

    /* --------------------------------------------------------- icons and lists */

    /**
     * The two views that show one folder rather than a route.
     *
     * An icon view and a list view are the same contents laid out differently, so they are
     * built from the same rows and differ in what draws them. Neither shows where it came
     * from, which is what columns are for and why the panel opens in columns.
     */
    private void showFlat(File folder) {
        this.root = folder;
        flat.removeAll();
        List<Node> rows = rowsIn(folder);

        if (mode == Mode.ICON) {
            DefaultListModel<Node> model = new DefaultListModel<>();
            for (Node one : rows) model.addElement(one);
            flatList = new JList<>(model);
            flatList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
            flatList.setVisibleRowCount(-1);
            flatList.setFixedCellWidth(110);
            flatList.setFixedCellHeight(72);
            flatList.setFont(Aqua.systemFont());
            flatList.setCellRenderer(new IconRenderer());
            flatList.getAccessibleContext().setAccessibleName(
                folder == null ? "" : folder.getName());
            flatList.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) chosenFlat(flatList.getSelectedValue());
            });
            flatList.addMouseListener(opener(() -> flatList.getSelectedValue()));
            flat.add(new JScrollPane(flatList), BorderLayout.CENTER);
        } else {
            flatTable = new JTable(new RowsModel(rows));
            flatTable.setFont(Aqua.systemFont());
            flatTable.setRowHeight(20);
            flatTable.setShowGrid(false);
            flatTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            flatTable.getAccessibleContext().setAccessibleName(
                folder == null ? "" : folder.getName());
            flatTable.getSelectionModel().addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) return;
                int at = flatTable.getSelectedRow();
                chosenFlat(at < 0 ? null : rows.get(at));
            });
            flatTable.addMouseListener(opener(() -> {
                int at = flatTable.getSelectedRow();
                return at < 0 ? null : rows.get(at);
            }));
            // A list view is the one that compares files against each other, so it has to
            // say what it is comparing. Without the headings the dates and sizes are four
            // unlabelled columns of numbers.
            JScrollPane holder = new JScrollPane(flatTable);
            holder.setColumnHeaderView(flatTable.getTableHeader());
            flatTable.getTableHeader().setFont(Aqua.systemFont());
            flatTable.getTableHeader().setReorderingAllowed(false);
            flat.add(holder, BorderLayout.CENTER);
        }
        flat.revalidate();
        flat.repaint();
        announce();
    }

    private java.awt.event.MouseListener opener(java.util.function.Supplier<Node> picked) {
        return new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() != 2) return;
                Node one = picked.get();
                if (one != null && one.file != null) onOpened.accept(one.file);
            }
        };
    }

    private void chosenFlat(Node picked) {
        if (picked == null || picked.file == null) return;
        if (picked.file.isDirectory()) showFlat(picked.file);
        onChosen.accept(picked.file);
    }

    /** What a folder holds that this browser would show, in one place for all three views. */
    private List<Node> rowsIn(File folder) {
        List<Node> out = new ArrayList<>();
        if (folder == null) return out;
        String wanted = searching.lowercase().toString();
        for (Node one : FS.list(folder)) {
            if (one.file == null || one.file.isHidden()) continue;
            if (!one.file.isDirectory() && !shows.test(one.file)) continue;
            if (!wanted.isEmpty()
                    && !one.name.toLowerCase(java.util.Locale.ROOT).contains(wanted)) {
                continue;
            }
            out.add(one);
        }
        return out;
    }

    /* ------------------------------------------------------------------ columns */

    /** The folder a column is showing the contents of. */
    private File folderOf(JList<Node> column) {
        Object held = column.getClientProperty("folder");
        return held instanceof File file ? file : null;
    }

    private void addColumn(File folder) {
        DefaultListModel<Node> model = new DefaultListModel<>();
        for (Node one : rowsIn(folder)) model.addElement(one);

        JList<Node> column = new JList<>(model);
        column.putClientProperty("folder", folder);
        column.setFont(Aqua.systemFont());
        column.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        column.setCellRenderer(new RowRenderer());
        column.getAccessibleContext().setAccessibleName(folder.getName());
        column.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            chose(column);
        });
        column.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() != 2) return;
                Node picked = column.getSelectedValue();
                if (picked != null && picked.file != null) onOpened.accept(picked.file);
            }
        });
        // The arrow keys walk a column; the sideways ones walk between them, which is what
        // makes a column browser navigable without a mouse.
        column.getInputMap(JComponent.WHEN_FOCUSED)
              .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, 0), "deeper");
        column.getActionMap().put("deeper", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                int at = columns.indexOf(column);
                if (at >= 0 && at + 1 < columns.size()) {
                    columns.get(at + 1).requestFocusInWindow();
                    if (columns.get(at + 1).getModel().getSize() > 0) {
                        columns.get(at + 1).setSelectedIndex(0);
                    }
                }
            }
        });
        column.getInputMap(JComponent.WHEN_FOCUSED)
              .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, 0), "shallower");
        column.getActionMap().put("shallower", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                int at = columns.indexOf(column);
                if (at > 0) columns.get(at - 1).requestFocusInWindow();
            }
        });

        JScrollPane pane = new JScrollPane(column,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        pane.setPreferredSize(new Dimension(COLUMN_WIDTH, 10));
        pane.setMaximumSize(new Dimension(COLUMN_WIDTH, Integer.MAX_VALUE));
        pane.setMinimumSize(new Dimension(COLUMN_WIDTH, 10));
        pane.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0xC8C8C8)));

        columns.add(column);
        row.add(pane);
        row.revalidate();
        row.repaint();
        keepLastInView();
    }

    /**
     * What happens when something in a column is chosen.
     *
     * Everything to the right goes, because it described a route that is no longer being
     * taken. A folder then opens a column of its own; anything else is the end of the road
     * and the panel above is told what was picked.
     */
    private void chose(JList<Node> column) {
        int at = columns.indexOf(column);
        if (at < 0) return;
        Node picked = column.getSelectedValue();
        trimTo(at + 1);
        if (picked != null && picked.file != null) {
            if (picked.file.isDirectory()) addColumn(picked.file);
            onChosen.accept(picked.file);
        }
        announce();
    }

    private void trimTo(int keep) {
        while (columns.size() > keep) {
            columns.remove(columns.size() - 1);
            row.remove(row.getComponentCount() - 1);
        }
        row.revalidate();
        row.repaint();
    }

    /** Keeps the column being worked in on the screen, however deep the route got. */
    private void keepLastInView() {
        SwingUtilities.invokeLater(() -> {
            int width = Math.max(row.getPreferredSize().width, 1);
            scroller.getHorizontalScrollBar().setValue(width);
        });
    }

    /** Says where the browser now is, for anything reading the screen. */
    private void announce() {
        File folder = currentFolder();
        if (folder != null) {
            getAccessibleContext().setAccessibleDescription(folder.getName());
        }
    }

    /** What a list view shows about each file: its name, when it changed, how big. */
    private static final class RowsModel extends javax.swing.table.AbstractTableModel {
        private final List<Node> rows;
        private final String[] headings = {
            FMLocalized.of(FMString.of("browser.name")).toString(),
            FMLocalized.of(FMString.of("browser.dateModified")).toString(),
            FMLocalized.of(FMString.of("browser.size")).toString(),
            FMLocalized.of(FMString.of("browser.kind")).toString(),
        };

        RowsModel(List<Node> rows) { this.rows = rows; }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return headings.length; }
        @Override public String getColumnName(int at) { return headings[at]; }

        @Override public Object getValueAt(int row, int column) {
            Node one = rows.get(row);
            // A column of minus one is not a column: it is how the browser asks this model
            // for the file behind a row without the table having to hold one.
            if (column < 0) return one.file;
            return switch (column) {
                case 0 -> one.name;
                case 1 -> one.modified <= 0 ? "" : FS.formatDate(one.modified);
                case 2 -> one.file != null && one.file.isDirectory() ? "--"
                                                                    : FS.formatBytes(one.size);
                default -> Kinds.display(one);
            };
        }
    }

    /**
     * An icon view row: the picture, with the name under it.
     *
     * The picture is the point of an icon view. Without one it is a list with worse
     * spacing, and the reason somebody chose this view was to recognise something by its
     * shape rather than by reading it.
     */
    private static final class IconRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean chosen, boolean focused) {
            Node node = value instanceof Node n ? n : null;
            String name = node == null ? "" : node.name;
            super.getListCellRendererComponent(list, name, index, chosen, focused);
            setHorizontalAlignment(CENTER);
            setHorizontalTextPosition(CENTER);
            setVerticalTextPosition(BOTTOM);
            setBorder(BorderFactory.createEmptyBorder(4, 4, 6, 4));
            setIcon(node == null ? null
                : new javax.swing.ImageIcon(org.fractalmicro.theme.Icons.forNode(node, 32)));
            if (node != null) {
                getAccessibleContext().setAccessibleName(name + ", " + Kinds.of(node));
            }
            return this;
        }
    }

    /**
     * A row: the name, with the kind said beside it.
     *
     * A folder is marked as one, because in a column browser the difference decides
     * whether choosing it opens another column, and somebody who cannot see the arrow has
     * no other way to know.
     */
    private static final class RowRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean chosen, boolean focused) {
            Node node = value instanceof Node n ? n : null;
            String name = node == null ? "" : node.name;
            super.getListCellRendererComponent(list, name, index, chosen, focused);
            setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            if (node != null && node.file != null) {
                boolean folder = node.file.isDirectory();
                setText(folder ? name + "  ▶" : name);
                setIcon(new javax.swing.ImageIcon(
                    org.fractalmicro.theme.Icons.forNode(node, 16)));
                getAccessibleContext().setAccessibleName(name + ", " + Kinds.of(node));
            }
            return this;
        }
    }
}
