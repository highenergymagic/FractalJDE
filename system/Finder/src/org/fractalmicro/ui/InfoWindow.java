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

import org.fractalmicro.appkit.FMAlert;

import org.fractalmicro.fs.FS;
import org.fractalmicro.fs.Kinds;
import org.fractalmicro.fs.Node;
import org.fractalmicro.theme.Aqua;
import org.fractalmicro.theme.Icons;

import org.fractalmicro.appkit.FMTextField;

import org.fractalmicro.appkit.FMTextArea;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Get Info, laid out the way 10.6 lays it out: the icon and name across the top, then
 * groups under the headings Finder uses (Spotlight Comments, General, Name and
 * Extension, Sharing and Permissions), each label written as a word and a colon.
 *
 * Titled "name Info", as Finder titles it.
 */
public class InfoWindow extends JInternalFrame {

    private static final char LEFT_QUOTE = '“';
    private static final char RIGHT_QUOTE = '”';
    private static final char APOSTROPHE = '’';

    public InfoWindow(Node node) {
        super(node.name + " Info", true, true, false, true);
        setFrameIcon(new ImageIcon(Icons.forNode(node, 16)));
        setSize(310, 470);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(
            Aqua.WINDOW_MARGIN, Aqua.WINDOW_MARGIN, Aqua.WINDOW_MARGIN, Aqua.WINDOW_MARGIN));
        form.setBackground(Aqua.WINDOW_BG);

        form.add(header(node));
        form.add(Box.createVerticalStrut(Aqua.GROUP_SPACING));

        form.add(group("Spotlight Comments:"));
        FMTextArea comments = new FMTextArea(2, 20);
        comments.setFont(Aqua.viewFont());
        comments.setLineWrap(true);
        comments.getAccessibleContext().setAccessibleName("Spotlight Comments");
        JScrollPane commentsScroll = new JScrollPane(comments);
        commentsScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        commentsScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        form.add(commentsScroll);
        form.add(Box.createVerticalStrut(Aqua.GROUP_SPACING));

        form.add(group("General:"));
        File f = node.file;
        row(form, "Kind", Kinds.display(node));
        // A volume is measured, not sized: it has a capacity, an amount left and an amount
        // used, and each of those is its own line. Everything else has one size.
        if (!node.isVolume()) {
            row(form, "Size", sizeText(node));
            row(form, "Where", where(node));
        }
        row(form, "Created", FS.formatDate(created(f)));
        row(form, "Modified", FS.formatDate(node.modified));
        if (node.label > 0) row(form, "Label", org.fractalmicro.fs.Labels.nameOf(node.label));
        if (node.kind == org.fractalmicro.fs.Node.Kind.ALIAS && f != null) {
            row(form, "Original", Kinds.aliasDetail(node));
            long fork = org.fractalmicro.fs.ResourceFork.sizeOn(f);
            if (fork > 0) row(form, "Resource fork", FS.formatBytes(fork));
        }
        if (node.isVolume()) {
            row(form, "Format", node.fileSystem.isEmpty() ? "Unknown" : node.fileSystem);
            if (node.size > 0) {
                // A volume is written plainly, with no byte counts: a disk is measured in
                // the units it was sold in, and the exact figure belongs to files.
                row(form, "Capacity", FS.formatBytes(node.size));
                row(form, "Available", FS.formatBytes(node.free));
                row(form, "Used", FS.formatBytes(node.size - node.free));
            } else {
                row(form, "Capacity", "No disc inserted");
            }
        }

        JCheckBox locked = new JCheckBox("Locked", node.locked);
        locked.setBackground(Aqua.WINDOW_BG);
        locked.setFont(Aqua.systemFont());
        locked.setAlignmentX(Component.LEFT_ALIGNMENT);
        locked.setEnabled(f != null && !node.isVolume());
        locked.addActionListener(e -> {
            if (f == null) return;
            boolean ok = locked.isSelected() ? f.setReadOnly() : f.setWritable(true);
            if (!ok) {
                FMAlert.tell(FMString.of("The lock on " + LEFT_QUOTE + node.name + RIGHT_QUOTE
                                   + " can" + APOSTROPHE + "t be changed."),
                           FMString.of("You may not have permission to change this item."));
            }
        });
        form.add(Box.createVerticalStrut(Aqua.CONTROL_SPACING));
        form.add(locked);

