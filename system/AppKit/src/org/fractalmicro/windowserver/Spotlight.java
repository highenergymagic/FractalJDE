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

import org.fractalmicro.foundation.FMLocalized;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.bundle.LaunchServices;


import org.fractalmicro.core.Shell;
import org.fractalmicro.fs.Node;
import org.fractalmicro.fs.Search;
import org.fractalmicro.theme.Aqua;
import org.fractalmicro.theme.Icons;

import org.fractalmicro.appkit.FMTextField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * Spotlight: a search field under the magnifying glass in the menu bar, with results
 * appearing as you type. Command Space opens it; Escape puts it away.
 */
public class Spotlight extends JDialog {

    private static Spotlight instance;

    private final FMTextField field = new FMTextField(24);
    private final DefaultListModel<Node> model = new DefaultListModel<>();
    private final JList<Node> results = new JList<>(model);
    private final JLabel summary = new JLabel(word(FMString.of("spotlight.typeToSearch")));
    private final Timer debounce;

    private Spotlight(Desktop owner) {
        super(owner, "Spotlight", false);
        setUndecorated(true);
        setFocusableWindowState(true);

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x8A8A8A)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        panel.setBackground(new Color(0xF4F4F4));

        JLabel label = new JLabel(word(FMString.of("spotlight.name")));
        label.setFont(Aqua.smallFont());
        label.setLabelFor(field);
        field.setFont(Aqua.systemFont());
        field.getAccessibleContext().setAccessibleName(word(FMString.of("spotlight.search")));

        results.setCellRenderer(new ResultRenderer());
        results.setVisibleRowCount(10);
        results.getAccessibleContext().setAccessibleName("Results");
        results.setFont(Aqua.smallFont());

        summary.setFont(Aqua.smallFont());


        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setOpaque(false);
        top.add(label, BorderLayout.WEST);
        top.add(field, BorderLayout.CENTER);

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(results), BorderLayout.CENTER);
        panel.add(summary, BorderLayout.SOUTH);
        setContentPane(panel);
        setSize(420, 320);

        debounce = new Timer(220, e -> search());
        debounce.setRepeats(false);

        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { debounce.restart(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { debounce.restart(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { debounce.restart(); }
        });

        field.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN && !model.isEmpty()) {
                    results.requestFocusInWindow();
                    results.setSelectedIndex(0);
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    openTop();
                }
            }
        });
        results.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) openSelected();
                else if (e.getKeyCode() == KeyEvent.VK_UP && results.getSelectedIndex() == 0) {
                    field.requestFocusInWindow();
                }
            }
        });
        results.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openSelected();
            }
        });

        KeyStroke esc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(esc, "close");
        getRootPane().getActionMap().put("close", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { setVisible(false); }
        });

        getAccessibleContext().setAccessibleName("Spotlight");
    }

    public static void open() {
        Desktop d = Desktop.sharedDesktop();
        if (d == null) return;
        if (instance == null) instance = new Spotlight(d);
        Rectangle b = d.getBounds();
        instance.setLocation(b.x + b.width - instance.getWidth() - 16, b.y + Aqua.MENU_BAR_HEIGHT + 2);
        instance.setVisible(true);
        instance.field.selectAll();
        instance.field.requestFocusInWindow();
    }

    /** Opens Spotlight with a search already entered, for the Services menu. */
    public static void openSearching(String what) {
        open();
        if (instance == null || what == null) return;
        instance.field.setText(what);
        instance.search();
    }

    private static String word(FMString key) {
        return FMLocalized.of(key).toString();
    }

    private void search() {
        String q = field.getText();
        if (q.isBlank()) {
            model.clear();
            summary.setText(word(FMString.of("spotlight.typeToSearch")));
            return;
        }
        summary.setText(word(FMString.of("spotlight.searching")));
        Shell.async(() -> {
            List<Node> hits = Search.everywhere(q, 60);
            SwingUtilities.invokeLater(() -> {
                model.clear();
                for (Node n : hits) model.addElement(n);
                // Three sentences rather than one built out of pieces: how many, then
                // what was looked for, then whether the index was there to ask.
                FMString said;
                if (hits.isEmpty()) {
                    said = FMLocalized.filled(FMString.of("spotlight.noResults"), FMString.of(q));
                } else if (hits.size() == 1) {
                    said = FMLocalized.filled(FMString.of("spotlight.oneResult"), FMString.of(q));
                } else {
                    said = FMLocalized.filled(FMString.of("spotlight.someResults"),
                        FMString.of(String.valueOf(hits.size())), FMString.of(q));
                }
                if (!org.fractalmicro.fs.Search.serverAnswering()) {
                    said = FMLocalized.filled(FMString.of("spotlight.withoutIndex"), said);
                }
                summary.setText(said.toString());
            });
        });
    }

    private void openTop() {
        if (model.isEmpty()) { Desktop.beep(); return; }
        open(model.get(0));
    }

    private void openSelected() {
        Node n = results.getSelectedValue();
        if (n != null) open(n);
    }

    /** What is said when the thing found cannot be opened after all. */
    private static final org.fractalmicro.foundation.FMString NOTHING_OPENS_THAT =
        org.fractalmicro.foundation.FMString.of("spotlight.nothingOpensThat");

    private void open(Node n) {
        setVisible(false);
        if (!LaunchServices.open(n)) {
            org.fractalmicro.appkit.FMAlert.tell(
                org.fractalmicro.foundation.FMLocalized.of(NOTHING_OPENS_THAT),
                org.fractalmicro.foundation.FMString.EMPTY);
        }
    }

    private static class ResultRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                boolean selected, boolean focused) {
            Node n = (Node) value;
            super.getListCellRendererComponent(list, n.name, index, selected, focused);
            setIcon(new ImageIcon(Icons.forNode(n, 16)));
            getAccessibleContext().setAccessibleName(n.accessibleName());
            return this;
        }
    }
}
