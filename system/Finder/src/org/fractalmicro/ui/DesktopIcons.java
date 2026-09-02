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



import org.fractalmicro.fs.*;
import org.fractalmicro.os.FMUserDefaults;
import org.fractalmicro.os.FinderSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The desktop icons: whatever is in ~/Desktop-Folder, then the volumes the four
 * Finder preferences ask for. Columns fill from the top right corner downwards.
 *
 * Each icon reads as its name alone. Finder does not read a disk's type and capacity
 * out on the desktop, and neither does this.
 */
public class DesktopIcons extends JList<Node> {

    /** When the selection last changed, to tell a slow click from a double one. */
    private long selectedAt;

    private final DefaultListModel<Node> model = new DefaultListModel<>();
    private final IconCellRenderer renderer;
    private long lastStamp;

    public DesktopIcons() {
        int iconSize = FinderSettings.desktopIconSize();
        renderer = new IconCellRenderer(true, iconSize);
        setModel(model);
        setCellRenderer(renderer);
        setLayoutOrientation(JList.VERTICAL_WRAP);
        setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        setFixedCellWidth(iconSize + 40);
        setFixedCellHeight(iconSize + 28);
        setOpaque(false);
        setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        getAccessibleContext().setAccessibleName("Desktop");

        installBehaviour();

        Timer poll = new Timer(4000, e -> refreshIfChanged());
        poll.start();

        FMUserDefaults.onChange((domain, key) -> {
            if (FMUserDefaults.FINDER.equals(domain)) refresh();
        });
        Volumes.onChange(this::refresh);
        Trash.onChange(this::refresh);

        refresh();
    }

    private void installBehaviour() {
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    int i = locationToIndex(e.getPoint());
                    if (i >= 0 && getCellBounds(i, i).contains(e.getPoint())) Finder.open(model.get(i));
                }
            }
            @Override public void mousePressed(MouseEvent e) { requestFocusInWindow(); }
            @Override public void mouseReleased(MouseEvent e) {
                // A click on the name of something already selected, a moment after the
                // click that selected it, means rename. Quicker than that is a double
                // click, and that means open.
                if (e.getClickCount() != 1 || e.isPopupTrigger()) return;
                int i = locationToIndex(e.getPoint());
                if (i < 0 || i != getSelectedIndex()) return;
                if (!getCellBounds(i, i).contains(e.getPoint())) return;
                long since = e.getWhen() - selectedAt;
                if (since < NameEditor.SLOW_CLICK) return;
                renameSelection();
            }
        });

        setComponentPopupMenu(Finder.contextMenu(this::selection, FS::desktopFolder));

        InputMap im = getInputMap(WHEN_FOCUSED);
        ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "rename");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_O, org.fractalmicro.windowserver.MainMenu.CMD), "open");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, org.fractalmicro.windowserver.MainMenu.CMD), "open");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_I, org.fractalmicro.windowserver.MainMenu.CMD), "info");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, org.fractalmicro.windowserver.MainMenu.CMD), "trash");
        am.put("rename", action(e -> renameSelection()));
        am.put("open", action(e -> Finder.openAll(selection())));
        am.put("info", action(e -> Finder.getInfo(getSelectedValue())));
        am.put("trash", action(e -> Finder.moveToTrash(selection())));

        addListSelectionListener(e -> {
            selectedAt = System.currentTimeMillis();
            org.fractalmicro.windowserver.Desktop desktop =
                org.fractalmicro.windowserver.Desktop.sharedDesktop();
            if (!e.getValueIsAdjusting() && desktop != null) desktop.setStatus(statusLine());
        });

        // The number of rows per column follows the height of the desktop. It is set
        // here rather than in doLayout: setVisibleRowCount asks for a revalidate, and
        // asking for one from inside a layout pass is how a layout loop starts.
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { updateRowCount(); }
        });
    }

    private static Action action(java.util.function.Consumer<ActionEvent> body) {
        return new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { body.accept(e); }
        };
    }

    public List<Node> selection() { return new ArrayList<>(getSelectedValuesList()); }

    private String statusLine() {
        int n = getSelectedIndices().length;
        if (n == 0) return model.size() + (model.size() == 1 ? " item" : " items");
        if (n == 1) {
            Node sel = getSelectedValue();
            return sel.name + ", " + sel.summary();
        }
        return n + " of " + model.size() + " selected";
    }

    /** Cheap change detection, so files appearing behind our back are noticed. */
    private void refreshIfChanged() {
        File dir = FS.desktopFolder();
        long stamp = dir.lastModified();
        File[] kids = dir.listFiles();
        if (kids != null) {
            for (File f : kids) stamp = 31 * stamp + f.lastModified() + f.getName().hashCode();
        }
        if (stamp != lastStamp) {
            lastStamp = stamp;
            refresh();
        }
    }

    public void refresh() {
        List<Node> wanted = new ArrayList<>(FS.list(FS.desktopFolder()));
        if (FinderSettings.showHardDisks()) wanted.addAll(Volumes.ofKind(Node.Kind.HARD_DISK));
        if (FinderSettings.showExternalDisks()) wanted.addAll(Volumes.ofKind(Node.Kind.EXTERNAL_DISK));
        if (FinderSettings.showRemovableMedia()) {
            for (Node n : Volumes.ofKind(Node.Kind.REMOVABLE_MEDIA)) {
                if (n.isMounted()) wanted.add(n);        // an empty optical drive shows nothing
            }
        }
        if (FinderSettings.showServers()) wanted.addAll(Volumes.ofKind(Node.Kind.SERVER));

        int size = FinderSettings.desktopIconSize();
        renderer.setIconSize(size);
        setFixedCellWidth(size + 40);
        setFixedCellHeight(size + 28);

        List<Node> previous = new ArrayList<>(getSelectedValuesList());
        model.clear();
        for (Node n : wanted) model.addElement(n);
        org.fractalmicro.core.Log.info("desktop: " + wanted.size() + " icons, "
            + Volumes.all().size() + " volumes known, bounds " + getWidth() + "x" + getHeight());
        for (Node n : previous) {
            int i = model.indexOf(n);
            if (i >= 0) addSelectionInterval(i, i);
        }
        updateRowCount();
        revalidate();
        repaint();
    }

    private void updateRowCount() {
        int cell = Math.max(1, getFixedCellHeight());
        int rows = Math.max(1, (getHeight() - 16) / cell);
        if (getVisibleRowCount() != rows) setVisibleRowCount(rows);
    }

    /**
     * Where the name of one item is drawn, so a field can be put exactly over it. The
     * name sits at the bottom of the cell in an icon view, which is where the renderer
     * puts it.
     */
    private java.awt.Rectangle nameBoundsOf(int index) {
        java.awt.Rectangle cell = getCellBounds(index, index);
        if (cell == null) return new java.awt.Rectangle();
        java.awt.FontMetrics metrics = getFontMetrics(getFont());
        int height = metrics.getHeight() + 4;
        return new java.awt.Rectangle(cell.x + 4, cell.y + cell.height - height - 4,
                                      cell.width - 8, height);
    }

    /** Renames the selected item in place, rather than in a dialog. */
    public void renameSelection() {
        int index = getSelectedIndex();
        if (index < 0) {
            org.fractalmicro.windowserver.Desktop.beep();
            return;
        }
        NameEditor.begin(this, nameBoundsOf(index), getSelectedValue(), this::repaint);
    }
}
