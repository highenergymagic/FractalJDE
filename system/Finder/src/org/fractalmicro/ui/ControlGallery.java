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

import org.fractalmicro.fs.Node;
import org.fractalmicro.theme.Aqua;
import org.fractalmicro.theme.Icons;

import org.fractalmicro.appkit.FMTextField;

import javax.swing.*;
import java.awt.*;

/**
 * One of everything, for looking at.
 *
 * Opened with --controls. It exists so the drawing can be checked against a screenshot
 * of the real thing without hunting through the program for a window that happens to
 * contain a checkbox.
 */
public class ControlGallery extends JInternalFrame {

    public ControlGallery() {
        super("Controls", true, true, true, true);
        setFrameIcon(new ImageIcon(Icons.forKind(Node.Kind.APPLICATION, 16)));
        setSize(480, 640);

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(Aqua.WINDOW_MARGIN, Aqua.WINDOW_MARGIN,
                                                    Aqua.WINDOW_MARGIN, Aqua.WINDOW_MARGIN));
        p.setBackground(Aqua.WINDOW_BG);

        p.add(heading("Push buttons"));
        JPanel buttons = row();
        JButton plain = new JButton("Cancel");
        JButton action = new JButton("Empty Trash");
        JButton off = new JButton("Disabled");
        off.setEnabled(false);
        buttons.add(plain);
        buttons.add(Box.createHorizontalStrut(Aqua.CONTROL_SPACING));
        buttons.add(action);
        buttons.add(Box.createHorizontalStrut(Aqua.CONTROL_SPACING));
        buttons.add(off);
        p.add(buttons);
        p.add(gap());

        p.add(heading("Checkboxes and radio buttons"));
        JCheckBox on = new JCheckBox("Show all filename extensions", true);
        JCheckBox offBox = new JCheckBox("Show warning before emptying the Trash", false);
        on.setBackground(Aqua.WINDOW_BG);
        offBox.setBackground(Aqua.WINDOW_BG);
        on.setAlignmentX(Component.LEFT_ALIGNMENT);
        offBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(on);
        p.add(offBox);

        ButtonGroup group = new ButtonGroup();
        JRadioButton first = new JRadioButton("Together", true);
        JRadioButton second = new JRadioButton("At top and bottom", false);
        for (JRadioButton r : new JRadioButton[]{first, second}) {
            r.setBackground(Aqua.WINDOW_BG);
            r.setAlignmentX(Component.LEFT_ALIGNMENT);
            group.add(r);
            p.add(r);
        }
        p.add(gap());

