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
package org.fractalmicro.windowserver;

import org.fractalmicro.bundle.Bundles;
import org.fractalmicro.bundle.LaunchServices;

import org.fractalmicro.appkit.FMDragOperation;
import org.fractalmicro.appkit.FMFileDragging;
import org.fractalmicro.appkit.FocusGroup;

import org.fractalmicro.core.Running;
import org.fractalmicro.core.WindowList;
import org.fractalmicro.fs.*;
import org.fractalmicro.os.FMUserDefaults;
import org.fractalmicro.os.DockSettings;
import org.fractalmicro.theme.Aqua;
import org.fractalmicro.theme.Icons;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The Dock. Finder first, then the tiles pinned in org.fractalmicro.dock, then anything
 * running that is not already there, then the Trash.
 *
 * Keyboard: Tab reaches the Dock as one stop and the arrow keys move along it, as they
 * do with full keyboard access on a Mac. Up opens the tile's menu, which is where
 * Keep in Dock lives. Escape puts the keyboard back where it came from.
 */
public class Dock extends JPanel {

    private final JPanel tiles = new JPanel();
    private FocusGroup focus;
    private Component focusCameFrom;

    public Dock() {
        setOpaque(false);
        setLayout(new BorderLayout());
        tiles.setOpaque(false);
        tiles.setLayout(new FlowLayout(FlowLayout.CENTER, 6, 4));
        tiles.setBorder(BorderFactory.createEmptyBorder(6, 12, 4, 12));
        add(tiles, BorderLayout.CENTER);

        getAccessibleContext().setAccessibleName("Dock");

        Running.onChange(this::rebuild);
        WindowList.onChange(this::rebuild);
        Trash.onChange(() -> { Icons.invalidateTrash(); rebuild(); });
        FMUserDefaults.onChange((domain, key) -> {
            if (FMUserDefaults.DOCK.equals(domain)) rebuild();
        });
        rebuild();
    }

    /** Called when the keyboard is sent here, so Escape knows where to go back to. */
    public void takeFocus() {
        focusCameFrom = FocusGroup.focusOwner();
        if (focus != null) focus.focusFirst();
    }

    public void rebuild() {
        tiles.removeAll();
        List<JComponent> order = new ArrayList<>();

        Node finder = new Node(Node.Kind.APPLICATION, "Finder", null);
        Tile finderTile = new Tile(finder, true, e -> Bundles.openIdentifier(LaunchServices.FILE_BROWSER));
        tiles.add(finderTile);
        order.add(finderTile);

        List<File> seen = new ArrayList<>();

        for (DockSettings.Tile pinned : DockSettings.persistentApps()) {
            Node n = new Node(Node.Kind.APPLICATION,
                pinned.label.isEmpty() ? stripExtension(pinned.file.getName()) : pinned.label,
                pinned.file);
            Tile tile = new Tile(n, Running.isRunning(n.name), e -> LaunchServices.open(n));
            tiles.add(tile);
            order.add(tile);
            seen.add(pinned.file);
        }

        Node browser = Apps.defaultBrowser();
        if (browser != null && notSeen(seen, browser.file)) {
            Tile tile = new Tile(browser, Running.isRunning(browser.name), e -> LaunchServices.open(browser));
            tiles.add(tile);
            order.add(tile);
            seen.add(browser.file);
        }
        Node mail = Apps.defaultMail();
        if (mail != null && notSeen(seen, mail.file)) {
            Tile tile = new Tile(mail, Running.isRunning(mail.name), e -> LaunchServices.open(mail));
            tiles.add(tile);
            order.add(tile);
            seen.add(mail.file);
        }

        // Everything with a window on screen, whoever started it. With no taskbar
        // this is the only place a running program can be reached from.
        for (WindowList.App app : WindowList.applications()) {
            if (!notSeen(seen, app.executable)) {
                markRunning(order, app);
                continue;
            }
            Node n = new Node(Node.Kind.APPLICATION, app.name, app.executable);
            Tile tile = new Tile(n, true, e -> WindowList.activate(app));
            tile.setApp(app);
            tiles.add(tile);
            order.add(tile);
            seen.add(app.executable);
        }

        // Anything started from here that has not put a window up yet.
        for (Running.Entry entry : Running.all()) {
            if (!notSeen(seen, entry.launcher)) continue;
            Node n = new Node(Node.Kind.APPLICATION, entry.name, entry.launcher);
            Tile tile = new Tile(n, true, e -> LaunchServices.open(n));
            tiles.add(tile);
            order.add(tile);
        }

        tiles.add(new Separator());

        Node trash = new Node(Node.Kind.TRASH, "Trash", null);
        Tile trashTile = new Tile(trash, false, e -> LaunchServices.tellFileBrowser(LaunchServices.TRASH));
        tiles.add(trashTile);
        order.add(trashTile);

        focus = FocusGroup.horizontal(tiles, order);
        focus.onEscape(() -> {
            // Whichever way the keyboard got here, Escape puts it back.
            Component back = focusCameFrom != null ? focusCameFrom : focus.cameFrom();
            focusCameFrom = null;
            if (back != null && back.isShowing()) back.requestFocusInWindow();
            else Desktop.sharedDesktop().icons().requestFocusInWindow();
        });

        revalidate();
        repaint();
    }

