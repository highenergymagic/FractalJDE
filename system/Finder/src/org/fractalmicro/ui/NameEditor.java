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

import org.fractalmicro.appkit.FMAlert;

import org.fractalmicro.appkit.FMTextField;
import org.fractalmicro.fs.FS;
import org.fractalmicro.fs.Node;
import org.fractalmicro.theme.Aqua;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;

/**
 * Renaming a file where the file is.
 *
 * The name under the icon, or in its row, becomes a field, with the part before the
 * extension selected.
 *
 * Return keeps the new name, Escape restores the old, and clicking away keeps it.
 *
 * The field carries the text system with it, with spelling turned off: a file name is not
 * prose.
 */
public final class NameEditor {
    private NameEditor() {}

    /** How long after a click a second click is a rename rather than an open. */
    public static final int SLOW_CLICK = 900;

    private static FMTextField editing;

    /** Whether a name is being edited right now. */
    public static boolean isEditing() { return editing != null; }

    /**
     * Puts a field over one item and lets it be typed into.
     *
     * @param over    the component the item is drawn in
     * @param bounds  where the name is drawn, in that component
     * @param node    the file being renamed
     * @param done    run once the name has been changed, or not
     */
    public static void begin(JComponent over, Rectangle bounds, Node node, Runnable done) {
        if (node == null || node.file == null) {
            Finder.beep();
            return;
        }
        if (node.isVolume()) {
            Finder.tell(FMLocalized.of(FMString.of("finder.volumesNotRenamed")), FMString.EMPTY);
            return;
        }
        cancel();

        String name = node.file.getName();
        FMTextField field = new FMTextField(FMString.of(name));
        field.plain();
        field.setFont(Aqua.viewFont());
        field.setHorizontalAlignment(SwingConstants.CENTER);
        field.setBorder(BorderFactory.createLineBorder(Aqua.SELECTION));
        // Named, and nothing else. A description saying which keys to press is repeated
        // every time the field is reached and is worth hearing once at most.
        field.getAccessibleContext().setAccessibleName(FMLocalized.of(FMString.of("browser.name")).toString());

        // Wide enough for the whole name, since the name is the thing being read and
        // changed; centred on the item, and kept inside the view it is drawn in.
        java.awt.FontMetrics metrics = field.getFontMetrics(field.getFont());
        int room = Math.max(metrics.stringWidth(name) + 24, Math.max(bounds.width + 24, 80));
        Rectangle where = new Rectangle(bounds);
        where.width = Math.min(room, Math.max(120, over.getWidth() - 8));
        where.x = Math.max(2, Math.min(bounds.x + (bounds.width - where.width) / 2,
                                       over.getWidth() - where.width - 2));
        where.height = Math.max(where.height, field.getPreferredSize().height);
        field.setBounds(where);

        over.add(field);
        over.setComponentZOrder(field, 0);
        over.revalidate();
        over.repaint();
        editing = field;

        // The name without its extension, which is the part being changed nine times in
        // ten; the extension stays where it is unless it is typed over deliberately.
        int dot = name.lastIndexOf('.');
        field.setCaretPosition(0);
        field.select(0, dot > 0 ? dot : name.length());
        field.requestFocusInWindow();

        Runnable finish = () -> {
            String wanted = field.getText().trim();
            stop(over, field);
            if (!wanted.isEmpty() && !wanted.equals(name)) apply(node, wanted);
            if (done != null) done.run();
        };

        field.addActionListener(e -> finish.run());
        field.getInputMap(JComponent.WHEN_FOCUSED)
             .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelRename");
        field.getActionMap().put("cancelRename", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                stop(over, field);
                if (done != null) done.run();
            }
        });
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                if (editing == field) finish.run();
            }
        });
    }

    private static void stop(JComponent over, JComponent field) {
        if (editing != field) return;
        editing = null;
        over.remove(field);
        over.revalidate();
        over.repaint();
        over.requestFocusInWindow();
    }

    /** Takes the field away without changing anything. */
    public static void cancel() {
        FMTextField field = editing;
        if (field == null) return;
        editing = null;
        Container parent = field.getParent();
        if (parent != null) {
            parent.remove(field);
            parent.revalidate();
            parent.repaint();
        }
    }

    /** Does the renaming, and says what went wrong when something does. */
    private static void apply(Node node, String wanted) {
        File from = node.file;
        File to = new File(from.getParentFile(), wanted);
        if (to.exists()) {
            FMAlert.tell(
                FMLocalized.filled(FMString.of("finder.nameTaken"), FMString.of(wanted)),
                FMLocalized.of(FMString.of("finder.chooseAnother")));
            return;
        }
        if (!from.renameTo(to)) {
            FMAlert.tell(
                FMLocalized.filled(FMString.of("finder.renameFailedNamed"),
                                   FMString.of(from.getName())),
                FMLocalized.of(FMString.of("finder.renameFailedWhy")));
            return;
        }
        // Anything kept beside the file rather than in it has to follow it.
        org.fractalmicro.fs.Sidecar.moved(from, to);
        org.fractalmicro.fs.Labels.forget(from);
        Finder.refreshAll();
    }

    /** The name that would be shown for a file, for sizing the field over it. */
    public static String displayName(Node node) {
        return node == null || node.file == null ? "" : FS.displayName(node.file);
    }
}