        p.add(heading("Text field"));
        FMTextField field = new FMTextField(org.fractalmicro.foundation.FMString.of("Local Disk"));
        field.getAccessibleContext().setAccessibleName("Volume name");
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(240, field.getPreferredSize().height));
        p.add(field);
        p.add(gap());

        p.add(heading("Scroll bars"));
        DefaultListModel<String> model = new DefaultListModel<>();
        for (int i = 1; i <= 40; i++) model.addElement("Item " + i);
        JList<String> list = new JList<>(model);
        list.getAccessibleContext().setAccessibleName("Items");
        list.setFont(Aqua.viewFont());
        list.setVisibleRowCount(5);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setMaximumSize(new Dimension(400, 110));
        p.add(scroll);
        p.add(gap());

        p.add(heading("Sliders"));
        JSlider slider = new JSlider(0, 100, 40);
        slider.getAccessibleContext().setAccessibleName("Plain slider");
        slider.setAlignmentX(Component.LEFT_ALIGNMENT);
        slider.setMaximumSize(new Dimension(240, slider.getPreferredSize().height));
        p.add(slider);
        JSlider ticked = new JSlider(0, 100, 70);
        ticked.setPaintTicks(true);
        ticked.setMajorTickSpacing(25);
        ticked.getAccessibleContext().setAccessibleName("Slider with tick marks");
        ticked.setAlignmentX(Component.LEFT_ALIGNMENT);
        ticked.setMaximumSize(new Dimension(240, ticked.getPreferredSize().height));
        p.add(ticked);
        p.add(gap());

        p.add(heading("Pop-up button"));
        JComboBox<String> popup = new JComboBox<>(new String[]{
            "Name", "Date Modified", "Size", "Kind"});
        popup.getAccessibleContext().setAccessibleName("Arrange by");
        popup.setAlignmentX(Component.LEFT_ALIGNMENT);
        popup.setMaximumSize(new Dimension(200, popup.getPreferredSize().height));
        p.add(popup);
        p.add(gap());

        p.add(heading("Progress"));
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(62);
        bar.getAccessibleContext().setAccessibleName("Copying");
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setMaximumSize(new Dimension(240, 16));
        p.add(bar);
        JProgressBar spinner = new JProgressBar();
        spinner.setIndeterminate(true);
        spinner.getAccessibleContext().setAccessibleName("Working");
        spinner.setAlignmentX(Component.LEFT_ALIGNMENT);
        spinner.setMaximumSize(new Dimension(240, 16));
        p.add(spinner);
        p.add(gap());

        // The three that arrived with the description protocol, which the gallery predates.
        // A control a program can name and this cannot show is a control nobody can look at
        // before they use it, which is the one thing a gallery is for.
        p.add(heading("Toolbar"));
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        JPanel toolbarLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        toolbarLeft.setOpaque(false);
        toolbarLeft.add(named(new JButton("◀"), "Back"));
        toolbarLeft.add(named(new JButton("▶"), "Forward"));
        JPanel toolbarRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        toolbarRight.setOpaque(false);
        FMTextField search = new FMTextField(12);
        search.getAccessibleContext().setAccessibleName("Search");
        toolbarRight.add(search);
        toolbar.add(toolbarLeft, BorderLayout.WEST);
        toolbar.add(toolbarRight, BorderLayout.EAST);
        toolbar.setAlignmentX(Component.LEFT_ALIGNMENT);
        toolbar.setMaximumSize(new Dimension(400, 34));
        p.add(toolbar);
        p.add(gap());

        p.add(heading(org.fractalmicro.foundation.FMLocalized.of(
            org.fractalmicro.foundation.FMString.of("gallery.splitView")).toString()));
        JList<String> places = new JList<>(new String[]{
            "DEVICES", "Startup", "PLACES", "Desktop", "Documents"});
        places.getAccessibleContext().setAccessibleName("Places");
        places.setFont(Aqua.viewFont());
        org.fractalmicro.appkit.FMBrowser browser = new org.fractalmicro.appkit.FMBrowser();
        browser.setRoot(org.fractalmicro.fs.FS.home());
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                          new JScrollPane(places), browser);
        split.getAccessibleContext().setAccessibleName("Sidebar and folder");
        split.setDividerLocation(120);
        split.setDividerSize(6);
        split.setBorder(null);
        split.setAlignmentX(Component.LEFT_ALIGNMENT);
        split.setMaximumSize(new Dimension(400, 160));
        split.setPreferredSize(new Dimension(400, 160));
        p.add(split);
        p.add(gap());

        p.add(heading("Tabs"));
        JTabbedPane tabs = new JTabbedPane();
        tabs.getAccessibleContext().setAccessibleName("Panes");
        tabs.addTab("General", new JLabel("  Settings for everything"));
        tabs.addTab("Labels", new JLabel("  Coloured labels"));
        tabs.addTab("Sidebar", new JLabel("  What the sidebar shows"));
        tabs.setAlignmentX(Component.LEFT_ALIGNMENT);
        tabs.setMaximumSize(new Dimension(400, 90));
        p.add(tabs);
        p.add(gap());

        p.add(heading("Column headings"));
        String[] columns = {"Name", "Date Modified", "Size", "Kind"};
        Object[][] rows = {
            {"Report", "Yesterday, 4:12 PM", "24 KB", "Document"},
            {"Photos", "12 March 2026", "--", "Folder"},
        };
        JTable table = new JTable(rows, columns);
        table.setFont(Aqua.viewFont());
        table.setRowHeight(17);
        table.setAutoCreateRowSorter(true);
        table.getAccessibleContext().setAccessibleName("Files");
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        tableScroll.setMaximumSize(new Dimension(400, 80));
        p.add(tableScroll);

        // Down the side, because there is more here than fits and a box layout given too
        // little room does not clip, it squeezes: every control shrinks towards nothing
        // together. A gallery whose specimens are the wrong height is worse than no
        // gallery, since the whole point of it is looking at how tall things are.
        JScrollPane all = new JScrollPane(p);
        all.setBorder(null);
        all.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        all.getVerticalScrollBar().setUnitIncrement(16);
        all.getViewport().setBackground(Aqua.WINDOW_BG);
        setContentPane(all);
        getAccessibleContext().setAccessibleName("Controls");

        // The action button is the default, so its pulse can be seen.
        SwingUtilities.invokeLater(() -> {
            if (getRootPane() != null) getRootPane().setDefaultButton(action);
        });
    }

    public static void open() {
        Desktop.sharedDesktop().addWindow(new ControlGallery());
    }

    /** A control with the name a screen reader will read, since every one here needs one. */
    private static <T extends JComponent> T named(T control, String name) {
        control.getAccessibleContext().setAccessibleName(name);
        return control;
    }

    private JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Aqua.emphasizedSmallFont());
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        return l;
    }

    private JPanel row() {
        JPanel r = new JPanel();
        r.setOpaque(false);
        r.setLayout(new BoxLayout(r, BoxLayout.X_AXIS));
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        return r;
    }

    private Component gap() {
        return Box.createVerticalStrut(Aqua.GROUP_SPACING);
    }
}
