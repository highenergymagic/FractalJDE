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

import org.fractalmicro.foundation.FMLocalized;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.windowserver.Desktop;

import org.fractalmicro.fs.FS;
import org.fractalmicro.fs.Node;
import org.fractalmicro.theme.Aqua;
import org.fractalmicro.theme.Icons;

import org.fractalmicro.appkit.FMTextArea;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/** Quick Look: a peek at the selected item without opening anything. */
public class QuickLook extends JInternalFrame {

    private QuickLook(Node node, JComponent body) {
        super(FMLocalized.filled(FMString.of("finder.quickLookTitle"),
                                 FMString.of(node.name)).toString(),
              true, true, true, true);
        setFrameIcon(new ImageIcon(Icons.forNode(node, 16)));
        setContentPane(body);
        setSize(560, 460);
        getAccessibleContext().setAccessibleName(node.name);
    }

    public static void show(Node node) {
        if (node == null) { Finder.beep(); return; }
        JComponent body;
        File f = node.file;
        String name = f == null ? node.name : f.getName().toLowerCase(Locale.ROOT);

        if (f != null && f.isFile() && isImage(name)) {
            ImageIcon icon = new ImageIcon(f.getAbsolutePath());
            JLabel label = new JLabel(icon);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.getAccessibleContext().setAccessibleName(
                FMLocalized.filled(FMString.of("finder.imagePreview"),
                    FMString.of(node.name),
                    FMString.of(icon.getIconWidth() + " × " + icon.getIconHeight())).toString());
            body = new JScrollPane(label);
        } else if (f != null && f.isFile() && isText(name) && f.length() < 512 * 1024) {
            String text;
            try {
                text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            } catch (Exception e) {
                text = FMLocalized.filled(FMString.of("finder.fileNotRead"),
                                          FMString.describing(e.getMessage())).toString();
            }
            FMTextArea area = new FMTextArea(org.fractalmicro.foundation.FMString.of(text));
            area.setEditable(false);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            area.setCaretPosition(0);
            area.getAccessibleContext().setAccessibleName(
                FMLocalized.filled(FMString.of("finder.contentsOf"),
                                   FMString.of(node.name)).toString());
            body = new JScrollPane(area);
        } else {
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setBackground(Color.WHITE);
            p.setBorder(BorderFactory.createEmptyBorder(30, 20, 20, 20));
            JLabel icon = new JLabel(new ImageIcon(Icons.forNode(node, 128)));
            icon.setAlignmentX(Component.CENTER_ALIGNMENT);
            p.add(icon);
            JLabel title = new JLabel(node.name);
            title.setFont(Aqua.titleFont());
            title.setAlignmentX(Component.CENTER_ALIGNMENT);
            p.add(Box.createVerticalStrut(10));
            p.add(title);
            FMString said = FMString.of(node.kindLabel());
            if (node.size >= 0) {
                said = FMLocalized.filled(FMString.of("finder.andSize"), said,
                                          FMString.of(FS.formatBytes(node.size)));
            }
            if (node.modified > 0) {
                said = FMLocalized.filled(FMString.of("finder.andModified"), said,
                                          FMString.of(FS.formatDate(node.modified)));
            }
            JLabel detail = new JLabel(said.toString());
            detail.setFont(Aqua.smallFont());
            detail.setAlignmentX(Component.CENTER_ALIGNMENT);
            p.add(Box.createVerticalStrut(6));
            p.add(detail);
            body = p;
        }
        Desktop.sharedDesktop().addWindow(new QuickLook(node, body));
    }

    private static boolean isImage(String name) {
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
            || name.endsWith(".gif") || name.endsWith(".bmp");
    }

    private static boolean isText(String name) {
        return name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".log")
            || name.endsWith(".json") || name.endsWith(".xml") || name.endsWith(".csv")
            || name.endsWith(".java") || name.endsWith(".ini") || name.endsWith(".cfg")
            || name.indexOf('.') < 0;
    }
}
