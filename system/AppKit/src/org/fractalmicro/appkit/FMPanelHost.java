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

import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.foundation.FMLocalized;
import org.fractalmicro.foundation.FMMutableArray;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMURL;
import org.fractalmicro.fs.FS;
import org.fractalmicro.fs.Kinds;
import org.fractalmicro.fs.Node;
import org.fractalmicro.os.FMUserDefaults;
import org.fractalmicro.theme.Aqua;
import org.fractalmicro.windowserver.Desktop;

import javax.swing.*;
import javax.swing.JInternalFrame;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Where a save or open panel is actually built.
 *
 * The panel is one piece of furniture with two shapes. Collapsed it is a name and the
 * places somebody usually saves into; expanded it is those plus a browser, a sidebar and a
 * way to make a folder. The triangle beside the name moves between them, and which one it
 * was left in is a preference rather than a fresh decision each time: somebody who wants
 * the browser wants it every time.
 *
 * Nothing here belongs to any one program. That is the point of the panel: every program
 * that saves asks the same question, and a person learns the answer once.
 */
final class FMPanelHost {
    private FMPanelHost() {}

    /** Whether the panel was last left showing the browser. Kept as Mac OS X keeps it. */
    private static final FMString EXPANDED = FMString.of("NSNavPanelExpandedStateForSaveMode");

    private static final int COLLAPSED_WIDTH = 500;
    private static final int COLLAPSED_HEIGHT = 160;
    private static final int EXPANDED_WIDTH = 660;
    private static final int EXPANDED_HEIGHT = 480;

    /**
     * Runs a panel and answers which button ended it.
     *
     * A program in a process of its own has no screen, so it does not come here at all: it
     * sends the request to the window server and the panel is built in that process. This
     * is the other side of that, and the side the desktop's own parts use directly.
     */
    static int run(FMSavePanel panel) {
        if (Desktop.get() == null) return FMSavePanel.CANCELLED;
        int[] answer = new int[]{FMSavePanel.CANCELLED};
        try {
            onSwing(() -> answer[0] = show(panel));
        } catch (Exception notShown) {
            org.fractalmicro.core.Log.info("the panel could not be shown: " + notShown);
            return FMSavePanel.CANCELLED;
        }
        return answer[0];
    }

