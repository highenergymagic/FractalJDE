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

import org.fractalmicro.windowserver.Desktop;

import org.fractalmicro.os.FinderSettings;
import org.fractalmicro.fs.Node;
import org.fractalmicro.theme.Aqua;
import org.fractalmicro.theme.Icons;

import javax.swing.*;
import java.awt.*;

/** Show View Options: icon size and sort order for the front window. */
public class ViewOptionsWindow extends JInternalFrame {

    private static ViewOptionsWindow instance;

    private ViewOptionsWindow() {
        super("View Options", true, true, false, false);
        setFrameIcon(new ImageIcon(Icons.forKind(Node.Kind.FOLDER, 16)));
        setSize(280, 240);

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        p.setBackground(Aqua.WINDOW_BG);

        JLabel sizeLabel = new JLabel("Icon size");
        sizeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JSlider size = new JSlider(32, 128, FinderSettings.windowIconSize());
        size.setAlignmentX(Component.LEFT_ALIGNMENT);
        size.setMajorTickSpacing(32);
        size.setPaintTicks(true);
        size.setMaximumSize(new Dimension(240, 46));
        size.getAccessibleContext().setAccessibleName("Icon size");
        sizeLabel.setLabelFor(size);
        size.addChangeListener(e -> {
            if (size.getValueIsAdjusting()) return;
            FinderSettings.setWindowIconSize(size.getValue());
            FinderWindow w = Finder.frontWindow();
            if (w != null) w.setIconSize(size.getValue());
        });
        p.add(sizeLabel);
        p.add(size);

        JLabel sortLabel = new JLabel("Arrange by");
        sortLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JComboBox<String> sort = new JComboBox<>(new String[]{"Name", "Date Modified", "Size", "Kind"});
        sort.setAlignmentX(Component.LEFT_ALIGNMENT);
        sort.setMaximumSize(new Dimension(200, 24));
        sort.getAccessibleContext().setAccessibleName("Arrange by");
        sortLabel.setLabelFor(sort);
        sort.addActionListener(e -> {
            FinderWindow w = Finder.frontWindow();
            if (w != null) w.arrangeBy(FinderSettings.arrangeKeyFor((String) sort.getSelectedItem()));
        });
        p.add(Box.createVerticalStrut(10));
        p.add(sortLabel);
        p.add(sort);

        JLabel viewLabel = new JLabel("Default view for new windows");
        viewLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JComboBox<String> view = new JComboBox<>(new String[]{"Icon", "List", "Column", "Cover Flow"});
        view.setSelectedItem(FinderSettings.viewNameFor(FinderSettings.preferredViewStyle()));
        view.setAlignmentX(Component.LEFT_ALIGNMENT);
        view.setMaximumSize(new Dimension(200, 24));
        view.getAccessibleContext().setAccessibleName("Default view");
        viewLabel.setLabelFor(view);
        view.addActionListener(e -> FinderSettings.setPreferredViewStyle(
            FinderSettings.viewCodeFor(
                org.fractalmicro.foundation.FMString.describing(view.getSelectedItem()))));
        p.add(Box.createVerticalStrut(10));
        p.add(viewLabel);
        p.add(view);

        setContentPane(p);
        getAccessibleContext().setAccessibleName("View Options");
    }

    public static void open() {
        if (instance == null || instance.isClosed()) {
            instance = new ViewOptionsWindow();
            Desktop.sharedDesktop().addWindow(instance);
        } else {
            instance.toFront();
            try { instance.setSelected(true); } catch (java.beans.PropertyVetoException ignored) { }
        }
    }
}
