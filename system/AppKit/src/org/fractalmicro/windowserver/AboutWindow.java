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
import org.fractalmicro.os.SystemProfile;
import org.fractalmicro.theme.Aqua;
import org.fractalmicro.theme.Icons;

import javax.swing.*;
import java.awt.*;

/**
 * About This Computer, laid out the way Mac OS X 10.6 lays it out: the mark, the
 * system name and version, then Processor, Memory and Startup Disk as label and value
 * pairs, then the company line.
 */
public class AboutWindow extends JInternalFrame {

    private AboutWindow(String title, JComponent body, int w, int h) {
        super(title, true, true, false, false);
        setFrameIcon(new ImageIcon(Icons.forKind(Node.Kind.COMPUTER, 16)));
        setContentPane(new JScrollPane(body));
        setSize(w, h);
        getAccessibleContext().setAccessibleName(title);
    }

    public static void showAboutComputer() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(20, 28, 18, 28));
        p.setBackground(Color.WHITE);

        JPanel logo = new JPanel() {
            @Override public Dimension getPreferredSize() { return new Dimension(72, 72); }
            @Override public Dimension getMaximumSize() { return getPreferredSize(); }
            @Override protected void paintComponent(Graphics g) {
                Aqua.antialias((Graphics2D) g);
                Image mark = org.fractalmicro.theme.BrandMark.image(64, new Color(0x1A1A1A));
                if (mark != null) {
                    g.drawImage(mark, (getWidth() - mark.getWidth(null)) / 2, 4, null);
                } else {
                    Icons.paintLogo((Graphics2D) g, 4, 4, 64, new Color(0x333333));
                }
            }
        };
        logo.setOpaque(false);
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        logo.setFocusable(false);
        p.add(logo);

        p.add(centred(SystemProfile.OS_NAME, Aqua.titleFont().deriveFont(Font.PLAIN, 24f)));
        p.add(Box.createVerticalStrut(2));
        p.add(centred(SystemProfile.OS_LONG_NAME, Aqua.smallFont()));
        p.add(centred("Version " + SystemProfile.version()
            + " (" + SystemProfile.build() + ")", Aqua.smallFont()));
        p.add(Box.createVerticalStrut(14));

        // The facts go in a table, which is what they are: rows of name and value, each
        // the rows, which a column of labels does not allow.
        String[][] rows = {
            {"Processor", SystemProfile.processor()},
            {"Memory", SystemProfile.memory()},
            {"Startup Disk", SystemProfile.startupDisk()},
        };
        javax.swing.table.DefaultTableModel model =
            new javax.swing.table.DefaultTableModel(rows, new String[]{"Item", "Value"}) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
        JTable table = new JTable(model);
        table.setFont(Aqua.smallFont());
        table.setRowHeight(20);
        table.setShowGrid(false);
        table.setFillsViewportHeight(false);
        table.setTableHeader(null);
        table.setBackground(Color.WHITE);
        table.getColumnModel().getColumn(0).setPreferredWidth(110);
        table.getAccessibleContext().setAccessibleName("System information");
        table.setPreferredScrollableViewportSize(new Dimension(320, 64));
        JPanel facts = new JPanel(new BorderLayout());
        facts.setOpaque(false);
        facts.setAlignmentX(Component.CENTER_ALIGNMENT);
        facts.setMaximumSize(new Dimension(340, 70));
        facts.add(table, BorderLayout.CENTER);
        p.add(facts);

        p.add(Box.createVerticalStrut(16));
        JButton more = new JButton("More Info…");
        more.setAlignmentX(Component.CENTER_ALIGNMENT);
        more.addActionListener(e ->
            org.fractalmicro.bundle.Bundles.openIdentifier("org.fractalmicro.systemprofiler"));
        p.add(more);

        p.add(Box.createVerticalStrut(12));
        p.add(centred(SystemProfile.VENDOR, Aqua.smallFont()));

        Desktop.sharedDesktop().addWindow(new AboutWindow("About This Computer", p, 400, 380));
    }

    public static void showAboutFinder() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(20, 24, 18, 24));
        p.setBackground(Color.WHITE);
        JLabel icon = new JLabel(new ImageIcon(Icons.forKind(Node.Kind.FOLDER, 64)));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(icon);
        p.add(Box.createVerticalStrut(8));
        p.add(centred("Finder", Aqua.titleFont().deriveFont(Font.PLAIN, 18f)));
        p.add(centred("Version " + SystemProfile.version()
            + " (" + SystemProfile.build() + ")", Aqua.smallFont()));
        Desktop.sharedDesktop().addWindow(new AboutWindow("About Finder", p, 320, 240));
    }

    /** The same panel for any other program, named for whichever one asked. */
    public static void showAboutApplication(String application) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(20, 24, 18, 24));
        p.setBackground(Color.WHITE);
        java.awt.Image art = org.fractalmicro.theme.AppIcons.forApplication(application, 64);
        JLabel icon = new JLabel(new ImageIcon(
            art != null ? art : Icons.forKind(Node.Kind.FILE, 64)));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(icon);
        p.add(Box.createVerticalStrut(8));
        p.add(centred(application, Aqua.titleFont().deriveFont(Font.PLAIN, 18f)));
        p.add(centred("Version " + SystemProfile.version()
            + " (" + SystemProfile.build() + ")", Aqua.smallFont()));
        p.add(Box.createVerticalStrut(12));
        p.add(centred(SystemProfile.VENDOR, Aqua.smallFont()));
        Desktop.sharedDesktop().addWindow(new AboutWindow("About " + application, p, 320, 260));
    }

    private static JLabel centred(String text, Font font) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

}