    private static void onSwing(Runnable task) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeAndWait(task);
    }

    /* --------------------------------------------------------------- the panel */

    private static int show(FMSavePanel panel) {
        boolean opening = panel instanceof FMOpenPanel;
        State state = new State(panel);
        state.folder = panel.directoryURL().asFile();
        state.body = build(state, opening);

        // A sheet when there is a window for it to belong to, which is the whole reason
        // Aqua has sheets: saving is a thing done to one document, and the question about
        // it hangs from that document rather than floating over the screen. Every other
        // window keeps working while it is up.
        JInternalFrame owner = frontWindow();
        if (Sheet.present(owner, state.body, closer -> state.closer = closer)) {
            return state.answer;
        }

        // Nothing to hang it from: a program with no window yet, or a check with no
        // screen. It stands on its own instead.
        JDialog dialog = new JDialog(Desktop.get(), panel.title().toString(),
                                     Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setResizable(false);
        state.dialog = dialog;
        state.closer = () -> {
            dialog.setVisible(false);
            dialog.dispose();
        };
        dialog.setContentPane(state.body);
        dialog.getAccessibleContext().setAccessibleName(state.body
            .getAccessibleContext().getAccessibleName());
        state.applyExpanded(state.expanded);
        dialog.setLocationRelativeTo(Desktop.get());
        dialog.setVisible(true);
        return state.answer;
    }

    /**
     * Everything in the panel, laid out, before it is put anywhere.
     *
     * Built apart from what carries it so that the same panel is the same panel whether it
     * hangs from a window or stands on its own, and so that it can be laid out and looked
     * at without being shown to anybody.
     */
    static JPanel build(State state, boolean opening) {
        JPanel body = new JPanel(new BorderLayout(0, 0));
        body.setBackground(Aqua.WINDOW_BG);
        body.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        body.setOpaque(true);

        body.add(top(state, opening), BorderLayout.NORTH);
        state.browser = browser(state);
        body.add(state.browser, BorderLayout.CENTER);
        body.add(bottom(state, opening), BorderLayout.SOUTH);
        body.getAccessibleContext().setAccessibleName(
            FMLocalized.of(opening ? FMSavePanel.OPEN_LABEL : FMSavePanel.SAVE_AS_LABEL)
                       .toString());

        state.expanded = rememberedExpanded();
        state.applyExpanded(state.expanded);
        state.setMode(rememberedMode());
        state.reload();
        return body;
    }

    /** The window a sheet would belong to: whichever one is in front. */
    private static JInternalFrame frontWindow() {
        Desktop desktop = Desktop.get();
        return desktop == null ? null : desktop.activeWindow();
    }

    /**
     * The top of the panel: what it is called, and where it is going.
     *
     * A save panel names the file here; an open panel has nothing to name, so the row is
     * the browser's own and the name field is not built at all rather than built and
     * disabled. A control that cannot be used is still a control somebody has to move
     * through to get past.
     */
    private static JComponent top(State state, boolean opening) {
        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));

        if (!state.panel.message().isEmpty()) {
            JLabel said = new JLabel(state.panel.message().toString());
            said.setFont(Aqua.systemFont());
            said.setAlignmentX(Component.LEFT_ALIGNMENT);
            rows.add(said);
            rows.add(Box.createVerticalStrut(8));
        }

        if (!opening) {
            JPanel nameRow = new JPanel(new BorderLayout(8, 0));
            nameRow.setOpaque(false);
            nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            nameRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

            JLabel label = new JLabel(state.panel.nameFieldLabel().toString());
            label.setFont(Aqua.systemFont());
            nameRow.add(label, BorderLayout.WEST);

            state.name = new JTextField(state.panel.nameFieldStringValue().toString());
            state.name.setFont(Aqua.systemFont());
            state.name.getAccessibleContext().setAccessibleName(
                state.panel.nameFieldLabel().toString());
            state.name.selectAll();
            state.name.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { state.nameChanged(); }
                @Override public void removeUpdate(DocumentEvent e) { state.nameChanged(); }
                @Override public void changedUpdate(DocumentEvent e) { state.nameChanged(); }
            });
            nameRow.add(state.name, BorderLayout.CENTER);
            nameRow.add(disclosure(state), BorderLayout.EAST);
            rows.add(nameRow);
            rows.add(Box.createVerticalStrut(8));
        }

        // The pop-up of usual places, which is the whole of the collapsed panel's
        // navigation and stays visible when it is expanded so the two agree.
        state.whereRow = new JPanel(new BorderLayout(8, 0));
        state.whereRow.setOpaque(false);
        state.whereRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        state.whereRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JLabel where = new JLabel(FMLocalized.of(FMSavePanel.WHERE_LABEL).toString());
        where.setFont(Aqua.systemFont());
        state.whereRow.add(where, BorderLayout.WEST);
        state.where = new JComboBox<>();
        state.where.setFont(Aqua.systemFont());
        state.where.getAccessibleContext().setAccessibleName(
            FMLocalized.of(FMSavePanel.WHERE_LABEL).toString());
        state.where.addActionListener(e -> state.wherePicked());
        state.whereRow.add(state.where, BorderLayout.CENTER);
        if (opening) state.whereRow.add(disclosure(state), BorderLayout.EAST);
        rows.add(state.whereRow);
        rows.add(Box.createVerticalStrut(10));
        return rows;
    }

    /** The triangle that moves between the two shapes of the panel. */
    private static JComponent disclosure(State state) {
        JButton triangle = new JButton("▼");
        triangle.setFont(Aqua.systemFont());
        triangle.setMargin(new Insets(0, 6, 0, 6));
        triangle.setFocusable(true);
        state.triangle = triangle;
        triangle.addActionListener(e -> {
            state.applyExpanded(!state.expanded);
            rememberExpanded(state.expanded);
        });
        return triangle;
    }

    /**
     * The browser: the places on the left, and the columns on the right.
     *
     * Columns rather than a list, because that is what this system shows a tree with and
     * because a list only ever says what is in one folder. In a column browser the route
     * is on the screen: where you are, what was beside it, and what you came through, all
     * at once and all still clickable.
     */
    private static JComponent browser(State state) {
        state.places = new JList<>(new DefaultListModel<>());
        state.places.setFont(Aqua.systemFont());
        state.places.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        state.places.getAccessibleContext().setAccessibleName(
            FMLocalized.of(FMSavePanel.PLACES).toString());
        state.places.setCellRenderer(new PlaceRenderer());
        // A heading is a label rather than a place, so it cannot be chosen and the
        // keyboard steps over it instead of stopping on something that does nothing.
        state.places.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) state.placePicked();
        });

        state.columns = new FMBrowser()
            .showing(state::allowed)
            .onChosen(state::browsedTo)
            .onOpened(state::openSelected);

        JScrollPane left = new JScrollPane(state.places);
        left.setPreferredSize(new Dimension(160, 10));
        left.setBorder(BorderFactory.createLineBorder(new Color(0xA0A0A0)));

        JPanel right = new JPanel(new BorderLayout(0, 6));
        right.setOpaque(false);
        right.add(navigation(state), BorderLayout.NORTH);
        right.add(state.columns, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setDividerLocation(160);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setOpaque(false);
        return split;
    }

    /**
     * The row above the browser: how to move, how it is shown, and what to look for.
     *
     * Back and forward walk the folders already visited, the way they do in a Finder
     * window. The three buttons beside them choose how a folder is drawn, and answer to
     * the same keys as in the Finder because it is the same choice. The field on the right
     * narrows what is shown to what is being looked for.
     */
    private static JComponent navigation(State state) {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);
        state.back = arrow("\u25c0", FMSavePanel.BACK, e -> state.goBack());
        state.forward = arrow("\u25b6", FMSavePanel.FORWARD, e -> state.goForward());
        left.add(state.back);
        left.add(state.forward);
        left.add(Box.createHorizontalStrut(8));
        left.add(viewButtons(state));
        bar.add(left, BorderLayout.WEST);

        state.here = new JLabel();
        state.here.setFont(Aqua.systemFont());
        state.here.setHorizontalAlignment(SwingConstants.CENTER);
        bar.add(state.here, BorderLayout.CENTER);

        state.search = new JTextField(12);
        state.search.setFont(Aqua.systemFont());
        state.search.getAccessibleContext().setAccessibleName(
            FMLocalized.of(FMSavePanel.SEARCH).toString());
        state.search.setToolTipText(FMLocalized.of(FMSavePanel.SEARCH).toString());
        state.search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { state.searchChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { state.searchChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { state.searchChanged(); }
        });
        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);
        right.add(state.search, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private static JButton arrow(String glyph, FMString named,
                                 java.awt.event.ActionListener what) {
        JButton button = new JButton(glyph);
        button.setFont(Aqua.systemFont());
        button.setMargin(new Insets(0, 8, 0, 8));
        button.setEnabled(false);
        button.getAccessibleContext().setAccessibleName(FMLocalized.of(named).toString());
        button.addActionListener(what);
        return button;
    }

    /**
     * The three buttons that choose how a folder is drawn.
     *
     * One is pressed at a time, so they say which by staying pressed, and each is named
     * for the view rather than for the picture on it: an icon of four squares tells
     * nothing to anybody not looking at it.
     */
    private static JComponent viewButtons(State state) {
        JPanel group = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        group.setOpaque(false);
        ButtonGroup one = new ButtonGroup();
        state.viewButtons.clear();
        FMBrowser.Mode[] modes = {FMBrowser.Mode.ICON, FMBrowser.Mode.LIST,
                                  FMBrowser.Mode.COLUMN};
        String[] glyphs = {"\u25a6", "\u2261", "\u2016"};
        FMString[] names = {FMSavePanel.AS_ICONS, FMSavePanel.AS_LIST, FMSavePanel.AS_COLUMNS};
        for (int i = 0; i < modes.length; i++) {
            FMBrowser.Mode mode = modes[i];
            JToggleButton button = new JToggleButton(glyphs[i]);
            button.setFont(Aqua.systemFont());
            button.setMargin(new Insets(0, 8, 0, 8));
            button.getAccessibleContext().setAccessibleName(
                FMLocalized.of(names[i]).toString());
            button.setToolTipText(FMLocalized.of(names[i]).toString());
            button.addActionListener(e -> state.setMode(mode));
            one.add(button);
            group.add(button);
            state.viewButtons.put(mode, button);
        }
        return group;
    }

    /** The buttons, and what a program put beside them. */
    private static JComponent bottom(State state, boolean opening) {
        JPanel all = new JPanel();
        all.setOpaque(false);
        all.setLayout(new BoxLayout(all, BoxLayout.Y_AXIS));

        if (state.panel.formats().count() > 0) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
            row.setOpaque(false);
            JLabel label = new JLabel(state.panel.formatLabel().toString());
            label.setFont(Aqua.systemFont());
            row.add(label);
            state.format = new JComboBox<>();
            for (FMString one : state.panel.formats()) state.format.addItem(one.toString());
            state.format.setSelectedIndex(Math.max(0,
                Math.min(state.panel.chosenFormat(), state.panel.formats().count() - 1)));
            state.format.setFont(Aqua.systemFont());
            state.format.getAccessibleContext().setAccessibleName(
                state.panel.formatLabel().toString());
            row.add(state.format);
            all.add(row);
        }

        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setOpaque(false);
        buttons.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        if (state.panel.canCreateDirectories()) {
            state.newFolder = new JButton(FMLocalized.of(FMSavePanel.NEW_FOLDER).toString());
            state.newFolder.setFont(Aqua.systemFont());
            state.newFolder.addActionListener(e -> state.makeFolder());
            buttons.add(state.newFolder, BorderLayout.WEST);
        }

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        JButton cancel = new JButton(FMLocalized.of(FMSavePanel.CANCEL_BUTTON).toString());
        cancel.setFont(Aqua.systemFont());
        cancel.addActionListener(e -> state.finish(FMSavePanel.CANCELLED));
        JButton go = new JButton(state.panel.prompt().toString());
        go.setFont(Aqua.systemFont());
        go.addActionListener(e -> state.accept());
        state.go = go;
        right.add(cancel);
        right.add(go);
        buttons.add(right, BorderLayout.EAST);
        all.add(buttons);

        SwingUtilities.invokeLater(() -> {
            if (state.dialog != null && state.dialog.getRootPane() != null) {
                state.dialog.getRootPane().setDefaultButton(go);
            }
            if (state.name != null) state.name.requestFocusInWindow();
        });
        // Escape leaves the panel, which is what Escape does everywhere and what somebody
        // who opened it by accident will try first. Held on the panel rather than on a
        // window, because a sheet has no window of its own to hold it.
        InputMap keys = all.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap does = all.getActionMap();
        keys.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "cancel");
        does.put("cancel", doing(() -> state.finish(FMSavePanel.CANCELLED)));

        // The same keys as in a Finder window, because it is the same choice being made.
        int command = org.fractalmicro.windowserver.MainMenu.CMD;
        int shift = org.fractalmicro.windowserver.MainMenu.SHIFT;
        keys.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_1, command), "icons");
        does.put("icons", doing(() -> state.setMode(FMBrowser.Mode.ICON)));
        keys.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_2, command), "list");
        does.put("list", doing(() -> state.setMode(FMBrowser.Mode.LIST)));
        keys.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_3, command), "columns");
        does.put("columns", doing(() -> state.setMode(FMBrowser.Mode.COLUMN)));

        keys.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, command), "find");
        does.put("find", doing(() -> {
            if (!state.expanded) state.applyExpanded(true);
            if (state.search != null) state.search.requestFocusInWindow();
        }));
        keys.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_G, command | shift), "goto");
        does.put("goto", doing(state::goToFolder));
        keys.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, command | shift), "new");
        does.put("new", doing(state::makeFolder));
        keys.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, command), "up");
        does.put("up", doing(() -> state.columns.goUp()));
        return all;
    }

    /** One key, one thing it does. Written once because there are eight of them. */
    private static AbstractAction doing(Runnable what) {
        return new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { what.run(); }
        };
    }

    /* ------------------------------------------------------------ what it remembers */

    private static boolean rememberedExpanded() {
        return FMUserDefaults.of(FMUserDefaults.GLOBAL).bool(EXPANDED, false);
    }

    private static void rememberExpanded(boolean expanded) {
        FMUserDefaults.of(FMUserDefaults.GLOBAL).set(EXPANDED, expanded);
    }

    /** Which of the three views the panel was last left in, kept between openings. */
    private static final FMString VIEW = FMString.of("NSNavPanelFileListModeForSaveMode");

    private static FMBrowser.Mode rememberedMode() {
        String named = FMUserDefaults.of(FMUserDefaults.GLOBAL).string(VIEW).toString();
        for (FMBrowser.Mode one : FMBrowser.Mode.values()) {
            if (one.name().equals(named)) return one;
        }
        return FMBrowser.Mode.COLUMN;
    }

    static void rememberMode(FMBrowser.Mode mode) {
        FMUserDefaults.of(FMUserDefaults.GLOBAL).set(VIEW, mode.name());
    }

    /* ------------------------------------------------------------- the panel's state */

    /** Everything one open panel is doing, so the pieces of it can talk to each other. */
    static final class State {
        final FMSavePanel panel;
        JDialog dialog;
        JPanel body;
        Runnable closer;
        JTextField name;
        JComboBox<String> where;
        JComboBox<String> format;
        JPanel whereRow;
        JComponent browser;
        JButton triangle;
        JButton newFolder;
        JButton go;
        JList<String> places;
        FMBrowser columns;
        JButton back;
        JButton forward;
        JLabel here;
        JTextField search;
        final java.util.Map<FMBrowser.Mode, JToggleButton> viewButtons =
            new java.util.LinkedHashMap<>();
        final List<File> visited = new ArrayList<>();
        int atVisit = -1;
        File folder;
        boolean expanded;
        int answer = FMSavePanel.CANCELLED;
        private final List<File> placeFiles = new ArrayList<>();
        private boolean settingWhere;

        State(FMSavePanel panel) { this.panel = panel; }

        void applyExpanded(boolean now) {
            expanded = now;
            browser.setVisible(now);
            if (newFolder != null) newFolder.setVisible(now);
            if (triangle != null) {
                triangle.setText(now ? "▲" : "▼");
                triangle.getAccessibleContext().setAccessibleName(FMLocalized.of(
                    now ? FMSavePanel.COLLAPSE : FMSavePanel.EXPAND).toString());
            }
            Dimension wanted = new Dimension(now ? EXPANDED_WIDTH : COLLAPSED_WIDTH,
                                             now ? EXPANDED_HEIGHT : COLLAPSED_HEIGHT);
            if (body != null) body.setPreferredSize(wanted);
            if (dialog != null) {
                dialog.setSize(wanted);
                dialog.validate();
            } else if (body != null) {
                // Already hanging from a window: it has to be resized where it hangs.
                body.setSize(body.getWidth(), wanted.height);
                body.revalidate();
                body.repaint();
            }
        }

        /** Fills the places, the pop-up and the listing for wherever the panel now is. */
        void reload() {
            // The same places the Finder's sidebar shows, from the same list. Somebody
            // who dragged a folder into the sidebar expects to find it here when they
            // save, which only holds while there is one list rather than two.
            DefaultListModel<String> sidebar = new DefaultListModel<>();
            placeFiles.clear();
            for (org.fractalmicro.fs.Places.Place one : org.fractalmicro.fs.Places.folders()) {
                if (!one.isRealFolder()) continue;
                placeFiles.add(one.file());
                sidebar.addElement(one.name().toString());
            }
            places.setModel(sidebar);

            settingWhere = true;
            where.removeAllItems();
            boolean known = false;
            for (File one : placeFiles) {
                where.addItem(one.getName());
                if (one.equals(folder)) known = true;
            }
            if (!known && folder != null) where.addItem(folder.getName());
            where.setSelectedItem(folder == null ? null : folder.getName());
            settingWhere = false;

            goTo(folder, true);
        }

        /**
         * Sends the browser somewhere, and remembers having been there.
         *
         * A jump from the sidebar or the pop-up starts a fresh route; walking into a
         * folder inside the one already shown adds a column to the route being taken. The
         * browser decides which of those it is, because it is the one holding the route.
         */
        void goTo(File folder, boolean remember) {
            if (folder == null) return;
            this.folder = folder;
            columns.show(folder);
            if (remember) {
                while (visited.size() > atVisit + 1) visited.remove(visited.size() - 1);
                if (visited.isEmpty() || !folder.equals(visited.get(visited.size() - 1))) {
                    visited.add(folder);
                    atVisit = visited.size() - 1;
                }
            }
            sayWhere();
        }

        void goBack() {
            if (atVisit <= 0) return;
            atVisit--;
            folder = visited.get(atVisit);
            columns.setRoot(folder);
            sayWhere();
        }

        void goForward() {
            if (atVisit + 1 >= visited.size()) return;
            atVisit++;
            folder = visited.get(atVisit);
            columns.setRoot(folder);
            sayWhere();
        }

        /** Keeps the label, the two arrows and the pop-up saying the same thing. */
        void sayWhere() {
            if (here != null) here.setText(folder == null ? "" : folder.getName());
            if (back != null) back.setEnabled(atVisit > 0);
            if (forward != null) forward.setEnabled(atVisit + 1 < visited.size());
            if (where != null && folder != null) {
                settingWhere = true;
                boolean listed = false;
                for (int i = 0; i < where.getItemCount(); i++) {
                    if (folder.getName().equals(where.getItemAt(i))) listed = true;
                }
                if (!listed) where.addItem(folder.getName());
                where.setSelectedItem(folder.getName());
                settingWhere = false;
            }
        }

        /** What the browser did: a folder moves the panel, a file names it. */
        /** Shows the folder the other way round, and marks the button that says so. */
        void setMode(FMBrowser.Mode wanted) {
            columns.setMode(wanted);
            JToggleButton button = viewButtons.get(wanted);
            if (button != null) button.setSelected(true);
            rememberMode(wanted);
        }

        void searchChanged() {
            columns.search(FMString.of(search == null ? "" : search.getText()));
        }

        /**
         * Asks for a folder by name and goes there.
         *
         * Typing a path is the fastest way to somewhere you already know, which is why
         * this exists beside a browser rather than instead of one. A path that is not a
         * folder is said so rather than silently ignored.
         */
        void goToFolder() {
            FMString typed = FMAlert.ask(FMLocalized.of(FMSavePanel.GO_TO_PROMPT),
                                       FMLocalized.of(FMSavePanel.GO_TO_LABEL),
                                       FMString.EMPTY,
                                       FMLocalized.of(FMSavePanel.GO_TO_BUTTON));
            if (typed.isBlank()) return;
            File asked = new File(typed.toString()
                .replace("~", System.getProperty("user.home")));
            if (asked.isDirectory()) {
                goTo(asked, true);
            } else {
                FMAlert.tell(FMLocalized.filled(FMSavePanel.NO_SUCH_FOLDER, typed),
                           FMLocalized.of(FMSavePanel.CHECK_SPELLING));
            }
        }

        void browsedTo(File what) {
            if (what == null) return;
            if (what.isDirectory()) {
                folder = what;
                sayWhere();
            } else if (name != null) {
                name.setText(what.getName());
            }
        }

        boolean allowed(File file) {
            FMArray<FMString> types = panel.allowedFileTypes();
            if (types.count() == 0) return true;
            String name = file.getName().toLowerCase(java.util.Locale.ROOT);
            for (FMString one : types) {
                if (name.endsWith("." + one.toString().toLowerCase(java.util.Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }

        void wherePicked() {
            if (settingWhere) return;
            int at = where.getSelectedIndex();
            if (at >= 0 && at < placeFiles.size()) goTo(placeFiles.get(at), true);
        }

        void placePicked() {
            int at = places.getSelectedIndex();
            if (at < 0 || at >= placeFiles.size()) return;
            goTo(placeFiles.get(at), true);
        }

        /** Double clicking a folder goes into it, and a file chooses it outright. */
        void openSelected(File what) {
            if (what == null) return;
            if (what.isDirectory()) goTo(what, true); else accept();
        }

        void nameChanged() {
            if (go != null && name != null) go.setEnabled(!name.getText().isBlank());
        }

        /** Makes a folder where the panel is looking, and goes into it. */
        void makeFolder() {
            if (folder == null) return;
            File made = FS.newFolder(folder);
            if (made == null) return;
            goTo(made, true);
        }

        /**
         * Takes what the panel is showing as the answer.
         *
         * A save over something that is already there asks first, because that is the one
         * thing a save panel does that cannot be undone.
         */
        void accept() {
            if (panel instanceof FMOpenPanel) {
                File picked = columns.selection();
                if (picked == null) return;
                if (picked.isDirectory()) { goTo(picked, true); return; }
                panel.chose(FMURL.of(picked), 0);
                finish(FMSavePanel.OK);
                return;
            }
            if (name == null || name.getText().isBlank() || folder == null) return;
            FMURL where = panel.completing(
                FMURL.of(folder).appending(FMString.of(name.getText().trim())));
            if (where.exists()) {
                boolean over = FMAlert.confirm(FMAlert.Kind.CAUTION,
                    FMLocalized.filled(FMSavePanel.REPLACE_QUESTION, where.lastComponent()),
                    FMLocalized.of(FMSavePanel.REPLACE_WARNING),
                    FMLocalized.of(FMSavePanel.REPLACE_BUTTON));
                if (!over) return;
            }
            panel.chose(where, format == null ? 0 : format.getSelectedIndex());
            finish(FMSavePanel.OK);
        }

        void finish(int how) {
            answer = how;
            if (closer != null) closer.run();
        }
    }

    /* ------------------------------------------------------------- how rows look */

    /** A place in the sidebar: its name, with the folder icon beside it. */
    private static final class PlaceRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean chosen, boolean focused) {
            super.getListCellRendererComponent(list, value, index, chosen, focused);
            setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 4));
            return this;
        }
    }

    /**
     * A row in the listing: what the file is called, and what kind it is.
     *
     * The kind is said as well as shown, because a listing that only draws an icon tells
     * nothing to anyone who is not looking at it.
     */
    private static final class FileRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean chosen, boolean focused) {
            Node node = value instanceof Node n ? n : null;
            String text = node == null ? "" : node.name;
            super.getListCellRendererComponent(list, text, index, chosen, focused);
            setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 4));
            if (node != null) {
                getAccessibleContext().setAccessibleName(
                    node.name + ", " + Kinds.of(node));
            }
            return this;
        }
    }
}
