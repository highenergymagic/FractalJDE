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

import org.fractalmicro.windowserver.Desktop;

import org.fractalmicro.fs.Node;
import org.fractalmicro.theme.Aqua;
import org.fractalmicro.theme.Icons;

import org.fractalmicro.appkit.FMTextArea;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMLocalized;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/** Fractal Help, and the list of keyboard shortcuts. */
public class HelpWindow extends JInternalFrame {

    private HelpWindow(String title, JComponent body, int w, int h) {
        super(title, true, true, true, true);
        setFrameIcon(new ImageIcon(Icons.forKind(Node.Kind.SEARCH, 16)));
        setContentPane(body);
        setSize(w, h);
        getAccessibleContext().setAccessibleName(title);
    }

    public static void openHelp() {
        FMTextArea text = new FMTextArea(helpText());
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(Aqua.systemFont());
        text.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        text.setCaretPosition(0);
        String title = FMLocalized.of(HELP_TITLE).toString();
        text.getAccessibleContext().setAccessibleName(title);
        Desktop.sharedDesktop().addWindow(
            new HelpWindow(title, new JScrollPane(text), 560, 460));
    }

    /**
     * Every shortcut, read out of the menu bar.
     *
     * There were forty rows written here, each a keystroke and a sentence, kept true by
     * somebody remembering to. The bar already holds every shortcut and what it does, in
     * the language this account reads, so the list is the bar.
     */
    public static void showShortcuts() {
        String title = FMLocalized.of(SHORTCUTS_TITLE).toString();
        DefaultTableModel model = new DefaultTableModel(new String[]{
            FMLocalized.of(SHORTCUT_COLUMN).toString(),
            FMLocalized.of(MEANING_COLUMN).toString()}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        org.fractalmicro.a11y.Announcer.learn(Desktop.sharedDesktop().mainMenu());
        for (javax.swing.KeyStroke stroke : org.fractalmicro.a11y.Announcer.known()) {
            String says = org.fractalmicro.a11y.Announcer.phraseFor(stroke);
            if (says == null || says.isBlank()) continue;
            model.addRow(new String[]{
                org.fractalmicro.theme.AquaMenuPainter.acceleratorText(stroke), says});
        }
        JTable table = new JTable(model);
        table.setFont(Aqua.smallFont());
        table.setRowHeight(20);
        table.getColumnModel().getColumn(0).setPreferredWidth(200);
        table.getColumnModel().getColumn(1).setPreferredWidth(340);
        table.getAccessibleContext().setAccessibleName(title);
        Desktop.sharedDesktop().addWindow(
            new HelpWindow(title, new JScrollPane(table), 600, 500));
    }
    /**
     * The help text, read from the framework rather than written in here.
     *
     * A page of prose in the source is a page nobody can translate and nobody can correct
     * without a compiler. It sits beside AppKit's other words, one file per language,
     * which is where a Help Book would be on a Mac.
     */
    private static FMString helpText() {
        FMString text = FMLocalized.resource(HELP_FILE);
        return text.isEmpty() ? FMLocalized.of(NO_HELP) : text;
    }

    private static final FMString HELP_FILE = FMString.of("Help.txt");

    private static final FMString HELP_TITLE = FMString.of("help.title");
    private static final FMString SHORTCUTS_TITLE = FMString.of("help.shortcutsTitle");
    private static final FMString SHORTCUT_COLUMN = FMString.of("help.shortcutColumn");
    private static final FMString MEANING_COLUMN = FMString.of("help.meaningColumn");
    private static final FMString NO_HELP = FMString.of("help.notInstalled");
}