        if (f != null && !f.isDirectory()) {
            form.add(Box.createVerticalStrut(Aqua.GROUP_SPACING));
            form.add(group("Name & Extension:"));
            FMTextField fullName = new FMTextField(FMString.of(f.getName()));
            fullName.setFont(Aqua.viewFont());
            fullName.setEditable(false);
            fullName.setAlignmentX(Component.LEFT_ALIGNMENT);
            fullName.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                                                  fullName.getPreferredSize().height));
            fullName.getAccessibleContext().setAccessibleName("Name & Extension");
            form.add(fullName);
        }

        form.add(Box.createVerticalStrut(Aqua.GROUP_SPACING));
        form.add(group("Sharing & Permissions:"));
        row(form, "You can", permissions(f));

        form.add(Box.createVerticalGlue());
        JButton reveal = new JButton("Show in Windows Explorer");
        reveal.setFont(Aqua.systemFont());
        reveal.setAlignmentX(Component.LEFT_ALIGNMENT);
        reveal.setEnabled(f != null);
        reveal.addActionListener(e -> FS.reveal(f));
        form.add(Box.createVerticalStrut(Aqua.GROUP_SPACING));
        form.add(reveal);

        setContentPane(new JScrollPane(form));
        getAccessibleContext().setAccessibleName(node.name + " Info");
    }

    /* -------------------------------------------------------------- pieces */

    private JComponent header(Node node) {
        JPanel top = new JPanel(new BorderLayout(Aqua.CONTROL_SPACING, 0));
        top.setOpaque(false);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));

        JLabel icon = new JLabel(new ImageIcon(Icons.forNode(node, 64)));
        icon.getAccessibleContext().setAccessibleName("Icon");
        top.add(icon, BorderLayout.WEST);

        JPanel names = new JPanel();
        names.setOpaque(false);
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(node.name);
        name.setFont(Aqua.emphasizedSystemFont());
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel summary = new JLabel(node.summary());
        summary.setFont(Aqua.smallFont());
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        names.add(Box.createVerticalGlue());
        names.add(name);
        names.add(summary);
        names.add(Box.createVerticalGlue());
        top.add(names, BorderLayout.CENTER);
        return top;
    }

    /** A group heading, in the emphasized small system font Finder uses for these. */
    private JLabel group(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Aqua.emphasizedSmallFont());
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        return l;
    }

    /** One "Label: value" line, the label right against its value as in Finder. */
    private void row(JPanel parent, String label, String value) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JLabel l = new JLabel(label + ":");
        l.setFont(Aqua.smallFont());
        JLabel v = new JLabel(value);
        v.setFont(Aqua.smallFont());
        l.setLabelFor(v);
        v.getAccessibleContext().setAccessibleName(value);
        row.add(l);
        row.add(v);
        parent.add(row);
    }

    /**
     * The size of one thing: what it rounds to, then exactly what it is. A folder says how
     * many things are in it, because that is the answer to the question being asked.
     */
    private String sizeText(Node node) {
        if (node.file != null && node.file.isDirectory()) return node.summary();
        if (node.size <= 0) return "Zero bytes";
        return FS.formatSize(node.size, org.fractalmicro.win.Files32.allocatedSizeOrLength(node.file));
    }

    private String where(Node node) {
        if (node.file == null) return "—";
        String parent = node.file.getParent();
        return parent == null ? node.file.getPath() : parent;
    }

    private long created(File f) {
        if (f == null) return 0;
        try {
            BasicFileAttributes a = Files.readAttributes(f.toPath(), BasicFileAttributes.class);
            return a.creationTime().toMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    private String permissions(File f) {
        if (f == null) return "Read only";
        if (f.canWrite() && f.canRead()) return "Read & Write";
        if (f.canRead()) return "Read only";
        return "No access";
    }
}