    /** A pinned tile whose program is running gets its light and its windows. */
    private void markRunning(List<JComponent> order, WindowList.App app) {
        for (JComponent c : order) {
            if (!(c instanceof Tile)) continue;
            Tile tile = (Tile) c;
            if (tile.node.file != null && app.executable != null
                    && tile.node.file.getAbsolutePath().equalsIgnoreCase(app.executable.getAbsolutePath())) {
                tile.setApp(app);
                return;
            }
        }
    }

    private boolean notSeen(List<File> seen, File file) {
        if (file == null) return true;
        for (File f : seen) {
            if (f != null && f.getAbsolutePath().equalsIgnoreCase(file.getAbsolutePath())) return false;
        }
        return true;
    }

    private static String stripExtension(String n) {
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    /** A menu item with something behind it, which is two lines said many times. */
    private static JMenuItem item(String text, ActionListener what) {
        JMenuItem made = new JMenuItem(text);
        made.addActionListener(what);
        return made;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(tiles.getPreferredSize().width + 24, DockSettings.tileSize() + 26);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Aqua.antialias(g);
        int w = getWidth(), h = getHeight();
        if (Aqua.highContrast()) {
            g.setColor(Color.BLACK);
            g.fillRoundRect(0, 0, w - 1, h + 10, 12, 12);
            g.setColor(Color.WHITE);
            g.drawRoundRect(0, 0, w - 1, h + 10, 12, 12);
            g.dispose();
            return;
        }
        Shape shelf = new RoundRectangle2D.Float(0, 0, w - 1, h + 12, 14, 14);
        g.setPaint(new GradientPaint(0, 0, Aqua.DOCK_GLASS_TOP, 0, h, Aqua.DOCK_GLASS_BOTTOM));
        g.fill(shelf);
        g.setColor(new Color(255, 255, 255, 90));
        g.draw(shelf);
        g.setColor(Aqua.DOCK_SHELF_LINE);
        g.drawLine(6, h - 20, w - 6, h - 20);
        g.dispose();
    }

    /** The divider before the Trash. Not focusable and not named. */
    private static class Separator extends JPanel {
        Separator() {
            setPreferredSize(new Dimension(10, 40));
            setOpaque(false);
            setFocusable(false);
        }
        @Override protected void paintComponent(Graphics g) {
            g.setColor(new Color(255, 255, 255, 120));
            g.drawLine(getWidth() / 2, 4, getWidth() / 2, getHeight() - 6);
            g.setColor(new Color(0, 0, 0, 60));
            g.drawLine(getWidth() / 2 + 1, 4, getWidth() / 2 + 1, getHeight() - 6);
        }
    }

    /** One Dock tile: a button whose name is the application's name and nothing more. */
    private class Tile extends JButton {
        private final Node node;
        private boolean running;
        private boolean hover;
        private WindowList.App app;

        Tile(Node node, boolean running, ActionListener onClick) {
            this.node = node;
            this.running = running;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            int size = DockSettings.tileSize();
            setPreferredSize(new Dimension(size + 8, size + 12));
            addActionListener(onClick);
            setToolTipText(node.name);
            getAccessibleContext().setAccessibleName(node.name);
            if (node.kind == Node.Kind.TRASH) {
                getAccessibleContext().setAccessibleDescription(
                    Trash.isEmpty() ? "Empty" : Trash.count() + " items");
            }

            takesDrops();
            setComponentPopupMenu(menu());
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "dockMenu");
            getActionMap().put("dockMenu", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { showMenu(); }
            });

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        /** Ties this tile to a program that is actually running. */
        void setApp(WindowList.App app) {
            this.app = app;
            this.running = true;
            setComponentPopupMenu(menu());
            repaint();
        }

        private void showMenu() {
            JPopupMenu m = menu();
            m.show(this, 0, -m.getPreferredSize().height);
        }

        /** The Dock's own menu: Options with Keep in Dock, then the app's commands. */
        private JPopupMenu menu() {
            JPopupMenu m = new JPopupMenu();
            if (node.kind == Node.Kind.TRASH) {
                m.add(item("Open", e -> LaunchServices.tellFileBrowser(LaunchServices.TRASH)));
                JMenuItem empty = item("Empty Trash", e -> LaunchServices.tellFileBrowser(LaunchServices.EMPTY_TRASH));
                empty.setEnabled(!Trash.isEmpty());
                m.add(empty);
                return m;
            }

            // The program's own windows come first, as they do on a Dock tile.
            if (app != null && !app.windows.isEmpty()) {
                for (org.fractalmicro.win.User32.Win window : app.windows) {
                    String title = window.title.length() > 60
                        ? window.title.substring(0, 57) + "…" : window.title;
                    JMenuItem item = item(title, e -> WindowList.activate(window));
                    if (window.minimized) item.setToolTipText("Minimized");
                    m.add(item);
                }
                m.addSeparator();
            }

            JMenu options = new JMenu("Options");
            File file = node.file;
            boolean pinned = DockSettings.isPinned(file);
            if (file != null) {
                JCheckBoxMenuItem keep = new JCheckBoxMenuItem("Keep in Dock", pinned);
                keep.addActionListener(e -> {
                    if (keep.isSelected()) DockSettings.pin(node.name, file);
                    else DockSettings.unpin(file);
                    rebuild();
                });
                options.add(keep);
                options.add(item("Show in Finder", e -> {
                    File target = Apps.resolve(file);
                    File folder = target.isDirectory() ? target : target.getParentFile();
                    LaunchServices.openFolder(folder);
                }));
            } else {
                JMenuItem keep = new JMenuItem("Keep in Dock");
                keep.setEnabled(false);
                options.add(keep);
            }
            m.add(options);
            m.addSeparator();

            if ("Finder".equals(node.name)) {
                m.add(item("New Finder Window", e -> Bundles.openIdentifier(LaunchServices.FILE_BROWSER)));
                return m;
            }
            if (app != null) {
                m.add(item("Hide", e -> WindowList.hide(app)));
                m.add(item("Quit", e -> WindowList.quit(app)));
            } else if (running) {
                m.add(item("Quit", e -> Desktop.quitApplication(node.name)));
            } else {
                m.add(item("Open", e -> LaunchServices.open(node)));
            }
            return m;
        }

        /**
         * What dropping files on this tile does.
         *
         * Two answers, and both of them are ones a person tries without being told. Files on
         * the Trash are thrown away. Files on a program are opened by that program, whether
         * or not it is running and whether or not it would have opened them by itself, which
         * is how somebody opens a text file in an editor that is not the one the system
         * would have picked.
         *
         * A tile that is neither takes nothing, and says so by refusing the pointer.
         */
        private void takesDrops() {
            if (node.kind != Node.Kind.TRASH && node.file == null) return;
            FMFileDragging.install(this, null, new FMFileDragging.Destination() {
                @Override public FMDragOperation operationAt(Point where, List<File> files, int keys) {
                    boolean willing = node.kind == Node.Kind.TRASH
                        ? Trash.canMoveToTrash() : node.file != null;
                    if (files.isEmpty() || !willing) return FMDragOperation.NONE;
                    // Opening is not a file operation. Copy is the nearest of the three and
                    // is what the pointer shows for it, because nothing is being moved.
                    return node.kind == Node.Kind.TRASH
                        ? FMDragOperation.MOVE : FMDragOperation.COPY;
                }

                @Override public void aimedAt(boolean yes) {
                    aimedAt = yes;
                    repaint();
                }

                @Override public boolean take(Point where, List<File> files, FMDragOperation how) {
                    if (node.kind == Node.Kind.TRASH) {
                        boolean any = Trash.moveToTrash(files) > 0;
                        LaunchServices.tellFileBrowser(LaunchServices.REFRESH);
                        return any;
                    }
                    return openWith(node.file, files);
                }
            });
        }

        /**
         * Hands files to a program.
         *
         * One of this system's own programs is asked by name, which reaches it whether it is
         * running or not and opens the documents in the copy already up rather than a second
         * one. Anything else is one of the host's, and the way to hand a file to one of those
         * is the way everything does it: put the names on its command line.
         */
        private boolean openWith(File program, List<File> files) {
            org.fractalmicro.bundle.Bundle bundle =
                org.fractalmicro.bundle.Bundle.looksLikeBundle(program)
                    ? Bundles.byFolder(program) : null;
            if (bundle != null) {
                return Bundles.openFiles(bundle.identifier().toString(), files);
            }
            List<String> command = new ArrayList<>();
            command.add(program.getPath());
            for (File f : files) command.add(f.getPath());
            org.fractalmicro.core.Shell.launch(command.toArray(new String[0]));
            return true;
        }

        /** Whether a drag is over this tile, so it can show that letting go would do something. */
        private boolean aimedAt;

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Aqua.antialias(g);
            int size = DockSettings.tileSize();
            if ((hover || aimedAt) && DockSettings.magnification()) {
                size = Math.min(DockSettings.largeSize(), (int) (size * 1.25));
            }
            int x = (getWidth() - size) / 2;
            int y = getHeight() - size - 8;
            Image icon = Icons.forNode(node, size);
            g.drawImage(icon, x, y, null);

            Composite old = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f));
            g.drawImage(icon, x, y + size + 4, size, -size / 3, null);
            g.setComposite(old);

            if (running) {
                g.setColor(new Color(255, 255, 255, 220));
                g.fillOval(getWidth() / 2 - 3, getHeight() - 6, 5, 5);
            }
            if (hasFocus()) {
                Aqua.paintFocusRing(g, 0, y - 4, getWidth(), size + 10, 10);
            }
            g.dispose();
        }
    }
}
