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

import org.fractalmicro.foundation.FMString;

import org.fractalmicro.appkit.FocusGroup;
import org.fractalmicro.windowserver.Desktop;
import org.fractalmicro.windowserver.MainMenu;

import org.fractalmicro.os.FinderSettings;
import org.fractalmicro.core.Recent;
import org.fractalmicro.fs.*;
import org.fractalmicro.theme.Aqua;
import org.fractalmicro.theme.Icons;

import org.fractalmicro.appkit.FMTextField;

import javax.swing.*;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A Finder window: sidebar, toolbar, one of four views, path bar and status bar. */
public class FinderWindow extends JInternalFrame {

    public enum Mode { FOLDER, APPLICATIONS, UTILITIES, COMPUTER, NETWORK, TRASH, SEARCH }

    /** One entry in the back/forward history. */
    private static class Location {
        final Mode mode;
        final File file;
        final String title;
        Location(Mode mode, File file, String title) {
            this.mode = mode;
            this.file = file;
            this.title = title;
        }
    }

    private final Sidebar sidebar;
    private final JPanel content = new JPanel(new CardLayout());
    private final IconView iconView;
    private final ListView listView;
    private final ColumnView columnView;
    private final CoverFlowView coverView;
    private final JLabel statusBar = new JLabel(" ");
    private final PathBar pathBar = new PathBar();
    private final JPanel toolbar;
    private final JButton back = new ArrowButton(false);
    private final JButton forward = new ArrowButton(true);
    private final FMTextField searchField = new FMTextField(12);
    private final JSplitPane split;

    private final List<Location> history = new ArrayList<>();
    private int historyIndex = -1;

    private Mode mode = Mode.FOLDER;
    private File folder;
    private List<Node> contents = new ArrayList<>();
    private List<Node> searchResults = new ArrayList<>();
    private String searchTitle = "Search";
    private String viewMode;
    private String arrangeKey = "Name";

    public FinderWindow(File dir) {
        super("Finder", true, true, true, true);
        setFrameIcon(new ImageIcon(Icons.forKind(Node.Kind.FOLDER, 16)));
        setSize(820, 540);
        viewMode = FinderSettings.viewNameFor(FinderSettings.preferredViewStyle()).toString();

        iconView = new IconView(this::openNode);
        listView = new ListView(this::openNode);
        columnView = new ColumnView(this::openNode);
        coverView = new CoverFlowView(this::openNode);

        JPopupMenu popup = Finder.contextMenu(this::selection, this::currentFolder);
        iconView.setPopup(popup);
        listView.setPopup(popup);
        columnView.setPopup(popup);
        coverView.setPopup(popup);
        Runnable onSel = this::updateStatus;
        iconView.onSelectionChange(onSel);
        listView.onSelectionChange(onSel);
        columnView.onSelectionChange(onSel);
        coverView.onSelectionChange(onSel);

        content.add(iconView.component(), "Icon");
        content.add(listView.component(), "List");
        content.add(columnView.component(), "Column");
        content.add(coverView.component(), "Cover Flow");

        sidebar = new Sidebar(this::goToTarget);
        toolbar = buildToolbar();

        JPanel right = new JPanel(new BorderLayout());
        right.add(content, BorderLayout.CENTER);
        right.add(pathBar, BorderLayout.SOUTH);

        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, right);
        split.setDividerLocation(FinderSettings.showSidebar() ? FinderSettings.sidebarWidth() : 0);
        split.setDividerSize(1);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.getAccessibleContext().setAccessibleName("Contents");

