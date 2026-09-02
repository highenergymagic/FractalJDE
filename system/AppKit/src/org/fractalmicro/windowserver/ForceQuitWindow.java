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

    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list = new JList<>(model);

    private ForceQuitWindow() {
        super("Force Quit Applications", true, true, false, true);
        setFrameIcon(new ImageIcon(Icons.forKind(Node.Kind.APPLICATION, 16)));
        setSize(360, 300);

        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        p.setBackground(Aqua.WINDOW_BG);

        JLabel hint = new JLabel("<html>If an application does not respond, select it and click Force Quit.</html>");
        hint.setFont(Aqua.smallFont());
        p.add(hint, BorderLayout.NORTH);

        list.getAccessibleContext().setAccessibleName("Applications");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        p.add(new JScrollPane(list), BorderLayout.CENTER);

        JButton quit = new JButton("Force Quit");
        quit.getAccessibleContext().setAccessibleName("Force Quit");
        quit.addActionListener(e -> forceQuit());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.setOpaque(false);
        south.add(quit);
        p.add(south, BorderLayout.SOUTH);

        setContentPane(p);
        getAccessibleContext().setAccessibleName("Force Quit Applications");
        Running.onChange(this::refresh);
        refresh();
    }

    public static void open() {
        if (instance == null || instance.isClosed()) {
            instance = new ForceQuitWindow();
            Desktop.get().addWindow(instance);
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
            Desktop.beep("Select an application first.");
            return;
        }
        if ("Finder".equals(name)) {
            org.fractalmicro.foundation.FMNotificationCenter.defaultCenter()
                .post(org.fractalmicro.foundation.FMNotificationCenter.PROGRAMS_CHANGED);
            Desktop.beep("The Finder relaunches rather than quitting.");
            return;
        }
        boolean go = FMAlert.confirm(FMAlert.Kind.CAUTION,
            FMString.of("Do you want to force " + '“' + name + '”' + " to quit?"),
            FMString.of("You will lose any unsaved changes."), FMString.of("Force Quit"));
        if (!go) return;
        Desktop.quitApplication(name);
        refresh();
    }
}
