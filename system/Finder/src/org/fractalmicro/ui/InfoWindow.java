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
import org.fractalmicro.foundation.FMLocalized;

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

    /** What the window is called: the item's name and the word Finder puts after it. */
    private static String titleFor(Node node) {
        return FMLocalized.filled(FMString.of("info.title"), FMString.of(node.name)).toString();
    }

    private static String word(FMString key) {
        return FMLocalized.of(key).toString();
    }

    public InfoWindow(Node node) {
        super(titleFor(node), true, true, false, true);
        setFrameIcon(new ImageIcon(Icons.forNode(node, 16)));
        setSize(310, 470);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(
            Aqua.WINDOW_MARGIN, Aqua.WINDOW_MARGIN, Aqua.WINDOW_MARGIN, Aqua.WINDOW_MARGIN));
        form.setBackground(Aqua.WINDOW_BG);

        form.add(header(node));
        form.add(Box.createVerticalStrut(Aqua.GROUP_SPACING));

        form.add(group(FMString.of("info.spotlightComments")));
        FMTextArea comments = new FMTextArea(2, 20);
        comments.setFont(Aqua.viewFont());
        comments.setLineWrap(true);
        comments.getAccessibleContext().setAccessibleName(word(FMString.of("info.spotlightCommentsField")));
        JScrollPane commentsScroll = new JScrollPane(comments);
        commentsScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        commentsScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        form.add(commentsScroll);
        form.add(Box.createVerticalStrut(Aqua.GROUP_SPACING));

        form.add(group(FMString.of("info.general")));
        File f = node.file;
        row(form, FMString.of("info.kind"), Kinds.display(node));
        // A volume is measured, not sized: it has a capacity, an amount left and an amount
        // used, and each of those is its own line. Everything else has one size.
        if (!node.isVolume()) {
            row(form, FMString.of("info.size"), sizeText(node));
            row(form, FMString.of("info.where"), where(node));
        }
        row(form, FMString.of("info.created"), FS.formatDate(created(f)));
        row(form, FMString.of("info.modified"), FS.formatDate(node.modified));
        if (node.label > 0) row(form, FMString.of("info.label"), org.fractalmicro.fs.Labels.nameOf(node.label));
        if (node.kind == org.fractalmicro.fs.Node.Kind.ALIAS && f != null) {
            row(form, FMString.of("info.original"), Kinds.aliasDetail(node));
            long fork = org.fractalmicro.fs.ResourceFork.sizeOn(f);
            if (fork > 0) row(form, FMString.of("info.resourceFork"), FS.formatBytes(fork));
        }
        if (node.isVolume()) {
            row(form, FMString.of("info.format"),
                node.fileSystem.isEmpty() ? word(FMString.of("info.unknownFormat")) : node.fileSystem);
            if (node.size > 0) {
                // A volume is written plainly, with no byte counts: a disk is measured in
                // the units it was sold in, and the exact figure belongs to files.
                row(form, FMString.of("info.capacity"), FS.formatBytes(node.size));
                row(form, FMString.of("info.available"), FS.formatBytes(node.free));
                row(form, FMString.of("info.used"), FS.formatBytes(node.size - node.free));
            } else {
                row(form, FMString.of("info.capacity"), word(FMString.of("info.noDisc")));
            }
        }

        JCheckBox locked = new JCheckBox(word(FMString.of("info.locked")), node.locked);
        locked.setBackground(Aqua.WINDOW_BG);
        locked.setFont(Aqua.systemFont());
        locked.setAlignmentX(Component.LEFT_ALIGNMENT);
        locked.setEnabled(f != null && !node.isVolume());
        locked.addActionListener(e -> {
            if (f == null) return;
            boolean ok = locked.isSelected() ? f.setReadOnly() : f.setWritable(true);
            if (!ok) {
                FMAlert.tell(
                    FMLocalized.filled(FMString.of("info.lockFailed"), FMString.of(node.name)),
                    FMLocalized.of(FMString.of("info.lockFailedWhy")));
            }
        });
        form.add(Box.createVerticalStrut(Aqua.CONTROL_SPACING));
        form.add(locked);

        if (f != null && !f.isDirectory()) {
            form.add(Box.createVerticalStrut(Aqua.GROUP_SPACING));
            form.add(group(FMString.of("info.nameAndExtension")));
            FMTextField fullName = new FMTextField(FMString.of(f.getName()));
            fullName.setFont(Aqua.viewFont());
            fullName.setEditable(false);
            fullName.setAlignmentX(Component.LEFT_ALIGNMENT);
            fullName.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                                                  fullName.getPreferredSize().height));
            fullName.getAccessibleContext().setAccessibleName(word(FMString.of("info.nameAndExtensionField")));
            form.add(fullName);
        }

        form.add(Box.createVerticalStrut(Aqua.GROUP_SPACING));
        form.add(group(FMString.of("info.sharingAndPermissions")));
        row(form, FMString.of("info.youCan"), permissions(f));

        form.add(Box.createVerticalGlue());
        JButton reveal = new JButton(word(FMString.of("finder.showInExplorer")));
        reveal.setFont(Aqua.systemFont());
        reveal.setAlignmentX(Component.LEFT_ALIGNMENT);
        reveal.setEnabled(f != null);
        reveal.addActionListener(e -> FS.reveal(f));
        form.add(Box.createVerticalStrut(Aqua.GROUP_SPACING));
        form.add(reveal);

        setContentPane(new JScrollPane(form));
        getAccessibleContext().setAccessibleName(titleFor(node));
    }

    /* -------------------------------------------------------------- pieces */

    private JComponent header(Node node) {
        JPanel top = new JPanel(new BorderLayout(Aqua.CONTROL_SPACING, 0));
        top.setOpaque(false);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));

        JLabel icon = new JLabel(new ImageIcon(Icons.forNode(node, 64)));
        icon.getAccessibleContext().setAccessibleName(word(FMString.of("info.icon")));
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
    private JLabel group(FMString key) {
        JLabel l = new JLabel(word(key));
        l.setFont(Aqua.emphasizedSmallFont());
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        return l;
    }

    /**
     * One "Label: value" line, the label right against its value as in Finder.
     *
     * The colon is in the table rather than added here, because it is not a colon in
     * every language: French puts a space before it and Japanese uses a different mark.
     */
    private void row(JPanel parent, FMString key, String value) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JLabel l = new JLabel(word(key));
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
        if (node.size <= 0) return word(FMString.of("info.zeroBytes"));
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
        if (f == null) return word(FMString.of("info.readOnly"));
        if (f.canWrite() && f.canRead()) return word(FMString.of("info.readWrite"));
        if (f.canRead()) return word(FMString.of("info.readOnly"));
        return word(FMString.of("info.noAccess"));
    }
}