        statusBar.setFont(Aqua.smallFont());
        statusBar.setHorizontalAlignment(SwingConstants.CENTER);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xC0C0C0)),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        statusBar.setOpaque(true);
        statusBar.setBackground(Aqua.STATUSBAR_BG);
        statusBar.getAccessibleContext().setAccessibleName("Status");

        JPanel body = new JPanel(new BorderLayout());
        body.add(toolbar, BorderLayout.NORTH);
        body.add(split, BorderLayout.CENTER);
        body.add(statusBar, BorderLayout.SOUTH);
        setContentPane(body);

        addInternalFrameListener(new InternalFrameAdapter() {
            @Override public void internalFrameClosed(InternalFrameEvent e) {
                if (Desktop.get() != null) Desktop.get().mainMenu().windowsChanged();
            }
            @Override public void internalFrameActivated(InternalFrameEvent e) {
                if (Desktop.get() != null) Desktop.get().mainMenu().windowsChanged();
            }
        });

        toolbar.setVisible(FinderSettings.showToolbar());
        pathBar.setVisible(FinderSettings.showPathBar());
        statusBar.setVisible(FinderSettings.showStatusBar());

        installKeys();
        setViewMode(viewMode);
        if (dir != null) navigateTo(dir);
    }

    /* ----------------------------------------------------------- toolbar */

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Aqua.vgradient((Graphics2D) g, 0, 0, getWidth(), getHeight(),
                               Aqua.TOOLBAR_TOP, Aqua.TOOLBAR_BOTTOM);
                g.setColor(new Color(0xA8A8A8));
                g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            }
        };
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        bar.getAccessibleContext().setAccessibleName("Toolbar");

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);
        back.setToolTipText("Back");
        back.getAccessibleContext().setAccessibleName("Back");
        back.addActionListener(e -> goBack());
        forward.setToolTipText("Forward");
        forward.getAccessibleContext().setAccessibleName("Forward");
        forward.addActionListener(e -> goForward());
        left.add(back);
        left.add(forward);
        left.add(Box.createHorizontalStrut(10));
        left.add(viewSwitcher());
        left.add(Box.createHorizontalStrut(10));
        left.add(actionMenuButton());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);
        searchField.putClientProperty("FMTextField.variant", "search");
        searchField.setToolTipText("Search this folder");
        searchField.getAccessibleContext().setAccessibleName("Search");
        searchField.addActionListener(e -> runSearch(searchField.getText()));
        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setFont(Aqua.smallFont());
        searchLabel.setLabelFor(searchField);
        right.add(searchLabel);
        right.add(searchField);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);

        // The toolbar is one tab stop, arrows move along it, as on a Mac with full
        // keyboard access switched on.
        List<JComponent> stops = new ArrayList<>();
        stops.add(back);
        stops.add(forward);
        stops.addAll(viewButtons);
        stops.add(actionButton);
        stops.add(searchField);
        FocusGroup.horizontal(bar, stops);
        return bar;
    }

    private final List<JComponent> viewButtons = new ArrayList<>();
    private JButton actionButton;

    private JComponent viewSwitcher() {
        JPanel group = new JPanel(new GridLayout(1, 4, 0, 0));
        group.setOpaque(false);
        group.getAccessibleContext().setAccessibleName("View");
        ButtonGroup bg = new ButtonGroup();
        String[] modes = {"Icon", "List", "Column", "Cover Flow"};
        String[] labels = {"as Icons", "as List", "as Columns", "as Cover Flow"};
        for (int i = 0; i < modes.length; i++) {
            String m = modes[i];
            JToggleButton b = new JToggleButton(new ViewIcon(m));
            b.setFont(Aqua.smallFont());
            b.setFocusPainted(true);
            b.setMargin(new Insets(2, 6, 2, 6));
            b.setToolTipText(labels[i]);
            b.getAccessibleContext().setAccessibleName(labels[i]);
            b.setSelected(m.equals(viewMode));
            b.addActionListener(e -> setViewMode(m));
            bg.add(b);
            group.add(b);
            viewButtons.add(b);
        }
        return group;
    }

    /** The cog on the Action button. */
    private static class GearIcon implements Icon {
        @Override public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            Aqua.antialias(g);
            g.setColor(c.isEnabled() ? new Color(0x333333) : new Color(0xA0A0A0));
            g.translate(x + 7, y + 7);
            for (int i = 0; i < 8; i++) {
                g.rotate(Math.PI / 4);
                g.fillRect(-1, -7, 3, 4);
            }
            g.fillOval(-5, -5, 10, 10);
            g.setColor(new Color(0xE4E4E4));
            g.fillOval(-2, -2, 4, 4);
            g.dispose();
        }
        @Override public int getIconWidth() { return 15; }
        @Override public int getIconHeight() { return 15; }
    }

    /** The four little pictograms on the view switcher, drawn rather than typed. */
    private static class ViewIcon implements Icon {
        private final String mode;
        ViewIcon(String mode) { this.mode = mode; }

        @Override public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setColor(c.isEnabled() ? new Color(0x333333) : new Color(0xA0A0A0));
            g.translate(x, y);
            switch (mode) {
                case "List":
                    for (int i = 0; i < 3; i++) {
                        g.fillRect(0, 1 + i * 5, 2, 2);
                        g.fillRect(4, 2 + i * 5, 8, 1);
                    }
                    break;
                case "Column":
                    for (int i = 0; i < 3; i++) g.drawRect(i * 5, 1, 4, 11);
                    break;
                case "Cover Flow":
                    g.drawRect(0, 4, 3, 6);
                    g.fillRect(5, 1, 5, 11);
                    g.drawRect(11, 4, 3, 6);
                    break;
                default:
                    for (int row = 0; row < 2; row++) {
                        for (int col = 0; col < 2; col++) g.fillRect(col * 7, 1 + row * 6, 5, 4);
                    }
            }
            g.dispose();
        }

        @Override public int getIconWidth() { return 14; }
        @Override public int getIconHeight() { return 13; }
    }

    private JComponent actionMenuButton() {
        JButton gear = new JButton(new GearIcon());
        actionButton = gear;
        gear.setToolTipText("Action");
        gear.getAccessibleContext().setAccessibleName("Action menu");
        gear.addActionListener(e -> {
            JPopupMenu m = Finder.contextMenu(this::selection, this::currentFolder);
            m.show(gear, 0, gear.getHeight());
        });
        return gear;
    }

    /* -------------------------------------------------------- navigation */

    public void navigateTo(File dir) {
        if (dir == null || !dir.isDirectory()) {
            Finder.beep("That folder could not be opened");
            return;
        }
        push(new Location(Mode.FOLDER, dir, titleFor(dir)));
        applyLocation();
        Recent.noteFolder(dir);
        Desktop.get().mainMenu().rebuildRecents();
    }

    public void showApplications(boolean utilities) {
        // Applications is a folder on disk holding bundles, not a made-up list.
        java.io.File folder = utilities
            ? org.fractalmicro.os.OSPaths.applicationsUtilities().toFile()
            : org.fractalmicro.os.OSPaths.applications().toFile();
        if (folder.isDirectory()) {
            navigateTo(folder);
            return;
        }
        push(new Location(utilities ? Mode.UTILITIES : Mode.APPLICATIONS, null,
                          utilities ? "Utilities" : "Applications"));
        applyLocation();
    }

    public void showComputer() {
        push(new Location(Mode.COMPUTER, null, System.getProperty("user.name") + "'s Fractal"));
        applyLocation();
    }

    public void showNetwork() {
        push(new Location(Mode.NETWORK, null, "Network"));
        applyLocation();
    }

    public void showTrash() {
        push(new Location(Mode.TRASH, null, "Trash"));
        applyLocation();
    }

    public void showSearchResults(String title, List<Node> results) {
        searchResults = new ArrayList<>(results);
        searchTitle = title;
        push(new Location(Mode.SEARCH, null, title));
        applyLocation();
    }

    private void push(Location loc) {
        while (history.size() > historyIndex + 1) history.remove(history.size() - 1);
        history.add(loc);
        historyIndex = history.size() - 1;
    }

    private void applyLocation() {
        Location loc = history.get(historyIndex);
        mode = loc.mode;
        folder = loc.file;
        setTitle(loc.title);
        getAccessibleContext().setAccessibleName(loc.title);
        setFrameIcon(new ImageIcon(Icons.forKind(
            mode == Mode.TRASH ? Node.Kind.TRASH
          : mode == Mode.COMPUTER ? Node.Kind.COMPUTER
          : mode == Mode.SEARCH ? Node.Kind.SEARCH
          : Node.Kind.FOLDER, 16)));
        back.setEnabled(historyIndex > 0);
        forward.setEnabled(historyIndex < history.size() - 1);
        reload();
    }

    public void goBack() {
        if (historyIndex <= 0) { Finder.beep("Nothing to go back to"); return; }
        historyIndex--;
        applyLocation();
    }

    public void goForward() {
        if (historyIndex >= history.size() - 1) { Finder.beep("Nothing to go forward to"); return; }
        historyIndex++;
        applyLocation();
    }

    public void goUp() {
        if (folder == null) { Finder.beep("There is no enclosing folder"); return; }
        File parent = folder.getParentFile();
        if (parent == null) { showComputer(); return; }
        navigateTo(parent);
    }

    private void goToTarget(Sidebar.Target t) {
        if (t.special == null) {
            navigateTo(t.file);
            return;
        }
        switch (t.special) {
            case "computer": showComputer(); break;
            case "network": showNetwork(); break;
            case "applications": showApplications(false); break;
            case "utilities": showApplications(true); break;
            case "trash": showTrash(); break;
            default:
                if (t.special.startsWith("search:")) runSavedSearch(t.special.substring(7));
        }
    }

    private void openNode(Node n) {
        if (n == null) return;
        if (n.isContainer() && n.file != null) {
            navigateTo(n.file);
        } else if (n.file != null) {
            Recent.noteItem(n.file);
            Finder.launchApp(n);
        } else {
            Finder.beep(n.name + " cannot be opened from here");
        }
    }

    /* ------------------------------------------------------------ content */

    public void reload() {
        switch (mode) {
            case APPLICATIONS: contents = Apps.applications(); break;
            case UTILITIES:    contents = Apps.utilities(); break;
            case COMPUTER:     contents = computerContents(); break;
            case NETWORK:      contents = Volumes.ofKind(Node.Kind.SERVER); break;
            case TRASH:        contents = Trash.list(); break;
            case SEARCH:       contents = searchResults; break;
            default:           contents = folder == null ? new ArrayList<>() : FS.list(folder);
        }
        List<Node> copy = new ArrayList<>(contents);
        FS.sort(copy, arrangeKey);
        currentView().setContents(copy);
        pathBar.setPath(folder, title());
        updateStatus();
    }

    private String title() { return getTitle(); }

    private List<Node> computerContents() {
        List<Node> out = new ArrayList<>(Volumes.all());
        Node network = new Node(Node.Kind.NETWORK, "Network", null);
        network.detail = "shared computers";
        out.add(network);
        return out;
    }

    private FileView currentView() {
        switch (viewMode) {
            case "List": return listView;
            case "Column": return columnView;
            case "Cover Flow": return coverView;
            default: return iconView;
        }
    }

    public void setViewMode(String mode) {
        viewMode = mode;
        FinderSettings.setPreferredViewStyle(FinderSettings.viewCodeFor(FMString.of(mode)));
        ((CardLayout) content.getLayout()).show(content, mode);
        reload();
        currentView().focusView();
        setStatusText(viewStatus());
    }

    private String viewStatus() {
        return "View: " + viewMode.toLowerCase(Locale.ROOT);
    }

    public void arrangeBy(String key) {
        arrangeKey = key;
        reload();
        setStatusText("Arranged by " + key.toLowerCase(Locale.ROOT));
    }

    /** Show and hide, writing the choice back to the Finder settings, as Finder does. */
    public void toggleChrome(String what) {
        switch (what) {
            case "toolbar":
                toolbar.setVisible(!toolbar.isVisible());
                FinderSettings.setShowToolbar(toolbar.isVisible());
                break;
            case "pathbar":
                pathBar.setVisible(!pathBar.isVisible());
                FinderSettings.setShowPathBar(pathBar.isVisible());
                break;
            case "statusbar":
                statusBar.setVisible(!statusBar.isVisible());
                FinderSettings.setShowStatusBar(statusBar.isVisible());
                break;
            case "sidebar":
                boolean show = split.getDividerLocation() < 20;
                split.setDividerLocation(show ? FinderSettings.sidebarWidth() : 0);
                FinderSettings.setShowSidebar(show);
                break;
            default:
                break;
        }
        revalidate();
        repaint();
    }

    /** Window titles show the folder name, or the drive letter at the top of a disk. */
    private String titleFor(File dir) {
        String name = dir.getName();
        if (!name.isEmpty()) return name;
        for (Node v : Volumes.all()) {
            if (v.file != null && v.file.getAbsolutePath().equalsIgnoreCase(dir.getAbsolutePath())) {
                return v.name;
            }
        }
        return dir.getPath();
    }

    public void setIconSize(int px) {
        currentView().setIconSize(px);
    }

    public String viewMode() { return viewMode; }

    /** Where the keyboard should land when this window opens: the file list itself. */
    public JComponent focusTarget() {
        FileView view = currentView();
        JComponent component = view.component();
        // The scroll pane is not the thing that takes keys; its list or table is.
        if (component instanceof JScrollPane) {
            java.awt.Component inner = ((JScrollPane) component).getViewport().getView();
            if (inner instanceof JComponent) return (JComponent) inner;
        }
        return component;
    }

    public boolean toolbarVisible()   { return toolbar.isVisible(); }

    /**
     * Puts the keyboard on the toolbar. Answers whether there was one to put it on.
     *
     * The toolbar is one stop in the tab order, as a cluster is under full keyboard access,
     * and this is the other way to it: straight there, without tabbing past everything else.
     */
    public boolean focusToolbar() {
        if (!toolbar.isVisible()) return false;
        java.awt.FocusTraversalPolicy policy = toolbar.getFocusTraversalPolicy();
        java.awt.Component target = policy == null ? null
            : policy.getDefaultComponent(toolbar);
        if (target == null) return false;
        target.requestFocusInWindow();
        return true;
    }
    public boolean pathBarVisible()   { return pathBar.isVisible(); }
    public boolean statusBarVisible() { return statusBar.isVisible(); }
    public boolean sidebarVisible()   { return split.getDividerLocation() > 20; }

    public List<Node> selection() { return currentView().selection(); }

    public void selectAll() { currentView().selectAll(); }

    public File currentFolder() {
        return folder != null ? folder : FS.desktopFolder();
    }

    public void setStatusText(String text) {
        statusBar.setText(text == null || text.isBlank() ? " " : text);
    }

    private void updateStatus() {
        List<Node> sel = selection();
        StringBuilder sb = new StringBuilder();
        if (sel.isEmpty()) {
            sb.append(contents.size()).append(contents.size() == 1 ? " item" : " items");
        } else if (sel.size() == 1) {
            sb.append(sel.get(0).name).append(", ").append(sel.get(0).summary());
        } else {
            sb.append(sel.size()).append(" of ").append(contents.size()).append(" selected");
        }
        if (folder != null) {
            File root = folder;
            while (root.getParentFile() != null) root = root.getParentFile();
            long free = root.getFreeSpace();
            if (free > 0) sb.append(", ").append(FS.formatBytes(free)).append(" available");
        }
        setStatusText(sb.toString());
    }

    /* ------------------------------------------------------------- search */

    private void runSearch(String query) {
        if (query == null || query.isBlank()) { Finder.beep("Type something to search for"); return; }
        File where = currentFolder();
        List<Node> hits = Search.inFolder(where, query, 500);
        showSearchResults("Searching “" + query + "”", hits);
    }

    private void runSavedSearch(String which) {
        List<Node> hits = Search.saved(which, 500);
        String title;
        switch (which) {
            case "today": title = "Today"; break;
            case "week": title = "Past Week"; break;
            case "images": title = "All Images"; break;
            default: title = "All Documents";
        }
        showSearchResults(title, hits);
    }

    /* --------------------------------------------------------------- keys */

    private void installKeys() {
        InputMap im = getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap am = getRootPane().getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, MainMenu.CMD), "openSelection");
        am.put("openSelection", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                for (Node n : selection()) openNode(n);
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, MainMenu.CMD), "reload");
        am.put("reload", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { reload(); }
        });
    }

    /* ------------------------------------------------------------ path bar */

    /** The strip of folder buttons along the bottom of the window. */
    private class PathBar extends JPanel {
        PathBar() {
            setLayout(new FlowLayout(FlowLayout.LEFT, 2, 2));
            setBackground(new Color(0xE8E8E8));
            setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xC8C8C8)));
            getAccessibleContext().setAccessibleName("Path");
        }

        void setPath(File dir, String fallback) {
            removeAll();
            if (dir == null) {
                add(crumbLabel(fallback));
            } else {
                List<File> parts = new ArrayList<>();
                for (File f = dir; f != null; f = f.getParentFile()) parts.add(0, f);
                for (int i = 0; i < parts.size(); i++) {
                    File f = parts.get(i);
                    String name = f.getName().isEmpty() ? f.getPath() : f.getName();
                    JButton b = new JButton(name);
                    b.setFont(Aqua.smallFont());
                    b.setBorderPainted(false);
                    b.setContentAreaFilled(false);
                    b.setMargin(new Insets(0, 3, 0, 3));
                    b.setIcon(new ImageIcon(Icons.forKind(Node.Kind.FOLDER, 12)));
                    b.getAccessibleContext().setAccessibleName(name);
                    b.addActionListener(e -> navigateTo(f));
                    add(b);
                    if (i < parts.size() - 1) add(crumbLabel("›"));
                }
            }
            revalidate();
            repaint();
        }

        private JLabel crumbLabel(String text) {
            JLabel l = new JLabel(text);
            l.setFont(Aqua.smallFont());
            l.setForeground(new Color(0x6A6A6A));
            return l;
        }
    }

    /** Back and forward, drawn as the flat triangles from the 10.6 toolbar. */
    private static class ArrowButton extends JButton {
        private final boolean forward;
        ArrowButton(boolean forward) {
            this.forward = forward;
            setPreferredSize(new Dimension(30, 22));
            setFocusPainted(true);
        }
        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            Aqua.antialias(g);
            g.setColor(isEnabled() ? new Color(0x333333) : new Color(0xAAAAAA));
            int cx = getWidth() / 2, cy = getHeight() / 2;
            Polygon p = forward
                ? new Polygon(new int[]{cx - 3, cx + 4, cx - 3}, new int[]{cy - 5, cy, cy + 5}, 3)
                : new Polygon(new int[]{cx + 3, cx - 4, cx + 3}, new int[]{cy - 5, cy, cy + 5}, 3);
            g.fill(p);
            g.dispose();
        }
    }
}
