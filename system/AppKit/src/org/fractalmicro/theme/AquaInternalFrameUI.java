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
package org.fractalmicro.theme;

import org.fractalmicro.foundation.FMLocalized;
import org.fractalmicro.foundation.FMString;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicInternalFrameUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Window chrome: a light gradient title bar with the three coloured buttons on the
 * left. The buttons are real JButtons with names, so they can be tabbed to and read.
 */
public class AquaInternalFrameUI extends BasicInternalFrameUI {

    /**
     * The mark on a window that is holding unsaved changes.
     *
     * A property on the window rather than a field, because the window is made by the
     * window server and the thing that knows about the changes is a program somewhere else.
     */
    public static final String DOCUMENT_EDITED = "org.fractalmicro.documentEdited";

    public AquaInternalFrameUI(JInternalFrame f) { super(f); }

    public static ComponentUI createUI(JComponent c) {
        return new AquaInternalFrameUI((JInternalFrame) c);
    }

    @Override
    protected JComponent createNorthPane(JInternalFrame w) {
        return new TitlePane(w);
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        c.setBorder(BorderFactory.createLineBorder(new Color(0x6E6E6E)));
        c.setBackground(Aqua.WINDOW_BG);
    }

    /** The title bar itself. */
    public static class TitlePane extends JPanel {
        private final JInternalFrame frame;

        TitlePane(JInternalFrame frame) {
            this.frame = frame;
            setLayout(null);
            setPreferredSize(new Dimension(100, 22));
            setOpaque(true);
            getAccessibleContext().setAccessibleName(
                FMLocalized.of(FMString.of("window.titleBar")).toString());

            java.util.List<javax.swing.JComponent> buttons = new java.util.ArrayList<>();
            int x = 8;
            if (frame.isClosable()) {
                JButton b = light(x, Aqua.CLOSE_RED, FMString.of("window.close"),
                                  true, "×", e -> close());
                add(b);
                buttons.add(b);
                x += 20;
            }
            if (frame.isIconifiable()) {
                JButton b = light(x, Aqua.MIN_YELLOW, FMString.of("window.minimize"),
                                  false, "−", e -> iconify());
                add(b);
                buttons.add(b);
                x += 20;
            }
            if (frame.isMaximizable()) {
                JButton b = light(x, Aqua.ZOOM_GREEN, FMString.of("window.zoom"),
                                  false, "+", e -> zoom());
                add(b);
                buttons.add(b);
            }
            // One tab stop for the three of them; left and right move between them.
            if (!buttons.isEmpty()) org.fractalmicro.appkit.FocusGroup.horizontal(this, buttons);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) zoom();
                }
            });
        }

        /**
         * Whether the window is holding changes that have not been written.
         *
         * Kept on the window rather than passed in, because whoever knows it is the program
         * and the program is usually somewhere else. It sets it through the window server,
         * the same way it sets a title.
         */
        static boolean edited(JInternalFrame frame) {
            return Boolean.TRUE.equals(frame.getClientProperty(DOCUMENT_EDITED));
        }

        /**
         * One of the three lights, named by a key and told what it is.
         *
         * Which one closes is said outright rather than worked out from what the button
         * is called. The dot that marks unsaved changes hung on the word Close until
         * somebody translated it, and then it hung on nothing.
         */
        private JButton light(int x, Color colour, FMString key, boolean closes,
                              String glyph, java.awt.event.ActionListener action) {
            String name = FMLocalized.of(key).toString();
            JButton b = new JButton() {
                @Override protected void paintComponent(Graphics g) {
                    // The cross, the minus and the plus appear only while the pointer is
                    // over the three of them, which is what a Mac does. The dot is the
                    // exception and has to be: it is a warning, and a warning nobody sees
                    // until they reach for the button is not one. It shows on an inactive
                    // window too, which is where somebody is most likely to have forgotten.
                    boolean marked = closes && edited(frame);
                    Aqua.paintTrafficLight((Graphics2D) g, 0, 0, 13, colour,
                        frame.isSelected(),
                        marked || getModel().isRollover() || hasFocus(),
                        marked ? "•" : glyph);
                }
            };
            b.setBounds(x, 5, 13, 13);
            b.setBorderPainted(false);
            b.setContentAreaFilled(false);
            b.setFocusPainted(false);
            b.setRolloverEnabled(true);
            b.setToolTipText(name);
            b.getAccessibleContext().setAccessibleName(name);
            b.addActionListener(action);
            return b;
        }

        private void close() {
            frame.doDefaultCloseAction();
        }

        private void iconify() {
            try { frame.setIcon(true); } catch (java.beans.PropertyVetoException ignored) { }
        }

        private void zoom() {
            if (!frame.isMaximizable()) return;
            try { frame.setMaximum(!frame.isMaximum()); } catch (java.beans.PropertyVetoException ignored) { }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Aqua.antialias(g);
            boolean active = frame.isSelected();
            Aqua.paintTitleBar(g, getWidth(), getHeight(), active);

            String title = frame.getTitle() == null ? "" : frame.getTitle();
            g.setFont(Aqua.titleFont());
            FontMetrics fm = g.getFontMetrics();
            Icon icon = frame.getFrameIcon();
            int textWidth = fm.stringWidth(title);
            int iconWidth = icon == null ? 0 : icon.getIconWidth() + 4;
            int start = (getWidth() - textWidth - iconWidth) / 2;
            if (start < 70) start = 70;
            int baseline = (getHeight() + fm.getAscent() - fm.getDescent()) / 2 - 1;

            if (icon != null) icon.paintIcon(this, g, start, (getHeight() - icon.getIconHeight()) / 2);
            g.setColor(active ? Aqua.TITLE_TEXT : Aqua.TITLE_TEXT_OFF);
            g.drawString(Aqua.clipEnd(fm, title, getWidth() - start - iconWidth - 10),
                         start + iconWidth, baseline);
            g.dispose();
        }
    }
}
