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

import org.fractalmicro.foundation.FMString;

import org.fractalmicro.theme.Aqua;
import org.fractalmicro.windowserver.Desktop;
import org.fractalmicro.win.SpellChecker;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The spelling panel: the word that is wrong, what to put instead, and what to do.
 *
 * The red line under a word is only of use to someone who can see it. This is the same
 * information as text: the word is in a field, the suggestions are in a list with names,
 * and every action is a button. Working through a document with the keyboard alone gets
 * to the same place as working through it with the mouse.
 */
public final class SpellingPanel extends JInternalFrame {

    private static final Map<javax.swing.text.JTextComponent, SpellingPanel> OPEN =
        new HashMap<>();

    private final javax.swing.text.JTextComponent text;
    private final Spelling spelling;
    private final FMTextField word = new FMTextField(20);
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> suggestions = new JList<>(model);
    private final JLabel status = new JLabel(" ");
    private SpellChecker.Mistake current;

    private SpellingPanel(javax.swing.text.JTextComponent text, Spelling spelling) {
        super("Spelling and Grammar", true, true, false, false);
        this.text = text;
        this.spelling = spelling;

        JPanel body = new JPanel(new BorderLayout(12, 12));
        body.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));

        JPanel top = new JPanel(new BorderLayout(8, 4));
        JLabel label = new JLabel("Word:");
        label.setFont(Aqua.systemFont());
        label.setLabelFor(word);
        word.getAccessibleContext().setAccessibleName("Word");
        top.add(label, BorderLayout.WEST);
        top.add(word, BorderLayout.CENTER);
        body.add(top, BorderLayout.NORTH);

        suggestions.getAccessibleContext().setAccessibleName("Suggestions");
        suggestions.setVisibleRowCount(6);
        suggestions.addListSelectionListener(e -> {
            String chosen = suggestions.getSelectedValue();
            if (chosen != null) word.setText(chosen);
        });
        body.add(new JScrollPane(suggestions), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(1, 4, 8, 0));
        buttons.add(button("Ignore", e -> ignore()));
        buttons.add(button("Learn", e -> learn()));
        buttons.add(button("Find Next", e -> findNext()));
        JButton change = button("Change", e -> change());
        buttons.add(change);

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        status.setFont(Aqua.smallFont());
        status.getAccessibleContext().setAccessibleName("Spelling status");
        bottom.add(status, BorderLayout.NORTH);
        bottom.add(buttons, BorderLayout.SOUTH);
        body.add(bottom, BorderLayout.SOUTH);

        setContentPane(body);
        getRootPane().setDefaultButton(change);
        pack();
        getAccessibleContext().setAccessibleName("Spelling and grammar");
        addInternalFrameListener(new javax.swing.event.InternalFrameAdapter() {
            @Override public void internalFrameClosed(javax.swing.event.InternalFrameEvent e) {
                OPEN.remove(text);
            }
        });
    }

    private JButton button(String text, java.awt.event.ActionListener action) {
        JButton b = new JButton(text);
        b.addActionListener(action);
        return b;
    }

    /* -------------------------------------------------------------- opening */

    public static void show(javax.swing.text.JTextComponent text, Spelling spelling) {
        if (!Spelling.available()) {
            org.fractalmicro.appkit.FMAlert.tell(FMString.of("Spelling cannot be checked on this computer."),
                FMString.of("No spelling dictionary is installed for any language this system asked for."));
            return;
        }
        SpellingPanel panel = OPEN.get(text);
        if (panel == null) {
            panel = new SpellingPanel(text, spelling);
            OPEN.put(text, panel);
            Desktop.sharedDesktop().addWindow(panel);
        } else {
            try {
                panel.setSelected(true);
            } catch (java.beans.PropertyVetoException ignored) { }
        }
        panel.findNext();
    }

    public static boolean isShowing(javax.swing.text.JTextComponent text) {
        return OPEN.containsKey(text);
    }

    public static void forget(javax.swing.text.JTextComponent text) {
        SpellingPanel panel = OPEN.remove(text);
        if (panel != null) panel.dispose();
    }

    /* -------------------------------------------------------------- working */

    /** Moves to the next misspelling and fills the panel in with it. */
    public void findNext() {
        spelling.checkNow();
        List<SpellChecker.Mistake> all = spelling.mistakes();
        if (all.isEmpty()) {
            current = null;
            word.setText("");
            model.clear();
            status.setText("No misspellings found");
            return;
        }
        current = spelling.after(text.getSelectionEnd());
        if (current == null) current = all.get(0);
        spelling.goTo(current);
        word.setText(spelling.wordOf(current).toString());
        model.clear();
        for (String suggestion : current.suggestions()) model.addElement(suggestion);
        if (!model.isEmpty()) suggestions.setSelectedIndex(0);
        status.setText(all.size() == 1 ? "1 misspelling" : all.size() + " misspellings");
        // The window keeps the keyboard, so the word and its suggestions can be worked
        // through without going back to the document.
        word.requestFocusInWindow();
    }

    private void change() {
        if (current == null) return;
        String replacement = word.getText().trim();
        if (replacement.isEmpty()) return;
        spelling.replace(current, FMString.of(replacement));
        findNext();
    }

    private void ignore() {
        if (current == null) return;
        spelling.ignore(current);
        findNext();
    }

    private void learn() {
        if (current == null) return;
        spelling.learn(current);
        findNext();
    }
}
