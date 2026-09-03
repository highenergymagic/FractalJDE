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

import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMLocalized;

import org.fractalmicro.appkit.FMAlert;
import org.fractalmicro.windowserver.Desktop;

import org.fractalmicro.core.Running;
import org.fractalmicro.fs.Node;
import org.fractalmicro.theme.Aqua;
import org.fractalmicro.theme.Icons;

import javax.swing.*;
import java.awt.*;

/** Force Quit Applications: what this desktop has started, and a button to drop it. */
public class ForceQuitWindow extends JInternalFrame {

    private static ForceQuitWindow instance;

    private static String word(FMString key) {
        return FMLocalized.of(key).toString();
    }

    /**
     * A program that has stopped answering says so, in red, as it does on a Mac.
     *
     * The mark is drawn and not stored. What the list holds is the program's name, which
     * is what the button acts on, and a name that had "(Not Responding)" glued onto it
     * would be a name nothing could match.
     */
    private static final class Stuck extends javax.swing.DefaultListCellRenderer {
        @Override public java.awt.Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean chosen, boolean focused) {
            super.getListCellRendererComponent(list, value, index, chosen, focused);
            String name = String.valueOf(value);
            if (org.fractalmicro.core.WindowList.notResponding(name)) {
                setText(name + " " + word(FMString.of("forceQuit.notResponding")));
                if (!chosen) setForeground(java.awt.Color.RED);
            }
            return this;
        }
    }

    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list = new JList<>(model);

    private ForceQuitWindow() {
        super(word(FMString.of("forceQuit.title")), true, true, false, true);
        setFrameIcon(new ImageIcon(Icons.forKind(Node.Kind.APPLICATION, 16)));
        setSize(360, 300);

        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        p.setBackground(Aqua.WINDOW_BG);

        JLabel hint = new JLabel("<html>" + word(FMString.of("forceQuit.hint")) + "</html>");
        hint.setFont(Aqua.smallFont());
        p.add(hint, BorderLayout.NORTH);

        list.getAccessibleContext().setAccessibleName(word(FMString.of("forceQuit.applications")));
        list.setCellRenderer(new Stuck());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        p.add(new JScrollPane(list), BorderLayout.CENTER);

        JButton quit = new JButton(word(FMString.of("forceQuit.button")));
        quit.getAccessibleContext().setAccessibleName(word(FMString.of("forceQuit.button")));
        quit.addActionListener(e -> forceQuit());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.setOpaque(false);
        south.add(quit);
        p.add(south, BorderLayout.SOUTH);

        setContentPane(p);
        getAccessibleContext().setAccessibleName(word(FMString.of("forceQuit.title")));
        Running.onChange(this::refresh);
        refresh();
    }

    public static void open() {
        if (instance == null || instance.isClosed()) {
            instance = new ForceQuitWindow();
            Desktop.sharedDesktop().addWindow(instance);
        } else {
            instance.toFront();
            try { instance.setSelected(true); } catch (java.beans.PropertyVetoException ignored) { }
        }
    }

    private void refresh() {
        String selected = list.getSelectedValue();
        model.clear();
        model.addElement("Finder");
        for (Running.Entry e : Running.all()) model.addElement(e.name);
        if (selected != null) list.setSelectedValue(selected, true);
    }

    private void forceQuit() {
        String name = list.getSelectedValue();
        if (name == null) {
            Desktop.beep();
            return;
        }
        if ("Finder".equals(name)) {
            org.fractalmicro.foundation.FMNotificationCenter.defaultCenter()
                .post(org.fractalmicro.foundation.FMNotificationCenter.PROGRAMS_CHANGED);
            org.fractalmicro.appkit.FMAlert.tell(
                FMLocalized.of(FMString.of("forceQuit.finderRelaunches")), FMString.EMPTY);
            return;
        }
        boolean go = FMAlert.confirm(FMAlert.Kind.CAUTION,
            FMLocalized.filled(FMString.of("forceQuit.ask"), FMString.of(name)),
            FMLocalized.of(FMString.of("forceQuit.warning")),
            FMLocalized.of(FMString.of("forceQuit.button")));
        if (!go) return;
        Desktop.quitApplication(name);
        refresh();
    }
}
